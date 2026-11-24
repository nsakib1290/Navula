package com.example.data.media

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.OfflineVideoEntity
import com.example.data.local.SpacedomDao
import com.example.security.ContentBlockerEngine
import com.example.security.UrlSecurityValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class HlsVariantOption(
    val url: String,
    val label: String,
    val bandwidth: Long,
    val audioGroupId: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val resolutionLabel: String? = null,
    val codecs: String? = null,
    val subtitleGroupId: String? = null,
    val estimatedSizeBytes: Long? = null
)

data class MediaAudioTrackOption(
    val id: String,
    val url: String,
    val label: String,
    val language: String? = null,
    val codecs: String? = null,
    val bitrateBps: Long = 0L,
    val contentLengthBytes: Long = 0L,
    val mimeType: String = "audio/mpeg",
    val fileExtension: String = "mp3",
    val isDefault: Boolean = false,
    val isSeparateStream: Boolean = true
)

data class MediaSubtitleTrackOption(
    val id: String,
    val url: String,
    val label: String,
    val language: String? = null,
    val format: String = "VTT",
    val mimeType: String = "text/vtt"
)

data class PrefetchStreamInfo(
    val sourceUrl: String,
    val resolvedUrl: String,
    val finalVerifiedHost: String = "",
    val isHls: Boolean,
    val contentType: String,
    val contentLengthBytes: Long,
    val supportsRange: Boolean,
    val qualityLabel: String?,
    val variants: List<HlsVariantOption> = emptyList(),
    val audioTracks: List<MediaAudioTrackOption> = emptyList(),
    val subtitleTracks: List<MediaSubtitleTrackOption> = emptyList(),
    val mediaKind: String = if (isHls) "HLS" else "VIDEO",
    val fileExtension: String = if (isHls) "m3u8" else "mp4",
    val audioLanguage: String? = null,
    val audioCodec: String? = null,
    val isDrmProtected: Boolean = false,
    val drmReason: String? = null,
    val segmentCount: Int = 0,
    val estimatedDurationMs: Long = 0L,
    val tlsVerified: Boolean = true,
    val securityVerified: Boolean = true,
    val prefetchedAt: Long = System.currentTimeMillis()
)

data class DownloadLiveMetrics(
    val taskId: String,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
    val percent: Int = 0,
    val detailLabel: String = "",
    val isSecureVerified: Boolean = true
)

data class EnqueueDownloadOutcome(
    val taskId: String,
    val isDuplicate: Boolean = false,
    val existingStatus: String? = null,
    val message: String? = null
)

class ExpiredLinkException(val httpCode: Int, message: String) : IllegalStateException(message)

class VideoDownloadManager(
    private val context: Context,
    private val dao: SpacedomDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val prefetchedStreams: StateFlow<Map<String, PrefetchStreamInfo>> = sharedPrefetchedStreams.asStateFlow()
    val liveMetrics: StateFlow<Map<String, DownloadLiveMetrics>> = sharedLiveMetrics.asStateFlow()

    suspend fun prefetchStream(
        rawUrl: String,
        referer: String? = "https://spacedom.live/",
        origin: String? = null,
        userAgent: String? = null,
        forceRefresh: Boolean = false
    ): Result<PrefetchStreamInfo> = withContext(Dispatchers.IO) {
        val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(rawUrl).getOrElse {
            return@withContext Result.failure(it)
        }
        val shieldDecision = ContentBlockerEngine.evaluateProviderOrMediaUrl(safeUrl)
        if (shieldDecision.blocked) {
            ContentBlockerEngine.recordBlocked(shieldDecision)
            return@withContext Result.failure(
                SecurityException("Blocked by internal shield (${shieldDecision.category?.label ?: "Threat"})")
            )
        }

        if (!forceRefresh) {
            val cached = sharedPrefetchedStreams.value[safeUrl] ?: sharedPrefetchedStreams.value[rawUrl]
            if (cached != null && System.currentTimeMillis() - cached.prefetchedAt <= PREFETCH_CACHE_TTL_MS) {
                return@withContext Result.success(cached)
            }
        }

        val safeReferer = referer?.let { UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull() }
        val safeOrigin = origin?.let { UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull() }

        runCatching {
            val isHlsCandidate = isHlsStream(safeUrl)
            if (isHlsCandidate) {
                secureClient.newCall(buildRequest(safeUrl, safeReferer, safeOrigin, userAgent)).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (resp.code == 403 || resp.code == 404 || resp.code == 410) {
                            throw ExpiredLinkException(resp.code, "Link expired or unavailable (HTTP ${resp.code})")
                        }
                        throw IllegalStateException("Prefetch blocked (HTTP ${resp.code})")
                    }
                    val ct = resp.header("Content-Type").orEmpty()
                    val cd = resp.header("Content-Disposition")
                    val resolved = resp.request.url.toString()
                    UrlSecurityValidator.validatePublicHttpsUrl(resolved, upgradeHttpToHttps = false).getOrThrow()
                    val resolvedShield = ContentBlockerEngine.evaluateProviderOrMediaUrl(resolved)
                    if (resolvedShield.blocked) {
                        ContentBlockerEngine.recordBlocked(resolvedShield)
                        throw SecurityException("Blocked unsafe redirect target")
                    }

                    val bodyText = resp.body?.string().orEmpty()
                    if (!bodyText.contains("#EXTM3U") &&
                        !UrlSecurityValidator.isValidVideoContentType(ct, resolved, cd, allowTokenizedOctetStream = true)
                    ) {
                        throw IllegalStateException("Blocked non-media response ($ct)")
                    }

                    val info = inspectHlsManifestToPrefetchInfo(
                        safeUrl = safeUrl,
                        resolvedUrl = resolved,
                        contentType = ct,
                        manifestText = bodyText,
                        tlsVerified = resp.handshake != null || resolved.startsWith("https://"),
                        fetchVariantPlaylist = { variantUrl ->
                            runCatching {
                                secureClient.newCall(buildRequest(variantUrl, safeReferer, safeOrigin, userAgent)).execute().use { vResp ->
                                    if (vResp.isSuccessful) vResp.body?.string() else null
                                }
                            }.getOrNull()
                        }
                    )
                    cachePrefetchInfo(rawUrl, safeUrl, info)
                    info
                }
            } else {
                var probedInfo: PrefetchStreamInfo? = null
                runCatching {
                    val headReq = buildRequest(safeUrl, safeReferer, safeOrigin, userAgent, isHead = true)
                    secureClient.newCall(headReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val ct = resp.header("Content-Type").orEmpty()
                            val cd = resp.header("Content-Disposition")
                            val resolved = resp.request.url.toString()
                            UrlSecurityValidator.validatePublicHttpsUrl(resolved, upgradeHttpToHttps = false).getOrThrow()
                            if (UrlSecurityValidator.isValidVideoContentType(ct, resolved, cd, allowTokenizedOctetStream = true)) {
                                val len = resp.header("Content-Length")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                                val acceptRanges = resp.header("Accept-Ranges").orEmpty().contains("bytes", true)
                                val detectedHls = ct.contains("mpegurl", true) || isHlsStream(resolved)
                                if (!detectedHls) {
                                    probedInfo = buildDirectPrefetchInfo(
                                        safeUrl = safeUrl,
                                        resolvedUrl = resolved,
                                        contentType = ct,
                                        contentDisposition = cd,
                                        contentLength = len,
                                        supportsRange = acceptRanges,
                                        tlsVerified = resp.handshake != null || resolved.startsWith("https://")
                                    )
                                }
                            }
                        } else if (resp.code == 403 || resp.code == 404 || resp.code == 410) {
                            throw ExpiredLinkException(resp.code, "Link expired or unavailable (HTTP ${resp.code})")
                        }
                    }
                }.onFailure { err ->
                    if (err is ExpiredLinkException) throw err
                }
                if (probedInfo != null && !probedInfo!!.isHls) {
                    val info = probedInfo!!
                    cachePrefetchInfo(rawUrl, safeUrl, info)
                    return@runCatching info
                }

                val rangeReq = buildRequest(safeUrl, safeReferer, safeOrigin, userAgent, rangeHeader = "bytes=0-1023")
                secureClient.newCall(rangeReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (resp.code == 403 || resp.code == 404 || resp.code == 410) {
                            throw ExpiredLinkException(resp.code, "Link expired or unavailable (HTTP ${resp.code})")
                        }
                        throw IllegalStateException("Prefetch check failed (HTTP ${resp.code})")
                    }
                    val ct = resp.header("Content-Type").orEmpty()
                    val cd = resp.header("Content-Disposition")
                    val resolved = resp.request.url.toString()
                    UrlSecurityValidator.validatePublicHttpsUrl(resolved, upgradeHttpToHttps = false).getOrThrow()
                    val peekText = runCatching { resp.peekBody(2048).string() }.getOrDefault("")
                    val sniffedHtml = peekText.trimStart().startsWith("<!doctype", true) ||
                        peekText.trimStart().startsWith("<html", true)
                    if (sniffedHtml) {
                        throw IllegalStateException("Blocked non-video HTML page")
                    }
                    val sniffedM3u8 = peekText.contains("#EXTM3U") || ct.contains("mpegurl", true) || isHlsStream(resolved)
                    if (!sniffedM3u8 &&
                        !UrlSecurityValidator.isValidVideoContentType(ct, resolved, cd, allowTokenizedOctetStream = true)
                    ) {
                        throw IllegalStateException("Blocked non-video content ($ct)")
                    }

                    val info = if (sniffedM3u8) {
                        val fullText = if (resp.code == 200) peekText else {
                            secureClient.newCall(buildRequest(resolved, safeReferer, safeOrigin, userAgent)).execute().use { mResp ->
                                if (mResp.isSuccessful) mResp.body?.string().orEmpty() else peekText
                            }
                        }
                        inspectHlsManifestToPrefetchInfo(
                            safeUrl = safeUrl,
                            resolvedUrl = resolved,
                            contentType = ct,
                            manifestText = fullText,
                            tlsVerified = resp.handshake != null || resolved.startsWith("https://"),
                            fetchVariantPlaylist = { null }
                        )
                    } else {
                        val totalFromRange = resp.header("Content-Range")
                            ?.substringAfterLast('/')
                            ?.takeIf { it != "*" }
                            ?.toLongOrNull()
                        val len = totalFromRange ?: resp.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
                        val supportsRange = resp.code == 206 || resp.header("Accept-Ranges").orEmpty().contains("bytes", true)
                        buildDirectPrefetchInfo(
                            safeUrl = safeUrl,
                            resolvedUrl = resolved,
                            contentType = ct,
                            contentDisposition = cd,
                            contentLength = len,
                            supportsRange = supportsRange,
                            tlsVerified = resp.handshake != null || resolved.startsWith("https://")
                        )
                    }
                    cachePrefetchInfo(rawUrl, safeUrl, info)
                    info
                }
            }
        }
    }

    private fun cachePrefetchInfo(rawUrl: String, safeUrl: String, info: PrefetchStreamInfo) {
        sharedPrefetchedStreams.update { current ->
            val next = LinkedHashMap<String, PrefetchStreamInfo>(current.size + 2)
            current.forEach { (k, v) -> next[k] = v }
            next[rawUrl] = info
            next[safeUrl] = info
            while (next.size > PREFETCH_CACHE_MAX_ENTRIES) {
                val oldestKey = next.keys.firstOrNull() ?: break
                next.remove(oldestKey)
            }
            next
        }
    }

    suspend fun enqueueDownloadChecked(
        url: String,
        suggestedTitle: String? = null,
        referer: String? = "https://spacedom.live/",
        origin: String? = null,
        userAgent: String? = null,
        preferredVariantUrl: String? = null,
        formatLabel: String? = null,
        qualityLabel: String? = null,
        audioLabel: String? = null,
        subtitleUrl: String? = null,
        subtitleLanguage: String? = null
    ): EnqueueDownloadOutcome = withContext(Dispatchers.IO) {
        val validation = UrlSecurityValidator.validatePublicHttpsUrl(url)
        val safeUrl = validation.getOrNull()
        val safePreferredVariant = preferredVariantUrl?.let {
            UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull()
        }

        if (safeUrl != null) {
            val duplicate = dao.findDuplicateDownload(safeUrl, safePreferredVariant)
            if (duplicate != null) {
                if (duplicate.status == DownloadTaskEntity.STATUS_PAUSED) {
                    resumeDownload(duplicate)
                    return@withContext EnqueueDownloadOutcome(
                        taskId = duplicate.id,
                        isDuplicate = true,
                        existingStatus = DownloadTaskEntity.STATUS_PAUSED,
                        message = "Resumed existing download \"${duplicate.title}\""
                    )
                }
                val msg = when (duplicate.status) {
                    DownloadTaskEntity.STATUS_COMPLETED -> "\"${duplicate.title}\" is already saved in Offline Vault"
                    else -> "\"${duplicate.title}\" is already in the download queue"
                }
                return@withContext EnqueueDownloadOutcome(
                    taskId = duplicate.id,
                    isDuplicate = true,
                    existingStatus = duplicate.status,
                    message = msg
                )
            }
        }

        val taskId = enqueueDownloadInternal(
            url = url,
            suggestedTitle = suggestedTitle,
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
        EnqueueDownloadOutcome(taskId = taskId, isDuplicate = false)
    }

    fun enqueueDownload(
        url: String,
        suggestedTitle: String? = null,
        referer: String? = "https://spacedom.live/",
        origin: String? = null,
        userAgent: String? = null,
        preferredVariantUrl: String? = null,
        formatLabel: String? = null,
        qualityLabel: String? = null,
        audioLabel: String? = null,
        subtitleUrl: String? = null,
        subtitleLanguage: String? = null
    ): String {
        return enqueueDownloadInternal(
            url = url,
            suggestedTitle = suggestedTitle,
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

    private fun enqueueDownloadInternal(
        url: String,
        suggestedTitle: String? = null,
        referer: String? = "https://spacedom.live/",
        origin: String? = null,
        userAgent: String? = null,
        preferredVariantUrl: String? = null,
        formatLabel: String? = null,
        qualityLabel: String? = null,
        audioLabel: String? = null,
        subtitleUrl: String? = null,
        subtitleLanguage: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        pausedTaskIds.remove(id)
        val validation = UrlSecurityValidator.validatePublicHttpsUrl(url)
        val safeUrl = validation.getOrNull()
        val cachedPrefetch = safeUrl?.let { sharedPrefetchedStreams.value[it] }
        if (cachedPrefetch?.isDrmProtected == true) {
            scope.launch {
                val cleanName = buildCleanTitle(suggestedTitle, url)
                dao.insertDownloadIfMissing(
                    DownloadTaskEntity(
                        id = id,
                        title = cleanName,
                        sourceUrl = safeUrl,
                        destinationPath = "",
                        status = DownloadTaskEntity.STATUS_FAILED,
                        errorMessage = cachedPrefetch.drmReason ?: "DRM protected stream cannot be downloaded",
                        formatLabel = "DRM",
                        providerHost = cachedPrefetch.finalVerifiedHost
                    )
                )
            }
            return id
        }

        val cleanName = buildCleanTitle(suggestedTitle, url)
        val offlineDir = File(context.filesDir, "offline_videos").apply { mkdirs() }
        val effectiveTargetUrl = preferredVariantUrl ?: safeUrl ?: url
        val isHlsUrl = isHlsStream(effectiveTargetUrl) || (cachedPrefetch?.isHls == true && preferredVariantUrl == null)
        val resolvedExt = if (isHlsUrl) "m3u8" else guessExtension(effectiveTargetUrl, cachedPrefetch?.contentType)
        val shortId = id.take(8)
        val safeBaseFile = UrlSecurityValidator.sanitizeFileName(cleanName, fallback = "navula_media")
        val destFile = if (isHlsUrl) {
            val hlsDir = UrlSecurityValidator.resolveConfinedChild(offlineDir, "${safeBaseFile}_$shortId").apply { mkdirs() }
            UrlSecurityValidator.resolveConfinedChild(hlsDir, "index.m3u8")
        } else {
            UrlSecurityValidator.resolveConfinedChild(offlineDir, "${safeBaseFile}_$shortId.$resolvedExt")
        }

        val shieldCheck = safeUrl?.let { ContentBlockerEngine.evaluateProviderOrMediaUrl(it) }
        val safePreferredVariant = preferredVariantUrl?.let {
            UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull()
        }
        val safeSubUrl = subtitleUrl?.let {
            UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull()
        }
        val host = cachedPrefetch?.finalVerifiedHost?.takeIf { it.isNotBlank() }
            ?: safeUrl?.let { runCatching { URI(it).host.orEmpty() }.getOrDefault("") }
        val resolvedFormatLabel = formatLabel?.takeIf { it.isNotBlank() }
            ?: when {
                isHlsUrl -> "HLS"
                else -> resolvedExt.uppercase()
            }
        val resolvedQuality = qualityLabel?.takeIf { it.isNotBlank() }
            ?: safePreferredVariant?.let { vUrl ->
                cachedPrefetch?.variants?.firstOrNull { it.url == vUrl }?.resolutionLabel ?: guessQualityFromUrl(vUrl)
            }
            ?: cachedPrefetch?.qualityLabel

        val preKnownBytes = cachedPrefetch?.contentLengthBytes ?: 0L
        val supportsResumeInit = isHlsUrl || (cachedPrefetch?.supportsRange == true)

        scope.launch {
            val task = DownloadTaskEntity(
                id = id,
                title = cleanName,
                sourceUrl = safeUrl ?: url,
                destinationPath = destFile.absolutePath,
                totalBytes = preKnownBytes,
                status = DownloadTaskEntity.STATUS_QUEUED,
                selectedVariantUrl = safePreferredVariant,
                formatLabel = resolvedFormatLabel,
                qualityLabel = resolvedQuality,
                mimeType = cachedPrefetch?.contentType,
                fileExtension = resolvedExt,
                audioLabel = audioLabel,
                subtitleUrl = safeSubUrl,
                subtitleLanguage = subtitleLanguage,
                providerHost = host,
                supportsPauseResume = supportsResumeInit
            )
            dao.insertDownloadIfMissing(task)

            if (safeUrl == null) {
                val errMsg = validation.exceptionOrNull()?.localizedMessage ?: "Blocked insecure download URL"
                dao.updateDownloadProgress(
                    id = id,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    status = DownloadTaskEntity.STATUS_FAILED,
                    errorMessage = errMsg
                )
                return@launch
            }

            if (shieldCheck?.blocked == true) {
                ContentBlockerEngine.recordBlocked(shieldCheck)
                dao.updateDownloadProgress(
                    id = id,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    status = DownloadTaskEntity.STATUS_FAILED,
                    errorMessage = "Blocked by uBlock/AdGuard Shield (${shieldCheck.category?.label ?: "Risk"})"
                )
                return@launch
            }
        }

        if (safeUrl == null || shieldCheck?.blocked == true) {
            return id
        }

        scheduleWorkManagerDownload(
            id = id,
            safeUrl = safeUrl,
            cleanName = cleanName,
            destPath = destFile.absolutePath,
            referer = referer,
            origin = origin,
            userAgent = userAgent,
            preferredVariantUrl = safePreferredVariant,
            prefetchedResolvedUrl = cachedPrefetch?.resolvedUrl,
            prefetchedLength = preKnownBytes,
            subtitleUrl = safeSubUrl,
            subtitleLanguage = subtitleLanguage,
            isResume = false
        )

        return id
    }

    fun pauseDownload(id: String) {
        pausedTaskIds.add(id)
        sharedLiveMetrics.update { it - id }
        runCatching {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.cancelUniqueWork(VideoDownloadWorker.uniqueWorkName(id))
            wm.cancelAllWorkByTag("task_$id")
        }
        scope.launch {
            val existing = dao.getDownloadById(id)
            if (existing != null && (existing.status == DownloadTaskEntity.STATUS_QUEUED || existing.status == DownloadTaskEntity.STATUS_DOWNLOADING)) {
                val diskBytes = computeExistingDownloadedBytes(existing)
                dao.updateDownloadStateDetailed(
                    id = id,
                    downloadedBytes = maxOf(existing.downloadedBytes, diskBytes),
                    totalBytes = existing.totalBytes,
                    status = DownloadTaskEntity.STATUS_PAUSED,
                    errorMessage = "Paused by user",
                    supportsPauseResume = existing.supportsPauseResume || existing.formatLabel == "HLS"
                )
            }
        }
    }

    fun resumeDownload(
        task: DownloadTaskEntity,
        referer: String? = "https://spacedom.live/",
        origin: String? = null,
        userAgent: String? = null
    ): Boolean {
        val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(task.sourceUrl).getOrNull() ?: return false
        pausedTaskIds.remove(task.id)
        scope.launch {
            val diskBytes = computeExistingDownloadedBytes(task)
            dao.updateDownloadStateDetailed(
                id = task.id,
                downloadedBytes = maxOf(task.downloadedBytes, diskBytes),
                totalBytes = task.totalBytes,
                status = DownloadTaskEntity.STATUS_QUEUED,
                errorMessage = "Resuming download...",
                supportsPauseResume = task.supportsPauseResume
            )
        }
        val cachedPrefetch = sharedPrefetchedStreams.value[safeUrl]
        scheduleWorkManagerDownload(
            id = task.id,
            safeUrl = safeUrl,
            cleanName = task.title,
            destPath = task.destinationPath,
            referer = referer,
            origin = origin,
            userAgent = userAgent,
            preferredVariantUrl = task.selectedVariantUrl,
            prefetchedResolvedUrl = cachedPrefetch?.resolvedUrl,
            prefetchedLength = task.totalBytes.takeIf { it > 0L } ?: (cachedPrefetch?.contentLengthBytes ?: 0L),
            subtitleUrl = task.subtitleUrl,
            subtitleLanguage = task.subtitleLanguage,
            isResume = true
        )
        return true
    }

    fun cancelDownload(id: String) {
        pausedTaskIds.remove(id)
        sharedLiveMetrics.update { it - id }
        runCatching {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.cancelUniqueWork(VideoDownloadWorker.uniqueWorkName(id))
            wm.cancelAllWorkByTag("task_$id")
        }
        scope.launch {
            val existing = dao.getDownloadById(id)
            if (existing != null && existing.destinationPath.isNotBlank()) {
                val destFile = File(existing.destinationPath)
                val partFile = File(destFile.parentFile, "${destFile.name}.part")
                val isHls = existing.formatLabel.equals("HLS", true) || destFile.name == "index.m3u8"
                cleanupDest(destFile, partFile, isHls)
            }
            dao.updateDownloadProgress(
                id = id,
                downloadedBytes = 0L,
                totalBytes = existing?.totalBytes ?: 0L,
                status = DownloadTaskEntity.STATUS_CANCELLED,
                errorMessage = "Cancelled by user"
            )
        }
    }

    private fun scheduleWorkManagerDownload(
        id: String,
        safeUrl: String,
        cleanName: String,
        destPath: String,
        referer: String?,
        origin: String?,
        userAgent: String?,
        preferredVariantUrl: String?,
        prefetchedResolvedUrl: String?,
        prefetchedLength: Long,
        subtitleUrl: String?,
        subtitleLanguage: String?,
        isResume: Boolean
    ) {
        val safeReferer = referer?.let { UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull() }
        val safeOrigin = origin?.let { UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull() }
        val safePreferredVariant = preferredVariantUrl?.let {
            UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull()
        }
        val safeSubUrl = subtitleUrl?.let {
            UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull()
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            VideoDownloadWorker.KEY_TASK_ID to id,
            VideoDownloadWorker.KEY_SOURCE_URL to safeUrl,
            VideoDownloadWorker.KEY_TITLE to cleanName,
            VideoDownloadWorker.KEY_DEST_PATH to destPath,
            VideoDownloadWorker.KEY_REFERER to safeReferer,
            VideoDownloadWorker.KEY_ORIGIN to safeOrigin,
            VideoDownloadWorker.KEY_USER_AGENT to userAgent,
            VideoDownloadWorker.KEY_PREFERRED_VARIANT_URL to safePreferredVariant,
            VideoDownloadWorker.KEY_PREFETCHED_RESOLVED_URL to prefetchedResolvedUrl,
            VideoDownloadWorker.KEY_PREFETCHED_LENGTH to prefetchedLength,
            VideoDownloadWorker.KEY_SUBTITLE_URL to safeSubUrl,
            VideoDownloadWorker.KEY_SUBTITLE_LANGUAGE to subtitleLanguage,
            VideoDownloadWorker.KEY_IS_RESUME to isResume
        )

        val request = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(VideoDownloadWorker.TAG_DOWNLOAD)
            .addTag("task_$id")
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                VideoDownloadWorker.uniqueWorkName(id),
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    private fun computeExistingDownloadedBytes(task: DownloadTaskEntity): Long {
        if (task.destinationPath.isBlank()) return task.downloadedBytes
        val destFile = File(task.destinationPath)
        return if (task.formatLabel.equals("HLS", true) || destFile.name == "index.m3u8") {
            destFile.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        } else {
            val partFile = File(destFile.parentFile, "${destFile.name}.part")
            partFile.takeIf { it.exists() }?.length() ?: destFile.takeIf { it.exists() }?.length() ?: 0L
        }
    }

    fun parseMasterVariants(baseUrl: String, masterText: String): List<HlsVariantOption> {
        return Companion.parseMasterVariants(baseUrl, masterText)
    }

    companion object {
        private const val PREFETCH_CACHE_MAX_ENTRIES = 32
        private const val PREFETCH_CACHE_TTL_MS = 15 * 60 * 1000L

        private val sharedPrefetchedStreams = MutableStateFlow<Map<String, PrefetchStreamInfo>>(emptyMap())
        private val sharedLiveMetrics = MutableStateFlow<Map<String, DownloadLiveMetrics>>(emptyMap())
        private val pausedTaskIds = ConcurrentHashMap.newKeySet<String>()

        private val secureClient: OkHttpClient by lazy {
            UrlSecurityValidator.createSecureOkHttpClient(
                connectTimeoutSeconds = 25L,
                readTimeoutSeconds = 90L
            )
        }

        private fun buildCleanTitle(suggestedTitle: String?, fallbackUrl: String): String {
            return UrlSecurityValidator.sanitizeFileName(
                suggestedTitle?.takeIf { it.isNotBlank() }
                    ?: Uri.parse(fallbackUrl).lastPathSegment?.substringBeforeLast('.')
                    ?: "Navula Media",
                fallback = "Navula Media"
            )
        }

        internal suspend fun inspectHlsManifestToPrefetchInfo(
            safeUrl: String,
            resolvedUrl: String,
            contentType: String,
            manifestText: String,
            tlsVerified: Boolean,
            fetchVariantPlaylist: suspend (String) -> String?
        ): PrefetchStreamInfo {
            val drmReason = detectDrmProtection(manifestText)
            var variants = emptyList<HlsVariantOption>()
            var audioTracks = emptyList<MediaAudioTrackOption>()
            var subtitleTracks = emptyList<MediaSubtitleTrackOption>()
            var qualityLabel: String? = null
            var segCount = 0
            var durMs = 0L
            var effectiveDrm = drmReason

            if (manifestText.contains("#EXT-X-STREAM-INF")) {
                audioTracks = parseAudioMediaTracks(resolvedUrl, manifestText)
                subtitleTracks = parseSubtitleMediaTracks(resolvedUrl, manifestText)
                variants = parseMasterVariants(resolvedUrl, manifestText)
                val best = variants.firstOrNull()
                qualityLabel = best?.resolutionLabel ?: best?.label
                val variantUrl = best?.url ?: pickBestVariantUrl(resolvedUrl, manifestText)
                if (variantUrl != null && UrlSecurityValidator.isSafePublicHttpsUrl(variantUrl)) {
                    val vText = fetchVariantPlaylist(variantUrl)
                    if (!vText.isNullOrBlank()) {
                        if (effectiveDrm == null) {
                            effectiveDrm = detectDrmProtection(vText)
                        }
                        segCount = countHlsSegments(vText)
                        durMs = sumHlsDurationMs(vText)
                    }
                }
                if (durMs > 0L) {
                    variants = variants.map { v ->
                        val estBytes = if (v.bandwidth > 0L) (v.bandwidth * durMs) / 8_000L else null
                        v.copy(estimatedSizeBytes = estBytes)
                    }
                }
            } else {
                segCount = countHlsSegments(manifestText)
                durMs = sumHlsDurationMs(manifestText)
            }

            val verifiedHost = runCatching { URI(resolvedUrl).host.orEmpty() }.getOrDefault("")
            val bestEstimate = variants.firstOrNull()?.estimatedSizeBytes ?: 0L
            val defaultAudio = audioTracks.firstOrNull { it.isDefault } ?: audioTracks.firstOrNull()

            return PrefetchStreamInfo(
                sourceUrl = safeUrl,
                resolvedUrl = resolvedUrl,
                finalVerifiedHost = verifiedHost,
                isHls = true,
                contentType = contentType.ifBlank { "application/x-mpegURL" },
                contentLengthBytes = bestEstimate,
                supportsRange = effectiveDrm == null,
                qualityLabel = qualityLabel ?: "HLS",
                variants = variants,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                mediaKind = if (effectiveDrm != null) "DRM_PROTECTED" else "HLS",
                fileExtension = "m3u8",
                audioLanguage = defaultAudio?.language ?: defaultAudio?.label,
                audioCodec = defaultAudio?.codecs,
                isDrmProtected = effectiveDrm != null,
                drmReason = effectiveDrm,
                segmentCount = segCount,
                estimatedDurationMs = durMs,
                tlsVerified = tlsVerified,
                securityVerified = effectiveDrm == null
            )
        }

        internal fun buildDirectPrefetchInfo(
            safeUrl: String,
            resolvedUrl: String,
            contentType: String,
            contentDisposition: String?,
            contentLength: Long,
            supportsRange: Boolean,
            tlsVerified: Boolean
        ): PrefetchStreamInfo {
            val verifiedHost = runCatching { URI(resolvedUrl).host.orEmpty() }.getOrDefault("")
            val ext = guessExtension(resolvedUrl, contentType, contentDisposition)
            val ctLower = contentType.lowercase()
            val isAudio = ctLower.startsWith("audio/") || ext in listOf("mp3", "m4a", "aac", "ogg", "opus", "flac")
            val isSubtitle = ctLower == "text/vtt" || ctLower == "application/x-subrip" || ext in listOf("vtt", "srt")

            val kind = when {
                isSubtitle -> "SUBTITLE"
                isAudio -> "AUDIO"
                else -> "VIDEO"
            }
            val quality = if (isAudio || isSubtitle) null else guessQualityFromUrl(resolvedUrl)

            val audioTracks = if (isAudio) {
                listOf(
                    MediaAudioTrackOption(
                        id = "direct_audio_${safeUrl.hashCode()}",
                        url = resolvedUrl,
                        label = "${ext.uppercase()} Audio",
                        language = null,
                        codecs = when (ext) {
                            "mp3" -> "MP3"
                            "aac", "m4a" -> "AAC"
                            "ogg", "opus" -> "Opus/Vorbis"
                            else -> null
                        },
                        bitrateBps = 0L,
                        contentLengthBytes = contentLength,
                        mimeType = contentType.ifBlank { "audio/mpeg" },
                        fileExtension = ext,
                        isDefault = true,
                        isSeparateStream = false
                    )
                )
            } else emptyList()

            val subTracks = if (isSubtitle) {
                listOf(
                    MediaSubtitleTrackOption(
                        id = "direct_sub_${safeUrl.hashCode()}",
                        url = resolvedUrl,
                        label = "${ext.uppercase()} Subtitles",
                        language = null,
                        format = ext.uppercase(),
                        mimeType = contentType.ifBlank { if (ext == "srt") "application/x-subrip" else "text/vtt" }
                    )
                )
            } else emptyList()

            return PrefetchStreamInfo(
                sourceUrl = safeUrl,
                resolvedUrl = resolvedUrl,
                finalVerifiedHost = verifiedHost,
                isHls = false,
                contentType = contentType.ifBlank {
                    when (ext) {
                        "mp3" -> "audio/mpeg"
                        "vtt" -> "text/vtt"
                        "srt" -> "application/x-subrip"
                        "webm" -> "video/webm"
                        "mkv" -> "video/x-matroska"
                        else -> "video/mp4"
                    }
                },
                contentLengthBytes = contentLength,
                supportsRange = supportsRange,
                qualityLabel = quality,
                variants = emptyList(),
                audioTracks = audioTracks,
                subtitleTracks = subTracks,
                mediaKind = kind,
                fileExtension = ext,
                audioCodec = audioTracks.firstOrNull()?.codecs,
                isDrmProtected = false,
                drmReason = null,
                tlsVerified = tlsVerified,
                securityVerified = true
            )
        }

        internal fun detectDrmProtection(manifestText: String): String? {
            for (raw in manifestText.lines()) {
                val line = raw.trim()
                if (!line.startsWith("#EXT-X-KEY") && !line.startsWith("#EXT-X-SESSION-KEY")) continue
                val upper = line.uppercase()
                if (upper.contains("METHOD=SAMPLE-AES")) {
                    return "DRM encrypted stream (SAMPLE-AES) is protected and cannot be downloaded."
                }
                if (upper.contains("WIDEVINE") ||
                    upper.contains("EDEF8BA9") ||
                    upper.contains("PLAYREADY") ||
                    upper.contains("9A04F079") ||
                    upper.contains("COM.APPLE.STREAMINGKEYDELIVERY")
                ) {
                    return "DRM protected stream (Widevine / FairPlay / PlayReady) cannot be downloaded."
                }
            }
            return null
        }

        internal suspend fun executeWorkerDownload(
            context: Context,
            dao: SpacedomDao,
            taskId: String,
            rawUrl: String,
            title: String,
            destFile: File,
            referer: String?,
            origin: String?,
            userAgent: String?,
            preferredVariantUrl: String?,
            prefetchedResolvedUrl: String?,
            prefetchedLength: Long,
            subtitleUrl: String? = null,
            subtitleLanguage: String? = null,
            isResume: Boolean = false,
            isStoppedCheck: () -> Boolean,
            onProgressUpdate: suspend (DownloadLiveMetrics, Long, Long) -> Unit
        ) = withContext(Dispatchers.IO) {
            val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(rawUrl).getOrThrow()
            val shieldCheck = ContentBlockerEngine.evaluateProviderOrMediaUrl(safeUrl)
            if (shieldCheck.blocked) {
                ContentBlockerEngine.recordBlocked(shieldCheck)
                val msg = "Blocked by uBlock/AdGuard Shield (${shieldCheck.category?.label ?: "Risk"})"
                dao.updateDownloadProgress(
                    id = taskId,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    status = DownloadTaskEntity.STATUS_FAILED,
                    errorMessage = msg
                )
                throw SecurityException(msg)
            }

            val safeReferer = referer?.let { UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull() }
            val safeOrigin = origin?.let { UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull() }
            val isHlsUrl = isHlsStream(preferredVariantUrl ?: safeUrl)
            val partFile = File(destFile.parentFile, "${destFile.name}.part")
            val offlineDir = File(context.filesDir, "offline_videos").apply { mkdirs() }
            var actualIsHls = isHlsUrl
            var currentKnownBytes = if (isResume && !isHlsUrl && partFile.exists()) partFile.length() else 0L
            var knownTotalBytes = prefetchedLength.coerceAtLeast(0L)
            var currentSupportsPauseResume = isHlsUrl

            try {
                if (isStoppedCheck()) throw CancellationException("Download stopped")

                dao.updateDownloadStateDetailed(
                    id = taskId,
                    downloadedBytes = currentKnownBytes,
                    totalBytes = knownTotalBytes,
                    status = DownloadTaskEntity.STATUS_DOWNLOADING,
                    errorMessage = null,
                    supportsPauseResume = isHlsUrl
                )

                val cachedPrefetch = sharedPrefetchedStreams.value[safeUrl]
                val effectiveUrl = prefetchedResolvedUrl
                    ?.let { UrlSecurityValidator.validatePublicHttpsUrl(it, upgradeHttpToHttps = false).getOrNull() }
                    ?: cachedPrefetch?.resolvedUrl
                    ?: safeUrl

                val resumeOffset = if (isResume && !isHlsUrl && partFile.exists() && partFile.length() > 0L) {
                    partFile.length()
                } else 0L

                val initialReq = buildRequest(
                    url = effectiveUrl,
                    referer = safeReferer,
                    origin = safeOrigin,
                    userAgent = userAgent,
                    rangeHeader = if (resumeOffset > 0L) "bytes=$resumeOffset-" else null
                )

                secureClient.newCall(initialReq).execute().use { response ->
                    if (response.code == 416 && resumeOffset > 0L && knownTotalBytes in 1L..resumeOffset) {
                        // Partial file already holds the entire payload
                        destFile.parentFile?.mkdirs()
                        if (destFile.exists()) destFile.delete()
                        partFile.renameTo(destFile)
                    } else if (!response.isSuccessful) {
                        val err = if (response.code == 403 || response.code == 404 || response.code == 410) {
                            "Download link expired or unavailable (HTTP ${response.code}). Refresh source and try again."
                        } else {
                            "Server responded with HTTP ${response.code}"
                        }
                        dao.updateDownloadProgress(
                            id = taskId,
                            downloadedBytes = currentKnownBytes,
                            totalBytes = knownTotalBytes,
                            status = DownloadTaskEntity.STATUS_FAILED,
                            errorMessage = err
                        )
                        if (response.code == 403 || response.code == 404 || response.code == 410) {
                            throw ExpiredLinkException(response.code, err)
                        }
                        throw IllegalStateException(err)
                    } else {
                        val finalResolvedUrl = response.request.url.toString()
                        UrlSecurityValidator.validatePublicHttpsUrl(finalResolvedUrl, upgradeHttpToHttps = false).getOrThrow()

                        val contentType = response.header("Content-Type").orEmpty().lowercase()
                        val contentDisposition = response.header("Content-Disposition")
                        val peekText = runCatching { response.peekBody(512).string() }.getOrDefault("")
                        val isHtmlBody = peekText.trimStart().startsWith("<!doctype", true) ||
                            peekText.trimStart().startsWith("<html", true) ||
                            contentType.contains("text/html")
                        val sniffedHls = peekText.contains("#EXTM3U")

                        if (isHtmlBody || (!sniffedHls && !UrlSecurityValidator.isValidVideoContentType(
                                contentType = contentType,
                                url = finalResolvedUrl,
                                contentDisposition = contentDisposition,
                                allowTokenizedOctetStream = true
                            ))
                        ) {
                            val err = "Blocked non-video response (${contentType.ifBlank { "HTML/Challenge" }}). Play video first."
                            dao.updateDownloadProgress(
                                id = taskId,
                                downloadedBytes = 0L,
                                totalBytes = 0L,
                                status = DownloadTaskEntity.STATUS_FAILED,
                                errorMessage = err
                            )
                            throw IllegalStateException(err)
                        }

                        val body = response.body ?: throw IllegalStateException("Empty response body")
                        val treatAsHls = isHlsUrl ||
                            sniffedHls ||
                            contentType.contains("mpegurl") ||
                            contentType.contains("x-mpegurl")
                        actualIsHls = treatAsHls

                        if (treatAsHls) {
                            val manifestText = body.string()
                            if (!manifestText.contains("#EXTM3U")) {
                                throw IllegalStateException("Invalid HLS playlist returned by server")
                            }
                            detectDrmProtection(manifestText)?.let { drmMsg ->
                                dao.updateDownloadProgress(
                                    id = taskId,
                                    downloadedBytes = 0L,
                                    totalBytes = 0L,
                                    status = DownloadTaskEntity.STATUS_FAILED,
                                    errorMessage = drmMsg
                                )
                                throw SecurityException(drmMsg)
                            }
                            currentSupportsPauseResume = true
                            val hlsFolder = (destFile.parentFile ?: UrlSecurityValidator.resolveConfinedChild(offlineDir, taskId))
                                .apply { mkdirs() }
                            val localIndex = UrlSecurityValidator.resolveConfinedChild(hlsFolder, "index.m3u8")
                            val hlsResult = downloadFullHlsVideo(
                                dao = dao,
                                taskId = taskId,
                                baseUrl = finalResolvedUrl,
                                manifestText = manifestText,
                                targetDir = hlsFolder,
                                localIndexFile = localIndex,
                                referer = safeReferer,
                                origin = safeOrigin,
                                userAgent = userAgent,
                                preferredVariantUrl = preferredVariantUrl,
                                isStoppedCheck = isStoppedCheck,
                                onProgressUpdate = onProgressUpdate
                            )
                            val savedSubPath = downloadOptionalSubtitle(
                                subtitleUrl = subtitleUrl,
                                destDir = hlsFolder,
                                baseFileName = "${taskId.take(8)}_sub",
                                referer = safeReferer,
                                origin = safeOrigin,
                                userAgent = userAgent,
                                isStoppedCheck = isStoppedCheck
                            )
                            val folderSize = hlsFolder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                            if (!localIndex.exists() || folderSize <= 0L) {
                                throw IllegalStateException("HLS download wrote no media data")
                            }
                            val finalMetrics = DownloadLiveMetrics(
                                taskId = taskId,
                                speedBytesPerSec = 0L,
                                etaSeconds = 0L,
                                percent = 100,
                                detailLabel = "Completed",
                                isSecureVerified = true
                            )
                            sharedLiveMetrics.update { it + (taskId to finalMetrics) }
                            onProgressUpdate(finalMetrics, folderSize, folderSize)

                            val host = runCatching { URI(finalResolvedUrl).host }.getOrNull()
                            dao.upsertVideo(
                                OfflineVideoEntity(
                                    id = taskId,
                                    title = title,
                                    uriString = Uri.fromFile(localIndex).toString(),
                                    sourceType = "DOWNLOADED",
                                    sourceUrl = safeUrl,
                                    mimeType = "application/x-mpegURL",
                                    fileSizeBytes = folderSize,
                                    durationMs = hlsResult.totalDurationMs,
                                    thumbnailPath = null,
                                    subtitlePath = savedSubPath,
                                    subtitleLanguage = subtitleLanguage,
                                    sourceHost = host
                                )
                            )
                            dao.updateDownloadStateDetailed(
                                id = taskId,
                                downloadedBytes = folderSize,
                                totalBytes = folderSize,
                                status = DownloadTaskEntity.STATUS_COMPLETED,
                                errorMessage = null,
                                supportsPauseResume = true,
                                subtitlePath = savedSubPath
                            )
                        } else {
                            val isAppendPartial = resumeOffset > 0L && response.code == 206
                            val totalFromRangeHeader = response.header("Content-Range")
                                ?.substringAfterLast('/')
                                ?.takeIf { it != "*" }
                                ?.toLongOrNull()
                            val totalBytes = (totalFromRangeHeader
                                ?: body.contentLength().takeIf { it > 0L }?.let { if (isAppendPartial) resumeOffset + it else it }
                                ?: prefetchedLength.takeIf { it > 0L }
                                ?: cachedPrefetch?.contentLengthBytes
                                ?: 0L).coerceAtLeast(0L)
                            knownTotalBytes = totalBytes

                            val supportsRange = isAppendPartial ||
                                response.header("Accept-Ranges").orEmpty().contains("bytes", true)
                            currentSupportsPauseResume = supportsRange

                            var downloaded = if (isAppendPartial) resumeOffset else 0L
                            currentKnownBytes = downloaded

                            dao.updateDownloadStateDetailed(
                                id = taskId,
                                downloadedBytes = downloaded,
                                totalBytes = totalBytes,
                                status = DownloadTaskEntity.STATUS_DOWNLOADING,
                                errorMessage = null,
                                supportsPauseResume = supportsRange
                            )

                            val buffer = ByteArray(32 * 1024)
                            var lastReportTime = System.currentTimeMillis()
                            var lastSampleBytes = downloaded
                            var lastSampleTime = lastReportTime

                            suspend fun emitLiveProgress(force: Boolean = false) {
                                val now = System.currentTimeMillis()
                                if (!force && now - lastReportTime < 350L) return
                                val speedDeltaTime = (now - lastSampleTime).coerceAtLeast(1L)
                                val speedBytes = ((downloaded - lastSampleBytes) * 1000L) / speedDeltaTime
                                lastSampleTime = now
                                lastSampleBytes = downloaded
                                lastReportTime = now

                                val pct = if (totalBytes > 0L) {
                                    ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                } else 0
                                val eta = if (totalBytes > downloaded && speedBytes > 0L) {
                                    (totalBytes - downloaded) / speedBytes
                                } else null

                                val metrics = DownloadLiveMetrics(
                                    taskId = taskId,
                                    speedBytesPerSec = speedBytes.coerceAtLeast(0L),
                                    etaSeconds = eta,
                                    percent = pct,
                                    detailLabel = if (totalBytes > 0L) "$pct%" else VideoMetadataHelper.formatFileSize(downloaded),
                                    isSecureVerified = true
                                )
                                sharedLiveMetrics.update { it + (taskId to metrics) }
                                dao.updateDownloadStateDetailed(
                                    id = taskId,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes,
                                    status = DownloadTaskEntity.STATUS_DOWNLOADING,
                                    errorMessage = null,
                                    supportsPauseResume = supportsRange
                                )
                                onProgressUpdate(metrics, downloaded, totalBytes)
                            }

                            body.byteStream().use { input ->
                                destFile.parentFile?.mkdirs()
                                FileOutputStream(partFile, isAppendPartial).use { output ->
                                    while (true) {
                                        if (isStoppedCheck()) {
                                            output.flush()
                                            currentKnownBytes = downloaded
                                            throw CancellationException("Download stopped")
                                        }
                                        val read = input.read(buffer)
                                        if (read == -1) break
                                        output.write(buffer, 0, read)
                                        downloaded += read
                                        currentKnownBytes = downloaded
                                        emitLiveProgress()
                                    }
                                    output.flush()
                                }
                            }

                            var resumeAttempts = 0
                            while (supportsRange && totalBytes > 0L && downloaded < totalBytes && resumeAttempts < 3) {
                                if (isStoppedCheck()) throw CancellationException("Download stopped")
                                resumeAttempts++
                                val resumeReq = buildRequest(
                                    url = finalResolvedUrl,
                                    referer = safeReferer,
                                    origin = safeOrigin,
                                    userAgent = userAgent,
                                    rangeHeader = "bytes=$downloaded-"
                                )
                                secureClient.newCall(resumeReq).execute().use { rResp ->
                                    if (rResp.code == 206) {
                                        rResp.body?.byteStream()?.use { rInput ->
                                            FileOutputStream(partFile, true).use { rOutput ->
                                                while (true) {
                                                    if (isStoppedCheck()) {
                                                        rOutput.flush()
                                                        currentKnownBytes = downloaded
                                                        throw CancellationException("Download stopped")
                                                    }
                                                    val read = rInput.read(buffer)
                                                    if (read == -1) break
                                                    rOutput.write(buffer, 0, read)
                                                    downloaded += read
                                                    currentKnownBytes = downloaded
                                                    emitLiveProgress()
                                                }
                                                rOutput.flush()
                                            }
                                        }
                                    } else {
                                        resumeAttempts = 3
                                    }
                                }
                            }

                            if (downloaded <= 0L || !partFile.exists() || partFile.length() <= 0L) {
                                throw IllegalStateException("Empty media stream received (0 bytes)")
                            }

                            destFile.parentFile?.mkdirs()
                            if (destFile.exists()) destFile.delete()
                            val moved = partFile.renameTo(destFile)
                            if (!moved || !destFile.exists()) {
                                partFile.copyTo(destFile, overwrite = true)
                                partFile.delete()
                            }

                            val finalSize = destFile.length()
                            if (!destFile.exists() || finalSize <= 0L) {
                                throw IllegalStateException("Failed to finalize offline media file")
                            }

                            val savedSubPath = downloadOptionalSubtitle(
                                subtitleUrl = subtitleUrl,
                                destDir = destFile.parentFile ?: offlineDir,
                                baseFileName = "${destFile.nameWithoutExtension}_sub",
                                referer = safeReferer,
                                origin = safeOrigin,
                                userAgent = userAgent,
                                isStoppedCheck = isStoppedCheck
                            )

                            val doneMetrics = DownloadLiveMetrics(
                                taskId = taskId,
                                speedBytesPerSec = 0L,
                                etaSeconds = 0L,
                                percent = 100,
                                detailLabel = "Completed",
                                isSecureVerified = true
                            )
                            sharedLiveMetrics.update { it + (taskId to doneMetrics) }
                            val resolvedTotal = if (totalBytes > 0L) maxOf(totalBytes, finalSize) else finalSize
                            onProgressUpdate(doneMetrics, finalSize, resolvedTotal)

                            val fileUri = Uri.fromFile(destFile)
                            val meta = VideoMetadataHelper.extractMetadata(
                                context = context,
                                uri = fileUri,
                                videoId = taskId,
                                fallbackTitle = title
                            )
                            val resolvedMime = contentType.takeIf {
                                it.startsWith("video/") || it.startsWith("audio/") || it == "text/vtt"
                            } ?: meta.mimeType
                            val host = runCatching { URI(finalResolvedUrl).host }.getOrNull()

                            dao.upsertVideo(
                                OfflineVideoEntity(
                                    id = taskId,
                                    title = meta.title,
                                    uriString = fileUri.toString(),
                                    sourceType = "DOWNLOADED",
                                    sourceUrl = safeUrl,
                                    mimeType = resolvedMime,
                                    fileSizeBytes = finalSize,
                                    durationMs = meta.durationMs,
                                    thumbnailPath = meta.thumbnailPath,
                                    subtitlePath = savedSubPath,
                                    subtitleLanguage = subtitleLanguage,
                                    sourceHost = host
                                )
                            )
                            dao.updateDownloadStateDetailed(
                                id = taskId,
                                downloadedBytes = finalSize,
                                totalBytes = resolvedTotal,
                                status = DownloadTaskEntity.STATUS_COMPLETED,
                                errorMessage = null,
                                supportsPauseResume = supportsRange,
                                subtitlePath = savedSubPath
                            )
                        }
                    }
                }
            } catch (ce: CancellationException) {
                if (pausedTaskIds.remove(taskId)) {
                    // Preserve partial download on disk for clean Pause/Resume
                    val savedOnDisk = if (actualIsHls) {
                        destFile.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: currentKnownBytes
                    } else {
                        partFile.takeIf { it.exists() }?.length() ?: currentKnownBytes
                    }
                    dao.updateDownloadStateDetailed(
                        id = taskId,
                        downloadedBytes = savedOnDisk,
                        totalBytes = knownTotalBytes,
                        status = DownloadTaskEntity.STATUS_PAUSED,
                        errorMessage = "Paused by user",
                        supportsPauseResume = currentSupportsPauseResume || actualIsHls
                    )
                } else {
                    cleanupDest(destFile, partFile, actualIsHls)
                    dao.updateDownloadProgress(
                        id = taskId,
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        status = DownloadTaskEntity.STATUS_CANCELLED,
                        errorMessage = "Download cancelled"
                    )
                }
                throw ce
            } catch (e: Exception) {
                val keepPartial = e is java.io.IOException && currentSupportsPauseResume &&
                    (partFile.exists() || actualIsHls)
                if (!keepPartial) {
                    cleanupDest(destFile, partFile, actualIsHls)
                }
                dao.updateDownloadStateDetailed(
                    id = taskId,
                    downloadedBytes = if (keepPartial) currentKnownBytes else 0L,
                    totalBytes = knownTotalBytes,
                    status = DownloadTaskEntity.STATUS_FAILED,
                    errorMessage = e.localizedMessage ?: "Download failed",
                    supportsPauseResume = keepPartial
                )
                throw e
            } finally {
                sharedLiveMetrics.update { it - taskId }
            }
        }

        private suspend fun downloadOptionalSubtitle(
            subtitleUrl: String?,
            destDir: File,
            baseFileName: String,
            referer: String?,
            origin: String?,
            userAgent: String?,
            isStoppedCheck: () -> Boolean
        ): String? {
            val cleanSubUrl = subtitleUrl?.let {
                UrlSecurityValidator.validatePublicHttpsUrl(it).getOrNull()
            } ?: return null
            if (isStoppedCheck()) return null
            val subExt = if (cleanSubUrl.substringBefore('?').lowercase().endsWith(".srt")) "srt" else "vtt"
            val subFile = UrlSecurityValidator.resolveConfinedChild(destDir, "$baseFileName.$subExt")
            return runCatching {
                downloadSmallBinaryWithRetry(
                    url = cleanSubUrl,
                    destFile = subFile,
                    referer = referer,
                    origin = origin,
                    userAgent = userAgent,
                    rangeHeader = null,
                    isStoppedCheck = isStoppedCheck
                )
                if (subFile.exists() && subFile.length() > 0L) subFile.absolutePath else null
            }.getOrNull()
        }

        private data class HlsDownloadResult(val totalDurationMs: Long)

        private suspend fun downloadFullHlsVideo(
            dao: SpacedomDao,
            taskId: String,
            baseUrl: String,
            manifestText: String,
            targetDir: File,
            localIndexFile: File,
            referer: String?,
            origin: String?,
            userAgent: String?,
            preferredVariantUrl: String?,
            isStoppedCheck: () -> Boolean,
            onProgressUpdate: suspend (DownloadLiveMetrics, Long, Long) -> Unit
        ): HlsDownloadResult {
            if (!manifestText.contains("#EXT-X-STREAM-INF")) {
                return downloadSingleMediaPlaylist(
                    dao = dao,
                    taskId = taskId,
                    playlistBaseUrl = baseUrl,
                    mediaManifest = manifestText,
                    targetDir = targetDir,
                    localPlaylistFile = localIndexFile,
                    filePrefix = "seg",
                    referer = referer,
                    origin = origin,
                    userAgent = userAgent,
                    reportProgress = true,
                    isStoppedCheck = isStoppedCheck,
                    onProgressUpdate = onProgressUpdate
                )
            }

            val variants = parseMasterVariants(baseUrl, manifestText)
            val chosenVariant = preferredVariantUrl?.let { pref ->
                variants.firstOrNull { it.url == pref }
            } ?: variants.firstOrNull()
            val chosenVideoUrl = UrlSecurityValidator.validatePublicHttpsUrl(
                chosenVariant?.url ?: pickBestVariantUrl(baseUrl, manifestText)
                ?: throw IllegalStateException("No playable HLS variant found"),
                upgradeHttpToHttps = true
            ).getOrThrow()
            val videoManifest = fetchTextWithRetry(chosenVideoUrl, referer, origin, userAgent, isStoppedCheck)
            if (!videoManifest.contains("#EXTM3U")) {
                throw IllegalStateException("Invalid HLS variant playlist")
            }
            detectDrmProtection(videoManifest)?.let { drmMsg ->
                throw SecurityException(drmMsg)
            }

            val audioGroupId = chosenVariant?.audioGroupId
            val audioMediaUri = parseAudioMediaUri(baseUrl, manifestText, audioGroupId)

            if (audioMediaUri == null) {
                return downloadSingleMediaPlaylist(
                    dao = dao,
                    taskId = taskId,
                    playlistBaseUrl = chosenVideoUrl,
                    mediaManifest = videoManifest,
                    targetDir = targetDir,
                    localPlaylistFile = localIndexFile,
                    filePrefix = "seg",
                    referer = referer,
                    origin = origin,
                    userAgent = userAgent,
                    reportProgress = true,
                    isStoppedCheck = isStoppedCheck,
                    onProgressUpdate = onProgressUpdate
                )
            }

            val localVideoM3u8 = UrlSecurityValidator.resolveConfinedChild(targetDir, "video.m3u8")
            val localAudioM3u8 = UrlSecurityValidator.resolveConfinedChild(targetDir, "audio.m3u8")

            val videoRes = downloadSingleMediaPlaylist(
                dao = dao,
                taskId = taskId,
                playlistBaseUrl = chosenVideoUrl,
                mediaManifest = videoManifest,
                targetDir = targetDir,
                localPlaylistFile = localVideoM3u8,
                filePrefix = "vseg",
                referer = referer,
                origin = origin,
                userAgent = userAgent,
                reportProgress = true,
                isStoppedCheck = isStoppedCheck,
                onProgressUpdate = onProgressUpdate
            )

            val safeAudioUrl = UrlSecurityValidator.validatePublicHttpsUrl(audioMediaUri, upgradeHttpToHttps = true).getOrThrow()
            val audioManifest = fetchTextWithRetry(safeAudioUrl, referer, origin, userAgent, isStoppedCheck)
            downloadSingleMediaPlaylist(
                dao = dao,
                taskId = taskId,
                playlistBaseUrl = safeAudioUrl,
                mediaManifest = audioManifest,
                targetDir = targetDir,
                localPlaylistFile = localAudioM3u8,
                filePrefix = "aseg",
                referer = referer,
                origin = origin,
                userAgent = userAgent,
                reportProgress = false,
                isStoppedCheck = isStoppedCheck,
                onProgressUpdate = onProgressUpdate
            )

            val masterRewritten = buildString {
                append("#EXTM3U\n")
                append("#EXT-X-VERSION:3\n")
                append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Default\",DEFAULT=YES,AUTOSELECT=YES,URI=\"audio.m3u8\"\n")
                val bw = chosenVariant?.bandwidth?.takeIf { it > 0 } ?: 2_000_000L
                append("#EXT-X-STREAM-INF:BANDWIDTH=$bw,AUDIO=\"audio\"\n")
                append("video.m3u8\n")
            }
            localIndexFile.writeText(masterRewritten)
            return videoRes
        }

        private suspend fun downloadSingleMediaPlaylist(
            dao: SpacedomDao,
            taskId: String,
            playlistBaseUrl: String,
            mediaManifest: String,
            targetDir: File,
            localPlaylistFile: File,
            filePrefix: String,
            referer: String?,
            origin: String?,
            userAgent: String?,
            reportProgress: Boolean,
            isStoppedCheck: () -> Boolean,
            onProgressUpdate: suspend (DownloadLiveMetrics, Long, Long) -> Unit
        ): HlsDownloadResult = coroutineScope {
            val lines = mediaManifest.lines()
            val totalSegments = countHlsSegments(mediaManifest).coerceAtLeast(1)

            var durationSecondsAcc = 0.0
            var keyCounter = 0
            var segCounter = 0
            val startedAt = System.currentTimeMillis()
            var pendingByteRangeHeader: String? = null
            var lastByteRangeEnd = 0L

            data class SegmentJobSpec(
                val index: Int,
                val remoteUrl: String,
                val localName: String,
                val rangeHeader: String? = null
            )
            val segmentJobs = mutableListOf<SegmentJobSpec>()
            val templateLines = mutableListOf<String>()

            if (reportProgress) {
                dao.updateDownloadStateDetailed(
                    id = taskId,
                    downloadedBytes = 0L,
                    totalBytes = totalSegments.toLong(),
                    status = DownloadTaskEntity.STATUS_DOWNLOADING,
                    supportsPauseResume = true
                )
            }

            val assetBytesTotal = AtomicLong(0L)
            for (rawLine in lines) {
                if (isStoppedCheck()) throw CancellationException("Download cancelled")
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("#EXTINF:")) {
                    val durStr = line.removePrefix("#EXTINF:").substringBefore(',').trim()
                    durStr.toDoubleOrNull()?.let { durationSecondsAcc += it }
                    templateLines.add(line)
                } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                    val spec = line.removePrefix("#EXT-X-BYTERANGE:").trim()
                    val len = spec.substringBefore('@').toLongOrNull()
                    val start = if (spec.contains('@')) {
                        spec.substringAfter('@').toLongOrNull() ?: lastByteRangeEnd
                    } else {
                        lastByteRangeEnd
                    }
                    if (len != null && len > 0L) {
                        val end = start + len - 1L
                        pendingByteRangeHeader = "bytes=$start-$end"
                        lastByteRangeEnd = end + 1L
                    }
                } else if (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP")) {
                    val uriMatch = Regex("URI=\"([^\"]+)\"").find(line)
                    if (uriMatch != null) {
                        val assetRel = uriMatch.groupValues[1]
                        val resolvedAsset = UrlSecurityValidator.validatePublicHttpsUrl(
                            resolveUrl(playlistBaseUrl, assetRel),
                            upgradeHttpToHttps = true
                        ).getOrThrow()
                        val localAssetName = if (line.startsWith("#EXT-X-KEY")) {
                            "${filePrefix}_key_${keyCounter++}.bin"
                        } else {
                            "${filePrefix}_init_${keyCounter++}.mp4"
                        }
                        val localAssetFile = UrlSecurityValidator.resolveConfinedChild(targetDir, localAssetName)
                        val existingAssetLen = if (localAssetFile.exists() && localAssetFile.length() > 0L) {
                            localAssetFile.length()
                        } else {
                            downloadSmallBinaryWithRetry(resolvedAsset, localAssetFile, referer, origin, userAgent, null, isStoppedCheck)
                        }
                        assetBytesTotal.addAndGet(existingAssetLen)
                        templateLines.add(line.replace(uriMatch.value, "URI=\"$localAssetName\""))
                    } else {
                        templateLines.add(line)
                    }
                } else if (line.startsWith("#")) {
                    templateLines.add(line)
                } else {
                    val segUrl = UrlSecurityValidator.validatePublicHttpsUrl(
                        resolveUrl(playlistBaseUrl, line),
                        upgradeHttpToHttps = true
                    ).getOrThrow()
                    val segExt = guessSegmentExtension(segUrl)
                    val localSegName = "${filePrefix}_${segCounter}.$segExt"
                    segmentJobs.add(SegmentJobSpec(segCounter, segUrl, localSegName, pendingByteRangeHeader))
                    pendingByteRangeHeader = null
                    templateLines.add(localSegName)
                    segCounter++
                }
            }

            if (segmentJobs.isEmpty()) {
                throw IllegalStateException("HLS playlist contained 0 downloadable segments")
            }

            val completedSegs = AtomicInteger(0)
            val totalBytes = AtomicLong(assetBytesTotal.get())
            val lastDaoReport = AtomicLong(0L)
            val semaphore = Semaphore(3)

            segmentJobs.map { spec ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (isStoppedCheck()) throw CancellationException("Download cancelled")
                        val localSegFile = UrlSecurityValidator.resolveConfinedChild(targetDir, spec.localName)
                        // Resume cleanly: skip already completed segment files on disk
                        val size = if (localSegFile.exists() && localSegFile.length() > 0L) {
                            localSegFile.length()
                        } else {
                            downloadSmallBinaryWithRetry(
                                url = spec.remoteUrl,
                                destFile = localSegFile,
                                referer = referer,
                                origin = origin,
                                userAgent = userAgent,
                                rangeHeader = spec.rangeHeader,
                                isStoppedCheck = isStoppedCheck
                            )
                        }
                        val bytesNow = totalBytes.addAndGet(size)
                        val doneCount = completedSegs.incrementAndGet()

                        if (reportProgress) {
                            val now = System.currentTimeMillis()
                            val prev = lastDaoReport.get()
                            if (doneCount == totalSegments || (now - prev >= 320L && lastDaoReport.compareAndSet(prev, now))) {
                                val estimatedTotal = if (doneCount > 0) (bytesNow / doneCount) * totalSegments else 0L
                                val elapsedSec = ((now - startedAt) / 1000.0).coerceAtLeast(0.5)
                                val speed = (bytesNow / elapsedSec).toLong()
                                val pct = ((doneCount * 100) / totalSegments).coerceIn(0, 100)
                                val remainingBytes = (estimatedTotal - bytesNow).coerceAtLeast(0L)
                                val eta = if (speed > 0L) remainingBytes / speed else null

                                val metrics = DownloadLiveMetrics(
                                    taskId = taskId,
                                    speedBytesPerSec = speed,
                                    etaSeconds = eta,
                                    percent = pct,
                                    detailLabel = "Seg $doneCount/$totalSegments",
                                    isSecureVerified = true
                                )
                                sharedLiveMetrics.update { it + (taskId to metrics) }
                                dao.updateDownloadStateDetailed(
                                    id = taskId,
                                    downloadedBytes = bytesNow,
                                    totalBytes = estimatedTotal,
                                    status = DownloadTaskEntity.STATUS_DOWNLOADING,
                                    supportsPauseResume = true
                                )
                                onProgressUpdate(metrics, bytesNow, estimatedTotal)
                            }
                        }
                    }
                }
            }.awaitAll()

            localPlaylistFile.writeText(templateLines.joinToString(separator = "\n", postfix = "\n"))
            HlsDownloadResult(totalDurationMs = (durationSecondsAcc * 1000.0).toLong())
        }

        private suspend fun fetchTextWithRetry(
            url: String,
            referer: String?,
            origin: String?,
            userAgent: String?,
            isStoppedCheck: () -> Boolean
        ): String {
            var lastErr: Throwable? = null
            repeat(4) { attempt ->
                if (isStoppedCheck()) throw CancellationException("Download cancelled")
                try {
                    secureClient.newCall(buildRequest(url, referer, origin, userAgent)).execute().use { resp ->
                        if (resp.code == 403 || resp.code == 404 || resp.code == 410) {
                            throw ExpiredLinkException(resp.code, "Link expired or unavailable (HTTP ${resp.code})")
                        }
                        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                        return resp.body?.string().orEmpty()
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (expired: ExpiredLinkException) {
                    throw expired
                } catch (e: Exception) {
                    lastErr = e
                    if (attempt < 3) delay(350L * (attempt + 1))
                }
            }
            throw lastErr ?: IllegalStateException("Failed to fetch $url")
        }

        private suspend fun downloadSmallBinaryWithRetry(
            url: String,
            destFile: File,
            referer: String?,
            origin: String?,
            userAgent: String?,
            rangeHeader: String?,
            isStoppedCheck: () -> Boolean
        ): Long {
            var lastErr: Throwable? = null
            val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
            repeat(4) { attempt ->
                if (isStoppedCheck()) throw CancellationException("Download cancelled")
                try {
                    secureClient.newCall(buildRequest(url, referer, origin, userAgent, rangeHeader = rangeHeader)).execute().use { resp ->
                        if (resp.code == 403 || resp.code == 404 || resp.code == 410) {
                            throw ExpiredLinkException(resp.code, "Segment link expired (HTTP ${resp.code})")
                        }
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Segment download failed (HTTP ${resp.code})")
                        }
                        val body = resp.body ?: return 0L
                        body.byteStream().use { input ->
                            FileOutputStream(tmpFile).use { output ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    if (isStoppedCheck()) {
                                        tmpFile.delete()
                                        throw CancellationException("Download cancelled")
                                    }
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                            }
                        }
                    }
                    if (destFile.exists()) destFile.delete()
                    if (!tmpFile.renameTo(destFile)) {
                        tmpFile.copyTo(destFile, overwrite = true)
                        tmpFile.delete()
                    }
                    return destFile.length()
                } catch (ce: CancellationException) {
                    tmpFile.delete()
                    throw ce
                } catch (expired: ExpiredLinkException) {
                    tmpFile.delete()
                    throw expired
                } catch (e: Exception) {
                    tmpFile.delete()
                    lastErr = e
                    if (attempt < 3) delay(350L * (attempt + 1))
                }
            }
            throw lastErr ?: IllegalStateException("Segment download failed")
        }

        fun parseMasterVariants(baseUrl: String, masterText: String): List<HlsVariantOption> {
            val lines = masterText.lines()
            val variants = mutableListOf<HlsVariantOption>()
            var pendingInf: String? = null

            for (raw in lines) {
                val line = raw.trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    pendingInf = line
                } else if (line.isNotEmpty() && !line.startsWith("#") && pendingInf != null) {
                    val inf = pendingInf!!
                    pendingInf = null
                    val resolved = resolveUrl(baseUrl, line)
                    if (!UrlSecurityValidator.isSafePublicHttpsUrl(resolved)) continue
                    val shield = ContentBlockerEngine.evaluateProviderOrMediaUrl(resolved)
                    if (shield.blocked) continue

                    val bw = Regex("BANDWIDTH=(\\d+)").find(inf)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val resMatch = Regex("RESOLUTION=(\\d+)x(\\d+)").find(inf)
                    val width = resMatch?.groupValues?.get(1)?.toIntOrNull()
                    val height = resMatch?.groupValues?.get(2)?.toIntOrNull()
                    val resLabel = height?.let { if (it >= 2160) "4K (${it}p)" else "${it}p" }
                    val audioGroup = Regex("AUDIO=\"([^\"]+)\"").find(inf)?.groupValues?.get(1)
                    val subtitleGroup = Regex("SUBTITLES=\"([^\"]+)\"").find(inf)?.groupValues?.get(1)
                    val codecs = Regex("CODECS=\"([^\"]+)\"").find(inf)?.groupValues?.get(1)
                    val mbps = if (bw > 0L) String.format(java.util.Locale.US, "%.1f Mbps", bw / 1_000_000.0) else null
                    val label = buildString {
                        append(height?.let { "${it}p" } ?: "Stream")
                        if (mbps != null) append(" • $mbps")
                    }
                    variants.add(
                        HlsVariantOption(
                            url = resolved,
                            label = label,
                            bandwidth = bw,
                            audioGroupId = audioGroup,
                            width = width,
                            height = height,
                            resolutionLabel = resLabel,
                            codecs = codecs,
                            subtitleGroupId = subtitleGroup
                        )
                    )
                }
            }
            return variants.sortedByDescending { it.bandwidth }
        }

        fun parseAudioMediaTracks(baseUrl: String, masterText: String): List<MediaAudioTrackOption> {
            val result = mutableListOf<MediaAudioTrackOption>()
            for ((index, raw) in masterText.lines().withIndex()) {
                val line = raw.trim()
                if (!line.startsWith("#EXT-X-MEDIA") || !line.contains("TYPE=AUDIO")) continue
                val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: continue
                val resolved = resolveUrl(baseUrl, uri)
                if (!UrlSecurityValidator.isSafePublicHttpsUrl(resolved) ||
                    ContentBlockerEngine.evaluateProviderOrMediaUrl(resolved).blocked
                ) continue

                val name = Regex("NAME=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "Audio ${index + 1}"
                val language = Regex("LANGUAGE=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                val codecs = Regex("CODECS=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                val isDefault = line.contains("DEFAULT=YES", ignoreCase = true)
                result.add(
                    MediaAudioTrackOption(
                        id = "hls_audio_${resolved.hashCode()}",
                        url = resolved,
                        label = name,
                        language = language,
                        codecs = codecs ?: "AAC/HLS",
                        bitrateBps = 0L,
                        contentLengthBytes = 0L,
                        mimeType = "application/x-mpegURL",
                        fileExtension = "m3u8",
                        isDefault = isDefault,
                        isSeparateStream = true
                    )
                )
            }
            return result.distinctBy { it.url }
        }

        fun parseSubtitleMediaTracks(baseUrl: String, masterText: String): List<MediaSubtitleTrackOption> {
            val result = mutableListOf<MediaSubtitleTrackOption>()
            for ((index, raw) in masterText.lines().withIndex()) {
                val line = raw.trim()
                if (!line.startsWith("#EXT-X-MEDIA") || !line.contains("TYPE=SUBTITLES")) continue
                val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: continue
                val resolved = resolveUrl(baseUrl, uri)
                if (!UrlSecurityValidator.isSafePublicHttpsUrl(resolved) ||
                    ContentBlockerEngine.evaluateProviderOrMediaUrl(resolved).blocked
                ) continue

                val name = Regex("NAME=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "Subtitle ${index + 1}"
                val language = Regex("LANGUAGE=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                val lower = resolved.substringBefore('?').lowercase()
                val format = when {
                    lower.endsWith(".srt") -> "SRT"
                    lower.endsWith(".vtt") -> "VTT"
                    else -> "VTT/HLS"
                }
                result.add(
                    MediaSubtitleTrackOption(
                        id = "hls_sub_${resolved.hashCode()}",
                        url = resolved,
                        label = name,
                        language = language,
                        format = format,
                        mimeType = if (format == "SRT") "application/x-subrip" else "text/vtt"
                    )
                )
            }
            return result.distinctBy { it.url }
        }

        private fun parseAudioMediaUri(baseUrl: String, masterText: String, targetGroupId: String?): String? {
            for (raw in masterText.lines()) {
                val line = raw.trim()
                if (line.startsWith("#EXT-X-MEDIA") && line.contains("TYPE=AUDIO")) {
                    val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: continue
                    if (targetGroupId != null) {
                        val gid = Regex("GROUP-ID=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                        if (gid != null && gid != targetGroupId) continue
                    }
                    val resolved = resolveUrl(baseUrl, uri)
                    if (UrlSecurityValidator.isSafePublicHttpsUrl(resolved) &&
                        !ContentBlockerEngine.evaluateProviderOrMediaUrl(resolved).blocked
                    ) {
                        return resolved
                    }
                }
            }
            return null
        }

        private fun countHlsSegments(manifest: String): Int =
            manifest.lines().count { line ->
                val t = line.trim()
                t.isNotEmpty() && !t.startsWith("#")
            }

        private fun sumHlsDurationMs(manifest: String): Long {
            var sec = 0.0
            for (raw in manifest.lines()) {
                val line = raw.trim()
                if (line.startsWith("#EXTINF:")) {
                    line.removePrefix("#EXTINF:").substringBefore(',').trim().toDoubleOrNull()?.let { sec += it }
                }
            }
            return (sec * 1000.0).toLong()
        }

        private fun pickBestVariantUrl(baseUrl: String, masterText: String): String? {
            return parseMasterVariants(baseUrl, masterText).firstOrNull()?.url
        }

        private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
            return runCatching {
                URI(baseUrl).resolve(relativeOrAbsolute).toString()
            }.getOrElse { relativeOrAbsolute }
        }

        private fun buildRequest(
            url: String,
            referer: String?,
            origin: String?,
            userAgent: String?,
            isHead: Boolean = false,
            rangeHeader: String? = null
        ): Request {
            val ua = userAgent?.takeIf { it.isNotBlank() }
                ?: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .header("Accept", "*/*")

            if (isHead) builder.head()
            if (!rangeHeader.isNullOrBlank()) builder.header("Range", rangeHeader)

            if (!referer.isNullOrBlank()) {
                builder.header("Referer", referer)
                val resolvedOrigin = origin?.takeIf { it.isNotBlank() } ?: runCatching {
                    val parsed = URI(referer)
                    if (!parsed.scheme.isNullOrBlank() && !parsed.host.isNullOrBlank()) {
                        "${parsed.scheme}://${parsed.host}"
                    } else null
                }.getOrNull()
                if (!resolvedOrigin.isNullOrBlank()) {
                    builder.header("Origin", resolvedOrigin)
                }
            } else if (!origin.isNullOrBlank()) {
                builder.header("Origin", origin)
            }

            runCatching {
                val cookie = CookieManager.getInstance().getCookie(url)
                    ?: referer?.let { CookieManager.getInstance().getCookie(it) }
                if (!cookie.isNullOrBlank()) {
                    builder.header("Cookie", cookie)
                }
            }
            return builder.build()
        }

        private fun cleanupDest(destFile: File, partFile: File, isHls: Boolean) {
            runCatching {
                partFile.delete()
                if (isHls) {
                    destFile.parentFile?.deleteRecursively()
                } else {
                    destFile.delete()
                }
            }
        }

        private fun isHlsStream(url: String): Boolean {
            val clean = url.substringBefore('?').lowercase()
            return clean.endsWith(".m3u8") || url.lowercase().contains(".m3u8")
        }

        private fun guessQualityFromUrl(url: String): String? {
            val lower = url.lowercase()
            return when {
                lower.contains("2160") || lower.contains("4k") -> "4K"
                lower.contains("1440") -> "1440p"
                lower.contains("1080") -> "1080p"
                lower.contains("720") -> "720p"
                lower.contains("480") -> "480p"
                lower.contains("360") -> "360p"
                else -> null
            }
        }

        private fun guessExtension(
            url: String,
            contentType: String? = null,
            contentDisposition: String? = null
        ): String {
            val lower = url.substringBefore('?').lowercase()
            val ct = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val cd = contentDisposition?.lowercase().orEmpty()
            return when {
                lower.endsWith(".mp3") || ct == "audio/mpeg" || cd.contains(".mp3") -> "mp3"
                lower.endsWith(".m4a") || ct == "audio/mp4" || cd.contains(".m4a") -> "m4a"
                lower.endsWith(".aac") || ct == "audio/aac" || cd.contains(".aac") -> "aac"
                lower.endsWith(".ogg") || ct == "audio/ogg" || cd.contains(".ogg") -> "ogg"
                lower.endsWith(".vtt") || ct == "text/vtt" || cd.contains(".vtt") -> "vtt"
                lower.endsWith(".srt") || ct == "application/x-subrip" || cd.contains(".srt") -> "srt"
                lower.endsWith(".mkv") || ct.contains("matroska") || cd.contains(".mkv") -> "mkv"
                lower.endsWith(".webm") || ct.contains("webm") || cd.contains(".webm") -> "webm"
                lower.endsWith(".mov") || ct.contains("quicktime") || cd.contains(".mov") -> "mov"
                else -> "mp4"
            }
        }

        private fun guessSegmentExtension(url: String): String {
            val lower = url.substringBefore('?').lowercase()
            return when {
                lower.endsWith(".m4s") -> "m4s"
                lower.endsWith(".mp4") -> "mp4"
                lower.endsWith(".aac") -> "aac"
                else -> "ts"
            }
        }

        fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0L) return "0 KB/s"
            val kb = bytesPerSec / 1024.0
            if (kb < 1024.0) return String.format(java.util.Locale.US, "%.0f KB/s", kb)
            val mb = kb / 1024.0
            return String.format(java.util.Locale.US, "%.1f MB/s", mb)
        }

        fun formatEta(seconds: Long?): String {
            if (seconds == null || seconds <= 0L) return ""
            val m = seconds / 60
            val s = seconds % 60
            return if (m > 0) "${m}m ${s}s left" else "${s}s left"
        }
    }
}
