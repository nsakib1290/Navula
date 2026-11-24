package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceUrl: String,
    val destinationPath: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = STATUS_QUEUED, // QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val selectedVariantUrl: String? = null,
    val formatLabel: String = "MP4",
    val qualityLabel: String? = null,
    val mimeType: String? = null,
    val fileExtension: String = "mp4",
    val audioLabel: String? = null,
    val subtitleUrl: String? = null,
    val subtitlePath: String? = null,
    val subtitleLanguage: String? = null,
    val providerHost: String? = null,
    val supportsPauseResume: Boolean = false,
    val updatedAt: Long = createdAt
) {
    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }

    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val isActive: Boolean
        get() = status == STATUS_QUEUED || status == STATUS_DOWNLOADING
}
