package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class ExtractedVideoMetadata(
    val title: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val thumbnailPath: String?
)

object VideoMetadataHelper {

    suspend fun extractMetadata(
        context: Context,
        uri: Uri,
        videoId: String,
        fallbackTitle: String? = null
    ): ExtractedVideoMetadata = withContext(Dispatchers.IO) {
        var displayName = fallbackTitle ?: "Offline Video"
        var sizeBytes = 0L
        var mimeType = "video/mp4"

        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            cursor.getString(nameIdx)?.let { raw ->
                                displayName = raw.substringBeforeLast('.')
                            }
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0) {
                            sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }
                context.contentResolver.getType(uri)?.let { mimeType = it }
            }
        } else if (uri.scheme == "file" && uri.path != null) {
            val file = File(uri.path!!)
            if (file.exists()) {
                sizeBytes = file.length()
                if (fallbackTitle.isNullOrBlank()) {
                    displayName = file.nameWithoutExtension
                }
            }
        }

        var durationMs = 0L
        var thumbPath: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L

            if (fallbackTitle.isNullOrBlank()) {
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (!metaTitle.isNullOrBlank()) {
                    displayName = metaTitle
                }
            }

            val captureTimeUs = if (durationMs > 3000L) 2_000_000L else 500_000L
            val bitmap = retriever.getFrameAtTime(captureTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (bitmap != null) {
                thumbPath = saveThumbnailBitmap(context, videoId, bitmap)
                bitmap.recycle()
            }
        } catch (_: Exception) {
            // Gracefully handle unsupported codecs or restricted URIs
        } finally {
            runCatching { retriever.release() }
        }

        ExtractedVideoMetadata(
            title = displayName.ifBlank { "Offline Video" },
            mimeType = mimeType,
            fileSizeBytes = sizeBytes,
            durationMs = durationMs,
            thumbnailPath = thumbPath
        )
    }

    private fun saveThumbnailBitmap(context: Context, videoId: String, bitmap: Bitmap): String? {
        return runCatching {
            val thumbsDir = File(context.filesDir, "thumbs").apply { mkdirs() }
            val outFile = File(thumbsDir, "$videoId.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            outFile.absolutePath
        }.getOrNull()
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "Unknown size"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }
}
