package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DownloadTaskEntity
import com.example.data.media.VideoDownloadManager
import com.example.data.media.VideoMetadataHelper
import com.example.data.provider.ProviderManifestParser
import com.example.security.UrlSecurityValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Navula", appName)
    }

    @Test
    fun `formatDuration formats minutes and hours properly`() {
        assertEquals("00:00", VideoMetadataHelper.formatDuration(0L))
        assertEquals("01:05", VideoMetadataHelper.formatDuration(65_000L))
        assertEquals("1:01:05", VideoMetadataHelper.formatDuration(3_665_000L))
    }

    @Test
    fun `download progress fraction clamps cleanly`() {
        val task = DownloadTaskEntity(
            id = "1",
            title = "Sample",
            sourceUrl = "https://spacedom.live/sample.mp4",
            destinationPath = "/tmp/sample.mp4",
            totalBytes = 1000L,
            downloadedBytes = 500L
        )
        assertEquals(0.5f, task.progressFraction, 0.001f)
    }

    @Test
    fun `manifest parser handles Vega-style manifest array companion baseUrls and GitHub blob fallback`() {
        val urls = ProviderManifestParser.resolveCandidateManifestUrls("Zenda-Cross/vega-providers")
        assertTrue(urls.any { it == "https://raw.githubusercontent.com/Zenda-Cross/vega-providers/main/manifest.json" })
        assertTrue(urls.any { it == "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/manifest.json" })

        val blobUrls = ProviderManifestParser.resolveCandidateManifestUrls(
            "https://github.com/Zenda-Cross/vega-providers/blob/main/manifest.json"
        )
        assertEquals(
            listOf("https://raw.githubusercontent.com/Zenda-Cross/vega-providers/main/manifest.json"),
            blobUrls
        )

        assertEquals("vega-org/providers-template", ProviderManifestParser.RECOMMENDED_VEGA_ORG_TEMPLATE)
        val vegaSchemeUrls = ProviderManifestParser.resolveCandidateManifestUrls(
            "vega://${ProviderManifestParser.RECOMMENDED_VEGA_ORG_TEMPLATE}"
        )
        assertTrue(
            vegaSchemeUrls.any {
                it == "https://raw.githubusercontent.com/vega-org/providers-template/main/manifest.json"
            }
        )

        val sampleJson = """
            [
              {
                "display_name": "VegaCloud",
                "value": "vegacloud",
                "version": "1.2",
                "type": "english",
                "baseUrl": "https://example.org/"
              },
              {
                "display_name": "CompanionSite",
                "value": "companionsite",
                "version": "2.0",
                "type": "global"
              },
              {
                "display_name": "MirrorNeeded",
                "value": "mirrorneeded",
                "version": "1.0"
              }
            ]
        """.trimIndent()
        val parsed = ProviderManifestParser.parseManifestJson(
            jsonText = sampleJson,
            sourceManifestUrl = "https://example.org/manifest.json",
            companionBaseUrls = mapOf("companionsite" to "https://companion.example.net/")
        )
        assertEquals(3, parsed.size)
        assertEquals("VegaCloud", parsed[0].name)
        assertEquals("https://example.org/", parsed[0].baseUrl)
        assertEquals("https://companion.example.net/", parsed[1].baseUrl)
        assertEquals("", parsed[2].baseUrl)
    }

    @Test
    fun `url security validator blocks unsafe schemes private hosts and non video content`() {
        assertTrue(UrlSecurityValidator.validatePublicHttpsUrl("https://spacedom.live/movie.mp4").isSuccess)
        assertTrue(UrlSecurityValidator.validatePublicHttpsUrl("javascript:alert(1)").isFailure)
        assertTrue(UrlSecurityValidator.validatePublicHttpsUrl("file:///etc/passwd").isFailure)
        assertTrue(UrlSecurityValidator.validatePublicHttpsUrl("https://127.0.0.1/test.mp4").isFailure)
        assertTrue(UrlSecurityValidator.validatePublicHttpsUrl("https://localhost/test.mp4").isFailure)
        assertTrue(UrlSecurityValidator.validatePublicHttpsUrl("https://192.168.1.50/test.mp4").isFailure)
        assertTrue(UrlSecurityValidator.validatePublicHttpsUrl("https://169.254.169.254/latest").isFailure)
        assertTrue(UrlSecurityValidator.validatePublicHttpsUrl("https://example.com/app.apk").isFailure)

        assertFalse(UrlSecurityValidator.isValidVideoContentType("text/html; charset=utf-8", "https://example.com/video.mp4"))
        assertTrue(UrlSecurityValidator.isValidVideoContentType("video/mp4", "https://example.com/video.mp4"))
        assertTrue(UrlSecurityValidator.isValidVideoContentType("application/vnd.apple.mpegurl", "https://example.com/master.m3u8"))

        val baseDir = File("/tmp/navula_vault")
        val confined = UrlSecurityValidator.resolveConfinedChild(baseDir, "../evil/video.mp4")
        assertTrue(confined.canonicalPath.startsWith(baseDir.canonicalPath + File.separator))
    }

    @Test
    fun `download live speed eta and HLS variants parse cleanly`() {
        assertEquals("512 KB/s", VideoDownloadManager.formatSpeed(512 * 1024L))
        assertEquals("2.5 MB/s", VideoDownloadManager.formatSpeed((2.5 * 1024 * 1024).toLong()))
        assertEquals("1m 5s left", VideoDownloadManager.formatEta(65L))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.SpacedomDatabase.getInstance(context)
        val mgr = VideoDownloadManager(context, db.spacedomDao())
        val masterM3u8 = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480,AUDIO="aac"
            480p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080,AUDIO="aac"
            1080p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720,AUDIO="aac"
            720p/index.m3u8
        """.trimIndent()
        val variants = mgr.parseMasterVariants("https://cdn.example.com/videos/master.m3u8", masterM3u8)
        assertEquals(3, variants.size)
        assertEquals("1080p • 4.5 Mbps", variants[0].label)
        assertEquals("https://cdn.example.com/videos/1080p/index.m3u8", variants[0].url)
        assertEquals("aac", variants[0].audioGroupId)
        assertEquals("720p • 2.4 Mbps", variants[1].label)
        assertEquals("480p • 1.2 Mbps", variants[2].label)
    }

    @Test
    fun `content blocker engine blocks uBlock and AdGuard ads trackers and malware`() {
        val engine = com.example.security.ContentBlockerEngine
        engine.updateConfig(com.example.security.ShieldConfig())

        val adDecision = engine.evaluateUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", "https://spacedom.live/")
        assertTrue(adDecision.blocked)
        assertEquals(com.example.security.FilterCategory.ADS, adDecision.category)

        val trackerDecision = engine.evaluateUrl("https://www.google-analytics.com/analytics.js", "https://spacedom.live/")
        assertTrue(trackerDecision.blocked)
        assertEquals(com.example.security.FilterCategory.TRACKERS, trackerDecision.category)

        val malwareDecision = engine.evaluateProviderOrMediaUrl("https://coinhive.com/lib/coinhive.min.js")
        assertTrue(malwareDecision.blocked)
        assertEquals(com.example.security.FilterCategory.MALWARE, malwareDecision.category)

        val cleanMedia = engine.evaluateProviderOrMediaUrl("https://cdn.example.com/movies/1080p/index.m3u8")
        assertFalse(cleanMedia.blocked)

        val parsed = engine.parseFilterListText(
            """
            ! uBlock / AdGuard sample
            ||evil-ad.example.net^
            0.0.0.0 bad-popup.example.org
            @@||safe-partner.example.com^
            ##.floating-ad-banner
            spacedom.live##.popup-overlay
            """.trimIndent(),
            com.example.security.FilterCategory.ADS
        )
        assertTrue(parsed.hosts.contains("evil-ad.example.net"))
        assertTrue(parsed.hosts.contains("bad-popup.example.org"))
        assertTrue(parsed.exceptions.contains("safe-partner.example.com"))
        assertTrue(parsed.globalCosmetics.contains(".floating-ad-banner"))
        assertTrue(parsed.domainCosmetics["spacedom.live"]?.contains(".popup-overlay") == true)

        val cosmeticJs = engine.buildCosmeticInjectionScript("https://spacedom.live/")
        assertTrue(!cosmeticJs.isNullOrBlank() && cosmeticJs.contains("navula-ublock-cosmetic"))
    }

    @Test
    fun `stream redirect guard blocks cross-site main frame redirects while allowing same-site and challenges`() {
        val guard = com.example.security.StreamRedirectGuard

        // Cross-domain main-frame redirect while watching on spacedom.live should be blocked
        assertTrue(
            guard.shouldBlockRedirect(
                targetUrl = "https://promo-redirect.xyz/landing?click=1",
                currentPageUrl = "https://spacedom.live/movie/interstellar",
                activeProviderBaseUrl = "https://spacedom.live/",
                isMainFrame = true,
                blockRedirectsEnabled = true
            )
        )

        // Same-site navigation to another movie/page should be allowed
        assertFalse(
            guard.shouldBlockRedirect(
                targetUrl = "https://spacedom.live/watch/episode-2",
                currentPageUrl = "https://spacedom.live/movie/interstellar",
                activeProviderBaseUrl = "https://spacedom.live/",
                isMainFrame = true,
                blockRedirectsEnabled = true
            )
        )

        // Same registrable subdomain should be allowed
        assertFalse(
            guard.shouldBlockRedirect(
                targetUrl = "https://cdn.spacedom.live/player",
                currentPageUrl = "https://spacedom.live/",
                activeProviderBaseUrl = "https://spacedom.live/",
                isMainFrame = true,
                blockRedirectsEnabled = true
            )
        )

        // Cloudflare challenge host should be allowed
        assertFalse(
            guard.shouldBlockRedirect(
                targetUrl = "https://challenges.cloudflare.com/turnstile",
                currentPageUrl = "https://spacedom.live/",
                activeProviderBaseUrl = "https://spacedom.live/",
                isMainFrame = true,
                blockRedirectsEnabled = true
            )
        )

        // Subframe/embed navigation or disabled toggle should not be blocked by redirect guard
        assertFalse(
            guard.shouldBlockRedirect(
                targetUrl = "https://embed-cdn.example.net/player.html",
                currentPageUrl = "https://spacedom.live/",
                activeProviderBaseUrl = "https://spacedom.live/",
                isMainFrame = false,
                blockRedirectsEnabled = true
            )
        )
        assertFalse(
            guard.shouldBlockRedirect(
                targetUrl = "https://other-site.example.com/",
                currentPageUrl = "https://spacedom.live/",
                activeProviderBaseUrl = "https://spacedom.live/",
                isMainFrame = true,
                blockRedirectsEnabled = false
            )
        )
    }

    @Test
    fun `theme mode and user preferences defaults are valid`() {
        assertEquals(com.example.data.local.AppThemeMode.DARK, com.example.data.local.AppThemeMode.fromStored(null))
        assertEquals(com.example.data.local.AppThemeMode.LIGHT, com.example.data.local.AppThemeMode.fromStored("light"))
        assertEquals(com.example.data.local.AppThemeMode.SYSTEM, com.example.data.local.AppThemeMode.fromStored("SYSTEM"))

        val prefs = com.example.data.local.UserPreferences()
        assertTrue(prefs.blockRedirectsEnabled)
        assertFalse(prefs.showHomeFloatingControls)
        assertEquals(com.example.data.local.AppThemeMode.DARK, prefs.themeMode)
    }

    @Test
    fun `manifest parser supports cloudstream repo plugins and mega repo nested lists without DCL`() {
        val csCandidates = ProviderManifestParser.resolveCandidateManifestUrls("cloudstreamrepo://recloudstream/TestPlugins")
        assertTrue(csCandidates.any { it.endsWith("/repo.json") })
        assertTrue(csCandidates.any { it.endsWith("/plugins.json") })
        assertTrue(csCandidates.any { it.endsWith("/manifest.json") })

        val megaObj = org.json.JSONObject(
            """
            {
              "name": "MegaStream Repo",
              "pluginLists": [
                "https://cdn.example.org/cs/plugins.json",
                "./sub/plugins.json"
              ],
              "repositories": [
                "owner/other-repo",
                "http://127.0.0.1/unsafe.json"
              ]
            }
            """.trimIndent()
        )
        val nested = ProviderManifestParser.extractNestedRepoOrPluginUrls(
            megaObj,
            "https://raw.githubusercontent.com/org/mega/main/repo.json"
        )
        assertEquals(3, nested.size)
        assertTrue(nested.contains("https://cdn.example.org/cs/plugins.json"))
        assertTrue(nested.contains("https://raw.githubusercontent.com/org/mega/main/sub/plugins.json"))
        assertTrue(nested.contains("https://raw.githubusercontent.com/owner/other-repo/main/repo.json"))

        // CloudStream plugins.json entry puts .cs3 binary in "url" — must NEVER be adopted as baseUrl (Play DCL compliance)
        val csPluginsJson = """
            [
              {
                "name": "CineCloud",
                "internalName": "CineCloudProvider",
                "apiVersion": 1,
                "version": 12,
                "tvTypes": ["Movie", "TvSeries"],
                "language": "en",
                "status": 1,
                "url": "https://github.com/org/repo/releases/download/CineCloud.cs3",
                "mainUrl": "https://cine.example.org"
              },
              {
                "name": "BinaryOnlyPlugin",
                "internalName": "BinaryOnly",
                "apiVersion": 1,
                "status": 0,
                "url": "https://github.com/org/repo/releases/download/BinaryOnly.cs3"
              }
            ]
        """.trimIndent()

        val parsed = ProviderManifestParser.parseManifestJson(
            jsonText = csPluginsJson,
            sourceManifestUrl = "https://cdn.example.org/cs/plugins.json"
        )
        assertEquals(2, parsed.size)
        assertEquals("CineCloud", parsed[0].name)
        assertEquals("CloudStream", parsed[0].category)
        assertEquals("https://cine.example.org/", parsed[0].baseUrl)
        assertTrue(parsed[0].isEnabled)

        assertEquals("BinaryOnlyPlugin", parsed[1].name)
        assertEquals("", parsed[1].baseUrl)
        assertFalse(parsed[1].isEnabled)

        assertTrue(UrlSecurityValidator.isNonSiteArchiveOrScriptUrl("https://cdn.example.org/Plugin.cs3"))
        assertTrue(UrlSecurityValidator.validatePublicHttpsUrl("https://cdn.example.org/Plugin.cs3").isFailure)
    }

    @Test
    fun `workmanager enqueues and cancels secure video download`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        val db = com.example.data.local.SpacedomDatabase.getInstance(context)
        val mgr = VideoDownloadManager(context, db.spacedomDao())

        val taskId = mgr.enqueueDownload(
            url = "https://cdn.example.com/streams/cosmos_1080p.mp4",
            suggestedTitle = "Cosmos Odyssey"
        )

        val workManager = androidx.work.WorkManager.getInstance(context)
        val enqueuedInfos = workManager
            .getWorkInfosForUniqueWork(com.example.data.media.VideoDownloadWorker.uniqueWorkName(taskId))
            .get()
        assertEquals(1, enqueuedInfos.size)
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, enqueuedInfos[0].state)
        assertTrue(enqueuedInfos[0].tags.contains(com.example.data.media.VideoDownloadWorker.TAG_DOWNLOAD))

        mgr.cancelDownload(taskId)
        val cancelledInfos = workManager
            .getWorkInfosForUniqueWork(com.example.data.media.VideoDownloadWorker.uniqueWorkName(taskId))
            .get()
        assertEquals(androidx.work.WorkInfo.State.CANCELLED, cancelledInfos[0].state)
    }

    @Test
    fun `universal manifest parser supports dot git repos Stremio Aniyomi and root object maps`() {
        val gitCandidates = ProviderManifestParser.resolveCandidateManifestUrls("https://github.com/vega-org/vega-app.git")
        assertTrue(gitCandidates.any { it == "https://raw.githubusercontent.com/vega-org/vega-app/main/manifest.json" })
        assertTrue(gitCandidates.any { it == "https://raw.githubusercontent.com/vega-org/vega-app/main/index.min.json" })

        val stremioUrls = ProviderManifestParser.resolveCandidateManifestUrls("stremio://torrentio.strem.fun/manifest.json")
        assertEquals(listOf("https://torrentio.strem.fun/manifest.json"), stremioUrls)

        val stremioAddonJson = """
            {
              "id": "com.example.stremio.catalog",
              "name": "Cosmic Stremio",
              "version": "1.4.0",
              "description": "Public domain cinema catalog",
              "resources": ["catalog", "stream"],
              "catalogs": [{"type": "movie", "id": "top"}],
              "website": "https://cinema.example.org/"
            }
        """.trimIndent()
        val stremioParsed = ProviderManifestParser.parseManifestJson(
            jsonText = stremioAddonJson,
            sourceManifestUrl = "https://addon.example.org/manifest.json"
        )
        assertEquals(1, stremioParsed.size)
        assertEquals("Cosmic Stremio", stremioParsed[0].name)
        assertEquals("Stremio", stremioParsed[0].category)
        assertEquals("https://cinema.example.org/", stremioParsed[0].baseUrl)

        val aniyomiJson = """
            [
              {
                "name": "Aniyomi: StreamPack",
                "pkg": "eu.kanade.tachiyomi.animeextension.en.streampack",
                "apk": "https://cdn.example.org/streampack.apk",
                "version": "1.2.3",
                "sources": [
                  {
                    "name": "StreamPack En",
                    "lang": "en",
                    "id": "12345",
                    "baseUrl": "https://streampack.example.org"
                  }
                ]
              }
            ]
        """.trimIndent()
        val aniyomiParsed = ProviderManifestParser.parseManifestJson(
            jsonText = aniyomiJson,
            sourceManifestUrl = "https://raw.githubusercontent.com/org/ani-repo/main/index.min.json"
        )
        assertEquals(1, aniyomiParsed.size)
        assertEquals("StreamPack En", aniyomiParsed[0].name)
        assertEquals("Aniyomi", aniyomiParsed[0].category)
        assertEquals("https://streampack.example.org/", aniyomiParsed[0].baseUrl)

        val objectMapJson = """
            {
              "nova": {
                "display_name": "Nova Cinema",
                "host": "https://nova.example.net",
                "version": "2.1"
              }
            }
        """.trimIndent()
        val mapParsed = ProviderManifestParser.parseManifestJson(
            jsonText = objectMapJson,
            sourceManifestUrl = "https://repo.example.net/providers.json"
        )
        assertEquals(1, mapParsed.size)
        assertEquals("Nova Cinema", mapParsed[0].name)
        assertEquals("https://nova.example.net/", mapParsed[0].baseUrl)

        assertTrue(
            UrlSecurityValidator.isValidVideoContentType(
                contentType = "application/octet-stream",
                url = "https://cdn.example.com/v1/stream/token123456",
                allowTokenizedOctetStream = true
            )
        )
        assertFalse(
            UrlSecurityValidator.isValidVideoContentType(
                contentType = "application/json",
                url = "https://cdn.example.com/v1/stream/token123456",
                allowTokenizedOctetStream = true
            )
        )
        assertFalse(
            UrlSecurityValidator.isValidVideoContentType(
                contentType = "application/octet-stream",
                url = "https://cdn.example.com/payload.apk",
                allowTokenizedOctetStream = true
            )
        )
    }

    @Test
    fun `default startup provider is spacedom and curated movie and live tv sources are deduplicated`() {
        val defaultProv = com.example.data.local.SpacedomDatabase.defaultSpacedomProvider()
        assertEquals("https://spacedom.live/", defaultProv.baseUrl)
        assertEquals("Movie", defaultProv.category)
        assertTrue(defaultProv.isActive)

        val curated = com.example.data.local.SpacedomDatabase.curatedMovieAndSportsProviders()
        assertEquals(31, curated.size)
        assertEquals(22, curated.count { it.category == "Movie" })
        assertEquals(9, curated.count { it.category == "Live TV & Sports" })
        assertEquals(31, curated.map { it.baseUrl }.distinct().size)

        val urls = curated.map { it.baseUrl }.toSet()
        assertTrue(urls.contains("https://7movies.ws/"))
        assertTrue(urls.contains("https://officialmoviebox.com/"))
        assertTrue(urls.contains("https://www.youtube.com/"))
        assertTrue(urls.contains("https://www.cinezo.st/"))
        assertTrue(urls.contains("https://sleepy.lat/"))
        assertTrue(urls.contains("https://ntv.st/matches/raptor"))
        assertTrue(urls.contains("https://zhangboheng.github.io/Easy-Web-TV-M3u8/routes/tv.html"))
        assertTrue(urls.contains("https://sportsbite.org/"))
    }

    @Test
    fun `manual source switching helper prioritizes same section sources and excludes failed ones`() {
        val defaultProv = com.example.data.local.SpacedomDatabase.defaultSpacedomProvider()
        val curated = com.example.data.local.SpacedomDatabase.curatedMovieAndSportsProviders()
        val allProviders = listOf(defaultProv) + curated
        val movieBox = curated.first { it.baseUrl == "https://officialmoviebox.com/" }
        val flyStream = curated.first { it.baseUrl == "https://flystream.net/" }

        val candidatesFromMovie = com.example.ui.viewmodel.MainViewModel.pickNextFailoverCandidates(
            allProviders = allProviders,
            currentProvider = movieBox,
            excludedIds = setOf(flyStream.id)
        )
        assertFalse(candidatesFromMovie.any { it.id == movieBox.id })
        assertFalse(candidatesFromMovie.any { it.id == flyStream.id })
        assertEquals("Movie", candidatesFromMovie.first().category)

        val ntv = curated.first { it.baseUrl == "https://ntv.st/matches/raptor" }
        val candidatesFromSports = com.example.ui.viewmodel.MainViewModel.pickNextFailoverCandidates(
            allProviders = allProviders,
            currentProvider = ntv,
            excludedIds = emptySet()
        )
        assertEquals("Live TV & Sports", candidatesFromSports.first().category)
        assertEquals("https://zlive.st/", candidatesFromSports.first().baseUrl)
    }

    @Test
    fun `app mode cleanup script preserves scrolling strips footers and extracts github slugs`() {
        val cleanupMovieJs = com.example.security.ContentBlockerEngine.buildAppModeCleanupScript("https://spacedom.live/")
        assertTrue(cleanupMovieJs.contains("navula-app-mode-style"))
        assertTrue(cleanupMovieJs.contains("all rights reserved"))
        assertTrue(cleanupMovieJs.contains("ensureScrollUnlocked"))
        assertFalse(cleanupMovieJs.contains("overflow-x: hidden"))
        assertFalse(cleanupMovieJs.contains("overscroll-behavior-y: contain"))
        assertTrue(cleanupMovieJs.contains("if (false) return;"))

        val cleanupYoutubeJs = com.example.security.ContentBlockerEngine.buildAppModeCleanupScript("https://www.youtube.com/")
        assertTrue(cleanupYoutubeJs.contains("if (true) return;"))

        assertEquals(
            "vega-org/vega-app",
            ProviderManifestParser.extractGitHubSlug("https://github.com/vega-org/vega-app.git")
        )
        assertEquals(
            "Zenda-Cross/vega-providers",
            ProviderManifestParser.extractGitHubSlug("https://github.com/Zenda-Cross/vega-providers")
        )
        assertEquals(
            "recloudstream/TestPlugins",
            ProviderManifestParser.extractGitHubSlug("recloudstream/TestPlugins")
        )
    }

    @Test
    fun `provider persistence restores selected provider home URL and never restores movie subpages`() {
        val defaultProv = com.example.data.local.SpacedomDatabase.defaultSpacedomProvider()
        val curated = com.example.data.local.SpacedomDatabase.curatedMovieAndSportsProviders()
        val movieBox = curated.first { it.baseUrl == "https://officialmoviebox.com/" }
        val allProviders = listOf(defaultProv) + curated

        val (chosen, homeUrl) = com.example.ui.viewmodel.MainViewModel.resolveProviderHomeStartupUrl(
            dbActive = defaultProv,
            allProviders = allProviders,
            persistedProviderId = movieBox.id
        )
        assertEquals(movieBox.id, chosen?.id)
        assertEquals("https://officialmoviebox.com/", homeUrl)

        val (fallbackChosen, fallbackHomeUrl) = com.example.ui.viewmodel.MainViewModel.resolveProviderHomeStartupUrl(
            dbActive = null,
            allProviders = allProviders,
            persistedProviderId = "non_existent_provider"
        )
        assertEquals(defaultProv.id, fallbackChosen?.id)
        assertEquals("https://spacedom.live/", fallbackHomeUrl)
    }

    @Test
    fun `home navigation resets temporary movie playback state without switching active provider`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.viewmodel.MainViewModel(app)
        val movieBox = com.example.data.local.SpacedomDatabase.curatedMovieAndSportsProviders()
            .first { it.baseUrl == "https://officialmoviebox.com/" }

        vm.activateAndOpenProvider(movieBox)
        vm.updateStreamNavigation(
            url = "https://officialmoviebox.com/watch/interstellar-episode-3",
            title = "Interstellar",
            canGoBack = true,
            canGoForward = false,
            hasError = true,
            errorDescription = "Temporary error"
        )
        vm.addDetectedStream("https://cdn.example.com/interstellar/master.m3u8", "Interstellar")
        vm.setStreamFullscreen(true)
        vm.saveWebViewState(android.os.Bundle().apply { putString("k", "v") })

        vm.navigateToCurrentProviderHome()

        val state = vm.streamState.value
        assertEquals("https://officialmoviebox.com/", state.currentUrl)
        assertEquals("https://officialmoviebox.com/", vm.pendingStreamUrl.value)
        assertFalse(state.canGoBack)
        assertFalse(state.canGoForward)
        assertFalse(state.hasError)
        assertTrue(state.detectedStreams.isEmpty())
        assertFalse(vm.isStreamFullscreen.value)
        assertEquals(null, vm.savedWebViewState)
        assertEquals(1, vm.homeResetTrigger.value)
    }

    @Test
    fun `content blocker applies custom block and whitelist rules immediately without blocking`() {
        val engine = com.example.security.ContentBlockerEngine
        engine.updateConfig(
            com.example.security.ShieldConfig(
                customBlockedDomains = setOf("custom-popup.example.net"),
                whitelistedDomains = setOf("safe-mirror.example.org")
            )
        )

        val customBlocked = engine.evaluateUrl("https://sub.custom-popup.example.net/ad.js", "https://spacedom.live/")
        assertTrue(customBlocked.blocked)

        val whitelisted = engine.evaluateUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", "https://safe-mirror.example.org/")
        assertFalse(whitelisted.blocked)

        engine.updateConfig(com.example.security.ShieldConfig())
    }

    @Test
    fun `performance monitor operates as lightweight diagnostic logger without stateflow overhead`() {
        val monitor = com.example.telemetry.PerformanceMonitor
        monitor.onWebViewCreated()
        monitor.onWebViewTimersStateChanged(true)
        monitor.onWebViewTimersStateChanged(false)
        monitor.onPlayerCreated()
        monitor.onPlayerReleased()
        monitor.onWebViewDestroyed()
        monitor.logDiagnostic("ShieldBench", "ok")
    }

    @Test
    fun `hls manifest inspection extracts truthful audio subtitle tracks variants and blocks DRM without inventing data`() {
        val masterWithAudioAndSubs = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",LANGUAGE="en",DEFAULT=YES,URI="audio_en/index.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="Bengali",LANGUAGE="bn",DEFAULT=NO,URI="audio_bn/index.m3u8"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English CC",LANGUAGE="en",URI="subs/en.vtt"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Bengali",LANGUAGE="bn",URI="subs/bn.srt"
            #EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=3840x2160,AUDIO="aac",SUBTITLES="subs",CODECS="avc1.640033,mp4a.40.2"
            2160p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080,AUDIO="aac",SUBTITLES="subs"
            1080p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,AUDIO="aac"
            360p/index.m3u8
        """.trimIndent()

        val variantPlaylistWithDuration = """
            #EXTM3U
            #EXTINF:10.0,
            seg_0.ts
            #EXTINF:10.0,
            seg_1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val info = kotlinx.coroutines.runBlocking {
            VideoDownloadManager.inspectHlsManifestToPrefetchInfo(
                safeUrl = "https://cdn.example.com/movie/master.m3u8",
                resolvedUrl = "https://cdn.example.com/movie/master.m3u8",
                contentType = "application/vnd.apple.mpegurl",
                manifestText = masterWithAudioAndSubs,
                tlsVerified = true,
                fetchVariantPlaylist = { variantPlaylistWithDuration }
            )
        }

        assertFalse(info.isDrmProtected)
        assertEquals(3, info.variants.size)
        assertEquals("4K (2160p)", info.variants[0].resolutionLabel)
        assertEquals("1080p", info.variants[1].resolutionLabel)
        assertEquals("360p", info.variants[2].resolutionLabel)
        assertEquals(20_000_000L, info.variants[0].estimatedSizeBytes)
        assertEquals(2, info.audioTracks.size)
        assertEquals("en", info.audioTracks[0].language)
        assertEquals("bn", info.audioTracks[1].language)
        assertEquals(2, info.subtitleTracks.size)
        assertEquals("VTT", info.subtitleTracks[0].format)
        assertEquals("SRT", info.subtitleTracks[1].format)

        // Truthfulness check: plain variant without RESOLUTION/AUDIO/SUBTITLES or duration must NOT invent fake data
        val minimalMaster = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1500000
            stream/index.m3u8
        """.trimIndent()
        val minimalInfo = kotlinx.coroutines.runBlocking {
            VideoDownloadManager.inspectHlsManifestToPrefetchInfo(
                safeUrl = "https://cdn.example.com/minimal/master.m3u8",
                resolvedUrl = "https://cdn.example.com/minimal/master.m3u8",
                contentType = "application/vnd.apple.mpegurl",
                manifestText = minimalMaster,
                tlsVerified = true,
                fetchVariantPlaylist = { null }
            )
        }
        assertEquals(1, minimalInfo.variants.size)
        assertEquals(null, minimalInfo.variants[0].resolutionLabel)
        assertEquals(null, minimalInfo.variants[0].estimatedSizeBytes)
        assertTrue(minimalInfo.audioTracks.isEmpty())
        assertTrue(minimalInfo.subtitleTracks.isEmpty())
        assertEquals(null, minimalInfo.audioLanguage)

        // DRM protection check: SAMPLE-AES and Widevine must mark isDrmProtected = true cleanly
        val drmManifest = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://key",KEYFORMAT="com.apple.streamingkeydelivery"
            #EXTINF:6.0,
            enc_0.m4s
        """.trimIndent()
        val drmInfo = kotlinx.coroutines.runBlocking {
            VideoDownloadManager.inspectHlsManifestToPrefetchInfo(
                safeUrl = "https://cdn.example.com/drm/index.m3u8",
                resolvedUrl = "https://cdn.example.com/drm/index.m3u8",
                contentType = "application/vnd.apple.mpegurl",
                manifestText = drmManifest,
                tlsVerified = true,
                fetchVariantPlaylist = { null }
            )
        }
        assertTrue(drmInfo.isDrmProtected)
        assertEquals("DRM_PROTECTED", drmInfo.mediaKind)
    }

    @Test
    fun `direct prefetch info validates audio subtitles and prevents duplicate downloads with pause resume`() {
        assertTrue(UrlSecurityValidator.isValidVideoContentType("audio/mpeg", "https://cdn.example.com/song.mp3"))
        assertTrue(UrlSecurityValidator.isValidVideoContentType("text/vtt", "https://cdn.example.com/en.vtt"))
        assertTrue(UrlSecurityValidator.isValidVideoContentType("application/x-subrip", "https://cdn.example.com/en.srt"))

        val mp3Info = VideoDownloadManager.buildDirectPrefetchInfo(
            safeUrl = "https://cdn.example.com/track.mp3",
            resolvedUrl = "https://cdn.example.com/track.mp3",
            contentType = "audio/mpeg",
            contentDisposition = null,
            contentLength = 4_194_304L,
            supportsRange = true,
            tlsVerified = true
        )
        assertEquals("AUDIO", mp3Info.mediaKind)
        assertEquals("mp3", mp3Info.fileExtension)
        assertEquals(null, mp3Info.qualityLabel)
        assertEquals(1, mp3Info.audioTracks.size)

        val unknownResMp4 = VideoDownloadManager.buildDirectPrefetchInfo(
            safeUrl = "https://cdn.example.com/video_file.mp4",
            resolvedUrl = "https://cdn.example.com/video_file.mp4",
            contentType = "video/mp4",
            contentDisposition = null,
            contentLength = 0L,
            supportsRange = false,
            tlsVerified = true
        )
        assertEquals("VIDEO", unknownResMp4.mediaKind)
        assertEquals(null, unknownResMp4.qualityLabel)
        assertEquals(0L, unknownResMp4.contentLengthBytes)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        val db = com.example.data.local.SpacedomDatabase.getInstance(context)
        val mgr = VideoDownloadManager(context, db.spacedomDao())

        val firstOutcome = kotlinx.coroutines.runBlocking {
            mgr.enqueueDownloadChecked(
                url = "https://cdn.example.com/movies/dup_check_1080p.mp4",
                suggestedTitle = "Dup Test Movie",
                formatLabel = "MP4",
                qualityLabel = "1080p"
            )
        }
        assertFalse(firstOutcome.isDuplicate)

        // Enqueue same source immediately -> duplicate guard blocks duplicate queue item
        val secondOutcome = kotlinx.coroutines.runBlocking {
            mgr.enqueueDownloadChecked(
                url = "https://cdn.example.com/movies/dup_check_1080p.mp4",
                suggestedTitle = "Dup Test Movie"
            )
        }
        assertTrue(secondOutcome.isDuplicate)
        assertEquals(firstOutcome.taskId, secondOutcome.taskId)

        // Pause -> status becomes PAUSED; Resume -> re-queues cleanly
        mgr.pauseDownload(firstOutcome.taskId)
        val pausedTask = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.delay(150)
            db.spacedomDao().getDownloadById(firstOutcome.taskId)
        }
        assertEquals(DownloadTaskEntity.STATUS_PAUSED, pausedTask?.status)
        assertTrue(mgr.resumeDownload(pausedTask!!))
        mgr.cancelDownload(firstOutcome.taskId)
    }

    @Test
    fun `content blocker supports modifiers badfilter elemhide exceptions and preserves first party media`() {
        val engine = com.example.security.ContentBlockerEngine
        val parsed = engine.parseFilterListText(
            """
            ! EasyList / AdGuard modifier test
            ||ad-thirdparty.example.net^${'$'}third-party
            ||pop-redirect.example.org^${'$'}popup
            ||scoped-banner.example.com^${'$'}domain=spacedom.live
            ||cancelled-rule.example.net^
            ||cancelled-rule.example.net^${'$'}badfilter
            @@||safe-stream.example.org^${'$'}elemhide
            @@||allowed-cdn.example.net^
            ##.valid-banner-ad
            ##div:has-text(Sponsored)
            ##div:style(display:block)
            ##.hero-ad:-abp-contains(Promo)
            spacedom.live##+js(nobab)
            spacedom.live##.site-specific-ad
            """.trimIndent(),
            com.example.security.FilterCategory.ADS
        )

        assertTrue(parsed.thirdPartyHosts.contains("ad-thirdparty.example.net"))
        assertTrue(parsed.popupHosts.contains("pop-redirect.example.org"))
        assertTrue(parsed.domainScopedHosts["spacedom.live"]?.contains("scoped-banner.example.com") == true)
        assertFalse(parsed.hosts.contains("cancelled-rule.example.net"))
        assertTrue(parsed.elemhideExceptions.contains("safe-stream.example.org"))
        assertTrue(parsed.exceptions.contains("allowed-cdn.example.net"))

        // Extended procedural uBO/ABP selectors and ##+js scriptlets MUST be filtered out so Chromium CSS stays intact
        assertTrue(parsed.globalCosmetics.contains(".valid-banner-ad"))
        assertFalse(parsed.globalCosmetics.any { it.contains(":has-text") || it.contains(":style(") || it.contains(":-abp-") })
        assertTrue(parsed.domainCosmetics["spacedom.live"]?.contains(".site-specific-ad") == true)
        assertFalse(parsed.domainCosmetics["spacedom.live"]?.any { it.contains("+js") } == true)
    }

    @Test
    fun `content blocker compiles binary v3 cache reloads fast and omits MutationObserver`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filterDir = File(context.filesDir, "filter_lists").apply { mkdirs() }
        filterDir.listFiles()?.forEach { it.delete() }

        val sampleSubFile = File(filterDir, "adguard_base.txt")
        val rawContent =
            """
            ||banner-cdn.example.net^
            ||pop-tracker.example.org^${'$'}popup
            ||thirdparty-ad.example.com^${'$'}3p
            ##.sponsored-banner-slot
            """.trimIndent()
        sampleSubFile.writeText(rawContent)

        val engine = com.example.security.ContentBlockerEngine
        engine.initialize(context)
        engine.updateConfig(com.example.security.ShieldConfig())
        engine.rebuildRuleset()

        val cacheFile = File(filterDir, "compiled_shield_v3.cache")
        assertTrue(cacheFile.exists() && cacheFile.length() > 0L)

        // Blank out raw subscription text preserving exact length & mtime, and simulate cold restart reload
        val origLen = sampleSubFile.length().toInt()
        val origTime = sampleSubFile.lastModified()
        sampleSubFile.writeText(" ".repeat(origLen))
        sampleSubFile.setLastModified(origTime)
        engine.resetInMemoryFingerprintForTest()
        val binaryLoadStartNs = System.nanoTime()
        engine.rebuildRuleset()
        val binaryLoadMs = (System.nanoTime() - binaryLoadStartNs) / 1_000_000.0
        println("NAVULA_BENCHMARK binary_v3_cache_reload_ms=$binaryLoadMs cache_bytes=${cacheFile.length()}")

        assertTrue(engine.metrics.value.cacheLoadedFast)
        assertTrue(engine.evaluateUrl("https://banner-cdn.example.net/banner.js", "https://spacedom.live/").blocked)
        assertTrue(engine.evaluateUrl("https://thirdparty-ad.example.com/ad.js", "https://spacedom.live/").blocked)
        // First-party request to same host must NOT match $3p rule
        assertFalse(engine.evaluateUrl("https://thirdparty-ad.example.com/asset.js", "https://thirdparty-ad.example.com/").blocked)

        // First-party and third-party CDN video segments/subtitles remain preserved
        assertFalse(engine.evaluateUrl("https://spacedom.live/media/movie_1080p.mp4", "https://spacedom.live/").blocked)
        assertFalse(engine.evaluateUrl("https://video-cdn.example.org/hls/1080p/seg_01.ts", "https://spacedom.live/").blocked)
        assertFalse(engine.evaluateUrl("https://sub-cdn.example.org/tracks/en.vtt", "https://spacedom.live/").blocked)
        assertFalse(engine.evaluateUrl("https://spacedom.live/cdn-cgi/challenge-platform/h/g", "https://spacedom.live/").blocked)

        // Chunked CSS cosmetic injection output should be one-shot with ZERO MutationObserver loops
        val cosmeticJs = engine.buildCosmeticInjectionScript("https://spacedom.live/")
        assertTrue(!cosmeticJs.isNullOrBlank())
        assertTrue(cosmeticJs!!.contains("navula-ublock-cosmetic"))
        assertTrue(cosmeticJs.contains(".sponsored-banner-slot"))
        assertFalse(cosmeticJs.contains("MutationObserver"))
        assertFalse(cosmeticJs.contains("setTimeout"))

        val cleanupJs = engine.buildAppModeCleanupScript("https://spacedom.live/")
        assertFalse(cleanupJs.contains("MutationObserver"))
        assertFalse(cleanupJs.contains("setTimeout"))
    }

    @Test
    fun `content blocker validates version checksum skips unchanged updates and rolls back corrupt syncs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filterDir = File(context.filesDir, "filter_lists_atomic_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val engine = com.example.security.ContentBlockerEngine
        val sub = engine.SUBSCRIPTIONS.first { it.id == "adguard_base" }

        val v1Raw = """
            ! Title: AdGuard Base
            ! Version: 2.0.100
            ||ad-v1.example.net^
            ||tracker-v1.example.net^
        """.trimIndent()

        val r1 = engine.applySubscriptionUpdateAtomically(filterDir, sub, v1Raw, previousMeta = null, etag = "\"v1\"")
        assertTrue(r1.changed)
        assertEquals("2.0.100", r1.meta.version)
        assertEquals(64, r1.meta.sha256.length)
        val cachedFile = File(filterDir, "adguard_base.txt")
        assertTrue(cachedFile.exists())
        val v1SavedText = cachedFile.readText()
        assertTrue(v1SavedText.contains("ad-v1.example.net"))

        // Re-applying identical checksum + version MUST skip disk write and return changed = false
        val rSame = engine.applySubscriptionUpdateAtomically(filterDir, sub, v1Raw, previousMeta = r1.meta, etag = "\"v1\"")
        assertFalse(rSame.changed)

        // Attempting invalid/empty payload MUST throw and preserve previous working cached DB
        var failedCorrupt = false
        try {
            engine.applySubscriptionUpdateAtomically(filterDir, sub, "! empty invalid payload", previousMeta = r1.meta)
        } catch (_: IllegalStateException) {
            failedCorrupt = true
        }
        assertTrue(failedCorrupt)
        assertEquals(v1SavedText, cachedFile.readText())
        assertFalse(File(filterDir, "adguard_base.txt.tmp").exists())

        // Regional lists must remain disabled by default so idle memory stays minimal
        val regionalSubs = engine.SUBSCRIPTIONS.filter { it.vendor == "Regional" }
        assertTrue(regionalSubs.size >= 3)
        assertTrue(regionalSubs.all { !it.enabledByDefault })

        filterDir.deleteRecursively()
    }

    @Test
    fun `filter update worker schedules periodic sync under unmetered wifi and charging constraints`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        com.example.security.FilterUpdateWorker.schedulePeriodic(context)

        val workManager = androidx.work.WorkManager.getInstance(context)
        val infos = workManager
            .getWorkInfosForUniqueWork(com.example.security.FilterUpdateWorker.UNIQUE_WORK_NAME)
            .get()
        assertEquals(1, infos.size)
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, infos[0].state)
        assertTrue(infos[0].tags.contains(com.example.security.FilterUpdateWorker.TAG_FILTER_SYNC))
        assertEquals(androidx.work.NetworkType.UNMETERED, infos[0].constraints.requiredNetworkType)
        assertTrue(infos[0].constraints.requiresCharging())
        assertTrue(infos[0].constraints.requiresBatteryNotLow())
    }

    @Test
    fun `stream redirect guard detects redirect loops and bounce chains while preserving auth and challenges`() {
        val guard = com.example.security.StreamRedirectGuard
        guard.resetHopHistory()

        val t0 = 10_000L
        assertFalse(guard.recordAndCheckRedirectLoop("https://a.example.com/step1", t0))
        assertFalse(guard.recordAndCheckRedirectLoop("https://b.example.net/step2", t0 + 300L))
        assertFalse(guard.recordAndCheckRedirectLoop("https://a.example.com/step1", t0 + 600L))
        assertFalse(guard.recordAndCheckRedirectLoop("https://b.example.net/step2", t0 + 900L))
        // Repeated bounce back to a.example.com/step1 within loop window triggers loop protection
        assertTrue(guard.recordAndCheckRedirectLoop("https://a.example.com/step1", t0 + 1200L))

        guard.resetHopHistory()
        assertFalse(guard.recordAndCheckRedirectLoop("https://hop1.example.com/", t0))
        assertFalse(guard.recordAndCheckRedirectLoop("https://hop2.example.net/", t0 + 200L))
        assertFalse(guard.recordAndCheckRedirectLoop("https://hop3.example.org/", t0 + 400L))
        // 4th distinct cross-domain hop inside 4 seconds triggers rapid bounce block
        assertTrue(guard.recordAndCheckRedirectLoop("https://hop4.example.info/", t0 + 600L))

        assertTrue(guard.isEssentialAuthOrChallengeUrl("https://challenges.cloudflare.com/turnstile"))
        assertTrue(guard.isEssentialAuthOrChallengeUrl("https://accounts.google.com/o/oauth2/v2/auth"))
        assertFalse(guard.isEssentialAuthOrChallengeUrl("https://popads.net/click?id=1"))
        guard.resetHopHistory()
    }

    @Test
    fun `content blocker lookup benchmark and popup toggle settings behave accurately`() {
        val engine = com.example.security.ContentBlockerEngine
        engine.updateConfig(com.example.security.ShieldConfig(blockPopups = false))
        assertFalse(engine.evaluateUrl("https://popads.net/pop.js", "https://spacedom.live/").blocked)

        engine.updateConfig(com.example.security.ShieldConfig(blockPopups = true))
        val popupDecision = engine.evaluateUrl("https://popads.net/pop.js", "https://spacedom.live/")
        assertTrue(popupDecision.blocked)
        assertEquals(com.example.security.FilterCategory.ANNOYANCES, popupDecision.category)

        // Reset defaults check
        engine.resetToDefaults()
        assertTrue(engine.getConfig().blockPopups)
        assertEquals(0, engine.metrics.value.totalBlocked)

        // Raw parse vs compiled lookup benchmark
        val syntheticRules = buildString {
            for (i in 1..2000) {
                append("||ad-bench-").append(i).append(".example.net^\n")
            }
        }
        val parseStartNs = System.nanoTime()
        val parsedSynth = engine.parseFilterListText(syntheticRules, com.example.security.FilterCategory.ADS)
        val parseMs = (System.nanoTime() - parseStartNs) / 1_000_000.0
        assertEquals(2000, parsedSynth.hosts.size)

        val urls = listOf(
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://www.google-analytics.com/analytics.js",
            "https://popads.net/pop.js",
            "https://spacedom.live/movies/poster.jpg",
            "https://cdn.example.com/streams/index.m3u8",
            "https://spacedom.live/api/catalog?page=2"
        )
        val coldStartNs = System.nanoTime()
        for (i in 0 until 2000) {
            engine.evaluateUrl("${urls[i % urls.size]}&q=$i", "https://spacedom.live/")
        }
        val coldElapsedMs = (System.nanoTime() - coldStartNs) / 1_000_000.0

        val warmStartNs = System.nanoTime()
        for (i in 0 until 2000) {
            engine.evaluateUrl(urls[i % urls.size], "https://spacedom.live/")
        }
        val warmElapsedMs = (System.nanoTime() - warmStartNs) / 1_000_000.0

        println("NAVULA_BENCHMARK parse_2000_rules_ms=$parseMs cold_2000_urls_ms=$coldElapsedMs warm_cached_2000_urls_ms=$warmElapsedMs")
        assertTrue(warmElapsedMs <= 150.0)

        val prefs = com.example.data.local.UserPreferences()
        assertTrue(prefs.blockPopupsEnabled)
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.viewmodel.MainViewModel(app)
        vm.setBlockPopupsEnabled(false)
        vm.resetFilterSettings()
    }
}
