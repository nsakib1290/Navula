package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.local.AppThemeMode
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.OfflineVideoEntity
import com.example.data.local.ProviderExtensionEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SpacedomDao
import com.example.data.local.SpacedomDatabase
import com.example.data.local.StreamBookmarkEntity
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.media.DownloadLiveMetrics
import com.example.data.media.EnqueueDownloadOutcome
import com.example.data.media.PrefetchStreamInfo
import com.example.data.media.VideoDownloadManager
import com.example.data.media.VideoMetadataHelper
import com.example.data.provider.ProviderHealthResult
import com.example.data.provider.ProviderManifestParser
import com.example.security.BlockDecision
import com.example.security.ContentBlockerEngine
import com.example.security.FilterListStatus
import com.example.security.FilterUpdateWorker
import com.example.security.ShieldMetrics
import com.example.security.UrlSecurityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SpacedomRepository(private val context: Context) {

    init {
        val appCtx = context.applicationContext
        ContentBlockerEngine.initialize(appCtx)
        FilterUpdateWorker.schedulePeriodic(appCtx)
    }

    private val database = SpacedomDatabase.getInstance(context)
    private val dao: SpacedomDao = database.spacedomDao()
    private val prefsRepo = UserPreferencesRepository(context)
    private val downloadManager = VideoDownloadManager(context, dao)

    val allVideos: Flow<List<OfflineVideoEntity>> = dao.observeAllVideos()
    val continueWatching: Flow<List<OfflineVideoEntity>> = dao.observeContinueWatching()
    val allBookmarks: Flow<List<StreamBookmarkEntity>> = dao.observeAllBookmarks()
    val allDownloads: Flow<List<DownloadTaskEntity>> = dao.observeAllDownloads()
    val allProviders: Flow<List<ProviderExtensionEntity>> = dao.observeAllProviders()
    val activeProvider: Flow<ProviderExtensionEntity?> = dao.observeActiveProvider()
    val searchHistory: Flow<List<SearchHistoryEntity>> = dao.observeSearchHistory()
    val preferences: Flow<UserPreferences> = prefsRepo.preferencesFlow.onEach { prefs ->
        ContentBlockerEngine.updateConfig(prefs.toShieldConfig())
    }
    val prefetchedStreams: StateFlow<Map<String, PrefetchStreamInfo>> = downloadManager.prefetchedStreams
    val liveDownloadMetrics: StateFlow<Map<String, DownloadLiveMetrics>> = downloadManager.liveMetrics
    val shieldMetrics: StateFlow<ShieldMetrics> = ContentBlockerEngine.metrics
    val filterSubscriptionStatuses: StateFlow<Map<String, FilterListStatus>> = ContentBlockerEngine.subscriptionStatuses
    val isSyncingFilters: StateFlow<Boolean> = ContentBlockerEngine.isSyncing

    suspend fun ensureDefaultProvider() {
        val existingDefault = dao.getProviderById(SpacedomDatabase.DEFAULT_PROVIDER_ID)
        if (existingDefault == null) {
            val hasAnyActive = dao.getActiveProviderOnce() != null
            dao.upsertProvider(
                SpacedomDatabase.defaultSpacedomProvider().copy(isActive = !hasAnyActive)
            )
        } else if (existingDefault.baseUrl.equals(SpacedomDatabase.LEGACY_DEFAULT_URL, ignoreCase = true) ||
            existingDefault.baseUrl.equals("https://7movies.ws", ignoreCase = true)
        ) {
            dao.upsertProvider(
                existingDefault.copy(
                    name = "Spacedom Live",
                    description = "Built-in default Movie provider (spacedom.live)",
                    baseUrl = SpacedomDatabase.DEFAULT_STARTUP_URL,
                    searchUrlTemplate = "https://spacedom.live/?s={query}",
                    category = "Movie"
                )
            )
        }
        dao.insertProvidersIfMissing(SpacedomDatabase.curatedMovieAndSportsProviders())
        if (dao.getActiveProviderOnce() == null) {
            dao.activateProvider(SpacedomDatabase.DEFAULT_PROVIDER_ID)
        }
    }

    suspend fun getActiveProviderOnce(): ProviderExtensionEntity? = dao.getActiveProviderOnce()
    suspend fun getAllProvidersOnce(): List<ProviderExtensionEntity> = dao.getAllProvidersOnce()

    suspend fun getVideoById(id: String): OfflineVideoEntity? = dao.getVideoById(id)

    suspend fun importLocalVideo(uri: Uri): OfflineVideoEntity? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val existing = dao.getVideoByUri(uri.toString())
        if (existing != null) return@withContext existing

        val id = UUID.randomUUID().toString()
        val meta = VideoMetadataHelper.extractMetadata(
            context = context,
            uri = uri,
            videoId = id
        )

        val finalUriString = if (hasPersistedPermission(uri)) {
            uri.toString()
        } else {
            val copied = copyUriToPrivateVault(uri, id, meta.mimeType)
            copied?.let { Uri.fromFile(it).toString() } ?: uri.toString()
        }

        val entity = OfflineVideoEntity(
            id = id,
            title = meta.title,
            uriString = finalUriString,
            sourceType = "IMPORTED",
            mimeType = meta.mimeType,
            fileSizeBytes = meta.fileSizeBytes,
            durationMs = meta.durationMs,
            thumbnailPath = meta.thumbnailPath
        )
        dao.upsertVideo(entity)
        entity
    }

    private fun hasPersistedPermission(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    private fun copyUriToPrivateVault(uri: Uri, id: String, mimeType: String): File? {
        return runCatching {
            val ext = when {
                mimeType.contains("webm") -> "webm"
                mimeType.contains("matroska") || mimeType.contains("mkv") -> "mkv"
                mimeType.contains("quicktime") -> "mov"
                else -> "mp4"
            }
            val vaultDir = File(context.filesDir, "offline_videos").apply { mkdirs() }
            val destFile = File(vaultDir, "$id.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile else null
        }.getOrNull()
    }

    suspend fun updatePlaybackProgress(id: String, positionMs: Long, durationMs: Long) {
        val clampedPos = if (durationMs > 0L && positionMs >= durationMs - 2000L) 0L else positionMs.coerceAtLeast(0L)
        dao.updatePlaybackProgress(
            id = id,
            positionMs = clampedPos,
            durationMs = durationMs,
            playedAt = System.currentTimeMillis()
        )
    }

    suspend fun toggleFavorite(video: OfflineVideoEntity) {
        dao.setFavorite(video.id, !video.isFavorite)
    }

    suspend fun renameVideo(id: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            dao.renameVideo(id, trimmed)
        }
    }

    suspend fun deleteVideo(video: OfflineVideoEntity) = withContext(Dispatchers.IO) {
        if (video.uriString.startsWith("file://")) {
            Uri.parse(video.uriString).path?.let { path ->
                val file = File(path)
                runCatching {
                    if (file.name == "index.m3u8" && file.parentFile?.parentFile?.name == "offline_videos") {
                        file.parentFile?.deleteRecursively()
                    } else {
                        file.delete()
                    }
                }
            }
        }
        video.thumbnailPath?.let { thumb ->
            runCatching { File(thumb).delete() }
        }
        dao.deleteVideoById(video.id)
    }

    suspend fun prefetchStream(
        url: String,
        referer: String? = SpacedomDatabase.DEFAULT_STARTUP_URL,
        origin: String? = null,
        userAgent: String? = null
    ): Result<PrefetchStreamInfo> {
        return downloadManager.prefetchStream(
            rawUrl = url,
            referer = referer,
            origin = origin,
            userAgent = userAgent
        )
    }

    fun startVideoDownload(
        url: String,
        title: String? = null,
        referer: String? = SpacedomDatabase.DEFAULT_STARTUP_URL,
        origin: String? = null,
        userAgent: String? = null,
        preferredVariantUrl: String? = null,
        formatLabel: String? = null,
        qualityLabel: String? = null,
        audioLabel: String? = null,
        subtitleUrl: String? = null,
        subtitleLanguage: String? = null
    ): String {
        return downloadManager.enqueueDownload(
            url = url,
            suggestedTitle = title,
            referer = referer,
            origin = origin,
            userAgent = userAgent,
            preferredVariantUrl = preferredVariantUrl,
            formatLabel = formatLabel,
            qualityLabel = qualityLabel,
            audioLabel = audioLabel,
            subtitleUrl = subtitleUrl,
            subtitleLanguage = subtitleLanguage
        )
    }

    suspend fun startVideoDownloadChecked(
        url: String,
        title: String? = null,
        referer: String? = SpacedomDatabase.DEFAULT_STARTUP_URL,
        origin: String? = null,
        userAgent: String? = null,
        preferredVariantUrl: String? = null,
        formatLabel: String? = null,
        qualityLabel: String? = null,
        audioLabel: String? = null,
        subtitleUrl: String? = null,
        subtitleLanguage: String? = null
    ): EnqueueDownloadOutcome {
        return downloadManager.enqueueDownloadChecked(
            url = url,
            suggestedTitle = title,
            referer = referer,
            origin = origin,
            userAgent = userAgent,
            preferredVariantUrl = preferredVariantUrl,
            formatLabel = formatLabel,
            qualityLabel = qualityLabel,
            audioLabel = audioLabel,
            subtitleUrl = subtitleUrl,
            subtitleLanguage = subtitleLanguage
        )
    }

    fun pauseDownload(id: String) {
        downloadManager.pauseDownload(id)
    }

    fun resumeDownload(
        task: DownloadTaskEntity,
        referer: String? = SpacedomDatabase.DEFAULT_STARTUP_URL,
        origin: String? = null,
        userAgent: String? = null
    ): Boolean {
        return downloadManager.resumeDownload(
            task = task,
            referer = referer,
            origin = origin,
            userAgent = userAgent
        )
    }

    fun cancelDownload(id: String) {
        downloadManager.cancelDownload(id)
    }

    suspend fun deleteDownloadRecord(task: DownloadTaskEntity) = withContext(Dispatchers.IO) {
        if (task.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
            task.status == DownloadTaskEntity.STATUS_QUEUED ||
            task.status == DownloadTaskEntity.STATUS_PAUSED
        ) {
            downloadManager.cancelDownload(task.id)
        }
        dao.deleteDownloadById(task.id)
    }

    // Bookmarks
    suspend fun addOrUpdateBookmark(url: String, title: String, category: String = "Page") {
        val cleanUrl = UrlSecurityValidator.validatePublicHttpsUrl(url).getOrNull() ?: return
        dao.upsertBookmark(
            StreamBookmarkEntity(
                id = UUID.nameUUIDFromBytes(cleanUrl.toByteArray()).toString(),
                title = title.ifBlank { cleanUrl },
                url = cleanUrl,
                category = category
            )
        )
    }

    suspend fun removeBookmarkByUrl(url: String) {
        dao.deleteBookmarkByUrl(url)
    }

    suspend fun removeBookmarkById(id: String) {
        dao.deleteBookmarkById(id)
    }

    // Cloud Provider Extensions
    suspend fun installManifestRepository(sourceInput: String): Result<Int> {
        val result = ProviderManifestParser.fetchProvidersFromSource(sourceInput)
        if (result.isSuccess) {
            val existingById = dao.getAllProvidersOnce().associateBy { it.id }
            val merged = result.getOrThrow().map { incoming ->
                val prev = existingById[incoming.id]
                if (prev != null) {
                    incoming.copy(
                        isActive = prev.isActive,
                        baseUrl = incoming.baseUrl.ifBlank { prev.baseUrl },
                        searchUrlTemplate = incoming.searchUrlTemplate.ifBlank { prev.searchUrlTemplate },
                        installedAt = prev.installedAt
                    )
                } else {
                    incoming
                }
            }
            dao.upsertProviders(merged)
            return Result.success(merged.size)
        }
        return Result.failure(result.exceptionOrNull() ?: IllegalStateException("Failed to load manifest"))
    }

    suspend fun refreshInstalledRepositories(currentProviders: List<ProviderExtensionEntity>): Result<Int> {
        val manifestSources = currentProviders
            .mapNotNull { it.manifestUrl?.takeIf { url -> url.isNotBlank() } }
            .distinct()
        if (manifestSources.isEmpty()) {
            return Result.success(0)
        }
        var totalUpdated = 0
        var lastError: Throwable? = null
        for (sourceUrl in manifestSources) {
            val res = installManifestRepository(sourceUrl)
            if (res.isSuccess) {
                totalUpdated += res.getOrDefault(0)
            } else {
                lastError = res.exceptionOrNull()
            }
        }
        return if (totalUpdated > 0 || lastError == null) {
            Result.success(totalUpdated)
        } else {
            Result.failure(lastError)
        }
    }

    suspend fun checkProviderHealth(provider: ProviderExtensionEntity): ProviderHealthResult {
        if (provider.baseUrl.isBlank()) {
            return ProviderHealthResult(isReachable = false, message = "Mirror URL required")
        }
        val result = ProviderManifestParser.checkProviderHealth(provider.baseUrl)
        // If redirected cleanly to a working HTTPS mirror, update baseUrl automatically
        val resolved = result.resolvedUrl
        if (result.isReachable && !resolved.isNullOrBlank() &&
            !resolved.equals(provider.baseUrl, ignoreCase = true)
        ) {
            val resolvedHost = Uri.parse(resolved).host
            val oldHost = Uri.parse(provider.baseUrl).host
            if (!resolvedHost.isNullOrBlank() && !resolvedHost.equals(oldHost, ignoreCase = true)) {
                val mirrorBase = "https://$resolvedHost/"
                dao.upsertProvider(
                    provider.copy(
                        baseUrl = mirrorBase,
                        searchUrlTemplate = if (provider.searchUrlTemplate.contains("{query}") && oldHost != null) {
                            provider.searchUrlTemplate.replace(oldHost, resolvedHost)
                        } else {
                            "${mirrorBase.trimEnd('/')}/?s={query}"
                        }
                    )
                )
            }
        }
        return result
    }

    suspend fun addCustomProvider(
        name: String,
        baseUrl: String,
        searchTemplate: String,
        activateNow: Boolean = true
    ): Result<ProviderExtensionEntity> {
        val normalizedBase = UrlSecurityValidator.validatePublicHttpsUrl(baseUrl).getOrElse {
            return Result.failure(it)
        }
        val blockDecision = ContentBlockerEngine.evaluateProviderOrMediaUrl(normalizedBase)
        if (blockDecision.blocked) {
            ContentBlockerEngine.recordBlocked(blockDecision)
            return Result.failure(
                SecurityException("Blocked unsafe provider URL (${blockDecision.category?.label ?: "Shield"})")
            )
        }
        val cleanSearch = if (searchTemplate.trim().isBlank()) {
            "${normalizedBase.trimEnd('/')}/?s={query}"
        } else {
            UrlSecurityValidator.validatePublicHttpsUrl(searchTemplate.trim().replace("{query}", "test"))
                .map { searchTemplate.trim().replace("http://", "https://") }
                .getOrElse { "${normalizedBase.trimEnd('/')}/?s={query}" }
        }
        val entity = ProviderExtensionEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { Uri.parse(normalizedBase).host ?: "Custom Provider" },
            version = "1.0.0",
            description = "Custom cloud provider extension",
            baseUrl = normalizedBase,
            searchUrlTemplate = cleanSearch,
            category = "Custom",
            isEnabled = true,
            isActive = false
        )
        dao.upsertProvider(entity)
        if (activateNow) {
            dao.activateProvider(entity.id)
        }
        return Result.success(entity)
    }

    suspend fun updateProviderUrl(
        provider: ProviderExtensionEntity,
        newBaseUrl: String,
        newSearchTemplate: String,
        newName: String? = null
    ): Result<ProviderExtensionEntity> {
        val normalizedBase = UrlSecurityValidator.validatePublicHttpsUrl(newBaseUrl).getOrElse {
            return Result.failure(it)
        }
        val blockDecision = ContentBlockerEngine.evaluateProviderOrMediaUrl(normalizedBase)
        if (blockDecision.blocked) {
            ContentBlockerEngine.recordBlocked(blockDecision)
            return Result.failure(
                SecurityException("Blocked unsafe provider URL (${blockDecision.category?.label ?: "Shield"})")
            )
        }
        val cleanSearch = newSearchTemplate.trim().ifBlank {
            "${normalizedBase.trimEnd('/')}/?s={query}"
        }.let { if (it.startsWith("http://")) "https://" + it.substring(7) else it }
        val cleanName = newName?.trim()?.takeIf { it.isNotBlank() } ?: provider.name
        val updated = provider.copy(
            name = cleanName,
            baseUrl = normalizedBase,
            searchUrlTemplate = cleanSearch,
            description = if (provider.id == SpacedomDatabase.DEFAULT_PROVIDER_ID) {
                "Built-in streaming provider ($normalizedBase)"
            } else {
                provider.description.replace("Needs Mirror URL", "Configured mirror")
            }
        )
        dao.upsertProvider(updated)
        return Result.success(updated)
    }

    suspend fun resetDefaultProvider(): ProviderExtensionEntity {
        val restored = SpacedomDatabase.defaultSpacedomProvider().copy(isActive = true)
        dao.upsertProvider(restored)
        dao.activateProvider(restored.id)
        return restored
    }

    suspend fun activateProvider(id: String) {
        dao.activateProvider(id)
    }

    suspend fun setProviderEnabled(id: String, enabled: Boolean) {
        dao.setProviderEnabled(id, enabled)
    }

    suspend fun deleteProvider(provider: ProviderExtensionEntity) {
        if (provider.id == SpacedomDatabase.DEFAULT_PROVIDER_ID) return
        val wasActive = provider.isActive
        dao.deleteProviderById(provider.id)
        if (wasActive) {
            dao.activateProvider(SpacedomDatabase.DEFAULT_PROVIDER_ID)
        }
    }

    // Search History
    suspend fun recordSearch(query: String, provider: ProviderExtensionEntity?) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val provId = provider?.id ?: SpacedomDatabase.DEFAULT_PROVIDER_ID
        val provName = provider?.name ?: "7Movies"
        val id = UUID.nameUUIDFromBytes("${clean.lowercase()}::$provId".toByteArray()).toString()
        dao.upsertSearchHistory(
            SearchHistoryEntity(
                id = id,
                query = clean,
                providerId = provId,
                providerName = provName,
                searchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSearchHistory(id: String) {
        dao.deleteSearchHistoryById(id)
    }

    suspend fun clearAllSearchHistory() {
        dao.clearAllSearchHistory()
    }

    // Preferences & Internal Shield
    suspend fun setAdGuardEnabled(enabled: Boolean) = prefsRepo.setAdGuardEnabled(enabled)
    suspend fun setBlockAdsEnabled(enabled: Boolean) = prefsRepo.setBlockAdsEnabled(enabled)
    suspend fun setBlockTrackersEnabled(enabled: Boolean) = prefsRepo.setBlockTrackersEnabled(enabled)
    suspend fun setBlockMalwareEnabled(enabled: Boolean) = prefsRepo.setBlockMalwareEnabled(enabled)
    suspend fun setBlockPopupsEnabled(enabled: Boolean) = prefsRepo.setBlockPopupsEnabled(enabled)
    suspend fun setCosmeticFilteringEnabled(enabled: Boolean) = prefsRepo.setCosmeticFilteringEnabled(enabled)
    suspend fun setBlockRedirectsEnabled(enabled: Boolean) = prefsRepo.setBlockRedirectsEnabled(enabled)
    suspend fun resetFilterSettings() {
        prefsRepo.resetFilterSettings()
        ContentBlockerEngine.resetToDefaults()
    }
    suspend fun setThemeMode(mode: AppThemeMode) = prefsRepo.setThemeMode(mode)
    suspend fun setFilterListEnabled(listId: String, enabled: Boolean) = prefsRepo.setFilterListEnabled(listId, enabled)
    suspend fun addCustomBlockedDomain(domain: String) = prefsRepo.addCustomBlockedDomain(domain)
    suspend fun removeCustomBlockedDomain(domain: String) = prefsRepo.removeCustomBlockedDomain(domain)
    suspend fun toggleWhitelistedDomain(domain: String) = prefsRepo.toggleWhitelistedDomain(domain)
    suspend fun syncFilterLists(): Result<Int> = ContentBlockerEngine.syncEnabledSubscriptions(context)
    fun recordBlockedRequest(decision: BlockDecision) = ContentBlockerEngine.recordBlocked(decision)
    suspend fun setDesktopModeEnabled(enabled: Boolean) = prefsRepo.setDesktopModeEnabled(enabled)
    suspend fun setAutoResumeEnabled(enabled: Boolean) = prefsRepo.setAutoResumeEnabled(enabled)
    suspend fun setShowHomeFloatingControls(enabled: Boolean) = prefsRepo.setShowHomeFloatingControls(enabled)
    suspend fun setDefaultPlaybackSpeed(speed: Float) = prefsRepo.setDefaultPlaybackSpeed(speed)
    suspend fun setLastVisitedUrl(url: String) = prefsRepo.setLastVisitedUrl(url)
    suspend fun setLastSelectedProvider(providerId: String) = prefsRepo.setLastSelectedProvider(providerId)
    suspend fun setLastSessionState(providerId: String?, url: String?) = prefsRepo.setLastSessionState(providerId, url)
}
