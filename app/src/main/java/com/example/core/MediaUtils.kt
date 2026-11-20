package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class PickedDocument(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentBody: String,
    val isTooLarge: Boolean
)

data class PickedUploadFile(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
    val textPreview: String,
    val isTooLarge: Boolean
)

object MediaUtils {
    private const val MAX_IMAGE_DIM = 1280
    const val MAX_IMAGE_UPLOAD_BYTES = 5_000_000L
    const val MAX_ATTACHMENT_UPLOAD_BYTES = 10_000_000L
    const val MAX_RESOURCE_UPLOAD_BYTES = 20_000_000L
    private const val MAX_TEXT_PREVIEW_CHARS = 50_000

    fun prepareImageBytes(
        context: Context,
        uri: Uri,
        maxBytes: Long = MAX_IMAGE_UPLOAD_BYTES
    ): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            val scale = max(original.width, original.height).toFloat() / MAX_IMAGE_DIM.toFloat()
            val resized = if (scale > 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width / scale).toInt().coerceAtLeast(1),
                    (original.height / scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                original
            }
            var quality = 85
            var compressed: ByteArray
            do {
                val out = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
                compressed = out.toByteArray()
                quality -= 12
            } while (compressed.size.toLong() > maxBytes && quality >= 35)

            if (compressed.size.toLong() > maxBytes) null else compressed
        } catch (_: Exception) {
            null
        }
    }

    fun inspectUriMeta(
        context: Context,
        uri: Uri,
        maxBytes: Long = MAX_ATTACHMENT_UPLOAD_BYTES
    ): PickedDocument? {
        return try {
            val resolver = context.contentResolver
            var name = "document_pedagogique"
            var size = 0L
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            if (size > maxBytes) {
                return PickedDocument(
                    fileName = name,
                    mimeType = mimeType,
                    sizeBytes = size,
                    contentBody = "",
                    isTooLarge = true
                )
            }
            val actualSize = if (size > 0L) {
                size
            } else {
                resolver.openInputStream(uri)?.use { stream ->
                    var total = 0L
                    val buf = ByteArray(8192)
                    while (true) {
                        val read = stream.read(buf)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) break
                    }
                    total
                } ?: 0L
            }
            if (actualSize > maxBytes) {
                return PickedDocument(name, mimeType, actualSize, "", true)
            }
            val textBody = if (mimeType.startsWith("text/") && actualSize <= 512_000L) {
                resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8).take(MAX_TEXT_PREVIEW_CHARS) }.orEmpty()
            } else {
                ""
            }
            PickedDocument(
                fileName = name,
                mimeType = mimeType,
                sizeBytes = actualSize,
                contentBody = textBody,
                isTooLarge = false
            )
        } catch (_: Exception) {
            null
        }
    }

    fun readUploadableFile(
        context: Context,
        uri: Uri,
        maxBytes: Long = MAX_ATTACHMENT_UPLOAD_BYTES
    ): PickedUploadFile? {
        return try {
            val resolver = context.contentResolver
            var name = "document_pedagogique"
            var size = 0L
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            if (size > maxBytes) {
                return PickedUploadFile(name, mimeType, size, ByteArray(0), "", true)
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val actualSize = if (size > 0L) size else bytes.size.toLong()
            if (actualSize > maxBytes || bytes.size.toLong() > maxBytes) {
                return PickedUploadFile(name, mimeType, actualSize, ByteArray(0), "", true)
            }
            val textPreview = if (mimeType.startsWith("text/")) {
                bytes.toString(Charsets.UTF_8).take(MAX_TEXT_PREVIEW_CHARS)
            } else {
                ""
            }
            PickedUploadFile(
                fileName = name,
                mimeType = mimeType,
                sizeBytes = actualSize,
                bytes = bytes,
                textPreview = textPreview,
                isTooLarge = false
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Read-only fallback decoder for any legacy Base64 data URIs stored prior to Cloud Storage migration. */
    fun decodeDataUriToBitmap(dataUri: String?): Bitmap? {
        if (dataUri.isNullOrBlank() || dataUri.startsWith("http://") || dataUri.startsWith("https://")) return null
        return try {
            val base64Part = dataUri.substringAfter("base64,", dataUri)
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
