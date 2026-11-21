package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioTrackOption
import com.example.data.model.MediaContentType
import com.example.data.model.PlaybackRequest
import com.example.data.model.UserPreferencesDoc
import com.example.player.AudioTrackHelper
import com.example.player.PlaybackDiagnosticsInfo
import com.example.player.PlayerLocalSettings
import com.example.player.PublicSampleStreams
import com.example.player.SubtitleTrackOption
import com.example.player.VideoDecoderMode
import com.example.player.VideoPlayerEngine
import com.example.player.VideoQualityOption
import com.example.player.VideoSurfaceMode
import com.example.ui.theme.UhBorder
import com.example.ui.theme.UhCrimson
import com.example.ui.theme.UhLiveGreen

@Composable
fun TechnicalInfoHudOverlay(
    diagnostics: PlaybackDiagnosticsInfo,
    playerLocalSettings: PlayerLocalSettings,
    showControls: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xBD0E0E12),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 14.dp, top = if (showControls) 76.dp else 14.dp, end = 14.dp)
            .border(1.dp, UhBorder, RoundedCornerShape(10.dp))
            .testTag("technical_playback_hud")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${playerLocalSettings.preferredPlayerEngine.label} • ${diagnostics.videoResolution} • ${diagnostics.frameRate}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = UhLiveGreen,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Codec: ${diagnostics.videoCodec} • Audio: ${diagnostics.audioCodec}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Decoder: ${diagnostics.decoderName} (${playerLocalSettings.videoDecoderMode.label}) • Dropped: ${diagnostics.droppedFrames}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val hasHttpErr = diagnostics.httpError != "None" && diagnostics.httpError.isNotBlank()
            Text(
                text = "Surface: ${diagnostics.videoSurfaceStatus} • HTTP: ${if (hasHttpErr) diagnostics.httpError else diagnostics.httpStatus}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (hasHttpErr) UhCrimson else Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaybackErrorCard(
    playbackError: String,
    diagnostics: PlaybackDiagnosticsInfo,
    isProbingDiagnostics: Boolean,
    fallbackLowerQualityOption: VideoQualityOption?,
    onRetry: () -> Unit,
    onSwitchPlayerClick: () -> Unit,
    onSelectFallbackQuality: (VideoQualityOption) -> Unit,
    onTestSample: (PlaybackRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xEB141418),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .widthIn(max = 580.dp)
            .padding(20.dp)
            .border(1.dp, UhBorder, RoundedCornerShape(16.dp))
            .testTag("playback_error_card")
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            val isAuth401 = diagnostics.playbackErrorCode.contains("401") ||
                diagnostics.httpError.contains("401") ||
                diagnostics.httpStatus.contains("401")
            val isAuth403 = diagnostics.playbackErrorCode.contains("403") ||
                diagnostics.httpError.contains("403") ||
                diagnostics.httpStatus.contains("403")
            val titleText = when {
                isAuth401 -> "Authentication Error (HTTP 401 Unauthorized)"
                isAuth403 -> "Access Denied (HTTP 403 Forbidden)"
                diagnostics.playbackErrorCode.contains("DECOD", ignoreCase = true) ||
                    diagnostics.playbackErrorCode.contains("FORMAT_UNSUPPORTED", ignoreCase = true) ->
                    "4K / Codec Compatibility Issue"
                else -> "Playback Error"
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = playbackError,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = UhBorder)
            Spacer(modifier = Modifier.height(10.dp))

            DiagnosticsFieldsBlock(
                diagnostics = diagnostics,
                isProbing = isProbingDiagnostics
            )

            Spacer(modifier = Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = UhCrimson),
                    modifier = Modifier.testTag("retry_stream_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
                OutlinedButton(
                    onClick = onSwitchPlayerClick,
                    modifier = Modifier.testTag("error_switch_player_button")
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Switch Player", color = Color.White)
                }
                if (fallbackLowerQualityOption != null && !isAuth401 && !isAuth403) {
                    Button(
                        onClick = { onSelectFallbackQuality(fallbackLowerQualityOption) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("Use ${fallbackLowerQualityOption.tier.label}", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val sample = PublicSampleStreams.samples.first()
                            onTestSample(
                                PlaybackRequest(
                                    itemId = sample.id,
                                    contentType = MediaContentType.MOVIE,
                                    title = sample.title,
                                    subtitle = sample.subtitle,
                                    posterUrl = "",
                                    streamUrl = sample.url,
                                    playlistId = "diagnostic_sample",
                                    candidateUrls = listOf(sample.url),
                                    isDiagnosticSample = true
                                )
                            )
                        },
                        modifier = Modifier.testTag("test_sample_stream_from_error_button")
                    ) {
                        Text("Test Sample", color = Color.White)
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerEngineSwitchSheet(
    activeEngine: VideoPlayerEngine,
    onSelectEngine: (VideoPlayerEngine) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Switch Video Player",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Switch between Android Media3 ExoPlayer 1.11.1, LibVLC 3.7.7, or an external player app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VideoPlayerEngine.entries.forEach { engine ->
                val selected = engine == activeEngine
                Surface(
                    color = if (selected) UhCrimson.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectEngine(engine) }
                        .testTag("switch_player_option_${engine.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = engine.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = engine.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Active player engine",
                                tint = UhCrimson
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSelectorSheet(
    subtitleOptions: List<SubtitleTrackOption>,
    onSelectSubtitle: (SubtitleTrackOption?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Select an embedded subtitle track or turn subtitles off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            val noneSelected = subtitleOptions.none { it.selected }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 18.dp)
            ) {
                item(key = "subtitle_off") {
                    Surface(
                        color = if (noneSelected) UhCrimson.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSubtitle(null) }
                            .testTag("subtitle_track_off")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Off",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (noneSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Subtitles disabled",
                                    tint = UhCrimson
                                )
                            }
                        }
                    }
                }
                if (subtitleOptions.isEmpty()) {
                    item(key = "subtitle_empty") {
                        Text(
                            text = "No embedded subtitle tracks detected in this stream.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(subtitleOptions, key = { "${it.groupIndex}_${it.trackIndex}_${it.vlcTrackId}" }) { option ->
                        Surface(
                            color = if (option.selected) UhCrimson.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSubtitle(option) }
                                .testTag("subtitle_track_option_${option.languageCode}_${option.trackIndex}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (option.selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected subtitle track",
                                        tint = UhCrimson
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QualityAndDecoderSheet(
    qualityOptions: List<VideoQualityOption>,
    playerLocalSettings: PlayerLocalSettings,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onSelectDecoderMode: (VideoDecoderMode) -> Unit,
    onSelectSurfaceMode: (VideoSurfaceMode) -> Unit,
    onToggleTechnicalInfo: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Video Quality & Decoder",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Only qualities available from this stream are shown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualityOptions.forEach { option ->
                    val enabled = option.available && option.deviceSupported
                    Surface(
                        color = when {
                            option.selected -> UhCrimson.copy(alpha = 0.18f)
                            enabled -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { onSelectQuality(option) }
                            .testTag("video_quality_option_${option.tier.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (enabled) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                                Text(
                                    text = option.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (option.selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected video quality",
                                    tint = UhCrimson
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = UhBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Video Decoder",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VideoDecoderMode.entries.forEach { mode ->
                    FilterChip(
                        selected = playerLocalSettings.videoDecoderMode == mode,
                        onClick = { onSelectDecoderMode(mode) },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UhCrimson,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("player_decoder_${mode.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Rendering Surface",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VideoSurfaceMode.entries.forEach { sMode ->
                    FilterChip(
                        selected = playerLocalSettings.videoSurfaceMode == sMode,
                        onClick = { onSelectSurfaceMode(sMode) },
                        label = { Text(sMode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UhCrimson,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playback Information Overlay",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = playerLocalSettings.showTechnicalPlaybackInfo,
                    onCheckedChange = onToggleTechnicalInfo,
                    colors = SwitchDefaults.colors(checkedTrackColor = UhCrimson),
                    modifier = Modifier.testTag("player_technical_info_switch")
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackSelectorSheet(
    preferences: UserPreferencesDoc,
    audioOptions: List<AudioTrackOption>,
    onSelectAudio: (AudioTrackOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.audio_language_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Auto priority: ${
                    preferences.audioPriority.joinToString(" -> ") {
                        AudioTrackHelper.languageDisplayName(it)
                    }
                }",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (audioOptions.isEmpty()) {
                Text(
                    text = "No alternate audio tracks detected in this stream.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 18.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 18.dp)
                ) {
                    items(audioOptions, key = { "${it.groupIndex}_${it.trackIndex}" }) { option ->
                        Surface(
                            color = if (option.selected) UhCrimson.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAudio(option) }
                                .testTag("audio_track_option_${option.languageCode}_${option.trackIndex}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (option.bitrate > 0) {
                                        Text(
                                            text = "${option.bitrate / 1000} kbps",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (option.selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected audio track",
                                        tint = UhCrimson
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackDiagnosticsSheet(
    diagnostics: PlaybackDiagnosticsInfo,
    isProbingDiagnostics: Boolean,
    onProbeClick: () -> Unit,
    onTestSampleClick: (PlaybackRequest) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Playback & 4K Diagnostics",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "IPTV credentials are masked for security.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            DiagnosticsFieldsBlock(
                diagnostics = diagnostics,
                isProbing = isProbingDiagnostics
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onProbeClick,
                    enabled = !isProbingDiagnostics,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isProbingDiagnostics) "Probing..." else "Re-run Probe")
                }
                Button(
                    onClick = {
                        val sample = PublicSampleStreams.samples.first()
                        onTestSampleClick(
                            PlaybackRequest(
                                itemId = sample.id,
                                contentType = MediaContentType.MOVIE,
                                title = sample.title,
                                subtitle = sample.subtitle,
                                posterUrl = "",
                                streamUrl = sample.url,
                                playlistId = "diagnostic_sample",
                                candidateUrls = listOf(sample.url),
                                isDiagnosticSample = true
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UhCrimson),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Sample Stream")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DiagnosticsFieldsBlock(
    diagnostics: PlaybackDiagnosticsInfo,
    isProbing: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        DiagnosticRow(label = "Player Engine:", value = diagnostics.playerEngine)
        DiagnosticRow(label = "Video Codec:", value = diagnostics.videoCodec)
        DiagnosticRow(label = "Audio Codec:", value = diagnostics.audioCodec)
        DiagnosticRow(label = "Resolution:", value = diagnostics.videoResolution)
        DiagnosticRow(label = "Frame Rate:", value = diagnostics.frameRate)
        DiagnosticRow(label = "Decoder:", value = diagnostics.decoderName)
        DiagnosticRow(label = "Hardware Decoder Supported:", value = diagnostics.hardwareDecodingSupported)
        DiagnosticRow(label = "Dropped Frames:", value = diagnostics.droppedFrames.toString())
        DiagnosticRow(label = "HTTP Errors:", value = diagnostics.httpError)
        DiagnosticRow(label = "Playback Error Code:", value = diagnostics.playbackErrorCode)
        DiagnosticRow(label = "Video Surface:", value = diagnostics.videoSurfaceStatus)
        DiagnosticRow(label = "HDR & Display Support:", value = diagnostics.hdrFormat)
        DiagnosticRow(
            label = "Stream Format:",
            value = "${diagnostics.streamFormat} (Attempt ${diagnostics.attemptIndex}/${diagnostics.totalAttempts})"
        )
        DiagnosticRow(
            label = "HTTP Status:",
            value = if (isProbing) "Checking HTTP..." else diagnostics.httpStatus
        )
        DiagnosticRow(
            label = "DNS Status:",
            value = if (isProbing) "Checking DNS..." else diagnostics.dnsStatus
        )
        if (diagnostics.device4kSummary.isNotBlank()) {
            DiagnosticRow(label = "Device 4K Capability:", value = diagnostics.device4kSummary)
        }
        DiagnosticRow(label = "Underlying Error:", value = diagnostics.underlyingError)
        if (diagnostics.redactedUrl.isNotBlank()) {
            DiagnosticRow(label = "Redacted URL:", value = diagnostics.redactedUrl)
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = UhCrimson,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White
        )
    }
}
