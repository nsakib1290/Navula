package com.example.security

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

object UrlSecurityValidator {

    private val BLOCKED_SCHEMES = setOf(
        "javascript", "data", "file", "content", "vbscript", "about", "intent", "blob"
    )

    private val BLOCKED_EXTENSIONS = listOf(
        ".apk", ".exe", ".msi", ".bat", ".sh", ".dex", ".jar", ".so", ".dll", ".scr", ".cs3"
    )

    private val NON_SITE_ARCHIVE_OR_SCRIPT_EXTENSIONS = listOf(
        ".cs3", ".zip", ".rar", ".7z", ".tar", ".gz", ".js", ".ts", ".kt", ".java", ".json", ".xml", ".txt"
    )

    /**
     * Validates that a URL is a well-formed public HTTPS URL without embedded credentials,
     * loopback/private network targets, or executable payload extensions.
     * Automatically upgrades plain http:// URLs to https:// unless blocked.
     */
    fun validatePublicHttpsUrl(rawUrl: String, upgradeHttpToHttps: Boolean = true): Result<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("URL cannot be empty"))
        }

        val schemeRaw = trimmed.substringBefore(":", "").lowercase()
        if (schemeRaw in BLOCKED_SCHEMES) {
            return Result.failure(SecurityException("Blocked URL scheme: $schemeRaw"))
        }

        val normalized = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> {
                if (upgradeHttpToHttps) {
                    "https://" + trimmed.substring(7)
                } else {
                    return Result.failure(SecurityException("Insecure HTTP URL blocked; HTTPS is required"))
                }
            }
            !trimmed.contains("://") && trimmed.contains(".") -> "https://$trimmed"
            else -> return Result.failure(SecurityException("Only HTTPS URLs are permitted"))
        }

        val uri = runCatching { URI(normalized) }.getOrElse {
            return Result.failure(IllegalArgumentException("Malformed URL"))
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return Result.failure(SecurityException("Only HTTPS URLs are allowed"))
        }

        if (!uri.userInfo.isNullOrBlank()) {
            return Result.failure(SecurityException("Embedded credentials in URLs are not allowed"))
        }

        val host = uri.host?.lowercase()?.trimEnd('.')
            ?: return Result.failure(IllegalArgumentException("URL is missing a host"))

        if (isPrivateOrLocalHost(host)) {
            return Result.failure(SecurityException("Access to local or private network hosts is blocked"))
        }

        val pathLower = (uri.path ?: "").lowercase()
        if (BLOCKED_EXTENSIONS.any { pathLower.endsWith(it) }) {
            return Result.failure(SecurityException("Executable or binary plugin downloads are blocked"))
        }

        return Result.success(normalized)
    }

    fun isSafePublicHttpsUrl(rawUrl: String): Boolean {
        return validatePublicHttpsUrl(rawUrl, upgradeHttpToHttps = false).isSuccess
    }

    fun isNonSiteArchiveOrScriptUrl(rawUrl: String): Boolean {
        val cleanPath = rawUrl.trim().substringBefore('?').substringBefore('#').lowercase()
        return NON_SITE_ARCHIVE_OR_SCRIPT_EXTENSIONS.any { cleanPath.endsWith(it) }
    }

    fun validateResolvedAddress(addr: InetAddress): Result<Unit> {
        if (addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isMulticastAddress ||
            isUniqueLocalOrCarrierNat(addr)
        ) {
            return Result.failure(SecurityException("Blocked connection to private or local IP (${addr.hostAddress})"))
        }
        return Result.success(Unit)
    }

    /**
     * Builds a hardened OkHttpClient that enforces Modern TLS + HTTPS + SSRF/DNS-rebinding
     * checks and uBlock/AdGuard threat filtering across EVERY redirect hop.
     */
    fun createSecureOkHttpClient(
        connectTimeoutSeconds: Long = 20L,
        readTimeoutSeconds: Long = 60L
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val hopUrl = req.url.toString()
                validatePublicHttpsUrl(hopUrl, upgradeHttpToHttps = false).getOrThrow()
                val shield = ContentBlockerEngine.evaluateProviderOrMediaUrl(hopUrl)
                if (shield.blocked) {
                    ContentBlockerEngine.recordBlocked(shield)
                    throw SecurityException("Blocked unsafe source/redirect (${shield.category?.label ?: "Shield"})")
                }
                chain.connection()?.socket()?.inetAddress?.let { remoteAddr ->
                    validateResolvedAddress(remoteAddr).getOrThrow()
                }
                chain.proceed(req)
            }
            .build()
    }

    fun isPrivateOrLocalHost(rawHost: String): Boolean {
        val host = rawHost.lowercase().trim().trim('[', ']')
        if (host.isBlank()) return true
        if (host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host.endsWith(".lan") ||
            host == "metadata.google.internal"
        ) {
            return true
        }

        // Fast numeric IPv4/IPv6 literal checks without performing blocking DNS lookups
        if (isIpLiteral(host)) {
            val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return true
            return validateResolvedAddress(addr).isFailure
        }
        return false
    }

    private fun isIpLiteral(host: String): Boolean {
        val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        return ipv4.matches(host) || host.contains(":")
    }

    private fun isUniqueLocalOrCarrierNat(addr: InetAddress): Boolean {
        val bytes = addr.address
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            // 100.64.0.0/10 (CGNAT) or 0.0.0.0/8 or 169.254.0.0/16
            if (b0 == 0 || (b0 == 100 && b1 in 64..127) || (b0 == 169 && b1 == 254)) {
                return true
            }
        } else if (bytes.size == 16) {
            val b0 = bytes[0].toInt() and 0xFF
            // fc00::/7 Unique Local IPv6
            if ((b0 and 0xFE) == 0xFC) return true
        }
        return false
    }

    fun isValidVideoContentType(
        contentType: String?,
        url: String,
        contentDisposition: String? = null,
        allowTokenizedOctetStream: Boolean = false
    ): Boolean {
        val cleanUrlPath = url.trim().substringBefore('?').substringBefore('#').lowercase()
        if (isNonSiteArchiveOrScriptUrl(url) || BLOCKED_EXTENSIONS.any { cleanUrlPath.endsWith(it) }) {
            return false
        }
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (ct.startsWith("text/html") ||
            ct.startsWith("application/xhtml") ||
            ct.contains("android.package-archive") ||
            ct.startsWith("application/json") ||
            ct.startsWith("application/javascript") ||
            ct.startsWith("text/javascript")
        ) {
            return false
        }
        if (ct.startsWith("video/") ||
            ct.startsWith("audio/") ||
            ct == "text/vtt" ||
            ct == "application/x-subrip" ||
            ct.contains("mpegurl") ||
            ct.contains("dash+xml") ||
            ct.contains("mp2t") ||
            ct == "application/mp4" ||
            ct == "application/ogg"
        ) {
            return true
        }
        val dispLower = contentDisposition?.lowercase().orEmpty()
        val hasMediaDisposition = listOf(
            ".mp4", ".m3u8", ".webm", ".mkv", ".mov", ".mpd", ".ts", ".m4s",
            ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".vtt", ".srt"
        ).any { dispLower.contains(it) }
        if (hasMediaDisposition) return true

        val isGenericBinary = ct.isBlank() ||
            ct == "application/octet-stream" ||
            ct == "binary/octet-stream" ||
            ct == "application/force-download" ||
            ct == "application/download" ||
            ct == "application/x-download" ||
            ct == "application/binary" ||
            ct.startsWith("text/plain")
        if (isGenericBinary) {
            val clean = url.substringBefore('?').lowercase()
            if (allowTokenizedOctetStream && (ct.isNotBlank() && !ct.startsWith("text/plain"))) {
                return true
            }
            return clean.endsWith(".mp4") ||
                clean.endsWith(".m3u8") ||
                clean.endsWith(".webm") ||
                clean.endsWith(".mkv") ||
                clean.endsWith(".mov") ||
                clean.endsWith(".mpd") ||
                clean.endsWith(".ts") ||
                clean.endsWith(".m4s") ||
                clean.endsWith(".mp3") ||
                clean.endsWith(".m4a") ||
                clean.endsWith(".aac") ||
                clean.endsWith(".ogg") ||
                clean.endsWith(".vtt") ||
                clean.endsWith(".srt") ||
                url.lowercase().contains(".m3u8")
        }
        return false
    }

    fun sanitizeFileName(raw: String, fallback: String = "navula_video"): String {
        val sanitized = raw
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
            .replace("..", "_")
            .trim('.', ' ', '_')
            .take(80)
        return sanitized.ifBlank { fallback }
    }

    fun resolveConfinedChild(baseDir: File, fileName: String): File {
        val safeName = sanitizeFileName(fileName)
        val candidate = File(baseDir, safeName)
        val baseCanonical = baseDir.canonicalPath
        val candidateCanonical = candidate.canonicalPath
        if (candidateCanonical != baseCanonical &&
            !candidateCanonical.startsWith(baseCanonical + File.separator)
        ) {
            throw SecurityException("Path traversal attempt blocked")
        }
        return candidate
    }
}
