package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.example.data.model.AudioTrackOption
import com.example.data.model.EpgProgram
import com.example.data.model.LiveChannel
import com.example.data.model.MediaContentType
import com.example.data.model.PlaybackRequest
import com.example.data.model.UserPreferencesDoc
import com.example.data.network.StreamUrlResolver
import com.example.player.AudioTrackHelper
import com.example.player.CodecCapabilityInspector
import com.example.player.ExoPlaybackController
import com.example.player.ExoPlayerHost
import com.example.player.ExternalPlayerHelper
import com.example.player.PlaybackDiagnosticsInfo
import com.example.player.PlayerLocalSettings
import com.example.player.StreamDiagnostics
import com.example.player.SubtitleTrackOption
import com.example.player.VideoPlayerEngine
import com.example.player.VideoQualityHelper
import com.example.player.VideoQualitySourceType
import com.example.player.VideoQualityTier
import com.example.player.VideoSurfaceMode
import com.example.player.VlcPlaybackController
import com.example.player.VlcPlayerHost
import com.example.ui.theme.UhBorder
import com.example.ui.theme.UhCrimson
import com.example.ui.theme.UhLiveGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    preferences: UserPreferencesDoc,
    playerLocalSettings: PlayerLocalSettings,
    epgPrograms: List<EpgProgram>,
    isFavourite: Boolean,
    onSwitchRequest: (PlaybackRequest) -> Unit,
    onUpdatePlayerSettings: (PlayerLocalSettings) -> Unit,
    onSaveProgress: (PlaybackRequest, Long, Long, String) -> Unit,
    onToggleFavourite: (PlaybackRequest) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val device4kProfile = remember(context) { CodecCapabilityInspector.inspectDevice4kCapabilities(context) }

    val candidateUrls = remember(request.streamUrl, request.itemId, request.candidateUrls) {
        StreamUrlResolver.deriveFallbackCandidates(
            primaryUrl = request.streamUrl,
            contentType = request.contentType,
            explicitCandidates = request.candidateUrls
        )
    }

    var candidateIndex by remember(request.streamUrl, request.itemId) { mutableIntStateOf(0) }
    val activeUrl = candidateUrls.getOrElse(candidateIndex) { request.streamUrl }

    var activeEngine by remember(playerLocalSettings.preferredPlayerEngine) {
        mutableStateOf(playerLocalSettings.preferredPlayerEngine)
    }
    var exoController by remember { mutableStateOf<ExoPlaybackController?>(null) }
    var vlcController by remember { mutableStateOf<VlcPlaybackController?>(null) }

    var isLandscape by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var showSwitchPlayerSheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isProbingDiagnostics by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }
    var currentPositionMs by remember { mutableLongStateOf(request.resumePositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var resizeModeName by remember(preferences.defaultPlayerResizeMode) {
        mutableStateOf(preferences.defaultPlayerResizeMode)
    }
    var externalLaunchMessage by remember(activeUrl, activeEngine) { mutableStateOf<String?>(null) }

    var autoFallbackToTextureView by remember(request.streamUrl, request.itemId) { mutableStateOf(false) }
    var videoSurfaceStallWarning by remember(request.streamUrl, request.itemId) { mutableStateOf(false) }

    val effectiveUseTextureView = when (playerLocalSettings.videoSurfaceMode) {
        VideoSurfaceMode.TEXTURE_VIEW -> true
        VideoSurfaceMode.SURFACE_VIEW -> false
        VideoSurfaceMode.AUTO -> autoFallbackToTextureView
    }

    var selectedQualityTier by remember(request.streamUrl, request.itemId, playerLocalSettings.defaultQualityTier) {
        mutableStateOf(
            VideoQualityHelper.detectChannelTierFromName(request.title)
                ?: playerLocalSettings.defaultQualityTier
        )
    }

    var diagnostics by remember(request.streamUrl, request.itemId) {
        mutableStateOf(
            PlaybackDiagnosticsInfo(
                playerEngine = when (activeEngine) {
                    VideoPlayerEngine.EXOPLAYER -> PlaybackDiagnosticsInfo.ENGINE_NAME
                    VideoPlayerEngine.VLC -> PlaybackDiagnosticsInfo.VLC_ENGINE_NAME
                    VideoPlayerEngine.EXTERNAL -> PlaybackDiagnosticsInfo.EXTERNAL_ENGINE_NAME
                },
                streamFormat = StreamUrlResolver.detectFormatLabel(activeUrl),
                dnsStatus = "Ready",
                httpStatus = "Connecting...",
                videoSurfaceStatus = if (effectiveUseTextureView) "Initializing TextureView..." else "Initializing SurfaceView...",
                redactedUrl = StreamUrlResolver.redactCredentials(activeUrl),
                device4kSummary = device4kProfile.compactSummaryText(),
                attemptIndex = 1,
                totalAttempts = candidateUrls.size.coerceAtLeast(1)
            )
        )
    }

    var audioOptions by remember(activeUrl, activeEngine) { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var subtitleOptions by remember(activeUrl, activeEngine) { mutableStateOf<List<SubtitleTrackOption>>(emptyList()) }
    var qualityOptions by remember(request.streamUrl, request.itemId, request.liveChannelQueue) {
        mutableStateOf(
            VideoQualityHelper.buildQualityOptions(
                tracks = Tracks.EMPTY,
                selectedTier = selectedQualityTier,
                currentChannelId = request.itemId,
                currentChannelTitle = request.title,
                liveChannelQueue = request.liveChannelQueue,
                deviceProfile = device4kProfile
            )
        )
    }
    var manualAudioOverride by remember(request.itemId, request.streamUrl) { mutableStateOf(false) }
    var activeAudioCode by remember(request.itemId) { mutableStateOf(request.preferredAudioLang) }

    fun runDiagnosticProbe(targetUrl: String) {
        scope.launch {
            isProbingDiagnostics = true
            val probe = StreamDiagnostics.probeStream(targetUrl)
            val httpErrText = if (probe.httpStatus.contains("401") || probe.httpStatus.contains("403")) {
                probe.httpStatus
            } else {
                diagnostics.httpError
            }
            diagnostics = diagnostics.copy(
                dnsStatus = probe.dnsStatus,
                httpStatus = probe.httpStatus,
                httpError = httpErrText,
                streamFormat = probe.detectedFormat,
                underlyingError = if (diagnostics.underlyingError == "None" && !probe.detailNote.isNullOrBlank()) {
                    probe.detailNote
                } else {
                    diagnostics.underlyingError
                }
            )
            isProbingDiagnostics = false
        }
    }

    fun switchToChannel(target: LiveChannel) {
        val nextCandidates = StreamUrlResolver.deriveFallbackCandidates(
            target.streamUrl,
            MediaContentType.LIVE,
            target.candidateUrls
        )
        onSwitchRequest(
            request.copy(
                itemId = target.id,
                title = target.name,
                subtitle = target.categoryName,
                posterUrl = target.logoUrl,
                streamUrl = nextCandidates.firstOrNull() ?: target.streamUrl,
                candidateUrls = nextCandidates
            )
        )
    }

    fun saveCurrentProgress() {
        val pos = when (activeEngine) {
            VideoPlayerEngine.EXOPLAYER -> exoController?.currentPositionMs() ?: currentPositionMs
            VideoPlayerEngine.VLC -> vlcController?.currentPositionMs() ?: currentPositionMs
            VideoPlayerEngine.EXTERNAL -> currentPositionMs
        }
        val dur = when (activeEngine) {
            VideoPlayerEngine.EXOPLAYER -> exoController?.currentDurationMs() ?: durationMs
            VideoPlayerEngine.VLC -> vlcController?.currentDurationMs() ?: durationMs
            VideoPlayerEngine.EXTERNAL -> durationMs
        }
        onSaveProgress(request, pos, dur, activeAudioCode)
    }

    // Launch external player automatically when EXTERNAL engine is active
    LaunchedEffect(activeEngine, activeUrl, retryToken) {
        if (activeEngine == VideoPlayerEngine.EXTERNAL) {
            isBuffering = false
            isPlaying = false
            playbackError = null
            diagnostics = diagnostics.copy(
                playerEngine = PlaybackDiagnosticsInfo.EXTERNAL_ENGINE_NAME,
                decoderName = "External App",
                videoSurfaceStatus = "Delegated to external app"
            )
            val res = ExternalPlayerHelper.launchExternalPlayer(context, activeUrl, request.title)
            externalLaunchMessage = res.message
            runDiagnosticProbe(activeUrl)
        }
    }

    // Orientation & immersive bars
    DisposableEffect(activity, isLandscape) {
        if (activity != null) {
            activity.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (activity != null) {
                val window = activity.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Auto-hide controls while actively playing
    LaunchedEffect(
        showControls,
        isPlaying,
        showAudioSheet,
        showSubtitleSheet,
        showQualitySheet,
        showDiagnosticsSheet,
        showSwitchPlayerSheet,
        playbackError,
        activeEngine
    ) {
        if (showControls &&
            isPlaying &&
            activeEngine != VideoPlayerEngine.EXTERNAL &&
            !showAudioSheet &&
            !showSubtitleSheet &&
            !showQualitySheet &&
            !showDiagnosticsSheet &&
            !showSwitchPlayerSheet &&
            playbackError == null
        ) {
            delay(4500L)
            showControls = false
        }
    }

    BackHandler {
        saveCurrentProgress()
        onBack()
    }

    val currentLiveIndex = remember(request) {
        request.liveChannelQueue.indexOfFirst { it.id == request.itemId }
    }
    val nextEpisodeRequest = remember(request) { findNextEpisode(request) }
    val fallbackLowerQualityOption = remember(qualityOptions) {
        qualityOptions.firstOrNull {
            it.available &&
                it.deviceSupported &&
                it.tier in listOf(VideoQualityTier.FULL_HD, VideoQualityTier.HD, VideoQualityTier.SD) &&
                (it.sourceType == VideoQualitySourceType.PROVIDER_CHANNEL || it.sourceType == VideoQualitySourceType.ABR_TRACK)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
            .testTag("player_root_box")
    ) {
        when (activeEngine) {
            VideoPlayerEngine.EXOPLAYER -> {
                ExoPlayerHost(
                    context = context,
                    activeUrl = activeUrl,
                    candidateIndex = candidateIndex,
                    candidateUrlsCount = candidateUrls.size,
                    request = request,
                    preferences = preferences,
                    playerLocalSettings = playerLocalSettings,
                    device4kProfile = device4kProfile,
                    selectedQualityTier = selectedQualityTier,
                    resizeModeName = resizeModeName,
                    retryToken = retryToken,
                    manualAudioOverride = manualAudioOverride,
                    useTextureViewOverride = effectiveUseTextureView,
                    onControllerReady = { exoController = it },
                    onPlayingChanged = { isPlaying = it },
                    onBufferingChanged = { isBuffering = it },
                    onProgressTick = { pos, dur ->
                        currentPositionMs = pos
                        if (dur > 0L) durationMs = dur
                    },
                    onEpisodeEnded = {
                        findNextEpisode(request)?.let(onSwitchRequest)
                    },
                    onAudioTracksUpdated = { tracks, selectedCode ->
                        audioOptions = tracks
                        if (!selectedCode.isNullOrBlank()) activeAudioCode = selectedCode
                    },
                    onSubtitleTracksUpdated = { subtitleOptions = it },
                    onQualityOptionsUpdated = { qualityOptions = it },
                    onDiagnosticsUpdated = { updater -> diagnostics = updater(diagnostics) },
                    onPlaybackError = { errorMsg, tryNextCandidate, probeUrl ->
                        if (tryNextCandidate && candidateIndex + 1 < candidateUrls.size) {
                            candidateIndex += 1
                        } else {
                            playbackError = errorMsg
                            if (!probeUrl.isNullOrBlank()) {
                                runDiagnosticProbe(probeUrl)
                            }
                        }
                    },
                    onSurfaceStallWarningChanged = { videoSurfaceStallWarning = it },
                    onAutoTextureFallbackTriggered = { autoFallbackToTextureView = true },
                    onSaveProgress = { pos, dur -> onSaveProgress(request, pos, dur, activeAudioCode) }
                )
            }

            VideoPlayerEngine.VLC -> {
                VlcPlayerHost(
                    context = context,
                    activeUrl = activeUrl,
                    request = request,
                    preferences = preferences,
                    playerLocalSettings = playerLocalSettings,
                    useTextureView = effectiveUseTextureView,
                    resizeModeName = resizeModeName,
                    retryToken = retryToken,
                    manualAudioOverride = manualAudioOverride,
                    onControllerReady = { vlcController = it },
                    onPlayingChanged = { isPlaying = it },
                    onBufferingChanged = { isBuffering = it },
                    onProgressTick = { pos, dur ->
                        currentPositionMs = pos
                        if (dur > 0L) durationMs = dur
                    },
                    onEpisodeEnded = {
                        findNextEpisode(request)?.let(onSwitchRequest)
                    },
                    onAudioTracksUpdated = { tracks, selectedCode ->
                        audioOptions = tracks
                        if (!selectedCode.isNullOrBlank()) activeAudioCode = selectedCode
                    },
                    onSubtitleTracksUpdated = { subtitleOptions = it },
                    onDiagnosticsUpdated = { updater -> diagnostics = updater(diagnostics) },
                    onPlaybackError = { errorMsg, _, _ ->
                        playbackError = errorMsg
                        if (errorMsg != null) {
                            runDiagnosticProbe(activeUrl)
                        }
                    },
                    onSaveProgress = { pos, dur -> onSaveProgress(request, pos, dur, activeAudioCode) }
                )
            }

            VideoPlayerEngine.EXTERNAL -> {
                Surface(
                    color = Color(0xEB141418),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 520.dp)
                        .padding(20.dp)
                        .border(1.dp, UhBorder, RoundedCornerShape(16.dp))
                        .testTag("external_player_panel")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "External Player Selected",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = externalLaunchMessage ?: "Opening stream in an external Android video player app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "HTTP Status: ${diagnostics.httpStatus} • Format: ${diagnostics.streamFormat}",
                            style = MaterialTheme.typography.labelMedium,
                            color = UhLiveGreen
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val res = ExternalPlayerHelper.launchExternalPlayer(context, activeUrl, request.title)
                                    externalLaunchMessage = res.message
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = UhCrimson),
                                modifier = Modifier.testTag("launch_external_again_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open External Player")
                            }
                            OutlinedButton(
                                onClick = { showSwitchPlayerSheet = true },
                                modifier = Modifier.testTag("external_switch_player_button")
                            ) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Switch Player", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        if (isBuffering && playbackError == null && activeEngine != VideoPlayerEngine.EXTERNAL) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = UhCrimson)
                if (candidateIndex > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Trying stream endpoint ${candidateIndex + 1}/${candidateUrls.size}: ${StreamUrlResolver.detectFormatLabel(activeUrl)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        if (videoSurfaceStallWarning && playbackError == null && activeEngine == VideoPlayerEngine.EXOPLAYER) {
            Surface(
                color = Color(0xE6141418),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 520.dp)
                    .border(1.dp, UhCrimson, RoundedCornerShape(14.dp))
                    .testTag("video_surface_stall_banner")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Audio Playing Without Video Frames",
                        style = MaterialTheme.typography.titleMedium,
                        color = UhCrimson,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "The decoder has not rendered frames to the surface. Try switching Video Surface (${if (effectiveUseTextureView) "SurfaceView" else "TextureView"}), Decoder Mode, or switching to VLC Player.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val nextSurface = if (effectiveUseTextureView) {
                                    VideoSurfaceMode.SURFACE_VIEW
                                } else {
                                    VideoSurfaceMode.TEXTURE_VIEW
                                }
                                onUpdatePlayerSettings(playerLocalSettings.copy(videoSurfaceMode = nextSurface))
                                videoSurfaceStallWarning = false
                            }
                        ) {
                            Text(if (effectiveUseTextureView) "Use SurfaceView" else "Use TextureView", color = Color.White)
                        }
                        Button(
                            onClick = { showSwitchPlayerSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = UhCrimson)
                        ) {
                            Text("Switch Player")
                        }
                    }
                }
            }
        }

        if (playerLocalSettings.showTechnicalPlaybackInfo && playbackError == null) {
            TechnicalInfoHudOverlay(
                diagnostics = diagnostics,
                playerLocalSettings = playerLocalSettings,
                showControls = showControls,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        if (playbackError != null) {
            PlaybackErrorCard(
                playbackError = playbackError!!,
                diagnostics = diagnostics,
                isProbingDiagnostics = isProbingDiagnostics,
                fallbackLowerQualityOption = fallbackLowerQualityOption,
                onRetry = {
                    playbackError = null
                    candidateIndex = 0
                    retryToken++
                },
                onSwitchPlayerClick = { showSwitchPlayerSheet = true },
                onSelectFallbackQuality = { opt ->
                    selectedQualityTier = opt.tier
                    val altCh = opt.alternativeChannel
                    if (altCh != null) {
                        switchToChannel(altCh)
                    } else {
                        playbackError = null
                        exoController?.applyQualityOption(opt)
                    }
                },
                onTestSample = onSwitchRequest,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.78f),
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.86f)
                            )
                        )
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                // Top Overlay Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                saveCurrentProgress()
                                onBack()
                            },
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = request.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val activeEpg = epgPrograms.firstOrNull { it.isLiveNow } ?: epgPrograms.firstOrNull()
                            val subText = if (request.contentType == MediaContentType.LIVE && activeEpg != null) {
                                "Now: ${activeEpg.title} (${activeEpg.startTimeLabel}–${activeEpg.endTimeLabel})"
                            } else {
                                request.subtitle
                            }
                            if (subText.isNotBlank()) {
                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clickable { showSwitchPlayerSheet = true }
                                .testTag("player_switch_engine_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = "Switch video player",
                                    tint = UhCrimson,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = when (activeEngine) {
                                        VideoPlayerEngine.EXOPLAYER -> "ExoPlayer"
                                        VideoPlayerEngine.VLC -> "VLC"
                                        VideoPlayerEngine.EXTERNAL -> "External"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clickable { showQualitySheet = true }
                                .testTag("player_quality_selector_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.HighQuality,
                                    contentDescription = "Select video quality or decoder",
                                    tint = UhCrimson,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = selectedQualityTier.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clickable { showAudioSheet = true }
                                .testTag("player_audio_selector_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Audiotrack,
                                    contentDescription = "Select audio language",
                                    tint = UhCrimson,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = AudioTrackHelper.languageDisplayName(activeAudioCode),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = { showSubtitleSheet = true },
                            modifier = Modifier.testTag("player_subtitle_selector_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ClosedCaption,
                                contentDescription = "Subtitles",
                                tint = if (subtitleOptions.any { it.selected }) UhCrimson else Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                showDiagnosticsSheet = true
                                runDiagnosticProbe(activeUrl)
                            },
                            modifier = Modifier.testTag("player_diagnostics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Playback diagnostics",
                                tint = Color.White
                            )
                        }

                        if (!request.isDiagnosticSample) {
                            IconButton(onClick = { onToggleFavourite(request) }) {
                                Icon(
                                    imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Toggle favourite",
                                    tint = if (isFavourite) UhCrimson else Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                resizeModeName = when (resizeModeName) {
                                    "FIT" -> "FILL"
                                    "FILL" -> "ZOOM"
                                    else -> "FIT"
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AspectRatio,
                                contentDescription = "Toggle aspect ratio",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { isLandscape = !isLandscape },
                            modifier = Modifier.testTag("player_fullscreen_toggle")
                        ) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = "Toggle orientation",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Center Playback Transport Controls
                if (playbackError == null && activeEngine != VideoPlayerEngine.EXTERNAL) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(26.dp)
                    ) {
                        if (request.contentType == MediaContentType.LIVE) {
                            val hasPrev = currentLiveIndex > 0
                            IconButton(
                                onClick = {
                                    if (hasPrev) switchToChannel(request.liveChannelQueue[currentLiveIndex - 1])
                                },
                                enabled = hasPrev,
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("player_prev_channel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Previous channel",
                                    tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    when (activeEngine) {
                                        VideoPlayerEngine.EXOPLAYER -> exoController?.seekRelativeMs(-10_000L, durationMs)
                                        VideoPlayerEngine.VLC -> vlcController?.seekRelativeMs(-10_000L, durationMs)
                                        VideoPlayerEngine.EXTERNAL -> Unit
                                    }
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = "Rewind 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                when (activeEngine) {
                                    VideoPlayerEngine.EXOPLAYER -> exoController?.togglePlayPause()
                                    VideoPlayerEngine.VLC -> vlcController?.togglePlayPause()
                                    VideoPlayerEngine.EXTERNAL -> Unit
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .background(UhCrimson, CircleShape)
                                .testTag("player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        if (request.contentType == MediaContentType.LIVE) {
                            val hasNext = currentLiveIndex in 0 until (request.liveChannelQueue.size - 1)
                            IconButton(
                                onClick = {
                                    if (hasNext) switchToChannel(request.liveChannelQueue[currentLiveIndex + 1])
                                },
                                enabled = hasNext,
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("player_next_channel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Next channel",
                                    tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    when (activeEngine) {
                                        VideoPlayerEngine.EXOPLAYER -> exoController?.seekRelativeMs(10_000L, durationMs)
                                        VideoPlayerEngine.VLC -> vlcController?.seekRelativeMs(10_000L, durationMs)
                                        VideoPlayerEngine.EXTERNAL -> Unit
                                    }
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = "Forward 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Bar: Seekbar or LIVE Badge + Next Episode button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    if (nextEpisodeRequest != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    saveCurrentProgress()
                                    onSwitchRequest(nextEpisodeRequest)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("player_next_episode_button")
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Next Episode", fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (request.contentType == MediaContentType.LIVE) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(color = UhLiveGreen, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = "${diagnostics.videoResolution} • ${diagnostics.streamFormat} • Audio: ${
                                    if (preferences.autoAudioEnabled) {
                                        preferences.audioPriority.joinToString(" → ") {
                                            AudioTrackHelper.languageDisplayName(it)
                                        }
                                    } else "Off"
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.82f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (activeEngine != VideoPlayerEngine.EXTERNAL) {
                        val safeDuration = durationMs.coerceAtLeast(1L)
                        Slider(
                            value = currentPositionMs.toFloat().coerceIn(0f, safeDuration.toFloat()),
                            onValueChange = { pos ->
                                currentPositionMs = pos.toLong()
                                when (activeEngine) {
                                    VideoPlayerEngine.EXOPLAYER -> exoController?.seekToMs(currentPositionMs)
                                    VideoPlayerEngine.VLC -> vlcController?.seekToMs(currentPositionMs)
                                    VideoPlayerEngine.EXTERNAL -> Unit
                                }
                            },
                            valueRange = 0f..safeDuration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = UhCrimson,
                                activeTrackColor = UhCrimson,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(currentPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Text(
                                text = "${diagnostics.videoResolution} • ${formatDuration(durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSwitchPlayerSheet) {
        PlayerEngineSwitchSheet(
            activeEngine = activeEngine,
            onSelectEngine = { chosen ->
                showSwitchPlayerSheet = false
                playbackError = null
                if (chosen == VideoPlayerEngine.EXTERNAL) {
                    activeEngine = VideoPlayerEngine.EXTERNAL
                } else {
                    activeEngine = chosen
                    onUpdatePlayerSettings(playerLocalSettings.copy(preferredPlayerEngine = chosen))
                }
            },
            onDismiss = { showSwitchPlayerSheet = false }
        )
    }

    if (showQualitySheet) {
        QualityAndDecoderSheet(
            qualityOptions = qualityOptions,
            playerLocalSettings = playerLocalSettings,
            onSelectQuality = { option ->
                selectedQualityTier = option.tier
                qualityOptions = qualityOptions.map { it.copy(selected = it.tier == option.tier) }
                val altCh = option.alternativeChannel
                showQualitySheet = false
                if (altCh != null) {
                    switchToChannel(altCh)
                } else {
                    exoController?.applyQualityOption(option)
                }
            },
            onSelectDecoderMode = { mode ->
                onUpdatePlayerSettings(playerLocalSettings.copy(videoDecoderMode = mode))
            },
            onSelectSurfaceMode = { sMode ->
                autoFallbackToTextureView = false
                onUpdatePlayerSettings(playerLocalSettings.copy(videoSurfaceMode = sMode))
            },
            onToggleTechnicalInfo = { show ->
                onUpdatePlayerSettings(playerLocalSettings.copy(showTechnicalPlaybackInfo = show))
            },
            onDismiss = { showQualitySheet = false }
        )
    }

    if (showAudioSheet) {
        AudioTrackSelectorSheet(
            preferences = preferences,
            audioOptions = audioOptions,
            onSelectAudio = { option ->
                manualAudioOverride = true
                activeAudioCode = option.languageCode
                when (activeEngine) {
                    VideoPlayerEngine.EXOPLAYER -> exoController?.selectAudioTrack(option)
                    VideoPlayerEngine.VLC -> vlcController?.selectAudioTrack(option.trackIndex)
                    VideoPlayerEngine.EXTERNAL -> Unit
                }
                audioOptions = audioOptions.map {
                    it.copy(selected = it.groupIndex == option.groupIndex && it.trackIndex == option.trackIndex)
                }
                showAudioSheet = false
            },
            onDismiss = { showAudioSheet = false }
        )
    }

    if (showSubtitleSheet) {
        SubtitleSelectorSheet(
            subtitleOptions = subtitleOptions,
            onSelectSubtitle = { option ->
                when (activeEngine) {
                    VideoPlayerEngine.EXOPLAYER -> exoController?.selectSubtitleTrack(option)
                    VideoPlayerEngine.VLC -> vlcController?.selectSubtitleTrack(option?.vlcTrackId ?: -1)
                    VideoPlayerEngine.EXTERNAL -> Unit
                }
                subtitleOptions = subtitleOptions.map {
                    it.copy(
                        selected = option != null &&
                            it.groupIndex == option.groupIndex &&
                            it.trackIndex == option.trackIndex &&
                            it.vlcTrackId == option.vlcTrackId
                    )
                }
                showSubtitleSheet = false
            },
            onDismiss = { showSubtitleSheet = false }
        )
    }

    if (showDiagnosticsSheet) {
        PlaybackDiagnosticsSheet(
            diagnostics = diagnostics,
            isProbingDiagnostics = isProbingDiagnostics,
            onProbeClick = { runDiagnosticProbe(activeUrl) },
            onTestSampleClick = { req ->
                showDiagnosticsSheet = false
                onSwitchRequest(req)
            },
            onDismiss = { showDiagnosticsSheet = false }
        )
    }
}

private fun findNextEpisode(request: PlaybackRequest): PlaybackRequest? {
    if (request.contentType != MediaContentType.EPISODE || request.episodeQueue.isEmpty()) return null
    val idx = request.episodeQueue.indexOfFirst { it.id == request.itemId }
    val next = if (idx >= 0 && idx + 1 < request.episodeQueue.size) request.episodeQueue[idx + 1] else return null
    val nextCandidates = StreamUrlResolver.deriveFallbackCandidates(
        primaryUrl = next.streamUrl,
        contentType = MediaContentType.EPISODE,
        explicitCandidates = next.candidateUrls
    )
    return request.copy(
        itemId = next.id,
        title = next.title,
        subtitle = "${next.seriesTitle} • S${next.seasonNumber} E${next.episodeNumber}",
        posterUrl = next.thumbnailUrl,
        streamUrl = nextCandidates.firstOrNull() ?: next.streamUrl,
        seasonNumber = next.seasonNumber,
        episodeNumber = next.episodeNumber,
        resumePositionMs = 0L,
        candidateUrls = nextCandidates
    )
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0L) "%d:%02d:%02d".format(hrs, mins, secs) else "%02d:%02d".format(mins, secs)
}
