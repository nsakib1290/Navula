package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data class StreamRoute(val targetUrl: String = "https://spacedom.live/")

@Serializable
data object SearchRoute

@Serializable
data object OfflineRoute

@Serializable
data object HubRoute

@Serializable
data object SettingsRoute

@Serializable
data class PlayerRoute(
    val videoId: String? = null,
    val streamUrl: String? = null,
    val title: String = "Navula Cinema"
)
