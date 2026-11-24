package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_videos")
data class OfflineVideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val uriString: String,
    val sourceType: String, // "DOWNLOADED" or "IMPORTED"
    val sourceUrl: String? = null,
    val mimeType: String = "video/mp4",
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val isFavorite: Boolean = false,
    val thumbnailPath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L,
    val subtitlePath: String? = null,
    val subtitleLanguage: String? = null,
    val qualityLabel: String? = null,
    val sourceHost: String? = null
)
