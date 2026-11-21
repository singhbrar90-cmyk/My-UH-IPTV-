package com.example.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import com.example.data.network.NetworkClientProvider
import com.example.data.network.StreamUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

data class PublicSampleStream(
    val id: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val formatLabel: String
)

object PublicSampleStreams {
    val samples = listOf(
        PublicSampleStream(
            id = "sample_hls_mux",
            title = "Sample HLS Stream (Mux Big Buck Bunny)",
            subtitle = "Public HTTPS HLS (.m3u8) diagnostic test",
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            formatLabel = "HLS (.m3u8)"
        ),
        PublicSampleStream(
            id = "sample_mp4_google",
            title = "Sample MP4 Stream (Google Media3 Test)",
            subtitle = "Public HTTPS MP4 diagnostic test",
            url = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
            formatLabel = "MP4"
        )
    )
}

data class PlaybackDiagnosticsInfo(
    val playerEngine: String = ENGINE_NAME,
    val videoCodec: String = "Detecting...",
    val audioCodec: String = "Detecting...",
    val videoResolution: String = "Detecting...",
    val frameRate: String = "Detecting...",
    val decoderName: String = "Initializing...",
    val hardwareDecodingSupported: String = "Checking...",
    val droppedFrames: Int = 0,
    val playbackErrorCode: String = "None",
    val httpError: String = "None",
    val videoSurfaceStatus: String = "Initializing SurfaceView...",
    val hdrFormat: String = "SDR / Checking...",
    val streamFormat: String = "Detecting...",
    val httpStatus: String = "Checking...",
    val dnsStatus: String = "Checking...",
    val underlyingError: String = "None",
    val redactedUrl: String = "",
    val device4kSummary: String = "",
    val attemptIndex: Int = 1,
    val totalAttempts: Int = 1
) {
    companion object {
        const val ENGINE_NAME = "AndroidX Media3 ExoPlayer 1.11.1 (Native Surface)"
        const val VLC_ENGINE_NAME = "LibVLC Android SDK 3.7.7 (Native VLC)"
        const val EXTERNAL_ENGINE_NAME = "External Android Video Player"
    }
}

data class StreamProbeResult(
    val dnsStatus: String,
    val httpStatus: String,
    val detectedFormat: String,
    val detailNote: String? = null
)

@UnstableApi
object StreamDiagnostics {

    fun mimeTypeForUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".mp4") || path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            path.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            else -> null
        }
    }

    fun extractHttpStatusCode(error: PlaybackException): Int? {
        return generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
            ?.responseCode
    }

    fun isHttpAuthError(error: PlaybackException): Boolean {
        val code = extractHttpStatusCode(error)
        return code == 401 || code == 403
    }

    fun isDecoderOrCodecError(error: PlaybackException): Boolean {
        if (isHttpAuthError(error)) return false
        if (error.errorCode in listOf(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
            )
        ) {
            return true
        }
        return generateSequence<Throwable>(error) { it.cause }
            .any { it is MediaCodecRenderer.DecoderInitializationException }
    }

    fun isEndpointFormatError(error: PlaybackException): Boolean {
        if (isDecoderOrCodecError(error) || isHttpAuthError(error)) return false
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        if (causes.any { it is UnknownHostException || it is SSLHandshakeException }) {
            return false
        }
        val httpInvalid = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        if (httpInvalid != null) {
            return httpInvalid.responseCode in setOf(404, 405, 406, 415, 500, 502)
        }
        return error.errorCode in listOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        ) || causes.any { it is UnrecognizedInputFormatException }
    }

    fun extractErrorDetails(
        error: PlaybackException,
        videoMimeHint: String? = null,
        streamWidth: Int = 0,
        streamHeight: Int = 0,
        device4kProfile: Device4kProfile? = null
    ): Pair<String, String> {
        val codeLabel = "${error.errorCodeName} (${error.errorCode})"
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()

        val decoderInit = causes.filterIsInstance<MediaCodecRenderer.DecoderInitializationException>().firstOrNull()
        val httpInvalid = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        val unrecognized = causes.filterIsInstance<UnrecognizedInputFormatException>().firstOrNull()
        val unknownHost = causes.filterIsInstance<UnknownHostException>().firstOrNull()
        val connectEx = causes.filterIsInstance<ConnectException>().firstOrNull()
        val timeoutEx = causes.filterIsInstance<SocketTimeoutException>().firstOrNull()
        val sslEx = causes.filterIsInstance<SSLHandshakeException>().firstOrNull()

        val resolvedMime = decoderInit?.mimeType ?: videoMimeHint
        val compatMsg = if (device4kProfile != null && (isDecoderOrCodecError(error) || streamHeight >= 2100 || streamWidth >= 3800)) {
            CodecCapabilityInspector.buildCompatibilityMessage(
                mimeType = resolvedMime,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                profile = device4kProfile
            )
        } else {
            null
        }

        val detail = when {
            httpInvalid?.responseCode == 401 -> {
                "HTTP 401 Unauthorized — Stream server rejected credentials, session token expired, or maximum concurrent connections reached. Check your playlist credentials and active devices."
            }
            httpInvalid?.responseCode == 403 -> {
                "HTTP 403 Forbidden — Provider denied stream access (expired token, IP restriction, or blocked stream endpoint)."
            }
            compatMsg != null && isDecoderOrCodecError(error) -> compatMsg
            decoderInit != null -> {
                val codecLabel = CodecCapabilityInspector.codecDisplayName(decoderInit.mimeType)
                val decName = decoderInit.codecInfo?.name ?: "none"
                "Device decoder failed to initialize for $codecLabel (decoder: $decName). ${
                    compatMsg ?: (decoderInit.localizedMessage ?: "Unsupported resolution, profile, or 10-bit depth.")
                }"
            }
            isDecoderOrCodecError(error) -> {
                val codecLabel = CodecCapabilityInspector.codecDisplayName(resolvedMime)
                val resText = if (streamWidth > 0 && streamHeight > 0) "${streamWidth}×${streamHeight} " else ""
                "This device cannot decode the ${resText}$codecLabel stream (${error.errorCodeName}). Choose Full HD or HD if supplied by your provider."
            }
            httpInvalid != null -> {
                val msg = httpInvalid.responseMessage?.takeIf { it.isNotBlank() } ?: "HTTP Error"
                val bodySnippet = runCatching {
                    String(httpInvalid.responseBody, Charsets.UTF_8)
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                        .take(120)
                }.getOrDefault("")
                buildString {
                    append("HTTP ${httpInvalid.responseCode} $msg")
                    if (bodySnippet.isNotBlank()) append(" — $bodySnippet")
                }
            }
            unknownHost != null ->
                "DNS resolution failed for host (${unknownHost.message ?: "UnknownHostException"})"
            connectEx != null ->
                "TCP connection failed (${connectEx.message ?: "ConnectException"})"
            timeoutEx != null ->
                "Server timed out (${timeoutEx.message ?: "SocketTimeoutException"})"
            sslEx != null ->
                "TLS handshake failed (${sslEx.message ?: "SSLHandshakeException"})"
            unrecognized != null ->
                "Unrecognized container/stream format (${unrecognized.message ?: "format mismatch"})"
            else -> {
                val deepest = causes.lastOrNull()
                val rawMsg = deepest?.message ?: error.message ?: error.localizedMessage ?: "Unknown playback error"
                "${deepest?.javaClass?.simpleName ?: "PlaybackException"}: $rawMsg"
            }
        }
        return codeLabel to StreamUrlResolver.redactCredentials(detail)
    }

    fun extractHttpErrorLabel(error: PlaybackException): String {
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        val httpInvalid = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        if (httpInvalid != null) {
            return when (httpInvalid.responseCode) {
                401 -> "HTTP 401 Unauthorized (Authentication / Token / Connection limit)"
                403 -> "HTTP 403 Forbidden (Access denied)"
                else -> "HTTP ${httpInvalid.responseCode} ${httpInvalid.responseMessage}".trim()
            }
        }
        val httpEx = causes.filterIsInstance<HttpDataSource.HttpDataSourceException>().firstOrNull()
        if (httpEx != null) {
            val root = causes.lastOrNull()
            return "Network I/O Error (${root?.javaClass?.simpleName ?: "HTTP type ${httpEx.type}"})"
        }
        return "None"
    }

    fun extractHttpStatusFromException(error: PlaybackException): String? {
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        val httpInvalid = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        if (httpInvalid != null) {
            return "HTTP ${httpInvalid.responseCode} ${httpInvalid.responseMessage}".trim()
        }
        val httpEx = causes.filterIsInstance<HttpDataSource.HttpDataSourceException>().firstOrNull()
        if (httpEx != null) {
            return when (val root = causes.lastOrNull()) {
                is UnknownHostException -> "Unreachable (DNS Failed)"
                is ConnectException -> "Connection Refused"
                is SocketTimeoutException -> "Timed Out"
                is SSLHandshakeException -> "TLS Handshake Failed"
                else -> "Network I/O Error (${root?.javaClass?.simpleName ?: httpEx.type})"
            }
        }
        return null
    }

    suspend fun probeStream(
        url: String,
        httpClient: OkHttpClient = NetworkClientProvider.streamOkHttpClient
    ): StreamProbeResult = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrlOrNull()
            ?: return@withContext StreamProbeResult(
                dnsStatus = "Invalid URL",
                httpStatus = "Not attempted",
                detectedFormat = "Unknown",
                detailNote = "Malformed stream URL"
            )

        val host = parsed.host
        val dnsResult = try {
            val addresses = InetAddress.getAllByName(host)
            val primaryIp = addresses.firstOrNull()?.hostAddress ?: "resolved"
            "Resolved ($host -> $primaryIp)"
        } catch (e: Exception) {
            return@withContext StreamProbeResult(
                dnsStatus = "Failed (${e.javaClass.simpleName}: ${e.message ?: host})",
                httpStatus = "Not reached",
                detectedFormat = StreamUrlResolver.detectFormatLabel(url),
                detailNote = "DNS lookup failed for $host"
            )
        }

        return@withContext try {
            val request = Request.Builder()
                .url(parsed)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val redirectCode = response.priorResponse?.code
                val statusLabel = buildString {
                    if (redirectCode != null) append("HTTP $redirectCode -> ")
                    append("HTTP ${response.code} ${response.message}".trim())
                }
                val contentType = response.header("Content-Type").orEmpty()
                val sniffBuffer = ByteArray(512)
                var bytesRead = 0
                val source = response.body?.source()
                if (source != null) {
                    val buffer = okio.Buffer()
                    runCatching { source.read(buffer, 512L) }
                    bytesRead = buffer.size.toInt().coerceAtMost(512)
                    if (bytesRead > 0) {
                        buffer.read(sniffBuffer, 0, bytesRead)
                    }
                }
                val signature = detectByteSignature(sniffBuffer, bytesRead)
                val detectedFormat = StreamUrlResolver.detectFormatLabel(
                    url = response.request.url.toString(),
                    contentTypeHeader = contentType,
                    sniffedSignature = signature
                )
                val note = when {
                    !response.isSuccessful -> "Server rejected stream with $statusLabel"
                    signature == "HTML" -> "Server returned an HTML page instead of media bytes"
                    else -> null
                }
                StreamProbeResult(
                    dnsStatus = dnsResult,
                    httpStatus = statusLabel,
                    detectedFormat = detectedFormat,
                    detailNote = note
                )
            }
        } catch (e: SSLHandshakeException) {
            StreamProbeResult(
                dnsStatus = dnsResult,
                httpStatus = "TLS Handshake Failed",
                detectedFormat = StreamUrlResolver.detectFormatLabel(url),
                detailNote = StreamUrlResolver.redactCredentials(e.message ?: "Certificate validation failed")
            )
        } catch (e: Exception) {
            StreamProbeResult(
                dnsStatus = dnsResult,
                httpStatus = "Error (${e.javaClass.simpleName})",
                detectedFormat = StreamUrlResolver.detectFormatLabel(url),
                detailNote = StreamUrlResolver.redactCredentials(e.message ?: "Stream connection error")
            )
        }
    }

    fun detectByteSignature(bytes: ByteArray, length: Int): String? {
        val safeLen = length.coerceIn(0, bytes.size)
        if (safeLen <= 0) return null
        val headText = String(bytes, 0, safeLen.coerceAtMost(256), Charsets.UTF_8).trimStart()
        return when {
            headText.startsWith("#EXTM3U", ignoreCase = true) -> "HLS"
            headText.startsWith("<!DOCTYPE", ignoreCase = true) || headText.startsWith("<html", ignoreCase = true) -> "HTML"
            bytes[0] == 0x47.toByte() -> "MPEG-TS"
            safeLen >= 8 &&
                bytes[4] == 'f'.code.toByte() &&
                bytes[5] == 't'.code.toByte() &&
                bytes[6] == 'y'.code.toByte() &&
                bytes[7] == 'p'.code.toByte() -> "MP4"
            safeLen >= 4 &&
                bytes[0] == 0x1A.toByte() &&
                bytes[1] == 0x45.toByte() &&
                bytes[2] == 0xDF.toByte() &&
                bytes[3] == 0xA3.toByte() -> "MKV"
            else -> null
        }
    }
}
