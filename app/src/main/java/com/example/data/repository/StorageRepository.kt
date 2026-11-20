package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.core.MediaUtils
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

data class UploadedMedia(
    val downloadUrl: String,
    val storagePath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

class StorageRepository(
    private val appContext: Context? = null
) {
    private val storage: FirebaseStorage by lazy { Firebase.storage }

    private fun requireContext(): Context =
        appContext ?: throw IllegalStateException("Contexte Android indisponible pour Firebase Storage.")

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        return cleaned.ifBlank { "fichier" }.take(80)
    }

    suspend fun uploadProfilePhoto(userId: String, uri: Uri): UploadedMedia = withContext(Dispatchers.IO) {
        val context = requireContext()
        val bytes = MediaUtils.prepareImageBytes(context, uri, MediaUtils.MAX_IMAGE_UPLOAD_BYTES)
            ?: throw IllegalArgumentException("Impossible de lire ou compresser la photo (max 5 Mo).")
        val path = "avatars/$userId/avatar_${UUID.randomUUID()}.jpg"
        uploadBytes(path, bytes, "image/jpeg", "avatar.jpg")
    }

    suspend fun uploadPostImage(userId: String, uri: Uri): UploadedMedia = withContext(Dispatchers.IO) {
        val context = requireContext()
        val bytes = MediaUtils.prepareImageBytes(context, uri, MediaUtils.MAX_IMAGE_UPLOAD_BYTES)
            ?: throw IllegalArgumentException("Impossible de lire ou compresser l'image (max 5 Mo).")
        val path = "posts/$userId/images/${UUID.randomUUID()}.jpg"
        uploadBytes(path, bytes, "image/jpeg", "image.jpg")
    }

    suspend fun uploadPostAttachment(userId: String, uri: Uri): UploadedMedia = withContext(Dispatchers.IO) {
        val context = requireContext()
        val file = MediaUtils.readUploadableFile(context, uri, MediaUtils.MAX_ATTACHMENT_UPLOAD_BYTES)
            ?: throw IllegalArgumentException("Impossible de lire la pièce jointe.")
        require(!file.isTooLarge) { "Pièce jointe trop volumineuse (max 10 Mo)." }
        val safeName = sanitizeFileName(file.fileName)
        val path = "posts/$userId/attachments/${UUID.randomUUID()}_$safeName"
        uploadBytes(path, file.bytes, file.mimeType, file.fileName)
    }

    suspend fun uploadMessageAttachment(conversationId: String, userId: String, uri: Uri): UploadedMedia =
        withContext(Dispatchers.IO) {
            val context = requireContext()
            val file = MediaUtils.readUploadableFile(context, uri, MediaUtils.MAX_ATTACHMENT_UPLOAD_BYTES)
                ?: throw IllegalArgumentException("Impossible de lire la pièce jointe.")
            require(!file.isTooLarge) { "Pièce jointe trop volumineuse (max 10 Mo)." }
            val safeName = sanitizeFileName(file.fileName)
            val path = "messages/$conversationId/$userId/${UUID.randomUUID()}_$safeName"
            uploadBytes(path, file.bytes, file.mimeType, file.fileName)
        }

    suspend fun uploadResourceFile(userId: String, uri: Uri): UploadedMedia = withContext(Dispatchers.IO) {
        val context = requireContext()
        val file = MediaUtils.readUploadableFile(context, uri, MediaUtils.MAX_RESOURCE_UPLOAD_BYTES)
            ?: throw IllegalArgumentException("Impossible de lire la ressource.")
        require(!file.isTooLarge) { "Fichier trop volumineux (max 20 Mo)." }
        val safeName = sanitizeFileName(file.fileName)
        val path = "resources/$userId/${UUID.randomUUID()}_$safeName"
        uploadBytes(path, file.bytes, file.mimeType, file.fileName)
    }

    suspend fun downloadTextPreview(fileUrl: String, maxBytes: Long = 512_000L): String? =
        withContext(Dispatchers.IO) {
            if (!fileUrl.startsWith("https://")) return@withContext null
            try {
                val bytes = storage.getReferenceFromUrl(fileUrl).getBytes(maxBytes).await()
                bytes.toString(Charsets.UTF_8).take(50_000)
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun uploadBytes(
        storagePath: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): UploadedMedia {
        val ref = storage.reference.child(storagePath)
        val metadata = StorageMetadata.Builder()
            .setContentType(mimeType.ifBlank { "application/octet-stream" })
            .build()
        ref.putBytes(bytes, metadata).await()
        val url = ref.downloadUrl.await().toString()
        return UploadedMedia(
            downloadUrl = url,
            storagePath = storagePath,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong()
        )
    }

    companion object {
        fun isLocalUri(raw: String): Boolean =
            raw.startsWith("content://") || raw.startsWith("file://")

        fun isRemoteHttpsUrl(raw: String): Boolean =
            raw.startsWith("https://")
    }
}
