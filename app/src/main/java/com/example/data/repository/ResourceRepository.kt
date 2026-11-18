package com.example.data.repository

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
    private val auth: FirebaseAuth = Firebase.auth
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
        contentBody: String
    ): Result<Unit> {
        val docRef = db.collection("resources").document()
        return try {
            val uid = requireUserId()
            require(title.trim().length >= 2) { "Titre requis." }
            require(fileSizeBytes <= 5_000_000L) { "Fichier trop volumineux (max 5 Mo)." }
            val payload = buildMap<String, Any> {
                put("title", title.trim().take(160))
                if (description.isNotBlank()) put("description", description.trim().take(1000))
                put("resourceType", resourceType)
                put("subject", subject.take(80))
                put("level", level.take(80))
                put("fileName", fileName.take(160))
                put("mimeType", mimeType.take(100))
                put("fileSizeBytes", fileSizeBytes)
                if (contentBody.isNotBlank()) put("contentBody", contentBody.take(450_000))
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
                contentBody = resource.contentBody.ifBlank { resource.description },
                authorName = resource.authorName
            )
            downloadedDao.upsert(entity)
            // Best-effort increment of downloadsCount if online & signed in
            try {
                if (auth.currentUser != null && resource.id.isNotBlank()) {
                    db.collection("resources").document(resource.id).update(
                        mapOf(
                            "downloadsCount" to resource.downloadsCount + 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
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
