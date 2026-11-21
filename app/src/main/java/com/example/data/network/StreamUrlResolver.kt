package com.example.data.network

import com.example.data.model.MediaContentType
import java.net.URLEncoder

object StreamUrlResolver {

    private val xtreamTypedPathRegex = Regex(
        pattern = """^(https?://[^/]+)/(live|movie|series)/([^/]+)/([^/]+)/([^/?#]+?)(\.[a-zA-Z0-9]{2,5})?(\?.*)?$""",
        option = RegexOption.IGNORE_CASE
    )

    private val xtreamShortLivePathRegex = Regex(
        pattern = """^(https?://[^/]+)/([^/]+)/([^/]+)/(\d+)(\.[a-zA-Z0-9]{2,5})?(\?.*)?$""",
        option = RegexOption.IGNORE_CASE
    )

    private val redactTypedUrlRegex = Regex(
        pattern = """(https?://[^/\s]+)/(live|movie|series)/([^/\s]+)/([^/\s]+)/([^/?#\s]+)""",
        option = RegexOption.IGNORE_CASE
    )

    private val redactShortUrlRegex = Regex(
        pattern = """(https?://[^/\s]+)/([^/\s]+)/([^/\s]+)/(\d+(?:\.[a-zA-Z0-9]{2,5})?)""",
        option = RegexOption.IGNORE_CASE
    )

    private val basicUserInfoRegex = Regex(
        pattern = """(https?://)([^:@/\s]+):([^@/\s]+)@""",
        option = RegexOption.IGNORE_CASE
    )

    private val queryCredRegex = Regex(
        pattern = """([?&](?:username|user|password|pass|token)=)([^&#\s]*)""",
        option = RegexOption.IGNORE_CASE
    )

    fun encodePathSegment(raw: String): String {
        if (raw.isEmpty()) return ""
        return URLEncoder.encode(raw, "UTF-8").replace("+", "%20")
    }

    fun cleanExtension(rawExt: String?, defaultExt: String): String {
        val cleaned = rawExt.orEmpty().trim().trimStart('.').lowercase()
        return if (cleaned.matches(Regex("""^[a-z0-9]{2,5}$"""))) cleaned else defaultExt
    }

    fun buildXtreamLiveCandidates(
        baseUrl: String,
        username: String,
        password: String,
        streamId: String,
        allowedOutputFormats: List<String> = emptyList(),
        directSource: String = ""
    ): List<String> {
        val cleanBase = baseUrl.trimEnd('/')
        val encUser = encodePathSegment(username.trim())
        val encPass = encodePathSegment(password)
        val encId = encodePathSegment(streamId.trim())

        val normalizedFormats = allowedOutputFormats.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val orderedExtensions = linkedSetOf<String>()

        // Honor provider's allowed_output_formats first; default to TS before M3U8 when provider lists ts or omits formats
        when {
            normalizedFormats.isEmpty() -> {
                orderedExtensions.add("ts")
                orderedExtensions.add("m3u8")
            }
            normalizedFormats.contains("ts") && !normalizedFormats.contains("m3u8") -> {
                orderedExtensions.add("ts")
                orderedExtensions.add("m3u8")
            }
            normalizedFormats.contains("m3u8") -> {
                orderedExtensions.add("m3u8")
                orderedExtensions.add("ts")
            }
            else -> {
                normalizedFormats.forEach { orderedExtensions.add(cleanExtension(it, "ts")) }
                orderedExtensions.add("ts")
                orderedExtensions.add("m3u8")
            }
        }

        val candidates = linkedSetOf<String>()
        val cleanDirect = directSource.trim()
        if (cleanDirect.startsWith("http://", ignoreCase = true) ||
            cleanDirect.startsWith("https://", ignoreCase = true)
        ) {
            candidates.add(cleanDirect)
        }
        orderedExtensions.forEach { ext ->
            candidates.add("$cleanBase/live/$encUser/$encPass/$encId.$ext")
        }
        // Fallback extensionless MPEG-TS patterns used by many Xtream UI / XUI panels
        candidates.add("$cleanBase/$encUser/$encPass/$encId")
        candidates.add("$cleanBase/live/$encUser/$encPass/$encId")
        return candidates.toList()
    }

    fun buildXtreamVodCandidates(
        baseUrl: String,
        username: String,
        password: String,
        streamId: String,
        containerExtension: String,
        directSource: String = ""
    ): List<String> {
        val cleanBase = baseUrl.trimEnd('/')
        val encUser = encodePathSegment(username.trim())
        val encPass = encodePathSegment(password)
        val encId = encodePathSegment(streamId.trim())
        val primaryExt = cleanExtension(containerExtension, "mp4")

        val candidates = linkedSetOf<String>()
        val cleanDirect = directSource.trim()
        if (cleanDirect.startsWith("http://", ignoreCase = true) ||
            cleanDirect.startsWith("https://", ignoreCase = true)
        ) {
            candidates.add(cleanDirect)
        }
        candidates.add("$cleanBase/movie/$encUser/$encPass/$encId.$primaryExt")
        listOf("mp4", "mkv", "ts", "m3u8", "avi").forEach { fallbackExt ->
            if (fallbackExt != primaryExt) {
                candidates.add("$cleanBase/movie/$encUser/$encPass/$encId.$fallbackExt")
            }
        }
        candidates.add("$cleanBase/movie/$encUser/$encPass/$encId")
        return candidates.toList()
    }

    fun buildXtreamSeriesCandidates(
        baseUrl: String,
        username: String,
        password: String,
        episodeId: String,
        containerExtension: String,
        directSource: String = ""
    ): List<String> {
        val cleanBase = baseUrl.trimEnd('/')
        val encUser = encodePathSegment(username.trim())
        val encPass = encodePathSegment(password)
        val encId = encodePathSegment(episodeId.trim())
        val primaryExt = cleanExtension(containerExtension, "mp4")

        val candidates = linkedSetOf<String>()
        val cleanDirect = directSource.trim()
        if (cleanDirect.startsWith("http://", ignoreCase = true) ||
            cleanDirect.startsWith("https://", ignoreCase = true)
        ) {
            candidates.add(cleanDirect)
        }
        candidates.add("$cleanBase/series/$encUser/$encPass/$encId.$primaryExt")
        listOf("mp4", "mkv", "ts", "m3u8", "avi").forEach { fallbackExt ->
            if (fallbackExt != primaryExt) {
                candidates.add("$cleanBase/series/$encUser/$encPass/$encId.$fallbackExt")
            }
        }
        candidates.add("$cleanBase/series/$encUser/$encPass/$encId")
        return candidates.toList()
    }

    /**
     * Ensures even legacy saved Favourites or Continue Watching URLs get fallback candidates
     * across Xtream stream extensions (.ts / .m3u8 / extensionless for Live, .mp4 / .mkv / .ts for VOD).
     */
    fun deriveFallbackCandidates(
        primaryUrl: String,
        contentType: MediaContentType,
        explicitCandidates: List<String> = emptyList()
    ): List<String> {
        val result = linkedSetOf<String>()
        if (primaryUrl.isNotBlank()) result.add(primaryUrl.trim())
        explicitCandidates.filter { it.isNotBlank() }.forEach { result.add(it.trim()) }

        val match = xtreamTypedPathRegex.find(primaryUrl.trim())
        if (match != null) {
            val base = match.groupValues[1]
            val kind = match.groupValues[2].lowercase()
            val user = match.groupValues[3]
            val pass = match.groupValues[4]
            val id = match.groupValues[5]
            val ext = match.groupValues[6].trimStart('.').lowercase()
            val query = match.groupValues[7]
            when (kind) {
                "live" -> {
                    listOf("ts", "m3u8").forEach { e ->
                        result.add("$base/live/$user/$pass/$id.$e$query")
                    }
                    result.add("$base/$user/$pass/$id$query")
                    result.add("$base/live/$user/$pass/$id$query")
                }
                "movie", "series" -> {
                    if (ext.isNotBlank()) result.add("$base/$kind/$user/$pass/$id.$ext$query")
                    listOf("mp4", "mkv", "ts", "m3u8").forEach { e ->
                        result.add("$base/$kind/$user/$pass/$id.$e$query")
                    }
                    result.add("$base/$kind/$user/$pass/$id$query")
                }
            }
        } else if (contentType == MediaContentType.LIVE) {
            val shortMatch = xtreamShortLivePathRegex.find(primaryUrl.trim())
            if (shortMatch != null) {
                val base = shortMatch.groupValues[1]
                val user = shortMatch.groupValues[2]
                val pass = shortMatch.groupValues[3]
                val id = shortMatch.groupValues[4]
                val query = shortMatch.groupValues[6]
                result.add("$base/live/$user/$pass/$id.ts$query")
                result.add("$base/live/$user/$pass/$id.m3u8$query")
                result.add("$base/$user/$pass/$id$query")
            }
        }
        return result.toList()
    }

    fun detectFormatLabel(url: String, contentTypeHeader: String? = null, sniffedSignature: String? = null): String {
        val header = contentTypeHeader?.lowercase().orEmpty()
        val pathOnly = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            sniffedSignature == "HLS" || pathOnly.endsWith(".m3u8") || pathOnly.endsWith(".m3u") || header.contains("mpegurl") ->
                "HLS (.m3u8)"
            pathOnly.endsWith(".mpd") || header.contains("dash+xml") ->
                "MPEG-DASH (.mpd)"
            sniffedSignature == "MPEG-TS" || pathOnly.endsWith(".ts") || header.contains("mp2t") ->
                "MPEG-TS (.ts)"
            sniffedSignature == "MP4" || pathOnly.endsWith(".mp4") || pathOnly.endsWith(".m4v") || header.contains("video/mp4") ->
                "MP4"
            sniffedSignature == "MKV" || pathOnly.endsWith(".mkv") || header.contains("matroska") ->
                "Matroska (.mkv)"
            pathOnly.endsWith(".avi") || header.contains("x-msvideo") ->
                "AVI"
            pathOnly.endsWith(".flv") ->
                "FLV"
            header.isNotBlank() -> header
            else -> "Auto-detect (Progressive / TS)"
        }
    }

    fun redactCredentials(text: String): String {
        if (text.isBlank()) return text
        var sanitized = basicUserInfoRegex.replace(text) { m ->
            "${m.groupValues[1]}***:***@"
        }
        sanitized = redactTypedUrlRegex.replace(sanitized) { m ->
            "${m.groupValues[1]}/${m.groupValues[2]}/***/***/${m.groupValues[5]}"
        }
        sanitized = redactShortUrlRegex.replace(sanitized) { m ->
            "${m.groupValues[1]}/***/***/${m.groupValues[4]}"
        }
        sanitized = queryCredRegex.replace(sanitized) { m ->
            "${m.groupValues[1]}***"
        }
        return sanitized
    }
}
