package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UserPreferencesDoc
import com.example.player.AudioTrackHelper
import com.example.player.CodecCapabilityInspector
import com.example.player.PlayerLocalSettings
import com.example.player.PublicSampleStream
import com.example.player.PublicSampleStreams
import com.example.player.VideoDecoderMode
import com.example.player.VideoPlayerEngine
import com.example.player.VideoQualityTier
import com.example.player.VideoSurfaceMode
import com.example.ui.theme.UhBorder
import com.example.ui.theme.UhCrimson

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPlayerPreferencesCard(
    preferences: UserPreferencesDoc,
    playerLocalSettings: PlayerLocalSettings,
    onSavePreferences: (List<String>, Boolean, Boolean, String) -> Unit,
    onUpdatePlayerLocalSettings: (PlayerLocalSettings) -> Unit,
    onPlaySampleStream: (PublicSampleStream) -> Unit
) {
    val context = LocalContext.current
    val device4k = remember(context) { CodecCapabilityInspector.inspectDevice4kCapabilities(context) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, UhBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Video Player",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = playerLocalSettings.preferredPlayerEngine.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VideoPlayerEngine.entries.forEach { engine ->
                    val selected = playerLocalSettings.preferredPlayerEngine == engine
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onUpdatePlayerLocalSettings(
                                playerLocalSettings.copy(preferredPlayerEngine = engine)
                            )
                        },
                        label = { Text(engine.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UhCrimson,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("settings_player_engine_${engine.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Video Decoder",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = playerLocalSettings.videoDecoderMode.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VideoDecoderMode.entries.forEach { mode ->
                    val selected = playerLocalSettings.videoDecoderMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onUpdatePlayerLocalSettings(
                                playerLocalSettings.copy(videoDecoderMode = mode)
                            )
                        },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UhCrimson,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("settings_decoder_${mode.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Preferred Video Quality (Multivariant ABR Streams)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Only selects qualities actually provided by the stream; never fabricates resolutions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    VideoQualityTier.AUTO,
                    VideoQualityTier.UHD_4K,
                    VideoQualityTier.FULL_HD,
                    VideoQualityTier.HD
                ).forEach { tier ->
                    val selected = playerLocalSettings.defaultQualityTier == tier
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onUpdatePlayerLocalSettings(
                                playerLocalSettings.copy(defaultQualityTier = tier)
                            )
                        },
                        label = { Text(tier.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UhCrimson,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("settings_quality_${tier.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Video Rendering Surface",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = playerLocalSettings.videoSurfaceMode.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VideoSurfaceMode.entries.forEach { surfaceMode ->
                    val selected = playerLocalSettings.videoSurfaceMode == surfaceMode
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onUpdatePlayerLocalSettings(
                                playerLocalSettings.copy(videoSurfaceMode = surfaceMode)
                            )
                        },
                        label = { Text(surfaceMode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UhCrimson,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("settings_surface_${surfaceMode.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Playback Information Overlay",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Display live video codec, resolution, FPS, decoder, dropped frames, and HTTP errors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = playerLocalSettings.showTechnicalPlaybackInfo,
                    onCheckedChange = { show ->
                        onUpdatePlayerLocalSettings(
                            playerLocalSettings.copy(showTechnicalPlaybackInfo = show)
                        )
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = UhCrimson),
                    modifier = Modifier.testTag("settings_technical_info_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UhBorder, RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Device 4K & HDR Hardware Capabilities",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${device4k.compactSummaryText()} • HEVC Max ${device4k.hevc.maxWidth}×${device4k.hevc.maxHeight}" +
                            if (device4k.hevc.maxFps4k > 0) " @ ${device4k.hevc.maxFps4k} fps" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val hdrText = if (device4k.supportedDisplayHdrTypes.isNotEmpty()) {
                        device4k.supportedDisplayHdrTypes.joinToString(", ")
                    } else {
                        "SDR Display Only"
                    }
                    Text(
                        text = "Display HDR: $hdrText • HEVC 10-bit: ${if (device4k.hevc.supports10BitHevc) "Yes" else "No"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Default Video Aspect Mode",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("FIT", "FILL", "ZOOM").forEach { mode ->
                    val selected = preferences.defaultPlayerResizeMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onSavePreferences(
                                preferences.audioPriority,
                                preferences.autoAudioEnabled,
                                preferences.preferHardwareDecoder,
                                mode
                            )
                        },
                        label = { Text(mode) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UhCrimson,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Playback Diagnostic Test",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Test the active video engine (${playerLocalSettings.preferredPlayerEngine.label}) with an authorised public HLS or MP4 stream independently of your IPTV server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PublicSampleStreams.samples.forEach { sample ->
                    OutlinedButton(
                        onClick = { onPlaySampleStream(sample) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_${sample.id}_button")
                    ) {
                        Text(
                            text = sample.formatLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAudioPriorityCard(
    preferences: UserPreferencesDoc,
    onSavePreferences: (List<String>, Boolean, Boolean, String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, UhBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audio_priority_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Applies to Live TV, Movies, and Series",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = preferences.autoAudioEnabled,
                    onCheckedChange = { enabled ->
                        onSavePreferences(
                            preferences.audioPriority,
                            enabled,
                            preferences.preferHardwareDecoder,
                            preferences.defaultPlayerResizeMode
                        )
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = UhCrimson),
                    modifier = Modifier.testTag("auto_audio_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            val priorityList = preferences.audioPriority.ifEmpty { AudioTrackHelper.DEFAULT_PRIORITY }
            priorityList.forEachIndexed { idx, code ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${idx + 1}. ${AudioTrackHelper.languageDisplayName(code)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row {
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val mutable = priorityList.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx - 1, item)
                                    onSavePreferences(
                                        mutable,
                                        preferences.autoAudioEnabled,
                                        preferences.preferHardwareDecoder,
                                        preferences.defaultPlayerResizeMode
                                    )
                                }
                            },
                            enabled = idx > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = {
                                if (idx < priorityList.lastIndex) {
                                    val mutable = priorityList.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx + 1, item)
                                    onSavePreferences(
                                        mutable,
                                        preferences.autoAudioEnabled,
                                        preferences.preferHardwareDecoder,
                                        preferences.defaultPlayerResizeMode
                                    )
                                }
                            },
                            enabled = idx < priorityList.lastIndex
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onSavePreferences(
                        AudioTrackHelper.DEFAULT_PRIORITY,
                        true,
                        preferences.preferHardwareDecoder,
                        preferences.defaultPlayerResizeMode
                    )
                },
                modifier = Modifier.testTag("reset_audio_priority_button")
            ) {
                Text("Reset to Hindi -> Punjabi -> English")
            }
        }
    }
}
