package com.quietrays.tonarc.data.network.youtube

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

data class ParsedInnertubeAuth(
    val cookies: String?,
    val visitorData: String?,
    val sapisid: String?
) {
    val isValid: Boolean
        get() = !cookies.isNullOrBlank() || !visitorData.isNullOrBlank()
}

object InnertubeAuthParser {

    /**
     * Parses raw user input containing Innertube authentication data.
     * Supports:
     * - Tagged block exports (e.g. `***INNERTUBE COOKIE*** = ...`, `***VISITOR DATA*** = ...`)
     * - Raw cookie strings ("SAPISID=...; __Secure-3PAPISID=...")
     * - "Cookie: ..." header strings
     * - Multi-line HTTP header dumps from browser DevTools
     * - Full cURL requests from browser Network tab
     * - JSON configurations (e.g. `{"cookie": "...", "visitorData": "..."}`)
     * - Plain SAPISID token strings (e.g. "1a2b3c4d5e...")
     * - Plain VisitorData token strings (e.g. "Cgt...")
     */
    fun parse(rawInput: String): ParsedInnertubeAuth {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ParsedInnertubeAuth(cookies = null, visitorData = null, sapisid = null)
        }

        var extractedCookies: String? = null
        var extractedVisitorData: String? = null

        // 0. Tagged block exports (e.g. ***INNERTUBE COOKIE*** = ..., ***VISITOR DATA*** = ...)
        val taggedPattern = Regex("""^\s*\*{1,3}([^=*\n]+)\*{1,3}\s*=\s*(.*)$""")
        val taggedMap = mutableMapOf<String, String>()
        for (line in trimmed.lines()) {
            val match = taggedPattern.find(line.trim())
            if (match != null) {
                val key = match.groupValues[1].trim().lowercase()
                val value = match.groupValues[2].trim()
                if (value.isNotEmpty()) {
                    taggedMap[key] = value
                }
            }
        }

        if (taggedMap.isNotEmpty()) {
            extractedCookies = taggedMap["innertube cookie"]
                ?: taggedMap["innertube_cookie"]
                ?: taggedMap["cookie"]
                ?: taggedMap["cookies"]

            extractedVisitorData = taggedMap["visitor data"]
                ?: taggedMap["visitor_data"]
                ?: taggedMap["visitordata"]
                ?: taggedMap["visitor"]
        }

        // 1. JSON parsing
        if (extractedCookies == null && trimmed.startsWith("{") && trimmed.endsWith("}")) {
            runCatching {
                val json = JSONObject(trimmed)
                if (json.has("cookie")) extractedCookies = json.optString("cookie")
                if (json.has("cookies")) extractedCookies = json.optString("cookies")
                if (json.has("visitorData")) extractedVisitorData = json.optString("visitorData")
                if (json.has("visitor_data")) extractedVisitorData = json.optString("visitor_data")

                if (extractedCookies == null) {
                    val pairs = mutableListOf<String>()
                    json.keys().forEach { key ->
                        val value = json.optString(key)
                        if (value.isNotBlank()) {
                            if (key.equals("visitorData", ignoreCase = true) || key.equals("visitor_data", ignoreCase = true)) {
                                extractedVisitorData = value
                            } else {
                                pairs.add("$key=$value")
                            }
                        }
                    }
                    if (pairs.isNotEmpty()) {
                        extractedCookies = pairs.joinToString("; ")
                    }
                }
            }
        }

        // 2. cURL command parsing
        if (extractedCookies == null && (trimmed.contains("curl", ignoreCase = true) || trimmed.contains("-H", ignoreCase = true))) {
            val cookieMatch = Regex("""(?:-H|--header)\s+['"][Cc]ookie:\s*([^'"]+)['"]""").find(trimmed)
            if (cookieMatch != null) {
                extractedCookies = cookieMatch.groupValues[1].trim()
            }

            val visitorMatch = Regex("""(?:-H|--header)\s+['"][Xx]-[Yy]ou[Tt]ube-[Vv]isitor-[Dd]ata:\s*([^'"]+)['"]""").find(trimmed)
            if (visitorMatch != null) {
                extractedVisitorData = visitorMatch.groupValues[1].trim()
            }
        }

        // 3. Multi-line HTTP headers
        if (extractedCookies == null && trimmed.contains("\n")) {
            val lines = trimmed.lines()
            val cookieLines = lines.filter {
                val l = it.trim()
                l.startsWith("cookie:", ignoreCase = true) ||
                    l.startsWith("cookie=", ignoreCase = true) ||
                    l.startsWith("cookie =", ignoreCase = true)
            }
            if (cookieLines.isNotEmpty()) {
                extractedCookies = cookieLines.joinToString("; ") { line ->
                    val trimmedLine = line.trim()
                    if (trimmedLine.contains(":")) {
                        trimmedLine.substringAfter(":").trim()
                    } else {
                        trimmedLine.substringAfter("=").trim()
                    }
                }
            }

            val visitorLine = lines.firstOrNull {
                val l = it.trim()
                l.startsWith("x-youtube-visitor-data:", ignoreCase = true) ||
                    l.startsWith("x-youtube-visitor-data=", ignoreCase = true) ||
                    l.startsWith("x-youtube-visitor-data =", ignoreCase = true)
            }
            if (visitorLine != null) {
                val trimmedLine = visitorLine.trim()
                extractedVisitorData = if (trimmedLine.contains(":")) {
                    trimmedLine.substringAfter(":").trim()
                } else {
                    trimmedLine.substringAfter("=").trim()
                }
            }
        }

        // 4. Single-line formats
        if (extractedCookies == null) {
            val clean = if (trimmed.startsWith("cookie:", ignoreCase = true)) {
                trimmed.substringAfter(":").trim()
            } else {
                trimmed
            }

            if (clean.contains("=") && clean.contains(";")) {
                extractedCookies = clean
            } else if (clean.contains("=")) {
                val key = clean.substringBefore("=").trim()
                val value = clean.substringAfter("=").trim()
                if (key.equals("visitorData", ignoreCase = true) ||
                    key.equals("visitor_data", ignoreCase = true) ||
                    key.equals("x-youtube-visitor-data", ignoreCase = true)
                ) {
                    extractedVisitorData = value
                } else {
                    extractedCookies = clean
                }
            } else if (clean.startsWith("Cgt") && clean.length >= 12) {
                extractedVisitorData = clean
            } else {
                // Treat as raw SAPISID token directly
                extractedCookies = "SAPISID=$clean; __Secure-3PAPISID=$clean"
            }
        }

        // Check if visitorData is embedded inside cookies
        if (extractedCookies != null && extractedVisitorData == null) {
            val embeddedVisitor = extractCookieValue(extractedCookies, "VISITOR_INFO1_LIVE")
                ?: extractCookieValue(extractedCookies, "visitor_data")
                ?: extractCookieValue(extractedCookies, "visitorData")
            if (!embeddedVisitor.isNullOrBlank()) {
                extractedVisitorData = embeddedVisitor
            }
        }

        val decodedVisitorData = extractedVisitorData?.let { rawVisitor ->
            runCatching {
                if (rawVisitor.contains("%")) {
                    URLDecoder.decode(rawVisitor, StandardCharsets.UTF_8.name())
                } else {
                    rawVisitor
                }
            }.getOrDefault(rawVisitor)
        }

        val normalizedCookies = extractedCookies?.let { normalizeCookies(it) }?.takeIf { it.isNotBlank() }
        val finalVisitorData = decodedVisitorData?.trim()?.takeIf { it.isNotBlank() }

        val sapisid = normalizedCookies?.let { cookies ->
            extractCookieValue(cookies, "SAPISID")
                ?: extractCookieValue(cookies, "__Secure-3PAPISID")
                ?: extractCookieValue(cookies, "__Secure-1PAPISID")
        }

        return ParsedInnertubeAuth(
            cookies = normalizedCookies,
            visitorData = finalVisitorData,
            sapisid = sapisid
        )
    }

    private fun normalizeCookies(raw: String): String {
        return raw.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("=") }
            .joinToString("; ")
    }

    fun extractCookieValue(cookies: String, name: String): String? {
        val regex = Regex("(?:^|;\\s*)${Regex.escape(name)}=([^;]+)")
        return regex.find(cookies)?.groupValues?.get(1)?.trim()
    }
}
