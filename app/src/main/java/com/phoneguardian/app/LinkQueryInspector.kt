package com.phoneguardian.app

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Inspects query values locally. It never opens URLs or performs network requests.
 */
internal object LinkQueryInspector {
    internal data class Result(
        val hosts: Set<String>,
        val hasNestedRedirectEncoding: Boolean
    )

    private val embeddedUrlPattern =
        Regex("""(?i)(?:https?://|//)[^\\s"'<>\\\\]+""")

    fun inspect(rawQuery: String, redirectKeys: Set<String>): Result {
        val hosts = linkedSetOf<String>()
        var hasNestedRedirectEncoding = false

        rawQuery.substringBefore("#").split("&").forEach { part ->
            if (!part.contains("=")) return@forEach

            val key = runCatching {
                URLDecoder.decode(part.substringBefore("="), "UTF-8")
                    .lowercase(Locale.ROOT)
            }.getOrDefault("")
            val isRedirectParameter = key in redirectKeys
            var candidate = part.substringAfter("=")

            repeat(5) { depth ->
                val decoded = runCatching {
                    URLDecoder.decode(candidate.replace("+", "%2B"), "UTF-8")
                }.getOrDefault(candidate)

                if (isRedirectParameter && depth > 0 &&
                    decoded != candidate && embeddedUrlPattern.containsMatchIn(decoded)
                ) {
                    hasNestedRedirectEncoding = true
                }

                candidate = decoded

                if (isRedirectParameter) {
                    extractHost(candidate)?.let { hosts.add(it) }
                }

                embeddedUrlPattern.findAll(candidate)
                    .take(8)
                    .forEach { match ->
                        extractHost(match.value.trimEnd('.', ',', ';', ')', ']'))
                            ?.let { hosts.add(it) }
                    }
            }
        }

        return Result(hosts, hasNestedRedirectEncoding)
    }

    private fun extractHost(value: String): String? {
        val trimmed = value.trim()
        val absolute = when {
            trimmed.startsWith("http://", true) ||
                trimmed.startsWith("https://", true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> return null
        }
        val uri = runCatching { URI(absolute) }.getOrNull()
        val authority = uri?.rawAuthority ?: absolute.substringAfter("://", "")
            .substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isBlank()) return null

        val withoutUserInfo = authority.substringAfterLast('@')
        val host = when {
            withoutUserInfo.startsWith("[") && "]" in withoutUserInfo ->
                withoutUserInfo.substringAfter("[").substringBefore("]")
            withoutUserInfo.count { it == ':' } == 1 ->
                withoutUserInfo.substringBeforeLast(':')
            else -> withoutUserInfo
        }
        return host.lowercase(Locale.ROOT).trimEnd('.').takeIf { it.isNotBlank() }
    }
}
