package com.example.player

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import com.example.R
import com.example.data.model.AudioTrackOption
import com.example.data.model.MediaContentType
import com.example.data.model.PlaybackRequest
import com.example.data.model.UserPreferencesDoc
import com.example.data.network.StreamUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale

class VlcPlaybackController(
    private val mediaPlayer: MediaPlayer
) {
    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        } else {
            mediaPlayer.play()
        }
    }

    fun seekToMs(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        if (mediaPlayer.isSeekable) {
            mediaPlayer.time = safePos
        }
    }

    fun seekRelativeMs(deltaMs: Long, durationMs: Long) {
        val target = (mediaPlayer.time.coerceAtLeast(0L) + deltaMs).coerceAtLeast(0L)
        val clamped = if (durationMs > 0L) target.coerceAtMost(durationMs) else target
        seekToMs(clamped)
    }

    fun selectAudioTrack(trackId: Int) {
        mediaPlayer.audioTrack = trackId
    }

    fun selectSubtitleTrack(trackId: Int) {
        mediaPlayer.spuTrack = trackId
    }

    fun currentPositionMs(): Long = mediaPlayer.time.coerceAtLeast(0L)

    fun currentDurationMs(): Long = mediaPlayer.length.coerceAtLeast(0L)

    fun isPlaying(): Boolean = mediaPlayer.isPlaying
}

@OptIn(UnstableApi::class)
@Composable
fun VlcPlayerHost(
    context: Context,
    activeUrl: String,
    request: PlaybackRequest,
    preferences: UserPreferencesDoc,
    playerLocalSettings: PlayerLocalSettings,
    useTextureView: Boolean,
    resizeModeName: String,
    retryToken: Int,
    manualAudioOverride: Boolean,
    onControllerReady: (VlcPlaybackController?) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onProgressTick: (positionMs: Long, durationMs: Long) -> Unit,
    onEpisodeEnded: () -> Unit,
    onAudioTracksUpdated: (List<AudioTrackOption>, String?) -> Unit,
    onSubtitleTracksUpdated: (List<SubtitleTrackOption>) -> Unit,
    onDiagnosticsUpdated: ((PlaybackDiagnosticsInfo) -> PlaybackDiagnosticsInfo) -> Unit,
    onPlaybackError: (errorMessage: String?, isHttpAuth: Boolean, tryNextCandidate: Boolean) -> Unit,
    onSaveProgress: (Long, Long) -> Unit
) {
    val latestPreferences by rememberUpdatedState(preferences)
    val latestManualAudioOverride by rememberUpdatedState(manualAudioOverride)
    val latestRequest by rememberUpdatedState(request)

    val libVlc = remember(context) {
        val options = arrayListOf(
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--http-reconnect",
            "--http-user-agent=VLC/3.0.20 LibVLC/3.0.20",
            "--network-caching=3000"
        )
        LibVLC(context.applicationContext, options)
    }

    val mediaPlayer = remember(libVlc) { MediaPlayer(libVlc) }
    val controller = remember(mediaPlayer) { VlcPlaybackController(mediaPlayer) }

    var vlcLayoutRef by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var attachedToSurface by remember { mutableStateOf(false) }
    var autoAppliedAudioForStream by remember(activeUrl, retryToken) { mutableStateOf(false) }
    var selectedSubtitleTrackId by remember(activeUrl, retryToken) { mutableIntStateOf(-1) }
    var voutRendered by remember(activeUrl, retryToken) { mutableStateOf(false) }

    LaunchedEffect(controller) {
        onControllerReady(controller)
    }

    // Map aspect ratio mode to LibVLC ScaleType
    LaunchedEffect(mediaPlayer, resizeModeName) {
        mediaPlayer.videoScale = when (resizeModeName) {
            "FILL" -> MediaPlayer.ScaleType.SURFACE_FILL
            "ZOOM" -> MediaPlayer.ScaleType.SURFACE_16_9
            else -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        }
    }

    // Helper to read audio, subtitle, video track, and dropped frames telemetry from LibVLC
    fun refreshVlcTelemetry() {
        val videoTrack = mediaPlayer.currentVideoTrack
        var audioTrackInfo: IMedia.AudioTrack? = null
        val mediaStats = runCatching {
            val m = mediaPlayer.media
            if (m != null) {
                try {
                    for (i in 0 until m.trackCount) {
                        val t = m.getTrack(i)
                        if (t is IMedia.AudioTrack) {
                            audioTrackInfo = t
                            break
                        }
                    }
                    m.stats
                } finally {
                    m.release()
                }
            } else {
                null
            }
        }.getOrNull()

        val rawAudioTracks = mediaPlayer.audioTracks.orEmpty().filter { it.id >= 0 }
        val activeAudioId = mediaPlayer.audioTrack
        if (!latestManualAudioOverride && !autoAppliedAudioForStream && latestPreferences.autoAudioEnabled && rawAudioTracks.isNotEmpty()) {
            val bestId = AudioTrackHelper.findBestTrackIdByLanguage(
                tracks = rawAudioTracks.map { it.id to it.name.orEmpty() },
                priority = latestPreferences.audioPriority,
                sessionPreferredLang = latestRequest.preferredAudioLang
            )
            if (bestId != null && bestId != activeAudioId) {
                mediaPlayer.audioTrack = bestId
            }
            autoAppliedAudioForStream = true
        }

        val currentAudioId = mediaPlayer.audioTrack
        val activeChannels = audioTrackInfo?.channels?.takeIf { it > 0 } ?: 2
        val activeBitrate = audioTrackInfo?.bitrate?.takeIf { it > 0 } ?: 0
        val audioList = rawAudioTracks.mapIndexed { idx, desc ->
            val rawName = desc.name.orEmpty()
            val langCode = AudioTrackHelper.normalizeLangCode(rawName, rawName)
            AudioTrackOption(
                groupIndex = 0,
                trackIndex = desc.id,
                languageCode = langCode,
                label = rawName.ifBlank { "Audio Track ${idx + 1}" },
                channels = activeChannels,
                bitrate = activeBitrate,
                selected = desc.id == currentAudioId
            )
        }
        val selectedLang = audioList.firstOrNull { it.selected }?.languageCode
        onAudioTracksUpdated(audioList, selectedLang)

        val currentSpuId = mediaPlayer.spuTrack
        selectedSubtitleTrackId = currentSpuId
        val rawSubTracks = mediaPlayer.spuTracks.orEmpty().filter { it.id >= 0 }
        val subList = rawSubTracks.mapIndexed { idx, desc ->
            val rawName = desc.name.orEmpty()
            val langCode = AudioTrackHelper.normalizeLangCode(rawName, rawName)
            SubtitleTrackOption(
                groupIndex = 0,
                trackIndex = desc.id,
                vlcTrackId = desc.id,
                languageCode = langCode,
                label = rawName.ifBlank { "Subtitle ${idx + 1}" },
                selected = desc.id == currentSpuId
            )
        }
        onSubtitleTracksUpdated(subList)

        val decoderModeLabel = when (playerLocalSettings.videoDecoderMode) {
            VideoDecoderMode.AUTO -> "LibVLC Auto Hardware"
            VideoDecoderMode.HARDWARE -> "LibVLC Hardware"
            VideoDecoderMode.SOFTWARE -> "LibVLC Software (avcodec)"
        }
        val hwSupportLabel = when (playerLocalSettings.videoDecoderMode) {
            VideoDecoderMode.SOFTWARE -> "No (Software mode selected)"
            else -> "Hardware (when supported by device)"
        }

        onDiagnosticsUpdated { current ->
            val vCodec = videoTrack?.codec?.takeIf { it.isNotBlank() } ?: current.videoCodec
            val aCodec = audioTrackInfo?.codec?.takeIf { it.isNotBlank() } ?: current.audioCodec
            val w = videoTrack?.width ?: 0
            val h = videoTrack?.height ?: 0
            val resLabel = if (w > 0 && h > 0) {
                val tier = VideoQualityHelper.tierForResolution(w, h)
                "$w × $h (${tier.label})"
            } else {
                current.videoResolution
            }
            val fpsVal = if (videoTrack != null && videoTrack.frameRateDen > 0) {
                videoTrack.frameRateNum.toFloat() / videoTrack.frameRateDen.toFloat()
            } else 0f
            val fpsLabel = if (fpsVal > 0f) {
                String.format(Locale.US, "%.1f fps", fpsVal).replace(".0 ", " ")
            } else {
                current.frameRate
            }
            val dropped = mediaStats?.lostPictures ?: current.droppedFrames
            current.copy(
                playerEngine = PlaybackDiagnosticsInfo.VLC_ENGINE_NAME,
                videoCodec = vCodec,
                audioCodec = aCodec,
                videoResolution = resLabel,
                frameRate = fpsLabel,
                decoderName = decoderModeLabel,
                hardwareDecodingSupported = hwSupportLabel,
                droppedFrames = dropped,
                videoSurfaceStatus = if (voutRendered) {
                    if (useTextureView) "LibVLC TextureView (Rendering)" else "LibVLC SurfaceView (Rendering)"
                } else {
                    if (useTextureView) "LibVLC TextureView (Initializing)" else "LibVLC SurfaceView (Initializing)"
                }
            )
        }
    }

    // Event listener registration
    DisposableEffect(mediaPlayer, activeUrl, retryToken) {
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    onBufferingChanged(true)
                }
                MediaPlayer.Event.Buffering -> {
                    onBufferingChanged(event.buffering < 100f)
                }
                MediaPlayer.Event.Playing -> {
                    onPlayingChanged(true)
                    onBufferingChanged(false)
                    onDiagnosticsUpdated {
                        it.copy(
                            playerEngine = PlaybackDiagnosticsInfo.VLC_ENGINE_NAME,
                            httpStatus = if (it.httpStatus == "Connecting...") "HTTP 200 (Streaming via LibVLC)" else it.httpStatus,
                            httpError = "None",
                            playbackErrorCode = "None",
                            underlyingError = "None"
                        )
                    }
                    refreshVlcTelemetry()
                }
                MediaPlayer.Event.Paused -> {
                    onPlayingChanged(false)
                }
                MediaPlayer.Event.Stopped -> {
                    onPlayingChanged(false)
                }
                MediaPlayer.Event.Vout -> {
                    if (event.voutCount > 0) {
                        voutRendered = true
                    }
                    refreshVlcTelemetry()
                }
                MediaPlayer.Event.ESAdded,
                MediaPlayer.Event.ESSelected,
                MediaPlayer.Event.ESDeleted -> {
                    refreshVlcTelemetry()
                }
                MediaPlayer.Event.TimeChanged -> {
                    onProgressTick(event.timeChanged.coerceAtLeast(0L), mediaPlayer.length.coerceAtLeast(0L))
                }
                MediaPlayer.Event.LengthChanged -> {
                    onProgressTick(mediaPlayer.time.coerceAtLeast(0L), event.lengthChanged.coerceAtLeast(0L))
                }
                MediaPlayer.Event.EndReached -> {
                    onPlayingChanged(false)
                    if (latestRequest.contentType == MediaContentType.EPISODE) {
                        onEpisodeEnded()
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    onPlayingChanged(false)
                    onBufferingChanged(false)
                    onPlaybackError("LibVLC encountered an error opening this stream.", false, false)
                }
            }
        }
        mediaPlayer.setEventListener(listener)
        onDispose {
            mediaPlayer.setEventListener(null)
        }
    }

    // Load media whenever URL, decoder mode, surface attachment, or retry changes
    LaunchedEffect(activeUrl, retryToken, playerLocalSettings.videoDecoderMode, attachedToSurface) {
        if (!attachedToSurface || activeUrl.isBlank()) return@LaunchedEffect
        voutRendered = false
        autoAppliedAudioForStream = false
        onBufferingChanged(true)
        onPlaybackError(null, false, false)
        onDiagnosticsUpdated {
            it.copy(
                playerEngine = PlaybackDiagnosticsInfo.VLC_ENGINE_NAME,
                videoResolution = "Detecting...",
                videoCodec = "Detecting...",
                audioCodec = "Detecting...",
                frameRate = "Detecting...",
                decoderName = when (playerLocalSettings.videoDecoderMode) {
                    VideoDecoderMode.AUTO -> "LibVLC Auto Hardware"
                    VideoDecoderMode.HARDWARE -> "LibVLC Hardware"
                    VideoDecoderMode.SOFTWARE -> "LibVLC Software"
                },
                hardwareDecodingSupported = when (playerLocalSettings.videoDecoderMode) {
                    VideoDecoderMode.SOFTWARE -> "No (Software mode)"
                    else -> "Hardware (when supported)"
                },
                httpStatus = "Connecting...",
                httpError = "None",
                playbackErrorCode = "None",
                underlyingError = "None",
                streamFormat = StreamUrlResolver.detectFormatLabel(activeUrl),
                redactedUrl = StreamUrlResolver.redactCredentials(activeUrl)
            )
        }

        // Probe HTTP status asynchronously so HTTP 401/403 shows accurately even before LibVLC finishes retrying
        val probe = withContext(Dispatchers.IO) {
            StreamDiagnostics.probeStream(activeUrl)
        }
        onDiagnosticsUpdated {
            it.copy(
                dnsStatus = probe.dnsStatus,
                httpStatus = probe.httpStatus,
                streamFormat = probe.detectedFormat
            )
        }
        if (probe.httpStatus.contains("401") || probe.httpStatus.contains("403")) {
            val is401 = probe.httpStatus.contains("401")
            val authMsg = if (is401) {
                "HTTP 401 Unauthorized — Stream server rejected credentials, session token expired, or maximum concurrent connections reached."
            } else {
                "HTTP 403 Forbidden — Provider denied stream access."
            }
            onDiagnosticsUpdated {
                it.copy(
                    httpError = probe.httpStatus,
                    playbackErrorCode = if (is401) "HTTP_401_UNAUTHORIZED" else "HTTP_403_FORBIDDEN",
                    underlyingError = authMsg
                )
            }
            onBufferingChanged(false)
            onPlaybackError(authMsg, true, false)
            return@LaunchedEffect
        }

        val media = Media(libVlc, Uri.parse(activeUrl))
        when (playerLocalSettings.videoDecoderMode) {
            VideoDecoderMode.AUTO -> media.setHWDecoderEnabled(true, false)
            VideoDecoderMode.HARDWARE -> media.setHWDecoderEnabled(true, true)
            VideoDecoderMode.SOFTWARE -> media.setHWDecoderEnabled(false, false)
        }
        media.addOption(":network-caching=3000")
        media.addOption(":clock-jitter=0")
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()

        if (request.contentType != MediaContentType.LIVE && request.resumePositionMs > 2000L) {
            delay(500L)
            if (mediaPlayer.isSeekable) {
                mediaPlayer.time = request.resumePositionMs
            }
        }
    }

    // Periodic progress & telemetry poll
    LaunchedEffect(mediaPlayer, request.itemId) {
        var tick = 0
        while (true) {
            if (mediaPlayer.isPlaying) {
                val pos = mediaPlayer.time.coerceAtLeast(0L)
                val dur = mediaPlayer.length.coerceAtLeast(0L)
                onProgressTick(pos, dur)
                refreshVlcTelemetry()
                tick++
                if (tick % 10 == 0) {
                    onSaveProgress(pos, dur)
                }
            }
            delay(1000L)
        }
    }

    DisposableEffect(libVlc, mediaPlayer) {
        onDispose {
            onControllerReady(null)
            runCatching {
                onSaveProgress(
                    mediaPlayer.time.coerceAtLeast(0L),
                    mediaPlayer.length.coerceAtLeast(0L)
                )
            }
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.detachViews() }
            runCatching { mediaPlayer.release() }
            runCatching { libVlc.release() }
        }
    }

    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.view_vlc_player, null, false) as VLCVideoLayout).apply {
                keepScreenOn = true
                mediaPlayer.attachViews(this, null, true, useTextureView)
                vlcLayoutRef = this
                attachedToSurface = true
            }
        },
        update = { layout ->
            if (vlcLayoutRef !== layout) {
                runCatching { mediaPlayer.detachViews() }
                mediaPlayer.attachViews(layout, null, true, useTextureView)
                vlcLayoutRef = layout
                attachedToSurface = true
            }
        },
        onRelease = { layout ->
            runCatching { mediaPlayer.detachViews() }
            if (vlcLayoutRef === layout) {
                vlcLayoutRef = null
                attachedToSurface = false
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
