package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_extensions")
data class ProviderExtensionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val baseUrl: String,
    val searchUrlTemplate: String = "",
    val manifestUrl: String? = null,
    val category: String = "Cloud", // "Built-in", "Manifest", "Custom"
    val isEnabled: Boolean = true,
    val isActive: Boolean = false,
    val installedAt: Long = System.currentTimeMillis()
) {
    fun buildSearchUrl(query: String): String {
        val encoded = android.net.Uri.encode(query.trim())
        if (searchUrlTemplate.contains("{query}")) {
            return searchUrlTemplate.replace("{query}", encoded)
        }
        val base = baseUrl.trimEnd('/')
        return "$base/?s=$encoded"
    }
}
