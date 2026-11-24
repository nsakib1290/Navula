package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.security.ContentBlockerEngine
import com.example.security.ShieldConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.spacedomDataStore: DataStore<Preferences> by preferencesDataStore(name = "spacedom_prefs")

enum class AppThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System");

    companion object {
        fun fromStored(value: String?): AppThemeMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DARK
        }
    }
}

data class UserPreferences(
    val adGuardEnabled: Boolean = true,
    val blockAdsEnabled: Boolean = true,
    val blockTrackersEnabled: Boolean = true,
    val blockMalwareEnabled: Boolean = true,
    val blockPopupsEnabled: Boolean = true,
    val cosmeticFilteringEnabled: Boolean = true,
    val blockRedirectsEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val enabledFilterListIds: Set<String> = ContentBlockerEngine.DEFAULT_ENABLED_LIST_IDS,
    val customBlockedDomains: Set<String> = emptySet(),
    val whitelistedDomains: Set<String> = emptySet(),
    val desktopModeEnabled: Boolean = false,
    val autoResumeEnabled: Boolean = true,
    val showHomeFloatingControls: Boolean = false,
    val defaultPlaybackSpeed: Float = 1.0f,
    val lastVisitedUrl: String = SpacedomDatabase.DEFAULT_STARTUP_URL,
    val lastProviderId: String = SpacedomDatabase.DEFAULT_PROVIDER_ID
) {
    fun toShieldConfig(): ShieldConfig = ShieldConfig(
        masterEnabled = adGuardEnabled,
        blockAds = blockAdsEnabled,
        blockTrackers = blockTrackersEnabled,
        blockMalware = blockMalwareEnabled,
        blockPopups = blockPopupsEnabled,
        cosmeticFiltering = cosmeticFilteringEnabled,
        enabledListIds = enabledFilterListIds,
        customBlockedDomains = customBlockedDomains,
        whitelistedDomains = whitelistedDomains
    )
}

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val AD_GUARD = booleanPreferencesKey("ad_guard_enabled")
        val BLOCK_ADS = booleanPreferencesKey("block_ads_enabled")
        val BLOCK_TRACKERS = booleanPreferencesKey("block_trackers_enabled")
        val BLOCK_MALWARE = booleanPreferencesKey("block_malware_enabled")
        val BLOCK_POPUPS = booleanPreferencesKey("block_popups_enabled")
        val COSMETIC_FILTER = booleanPreferencesKey("cosmetic_filtering_enabled")
        val BLOCK_REDIRECTS = booleanPreferencesKey("block_redirects_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ENABLED_FILTER_LISTS = stringSetPreferencesKey("enabled_filter_lists")
        val CUSTOM_BLOCKED_DOMAINS = stringSetPreferencesKey("custom_blocked_domains")
        val WHITELISTED_DOMAINS = stringSetPreferencesKey("whitelisted_domains")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode_enabled")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume_enabled")
        val SHOW_HOME_FLOATING_CONTROLS = booleanPreferencesKey("show_home_floating_controls")
        val DEFAULT_SPEED = floatPreferencesKey("default_playback_speed")
        val LAST_URL = stringPreferencesKey("last_visited_url")
        val LAST_PROVIDER_ID = stringPreferencesKey("last_provider_id")
    }

    val preferencesFlow: Flow<UserPreferences> = context.spacedomDataStore.data.map { prefs ->
        val rawLastUrl = prefs[Keys.LAST_URL]
        val storedProviderId = prefs[Keys.LAST_PROVIDER_ID] ?: SpacedomDatabase.DEFAULT_PROVIDER_ID
        val normalizedLastUrl = when {
            rawLastUrl.isNullOrBlank() -> SpacedomDatabase.DEFAULT_STARTUP_URL
            storedProviderId == SpacedomDatabase.DEFAULT_PROVIDER_ID &&
                (rawLastUrl.equals(SpacedomDatabase.LEGACY_DEFAULT_URL, ignoreCase = true) ||
                    rawLastUrl.equals("https://7movies.ws", ignoreCase = true)) -> SpacedomDatabase.DEFAULT_STARTUP_URL
            else -> rawLastUrl
        }
        UserPreferences(
            adGuardEnabled = prefs[Keys.AD_GUARD] ?: true,
            blockAdsEnabled = prefs[Keys.BLOCK_ADS] ?: true,
            blockTrackersEnabled = prefs[Keys.BLOCK_TRACKERS] ?: true,
            blockMalwareEnabled = prefs[Keys.BLOCK_MALWARE] ?: true,
            blockPopupsEnabled = prefs[Keys.BLOCK_POPUPS] ?: true,
            cosmeticFilteringEnabled = prefs[Keys.COSMETIC_FILTER] ?: true,
            blockRedirectsEnabled = prefs[Keys.BLOCK_REDIRECTS] ?: true,
            themeMode = AppThemeMode.fromStored(prefs[Keys.THEME_MODE]),
            enabledFilterListIds = prefs[Keys.ENABLED_FILTER_LISTS] ?: ContentBlockerEngine.DEFAULT_ENABLED_LIST_IDS,
            customBlockedDomains = prefs[Keys.CUSTOM_BLOCKED_DOMAINS] ?: emptySet(),
            whitelistedDomains = prefs[Keys.WHITELISTED_DOMAINS] ?: emptySet(),
            desktopModeEnabled = prefs[Keys.DESKTOP_MODE] ?: false,
            autoResumeEnabled = prefs[Keys.AUTO_RESUME] ?: true,
            showHomeFloatingControls = prefs[Keys.SHOW_HOME_FLOATING_CONTROLS] ?: false,
            defaultPlaybackSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            lastVisitedUrl = normalizedLastUrl,
            lastProviderId = prefs[Keys.LAST_PROVIDER_ID] ?: SpacedomDatabase.DEFAULT_PROVIDER_ID
        )
    }

    suspend fun setAdGuardEnabled(enabled: Boolean) {
        context.spacedomDataStore.edit { it[Keys.AD_GUARD] = enabled }
    }

    suspend fun setBlockAdsEnabled(enabled: Boolean) {
        context.spacedomDataStore.edit { it[Keys.BLOCK_ADS] = enabled }
    }

    suspend fun setBlockTrackersEnabled(enabled: Boolean) {
        context.spacedomDataStore.edit { it[Keys.BLOCK_TRACKERS] = enabled }
    }

    suspend fun setBlockMalwareEnabled(enabled: Boolean) {
        context.spacedomDataStore.edit { it[Keys.BLOCK_MALWARE] = enabled }
    }

    suspend fun setBlockPopupsEnabled(enabled: Boolean) {
        context.spacedomDataStore.edit { it[Keys.BLOCK_POPUPS] = enabled }
    }

    suspend fun setCosmeticFilteringEnabled(enabled: Boolean) {
        context.spacedomDataStore.edit { it[Keys.COSMETIC_FILTER] = enabled }
    }

    suspend fun setBlockRedirectsEnabled(enabled: Boolean) {
        context.spacedomDataStore.edit { it[Keys.BLOCK_REDIRECTS] = enabled }
    }

    suspend fun resetFilterSettings() {
        context.spacedomDataStore.edit { prefs ->
            prefs[Keys.AD_GUARD] = true
            prefs[Keys.BLOCK_ADS] = true
            prefs[Keys.BLOCK_TRACKERS] = true
            prefs[Keys.BLOCK_MALWARE] = true
            prefs[Keys.BLOCK_POPUPS] = true
            prefs[Keys.COSMETIC_FILTER] = true
            prefs[Keys.BLOCK_REDIRECTS] = true
            prefs[Keys.ENABLED_FILTER_LISTS] = ContentBlockerEngine.DEFAULT_ENABLED_LIST_IDS
            prefs[Keys.CUSTOM_BLOCKED_DOMAINS] = emptySet()
            prefs[Keys.WHITELISTED_DOMAINS] = emptySet()
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.spacedomDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setFilterListEnabled(listId: String, enabled: Boolean) {
        context.spacedomDataStore.edit { prefs ->
            val current = (prefs[Keys.ENABLED_FILTER_LISTS] ?: ContentBlockerEngine.DEFAULT_ENABLED_LIST_IDS).toMutableSet()
            if (enabled) current.add(listId) else current.remove(listId)
            prefs[Keys.ENABLED_FILTER_LISTS] = current
        }
    }

    suspend fun addCustomBlockedDomain(domain: String) {
        val clean = domain.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").substringBefore("/").trim('.')
        if (clean.isBlank()) return
        context.spacedomDataStore.edit { prefs ->
            val current = (prefs[Keys.CUSTOM_BLOCKED_DOMAINS] ?: emptySet()).toMutableSet()
            current.add(clean)
            prefs[Keys.CUSTOM_BLOCKED_DOMAINS] = current
        }
    }

    suspend fun removeCustomBlockedDomain(domain: String) {
        context.spacedomDataStore.edit { prefs ->
            val current = (prefs[Keys.CUSTOM_BLOCKED_DOMAINS] ?: emptySet()).toMutableSet()
            current.remove(domain)
            prefs[Keys.CUSTOM_BLOCKED_DOMAINS] = current
        }
    }

    suspend fun toggleWhitelistedDomain(domain: String) {
        val clean = domain.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").substringBefore("/").trim('.')
        if (clean.isBlank()) return
        context.spacedomDataStore.edit { prefs ->
            val current = (prefs[Keys.WHITELISTED_DOMAINS] ?: emptySet()).toMutableSet()
            if (current.contains(clean)) current.remove(clean) else current.add(clean)
            prefs[Keys.WHITELISTED_DOMAINS] = current
        }
    }

    suspend fun setDesktopModeEnabled(enabled: Boolean) {
        context.spacedomDataStore.edit { it[Keys.DESKTOP_MODE] = enabled }
    }

    suspend fun setAutoResumeEnabled(enabled: Boolean) {
        context.spacedomDataStore.edit { it[Keys.AUTO_RESUME] = enabled }
    }

    suspend fun setShowHomeFloatingControls(enabled: Boolean) {
        context.spacedomDataStore.edit { it[Keys.SHOW_HOME_FLOATING_CONTROLS] = enabled }
    }

    suspend fun setDefaultPlaybackSpeed(speed: Float) {
        context.spacedomDataStore.edit { it[Keys.DEFAULT_SPEED] = speed }
    }

    suspend fun setLastSelectedProvider(providerId: String) {
        val clean = providerId.trim()
        if (clean.isEmpty()) return
        context.spacedomDataStore.edit { prefs ->
            prefs[Keys.LAST_PROVIDER_ID] = clean
        }
    }

    suspend fun setLastVisitedUrl(url: String) {
        context.spacedomDataStore.edit { it[Keys.LAST_URL] = url }
    }

    suspend fun setLastSessionState(providerId: String?, url: String?) {
        if (providerId.isNullOrBlank()) return
        setLastSelectedProvider(providerId)
    }
}
