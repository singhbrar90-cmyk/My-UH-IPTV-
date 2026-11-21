package com.example.player

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.AudioTrackOption
import com.example.data.model.MediaContentType
import com.example.data.model.PlaybackRequest
import com.example.data.model.UserPreferencesDoc
import com.example.data.network.NetworkClientProvider
import com.example.data.network.StreamUrlResolver
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(UnstableApi::class)
class ExoPlaybackController(
    val exoPlayer: ExoPlayer
) {
    private var lastTracks: Tracks = Tracks.EMPTY

    fun updateTracks(tracks: Tracks) {
        lastTracks = tracks
    }

    fun currentTracks(): Tracks = lastTracks

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekToMs(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekRelativeMs(deltaMs: Long, durationMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
        exoPlayer.seekTo(if (durationMs > 0L) target.coerceAtMost(durationMs) else target)
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        AudioTrackHelper.selectManualTrack(exoPlayer, lastTracks, option)
    }

    fun selectSubtitleTrack(option: SubtitleTrackOption?) {
        SubtitleTrackHelper.selectSubtitleTrack(exoPlayer, lastTracks, option)
    }

    fun applyQualityOption(option: VideoQualityOption) {
        VideoQualityHelper.applyAbrQualitySelection(exoPlayer, lastTracks, option)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun currentPositionMs(): Long = exoPlayer.currentPosition.coerceAtLeast(0L)

    fun currentDurationMs(): Long = exoPlayer.duration.coerceAtLeast(0L)

    fun isPlaying(): Boolean = exoPlayer.isPlaying
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerHost(
    context: Context,
    activeUrl: String,
    candidateIndex: Int,
    candidateUrlsCount: Int,
    request: PlaybackRequest,
    preferences: UserPreferencesDoc,
    playerLocalSettings: PlayerLocalSettings,
    device4kProfile: Device4kProfile,
    selectedQualityTier: VideoQualityTier,
    resizeModeName: String,
    retryToken: Int,
    manualAudioOverride: Boolean,
    useTextureViewOverride: Boolean,
    onControllerReady: (ExoPlaybackController?) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onProgressTick: (positionMs: Long, durationMs: Long) -> Unit,
    onEpisodeEnded: () -> Unit,
    onAudioTracksUpdated: (List<AudioTrackOption>, String?) -> Unit,
    onSubtitleTracksUpdated: (List<SubtitleTrackOption>) -> Unit,
    onQualityOptionsUpdated: (List<VideoQualityOption>) -> Unit,
    onDiagnosticsUpdated: ((PlaybackDiagnosticsInfo) -> PlaybackDiagnosticsInfo) -> Unit,
    onPlaybackError: (errorMessage: String?, tryNextCandidate: Boolean, probeUrl: String?) -> Unit,
    onSurfaceStallWarningChanged: (Boolean) -> Unit,
    onAutoTextureFallbackTriggered: () -> Unit,
    onSaveProgress: (Long, Long) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestPreferences by rememberUpdatedState(preferences)
    val latestManualAudioOverride by rememberUpdatedState(manualAudioOverride)
    val latestRequest by rememberUpdatedState(request)
    val latestQualityTier by rememberUpdatedState(selectedQualityTier)

    var observedVideoMime by remember(activeUrl, request.itemId) { mutableStateOf<String?>(null) }
    var observedVideoWidth by remember(activeUrl, request.itemId) { mutableIntStateOf(0) }
    var observedVideoHeight by remember(activeUrl, request.itemId) { mutableIntStateOf(0) }
    var renderedFirstFrame by remember(activeUrl, request.itemId) { mutableStateOf(false) }
    var droppedFramesCount by remember(activeUrl, request.itemId) { mutableIntStateOf(0) }
    var hasLocalPlayingState by remember(activeUrl, request.itemId) { mutableStateOf(true) }
    var hasLocalErrorState by remember(activeUrl, request.itemId) { mutableStateOf(false) }
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val uhCodecSelector = remember { UhMediaCodecSelector(playerLocalSettings.videoDecoderMode) }
    var lastAppliedDecoderMode by remember { mutableStateOf(playerLocalSettings.videoDecoderMode) }

    val trackSelector = remember(context) {
        DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setViewportSizeToPhysicalDisplaySize(context, true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setExceedVideoConstraintsIfNecessary(false)
                    .setExceedRendererCapabilitiesIfNecessary(false)
            )
        }
    }

    val exoPlayer = remember(context) {
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkClientProvider.streamOkHttpClient)
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(2))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(25_000, 60_000, 4_000, 6_000)
            .setTargetBufferBytes(64 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setMediaCodecSelector(uhCodecSelector)
            setEnableDecoderFallback(true)
            forceEnableMediaCodecAsynchronousQueueing()
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    val controller = remember(exoPlayer) { ExoPlaybackController(exoPlayer) }

    LaunchedEffect(controller) {
        onControllerReady(controller)
    }

    LaunchedEffect(playerLocalSettings.videoDecoderMode) {
        uhCodecSelector.decoderMode = playerLocalSettings.videoDecoderMode
        if (lastAppliedDecoderMode != playerLocalSettings.videoDecoderMode) {
            lastAppliedDecoderMode = playerLocalSettings.videoDecoderMode
            renderedFirstFrame = false
            onSurfaceStallWarningChanged(false)
            val savedPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            exoPlayer.stop()
            if (request.contentType != MediaContentType.LIVE && savedPos > 2000L) {
                exoPlayer.seekTo(savedPos)
            }
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    LaunchedEffect(playerLocalSettings.defaultQualityTier, activeUrl) {
        if (VideoQualityHelper.detectChannelTierFromName(request.title) == null) {
            VideoQualityHelper.applyPreferredQualityConstraint(exoPlayer, playerLocalSettings.defaultQualityTier)
        }
    }

    DisposableEffect(lifecycleOwner, playerViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> playerViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> playerViewRef?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Watchdog: detect if audio plays while 0 video frames render
    LaunchedEffect(activeUrl, retryToken, useTextureViewOverride, hasLocalPlayingState, renderedFirstFrame, hasLocalErrorState) {
        if (hasLocalPlayingState && !renderedFirstFrame && !hasLocalErrorState) {
            delay(5000L)
            val hasSelectedVideo = currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            if (hasLocalPlayingState && !renderedFirstFrame && hasSelectedVideo && !hasLocalErrorState) {
                if (playerLocalSettings.videoSurfaceMode == VideoSurfaceMode.AUTO && !useTextureViewOverride) {
                    onAutoTextureFallbackTriggered()
                    onDiagnosticsUpdated {
                        it.copy(videoSurfaceStatus = "Switched to TextureView (SurfaceView rendered 0 frames)")
                    }
                } else {
                    onSurfaceStallWarningChanged(true)
                    onDiagnosticsUpdated {
                        it.copy(
                            videoSurfaceStatus = "${if (useTextureViewOverride) "TextureView" else "SurfaceView"} (No frames rendered while audio plays)"
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(activeUrl, request.itemId, retryToken) {
        onBufferingChanged(true)
        onSurfaceStallWarningChanged(false)
        hasLocalErrorState = false
        observedVideoMime = null
        observedVideoWidth = 0
        observedVideoHeight = 0
        renderedFirstFrame = false
        droppedFramesCount = 0

        onDiagnosticsUpdated {
            it.copy(
                playerEngine = PlaybackDiagnosticsInfo.ENGINE_NAME,
                videoResolution = "Detecting...",
                videoCodec = "Detecting...",
                audioCodec = "Detecting...",
                frameRate = "Detecting...",
                droppedFrames = 0,
                hdrFormat = "SDR / Checking...",
                videoSurfaceStatus = if (useTextureViewOverride) "TextureView Ready" else "SurfaceView Ready",
                streamFormat = StreamUrlResolver.detectFormatLabel(activeUrl),
                decoderName = "Initializing...",
                hardwareDecodingSupported = "Checking...",
                dnsStatus = "Resolving...",
                httpStatus = "Connecting...",
                httpError = "None",
                playbackErrorCode = "None",
                underlyingError = "None",
                redactedUrl = StreamUrlResolver.redactCredentials(activeUrl),
                device4kSummary = device4kProfile.compactSummaryText(),
                attemptIndex = candidateIndex + 1,
                totalAttempts = candidateUrlsCount.coerceAtLeast(1)
            )
        }

        AudioTrackHelper.applyLanguagePriorityParameters(
            player = exoPlayer,
            priority = preferences.audioPriority,
            autoEnabled = preferences.autoAudioEnabled
        )

        val itemBuilder = MediaItem.Builder().setUri(activeUrl)
        StreamDiagnostics.mimeTypeForUrl(activeUrl)?.let { mime ->
            itemBuilder.setMimeType(mime)
        }
        exoPlayer.setMediaItem(itemBuilder.build())
        if (request.contentType != MediaContentType.LIVE && request.resumePositionMs > 2000L && candidateIndex == 0) {
            exoPlayer.seekTo(request.resumePositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer, activeUrl, candidateIndex, useTextureViewOverride) {
        val analyticsListener = object : AnalyticsListener {
            override fun onRenderedFirstFrame(
                eventTime: AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long
            ) {
                renderedFirstFrame = true
                onSurfaceStallWarningChanged(false)
                val surfaceKind = if (useTextureViewOverride) "TextureView" else "SurfaceView"
                onDiagnosticsUpdated { it.copy(videoSurfaceStatus = "$surfaceKind (Frames rendering)") }
            }

            override fun onSurfaceSizeChanged(
                eventTime: AnalyticsListener.EventTime,
                width: Int,
                height: Int
            ) {
                if (width > 0 && height > 0) {
                    val surfaceKind = if (useTextureViewOverride) "TextureView" else "SurfaceView"
                    val stateSuffix = if (renderedFirstFrame) "Frames rendering" else "Waiting for first frame"
                    onDiagnosticsUpdated {
                        it.copy(videoSurfaceStatus = "$surfaceKind ${width}×${height} ($stateSuffix)")
                    }
                }
            }

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                droppedFramesCount += droppedFrames
                onDiagnosticsUpdated { it.copy(droppedFrames = droppedFramesCount) }
            }

            override fun onVideoCodecError(
                eventTime: AnalyticsListener.EventTime,
                videoCodecError: Exception
            ) {
                val redacted = StreamUrlResolver.redactCredentials(
                    videoCodecError.localizedMessage ?: videoCodecError.javaClass.simpleName
                )
                onDiagnosticsUpdated {
                    it.copy(playbackErrorCode = "VIDEO_CODEC_ERROR", underlyingError = redacted)
                }
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                val isSoftware = CodecCapabilityInspector.isSoftwareDecoderName(decoderName)
                val codecSummary = device4kProfile.summaryForMime(observedVideoMime)
                val hwLabel = when {
                    !isSoftware -> "Yes (Hardware active: $decoderName)"
                    codecSummary?.hardwareSupported == true -> "Yes (HW available; Software active: $decoderName)"
                    else -> "No (Software decoder active)"
                }
                onDiagnosticsUpdated {
                    it.copy(decoderName = decoderName, hardwareDecodingSupported = hwLabel)
                }
            }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                observedVideoMime = format.sampleMimeType
                if (format.width > 0 && format.height > 0) {
                    observedVideoWidth = format.width
                    observedVideoHeight = format.height
                }
                val fpsText = if (format.frameRate > 0f) {
                    String.format(Locale.US, "%.1f fps", format.frameRate).replace(".0 ", " ")
                } else {
                    "Stream default"
                }
                val resText = if (format.width > 0 && format.height > 0) {
                    val tier = VideoQualityHelper.tierForResolution(format.width, format.height)
                    "${format.width} × ${format.height} (${tier.label})"
                } else {
                    null
                }
                val bitrateText = if (format.bitrate > 0) {
                    String.format(Locale.US, " • %.1f Mbps", format.bitrate / 1_000_000.0)
                } else {
                    ""
                }
                val hdrLabel = CodecCapabilityInspector.hdrFormatLabel(format.colorInfo, format.sampleMimeType)
                val hdrSuffix = if (hdrLabel != "SDR") " • $hdrLabel" else ""
                val codecText = CodecCapabilityInspector.codecDisplayName(format.sampleMimeType, format.codecs) + bitrateText + hdrSuffix
                val codecSummary = device4kProfile.summaryForMime(format.sampleMimeType)
                val is4k = format.width >= 3800 || format.height >= 2100
                val hwCapText = when {
                    codecSummary == null -> null
                    is4k && codecSummary.hardwareSupported && codecSummary.supports4k ->
                        "Yes (4K ${codecSummary.displayName} HW: ${codecSummary.decoderName})"
                    is4k && !codecSummary.supports4k ->
                        "No (Max supported: ${codecSummary.maxWidth}×${codecSummary.maxHeight})"
                    codecSummary.hardwareSupported -> "Yes (${codecSummary.decoderName})"
                    else -> "No (Software only)"
                }
                onDiagnosticsUpdated { cur ->
                    cur.copy(
                        videoResolution = resText ?: cur.videoResolution,
                        frameRate = fpsText,
                        hdrFormat = if (device4kProfile.supportedDisplayHdrTypes.isNotEmpty()) {
                            "$hdrLabel (Display: ${device4kProfile.supportedDisplayHdrTypes.joinToString(", ")})"
                        } else {
                            "$hdrLabel (SDR Display)"
                        },
                        videoCodec = codecText,
                        decoderName = if (cur.decoderName == "Initializing...") {
                            codecSummary?.decoderName ?: cur.decoderName
                        } else cur.decoderName,
                        hardwareDecodingSupported = hwCapText ?: cur.hardwareDecodingSupported
                    )
                }
            }

            override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    observedVideoWidth = videoSize.width
                    observedVideoHeight = videoSize.height
                    val tier = VideoQualityHelper.tierForResolution(videoSize.width, videoSize.height)
                    onDiagnosticsUpdated {
                        it.copy(videoResolution = "${videoSize.width} × ${videoSize.height} (${tier.label})")
                    }
                }
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                val chLabel = when (format.channelCount) {
                    6 -> " • 5.1"
                    8 -> " • 7.1"
                    2 -> " • Stereo"
                    1 -> " • Mono"
                    else -> ""
                }
                onDiagnosticsUpdated {
                    it.copy(audioCodec = CodecCapabilityInspector.codecDisplayName(format.sampleMimeType, format.codecs) + chLabel)
                }
            }
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                hasLocalPlayingState = playing
                onPlayingChanged(playing)
                if (playing) {
                    onDiagnosticsUpdated {
                        it.copy(
                            dnsStatus = if (it.dnsStatus == "Resolving...") "Resolved" else it.dnsStatus,
                            httpStatus = if (it.httpStatus == "Connecting...") "HTTP 200 (Streaming)" else it.httpStatus,
                            httpError = "None",
                            playbackErrorCode = "None",
                            underlyingError = "None"
                        )
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                onBufferingChanged(playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    onProgressTick(exoPlayer.currentPosition.coerceAtLeast(0L), exoPlayer.duration.coerceAtLeast(0L))
                    onDiagnosticsUpdated {
                        it.copy(
                            dnsStatus = if (it.dnsStatus == "Resolving...") "Resolved" else it.dnsStatus,
                            httpStatus = if (it.httpStatus == "Connecting...") "HTTP 200 (Ready)" else it.httpStatus
                        )
                    }
                } else if (playbackState == Player.STATE_ENDED && latestRequest.contentType == MediaContentType.EPISODE) {
                    onEpisodeEnded()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
                controller.updateTracks(tracks)

                val updatedAudio = AudioTrackHelper.extractAndAutoSelectAudioTracks(
                    player = exoPlayer,
                    tracks = tracks,
                    priority = latestPreferences.audioPriority,
                    autoEnabled = latestPreferences.autoAudioEnabled,
                    manualOverrideActive = latestManualAudioOverride,
                    sessionPreferredLang = latestRequest.preferredAudioLang
                )
                onAudioTracksUpdated(updatedAudio, updatedAudio.firstOrNull { it.selected }?.languageCode)

                val updatedSubtitles = SubtitleTrackHelper.extractSubtitleTracks(tracks)
                onSubtitleTracksUpdated(updatedSubtitles)

                onQualityOptionsUpdated(
                    VideoQualityHelper.buildQualityOptions(
                        tracks = tracks,
                        selectedTier = latestQualityTier,
                        currentChannelId = latestRequest.itemId,
                        currentChannelTitle = latestRequest.title,
                        liveChannelQueue = latestRequest.liveChannelQueue,
                        deviceProfile = device4kProfile
                    )
                )

                val hasSelectedVideo = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                if (!hasSelectedVideo) {
                    val unsupported = VideoQualityHelper.inspectUnsupportedVideoTrack(tracks)
                    if (unsupported != null) {
                        observedVideoMime = unsupported.codecMime
                        observedVideoWidth = unsupported.width
                        observedVideoHeight = unsupported.height
                        val compatMsg = CodecCapabilityInspector.buildCompatibilityMessage(
                            mimeType = unsupported.codecMime,
                            streamWidth = unsupported.width,
                            streamHeight = unsupported.height,
                            profile = device4kProfile
                        ) ?: unsupported.reason
                        val summary = device4kProfile.summaryForMime(unsupported.codecMime)
                        onDiagnosticsUpdated {
                            it.copy(
                                videoResolution = if (unsupported.width > 0) "${unsupported.width} × ${unsupported.height} (Unsupported)" else "Unsupported",
                                videoCodec = CodecCapabilityInspector.codecDisplayName(unsupported.codecMime),
                                decoderName = summary?.decoderName ?: "Unavailable",
                                hardwareDecodingSupported = "No (${unsupported.reason})",
                                playbackErrorCode = "FORMAT_UNSUPPORTED_ON_DEVICE",
                                underlyingError = compatMsg
                            )
                        }
                        hasLocalErrorState = true
                        onBufferingChanged(false)
                        onPlaybackError(compatMsg, false, null)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val (codeLabel, detailMsg) = StreamDiagnostics.extractErrorDetails(
                    error = error,
                    videoMimeHint = observedVideoMime,
                    streamWidth = observedVideoWidth,
                    streamHeight = observedVideoHeight,
                    device4kProfile = device4kProfile
                )
                val httpStatus = StreamDiagnostics.extractHttpStatusFromException(error)
                val httpErrorLabel = StreamDiagnostics.extractHttpErrorLabel(error)
                val isDnsError = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED &&
                    detailMsg.contains("DNS resolution failed", ignoreCase = true)

                // HTTP 401/403 is never a container/decoder error: surface auth failure immediately.
                if (!StreamDiagnostics.isHttpAuthError(error) &&
                    StreamDiagnostics.isEndpointFormatError(error) &&
                    candidateIndex + 1 < candidateUrlsCount
                ) {
                    onPlaybackError(null, true, null)
                    return
                }

                hasLocalErrorState = true
                onBufferingChanged(false)
                val isDecoderErr = StreamDiagnostics.isDecoderOrCodecError(error)
                onDiagnosticsUpdated { cur ->
                    cur.copy(
                        dnsStatus = if (isDnsError) "Failed" else cur.dnsStatus,
                        httpStatus = when {
                            httpStatus != null -> httpStatus
                            isDecoderErr && cur.httpStatus == "Connecting..." -> "HTTP 200 (Stream decode error)"
                            else -> cur.httpStatus
                        },
                        httpError = httpErrorLabel,
                        hardwareDecodingSupported = if (isDecoderErr) "No (Codec/Resolution unsupported)" else cur.hardwareDecodingSupported,
                        playbackErrorCode = codeLabel,
                        underlyingError = detailMsg,
                        redactedUrl = StreamUrlResolver.redactCredentials(activeUrl),
                        attemptIndex = candidateIndex + 1,
                        totalAttempts = candidateUrlsCount.coerceAtLeast(1)
                    )
                }
                onPlaybackError(detailMsg, false, activeUrl)
            }
        }

        exoPlayer.addAnalyticsListener(analyticsListener)
        exoPlayer.addListener(listener)
        onDispose {
            onSaveProgress(exoPlayer.currentPosition.coerceAtLeast(0L), exoPlayer.duration.coerceAtLeast(0L))
            exoPlayer.removeListener(listener)
            exoPlayer.removeAnalyticsListener(analyticsListener)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            onControllerReady(null)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, request.itemId) {
        var tick = 0
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            onProgressTick(pos, dur)
            tick++
            if (tick % 10 == 0 && exoPlayer.isPlaying) {
                onSaveProgress(pos, dur)
            }
            delay(1000L)
        }
    }

    val resizeMode = when (resizeModeName) {
        "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "ZOOM" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    key(useTextureViewOverride) {
        AndroidView(
            factory = { ctx ->
                val layoutRes = if (useTextureViewOverride) {
                    R.layout.view_player_texture
                } else {
                    R.layout.view_player_surface
                }
                (LayoutInflater.from(ctx).inflate(layoutRes, null, false) as PlayerView).apply {
                    if (!useTextureViewOverride) {
                        setEnableComposeSurfaceSyncWorkaround(true)
                    }
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    this.resizeMode = resizeMode
                    playerViewRef = this
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
                playerView.resizeMode = resizeMode
                playerViewRef = playerView
            },
            onRelease = { playerView ->
                playerView.player = null
                if (playerViewRef === playerView) {
                    playerViewRef = null
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
