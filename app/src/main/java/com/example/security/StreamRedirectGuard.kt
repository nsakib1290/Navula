package com.example.security

import java.net.URI

/**
 * Prevents streaming pages from hijacking the main WebView frame with cross-domain ad/popup
 * redirects or infinite redirect/bounce loops while preserving legitimate same-site navigation,
 * OAuth/login flows, and Cloudflare/human-verification challenges.
 */
object StreamRedirectGuard {

    private const val HOP_HISTORY_MAX = 8
    private const val LOOP_WINDOW_MS = 4_500L
    private const val RAPID_BOUNCE_WINDOW_MS = 4_000L

    private data class NavHop(
        val url: String,
        val host: String,
        val timestampMs: Long
    )

    private val recentHops = ArrayDeque<NavHop>(HOP_HISTORY_MAX)

    private val SECOND_LEVEL_COUNTRY_TLDS = setOf(
        "co.uk", "org.uk", "ac.uk", "co.in", "net.in", "org.in",
        "com.au", "net.au", "com.br", "com.mx", "co.jp", "co.kr",
        "com.tr", "com.sg", "com.pk", "com.bd"
    )

    private val ESSENTIAL_CHALLENGE_DOMAINS = setOf(
        "cloudflare.com",
        "challenges.cloudflare.com",
        "hcaptcha.com",
        "recaptcha.net",
        "gstatic.com",
        "turnstile.com"
    )

    private val AUTH_PATH_MARKERS = listOf(
        "/oauth", "/authorize", "/signin", "/sign-in", "/login", "/sso", "/auth/"
    )

    fun extractHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            URI(url.trim()).host?.lowercase()?.trimEnd('.')
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun registrableDomain(host: String?): String {
        val clean = host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotBlank() } ?: return ""
        val parts = clean.split('.').filter { it.isNotBlank() }
        if (parts.size <= 2) return clean
        val lastTwo = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        return if (lastTwo in SECOND_LEVEL_COUNTRY_TLDS && parts.size >= 3) {
            "${parts[parts.size - 3]}.$lastTwo"
        } else {
            lastTwo
        }
    }

    fun isSameRegistrableSite(hostA: String?, hostB: String?): Boolean {
        val regA = registrableDomain(hostA)
        val regB = registrableDomain(hostB)
        return regA.isNotBlank() && regA == regB
    }

    fun isChallengeHost(host: String?): Boolean {
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        return ESSENTIAL_CHALLENGE_DOMAINS.any { h == it || h.endsWith(".$it") }
    }

    fun isEssentialAuthOrChallengeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = extractHost(url)
        if (isChallengeHost(host)) return true
        val low = url.lowercase()
        if (low.contains("/cdn-cgi/")) return true
        return AUTH_PATH_MARKERS.any { low.contains(it) }
    }

    fun resetHopHistory() {
        synchronized(recentHops) {
            recentHops.clear()
        }
    }

    /**
     * Records a main-frame navigation attempt and detects:
     * 1) Tight URL loops (e.g. A -> B -> A -> B or >=3 rapid hits to the same redirect URL)
     * 2) Rapid multi-domain bounce chains (>=3 distinct cross-site hosts within 4s)
     */
    fun recordAndCheckRedirectLoop(
        targetUrl: String,
        timestampMs: Long = System.currentTimeMillis()
    ): Boolean {
        val host = extractHost(targetUrl) ?: return false
        if (isChallengeHost(host) || targetUrl.contains("/cdn-cgi/", ignoreCase = true)) {
            return false
        }
        val normalized = targetUrl.substringBefore('#').trim()
        synchronized(recentHops) {
            while (recentHops.size >= HOP_HISTORY_MAX) {
                recentHops.removeFirst()
            }

            // Detect repeating redirect loop on exact URL or back-and-forth A <-> B pattern
            val sameUrlRecentCount = recentHops.count {
                it.url == normalized && (timestampMs - it.timestampMs) <= LOOP_WINDOW_MS
            }
            if (sameUrlRecentCount >= 2) {
                val distinctHostsRecent = recentHops
                    .filter { (timestampMs - it.timestampMs) <= LOOP_WINDOW_MS }
                    .map { registrableDomain(it.host) }
                    .distinct()
                    .size
                // A <-> B cross-host ping-pong or >=3 rapid reloads in <4.5s
                if (distinctHostsRecent >= 2 || sameUrlRecentCount >= 3) {
                    return true
                }
            }

            // Detect rapid multi-hop bounce chain across >=3 different registrable domains
            val rapidCrossHosts = recentHops
                .filter { (timestampMs - it.timestampMs) <= RAPID_BOUNCE_WINDOW_MS }
                .map { registrableDomain(it.host) }
                .toMutableSet()
            rapidCrossHosts.add(registrableDomain(host))
            if (rapidCrossHosts.size >= 4) {
                return true
            }

            recentHops.addLast(NavHop(normalized, host, timestampMs))
            return false
        }
    }

    /**
     * Returns true if a WebView navigation should be blocked as an unwanted redirect.
     */
    fun shouldBlockRedirect(
        targetUrl: String,
        currentPageUrl: String?,
        activeProviderBaseUrl: String?,
        isMainFrame: Boolean,
        blockRedirectsEnabled: Boolean
    ): Boolean {
        if (!blockRedirectsEnabled || !isMainFrame) return false

        val targetHost = extractHost(targetUrl) ?: return false
        val targetReg = registrableDomain(targetHost)

        // Allow Cloudflare and human verification frames
        if (isChallengeHost(targetHost)) {
            return false
        }

        val pageHost = extractHost(currentPageUrl)
        val providerHost = extractHost(activeProviderBaseUrl)

        // If neither active provider nor current page has a host yet, allow initial load
        if (pageHost == null && providerHost == null) return false

        if (pageHost != null && isSameRegistrableSite(targetHost, pageHost)) {
            return false
        }
        if (providerHost != null && isSameRegistrableSite(targetHost, providerHost)) {
            return false
        }

        return targetReg.isNotBlank()
    }
}
