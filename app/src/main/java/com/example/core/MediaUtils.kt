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

object MediaUtils {
    private const val MAX_IMAGE_DIM = 800
    private const val MAX_BASE64_CHARS = 300_000
    private const val MAX_DOC_BYTES = 280_000L

    fun encodeImageUriToDataUri(context: Context, uri: Uri): String? {
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
            var quality = 78
            var encoded: String
            do {
                val out = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
                encoded = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                quality -= 12
            } while (encoded.length > MAX_BASE64_CHARS && quality >= 30)

            if (encoded.length > MAX_BASE64_CHARS) null else encoded
        } catch (_: Exception) {
            null
        }
    }

    fun decodeDataUriToBitmap(dataUri: String?): Bitmap? {
        if (dataUri.isNullOrBlank()) return null
        return try {
            val base64Part = dataUri.substringAfter("base64,", dataUri)
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun readDocumentFromUri(context: Context, uri: Uri): PickedDocument? {
        return try {
            val resolver = context.contentResolver
            var name = "document_pedagogique"
            var size = 0L
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
                }
            }
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            if (size > MAX_DOC_BYTES) {
                return PickedDocument(
                    fileName = name,
                    mimeType = mimeType,
                    sizeBytes = size,
                    contentBody = "",
                    isTooLarge = true
                )
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val actualSize = if (size > 0) size else bytes.size.toLong()
            if (actualSize > MAX_DOC_BYTES) {
                return PickedDocument(name, mimeType, actualSize, "", true)
            }
            val body = if (mimeType.startsWith("text/")) {
                bytes.toString(Charsets.UTF_8)
            } else {
                "base64:" + Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
            PickedDocument(
                fileName = name,
                mimeType = mimeType,
                sizeBytes = actualSize,
                contentBody = body.take(400_000),
                isTooLarge = false
            )
        } catch (_: Exception) {
            null
        }
    }
}
