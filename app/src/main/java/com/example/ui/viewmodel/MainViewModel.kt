package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SpacedomRepository
import com.example.data.local.AppThemeMode
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.OfflineVideoEntity
import com.example.data.local.ProviderExtensionEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SpacedomDatabase
import com.example.data.local.StreamBookmarkEntity
import com.example.data.local.UserPreferences
import com.example.data.media.DownloadLiveMetrics
import com.example.data.media.PrefetchStreamInfo
import com.example.data.provider.ProviderHealthResult
import com.example.data.provider.ProviderManifestParser
import com.example.security.BlockDecision
import com.example.security.ContentBlockerEngine
import com.example.security.FilterListStatus
import com.example.security.ShieldMetrics
import com.example.security.StreamRedirectGuard
import com.example.security.UrlSecurityValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OfflineFilter(val label: String) {
    ALL("All"),
    DOWNLOADED("Downloaded"),
    IMPORTED("Imported"),
    FAVORITES("Favorites")
}

enum class OfflineSort(val label: String) {
    RECENT("Recent"),
    TITLE("Title"),
    DURATION("Duration"),
    SIZE("Size")
}

data class DetectedStream(
    val url: String,
    val title: String,
    val formatLabel: String,
    val referer: String? = null,
    val origin: String? = null,
    val detectedAt: Long = System.currentTimeMillis()
)

data class DetectedSubtitleTrack(
    val url: String,
    val label: String,
    val language: String? = null,
    val format: String = "VTT",
    val detectedAt: Long = System.currentTimeMillis()
)

data class StreamBrowserState(
    val currentUrl: String = SpacedomDatabase.DEFAULT_STARTUP_URL,
    val pageTitle: String = "Navula",
    val progress: Int = 0,
    val isLoading: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val hasError: Boolean = false,
    val errorDescription: String? = null,
    val blockedCount: Int = 0,
    val detectedMediaUrl: String? = null,
    val detectedStreams: List<DetectedStream> = emptyList(),
    val detectedSubtitles: List<DetectedSubtitleTrack> = emptyList(),
    val activeUserAgent: String? = null,
    val isAutoSwitching: Boolean = false
)

data class CompletedDownloadNotice(
    val taskId: String,
    val title: String,
    val fileSizeBytes: Long,
    val completedAt: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpacedomRepository(application.applicationContext)

    private val _startupResolvedUrl = MutableStateFlow<String?>(null)
    val startupResolvedUrl: StateFlow<String?> = _startupResolvedUrl.asStateFlow()

    private val _startupDesktopMode = MutableStateFlow<Boolean?>(null)
    val startupDesktopMode: StateFlow<Boolean?> = _startupDesktopMode.asStateFlow()

    @Volatile
    private var selectedProviderSnapshot: ProviderExtensionEntity? = null

    private val failoverExcludedIds = LinkedHashSet<String>()
    private var failoverAttempts = 0
    @Volatile
    private var isHandlingFailover = false

    init {
        viewModelScope.launch {
            repository.ensureDefaultProvider()
            val prefs = runCatching { repository.preferences.first() }.getOrDefault(UserPreferences())
            val allProvs = runCatching { repository.getAllProvidersOnce() }.getOrDefault(emptyList())
            val dbActive = runCatching { repository.getActiveProviderOnce() }.getOrNull()

            val (persistedActive, safeResolved) = resolveProviderHomeStartupUrl(
                dbActive = dbActive,
                allProviders = allProvs,
                persistedProviderId = prefs.lastProviderId
            )

            if (persistedActive != null && dbActive?.id != persistedActive.id) {
                repository.activateProvider(persistedActive.id)
            }
            if (persistedActive != null && prefs.lastProviderId != persistedActive.id) {
                repository.setLastSelectedProvider(persistedActive.id)
            }
            selectedProviderSnapshot = persistedActive

            // Reset temporary session / playback / WebView state on cold relaunch;
            // always start at the active provider's configured Home URL (never a movie/episode page).
            savedWebViewState = null
            _pendingStreamUrl.value = null
            _isStreamFullscreen.value = false
            _startupDesktopMode.value = prefs.desktopModeEnabled
            _streamState.value = StreamBrowserState(
                currentUrl = safeResolved,
                pageTitle = persistedActive?.name?.ifBlank { "Navula" } ?: "Navula",
                isLoading = true
            )
            _startupResolvedUrl.value = safeResolved
        }
        viewModelScope.launch {
            var initialized = false
            val prevStatuses = mutableMapOf<String, String>()
            repository.allDownloads.collect { list ->
                if (!initialized) {
                    list.forEach { prevStatuses[it.id] = it.status }
                    initialized = true
                } else {
                    list.forEach { task ->
                        val prev = prevStatuses[task.id]
                        if (task.status == DownloadTaskEntity.STATUS_COMPLETED &&
                            prev != DownloadTaskEntity.STATUS_COMPLETED
                        ) {
                            val notice = CompletedDownloadNotice(
                                taskId = task.id,
                                title = task.title,
                                fileSizeBytes = maxOf(task.downloadedBytes, task.totalBytes)
                            )
                            _latestCompletedDownload.value = notice
                            _completedDownloadEvents.tryEmit(notice)
                        }
                        prevStatuses[task.id] = task.status
                    }
                }
            }
        }
    }

    val preferences: StateFlow<UserPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val allVideos: StateFlow<List<OfflineVideoEntity>> = repository.allVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<OfflineVideoEntity>> = repository.continueWatching
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<StreamBookmarkEntity>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadTaskEntity>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val providers: StateFlow<List<ProviderExtensionEntity>> = repository.allProviders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProvider: StateFlow<ProviderExtensionEntity?> = repository.activeProvider
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SpacedomDatabase.defaultSpacedomProvider()
        )

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = repository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prefetchedStreams: StateFlow<Map<String, PrefetchStreamInfo>> = repository.prefetchedStreams
    val liveDownloadMetrics: StateFlow<Map<String, DownloadLiveMetrics>> = repository.liveDownloadMetrics
    val shieldMetrics: StateFlow<ShieldMetrics> = repository.shieldMetrics
    val filterSubscriptionStatuses: StateFlow<Map<String, FilterListStatus>> = repository.filterSubscriptionStatuses
    val isSyncingFilters: StateFlow<Boolean> = repository.isSyncingFilters

    private val _prefetchingUrls = MutableStateFlow<Set<String>>(emptySet())
    val prefetchingUrls: StateFlow<Set<String>> = _prefetchingUrls.asStateFlow()

    private val _providerHealthMap = MutableStateFlow<Map<String, ProviderHealthResult>>(emptyMap())
    val providerHealthMap: StateFlow<Map<String, ProviderHealthResult>> = _providerHealthMap.asStateFlow()

    private val _testingProviderIds = MutableStateFlow<Set<String>>(emptySet())
    val testingProviderIds: StateFlow<Set<String>> = _testingProviderIds.asStateFlow()

    private val _isStreamFullscreen = MutableStateFlow(false)
    val isStreamFullscreen: StateFlow<Boolean> = _isStreamFullscreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(OfflineFilter.ALL)
    val selectedFilter: StateFlow<OfflineFilter> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(OfflineSort.RECENT)
    val selectedSort: StateFlow<OfflineSort> = _selectedSort.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isInstallingProvider = MutableStateFlow(false)
    val isInstallingProvider: StateFlow<Boolean> = _isInstallingProvider.asStateFlow()

    private val _streamState = MutableStateFlow(StreamBrowserState())
    val streamState: StateFlow<StreamBrowserState> = _streamState.asStateFlow()

    private val _pendingStreamUrl = MutableStateFlow<String?>(null)
    val pendingStreamUrl: StateFlow<String?> = _pendingStreamUrl.asStateFlow()

    private val _homeRefreshTrigger = MutableStateFlow(0)
    val homeRefreshTrigger: StateFlow<Int> = _homeRefreshTrigger.asStateFlow()

    private val _homeResetTrigger = MutableStateFlow(0)
    val homeResetTrigger: StateFlow<Int> = _homeResetTrigger.asStateFlow()

    private val _openPluginSheetTrigger = MutableStateFlow(0)
    val openPluginSheetTrigger: StateFlow<Int> = _openPluginSheetTrigger.asStateFlow()

    private val _cinemaFullscreenTrigger = MutableStateFlow(0)
    val cinemaFullscreenTrigger: StateFlow<Int> = _cinemaFullscreenTrigger.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _completedDownloadEvents = MutableSharedFlow<CompletedDownloadNotice>(extraBufferCapacity = 8)
    val completedDownloadEvents: SharedFlow<CompletedDownloadNotice> = _completedDownloadEvents.asSharedFlow()

    private val _latestCompletedDownload = MutableStateFlow<CompletedDownloadNotice?>(null)
    val latestCompletedDownload: StateFlow<CompletedDownloadNotice?> = _latestCompletedDownload.asStateFlow()

    fun dismissCompletedDownloadNotice() {
        _latestCompletedDownload.value = null
    }

    @Volatile
    var savedWebViewState: Bundle? = null
        private set

    fun saveWebViewState(bundle: Bundle?) {
        savedWebViewState = bundle
    }

    fun clearSavedWebViewState() {
        savedWebViewState = null
    }

    val filteredVideos: StateFlow<List<OfflineVideoEntity>> = combine(
        allVideos,
        _searchQuery,
        _selectedFilter,
        _selectedSort
    ) { videos, query, filter, sort ->
        val trimmed = query.trim().lowercase()
        val filtered = videos.filter { video ->
            val matchesQuery = trimmed.isEmpty() || video.title.lowercase().contains(trimmed)
            val matchesFilter = when (filter) {
                OfflineFilter.ALL -> true
                OfflineFilter.DOWNLOADED -> video.sourceType == "DOWNLOADED"
                OfflineFilter.IMPORTED -> video.sourceType == "IMPORTED"
                OfflineFilter.FAVORITES -> video.isFavorite
            }
            matchesQuery && matchesFilter
        }
        when (sort) {
            OfflineSort.RECENT -> filtered.sortedByDescending { it.addedAt }
            OfflineSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            OfflineSort.DURATION -> filtered.sortedByDescending { it.durationMs }
            OfflineSort.SIZE -> filtered.sortedByDescending { it.fileSizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isCurrentUrlBookmarked: StateFlow<Boolean> = combine(
        bookmarks,
        _streamState
    ) { list, state ->
        list.any { it.url.equals(state.currentUrl, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: OfflineFilter) {
        _selectedFilter.value = filter
    }

    fun setSort(sort: OfflineSort) {
        _selectedSort.value = sort
    }

    fun setStreamFullscreen(fullscreen: Boolean) {
        _isStreamFullscreen.value = fullscreen
    }

    fun triggerHomePluginRefresh() {
        _homeRefreshTrigger.update { it + 1 }
    }

    /**
     * Navigates directly to the currently selected provider's actual Home Page,
     * clearing all temporary movie, episode, playback, and fullscreen states without switching providers.
     */
    fun navigateToCurrentProviderHome() {
        val currentProv = selectedProviderSnapshot
            ?: activeProvider.value?.takeIf { it.baseUrl.isNotBlank() }
        val candidateBase = currentProv?.baseUrl?.takeIf { it.isNotBlank() }
            ?: _startupResolvedUrl.value?.takeIf { it.isNotBlank() }
            ?: SpacedomDatabase.DEFAULT_STARTUP_URL
        val homeUrl = UrlSecurityValidator.validatePublicHttpsUrl(candidateBase)
            .getOrDefault(SpacedomDatabase.DEFAULT_STARTUP_URL)

        resetFailoverState()
        StreamRedirectGuard.resetHopHistory()
        savedWebViewState = null
        _isStreamFullscreen.value = false
        _pendingStreamUrl.value = homeUrl
        _streamState.update { current ->
            current.copy(
                currentUrl = homeUrl,
                pageTitle = currentProv?.name?.ifBlank { "Navula" } ?: "Navula",
                progress = 0,
                isLoading = true,
                canGoBack = false,
                canGoForward = false,
                hasError = false,
                errorDescription = null,
                isAutoSwitching = false,
                detectedMediaUrl = null,
                detectedStreams = emptyList(),
                detectedSubtitles = emptyList()
            )
        }
        _homeResetTrigger.update { it + 1 }
    }

    fun requestOpenDownloadPluginInHome() {
        _openPluginSheetTrigger.update { it + 1 }
    }

    fun consumeOpenPluginTrigger() {
        _openPluginSheetTrigger.value = 0
    }

    fun requestCinemaFullscreenOnHome() {
        _cinemaFullscreenTrigger.update { it + 1 }
    }

    fun consumeCinemaFullscreenTrigger() {
        _cinemaFullscreenTrigger.value = 0
    }

    fun requestLoadInHome(url: String) {
        val safe = UrlSecurityValidator.validatePublicHttpsUrl(url).getOrNull() ?: return
        _pendingStreamUrl.value = safe
        triggerHomePluginRefresh()
    }

    fun consumePendingStreamUrl() {
        _pendingStreamUrl.value = null
    }

    fun executeSearch(query: String, targetProvider: ProviderExtensionEntity? = activeProvider.value): String? {
        val clean = query.trim()
        if (clean.isEmpty()) return null
        val provider = targetProvider ?: activeProvider.value ?: SpacedomDatabase.defaultSpacedomProvider()
        if (provider.baseUrl.isBlank()) {
            _snackbarEvents.tryEmit("Set a mirror URL for \"${provider.name}\" in Hub first")
            return null
        }
        viewModelScope.launch {
            repository.recordSearch(clean, provider)
        }
        val searchUrl = provider.buildSearchUrl(clean)
        requestLoadInHome(searchUrl)
        return searchUrl
    }

    fun deleteSearchHistory(id: String) {
        viewModelScope.launch {
            repository.deleteSearchHistory(id)
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            repository.clearAllSearchHistory()
            _snackbarEvents.tryEmit("Search history cleared")
        }
    }

    fun installProviderManifest(sourceUrlOrRepo: String) {
        val clean = sourceUrlOrRepo.trim()
        if (clean.isEmpty()) {
            _snackbarEvents.tryEmit("Enter a manifest URL or GitHub owner/repo")
            return
        }
        viewModelScope.launch {
            _isInstallingProvider.value = true
            val result = repository.installManifestRepository(clean)
            _isInstallingProvider.value = false
            result.onSuccess { count ->
                _snackbarEvents.tryEmit("Installed $count cloud provider(s) securely")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to load manifest")
            }
        }
    }

    fun refreshAllInstalledRepos() {
        val current = providers.value
        val hasManifests = current.any { !it.manifestUrl.isNullOrBlank() }
        if (!hasManifests) {
            _snackbarEvents.tryEmit("Install a Vega, CloudStream, Stremio, Aniyomi, or Mega repo first")
            return
        }
        viewModelScope.launch {
            _isInstallingProvider.value = true
            val result = repository.refreshInstalledRepositories(current)
            _isInstallingProvider.value = false
            result.onSuccess { updated ->
                _snackbarEvents.tryEmit("Refreshed $updated repo extension(s)")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to refresh repositories")
            }
        }
    }

    fun testProviderConnection(provider: ProviderExtensionEntity) {
        viewModelScope.launch {
            _testingProviderIds.update { it + provider.id }
            val result = repository.checkProviderHealth(provider)
            _testingProviderIds.update { it - provider.id }
            _providerHealthMap.update { it + (provider.id to result) }
            if (result.isReachable) {
                _snackbarEvents.tryEmit("${provider.name}: ${result.message}")
            } else {
                _snackbarEvents.tryEmit("${provider.name}: ${result.message}")
            }
        }
    }

    fun addCustomProvider(name: String, baseUrl: String, searchTemplate: String) {
        if (baseUrl.isBlank()) {
            _snackbarEvents.tryEmit("Provider URL cannot be empty")
            return
        }
        viewModelScope.launch {
            val result = repository.addCustomProvider(name, baseUrl, searchTemplate, activateNow = true)
            result.onSuccess { added ->
                selectedProviderSnapshot = added
                resetFailoverState()
                savedWebViewState = null
                _streamState.update {
                    it.copy(
                        hasError = false,
                        errorDescription = null,
                        currentUrl = added.baseUrl,
                        pageTitle = added.name,
                        detectedMediaUrl = null,
                        detectedStreams = emptyList()
                    )
                }
                _pendingStreamUrl.value = added.baseUrl
                repository.setLastSelectedProvider(added.id)
                _snackbarEvents.tryEmit("Activated secure provider \"${added.name}\"")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Blocked insecure provider URL")
            }
        }
    }

    fun updateProviderUrl(
        provider: ProviderExtensionEntity,
        newBaseUrl: String,
        newSearchTemplate: String,
        newName: String? = null,
        activateAndOpen: Boolean = false
    ) {
        if (newBaseUrl.isBlank()) return
        viewModelScope.launch {
            val result = repository.updateProviderUrl(provider, newBaseUrl, newSearchTemplate, newName)
            result.onSuccess { updated ->
                if (activateAndOpen || provider.isActive || selectedProviderSnapshot?.id == updated.id) {
                    selectedProviderSnapshot = updated
                    resetFailoverState()
                    if (activateAndOpen) repository.activateProvider(updated.id)
                    savedWebViewState = null
                    _streamState.update {
                        it.copy(
                            hasError = false,
                            errorDescription = null,
                            currentUrl = updated.baseUrl,
                            pageTitle = updated.name,
                            detectedMediaUrl = null,
                            detectedStreams = emptyList()
                        )
                    }
                    _pendingStreamUrl.value = updated.baseUrl
                    repository.setLastSelectedProvider(updated.id)
                    triggerHomePluginRefresh()
                }
                _snackbarEvents.tryEmit("Updated \"${updated.name}\"")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Invalid or insecure provider URL")
            }
        }
    }

    fun resetDefaultProvider(openInHome: Boolean = true) {
        viewModelScope.launch {
            resetFailoverState()
            val restored = repository.resetDefaultProvider()
            selectedProviderSnapshot = restored
            savedWebViewState = null
            _streamState.update {
                it.copy(
                    hasError = false,
                    errorDescription = null,
                    currentUrl = restored.baseUrl,
                    pageTitle = restored.name,
                    detectedMediaUrl = null,
                    detectedStreams = emptyList()
                )
            }
            repository.setLastSelectedProvider(restored.id)
            if (openInHome) {
                _pendingStreamUrl.value = restored.baseUrl
                triggerHomePluginRefresh()
            }
            _snackbarEvents.tryEmit("Restored built-in provider to spacedom.live")
        }
    }

    fun activateAndOpenProvider(provider: ProviderExtensionEntity) {
        val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(provider.baseUrl).getOrNull()
        if (safeUrl == null) {
            _snackbarEvents.tryEmit("Please configure a valid HTTPS mirror URL for \"${provider.name}\" first")
            return
        }
        selectedProviderSnapshot = provider.copy(baseUrl = safeUrl, isActive = true)
        resetFailoverState()
        StreamRedirectGuard.resetHopHistory()
        savedWebViewState = null
        _isStreamFullscreen.value = false
        _streamState.update {
            it.copy(
                hasError = false,
                errorDescription = null,
                isAutoSwitching = false,
                isLoading = true,
                progress = 0,
                canGoBack = false,
                canGoForward = false,
                currentUrl = safeUrl,
                pageTitle = provider.name,
                detectedMediaUrl = null,
                detectedStreams = emptyList()
            )
        }
        _pendingStreamUrl.value = safeUrl
        triggerHomePluginRefresh()
        viewModelScope.launch {
            repository.activateProvider(provider.id)
            repository.setLastSelectedProvider(provider.id)
            _snackbarEvents.tryEmit("Switched to ${provider.name}")
        }
    }

    fun activateProvider(provider: ProviderExtensionEntity) {
        activateAndOpenProvider(provider)
    }

    fun resetFailoverState() {
        failoverExcludedIds.clear()
        failoverAttempts = 0
        isHandlingFailover = false
    }

    fun switchToNextInCategory() {
        val current = activeProvider.value
        val candidates = pickNextFailoverCandidates(
            allProviders = providers.value,
            currentProvider = current,
            excludedIds = emptySet()
        )
        val next = candidates.firstOrNull() ?: return
        activateAndOpenProvider(next)
    }

    fun handleProviderLoadFailure(
        failedUrl: String?,
        reason: String?,
        isDeviceOnline: Boolean = true
    ) {
        // Automatic site switching is disabled; keep user on current site with clean retry option.
        _streamState.update {
            it.copy(
                isLoading = false,
                isAutoSwitching = false,
                hasError = true,
                errorDescription = reason ?: if (isDeviceOnline) {
                    "Unable to reach current source"
                } else {
                    "No internet connection"
                }
            )
        }
    }

    fun setProviderEnabled(provider: ProviderExtensionEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.setProviderEnabled(provider.id, enabled)
        }
    }

    fun deleteProvider(provider: ProviderExtensionEntity) {
        viewModelScope.launch {
            repository.deleteProvider(provider)
            _snackbarEvents.tryEmit("Removed \"${provider.name}\"")
        }
    }

    fun importVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            var count = 0
            for (uri in uris) {
                val imported = repository.importLocalVideo(uri)
                if (imported != null) count++
            }
            _isImporting.value = false
            if (count > 0) {
                _snackbarEvents.tryEmit(
                    if (count == 1) "Added 1 video to Offline Vault" else "Added $count videos to Offline Vault"
                )
            } else {
                _snackbarEvents.tryEmit("Could not import selected video")
            }
        }
    }

    fun prefetchStream(
        url: String,
        referer: String? = _streamState.value.currentUrl,
        origin: String? = null,
        userAgent: String? = _streamState.value.activeUserAgent
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty() || _prefetchingUrls.value.contains(cleanUrl)) return
        val matched = _streamState.value.detectedStreams.firstOrNull { it.url == cleanUrl }
        val resolvedReferer = referer ?: matched?.referer ?: _streamState.value.currentUrl
        val resolvedOrigin = origin ?: matched?.origin
        viewModelScope.launch {
            _prefetchingUrls.update { it + cleanUrl }
            val result = repository.prefetchStream(
                url = cleanUrl,
                referer = resolvedReferer,
                origin = resolvedOrigin,
                userAgent = userAgent
            )
            _prefetchingUrls.update { it - cleanUrl }
            result.onFailure {
                // Automatic prefetch failures remain non-intrusive
            }
        }
    }

    fun prefetchAllDetectedStreams() {
        val currentStreams = _streamState.value.detectedStreams
        val fallbackReferer = _streamState.value.currentUrl
        val ua = _streamState.value.activeUserAgent
        currentStreams.take(5).forEach { stream ->
            if (!prefetchedStreams.value.containsKey(stream.url)) {
                prefetchStream(
                    url = stream.url,
                    referer = stream.referer ?: fallbackReferer,
                    origin = stream.origin,
                    userAgent = ua
                )
            }
        }
    }

    suspend fun prefetchUrlDirect(url: String): kotlin.Result<PrefetchStreamInfo> {
        val cleanUrl = url.trim()
        _prefetchingUrls.update { it + cleanUrl }
        val result = repository.prefetchStream(
            url = cleanUrl,
            referer = _streamState.value.currentUrl,
            userAgent = _streamState.value.activeUserAgent
        )
        _prefetchingUrls.update { it - cleanUrl }
        return result
    }

    fun enqueueDownload(
        url: String,
        title: String? = null,
        referer: String? = null,
        origin: String? = null,
        userAgent: String? = _streamState.value.activeUserAgent,
        preferredVariantUrl: String? = null,
        formatLabel: String? = null,
        qualityLabel: String? = null,
        audioLabel: String? = null,
        subtitleUrl: String? = null,
        subtitleLanguage: String? = null,
        allowDuplicate: Boolean = false
    ) {
        val validation = UrlSecurityValidator.validatePublicHttpsUrl(url)
        val safeUrl = validation.getOrNull()
        if (safeUrl == null) {
            _snackbarEvents.tryEmit(
                validation.exceptionOrNull()?.localizedMessage ?: "Blocked insecure or invalid media URL"
            )
            return
        }
        val shield = ContentBlockerEngine.evaluateProviderOrMediaUrl(safeUrl)
        if (shield.blocked) {
            ContentBlockerEngine.recordBlocked(shield)
            _snackbarEvents.tryEmit("Blocked unsafe media source (${shield.category?.label ?: "Shield"})")
            return
        }
        val cachedInfo = prefetchedStreams.value[safeUrl] ?: prefetchedStreams.value[url]
        if (cachedInfo?.isDrmProtected == true) {
            _snackbarEvents.tryEmit(cachedInfo.drmReason ?: "DRM protected streams cannot be downloaded offline")
            return
        }
        val matched = _streamState.value.detectedStreams.firstOrNull { it.url == safeUrl || it.url == url }
        val resolvedReferer = referer ?: matched?.referer ?: _streamState.value.currentUrl
        val resolvedOrigin = origin ?: matched?.origin

        viewModelScope.launch {
            if (allowDuplicate) {
                repository.startVideoDownload(
                    url = safeUrl,
                    title = title,
                    referer = resolvedReferer,
                    origin = resolvedOrigin,
                    userAgent = userAgent,
                    preferredVariantUrl = preferredVariantUrl,
                    formatLabel = formatLabel,
                    qualityLabel = qualityLabel,
                    audioLabel = audioLabel,
                    subtitleUrl = subtitleUrl,
                    subtitleLanguage = subtitleLanguage
                )
                _snackbarEvents.tryEmit("Scheduled secure WorkManager download — live progress in Offline Vault")
            } else {
                val outcome = repository.startVideoDownloadChecked(
                    url = safeUrl,
                    title = title,
                    referer = resolvedReferer,
                    origin = resolvedOrigin,
                    userAgent = userAgent,
                    preferredVariantUrl = preferredVariantUrl,
                    formatLabel = formatLabel,
                    qualityLabel = qualityLabel,
                    audioLabel = audioLabel,
                    subtitleUrl = subtitleUrl,
                    subtitleLanguage = subtitleLanguage
                )
                _snackbarEvents.tryEmit(
                    outcome.message ?: "Scheduled secure WorkManager download — live progress in Offline Vault"
                )
            }
        }
    }

    fun pauseDownload(id: String) {
        repository.pauseDownload(id)
        _snackbarEvents.tryEmit("Download paused")
    }

    fun resumeDownload(task: DownloadTaskEntity) {
        val started = repository.resumeDownload(
            task = task,
            referer = _streamState.value.currentUrl,
            userAgent = _streamState.value.activeUserAgent
        )
        if (started) {
            _snackbarEvents.tryEmit("Resuming \"${task.title}\"")
        } else {
            _snackbarEvents.tryEmit("Unable to resume — link may be invalid")
        }
    }

    fun retryDownload(task: DownloadTaskEntity) {
        if (task.status == DownloadTaskEntity.STATUS_PAUSED) {
            resumeDownload(task)
            return
        }
        enqueueDownload(
            url = task.sourceUrl,
            title = task.title,
            referer = _streamState.value.currentUrl,
            userAgent = _streamState.value.activeUserAgent,
            preferredVariantUrl = task.selectedVariantUrl,
            formatLabel = task.formatLabel,
            qualityLabel = task.qualityLabel,
            audioLabel = task.audioLabel,
            subtitleUrl = task.subtitleUrl,
            subtitleLanguage = task.subtitleLanguage,
            allowDuplicate = true
        )
    }

    fun cancelDownload(id: String) {
        repository.cancelDownload(id)
    }

    fun deleteDownloadRecord(task: DownloadTaskEntity) {
        if (_latestCompletedDownload.value?.taskId == task.id) {
            _latestCompletedDownload.value = null
        }
        viewModelScope.launch {
            repository.deleteDownloadRecord(task)
        }
    }

    fun toggleFavorite(video: OfflineVideoEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(video)
        }
    }

    fun renameVideo(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameVideo(id, newTitle)
            _snackbarEvents.tryEmit("Video renamed")
        }
    }

    fun deleteVideo(video: OfflineVideoEntity) {
        if (_latestCompletedDownload.value?.taskId == video.id) {
            _latestCompletedDownload.value = null
        }
        viewModelScope.launch {
            repository.deleteVideo(video)
            _snackbarEvents.tryEmit("Removed \"${video.title}\" from vault")
        }
    }

    suspend fun getVideoById(id: String): OfflineVideoEntity? = repository.getVideoById(id)

    fun savePlaybackProgress(videoId: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            repository.updatePlaybackProgress(videoId, positionMs, durationMs)
        }
    }

    fun toggleCurrentPageBookmark() {
        val state = _streamState.value
        val url = state.currentUrl
        val isBookmarked = bookmarks.value.any { it.url.equals(url, ignoreCase = true) }
        viewModelScope.launch {
            if (isBookmarked) {
                repository.removeBookmarkByUrl(url)
                _snackbarEvents.tryEmit("Bookmark removed")
            } else {
                repository.addOrUpdateBookmark(url, state.pageTitle.ifBlank { "Navula Bookmark" }, "Page")
                _snackbarEvents.tryEmit("Bookmarked \"${state.pageTitle}\"")
            }
        }
    }

    fun addBookmark(url: String, title: String, category: String) {
        viewModelScope.launch {
            repository.addOrUpdateBookmark(url, title, category)
            _snackbarEvents.tryEmit("Bookmark saved")
        }
    }

    fun removeBookmark(id: String) {
        viewModelScope.launch {
            repository.removeBookmarkById(id)
        }
    }

    fun updateStreamNavigation(
        url: String? = null,
        title: String? = null,
        progress: Int? = null,
        isLoading: Boolean? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null,
        hasError: Boolean? = null,
        errorDescription: String? = null
    ) {
        _streamState.update { current ->
            val next = current.copy(
                currentUrl = url ?: current.currentUrl,
                pageTitle = title ?: current.pageTitle,
                progress = progress ?: current.progress,
                isLoading = isLoading ?: current.isLoading,
                canGoBack = canGoBack ?: current.canGoBack,
                canGoForward = canGoForward ?: current.canGoForward,
                hasError = hasError ?: current.hasError,
                errorDescription = if (hasError == false) null else (errorDescription ?: current.errorDescription)
            )
            if (next == current) current else next
        }
    }

    fun recordBlockedRequest(decision: BlockDecision) {
        repository.recordBlockedRequest(decision)
    }

    fun incrementBlockedPopups() {
        repository.recordBlockedRequest(
            BlockDecision(
                blocked = true,
                category = com.example.security.FilterCategory.ANNOYANCES,
                matchedRule = "popup_redirect"
            )
        )
    }

    fun setDetectedMediaUrl(url: String?) {
        _streamState.update { it.copy(detectedMediaUrl = url) }
    }

    fun addDetectedSubtitleTrack(
        url: String,
        label: String? = null,
        language: String? = null
    ) {
        val cleanUrl = UrlSecurityValidator.validatePublicHttpsUrl(url).getOrNull() ?: return
        val lower = cleanUrl.substringBefore('?').lowercase()
        val format = when {
            lower.endsWith(".srt") -> "SRT"
            lower.endsWith(".ass") -> "ASS"
            lower.endsWith(".ssa") -> "SSA"
            else -> "VTT"
        }
        val resolvedLabel = label?.trim()?.takeIf { it.isNotBlank() }
            ?: language?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
            ?: "Subtitle ($format)"

        _streamState.update { state ->
            val filtered = state.detectedSubtitles.filterNot { it.url == cleanUrl }
            val added = DetectedSubtitleTrack(
                url = cleanUrl,
                label = resolvedLabel,
                language = language?.trim()?.takeIf { it.isNotBlank() },
                format = format
            )
            state.copy(detectedSubtitles = (listOf(added) + filtered).take(12))
        }
    }

    fun addDetectedStream(
        url: String,
        title: String? = null,
        formatLabel: String? = null,
        referer: String? = null,
        origin: String? = null
    ) {
        val currentList = _streamState.value.detectedStreams
        if (currentList.firstOrNull()?.url == url) {
            return
        }
        val cleanUrl = UrlSecurityValidator.validatePublicHttpsUrl(url).getOrNull() ?: return
        val lower = cleanUrl.substringBefore('?').lowercase()
        if (lower.endsWith(".vtt") || lower.endsWith(".srt") || lower.endsWith(".ass")) {
            addDetectedSubtitleTrack(cleanUrl, label = title, language = null)
            return
        }
        val resolvedFormat = formatLabel ?: when {
            lower.endsWith(".m3u8") || cleanUrl.lowercase().contains(".m3u8") || cleanUrl.lowercase().contains("chunklist") -> "HLS"
            lower.endsWith(".mpd") -> "DASH"
            lower.endsWith(".mp4") -> "MP4"
            lower.endsWith(".webm") -> "WEBM"
            lower.endsWith(".mkv") -> "MKV"
            lower.endsWith(".mov") -> "MOV"
            lower.endsWith(".mp3") -> "MP3"
            lower.endsWith(".m4a") -> "M4A"
            lower.endsWith(".aac") -> "AAC"
            else -> "VIDEO"
        }
        val cleanTitle = title?.takeIf {
            it.isNotBlank() && !it.equals("Just a moment...", ignoreCase = true)
        } ?: _streamState.value.pageTitle.takeIf {
            it.isNotBlank() && !it.equals("Just a moment...", ignoreCase = true)
        } ?: "Navula Video"

        val previous = currentList.firstOrNull { it.url == cleanUrl }
        val isNewStream = previous == null
        val effectiveReferer = referer?.takeIf { it.isNotBlank() } ?: previous?.referer ?: _streamState.value.currentUrl
        val effectiveOrigin = origin?.takeIf { it.isNotBlank() } ?: previous?.origin

        _streamState.update { state ->
            val existing = state.detectedStreams.filterNot { it.url == cleanUrl }
            val updated = listOf(
                DetectedStream(
                    url = cleanUrl,
                    title = cleanTitle,
                    formatLabel = resolvedFormat,
                    referer = effectiveReferer,
                    origin = effectiveOrigin
                )
            ) + existing
            state.copy(
                detectedStreams = updated.take(15),
                detectedMediaUrl = state.detectedMediaUrl ?: cleanUrl
            )
        }

        // Throttle automatic prefetch: skip redundant sub-chunklists and avoid saturating radios/CPU
        val isChunklistOnly = cleanUrl.lowercase().contains("chunklist")
        if (isNewStream &&
            !isChunklistOnly &&
            resolvedFormat != "FILE" &&
            _prefetchingUrls.value.size < 2 &&
            !prefetchedStreams.value.containsKey(cleanUrl)
        ) {
            prefetchStream(
                url = cleanUrl,
                referer = effectiveReferer,
                origin = effectiveOrigin,
                userAgent = _streamState.value.activeUserAgent
            )
        }
    }

    fun clearDetectedStreamsForNewPage() {
        _streamState.update {
            it.copy(
                detectedStreams = emptyList(),
                detectedSubtitles = emptyList(),
                detectedMediaUrl = null
            )
        }
    }

    fun setActiveUserAgent(ua: String?) {
        _streamState.update { it.copy(activeUserAgent = ua) }
    }

    fun setAdGuardEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAdGuardEnabled(enabled) }
    }

    fun setBlockAdsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockAdsEnabled(enabled) }
    }

    fun setBlockTrackersEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockTrackersEnabled(enabled) }
    }

    fun setBlockMalwareEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockMalwareEnabled(enabled) }
    }

    fun setCosmeticFilteringEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setCosmeticFilteringEnabled(enabled) }
    }

    fun setBlockPopupsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockPopupsEnabled(enabled) }
    }

    fun setBlockRedirectsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockRedirectsEnabled(enabled) }
    }

    fun resetFilterSettings() {
        viewModelScope.launch {
            repository.resetFilterSettings()
            _snackbarEvents.tryEmit("Reset filter settings to lightweight defaults")
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setFilterListEnabled(listId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setFilterListEnabled(listId, enabled)
            if (enabled) {
                repository.syncFilterLists()
            }
        }
    }

    fun syncFilterLists() {
        viewModelScope.launch {
            val res = repository.syncFilterLists()
            res.onSuccess { synced ->
                val totalRules = shieldMetrics.value.activeRuleCount
                _snackbarEvents.tryEmit("Updated $synced filter lists • $totalRules active shield rules")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to update filter lists")
            }
        }
    }

    fun addCustomBlockedDomain(domain: String) {
        viewModelScope.launch {
            repository.addCustomBlockedDomain(domain)
            _snackbarEvents.tryEmit("Added \"$domain\" to custom blocklist")
        }
    }

    fun removeCustomBlockedDomain(domain: String) {
        viewModelScope.launch {
            repository.removeCustomBlockedDomain(domain)
        }
    }

    fun toggleWhitelistedDomain(domain: String) {
        val clean = domain.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").substringBefore("/").trim('.')
        if (clean.isBlank()) return
        val wasListed = preferences.value.whitelistedDomains.contains(clean)
        viewModelScope.launch {
            repository.toggleWhitelistedDomain(clean)
            _snackbarEvents.tryEmit(
                if (wasListed) "Re-enabled shield for $clean" else "Whitelisted ads on $clean (Malware shield stays on)"
            )
        }
    }

    fun setDesktopModeEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setDesktopModeEnabled(enabled) }
    }

    fun setAutoResumeEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoResumeEnabled(enabled) }
    }

    fun setShowHomeFloatingControls(enabled: Boolean) {
        viewModelScope.launch { repository.setShowHomeFloatingControls(enabled) }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch { repository.setDefaultPlaybackSpeed(speed) }
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    companion object {
        private const val MAX_AUTO_FAILOVER_HOPS = 4

        /**
         * Resolves which provider should be active on launch and ALWAYS returns its configured Home URL
         * (never restoring previously watched movie, episode, or playback pages).
         */
        internal fun resolveProviderHomeStartupUrl(
            dbActive: ProviderExtensionEntity?,
            allProviders: List<ProviderExtensionEntity>,
            persistedProviderId: String?
        ): Pair<ProviderExtensionEntity?, String> {
            val persistedMatch = persistedProviderId?.let { targetId ->
                allProviders.firstOrNull { it.id == targetId && it.baseUrl.isNotBlank() }
            }
            val dbValid = dbActive?.takeIf { it.baseUrl.isNotBlank() }
            val fallbackDefault = allProviders.firstOrNull {
                it.id == SpacedomDatabase.DEFAULT_PROVIDER_ID && it.baseUrl.isNotBlank()
            } ?: SpacedomDatabase.defaultSpacedomProvider()

            val chosenProvider = persistedMatch ?: dbValid ?: fallbackDefault
            val safeHomeUrl = UrlSecurityValidator.validatePublicHttpsUrl(chosenProvider.baseUrl)
                .getOrDefault(SpacedomDatabase.DEFAULT_STARTUP_URL)
            return chosenProvider to safeHomeUrl
        }

        /**
         * Orders failover candidates preferring enabled sources in the same section
         * (e.g. Movie -> Movie, Live TV & Sports -> Live TV & Sports) before falling back.
         */
        internal fun pickNextFailoverCandidates(
            allProviders: List<ProviderExtensionEntity>,
            currentProvider: ProviderExtensionEntity?,
            excludedIds: Set<String>
        ): List<ProviderExtensionEntity> {
            val eligible = allProviders.filter { prov ->
                prov.isEnabled &&
                    prov.baseUrl.isNotBlank() &&
                    prov.id != currentProvider?.id &&
                    prov.id !in excludedIds
            }
            if (currentProvider == null) return eligible

            val sameCategory = eligible.filter {
                it.category.equals(currentProvider.category, ignoreCase = true)
            }
            val sameSectionOrder = if (sameCategory.isNotEmpty()) {
                val allInCat = allProviders.filter {
                    it.isEnabled && it.baseUrl.isNotBlank() && it.category.equals(currentProvider.category, ignoreCase = true)
                }
                val idx = allInCat.indexOfFirst { it.id == currentProvider.id }
                if (idx >= 0 && allInCat.size > 1) {
                    val rotated = allInCat.drop(idx + 1) + allInCat.take(idx)
                    rotated.filter { it.id !in excludedIds && it.id != currentProvider.id }
                } else {
                    sameCategory
                }
            } else emptyList()

            val others = eligible.filter { candidate ->
                sameSectionOrder.none { it.id == candidate.id }
            }
            return sameSectionOrder + others
        }
    }
}
