package com.example.security

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class FilterCategory(val label: String) {
    ADS("Ads & Banners"),
    TRACKERS("Trackers"),
    MALWARE("Malware & Badware"),
    ANNOYANCES("Annoyances & Popups")
}

data class FilterSubscription(
    val id: String,
    val name: String,
    val vendor: String,
    val description: String,
    val category: FilterCategory,
    val url: String,
    val enabledByDefault: Boolean = true
)

data class FilterListStatus(
    val id: String,
    val ruleCount: Int = 0,
    val lastUpdatedMs: Long = 0L,
    val isCached: Boolean = false,
    val lastError: String? = null,
    val version: String = "",
    val sha256: String = ""
)

data class BlockDecision(
    val blocked: Boolean,
    val category: FilterCategory? = null,
    val matchedRule: String? = null
)

data class ShieldMetrics(
    val totalBlocked: Int = 0,
    val adsBlocked: Int = 0,
    val trackersBlocked: Int = 0,
    val malwareBlocked: Int = 0,
    val popupsBlocked: Int = 0,
    val activeRuleCount: Int = 0,
    val cosmeticRuleCount: Int = 0,
    val lastSyncedMs: Long = 0L,
    val cacheLoadedFast: Boolean = false
)

data class ShieldConfig(
    val masterEnabled: Boolean = true,
    val blockAds: Boolean = true,
    val blockTrackers: Boolean = true,
    val blockMalware: Boolean = true,
    val blockPopups: Boolean = true,
    val cosmeticFiltering: Boolean = true,
    val enabledListIds: Set<String> = ContentBlockerEngine.DEFAULT_ENABLED_LIST_IDS,
    val customBlockedDomains: Set<String> = emptySet(),
    val whitelistedDomains: Set<String> = emptySet()
)

/**
 * Lightweight native network & standard-CSS cosmetic filter engine.
 *
 * Supported rule subset (EasyList / EasyPrivacy / AdGuard / Adblock / Hosts):
 * - Domain & subdomain blocks: `||domain.com^`
 * - Host-file entries: `0.0.0.0 domain.com`, `127.0.0.1 domain.com`
 * - Exception allowlists: `@@||domain.com^` (`$elemhide`, `$generichide`, `$document`)
 * - Rule modifiers: `$third-party`/`$3p`, `$first-party`/`$1p`, `$popup`, `$domain=...`, `$badfilter`
 * - Literal path/URL tokens extracted from path & simple wildcard rules
 * - Standard CSS hiding rules: `##selector` and `domain##selector` (validated against unsupported
 *   extended procedural selectors so Chromium never drops valid CSS blocks).
 *
 * Unsupported procedural JS scriptlets (`##+js(...)`, `:has-text()`, `:style()`, `:-abp-*`) are
 * ignored cleanly rather than injected as broken CSS.
 */
object ContentBlockerEngine {

    private const val MAX_LINES_PER_SUBSCRIPTION = 85_000
    private const val MAX_PATH_TOKENS_PER_CATEGORY = 480
    private const val MAX_GLOBAL_COSMETIC_SELECTORS = 360
    private const val COMPILED_CACHE_FILE = "compiled_shield_v3.cache"
    private const val LEGACY_COMPILED_CACHE_FILE = "compiled_shield_v2.cache"
    private const val FILTER_META_FILE = "filter_meta.json"
    private const val COMPILED_CACHE_MAGIC_INT = 0x4E564C33 // "NVL3"
    private const val COMPILED_CACHE_FORMAT_VERSION = 3
    private const val COMPILED_CACHE_END_SENTINEL = 0x5A454E44 // "ZEND"
    private const val DECISION_CACHE_MAX = 512

    val SUBSCRIPTIONS: List<FilterSubscription> = listOf(
        FilterSubscription(
            id = "adguard_base",
            name = "AdGuard Base Filter",
            vendor = "AdGuard",
            description = "Core web banner, overlay & video ad blocking rules",
            category = FilterCategory.ADS,
            url = "https://filters.adtidy.org/android/filters/2.txt",
            enabledByDefault = true
        ),
        FilterSubscription(
            id = "adguard_mobile",
            name = "AdGuard Mobile Ads",
            vendor = "AdGuard",
            description = "Mobile banner, interstitial & redirect ad rules",
            category = FilterCategory.ADS,
            url = "https://filters.adtidy.org/android/filters/11.txt",
            enabledByDefault = true
        ),
        FilterSubscription(
            id = "adguard_tracking",
            name = "AdGuard Tracking Protection",
            vendor = "AdGuard",
            description = "Blocks invisible trackers, pixels & telemetry beacons",
            category = FilterCategory.TRACKERS,
            url = "https://filters.adtidy.org/android/filters/3.txt",
            enabledByDefault = true
        ),
        FilterSubscription(
            id = "ubo_badware",
            name = "uBlock Badware Risks",
            vendor = "uBlock Origin",
            description = "Blocks known malware, scam & hijack redirect hosts",
            category = FilterCategory.MALWARE,
            url = "https://ublockorigin.github.io/uAssets/filters/badware.txt",
            enabledByDefault = true
        ),
        FilterSubscription(
            id = "easylist_core",
            name = "EasyList Web Ads",
            vendor = "EasyList",
            description = "Standard international banner & popup filter list",
            category = FilterCategory.ADS,
            url = "https://easylist.to/easylist/easylist.txt",
            enabledByDefault = false
        ),
        FilterSubscription(
            id = "easyprivacy",
            name = "EasyPrivacy",
            vendor = "EasyList",
            description = "Blocks third-party analytics & tracking scripts",
            category = FilterCategory.TRACKERS,
            url = "https://easylist.to/easylist/easyprivacy.txt",
            enabledByDefault = false
        ),
        FilterSubscription(
            id = "ubo_filters",
            name = "uBlock Origin – Filters",
            vendor = "uBlock Origin",
            description = "Additional ad network & popunder rules",
            category = FilterCategory.ADS,
            url = "https://ublockorigin.github.io/uAssets/filters/filters.txt",
            enabledByDefault = false
        ),
        FilterSubscription(
            id = "ubo_privacy",
            name = "uBlock Origin – Privacy",
            vendor = "uBlock Origin",
            description = "Additional telemetry & beacon filters",
            category = FilterCategory.TRACKERS,
            url = "https://ublockorigin.github.io/uAssets/filters/privacy.txt",
            enabledByDefault = false
        ),
        FilterSubscription(
            id = "urlhaus_malware",
            name = "Malicious URL Blocklist (URLhaus)",
            vendor = "URLhaus",
            description = "Real-time malware distribution & phishing domains",
            category = FilterCategory.MALWARE,
            url = "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-ag-online.txt",
            enabledByDefault = false
        ),
        FilterSubscription(
            id = "adguard_annoyances",
            name = "AdGuard Annoyances",
            vendor = "AdGuard",
            description = "Blocks push traps, floating banners & sticky popups",
            category = FilterCategory.ANNOYANCES,
            url = "https://filters.adtidy.org/android/filters/14.txt",
            enabledByDefault = false
        ),
        FilterSubscription(
            id = "adguard_regional_ru",
            name = "AdGuard Cyrillic / RU Regional",
            vendor = "Regional",
            description = "Optional regional ads & banners for Russian/CIS sites",
            category = FilterCategory.ADS,
            url = "https://filters.adtidy.org/android/filters/1.txt",
            enabledByDefault = false
        ),
        FilterSubscription(
            id = "adguard_regional_es_pt",
            name = "AdGuard Spanish & Portuguese",
            vendor = "Regional",
            description = "Optional regional ads for Spanish & Portuguese sites",
            category = FilterCategory.ADS,
            url = "https://filters.adtidy.org/android/filters/9.txt",
            enabledByDefault = false
        ),
        FilterSubscription(
            id = "adguard_regional_in",
            name = "AdGuard Indian Regional",
            vendor = "Regional",
            description = "Optional regional ads for Hindi & Indian sites",
            category = FilterCategory.ADS,
            url = "https://filters.adtidy.org/android/filters/233.txt",
            enabledByDefault = false
        )
    )

    val DEFAULT_ENABLED_LIST_IDS: Set<String> = SUBSCRIPTIONS
        .filter { it.enabledByDefault }
        .map { it.id }
        .toSet()

    private val BUILTIN_AD_HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "imasdk.googleapis.com", "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com", "adnxs.com", "appnexus.com", "criteo.com",
        "criteo.net", "pubmatic.com", "rubiconproject.com", "openx.net",
        "casalemedia.com", "smartadserver.com", "yieldmo.com", "sharethrough.com",
        "33across.com", "adsrvr.org", "media.net", "undertone.com", "sovrn.com",
        "taboola.com", "outbrain.com", "mgid.com", "revcontent.com", "zergnet.com",
        "adsterra.com", "highperformanceformat.com", "profitablegatecpm.com",
        "topcreativeformat.com", "exoclick.com", "exosrv.com", "realsrv.com",
        "syndication.realsrv.com", "trafficjunky.net", "trafficjunky.com",
        "juicyads.com", "tsyndicate.com", "hilltopads.net", "monetag.com",
        "galaksion.com", "admaven.com", "ad-maven.com", "clickadu.com", "acscdn.com",
        "dtscout.com", "bidvertiser.com", "infolinks.com", "chitika.com",
        "adskeeper.co.uk", "adskeeper.com", "trafficstars.com", "ero-advertising.com",
        "plugrush.com", "trafficforce.com", "betteradsystem.com", "a-ads.com",
        "coinzilla.com", "bitmedia.io", "ads.yahoo.com", "advertising.com",
        "amazon-adsystem.com", "moatads.com", "adsbynimbus.com", "adcolony.com",
        "vungle.com", "unityads.unity3d.com", "applovin.com", "ironsrc.com",
        "chartboost.com", "fyber.com", "mads.amazon-adsystem.com", "aan.amazon.com",
        "servedbyadbutler.com", "lijit.com", "contextweb.com", "adform.net",
        "adroll.com", "teads.tv", "gumgum.com", "kargo.com", "triplelift.com",
        "indexexchange.com", "3lift.com", "spotxchange.com", "springserve.com"
    )

    private val BUILTIN_POPUP_HOSTS = setOf(
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com",
        "onclickalgo.com", "onclkds.com", "richpush.co", "pushub.net", "pushame.com",
        "pushwhy.com", "clksite.com", "clkmon.com", "adxpansion.com",
        "adf.ly", "ouo.io", "ouo.press", "shrinkme.io", "exe.io", "cpmlink.net",
        "sh.st", "festyy.com", "gestyy.com", "bc.vc", "shorte.st",
        "notix.io", "gravitec.net", "click-track-redirect.com", "trk-srv.com",
        "popundertotal.com", "popmyads.com", "popwin.net", "onclickmax.com"
    )

    private val BUILTIN_TRACKER_HOSTS = setOf(
        "google-analytics.com", "ssl.google-analytics.com", "googletagmanager.com",
        "analytics.google.com", "mc.yandex.ru", "yandex.ru", "pixel.facebook.com",
        "connect.facebook.net", "analytics.tiktok.com", "business-api.tiktok.com",
        "hotjar.com", "static.hotjar.com", "clarity.ms", "mixpanel.com",
        "api.mixpanel.com", "amplitude.com", "api.amplitude.com", "segment.io",
        "api.segment.io", "cdn.segment.com", "fullstory.com", "crazyegg.com",
        "scorecardresearch.com", "sb.scorecardresearch.com", "quantserve.com",
        "pixel.quantserve.com", "omtrdc.net", "demdex.net", "bluekai.com",
        "krxd.net", "newrelic.com", "js-agent.newrelic.com", "nr-data.net",
        "sentry.io", "bugsnag.com", "heapanalytics.com", "mouseflow.com",
        "luckyorange.com", "histats.com", "sstatic1.histats.com", "whos.amung.us",
        "amung.us", "supercounters.com", "statcounter.com", "c.statcounter.com",
        "branch.io", "app.link", "adjust.com", "appsflyer.com", "kochava.com"
    )

    private val BUILTIN_MALWARE_HOSTS = setOf(
        "coinhive.com", "coin-hive.com", "cnhv.co", "cryptoloot.pro", "crypto-loot.com",
        "jsecoin.com", "minero.cc", "webminepool.com", "authedmine.com", "miner.pr0gramm.com",
        "iplogger.org", "iplogger.com", "grabify.link", "yip.su", "bmwforum.co",
        "browser-update.org", "redirectvoluum.com", "voluumtrk.com", "go2cloud.org",
        "trackingclk.com", "track.ad", "malwarebytes-scam.example",
        "flashplayer-update.com", "getflashplayer.info", "cleanmypc.com",
        "mackeeper.com", "mackeeperapp.zeobit.com", "pcoptimizerpro.com",
        "driverupdate.net", "windows-defender-alert.com", "virus-scan-alert.com",
        "free-iphone-winner.com", "claim-prize-now.com", "verify-human-captcha.com",
        "push-notifications.io", "sendpulse.com", "rd.rakuten.co.jp"
    )

    private val BUILTIN_AD_PATH_TOKENS = listOf(
        "/pagead/", "/adsid/", "/adserver/", "/vast.xml", "/vast/", "/vmap.xml",
        "adblock-detect", "antiadblock", "/doubleclick/", "google_ads",
        "googlesyndication", "adsterra", "exoclick", "propellerads",
        "highperformanceformat", "profitablegatecpm", "/adview?", "/adclick?",
        "/adsystem/", "/banner_ads/", "/sponsored_ads/", "/banners/",
        "/banner/", "/ads/banner", "/ad-banner", "_banner.", "promo-banner"
    )

    private val BUILTIN_POPUP_PATH_TOKENS = listOf(
        "/popunder", "/pop_under", "popunder.js", "/popads", "/popup_ad", "popup-ad"
    )

    private val BUILTIN_TRACKER_PATH_TOKENS = listOf(
        "/gtag/js", "/analytics.js", "/fbevents.js", "/pixel.gif", "/collect?v=",
        "/tr/?id=", "/telemetry/", "/beacon.js", "fingerprintjs", "/fp.min.js"
    )

    private val BUILTIN_MALWARE_PATH_TOKENS = listOf(
        "coinhive.min.js", "cryptonight.wasm", "fake-update", "download-codec",
        "install_flash_player", "setup_player.exe", "android_update.apk",
        "/phishing/", "/malware/", "wp-content/uploads/ads-redirect"
    )

    // Curated, safe standard-CSS selectors targeting banner/container/popup ads
    // Explicitly avoids matching <video> or player containers.
    private val BUILTIN_COSMETIC_SELECTORS = listOf(
        "ins.adsbygoogle",
        "[id^=\"google_ads_\"]",
        "[id*=\"div-gpt-ad\"]",
        "[data-ad-slot]",
        "[data-ad-client]",
        "[data-google-query-id]",
        "[class*=\"ad-banner\"]:not(video):not([class*=\"player\"])",
        "[class*=\"banner-ad\"]:not(video):not([class*=\"player\"])",
        "[class*=\"banner_ad\"]:not(video):not([class*=\"player\"])",
        "[id*=\"banner-ad\"]:not([id*=\"player\"])",
        "[id*=\"ad-banner\"]:not([id*=\"player\"])",
        "[class*=\"ad-slot\"]:not([class*=\"player\"])",
        "[class*=\"ad-container\"]:not([class*=\"player\"])",
        "[class*=\"ad-wrapper\"]:not([class*=\"player\"])",
        "[class*=\"sponsored-ad\"]:not([class*=\"player\"])",
        "[class*=\"sponsored_ad\"]:not([class*=\"player\"])",
        "[class*=\"ad-overlay\"]:not([class*=\"player\"])",
        "[class*=\"popup-ad\"]:not([class*=\"player\"])",
        "[class*=\"popunder\"]",
        "[id*=\"adsterra\"]",
        "[class*=\"adsterra\"]",
        "iframe[src*=\"doubleclick\"]",
        "iframe[src*=\"googlesyndication\"]",
        "iframe[src*=\"adsterra\"]",
        "iframe[src*=\"exoclick\"]",
        "iframe[src*=\"popads\"]",
        "iframe[src*=\"propeller\"]",
        "iframe[src*=\"realsrv\"]",
        "iframe[src*=\"highperformanceformat\"]",
        "a[href*=\"adsterra\"]",
        "a[href*=\"exoclick\"]",
        "a[href*=\"popads\"]",
        "div[style*=\"z-index: 2147483647\"]",
        "div[style*=\"z-index:2147483647\"]"
    )

    private val SAFE_MEDIA_AND_ASSET_EXTS = setOf(
        ".mp4", ".m3u8", ".mpd", ".ts", ".m4s", ".webm", ".mkv", ".mov",
        ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".vtt", ".srt", ".ass", ".ssa",
        ".woff2", ".woff", ".ttf"
    )

    private val SAFE_STREAM_DELIVERY_EXTS = setOf(
        ".mp4", ".m3u8", ".mpd", ".ts", ".m4s", ".webm", ".mkv", ".mov",
        ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".vtt", ".srt", ".ass", ".ssa"
    )

    private class CompiledRuleset(
        val adHosts: HashSet<String>,
        val thirdPartyAdHosts: HashSet<String>,
        val popupHosts: HashSet<String>,
        val trackerHosts: HashSet<String>,
        val thirdPartyTrackerHosts: HashSet<String>,
        val malwareHosts: HashSet<String>,
        val exceptionHosts: HashSet<String>,
        val elemhideExceptionHosts: HashSet<String>,
        val domainScopedAdHosts: Map<String, HashSet<String>>,
        val adPathTokens: Array<String>,
        val popupPathTokens: Array<String>,
        val trackerPathTokens: Array<String>,
        val malwarePathTokens: Array<String>,
        val globalCosmeticSelectors: List<String>,
        val domainCosmeticSelectors: Map<String, List<String>>,
        val totalRuleCount: Int,
        val loadedFromCompiledCache: Boolean = false
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
        .build()

    private val initialized = AtomicBoolean(false)
    private var appContext: Context? = null

    private val _subscriptionStatuses = MutableStateFlow<Map<String, FilterListStatus>>(emptyMap())
    val subscriptionStatuses: StateFlow<Map<String, FilterListStatus>> = _subscriptionStatuses.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    @Volatile
    private var currentConfig = ShieldConfig()

    @Volatile
    private var compiledRuleset: CompiledRuleset = buildBuiltinRuleset(ShieldConfig())

    private val totalBlockedCounter = AtomicInteger(0)
    private val adsBlockedCounter = AtomicInteger(0)
    private val trackersBlockedCounter = AtomicInteger(0)
    private val malwareBlockedCounter = AtomicInteger(0)
    private val popupsBlockedCounter = AtomicInteger(0)
    private val lastMetricsUiEmitMs = AtomicLong(0L)

    // Bounded O(1) lookup cache keyed by "main|pageHost|url" to avoid re-scanning repeated subresources
    private val decisionCache: MutableMap<String, BlockDecision> = Collections.synchronizedMap(
        object : LinkedHashMap<String, BlockDecision>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BlockDecision>?): Boolean {
                return size > DECISION_CACHE_MAX
            }
        }
    )

    private val _metrics = MutableStateFlow(
        ShieldMetrics(
            activeRuleCount = compiledRuleset.totalRuleCount,
            cosmeticRuleCount = compiledRuleset.globalCosmeticSelectors.size
        )
    )
    val metrics: StateFlow<ShieldMetrics> = _metrics.asStateFlow()

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rebuildLock = Any()
    private val rebuildRunning = AtomicBoolean(false)
    private val rebuildDirty = AtomicBoolean(false)

    @Volatile
    private var lastLoadedFingerprint: String = ""

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized.compareAndSet(false, true)) {
            scheduleAsyncRebuild()
        }
    }

    fun updateConfig(config: ShieldConfig) {
        val prev = currentConfig
        currentConfig = config
        decisionCache.clear()
        if (prev.enabledListIds != config.enabledListIds ||
            prev.customBlockedDomains != config.customBlockedDomains ||
            prev.whitelistedDomains != config.whitelistedDomains
        ) {
            scheduleAsyncRebuild()
        }
    }

    fun resetToDefaults() {
        val defaults = ShieldConfig()
        currentConfig = defaults
        decisionCache.clear()
        resetSessionCounter()
        scheduleAsyncRebuild()
    }

    internal fun resetInMemoryFingerprintForTest() {
        synchronized(rebuildLock) {
            lastLoadedFingerprint = ""
            decisionCache.clear()
        }
    }

    private fun scheduleAsyncRebuild() {
        rebuildDirty.set(true)
        if (!rebuildRunning.compareAndSet(false, true)) return
        initScope.launch {
            try {
                while (rebuildDirty.getAndSet(false)) {
                    rebuildRuleset()
                }
            } finally {
                rebuildRunning.set(false)
                if (rebuildDirty.get()) {
                    scheduleAsyncRebuild()
                }
            }
        }
    }

    fun getConfig(): ShieldConfig = currentConfig

    fun rebuildRuleset() {
        synchronized(rebuildLock) {
            val snapshotCfg = currentConfig
            val ctx = appContext
            val ruleset = if (ctx == null) {
                buildBuiltinRuleset(snapshotCfg)
            } else {
                buildOrLoadCompiledRuleset(ctx, snapshotCfg)
            }
            compiledRuleset = ruleset
            decisionCache.clear()
            _metrics.update {
                it.copy(
                    activeRuleCount = ruleset.totalRuleCount,
                    cosmeticRuleCount = ruleset.globalCosmeticSelectors.size +
                        ruleset.domainCosmeticSelectors.values.sumOf { list -> list.size },
                    cacheLoadedFast = ruleset.loadedFromCompiledCache
                )
            }
        }
    }

    /**
     * Evaluates whether a URL should be blocked by internal network filter rules.
     * Optimized for WebView's hot shouldInterceptRequest path: fast host parsing, O(1) decision cache,
     * and zero allocations on common clean same-site media chunks.
     */
    fun evaluateUrl(
        rawUrl: String,
        pageUrl: String? = null,
        isMainFrame: Boolean = false
    ): BlockDecision {
        val cfg = currentConfig
        if (!cfg.masterEnabled) return NOT_BLOCKED

        val targetHost = extractHostFast(rawUrl) ?: return NOT_BLOCKED
        val pageHost = extractHostFast(pageUrl)

        val cacheKey = "${if (isMainFrame) '1' else '0'}|${pageHost.orEmpty()}|$rawUrl"
        decisionCache[cacheKey]?.let { return it }

        val decision = evaluateUncached(rawUrl, targetHost, pageHost, isMainFrame, cfg)
        decisionCache[cacheKey] = decision
        return decision
    }

    private fun evaluateUncached(
        rawUrl: String,
        targetHost: String,
        pageHost: String?,
        isMainFrame: Boolean,
        cfg: ShieldConfig
    ): BlockDecision {
        val rules = compiledRuleset
        val isWhitelistedTarget = matchesHostSet(targetHost, rules.exceptionHosts) ||
            matchesConfigSet(targetHost, cfg.whitelistedDomains)
        val isWhitelistedPage = pageHost != null && (
            matchesHostSet(pageHost, rules.exceptionHosts) ||
                matchesConfigSet(pageHost, cfg.whitelistedDomains)
            )

        // Short-circuit clean static media segments when not matching any malware/ad host
        val isThirdParty = pageHost == null || !isSameSiteFast(targetHost, pageHost)
        val qIdx = rawUrl.indexOf('?')
        val pathNoQuery = if (qIdx >= 0) rawUrl.substring(0, qIdx) else rawUrl

        val lowerUrl = rawUrl.lowercase()

        // 1. Malware / Badware shield ALWAYS runs (unless explicit target exception)
        if (cfg.blockMalware && !isWhitelistedTarget) {
            if (matchesHostSet(targetHost, rules.malwareHosts)) {
                return BlockDecision(true, FilterCategory.MALWARE, "||$targetHost^")
            }
            for (token in rules.malwarePathTokens) {
                if (lowerUrl.contains(token)) {
                    return BlockDecision(true, FilterCategory.MALWARE, token)
                }
            }
        }

        if (isWhitelistedTarget || isWhitelistedPage) {
            return NOT_BLOCKED
        }

        // Preserve security verification / Cloudflare challenge frames unconditionally
        if (StreamRedirectGuard.isChallengeHost(targetHost) || lowerUrl.contains("/cdn-cgi/")) {
            return NOT_BLOCKED
        }

        // Fast-path clean first-party media/font delivery
        val hasSafeMediaExt = hasCleanExt(pathNoQuery, SAFE_STREAM_DELIVERY_EXTS)
        if (!isThirdParty && !isMainFrame && hasCleanExt(pathNoQuery, SAFE_MEDIA_AND_ASSET_EXTS)) {
            return NOT_BLOCKED
        }

        // Preserve third-party CDN video/audio/subtitle streams unless hosted on a known ad/popup domain or VAST ad-tag
        if (hasSafeMediaExt && !isMainFrame) {
            val onKnownAdOrPopupHost = matchesHostSet(targetHost, rules.adHosts) ||
                matchesHostSet(targetHost, rules.popupHosts) ||
                matchesConfigSet(targetHost, cfg.customBlockedDomains)
            val isAdVideoTag = lowerUrl.contains("/vast") || lowerUrl.contains("/vmap") || lowerUrl.contains("/adserver/")
            if (!onKnownAdOrPopupHost && !isAdVideoTag) {
                return NOT_BLOCKED
            }
        }

        // 2. Pop-up domain & token rules
        if (cfg.blockPopups) {
            if (matchesHostSet(targetHost, rules.popupHosts)) {
                return BlockDecision(true, FilterCategory.ANNOYANCES, "||$targetHost^\$popup")
            }
            if (!isMainFrame || isThirdParty) {
                for (token in rules.popupPathTokens) {
                    if (lowerUrl.contains(token)) {
                        return BlockDecision(true, FilterCategory.ANNOYANCES, token)
                    }
                }
            }
        }

        // 3. Ads & Banners (host set, third-party host set, domain-scoped rules, path tokens)
        if (cfg.blockAds) {
            if (matchesHostSet(targetHost, rules.adHosts) || matchesConfigSet(targetHost, cfg.customBlockedDomains)) {
                return BlockDecision(true, FilterCategory.ADS, "||$targetHost^")
            }
            if (isThirdParty && matchesHostSet(targetHost, rules.thirdPartyAdHosts)) {
                return BlockDecision(true, FilterCategory.ADS, "||$targetHost^\$3p")
            }
            if (pageHost != null && rules.domainScopedAdHosts.isNotEmpty()) {
                if (matchesDomainScopedHosts(targetHost, pageHost, rules.domainScopedAdHosts)) {
                    return BlockDecision(true, FilterCategory.ADS, "||$targetHost^\$domain=$pageHost")
                }
            }
            if (!isMainFrame || isThirdParty) {
                for (token in rules.adPathTokens) {
                    if (lowerUrl.contains(token)) {
                        return BlockDecision(true, FilterCategory.ADS, token)
                    }
                }
            }
        }

        // 4. Privacy & Trackers
        if (cfg.blockTrackers) {
            if (matchesHostSet(targetHost, rules.trackerHosts)) {
                return BlockDecision(true, FilterCategory.TRACKERS, "||$targetHost^")
            }
            if (isThirdParty && matchesHostSet(targetHost, rules.thirdPartyTrackerHosts)) {
                return BlockDecision(true, FilterCategory.TRACKERS, "||$targetHost^\$3p")
            }
            if (!isMainFrame) {
                for (token in rules.trackerPathTokens) {
                    if (lowerUrl.contains(token)) {
                        return BlockDecision(true, FilterCategory.TRACKERS, token)
                    }
                }
            }
        }

        return NOT_BLOCKED
    }

    /**
     * Quick check used by manifest downloads, cloud providers, and video downloads to reject
     * malware or known adware/hijack URLs before connecting.
     */
    fun evaluateProviderOrMediaUrl(rawUrl: String): BlockDecision {
        val cfg = currentConfig
        if (!cfg.masterEnabled) return NOT_BLOCKED
        val host = extractHostFast(rawUrl) ?: return NOT_BLOCKED
        val rules = compiledRuleset

        if (matchesHostSet(host, rules.exceptionHosts) || matchesConfigSet(host, cfg.whitelistedDomains)) {
            return NOT_BLOCKED
        }
        if (cfg.blockMalware && matchesHostSet(host, rules.malwareHosts)) {
            return BlockDecision(true, FilterCategory.MALWARE, "||$host^")
        }
        if (cfg.blockMalware) {
            val low = rawUrl.lowercase()
            for (token in rules.malwarePathTokens) {
                if (low.contains(token)) return BlockDecision(true, FilterCategory.MALWARE, token)
            }
        }
        if (cfg.blockPopups && matchesHostSet(host, rules.popupHosts)) {
            return BlockDecision(true, FilterCategory.ANNOYANCES, "||$host^")
        }
        if (cfg.blockAds && (matchesHostSet(host, rules.adHosts) || matchesConfigSet(host, cfg.customBlockedDomains))) {
            return BlockDecision(true, FilterCategory.ADS, "||$host^")
        }
        return NOT_BLOCKED
    }

    private fun matchesDomainScopedHosts(
        targetHost: String,
        pageHost: String,
        scopedMap: Map<String, HashSet<String>>
    ): Boolean {
        val directSet = scopedMap[pageHost]
        if (directSet != null && matchesHostSet(targetHost, directSet)) return true
        var idx = pageHost.indexOf('.')
        while (idx >= 0 && idx < pageHost.length - 1) {
            val parent = pageHost.substring(idx + 1)
            val pSet = scopedMap[parent]
            if (pSet != null && matchesHostSet(targetHost, pSet)) return true
            idx = pageHost.indexOf('.', idx + 1)
        }
        return false
    }

    private fun matchesConfigSet(host: String, rawSet: Set<String>): Boolean {
        if (rawSet.isEmpty()) return false
        for (entry in rawSet) {
            val clean = entry.trim().lowercase().trimEnd('.')
            if (clean.isEmpty()) continue
            if (host == clean || host.endsWith(".$clean")) return true
        }
        return false
    }

    fun recordBlocked(decision: BlockDecision) {
        if (!decision.blocked) return
        val total = totalBlockedCounter.incrementAndGet()
        val ads = if (decision.category == FilterCategory.ADS) adsBlockedCounter.incrementAndGet() else adsBlockedCounter.get()
        val trackers = if (decision.category == FilterCategory.TRACKERS) trackersBlockedCounter.incrementAndGet() else trackersBlockedCounter.get()
        val malware = if (decision.category == FilterCategory.MALWARE) malwareBlockedCounter.incrementAndGet() else malwareBlockedCounter.get()
        val popups = if (decision.category == FilterCategory.ANNOYANCES) popupsBlockedCounter.incrementAndGet() else popupsBlockedCounter.get()

        val now = System.currentTimeMillis()
        val prev = lastMetricsUiEmitMs.get()
        if (total <= 3 || (now - prev >= 400L && lastMetricsUiEmitMs.compareAndSet(prev, now))) {
            _metrics.update { cur ->
                cur.copy(
                    totalBlocked = total,
                    adsBlocked = ads,
                    trackersBlocked = trackers,
                    malwareBlocked = malware,
                    popupsBlocked = popups
                )
            }
        }
    }

    fun resetSessionCounter() {
        totalBlockedCounter.set(0)
        adsBlockedCounter.set(0)
        trackersBlockedCounter.set(0)
        malwareBlockedCounter.set(0)
        popupsBlockedCounter.set(0)
        lastMetricsUiEmitMs.set(0L)
        _metrics.update {
            it.copy(totalBlocked = 0, adsBlocked = 0, trackersBlocked = 0, malwareBlocked = 0, popupsBlocked = 0)
        }
    }

    /**
     * Builds a safe, chunked CSS stylesheet + lightweight debounced container-collapse &
     * anti-popunder script for WebView pages. Chunking CSS rules prevents one unsupported
     * selector from breaking the entire stylesheet in Chromium.
     */
    fun buildCosmeticInjectionScript(pageUrl: String?): String? {
        val cfg = currentConfig
        if (!cfg.masterEnabled) return null

        val pageHost = extractHostFast(pageUrl)
        val rules = compiledRuleset
        if (pageHost != null && (
                matchesHostSet(pageHost, rules.exceptionHosts) ||
                    matchesHostSet(pageHost, rules.elemhideExceptionHosts) ||
                    matchesConfigSet(pageHost, cfg.whitelistedDomains)
                )
        ) {
            return null
        }

        val selectors = LinkedHashSet<String>()
        if (cfg.cosmeticFiltering) {
            if (cfg.blockAds) {
                selectors.addAll(rules.globalCosmeticSelectors)
            }
            if (pageHost != null) {
                rules.domainCosmeticSelectors.forEach { (domain, domSelectors) ->
                    if (pageHost == domain || pageHost.endsWith(".$domain")) {
                        selectors.addAll(domSelectors)
                    }
                }
            }
        }

        // Group selectors into small chunks of 25 so a single invalid selector never kills other rules
        val cssText = if (cfg.cosmeticFiltering && selectors.isNotEmpty()) {
            selectors
                .asSequence()
                .filter { isSafeStandardCssSelector(it) }
                .take(MAX_GLOBAL_COSMETIC_SELECTORS)
                .chunked(25)
                .joinToString("\n") { chunk ->
                    chunk.joinToString(",") +
                        "{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;pointer-events:none!important;}"
                }
        } else ""

        val escapedCss = cssText
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")

        val installAntiPop = if (cfg.blockPopups) "true" else "false"
        val isCosmeticActive = if (cfg.cosmeticFiltering && cssText.isNotEmpty()) "true" else "false"

        return """
            (function() {
                try {
                    if ($installAntiPop && !window.__navulaAntiPopInstalled) {
                        window.__navulaAntiPopInstalled = true;
                        window.open = function() { return null; };
                    }
                    if ($isCosmeticActive) {
                        var styleId = 'navula-ublock-cosmetic';
                        if (!document.getElementById(styleId)) {
                            var s = document.createElement('style');
                            s.id = styleId;
                            s.textContent = '$escapedCss';
                            (document.head || document.documentElement).appendChild(s);
                        }
                        var cKey = location.href + '|' + document.readyState;
                        if (window.__navulaCosmeticInjectedUrl === cKey) return;
                        window.__navulaCosmeticInjectedUrl = cKey;
                        if (document.hidden || window.__navulaVideoPlaying) return;
                        var adCheckSel = 'ins.adsbygoogle,[id^="google_ads_"],[id*="div-gpt-ad"],[data-ad-slot],[class*="ad-banner"],[class*="banner-ad"],[class*="ad-container"],[class*="popup-ad"]';
                        var nodes = document.querySelectorAll(adCheckSel);
                        var limit = Math.min(nodes.length, 40);
                        for (var i = 0; i < limit; i++) {
                            var el = nodes[i];
                            if (!el || (el.dataset && el.dataset.navulaAdHidden === '1')) continue;
                            if (el.tagName === 'VIDEO' || el.tagName === 'BODY' || el.tagName === 'HTML') continue;
                            if (el.querySelector && el.querySelector('video,#player,[class*="player"],[id*="player"]')) continue;
                            if (el.dataset) el.dataset.navulaAdHidden = '1';
                            el.style.setProperty('display', 'none', 'important');
                            el.style.setProperty('height', '0', 'important');
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /**
     * Strips website-only clutter (footers, "All Rights Reserved", DMCA/disclaimers, cookie prompts,
     * scrollbars) once per page load without running timers or MutationObservers.
     */
    fun buildAppModeCleanupScript(pageUrl: String?): String {
        val host = extractHostFast(pageUrl).orEmpty()
        val isYouTube = host.contains("youtube.com") || host.contains("youtu.be")
        val skipTextFooterWalk = if (isYouTube) "true" else "false"

        return """
            (function() {
                try {
                    var styleId = 'navula-app-mode-style';
                    if (!document.getElementById(styleId)) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.textContent = [
                            'html, body { -webkit-tap-highlight-color: transparent !important; touch-action: pan-x pan-y pinch-zoom; }',
                            '::-webkit-scrollbar { width: 0px !important; height: 0px !important; display: none !important; }',
                            'footer, [role="contentinfo"], #footer, .footer, .site-footer, .main-footer, .page-footer, .footer-wrapper, .footer-bottom, .footer-copyright, .copyright, .copyrights, .all-rights-reserved, .dmca, .disclaimer, .site-disclaimer, .cookie-banner, #cookie-banner, #cookie-notice, .cookie-notice, .cc-window, .consent-banner, [class*="cookie-consent"], [id*="cookie-consent"], [class*="telegram-banner"], [class*="app-download"], [class*="install-app"] { display: none !important; height: 0 !important; min-height: 0 !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; }'
                        ].join('\n');
                        (document.head || document.documentElement).appendChild(style);
                    }

                    function ensureScrollUnlocked() {
                        try {
                            if (document.fullscreenElement || document.webkitFullscreenElement) return;
                            var targets = [document.documentElement, document.body];
                            for (var k = 0; k < targets.length; k++) {
                                var node = targets[k];
                                if (!node || !window.getComputedStyle) continue;
                                var cs = window.getComputedStyle(node);
                                if (cs && cs.overflowY === 'hidden') {
                                    node.style.setProperty('overflow-y', 'auto', 'important');
                                }
                                if (cs && cs.touchAction === 'none') {
                                    node.style.setProperty('touch-action', 'pan-y pinch-zoom', 'important');
                                }
                            }
                        } catch(e) {}
                    }

                    ensureScrollUnlocked();
                    var aKey = location.href + '|' + document.readyState;
                    if (window.__navulaClutterCleanedUrl === aKey) return;
                    window.__navulaClutterCleanedUrl = aKey;
                    if (document.hidden) return;
                    if ($skipTextFooterWalk) return;

                    var footerEls = document.querySelectorAll('footer, [role="contentinfo"], #footer, .footer, .site-footer, .footer-bottom, .copyright, .dmca, .disclaimer');
                    for (var i = 0; i < footerEls.length; i++) {
                        var f = footerEls[i];
                        if (f && f.tagName !== 'BODY' && f.tagName !== 'HTML' &&
                            !(f.dataset && f.dataset.navulaCleaned === '1') &&
                            !f.querySelector('video, iframe[src*="player"], #player, [class*="player"], [class*="movie"], [class*="grid"]')) {
                            if (f.dataset) f.dataset.navulaCleaned = '1';
                            f.style.setProperty('display', 'none', 'important');
                        }
                    }

                    var phrases = [
                        'all rights reserved',
                        'does not store any files',
                        'does not host any files',
                        'any media files on its server',
                        'dmca notice',
                        'disclaimer:',
                        'terms of service',
                        'join our telegram'
                    ];
                    var candidates = document.querySelectorAll('footer, section, p, div[class*="footer"], div[class*="copy"], div[id*="footer"]');
                    var docHeight = Math.max(document.body ? document.body.scrollHeight : 0, 800);
                    var maxCheck = Math.min(candidates.length, 60);
                    for (var j = candidates.length - 1; j >= Math.max(0, candidates.length - maxCheck); j--) {
                        var el = candidates[j];
                        if (!el || (el.dataset && el.dataset.navulaCleaned === '1')) continue;
                        if (el.tagName === 'BODY' || el.tagName === 'HTML' || el.tagName === 'MAIN') continue;
                        if (el.id === 'root' || el.id === 'app' || el.id === '__next') continue;
                        if (el.querySelector && el.querySelector('video, iframe, #player, [class*="player"], [class*="movie"], [class*="grid"], [class*="card"], [class*="list"]')) continue;
                        var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                        if (rect && rect.height > 420) continue;
                        var txt = (el.innerText || '').trim().toLowerCase();
                        if (!txt || txt.length > 600) continue;
                        var isLower = !rect || (rect.top + window.scrollY > docHeight * 0.45) || txt.indexOf('all rights reserved') !== -1 || txt.indexOf('©') !== -1;
                        if (!isLower) continue;
                        var matched = txt.indexOf('©') !== -1;
                        if (!matched) {
                            for (var p = 0; p < phrases.length; p++) {
                                if (txt.indexOf(phrases[p]) !== -1) { matched = true; break; }
                            }
                        }
                        if (matched && el.children.length <= 12) {
                            if (el.dataset) el.dataset.navulaCleaned = '1';
                            el.style.setProperty('display', 'none', 'important');
                            el.style.setProperty('height', '0', 'important');
                            el.style.setProperty('margin', '0', 'important');
                            el.style.setProperty('padding', '0', 'important');
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /**
     * Downloads enabled filter lists in background, validates SHA-256 checksum & `! Version:`,
     * uses HTTP ETag (`If-None-Match` / 304) to skip unchanged lists, stages atomically via `.tmp`
     * so failed syncs preserve the previous working DB, and compiles once into `compiled_shield_v3.cache`.
     */
    suspend fun syncEnabledSubscriptions(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext Result.success(0)
        _isSyncing.value = true
        appContext = context.applicationContext

        try {
            val enabledIds = currentConfig.enabledListIds
            val targets = SUBSCRIPTIONS.filter { it.id in enabledIds }
            val filterDir = File(context.filesDir, "filter_lists").apply { mkdirs() }
            val metaMap = readFilterMeta(filterDir).toMutableMap()
            val updatedCount = AtomicInteger(0)

            coroutineScope {
                targets.map { sub ->
                    async(Dispatchers.IO) {
                        val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(sub.url).getOrNull()
                            ?: return@async
                        val cacheFile = UrlSecurityValidator.resolveConfinedChild(filterDir, "${sub.id}.txt")
                        val prevMeta = metaMap[sub.id]

                        runCatching {
                            val reqBuilder = Request.Builder()
                                .url(safeUrl)
                                .header("User-Agent", "NavulaShield/3.0 (Adblock-Compatible)")
                                .header("Accept", "text/plain, */*")
                            if (cacheFile.exists() && cacheFile.length() > 0L && !prevMeta?.etag.isNullOrBlank()) {
                                reqBuilder.header("If-None-Match", prevMeta!!.etag)
                            }
                            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                                if (resp.code == 304 && cacheFile.exists() && cacheFile.length() > 0L) {
                                    val nowMs = System.currentTimeMillis()
                                    val verifiedMeta = (prevMeta ?: StoredSubscriptionMeta()).copy(lastUpdatedMs = nowMs)
                                    synchronized(metaMap) { metaMap[sub.id] = verifiedMeta }
                                    _subscriptionStatuses.update {
                                        it + (sub.id to FilterListStatus(
                                            id = sub.id,
                                            ruleCount = verifiedMeta.ruleCount,
                                            lastUpdatedMs = nowMs,
                                            isCached = true,
                                            lastError = null,
                                            version = verifiedMeta.version,
                                            sha256 = verifiedMeta.sha256
                                        ))
                                    }
                                    return@use
                                }
                                if (!resp.isSuccessful) {
                                    error("HTTP ${resp.code}")
                                }
                                val bodyText = resp.body?.string().orEmpty()
                                val newEtag = resp.header("ETag").orEmpty()
                                val stageResult = applySubscriptionUpdateAtomically(
                                    filterDir = filterDir,
                                    subscription = sub,
                                    rawText = bodyText,
                                    previousMeta = prevMeta,
                                    etag = newEtag
                                )
                                synchronized(metaMap) { metaMap[sub.id] = stageResult.meta }
                                if (stageResult.changed) {
                                    updatedCount.incrementAndGet()
                                }
                                _subscriptionStatuses.update {
                                    it + (sub.id to FilterListStatus(
                                        id = sub.id,
                                        ruleCount = stageResult.meta.ruleCount,
                                        lastUpdatedMs = stageResult.meta.lastUpdatedMs,
                                        isCached = true,
                                        lastError = null,
                                        version = stageResult.meta.version,
                                        sha256 = stageResult.meta.sha256
                                    ))
                                }
                            }
                        }.onFailure { err ->
                            if (err is CancellationException) throw err
                            _subscriptionStatuses.update { cur ->
                                val prev = cur[sub.id] ?: FilterListStatus(
                                    id = sub.id,
                                    ruleCount = prevMeta?.ruleCount ?: builtInCountForCategory(sub.category),
                                    lastUpdatedMs = prevMeta?.lastUpdatedMs ?: 0L,
                                    isCached = cacheFile.exists() && cacheFile.length() > 0L,
                                    version = prevMeta?.version.orEmpty(),
                                    sha256 = prevMeta?.sha256.orEmpty()
                                )
                                cur + (sub.id to prev.copy(lastError = err.localizedMessage ?: "Sync error"))
                            }
                        }
                    }
                }.awaitAll()
            }

            writeFilterMetaAtomically(filterDir, metaMap)

            val compiledCacheExists = File(filterDir, COMPILED_CACHE_FILE).let { it.exists() && it.length() > 0L }
            if (updatedCount.get() > 0 || !compiledCacheExists) {
                lastLoadedFingerprint = ""
                rebuildRuleset()
            }
            _metrics.update { it.copy(lastSyncedMs = System.currentTimeMillis()) }
            Result.success(updatedCount.get())
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    internal data class StoredSubscriptionMeta(
        val version: String = "",
        val sha256: String = "",
        val etag: String = "",
        val ruleCount: Int = 0,
        val lastUpdatedMs: Long = 0L
    )

    internal data class StagedSubscriptionResult(
        val changed: Boolean,
        val meta: StoredSubscriptionMeta
    )

    /**
     * Validates `! Version:`, SHA-256 checksum, and parsed rule integrity before committing to disk.
     * Writes to a `.tmp` staging file and swaps atomically so invalid/empty updates never corrupt
     * the existing cached ruleset.
     */
    internal fun applySubscriptionUpdateAtomically(
        filterDir: File,
        subscription: FilterSubscription,
        rawText: String,
        previousMeta: StoredSubscriptionMeta? = null,
        etag: String = ""
    ): StagedSubscriptionResult {
        val cacheFile = UrlSecurityValidator.resolveConfinedChild(filterDir, "${subscription.id}.txt")
        val tmpFile = UrlSecurityValidator.resolveConfinedChild(filterDir, "${subscription.id}.txt.tmp")
        val parsedVersion = extractFilterVersion(rawText)
        val checksum = sha256Hex(rawText)
        val nowMs = System.currentTimeMillis()

        // Skip re-parsing & disk rewrite when existing file and checksum/version are identical
        if (cacheFile.exists() && cacheFile.length() > 0L &&
            previousMeta != null &&
            previousMeta.sha256 == checksum &&
            (parsedVersion.isEmpty() || previousMeta.version == parsedVersion) &&
            previousMeta.ruleCount > 0
        ) {
            return StagedSubscriptionResult(
                changed = false,
                meta = previousMeta.copy(
                    etag = etag.ifBlank { previousMeta.etag },
                    lastUpdatedMs = nowMs
                )
            )
        }

        val parsed = parseFilterListText(rawText, subscription.category)
        val ruleCount = parsed.totalParsedCount()
        if (ruleCount <= 0) {
            throw IllegalStateException("Rejected empty or invalid filter payload for ${subscription.id}")
        }

        return try {
            writeCompactSubscriptionFile(tmpFile, parsed)
            if (!tmpFile.exists() || tmpFile.length() <= 0L) {
                throw IllegalStateException("Staged compact filter file was empty")
            }
            if (!tmpFile.renameTo(cacheFile)) {
                tmpFile.copyTo(cacheFile, overwrite = true)
                tmpFile.delete()
            }
            StagedSubscriptionResult(
                changed = true,
                meta = StoredSubscriptionMeta(
                    version = parsedVersion,
                    sha256 = checksum,
                    etag = etag,
                    ruleCount = ruleCount,
                    lastUpdatedMs = nowMs
                )
            )
        } catch (t: Throwable) {
            runCatching { tmpFile.delete() }
            throw t
        }
    }

    internal fun extractFilterVersion(rawText: String): String {
        var linesChecked = 0
        for (raw in rawText.lineSequence()) {
            if (linesChecked++ > 40) break
            val line = raw.trim()
            if (line.startsWith("! Version:", ignoreCase = true)) {
                return line.substringAfter(':').trim().take(64)
            }
        }
        return ""
    }

    internal fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val hex = (b.toInt() and 0xFF)
            if (hex < 0x10) sb.append('0')
            sb.append(hex.toString(16))
        }
        return sb.toString()
    }

    internal data class ParsedSubscription(
        val hosts: Set<String>,
        val thirdPartyHosts: Set<String> = emptySet(),
        val popupHosts: Set<String> = emptySet(),
        val exceptions: Set<String>,
        val elemhideExceptions: Set<String> = emptySet(),
        val badfilterRules: Set<String> = emptySet(),
        val domainScopedHosts: Map<String, Set<String>> = emptyMap(),
        val pathTokens: List<String>,
        val globalCosmetics: List<String>,
        val domainCosmetics: Map<String, List<String>>
    ) {
        fun totalParsedCount(): Int = hosts.size + thirdPartyHosts.size + popupHosts.size +
            exceptions.size + elemhideExceptions.size + pathTokens.size +
            globalCosmetics.size + domainCosmetics.values.sumOf { it.size } +
            domainScopedHosts.values.sumOf { it.size }
    }

    /**
     * Parses AdBlock Plus / EasyList / AdGuard / hosts syntax safely and accurately:
     * respects `$third-party`, `$popup`, `$domain=...`, `$badfilter`, `$elemhide`,
     * and validates standard CSS selectors cleanly without executing JS scriptlets.
     */
    internal fun parseFilterListText(rawText: String, category: FilterCategory): ParsedSubscription {
        val hosts = HashSet<String>(1024)
        val thirdPartyHosts = HashSet<String>(256)
        val popupHosts = HashSet<String>(128)
        val exceptions = HashSet<String>(128)
        val elemhideExceptions = HashSet<String>(64)
        val badfilterKeys = HashSet<String>()
        val domainScopedHosts = LinkedHashMap<String, HashSet<String>>()
        val pathTokens = LinkedHashSet<String>()
        val globalCosmetics = LinkedHashSet<String>()
        val domainCosmetics = LinkedHashMap<String, MutableList<String>>()

        var lineCounter = 0
        val lineSeq = rawText.lineSequence()

        for (rawLine in lineSeq) {
            if (lineCounter >= MAX_LINES_PER_SUBSCRIPTION) break
            lineCounter++

            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue

            // Exception rules: @@||domain.com^$options
            if (line.startsWith("@@")) {
                val withoutPrefix = line.removePrefix("@@")
                val dollarIdx = withoutPrefix.indexOf('$')
                val body = (if (dollarIdx >= 0) withoutPrefix.substring(0, dollarIdx) else withoutPrefix).trim()
                val options = if (dollarIdx >= 0) withoutPrefix.substring(dollarIdx + 1).lowercase() else ""

                if (body.startsWith("||")) {
                    val dom = body.removePrefix("||").substringBefore("^").substringBefore("/").lowercase()
                    if (isValidFilterDomain(dom)) {
                        if (options.contains("elemhide") || options.contains("generichide")) {
                            elemhideExceptions.add(dom)
                        }
                        if (!options.contains("elemhide") || options.contains("document") || options.isEmpty()) {
                            exceptions.add(dom)
                        }
                    }
                }
                continue
            }

            // Skip unsupported scriptlets, HTML filters, snippet filters, and cosmetic exceptions
            if (line.contains("#@#") || line.contains("##+js") || line.contains("#$#") ||
                line.contains("#?#") || line.contains("##^") || line.contains("$$")
            ) {
                continue
            }

            // Cosmetic hiding rules: ##.ad-box or example.com##.ad-box
            val cosmeticIdx = line.indexOf("##")
            if (cosmeticIdx >= 0) {
                val domainsPart = line.substring(0, cosmeticIdx).trim()
                val selector = line.substring(cosmeticIdx + 2).trim()
                if (isSafeStandardCssSelector(selector)) {
                    if (domainsPart.isEmpty()) {
                        if (globalCosmetics.size < MAX_GLOBAL_COSMETIC_SELECTORS) {
                            globalCosmetics.add(selector)
                        }
                    } else {
                        for (rawDom in domainsPart.split(',')) {
                            val d = rawDom.trim().lowercase()
                            if (d.startsWith("~")) continue
                            if (isValidFilterDomain(d)) {
                                val list = domainCosmetics.getOrPut(d) { mutableListOf() }
                                if (list.size < 40 && !list.contains(selector)) {
                                    list.add(selector)
                                }
                            }
                        }
                    }
                }
                continue
            }

            // Hosts-file entries: 0.0.0.0 domain.com
            if (line.startsWith("#")) continue
            if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
                val dom = line.substringAfter(' ').substringBefore('#').trim().lowercase()
                if (isValidFilterDomain(dom) && dom != "localhost") {
                    hosts.add(dom)
                }
                continue
            }

            // Network filter rule with optional $modifiers
            val dollarIdx = line.indexOf('$')
            val rulePart = (if (dollarIdx >= 0) line.substring(0, dollarIdx) else line).trim()
            val optionsPart = if (dollarIdx >= 0) line.substring(dollarIdx + 1).trim().lowercase() else ""

            var isBadfilter = false
            var isThirdParty = false
            var isPopup = false
            var scopedDomains: List<String>? = null

            if (optionsPart.isNotEmpty()) {
                for (opt in optionsPart.split(',')) {
                    val cleanOpt = opt.trim()
                    when {
                        cleanOpt == "badfilter" -> isBadfilter = true
                        cleanOpt == "third-party" || cleanOpt == "3p" -> isThirdParty = true
                        cleanOpt == "popup" || cleanOpt == "popunder" -> isPopup = true
                        cleanOpt.startsWith("domain=") -> {
                            val domSpec = cleanOpt.removePrefix("domain=")
                            val positives = domSpec.split('|')
                                .map { it.trim().lowercase() }
                                .filter { !it.startsWith("~") && isValidFilterDomain(it) }
                            if (positives.isNotEmpty()) {
                                scopedDomains = positives
                            }
                        }
                    }
                }
            }

            if (rulePart.startsWith("||")) {
                val rest = rulePart.removePrefix("||")
                val caretOrSlash = rest.indexOfFirst { it == '^' || it == '/' || it == '*' }
                val isPureDomainRule = caretOrSlash < 0 ||
                    (rest[caretOrSlash] == '^' && caretOrSlash == rest.length - 1)
                val domCandidate = (if (caretOrSlash >= 0) rest.substring(0, caretOrSlash) else rest).lowercase()

                if (isPureDomainRule && isValidFilterDomain(domCandidate)) {
                    if (isBadfilter) {
                        badfilterKeys.add(domCandidate)
                    } else if (scopedDomains != null) {
                        for (pageDom in scopedDomains) {
                            domainScopedHosts.getOrPut(pageDom) { HashSet(4) }.add(domCandidate)
                        }
                    } else if (isPopup) {
                        popupHosts.add(domCandidate)
                    } else if (isThirdParty) {
                        thirdPartyHosts.add(domCandidate)
                    } else {
                        hosts.add(domCandidate)
                    }
                } else if (!isBadfilter) {
                    val literal = extractLongestLiteralToken(rest)
                    if (literal != null && pathTokens.size < MAX_PATH_TOKENS_PER_CATEGORY) {
                        pathTokens.add(literal)
                    }
                }
                continue
            }

            if (!isBadfilter && rulePart.length in 9..96) {
                val lower = rulePart.lowercase()
                if (!lower.contains('*') && !lower.contains('|') && isValidFilterDomain(lower)) {
                    if (isPopup) popupHosts.add(lower)
                    else if (isThirdParty) thirdPartyHosts.add(lower)
                    else hosts.add(lower)
                } else {
                    val literal = extractLongestLiteralToken(lower)
                    if (literal != null && pathTokens.size < MAX_PATH_TOKENS_PER_CATEGORY) {
                        pathTokens.add(literal)
                    }
                }
            }
        }

        // Remove any conflicting hosts marked with $badfilter inside the same subscription
        if (badfilterKeys.isNotEmpty()) {
            hosts.removeAll(badfilterKeys)
            thirdPartyHosts.removeAll(badfilterKeys)
            popupHosts.removeAll(badfilterKeys)
        }

        return ParsedSubscription(
            hosts = hosts,
            thirdPartyHosts = thirdPartyHosts,
            popupHosts = popupHosts,
            exceptions = exceptions,
            elemhideExceptions = elemhideExceptions,
            badfilterRules = badfilterKeys,
            domainScopedHosts = domainScopedHosts,
            pathTokens = pathTokens.toList(),
            globalCosmetics = globalCosmetics.toList(),
            domainCosmetics = domainCosmetics
        )
    }

    private fun extractLongestLiteralToken(rawPattern: String): String? {
        val cleaned = rawPattern.replace("^", "/").replace("|", "")
        val segments = cleaned.split('*')
        val best = segments
            .map { it.trim().lowercase() }
            .filter { seg ->
                seg.length >= 10 &&
                    (seg.contains('/') || seg.contains('_') || seg.contains('-')) &&
                    !seg.startsWith("http")
            }
            .maxByOrNull { it.length }
        return best?.take(64)
    }

    private fun buildBuiltinRuleset(cfg: ShieldConfig): CompiledRuleset {
        val adHosts = HashSet<String>(BUILTIN_AD_HOSTS)
        adHosts.addAll(cfg.customBlockedDomains.mapNotNull { it.trim().lowercase().takeIf(::isValidFilterDomain) })
        val popupHosts = HashSet<String>(BUILTIN_POPUP_HOSTS)
        val trackerHosts = HashSet<String>(BUILTIN_TRACKER_HOSTS)
        val malwareHosts = HashSet<String>(BUILTIN_MALWARE_HOSTS)
        val exceptionHosts = HashSet<String>(cfg.whitelistedDomains.mapNotNull { it.trim().lowercase().takeIf { dom -> dom.isNotBlank() } })

        val total = adHosts.size + popupHosts.size + trackerHosts.size + malwareHosts.size +
            exceptionHosts.size + BUILTIN_AD_PATH_TOKENS.size + BUILTIN_POPUP_PATH_TOKENS.size +
            BUILTIN_TRACKER_PATH_TOKENS.size + BUILTIN_MALWARE_PATH_TOKENS.size +
            BUILTIN_COSMETIC_SELECTORS.size

        return CompiledRuleset(
            adHosts = adHosts,
            thirdPartyAdHosts = HashSet(),
            popupHosts = popupHosts,
            trackerHosts = trackerHosts,
            thirdPartyTrackerHosts = HashSet(),
            malwareHosts = malwareHosts,
            exceptionHosts = exceptionHosts,
            elemhideExceptionHosts = HashSet(),
            domainScopedAdHosts = emptyMap(),
            adPathTokens = BUILTIN_AD_PATH_TOKENS.toTypedArray(),
            popupPathTokens = BUILTIN_POPUP_PATH_TOKENS.toTypedArray(),
            trackerPathTokens = BUILTIN_TRACKER_PATH_TOKENS.toTypedArray(),
            malwarePathTokens = BUILTIN_MALWARE_PATH_TOKENS.toTypedArray(),
            globalCosmeticSelectors = BUILTIN_COSMETIC_SELECTORS,
            domainCosmeticSelectors = emptyMap(),
            totalRuleCount = total,
            loadedFromCompiledCache = false
        )
    }

    private fun buildOrLoadCompiledRuleset(context: Context, cfg: ShieldConfig): CompiledRuleset {
        val filterDir = File(context.filesDir, "filter_lists")
        val fingerprint = buildFingerprint(filterDir, cfg)
        if (fingerprint == lastLoadedFingerprint) {
            return compiledRuleset
        }

        val compiledFile = File(filterDir, COMPILED_CACHE_FILE)
        runCatching { File(filterDir, LEGACY_COMPILED_CACHE_FILE).delete() }
        if (compiledFile.exists() && compiledFile.length() > 0L) {
            val fastLoaded = runCatching { readCompiledCacheIfMatching(compiledFile, fingerprint) }.getOrNull()
            if (fastLoaded != null) {
                lastLoadedFingerprint = fingerprint
                updateSubscriptionStatusesFromDisk(filterDir, cfg)
                return fastLoaded
            }
        }

        val fresh = buildRulesetFromSubscriptionFiles(filterDir, cfg)
        lastLoadedFingerprint = fingerprint
        runCatching { writeCompiledCacheAtomically(compiledFile, fingerprint, fresh) }
        return fresh
    }

    private fun buildFingerprint(filterDir: File, cfg: ShieldConfig): String {
        return buildString {
            append(cfg.enabledListIds.sorted().joinToString(","))
            append('|')
            append(cfg.customBlockedDomains.sorted().joinToString(","))
            append('|')
            append(cfg.whitelistedDomains.sorted().joinToString(","))
            append('|')
            for (sub in SUBSCRIPTIONS) {
                if (sub.id in cfg.enabledListIds) {
                    val f = File(filterDir, "${sub.id}.txt")
                    if (f.exists()) {
                        append(sub.id).append(':').append(f.lastModified()).append(':').append(f.length()).append(';')
                    }
                }
            }
        }
    }

    private fun updateSubscriptionStatusesFromDisk(filterDir: File, cfg: ShieldConfig) {
        val metaMap = readFilterMeta(filterDir)
        val statuses = LinkedHashMap<String, FilterListStatus>()
        for (sub in SUBSCRIPTIONS) {
            val f = File(filterDir, "${sub.id}.txt")
            val hasCache = f.exists() && f.length() > 0L
            val stored = metaMap[sub.id]
            statuses[sub.id] = FilterListStatus(
                id = sub.id,
                ruleCount = if (hasCache && sub.id in cfg.enabledListIds) {
                    stored?.ruleCount?.takeIf { it > 0 } ?: (f.length() / 22L).toInt().coerceAtLeast(1)
                } else if (sub.id in cfg.enabledListIds) {
                    builtInCountForCategory(sub.category)
                } else 0,
                lastUpdatedMs = if (hasCache) (stored?.lastUpdatedMs?.takeIf { it > 0L } ?: f.lastModified()) else 0L,
                isCached = hasCache,
                version = stored?.version.orEmpty(),
                sha256 = stored?.sha256.orEmpty()
            )
        }
        _subscriptionStatuses.value = statuses
    }

    private fun buildRulesetFromSubscriptionFiles(filterDir: File, cfg: ShieldConfig): CompiledRuleset {
        val adHosts = HashSet<String>(BUILTIN_AD_HOSTS)
        val thirdPartyAdHosts = HashSet<String>(512)
        val popupHosts = HashSet<String>(BUILTIN_POPUP_HOSTS)
        val trackerHosts = HashSet<String>(BUILTIN_TRACKER_HOSTS)
        val thirdPartyTrackerHosts = HashSet<String>(256)
        val malwareHosts = HashSet<String>(BUILTIN_MALWARE_HOSTS)
        val exceptionHosts = HashSet<String>(cfg.whitelistedDomains.mapNotNull { it.trim().lowercase().takeIf { dom -> dom.isNotBlank() } })
        val elemhideExceptions = HashSet<String>()
        val badfilters = HashSet<String>()
        val domainScopedAdHosts = LinkedHashMap<String, HashSet<String>>()

        adHosts.addAll(cfg.customBlockedDomains.mapNotNull { it.trim().lowercase().takeIf(::isValidFilterDomain) })

        val adPaths = LinkedHashSet<String>(BUILTIN_AD_PATH_TOKENS)
        val popupPaths = LinkedHashSet<String>(BUILTIN_POPUP_PATH_TOKENS)
        val trackerPaths = LinkedHashSet<String>(BUILTIN_TRACKER_PATH_TOKENS)
        val malwarePaths = LinkedHashSet<String>(BUILTIN_MALWARE_PATH_TOKENS)
        val globalCosmetics = LinkedHashSet<String>(BUILTIN_COSMETIC_SELECTORS)
        val domainCosmetics = LinkedHashMap<String, MutableList<String>>()
        val statuses = LinkedHashMap<String, FilterListStatus>()

        for (sub in SUBSCRIPTIONS) {
            val cacheFile = File(filterDir, "${sub.id}.txt")
            val hasCache = cacheFile.exists() && cacheFile.length() > 0L

            if (hasCache && sub.id in cfg.enabledListIds) {
                val parsed = runCatching {
                    parseFilterListText(cacheFile.readText(), sub.category)
                }.getOrNull()

                if (parsed != null) {
                    badfilters.addAll(parsed.badfilterRules)
                    popupHosts.addAll(parsed.popupHosts)
                    when (sub.category) {
                        FilterCategory.ADS -> {
                            adHosts.addAll(parsed.hosts)
                            thirdPartyAdHosts.addAll(parsed.thirdPartyHosts)
                            adPaths.addAll(parsed.pathTokens)
                            parsed.domainScopedHosts.forEach { (dom, set) ->
                                domainScopedAdHosts.getOrPut(dom) { HashSet() }.addAll(set)
                            }
                        }
                        FilterCategory.ANNOYANCES -> {
                            adHosts.addAll(parsed.hosts)
                            popupHosts.addAll(parsed.thirdPartyHosts)
                            popupPaths.addAll(parsed.pathTokens)
                        }
                        FilterCategory.TRACKERS -> {
                            trackerHosts.addAll(parsed.hosts)
                            thirdPartyTrackerHosts.addAll(parsed.thirdPartyHosts)
                            trackerPaths.addAll(parsed.pathTokens)
                        }
                        FilterCategory.MALWARE -> {
                            malwareHosts.addAll(parsed.hosts)
                            malwarePaths.addAll(parsed.pathTokens)
                        }
                    }
                    exceptionHosts.addAll(parsed.exceptions)
                    elemhideExceptions.addAll(parsed.elemhideExceptions)
                    for (sel in parsed.globalCosmetics) {
                        if (globalCosmetics.size < MAX_GLOBAL_COSMETIC_SELECTORS) {
                            globalCosmetics.add(sel)
                        }
                    }
                    for ((dom, sels) in parsed.domainCosmetics) {
                        val dest = domainCosmetics.getOrPut(dom) { mutableListOf() }
                        for (s in sels) {
                            if (dest.size < 40 && !dest.contains(s)) dest.add(s)
                        }
                    }
                    statuses[sub.id] = FilterListStatus(
                        id = sub.id,
                        ruleCount = parsed.totalParsedCount(),
                        lastUpdatedMs = cacheFile.lastModified(),
                        isCached = true
                    )
                } else {
                    statuses[sub.id] = FilterListStatus(
                        id = sub.id,
                        ruleCount = builtInCountForCategory(sub.category),
                        lastUpdatedMs = 0L,
                        isCached = false,
                        lastError = "Corrupt cache ignored cleanly"
                    )
                }
            } else {
                statuses[sub.id] = FilterListStatus(
                    id = sub.id,
                    ruleCount = if (sub.id in cfg.enabledListIds) builtInCountForCategory(sub.category) else 0,
                    lastUpdatedMs = if (hasCache) cacheFile.lastModified() else 0L,
                    isCached = hasCache
                )
            }
        }

        if (badfilters.isNotEmpty()) {
            adHosts.removeAll(badfilters)
            thirdPartyAdHosts.removeAll(badfilters)
            popupHosts.removeAll(badfilters)
            trackerHosts.removeAll(badfilters)
            thirdPartyTrackerHosts.removeAll(badfilters)
        }

        // Cross-list deduplication: avoid redundant lookups in secondary sets
        thirdPartyAdHosts.removeAll(adHosts)
        thirdPartyTrackerHosts.removeAll(trackerHosts)
        popupHosts.removeAll(malwareHosts)

        _subscriptionStatuses.value = statuses

        val adPathArr = adPaths.take(MAX_PATH_TOKENS_PER_CATEGORY).toTypedArray()
        val popupPathArr = popupPaths.take(MAX_PATH_TOKENS_PER_CATEGORY).toTypedArray()
        val trackerPathArr = trackerPaths.take(MAX_PATH_TOKENS_PER_CATEGORY).toTypedArray()
        val malwarePathArr = malwarePaths.take(MAX_PATH_TOKENS_PER_CATEGORY).toTypedArray()

        val totalRules = adHosts.size + thirdPartyAdHosts.size + popupHosts.size +
            trackerHosts.size + thirdPartyTrackerHosts.size + malwareHosts.size +
            exceptionHosts.size + elemhideExceptions.size +
            adPathArr.size + popupPathArr.size + trackerPathArr.size + malwarePathArr.size +
            globalCosmetics.size + domainCosmetics.values.sumOf { it.size } +
            domainScopedAdHosts.values.sumOf { it.size }

        return CompiledRuleset(
            adHosts = adHosts,
            thirdPartyAdHosts = thirdPartyAdHosts,
            popupHosts = popupHosts,
            trackerHosts = trackerHosts,
            thirdPartyTrackerHosts = thirdPartyTrackerHosts,
            malwareHosts = malwareHosts,
            exceptionHosts = exceptionHosts,
            elemhideExceptionHosts = elemhideExceptions,
            domainScopedAdHosts = domainScopedAdHosts,
            adPathTokens = adPathArr,
            popupPathTokens = popupPathArr,
            trackerPathTokens = trackerPathArr,
            malwarePathTokens = malwarePathArr,
            globalCosmeticSelectors = globalCosmetics.toList(),
            domainCosmeticSelectors = domainCosmetics,
            totalRuleCount = totalRules,
            loadedFromCompiledCache = false
        )
    }

    private fun writeCompactSubscriptionFile(dest: File, parsed: ParsedSubscription) {
        dest.parentFile?.mkdirs()
        dest.bufferedWriter(Charsets.UTF_8, 16 * 1024).use { w ->
            for (h in parsed.hosts) { w.write("||"); w.write(h); w.write("^\n") }
            for (h in parsed.thirdPartyHosts) { w.write("||"); w.write(h); w.write("^\$3p\n") }
            for (h in parsed.popupHosts) { w.write("||"); w.write(h); w.write("^\$popup\n") }
            for (e in parsed.exceptions) { w.write("@@||"); w.write(e); w.write("^\n") }
            for (e in parsed.elemhideExceptions) { w.write("@@||"); w.write(e); w.write("^\$elemhide\n") }
            for (b in parsed.badfilterRules) { w.write("||"); w.write(b); w.write("^\$badfilter\n") }
            for ((pageDom, targets) in parsed.domainScopedHosts) {
                for (t in targets) {
                    w.write("||"); w.write(t); w.write("^\$domain="); w.write(pageDom); w.write("\n")
                }
            }
            for (p in parsed.pathTokens) { w.write(p); w.write("\n") }
            for (c in parsed.globalCosmetics) { w.write("##"); w.write(c); w.write("\n") }
            for ((dom, sels) in parsed.domainCosmetics) {
                for (s in sels) { w.write(dom); w.write("##"); w.write(s); w.write("\n") }
            }
        }
    }

    private fun readFilterMeta(filterDir: File): Map<String, StoredSubscriptionMeta> {
        val metaFile = File(filterDir, FILTER_META_FILE)
        if (!metaFile.exists() || metaFile.length() == 0L) return emptyMap()
        return runCatching {
            val root = JSONObject(metaFile.readText(Charsets.UTF_8))
            val map = LinkedHashMap<String, StoredSubscriptionMeta>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val obj = root.optJSONObject(id) ?: continue
                map[id] = StoredSubscriptionMeta(
                    version = obj.optString("version"),
                    sha256 = obj.optString("sha256"),
                    etag = obj.optString("etag"),
                    ruleCount = obj.optInt("ruleCount", 0),
                    lastUpdatedMs = obj.optLong("lastUpdatedMs", 0L)
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun writeFilterMetaAtomically(filterDir: File, metaMap: Map<String, StoredSubscriptionMeta>) {
        runCatching {
            val root = JSONObject()
            for ((id, m) in metaMap) {
                val obj = JSONObject()
                obj.put("version", m.version)
                obj.put("sha256", m.sha256)
                obj.put("etag", m.etag)
                obj.put("ruleCount", m.ruleCount)
                obj.put("lastUpdatedMs", m.lastUpdatedMs)
                root.put(id, obj)
            }
            val dest = File(filterDir, FILTER_META_FILE)
            val tmp = File(filterDir, "$FILTER_META_FILE.tmp")
            tmp.writeText(root.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        }
    }

    /**
     * Compact binary cache writer with `.tmp` atomic swap so partial writes never corrupt existing DB.
     */
    private fun writeCompiledCacheAtomically(cacheFile: File, fingerprint: String, rules: CompiledRuleset) {
        cacheFile.parentFile?.mkdirs()
        val tmpFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmpFile), 32 * 1024)).use { out ->
                out.writeInt(COMPILED_CACHE_MAGIC_INT)
                out.writeInt(COMPILED_CACHE_FORMAT_VERSION)
                out.writeUTF(fingerprint)

                fun writeCollection(items: Collection<String>) {
                    out.writeInt(items.size)
                    for (item in items) out.writeUTF(item)
                }

                writeCollection(rules.adHosts)
                writeCollection(rules.thirdPartyAdHosts)
                writeCollection(rules.popupHosts)
                writeCollection(rules.trackerHosts)
                writeCollection(rules.thirdPartyTrackerHosts)
                writeCollection(rules.malwareHosts)
                writeCollection(rules.exceptionHosts)
                writeCollection(rules.elemhideExceptionHosts)
                writeCollection(rules.adPathTokens.asList())
                writeCollection(rules.popupPathTokens.asList())
                writeCollection(rules.trackerPathTokens.asList())
                writeCollection(rules.malwarePathTokens.asList())
                writeCollection(rules.globalCosmeticSelectors)

                out.writeInt(rules.domainCosmeticSelectors.size)
                for ((dom, sels) in rules.domainCosmeticSelectors) {
                    out.writeUTF(dom)
                    out.writeInt(sels.size)
                    for (s in sels) out.writeUTF(s)
                }

                out.writeInt(rules.domainScopedAdHosts.size)
                for ((pageDom, targets) in rules.domainScopedAdHosts) {
                    out.writeUTF(pageDom)
                    out.writeInt(targets.size)
                    for (t in targets) out.writeUTF(t)
                }

                out.writeInt(COMPILED_CACHE_END_SENTINEL)
                out.flush()
            }
            if (!tmpFile.renameTo(cacheFile)) {
                tmpFile.copyTo(cacheFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (t: Throwable) {
            runCatching { tmpFile.delete() }
            throw t
        }
    }

    private fun readCompiledCacheIfMatching(cacheFile: File, expectedFingerprint: String): CompiledRuleset? {
        DataInputStream(BufferedInputStream(FileInputStream(cacheFile), 32 * 1024)).use { input ->
            if (input.readInt() != COMPILED_CACHE_MAGIC_INT) return null
            if (input.readInt() != COMPILED_CACHE_FORMAT_VERSION) return null
            if (input.readUTF() != expectedFingerprint) return null

            fun readHashSet(capHint: Int): HashSet<String> {
                val count = input.readInt()
                if (count < 0 || count > 500_000) error("Corrupt cache count")
                val set = HashSet<String>(maxOf(count, capHint))
                repeat(count) { set.add(input.readUTF()) }
                return set
            }

            fun readArrayList(): ArrayList<String> {
                val count = input.readInt()
                if (count < 0 || count > 50_000) error("Corrupt cache list count")
                val list = ArrayList<String>(count)
                repeat(count) { list.add(input.readUTF()) }
                return list
            }

            val adHosts = readHashSet(2048)
            val ad3p = readHashSet(512)
            val popHosts = readHashSet(256)
            val trkHosts = readHashSet(1024)
            val trk3p = readHashSet(256)
            val malHosts = readHashSet(512)
            val excHosts = readHashSet(256)
            val ehExcHosts = readHashSet(64)
            val adPaths = readArrayList()
            val popPaths = readArrayList()
            val trkPaths = readArrayList()
            val malPaths = readArrayList()
            val globalCos = readArrayList()

            val domCosCount = input.readInt()
            if (domCosCount < 0 || domCosCount > 100_000) return null
            val domCos = LinkedHashMap<String, MutableList<String>>(domCosCount)
            repeat(domCosCount) {
                val dom = input.readUTF()
                val selCount = input.readInt()
                val list = ArrayList<String>(selCount)
                repeat(selCount) { list.add(input.readUTF()) }
                domCos[dom] = list
            }

            val domScopedCount = input.readInt()
            if (domScopedCount < 0 || domScopedCount > 100_000) return null
            val domScoped = LinkedHashMap<String, HashSet<String>>(domScopedCount)
            repeat(domScopedCount) {
                val pageDom = input.readUTF()
                val targetCount = input.readInt()
                val set = HashSet<String>(targetCount)
                repeat(targetCount) { set.add(input.readUTF()) }
                domScoped[pageDom] = set
            }

            if (input.readInt() != COMPILED_CACHE_END_SENTINEL) return null

            val total = adHosts.size + ad3p.size + popHosts.size + trkHosts.size + trk3p.size +
                malHosts.size + excHosts.size + ehExcHosts.size +
                adPaths.size + popPaths.size + trkPaths.size + malPaths.size +
                globalCos.size + domCos.values.sumOf { it.size } + domScoped.values.sumOf { it.size }

            return CompiledRuleset(
                adHosts = adHosts,
                thirdPartyAdHosts = ad3p,
                popupHosts = popHosts,
                trackerHosts = trkHosts,
                thirdPartyTrackerHosts = trk3p,
                malwareHosts = malHosts,
                exceptionHosts = excHosts,
                elemhideExceptionHosts = ehExcHosts,
                domainScopedAdHosts = domScoped,
                adPathTokens = adPaths.toTypedArray(),
                popupPathTokens = popPaths.toTypedArray(),
                trackerPathTokens = trkPaths.toTypedArray(),
                malwarePathTokens = malPaths.toTypedArray(),
                globalCosmeticSelectors = globalCos,
                domainCosmeticSelectors = domCos,
                totalRuleCount = total,
                loadedFromCompiledCache = true
            )
        }
    }

    private fun builtInCountForCategory(cat: FilterCategory): Int = when (cat) {
        FilterCategory.ADS -> BUILTIN_AD_HOSTS.size + BUILTIN_AD_PATH_TOKENS.size
        FilterCategory.TRACKERS -> BUILTIN_TRACKER_HOSTS.size + BUILTIN_TRACKER_PATH_TOKENS.size
        FilterCategory.MALWARE -> BUILTIN_MALWARE_HOSTS.size + BUILTIN_MALWARE_PATH_TOKENS.size
        FilterCategory.ANNOYANCES -> BUILTIN_POPUP_HOSTS.size + BUILTIN_POPUP_PATH_TOKENS.size + BUILTIN_COSMETIC_SELECTORS.size
    }

    private fun matchesHostSet(host: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        if (set.contains(host)) return true
        var idx = host.indexOf('.')
        while (idx >= 0 && idx < host.length - 1) {
            val parent = host.substring(idx + 1)
            if (set.contains(parent)) return true
            idx = host.indexOf('.', idx + 1)
        }
        return false
    }

    /**
     * Zero-regex fast host parser avoiding URI(...) exceptions and string array allocations.
     */
    internal fun extractHostFast(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        val hostStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
        if (hostStart >= trimmed.length) return null
        val slash = trimmed.indexOf('/', hostStart)
        val qMark = trimmed.indexOf('?', hostStart)
        val hash = trimmed.indexOf('#', hostStart)
        var hostEnd = trimmed.length
        if (slash in hostStart until hostEnd) hostEnd = slash
        if (qMark in hostStart until hostEnd) hostEnd = qMark
        if (hash in hostStart until hostEnd) hostEnd = hash
        if (hostStart >= hostEnd) return null

        var authority = trimmed.substring(hostStart, hostEnd)
        val atIdx = authority.lastIndexOf('@')
        if (atIdx >= 0) authority = authority.substring(atIdx + 1)
        val colonIdx = authority.indexOf(':')
        if (colonIdx > 0) authority = authority.substring(0, colonIdx)
        val clean = authority.lowercase().trimEnd('.')
        return clean.takeIf { it.isNotBlank() && it.contains('.') && !it.contains(' ') }
    }

    private fun isSameSiteFast(a: String, b: String): Boolean {
        if (a == b) return true
        val rootA = rootTwoLabels(a)
        val rootB = rootTwoLabels(b)
        return rootA.isNotEmpty() && rootA == rootB
    }

    private fun rootTwoLabels(host: String): String {
        val lastDot = host.lastIndexOf('.')
        if (lastDot <= 0) return host
        val secondLastDot = host.lastIndexOf('.', lastDot - 1)
        return if (secondLastDot >= 0) host.substring(secondLastDot + 1) else host
    }

    private fun hasCleanExt(pathNoQuery: String, exts: Set<String>): Boolean {
        val dot = pathNoQuery.lastIndexOf('.')
        if (dot < 0 || pathNoQuery.length - dot > 6) return false
        val ext = pathNoQuery.substring(dot).lowercase()
        return ext in exts
    }

    internal fun isSafeStandardCssSelector(selector: String): Boolean {
        if (selector.isEmpty() || selector.length > 130) return false
        // Reject uBlock/AdGuard extended procedural selectors so Chromium never rejects CSS blocks
        if (selector.contains(":has(") ||
            selector.contains(":has-text(") ||
            selector.contains(":upward(") ||
            selector.contains(":remove(") ||
            selector.contains(":style(") ||
            selector.contains(":matches-css(") ||
            selector.contains(":-abp-") ||
            selector.contains(":xpath(") ||
            selector.contains(":nth-ancestor(") ||
            selector.contains(":watch-attr(") ||
            selector.contains(":min-text-length(") ||
            selector.contains(":others(") ||
            selector.contains("\\") ||
            selector.contains("{") ||
            selector.contains("}") ||
            selector.contains("\n") ||
            selector.contains("\r")
        ) {
            return false
        }
        return true
    }

    private fun isValidFilterDomain(domain: String): Boolean {
        if (domain.length < 4 || domain.length > 180) return false
        var hasDot = false
        var labelLen = 0
        for (i in domain.indices) {
            val c = domain[i]
            when {
                c == '.' -> {
                    if (labelLen == 0) return false
                    hasDot = true
                    labelLen = 0
                }
                (c in 'a'..'z') || (c in '0'..'9') -> labelLen++
                c == '-' -> {
                    if (labelLen == 0 || i == domain.lastIndex || domain[i + 1] == '.') return false
                    labelLen++
                }
                else -> return false
            }
        }
        return hasDot && labelLen > 0
    }

    private val NOT_BLOCKED = BlockDecision(blocked = false)
}
