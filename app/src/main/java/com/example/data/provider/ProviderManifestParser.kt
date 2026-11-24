package com.example.data.provider

import com.example.data.local.ProviderExtensionEntity
import com.example.security.ContentBlockerEngine
import com.example.security.UrlSecurityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

data class ProviderHealthResult(
    val isReachable: Boolean,
    val statusCode: Int = 0,
    val latencyMs: Long = 0L,
    val resolvedUrl: String? = null,
    val message: String = ""
)

object ProviderManifestParser {

    const val RECOMMENDED_VEGA_REPO = "Zenda-Cross/vega-providers"
    const val RECOMMENDED_VEGA_ORG_TEMPLATE = "vega-org/providers-template"
    const val RECOMMENDED_CLOUDSTREAM_HINT = "recloudstream/TestPlugins"
    private const val MAX_REPO_RECURSION_DEPTH = 2
    private const val MAX_SUBLISTS_PER_FETCH = 24

    private val client: OkHttpClient by lazy {
        UrlSecurityValidator.createSecureOkHttpClient(
            connectTimeoutSeconds = 12L,
            readTimeoutSeconds = 15L
        )
    }

    private val fastProbeClient: OkHttpClient by lazy {
        UrlSecurityValidator.createSecureOkHttpClient(
            connectTimeoutSeconds = 5L,
            readTimeoutSeconds = 6L
        )
    }

    suspend fun fetchProvidersFromSource(rawInput: String): Result<List<ProviderExtensionEntity>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmedInput = normalizeRepoSchemePrefix(rawInput.trim())
                val candidateUrls = resolveCandidateManifestUrls(rawInput)
                var lastError: Throwable? = null

                for (url in candidateUrls) {
                    val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(url).getOrElse {
                        lastError = it
                        continue
                    }
                    val blockDecision = ContentBlockerEngine.evaluateProviderOrMediaUrl(safeUrl)
                    if (blockDecision.blocked) {
                        ContentBlockerEngine.recordBlocked(blockDecision)
                        lastError = SecurityException(
                            "Blocked unsafe manifest source (${blockDecision.category?.label ?: "Shield"})"
                        )
                        continue
                    }

                    val responseResult = runCatching {
                        val visited = HashSet<String>()
                        fetchRepoOrManifestRecursive(
                            sourceUrl = safeUrl,
                            depth = 0,
                            visitedUrls = visited,
                            inheritedRepoCategory = null
                        )
                    }
                    if (responseResult.isSuccess) {
                        val parsed = responseResult.getOrThrow()
                        if (parsed.isNotEmpty()) {
                            return@runCatching parsed.distinctBy { "${it.name.lowercase()}::${it.baseUrl.lowercase()}" }
                        }
                    } else {
                        lastError = responseResult.exceptionOrNull()
                    }
                }

                // GitHub API deep repo & Vega-app config discovery fallback when direct raw guesses miss
                val githubSlug = extractGitHubSlug(rawInput)
                if (githubSlug != null) {
                    val discoveredUrls = discoverGitHubRepoSourcesViaApi(githubSlug)
                    for (url in discoveredUrls) {
                        val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(url).getOrNull() ?: continue
                        val blockDecision = ContentBlockerEngine.evaluateProviderOrMediaUrl(safeUrl)
                        if (blockDecision.blocked) {
                            ContentBlockerEngine.recordBlocked(blockDecision)
                            continue
                        }
                        val apiRes = runCatching {
                            fetchRepoOrManifestRecursive(
                                sourceUrl = safeUrl,
                                depth = 0,
                                visitedUrls = HashSet(),
                                inheritedRepoCategory = if (githubSlug.contains("vega", ignoreCase = true)) "Vega" else null
                            )
                        }.getOrNull()
                        if (!apiRes.isNullOrEmpty()) {
                            return@runCatching apiRes.distinctBy { "${it.name.lowercase()}::${it.baseUrl.lowercase()}" }
                        }
                    }
                }

                // Direct website fallback when user enters a real streaming website URL (never GitHub/GitLab/Codeberg repo pages)
                val isSlug = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
                    .matches(trimmedInput.removePrefix("github:").trim())
                if (!isSlug && !trimmedInput.endsWith(".json", ignoreCase = true)) {
                    val safeSiteUrl = UrlSecurityValidator.validatePublicHttpsUrl(trimmedInput).getOrNull()
                    if (safeSiteUrl != null && !UrlSecurityValidator.isNonSiteArchiveOrScriptUrl(safeSiteUrl)) {
                        val host = runCatching { URI(safeSiteUrl).host?.lowercase().orEmpty() }.getOrDefault("")
                        val isCodeHost = host == "github.com" || host.endsWith(".github.com") ||
                            host == "raw.githubusercontent.com" ||
                            host == "gitlab.com" || host.endsWith(".gitlab.com") ||
                            host == "codeberg.org"
                        if (!isCodeHost) {
                            val decision = ContentBlockerEngine.evaluateProviderOrMediaUrl(safeSiteUrl)
                            if (decision.blocked) {
                                ContentBlockerEngine.recordBlocked(decision)
                                throw SecurityException("Blocked unsafe provider domain (${decision.category?.label ?: "Shield"})")
                            }
                            val displayName = host.removePrefix("www.").substringBefore('.').replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase() else it.toString()
                            }.ifBlank { "Cloud Provider" }
                            val normalizedBase = safeSiteUrl.trimEnd('/') + "/"
                            return@runCatching listOf(
                                ProviderExtensionEntity(
                                    id = UUID.nameUUIDFromBytes("direct::$normalizedBase".toByteArray()).toString(),
                                    name = displayName,
                                    version = "1.0.0",
                                    description = "Direct cloud streaming provider ($host)",
                                    baseUrl = normalizedBase,
                                    searchUrlTemplate = "${normalizedBase.trimEnd('/')}/?s={query}",
                                    manifestUrl = null,
                                    category = "Cloud",
                                    isEnabled = true,
                                    isActive = false
                                )
                            )
                        }
                    }
                }

                throw lastError ?: IllegalArgumentException("No valid HTTPS providers found in repo or manifest")
            }
        }

    private suspend fun fetchRepoOrManifestRecursive(
        sourceUrl: String,
        depth: Int,
        visitedUrls: MutableSet<String>,
        inheritedRepoCategory: String?
    ): List<ProviderExtensionEntity> = coroutineScope {
        if (depth > MAX_REPO_RECURSION_DEPTH) return@coroutineScope emptyList()
        synchronized(visitedUrls) {
            if (!visitedUrls.add(sourceUrl)) return@coroutineScope emptyList()
        }

        val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(sourceUrl, upgradeHttpToHttps = false).getOrThrow()
        val blockDecision = ContentBlockerEngine.evaluateProviderOrMediaUrl(safeUrl)
        if (blockDecision.blocked) {
            ContentBlockerEngine.recordBlocked(blockDecision)
            throw SecurityException("Blocked unsafe repo source (${blockDecision.category?.label ?: "Shield"})")
        }

        val body = fetchJsonText(safeUrl)
        if (!body.startsWith("[") && !body.startsWith("{")) {
            error("Not a JSON manifest or repo at $safeUrl")
        }

        val companionBaseUrls = fetchCompanionBaseUrlMap(safeUrl)

        if (body.startsWith("[")) {
            val arr = JSONArray(body)
            val urlStrings = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val str = arr.optString(i).takeIf {
                    it.isNotBlank() && !it.trimStart().startsWith("{") &&
                        (it.contains("://") || it.contains(".json", true) || Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(it.trim()))
                }
                if (str != null) urlStrings.add(str)
            }
            if (urlStrings.isNotEmpty() && arr.optJSONObject(0) == null) {
                val repoCandidates = resolveNestedUrlsList(urlStrings, safeUrl)
                val subResults = repoCandidates.take(MAX_SUBLISTS_PER_FETCH).map { childUrl ->
                    async(Dispatchers.IO) {
                        runCatching {
                            fetchRepoOrManifestRecursive(
                                sourceUrl = childUrl,
                                depth = depth + 1,
                                visitedUrls = visitedUrls,
                                inheritedRepoCategory = inheritedRepoCategory ?: "MegaRepo"
                            )
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
                if (subResults.isNotEmpty()) {
                    return@coroutineScope enrichMissingProviderUrls(subResults, safeUrl)
                }
            }
        }

        if (body.startsWith("{")) {
            val obj = JSONObject(body)
            val subUrls = extractNestedRepoOrPluginUrls(obj, safeUrl)
            if (subUrls.isNotEmpty()) {
                val repoCategory = when {
                    obj.has("repositories") || obj.has("repos") || obj.has("mirrors") || obj.has("subRepos") || subUrls.size > 1 -> "MegaRepo"
                    obj.has("pluginLists") || obj.has("manifestVersion") -> "CloudStream"
                    else -> inheritedRepoCategory ?: "MegaRepo"
                }

                val subResults = subUrls.take(MAX_SUBLISTS_PER_FETCH).map { childUrl ->
                    async(Dispatchers.IO) {
                        val directRes = runCatching {
                            fetchRepoOrManifestRecursive(
                                sourceUrl = childUrl,
                                depth = depth + 1,
                                visitedUrls = visitedUrls,
                                inheritedRepoCategory = repoCategory
                            )
                        }
                        if (directRes.isSuccess && directRes.getOrThrow().isNotEmpty()) {
                            return@async directRes.getOrThrow()
                        }
                        // Fallback: if nested GitHub main/repo.json wasn't present, probe manifest.json / builds/repo.json / plugins.json
                        val rawPrefix = "https://raw.githubusercontent.com/"
                        if (childUrl.startsWith(rawPrefix) && childUrl.endsWith("/main/repo.json")) {
                            val slug = childUrl.removePrefix(rawPrefix).removeSuffix("/main/repo.json")
                            for (alt in buildGitHubRepoCandidates(slug)) {
                                if (alt == childUrl) continue
                                val altRes = runCatching {
                                    fetchRepoOrManifestRecursive(
                                        sourceUrl = alt,
                                        depth = depth + 1,
                                        visitedUrls = visitedUrls,
                                        inheritedRepoCategory = repoCategory
                                    )
                                }.getOrNull()
                                if (!altRes.isNullOrEmpty()) return@async altRes
                            }
                        }
                        emptyList()
                    }
                }.awaitAll().flatten()

                // Also parse any inline plugins/providers declared alongside pluginLists
                val inlineItems = parseManifestJson(
                    jsonText = body,
                    sourceManifestUrl = safeUrl,
                    companionBaseUrls = companionBaseUrls,
                    overrideCategory = repoCategory
                )

                val combined = (inlineItems + subResults)
                if (combined.isNotEmpty()) {
                    return@coroutineScope enrichMissingProviderUrls(combined, safeUrl)
                }
            }
        }

        val parsed = parseManifestJson(
            jsonText = body,
            sourceManifestUrl = safeUrl,
            companionBaseUrls = companionBaseUrls,
            overrideCategory = inheritedRepoCategory
        )
        enrichMissingProviderUrls(parsed, safeUrl)
    }

    private fun fetchJsonText(safeUrl: String): String {
        val req = Request.Builder()
            .url(safeUrl)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", "NavulaAndroid/1.0")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code} from $safeUrl")
            }
            resp.body?.string().orEmpty().trim()
        }
    }

    internal fun extractNestedRepoOrPluginUrls(obj: JSONObject, parentUrl: String): List<String> {
        val keys = listOf(
            "pluginLists", "repositories", "repos", "manifests", "lists",
            "mirrors", "includes", "imports", "subRepos", "feeds"
        )
        val rawCollected = mutableListOf<String>()
        for (key in keys) {
            val arr = obj.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val strItem = arr.optString(i).takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") }
                val rawUrl = strItem ?: arr.optJSONObject(i)?.let { childObj ->
                    childObj.optString("url")
                        .ifBlank { childObj.optString("manifestUrl") }
                        .ifBlank { childObj.optString("repoUrl") }
                        .ifBlank { childObj.optString("pluginList") }
                        .ifBlank { childObj.optString("transportUrl") }
                }.orEmpty()
                if (rawUrl.isNotBlank()) {
                    rawCollected.add(rawUrl)
                }
            }
        }
        return resolveNestedUrlsList(rawCollected, parentUrl)
    }

    private fun resolveNestedUrlsList(rawInputs: List<String>, parentUrl: String): List<String> {
        val collected = mutableListOf<String>()
        for (rawUrl in rawInputs) {
            val normalizedInput = normalizeRepoSchemePrefix(rawUrl)
            val resolved = when {
                normalizedInput.startsWith("http://") || normalizedInput.startsWith("https://") -> {
                    if ((normalizedInput.contains("github.com/", true) ||
                            normalizedInput.contains("gitlab.com/", true) ||
                            normalizedInput.contains("codeberg.org/", true)) &&
                        !normalizedInput.endsWith(".json", true)
                    ) {
                        resolveCandidateManifestUrls(normalizedInput).firstOrNull() ?: normalizedInput
                    } else {
                        normalizedInput
                    }
                }
                Regex("^[A-Za-z0-9-]+/[A-Za-z0-9_.-]+$").matches(normalizedInput) &&
                    !normalizedInput.endsWith(".json", ignoreCase = true) -> {
                    "https://raw.githubusercontent.com/$normalizedInput/main/repo.json"
                }
                else -> {
                    runCatching { URI(parentUrl).resolve(normalizedInput).toString() }.getOrDefault(normalizedInput)
                }
            }
            UrlSecurityValidator.validatePublicHttpsUrl(resolved).getOrNull()?.let { safeChild ->
                val shield = ContentBlockerEngine.evaluateProviderOrMediaUrl(safeChild)
                if (!shield.blocked) {
                    collected.add(safeChild)
                } else {
                    ContentBlockerEngine.recordBlocked(shield)
                }
            }
        }
        return collected.distinct()
    }

    suspend fun checkProviderHealth(rawUrl: String): ProviderHealthResult = withContext(Dispatchers.IO) {
        val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(rawUrl).getOrElse { err ->
            return@withContext ProviderHealthResult(
                isReachable = false,
                message = err.localizedMessage ?: "Blocked insecure URL"
            )
        }
        val shieldDecision = ContentBlockerEngine.evaluateProviderOrMediaUrl(safeUrl)
        if (shieldDecision.blocked) {
            ContentBlockerEngine.recordBlocked(shieldDecision)
            return@withContext ProviderHealthResult(
                isReachable = false,
                message = "Blocked (${shieldDecision.category?.label ?: "Shield"})"
            )
        }
        val startMs = System.currentTimeMillis()
        runCatching {
            val req = Request.Builder()
                .url(safeUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(req).execute().use { resp ->
                val latency = System.currentTimeMillis() - startMs
                val finalUrl = resp.request.url.toString()
                val safeFinal = UrlSecurityValidator.validatePublicHttpsUrl(finalUrl, upgradeHttpToHttps = false).getOrNull() ?: safeUrl
                val reachable = resp.code in 200..403
                val statusLabel = when {
                    resp.code in 200..299 -> "Online (${latency}ms)"
                    resp.code == 403 -> "Protected (${latency}ms)"
                    else -> "HTTP ${resp.code}"
                }
                ProviderHealthResult(
                    isReachable = reachable,
                    statusCode = resp.code,
                    latencyMs = latency,
                    resolvedUrl = safeFinal,
                    message = statusLabel
                )
            }
        }.getOrElse { err ->
            ProviderHealthResult(
                isReachable = false,
                latencyMs = System.currentTimeMillis() - startMs,
                message = err.localizedMessage ?: "Connection failed"
            )
        }
    }

    private fun normalizeRepoSchemePrefix(rawInput: String): String {
        val trimmed = rawInput.trim()
        val lower = trimmed.lowercase()
        val strippedScheme = when {
            lower.startsWith("http://") || lower.startsWith("https://") -> trimmed
            lower.startsWith("github:") -> trimmed.substring("github:".length)
            trimmed.contains("://") -> {
                val scheme = trimmed.substringBefore("://").lowercase()
                if (scheme in setOf("javascript", "file", "data", "content", "vbscript", "about", "blob")) {
                    trimmed
                } else {
                    trimmed.substringAfter("://")
                }
            }
            else -> trimmed
        }.trim()
        return strippedScheme.removeSuffix(".git").trim()
    }

    fun resolveCandidateManifestUrls(input: String): List<String> {
        val cleaned = normalizeRepoSchemePrefix(input)
        val githubSlugRegex = Regex("^[A-Za-z0-9-]+/[A-Za-z0-9_.-]+$")
        if (githubSlugRegex.matches(cleaned) && !cleaned.endsWith(".json", ignoreCase = true)) {
            return buildGitHubRepoCandidates(cleaned)
        }
        if (cleaned.startsWith("https://github.com/", ignoreCase = true) ||
            cleaned.startsWith("http://github.com/", ignoreCase = true)
        ) {
            val httpsCleaned = if (cleaned.startsWith("http://", true)) "https://" + cleaned.substring(7) else cleaned
            if (httpsCleaned.contains("/blob/")) {
                val rawUrl = httpsCleaned
                    .replace("https://github.com/", "https://raw.githubusercontent.com/")
                    .replace("/blob/", "/")
                return listOf(rawUrl)
            }
            if (httpsCleaned.contains("/tree/")) {
                val afterTree = httpsCleaned.substringAfter("/tree/")
                val slug = httpsCleaned.removePrefix("https://github.com/").substringBefore("/tree/").trimEnd('/')
                val branch = afterTree.substringBefore('/').ifBlank { "main" }
                val subPath = afterTree.substringAfter('/', "").trim('/')
                val baseRaw = if (subPath.isBlank()) {
                    "https://raw.githubusercontent.com/$slug/$branch"
                } else {
                    "https://raw.githubusercontent.com/$slug/$branch/$subPath"
                }
                return listOf(
                    "$baseRaw/manifest.json",
                    "$baseRaw/repo.json",
                    "$baseRaw/plugins.json",
                    "$baseRaw/index.min.json",
                    "$baseRaw/index.json",
                    "$baseRaw/extensions.json",
                    "$baseRaw/providers.json"
                )
            }
            val parts = httpsCleaned.removePrefix("https://github.com/").trimEnd('/').split("/")
            if (parts.size >= 2) {
                val slug = "${parts[0]}/${parts[1].removeSuffix(".git")}"
                return buildGitHubRepoCandidates(slug)
            }
        }
        if (cleaned.startsWith("https://gitlab.com/", ignoreCase = true) ||
            cleaned.startsWith("http://gitlab.com/", ignoreCase = true)
        ) {
            val httpsCleaned = if (cleaned.startsWith("http://", true)) "https://" + cleaned.substring(7) else cleaned
            if (httpsCleaned.contains("/-/blob/")) {
                return listOf(httpsCleaned.replace("/-/blob/", "/-/raw/"))
            }
            val parts = httpsCleaned.removePrefix("https://gitlab.com/").trimEnd('/').split("/")
            if (parts.size >= 2 && !httpsCleaned.endsWith(".json", true)) {
                val slug = "${parts[0]}/${parts[1].removeSuffix(".git")}"
                return listOf(
                    "https://gitlab.com/$slug/-/raw/main/manifest.json",
                    "https://gitlab.com/$slug/-/raw/main/repo.json",
                    "https://gitlab.com/$slug/-/raw/main/plugins.json",
                    "https://gitlab.com/$slug/-/raw/main/index.min.json",
                    "https://gitlab.com/$slug/-/raw/master/manifest.json",
                    "https://gitlab.com/$slug/-/raw/master/repo.json"
                )
            }
        }
        if (cleaned.startsWith("https://codeberg.org/", ignoreCase = true) && !cleaned.endsWith(".json", true)) {
            val parts = cleaned.removePrefix("https://codeberg.org/").trimEnd('/').split("/")
            if (parts.size >= 2) {
                val slug = "${parts[0]}/${parts[1].removeSuffix(".git")}"
                return listOf(
                    "https://codeberg.org/$slug/raw/branch/main/repo.json",
                    "https://codeberg.org/$slug/raw/branch/builds/repo.json",
                    "https://codeberg.org/$slug/raw/branch/main/plugins.json",
                    "https://codeberg.org/$slug/raw/branch/main/manifest.json",
                    "https://codeberg.org/$slug/raw/branch/main/index.min.json",
                    "https://codeberg.org/$slug/raw/branch/master/repo.json",
                    "https://codeberg.org/$slug/raw/branch/master/manifest.json"
                )
            }
        }

        val normalized = UrlSecurityValidator.validatePublicHttpsUrl(cleaned).getOrElse {
            if (cleaned.startsWith("https://")) cleaned else "https://$cleaned"
        }
        return if (normalized.endsWith(".json", ignoreCase = true) || normalized.contains(".json?", ignoreCase = true)) {
            listOf(normalized)
        } else {
            val base = normalized.trimEnd('/')
            listOf(
                "$base/repo.json",
                "$base/plugins.json",
                "$base/manifest.json",
                "$base/index.min.json",
                "$base/index.json",
                "$base/extensions.json",
                "$base/providers.json",
                "$base/addons.json",
                "$base/list.json",
                "$base/mega.json",
                normalized
            )
        }
    }

    private fun buildGitHubRepoCandidates(slug: String): List<String> = listOf(
        "https://raw.githubusercontent.com/$slug/main/manifest.json",
        "https://raw.githubusercontent.com/$slug/main/repo.json",
        "https://raw.githubusercontent.com/$slug/builds/repo.json",
        "https://raw.githubusercontent.com/$slug/builds/plugins.json",
        "https://raw.githubusercontent.com/$slug/main/plugins.json",
        "https://raw.githubusercontent.com/$slug/main/index.min.json",
        "https://raw.githubusercontent.com/$slug/repo/index.min.json",
        "https://raw.githubusercontent.com/$slug/main/index.json",
        "https://raw.githubusercontent.com/$slug/main/extensions.json",
        "https://raw.githubusercontent.com/$slug/main/providers.json",
        "https://raw.githubusercontent.com/$slug/main/addons.json",
        "https://raw.githubusercontent.com/$slug/main/list.json",
        "https://raw.githubusercontent.com/$slug/main/providers/manifest.json",
        "https://raw.githubusercontent.com/$slug/main/dist/manifest.json",
        "https://raw.githubusercontent.com/$slug/master/manifest.json",
        "https://raw.githubusercontent.com/$slug/master/repo.json",
        "https://raw.githubusercontent.com/$slug/master/plugins.json",
        "https://raw.githubusercontent.com/$slug/master/index.min.json",
        "https://cdn.jsdelivr.net/gh/$slug@main/manifest.json",
        "https://cdn.jsdelivr.net/gh/$slug@main/repo.json",
        "https://cdn.jsdelivr.net/gh/$slug@builds/repo.json",
        "https://cdn.jsdelivr.net/gh/$slug@repo/index.min.json",
        "https://cdn.jsdelivr.net/gh/$slug@master/manifest.json"
    )

    private suspend fun fetchCompanionBaseUrlMap(manifestUrl: String): Map<String, String> = coroutineScope {
        val baseDir = manifestUrl.substringBeforeLast('/', "")
        if (baseDir.isBlank()) return@coroutineScope emptyMap()

        val directJsonCandidates = listOf(
            "$baseDir/baseurl.json",
            "$baseDir/baseUrl.json",
            "$baseDir/baseUrls.json",
            "$baseDir/domains.json",
            "$baseDir/urls.json",
            "$baseDir/modflix.json",
            "$baseDir/src/baseurl.json",
            "$baseDir/dist/baseurl.json"
        )

        val isVegaManifest = manifestUrl.contains("vega", ignoreCase = true) ||
            manifestUrl.endsWith("/manifest.json", ignoreCase = true)
        val vegaGlobalMapFallback = if (isVegaManifest) {
            listOf("https://himanshu8443.github.io/providers/modflix.json")
        } else emptyList()

        val discoveredRemoteMaps = discoverRemoteDomainMapUrls(baseDir)
        val allCandidates = (directJsonCandidates + discoveredRemoteMaps + vegaGlobalMapFallback).distinct()

        val resolvedMap = mutableMapOf<String, String>()
        val fetchedMaps = allCandidates.map { candidateUrl ->
            async(Dispatchers.IO) {
                fetchSingleDomainMap(candidateUrl)
            }
        }.awaitAll()

        for (partial in fetchedMaps) {
            resolvedMap.putAll(partial)
        }
        resolvedMap
    }

    private suspend fun discoverRemoteDomainMapUrls(baseDir: String): List<String> = coroutineScope {
        val helperFiles = listOf(
            "$baseDir/dist/getBaseUrl.js",
            "$baseDir/providers/getBaseUrl.js",
            "$baseDir/src/lib/getBaseUrl.ts",
            "$baseDir/src/getBaseUrl.ts",
            "$baseDir/utils/getBaseUrl.js",
            "$baseDir/getBaseUrl.js"
        )
        val httpsUrlRegex = Regex("""https://[A-Za-z0-9.-]+(?:/[A-Za-z0-9_./?=&%-]*)?""")
        helperFiles.map { fileUrl ->
            async(Dispatchers.IO) {
                if (!UrlSecurityValidator.isSafePublicHttpsUrl(fileUrl)) return@async emptyList()
                runCatching {
                    val req = Request.Builder().url(fileUrl).header("User-Agent", "NavulaAndroid/1.0").build()
                    fastProbeClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use emptyList()
                        val text = resp.body?.string()?.take(24_576).orEmpty()
                        httpsUrlRegex.findAll(text)
                            .map { it.value.trimEnd('.', ',', ';', ')', '"', '\'') }
                            .filter { u ->
                                !u.contains("github.com") &&
                                    !u.contains("w3.org") &&
                                    !u.contains("mozilla.org") &&
                                    (u.endsWith(".json", true) || u.contains("modflix", true) ||
                                        u.contains("baseurl", true) || u.contains("pastebin", true) ||
                                        u.contains("workers.dev", true) || u.contains("vercel.app", true))
                            }
                            .toList()
                    }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().distinct()
    }

    private fun fetchSingleDomainMap(jsonUrl: String): Map<String, String> {
        if (!UrlSecurityValidator.isSafePublicHttpsUrl(jsonUrl)) return emptyMap()
        val result = mutableMapOf<String, String>()
        runCatching {
            val req = Request.Builder()
                .url(jsonUrl)
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "NavulaAndroid/1.0")
                .build()
            fastProbeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val text = resp.body?.string().orEmpty().trim()
                if (!text.startsWith("{")) return@use
                val json = JSONObject(text)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = json.optJSONObject(key)
                    val rawValue = if (obj != null) {
                        obj.optString("url")
                            .ifBlank { obj.optString("baseUrl") }
                            .ifBlank { obj.optString("mainUrl") }
                            .ifBlank { obj.optString("domain") }
                            .ifBlank { obj.optString("host") }
                    } else {
                        json.optString(key)
                    }
                    UrlSecurityValidator.validatePublicHttpsUrl(rawValue).getOrNull()?.let { safe ->
                        val normalized = safe.trimEnd('/') + "/"
                        result[key.lowercase()] = normalized
                        result[normalizeKey(key)] = normalized
                    }
                }
            }
        }
        return result
    }

    /**
     * Concurrently inspects static text files in Vega-style provider repos using regex
     * strictly for literal https:// base URL strings. No external script or binary is ever compiled or executed.
     */
    private suspend fun enrichMissingProviderUrls(
        providers: List<ProviderExtensionEntity>,
        manifestUrl: String
    ): List<ProviderExtensionEntity> = coroutineScope {
        val baseDir = manifestUrl.substringBeforeLast('/', "")
        if (baseDir.isBlank()) return@coroutineScope providers

        val missingCount = providers.count { it.baseUrl.isBlank() }
        if (missingCount == 0) return@coroutineScope providers

        providers.mapIndexed { index, provider ->
            async(Dispatchers.IO) {
                if (provider.baseUrl.isNotBlank() || index >= 40 || provider.category == "CloudStream" || provider.category == "MegaRepo") {
                    return@async provider
                }

                val slug = provider.description
                    .substringAfter("module:", "")
                    .substringBefore(")")
                    .trim()
                    .ifBlank { provider.name.lowercase().replace(" ", "") }

                val textCandidates = listOf(
                    "$baseDir/dist/$slug/posts.js",
                    "$baseDir/dist/$slug/meta.js",
                    "$baseDir/providers/$slug/posts.js",
                    "$baseDir/providers/$slug/meta.js",
                    "$baseDir/providers/$slug/posts.ts",
                    "$baseDir/src/$slug/posts.ts",
                    "$baseDir/src/$slug/meta.ts"
                )

                var extractedBase: String? = null
                for (candidate in textCandidates) {
                    if (!UrlSecurityValidator.isSafePublicHttpsUrl(candidate)) continue
                    extractedBase = runCatching {
                        val req = Request.Builder()
                            .url(candidate)
                            .header("User-Agent", "NavulaAndroid/1.0")
                            .build()
                        fastProbeClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val snippet = resp.body?.string()?.take(16_384).orEmpty()
                            extractHttpsBaseUrlFromText(snippet)
                        }
                    }.getOrNull()
                    if (!extractedBase.isNullOrBlank()) break
                }

                if (!extractedBase.isNullOrBlank()) {
                    val cleanBase = extractedBase.trimEnd('/') + "/"
                    provider.copy(
                        baseUrl = cleanBase,
                        searchUrlTemplate = "${cleanBase.trimEnd('/')}/?s={query}",
                        description = provider.description.replace("Needs Mirror URL", "Cloud streaming module")
                    )
                } else {
                    provider
                }
            }
        }.awaitAll()
    }

    private fun extractHttpsBaseUrlFromText(text: String): String? {
        val basePattern = Regex("""(?:baseUrl|mainUrl|BASE_URL|url)\s*[:=]\s*['"](https://[A-Za-z0-9.-]+(?:/[^'"]*)?)['"]""")
        val firstMatch = basePattern.find(text)?.groupValues?.getOrNull(1)
        if (firstMatch != null) {
            return UrlSecurityValidator.validatePublicHttpsUrl(firstMatch).getOrNull()
        }
        val genericHttps = Regex("""['"](https://[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?:/[^'"\s]*)?)['"]""")
        for (match in genericHttps.findAll(text)) {
            val url = match.groupValues[1]
            val lower = url.lowercase()
            if (lower.contains("github.com") ||
                lower.contains("githubusercontent.com") ||
                lower.contains("themoviedb.org") ||
                lower.contains("imdb.com")
            ) {
                continue
            }
            val safe = UrlSecurityValidator.validatePublicHttpsUrl(url).getOrNull()
            if (safe != null) return safe
        }
        return null
    }

    private fun normalizeKey(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun parseManifestJson(
        jsonText: String,
        sourceManifestUrl: String,
        companionBaseUrls: Map<String, String> = emptyMap(),
        overrideCategory: String? = null
    ): List<ProviderExtensionEntity> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val items = mutableListOf<JSONObject>()
        var containerLooksLikeRepoOnly = false
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                // Aniyomi / Tachiyomi index.min.json nests playable site entries under "sources"
                val nestedSources = entry.optJSONArray("sources")
                if (nestedSources != null && nestedSources.length() > 0 && entry.has("pkg")) {
                    val parentVer = entry.optString("version").ifBlank { entry.optInt("code", 1).toString() }
                    val parentLang = entry.optString("lang")
                    val parentPkg = entry.optString("pkg")
                    for (s in 0 until nestedSources.length()) {
                        val srcObj = nestedSources.optJSONObject(s) ?: continue
                        val merged = JSONObject(srcObj.toString())
                        if (!merged.has("version") && parentVer.isNotBlank()) merged.put("version", parentVer)
                        if (!merged.has("language") && parentLang.isNotBlank()) merged.put("language", parentLang)
                        if (!merged.has("value") && parentPkg.isNotBlank()) merged.put("value", "$parentPkg:${srcObj.optString("id")}")
                        merged.put("__aniyomi", true)
                        items.add(merged)
                    }
                } else {
                    items.add(entry)
                }
            }
        } else if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            val isRootStremioAddon = obj.has("resources") ||
                (obj.has("catalogs") && (obj.has("id") || obj.has("name")))
            val arrayKeys = listOf(
                "providers", "extensions", "plugins", "items", "sources",
                "addons", "data", "list", "channels", "sites",
                "modules", "entries", "results", "records"
            )
            var matchedArray: JSONArray? = null
            if (!isRootStremioAddon) {
                for (k in arrayKeys) {
                    val candidate = obj.optJSONArray(k)
                    if (candidate != null && candidate.length() > 0 && candidate.optJSONObject(0) != null) {
                        matchedArray = candidate
                        break
                    }
                }
            }
            if (matchedArray != null) {
                for (i in 0 until matchedArray.length()) {
                    matchedArray.optJSONObject(i)?.let { items.add(it) }
                }
            } else if (
                !isRootStremioAddon &&
                (obj.has("pluginLists") || obj.has("repositories") || obj.has("repos") ||
                    obj.has("manifests") || obj.has("subRepos")) &&
                !obj.has("baseUrl") && !obj.has("mainUrl")
            ) {
                containerLooksLikeRepoOnly = true
            } else if (isRootStremioAddon || obj.has("name") || obj.has("display_name") || obj.has("title")) {
                items.add(obj)
            } else {
                // Root Object Map keyed by provider slug: {"siteA": {"url": "..."}, "siteB": "https://..."}
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val childObj = obj.optJSONObject(key)
                    if (childObj != null) {
                        val copy = JSONObject(childObj.toString())
                        if (!copy.has("name") && !copy.has("display_name")) copy.put("name", key)
                        if (!copy.has("value")) copy.put("value", key)
                        items.add(copy)
                    } else {
                        val strVal = obj.optString(key)
                        if (strVal.startsWith("http://") || strVal.startsWith("https://")) {
                            items.add(
                                JSONObject().apply {
                                    put("name", key)
                                    put("value", key)
                                    put("baseUrl", strVal)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (containerLooksLikeRepoOnly) return emptyList()

        return items.mapNotNull { obj ->
            val name = obj.optString("display_name")
                .ifBlank { obj.optString("name") }
                .ifBlank { obj.optString("title") }
                .ifBlank { obj.optString("internalName") }
                .ifBlank { obj.optString("label") }
                .ifBlank { obj.optString("value") }
                .ifBlank { obj.optString("id") }
                .trim()
            if (name.isBlank()) return@mapNotNull null

            val internalName = obj.optString("internalName").trim()
            val rawValue = obj.optString("value").trim()
                .ifBlank { internalName }
                .ifBlank { obj.optString("id").trim() }
            val isStremioAddon = obj.has("resources") || (obj.has("catalogs") && obj.has("id"))
            val isAniyomiShape = obj.optBoolean("__aniyomi", false) || obj.has("pkg") ||
                sourceManifestUrl.contains("index.min.json", ignoreCase = true)
            val isCloudStreamShape = obj.has("apiVersion") ||
                obj.has("tvTypes") ||
                obj.has("internalName") ||
                sourceManifestUrl.contains("plugins.json", ignoreCase = true) ||
                sourceManifestUrl.contains("repo.json", ignoreCase = true)

            // CloudStream plugins.json often puts `.cs3` binary archive inside "url".
            // Strictly ignore .cs3/.apk/.zip/.jar/.dex/script/GitHub release URLs for site baseUrl (DCL safe).
            val candidateUrlField = obj.optString("url").trim().takeIf { candidate ->
                candidate.isNotBlank() &&
                    !UrlSecurityValidator.isNonSiteArchiveOrScriptUrl(candidate) &&
                    !candidate.contains("raw.githubusercontent.com", ignoreCase = true) &&
                    !candidate.contains("github.com/", ignoreCase = true)
            }.orEmpty()

            val arrayUrlCandidate = extractFirstUrlFromArray(obj.optJSONArray("baseUrls"))
                .ifBlank { extractFirstUrlFromArray(obj.optJSONArray("mirrors")) }
                .ifBlank { extractFirstUrlFromArray(obj.optJSONArray("domains")) }

            val stremioOriginFallback = if (isStremioAddon && sourceManifestUrl.startsWith("https://")) {
                runCatching {
                    val uri = URI(sourceManifestUrl)
                    if (!uri.host.isNullOrBlank()) "${uri.scheme}://${uri.host}/" else ""
                }.getOrDefault("")
            } else ""

            val rawUrl = obj.optString("baseUrl")
                .ifBlank { obj.optString("mainUrl") }
                .ifBlank { obj.optString("homeUrl") }
                .ifBlank { obj.optString("siteUrl") }
                .ifBlank { obj.optString("webUrl") }
                .ifBlank { obj.optString("sourceUrl") }
                .ifBlank { obj.optString("website") }
                .ifBlank { obj.optString("webSite") }
                .ifBlank { obj.optString("host") }
                .ifBlank { obj.optString("domain") }
                .ifBlank { obj.optString("site") }
                .ifBlank { obj.optString("home") }
                .ifBlank { obj.optString("homepage") }
                .ifBlank { obj.optString("endpoint") }
                .ifBlank { obj.optString("server") }
                .ifBlank { obj.optString("mirror") }
                .ifBlank { obj.optString("apiUrl") }
                .ifBlank { obj.optString("transportUrl") }
                .ifBlank { obj.optString("streamUrl") }
                .ifBlank { obj.optString("watchUrl") }
                .ifBlank { obj.optString("href") }
                .ifBlank { obj.optString("link") }
                .ifBlank { obj.optString("base") }
                .ifBlank { arrayUrlCandidate }
                .ifBlank { candidateUrlField }
                .trim()
                .ifBlank { companionBaseUrls[rawValue.lowercase()].orEmpty() }
                .ifBlank { companionBaseUrls[normalizeKey(rawValue)].orEmpty() }
                .ifBlank { companionBaseUrls[name.lowercase()].orEmpty() }
                .ifBlank { companionBaseUrls[normalizeKey(name)].orEmpty() }
                .ifBlank { stremioOriginFallback }

            val resolvedBaseUrl = if (rawUrl.isNotBlank() && !UrlSecurityValidator.isNonSiteArchiveOrScriptUrl(rawUrl)) {
                UrlSecurityValidator.validatePublicHttpsUrl(rawUrl).getOrNull()?.let { safeBase ->
                    val normalized = safeBase.trimEnd('/') + "/"
                    val decision = ContentBlockerEngine.evaluateProviderOrMediaUrl(normalized)
                    if (decision.blocked) {
                        ContentBlockerEngine.recordBlocked(decision)
                        ""
                    } else {
                        normalized
                    }
                }.orEmpty()
            } else {
                ""
            }

            val searchTemplate = obj.optString("searchUrl")
                .ifBlank { obj.optString("searchTemplate") }
                .ifBlank { obj.optString("searchPath") }
                .let { raw ->
                    when {
                        resolvedBaseUrl.isBlank() -> ""
                        raw.isBlank() -> "${resolvedBaseUrl.trimEnd('/')}/?s={query}"
                        raw.startsWith("http://") || raw.startsWith("https://") ->
                            UrlSecurityValidator.validatePublicHttpsUrl(raw).getOrDefault("")
                        else -> "${resolvedBaseUrl.trimEnd('/')}/${raw.trimStart('/')}"
                    }
                }

            val version = obj.opt("version")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: "1.0.0"
            val typeLabel = obj.optString("type").ifBlank { obj.optString("language") }.ifBlank { obj.optString("lang") }
            val tvTypesArr = obj.optJSONArray("tvTypes") ?: obj.optJSONArray("categories") ?: obj.optJSONArray("types")
            val tvTypesSummary = if (tvTypesArr != null && tvTypesArr.length() > 0) {
                (0 until minOf(tvTypesArr.length(), 3))
                    .mapNotNull { tvTypesArr.optString(it).takeIf { s -> s.isNotBlank() } }
                    .joinToString("/")
            } else ""

            val categoryLabel = overrideCategory ?: when {
                isStremioAddon -> "Stremio"
                isAniyomiShape -> "Aniyomi"
                isCloudStreamShape -> "CloudStream"
                obj.has("display_name") || obj.has("value") -> "Vega"
                else -> "Manifest"
            }

            val desc = obj.optString("description")
                .ifBlank {
                    buildString {
                        if (tvTypesSummary.isNotBlank()) append(tvTypesSummary)
                        if (typeLabel.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(typeLabel)
                        }
                        if (resolvedBaseUrl.isBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append("Needs Mirror URL (module: ${rawValue.ifBlank { name }})")
                        } else if (rawValue.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append("$categoryLabel module ($rawValue)")
                        }
                    }
                }

            val statusInt = obj.optInt("status", 1)
            val disabled = obj.optBoolean("disabled", false) || statusInt == 0
            val idSeed = "$sourceManifestUrl::${rawValue.ifBlank { name }}"
            ProviderExtensionEntity(
                id = UUID.nameUUIDFromBytes(idSeed.toByteArray()).toString(),
                name = name,
                version = version,
                description = desc.ifBlank { "$categoryLabel provider extension" },
                baseUrl = resolvedBaseUrl,
                searchUrlTemplate = searchTemplate,
                manifestUrl = sourceManifestUrl,
                category = categoryLabel,
                isEnabled = !disabled,
                isActive = false
            )
        }
    }

    private fun extractFirstUrlFromArray(arr: JSONArray?): String {
        if (arr == null) return ""
        for (i in 0 until arr.length()) {
            val candidate = arr.optString(i).trim()
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                return candidate
            }
        }
        return ""
    }

    internal fun extractGitHubSlug(rawInput: String): String? {
        val cleaned = normalizeRepoSchemePrefix(rawInput)
        val githubSlugRegex = Regex("^[A-Za-z0-9-]+/[A-Za-z0-9_.-]+$")
        if (githubSlugRegex.matches(cleaned) && !cleaned.endsWith(".json", ignoreCase = true)) {
            return cleaned
        }
        if (cleaned.startsWith("https://github.com/", ignoreCase = true) ||
            cleaned.startsWith("http://github.com/", ignoreCase = true)
        ) {
            val parts = cleaned
                .replace("http://github.com/", "https://github.com/", ignoreCase = true)
                .removePrefix("https://github.com/")
                .trimEnd('/')
                .split("/")
            if (parts.size >= 2) {
                return "${parts[0]}/${parts[1].removeSuffix(".git")}"
            }
        }
        return null
    }

    /**
     * Queries GitHub REST API for the repo's default branch and file tree so ANY GitHub repo—
     * including app source repositories like `vega-org/vega-app` or non-standard branch/folder
     * layouts—can automatically locate provider manifests or referenced Vega/CloudStream repos.
     */
    private fun discoverGitHubRepoSourcesViaApi(slug: String): List<String> {
        val discovered = LinkedHashSet<String>()
        runCatching {
            val repoReq = Request.Builder()
                .url("https://api.github.com/repos/$slug")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NavulaAndroid/1.0")
                .build()
            var defaultBranch = "main"
            fastProbeClient.newCall(repoReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    defaultBranch = json.optString("default_branch").ifBlank { "main" }
                }
            }

            val treeReq = Request.Builder()
                .url("https://api.github.com/repos/$slug/git/trees/$defaultBranch?recursive=1")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NavulaAndroid/1.0")
                .build()
            val configTextPaths = mutableListOf<String>()
            fastProbeClient.newCall(treeReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val treeArr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("tree")
                    if (treeArr != null) {
                        val manifestNameRegex = Regex(
                            """(^|/)(manifest|repo|plugins|providers|extensions|addons|index\.min|mega)\.json$""",
                            RegexOption.IGNORE_CASE
                        )
                        val configInspectRegex = Regex(
                            """(manifest|provider|extension|repo|source|constant|config)""",
                            RegexOption.IGNORE_CASE
                        )
                        for (i in 0 until treeArr.length()) {
                            val item = treeArr.optJSONObject(i) ?: continue
                            if (item.optString("type") != "blob") continue
                            val path = item.optString("path").trimStart('/')
                            if (manifestNameRegex.containsMatchIn(path)) {
                                discovered.add("https://raw.githubusercontent.com/$slug/$defaultBranch/$path")
                            } else if ((path.endsWith(".ts", true) || path.endsWith(".tsx", true) ||
                                    path.endsWith(".js", true) || path.endsWith(".json", true)) &&
                                configInspectRegex.containsMatchIn(path) &&
                                configTextPaths.size < 8
                            ) {
                                configTextPaths.add(path)
                            }
                        }
                    }
                }
            }

            // If repo is a Vega-style app repository (such as vega-org/vega-app) with no direct manifest.json,
            // scan its config/source files for linked manifest URLs or Vega provider repos
            if (discovered.isEmpty()) {
                val manifestUrlRegex = Regex(
                    """https://(?:raw\.githubusercontent\.com|cdn\.jsdelivr\.net/gh)/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/[^\s"'`]+?\.json"""
                )
                val vegaSlugRegex = Regex("""github\.com/([A-Za-z0-9-]+/[A-Za-z0-9_.-]*provider[A-Za-z0-9_.-]*)""", RegexOption.IGNORE_CASE)
                for (path in configTextPaths) {
                    val rawFileUrl = "https://raw.githubusercontent.com/$slug/$defaultBranch/$path"
                    if (!UrlSecurityValidator.isSafePublicHttpsUrl(rawFileUrl)) continue
                    runCatching {
                        val fileReq = Request.Builder().url(rawFileUrl).header("User-Agent", "NavulaAndroid/1.0").build()
                        fastProbeClient.newCall(fileReq).execute().use { fResp ->
                            if (fResp.isSuccessful) {
                                val content = fResp.body?.string()?.take(32_768).orEmpty()
                                manifestUrlRegex.findAll(content).forEach { m ->
                                    discovered.add(m.value)
                                }
                                vegaSlugRegex.findAll(content).forEach { m ->
                                    val refSlug = m.groupValues[1].removeSuffix(".git")
                                    if (!refSlug.equals(slug, ignoreCase = true)) {
                                        discovered.addAll(buildGitHubRepoCandidates(refSlug).take(6))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Vega app official repository hub resolution (vega-org/vega-app -> official Vega provider repos)
        if (discovered.isEmpty() && (slug.contains("vega-app", ignoreCase = true) || slug.startsWith("vega-org/", ignoreCase = true))) {
            discovered.addAll(buildGitHubRepoCandidates(RECOMMENDED_VEGA_REPO).take(6))
            discovered.addAll(buildGitHubRepoCandidates(RECOMMENDED_VEGA_ORG_TEMPLATE).take(4))
        }
        return discovered.toList()
    }
}
