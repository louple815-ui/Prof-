package com.example.data.repository

import android.net.Uri
import com.example.core.MediaUtils
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.local.DownloadedResourceDao
import com.example.data.local.DownloadedResourceEntity
import com.example.data.model.ResourceItem
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ResourceRepository(
    private val db: FirebaseFirestore,
    private val downloadedDao: DownloadedResourceDao,
    private val auth: FirebaseAuth = Firebase.auth,
    private val storageRepository: StorageRepository = StorageRepository()
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Utilisateur non connecté.")

    fun observePublicResources(): Flow<List<ResourceItem>> = flow {
        val path = "resources"
        emitAll(
            db.collection("resources")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ResourceItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeOfflineDownloaded(): Flow<List<DownloadedResourceEntity>> =
        downloadedDao.observeAll()

    suspend fun publishResource(
        authorName: String,
        title: String,
        description: String,
        resourceType: String,
        subject: String,
        level: String,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        contentBody: String,
        fileUriString: String = ""
    ): Result<Unit> {
        val docRef = db.collection("resources").document()
        return try {
            val uid = requireUserId()
            require(title.trim().length >= 2) { "Titre requis." }
            require(fileSizeBytes <= MediaUtils.MAX_RESOURCE_UPLOAD_BYTES) {
                "Fichier trop volumineux (max 20 Mo)."
            }
            val uploaded = if (StorageRepository.isLocalUri(fileUriString)) {
                storageRepository.uploadResourceFile(uid, Uri.parse(fileUriString))
            } else null

            val safeTextBody = if (contentBody.startsWith("base64:") || contentBody.startsWith("data:")) {
                ""
            } else {
                contentBody.take(50_000)
            }
            val resolvedFileName = uploaded?.fileName ?: fileName
            val resolvedMime = uploaded?.mimeType ?: mimeType
            val resolvedSize = uploaded?.sizeBytes ?: fileSizeBytes

            val payload = buildMap<String, Any> {
                put("title", title.trim().take(160))
                if (description.isNotBlank()) put("description", description.trim().take(1000))
                put("resourceType", resourceType)
                put("subject", subject.take(80))
                put("level", level.take(80))
                put("fileName", resolvedFileName.take(160))
                put("mimeType", resolvedMime.take(100))
                put("fileSizeBytes", resolvedSize)
                if (uploaded != null) {
                    put("fileUrl", uploaded.downloadUrl)
                    put("storagePath", uploaded.storagePath)
                }
                if (safeTextBody.isNotBlank()) put("contentBody", safeTextBody)
                put("authorId", uid)
                put("authorName", authorName.take(120))
                put("visibility", "PUBLIC")
                put("downloadsCount", 0L)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun downloadForOffline(resource: ResourceItem): Result<DownloadedResourceEntity> {
        return try {
            val fetchedText = if (
                resource.contentBody.isBlank() &&
                resource.fileUrl.isNotBlank() &&
                resource.mimeType.startsWith("text/")
            ) {
                storageRepository.downloadTextPreview(resource.fileUrl).orEmpty()
            } else {
                ""
            }
            val bodyForOffline = resource.contentBody
                .ifBlank { fetchedText }
                .ifBlank {
                    buildString {
                        append(resource.description)
                        if (resource.fileUrl.isNotBlank()) {
                            if (isNotEmpty()) append("\n\n")
                            append("Lien Cloud Storage : ${resource.fileUrl}")
                        }
                    }
                }
            val entity = DownloadedResourceEntity(
                resourceId = resource.id,
                title = resource.title,
                description = resource.description,
                resourceType = resource.resourceType,
                subject = resource.subject,
                level = resource.level,
                fileName = resource.fileName,
                mimeType = resource.mimeType,
                fileSizeBytes = resource.fileSizeBytes,
                contentBody = bodyForOffline,
                authorName = resource.authorName
            )
            downloadedDao.upsert(entity)

            // Record download event document (Cloud Functions updates resources.downloadsCount server-side)
            try {
                val uid = auth.currentUser?.uid
                if (uid != null && resource.id.isNotBlank()) {
                    db.collection("resources").document(resource.id)
                        .collection("downloads")
                        .document()
                        .set(
                            mapOf(
                                "resourceId" to resource.id,
                                "userId" to uid,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                }
            } catch (_: Exception) {
            }
            Result.success(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeOfflineResource(resourceId: String) {
        downloadedDao.deleteById(resourceId)
    }
}
