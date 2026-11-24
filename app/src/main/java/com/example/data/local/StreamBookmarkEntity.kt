package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_bookmarks")
data class StreamBookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val category: String = "Page", // "Page" or "Direct Stream"
    val createdAt: Long = System.currentTimeMillis()
)
