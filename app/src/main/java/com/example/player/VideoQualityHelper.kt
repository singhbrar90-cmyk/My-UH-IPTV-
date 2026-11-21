package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.example.data.model.LiveChannel
import java.util.Locale

enum class VideoQualityTier(val label: String, val maxWidth: Int, val maxHeight: Int) {
    AUTO("Auto", Int.MAX_VALUE, Int.MAX_VALUE),
    UHD_4K("4K", 3840, 2160),
    FULL_HD("Full HD", 1920, 1080),
    HD("HD", 1280, 720),
    SD("SD", 854, 480)
}

enum class VideoQualitySourceType {
    ABR_AUTO,
    ABR_TRACK,
    PROVIDER_CHANNEL,
    CURRENT_SOURCE,
    UNAVAILABLE
}

data class VideoQualityOption(
    val tier: VideoQualityTier,
    val label: String,
    val detail: String,
    val available: Boolean,
    val deviceSupported: Boolean,
    val sourceType: VideoQualitySourceType,
    val selected: Boolean,
    val alternativeChannel: LiveChannel? = null,
    val groupIndex: Int? = null,
    val trackIndex: Int? = null
)

data class UnsupportedTrackFinding(
    val codecMime: String?,
    val width: Int,
    val height: Int,
    val reason: String
)

@OptIn(UnstableApi::class)
object VideoQualityHelper {

    private val qualityTokenRegex = Regex(
        pattern = """\b(4k|uhd|2160p|fhd|1080p|full\s*hd|hd|720p|sd|480p|576p|hevc|h\.?265|h\.?264|avc|raw|vip)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val bracketQualityRegex = Regex("""[\[(]([^)\]]*(?:4k|uhd|fhd|hd|sd|hevc|2160|1080|720)[^)\]]*)[\])]""", RegexOption.IGNORE_CASE)

    fun normalizeChannelBaseName(channelName: String): String {
        val withoutBrackets = bracketQualityRegex.replace(channelName, " ")
        val withoutTokens = qualityTokenRegex.replace(withoutBrackets, " ")
        return withoutTokens
            .replace(Regex("""[|\-:_/]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    fun detectChannelTierFromName(channelName: String): VideoQualityTier? {
        val lower = channelName.lowercase(Locale.ROOT)
        return when {
            Regex("""\b(4k|uhd|2160p)\b""").containsMatchIn(lower) -> VideoQualityTier.UHD_4K
            Regex("""\b(fhd|1080p|full\s*hd)\b""").containsMatchIn(lower) -> VideoQualityTier.FULL_HD
            Regex("""\b(hd|720p)\b""").containsMatchIn(lower) -> VideoQualityTier.HD
            Regex("""\b(sd|480p|576p)\b""").containsMatchIn(lower) -> VideoQualityTier.SD
            else -> null
        }
    }

    fun tierForResolution(width: Int, height: Int): VideoQualityTier {
        return when {
            height >= 2000 || width >= 3500 -> VideoQualityTier.UHD_4K
            height >= 1000 || width >= 1800 -> VideoQualityTier.FULL_HD
            height >= 680 || width >= 1200 -> VideoQualityTier.HD
            else -> VideoQualityTier.SD
        }
    }

    fun inspectUnsupportedVideoTrack(tracks: Tracks): UnsupportedTrackFinding? {
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) {
                    val fmt = group.getTrackFormat(i)
                    val support = group.getTrackSupport(i)
                    val reason = when (support) {
                        C.FORMAT_EXCEEDS_CAPABILITIES -> "Exceeds device decoder capabilities"
                        C.FORMAT_UNSUPPORTED_SUBTYPE -> "Unsupported video codec (${fmt.sampleMimeType ?: "unknown"})"
                        C.FORMAT_UNSUPPORTED_DRM -> "Unsupported DRM scheme"
                        else -> "Device cannot decode track format"
                    }
                    return UnsupportedTrackFinding(
                        codecMime = fmt.sampleMimeType,
                        width = fmt.width.coerceAtLeast(0),
                        height = fmt.height.coerceAtLeast(0),
                        reason = reason
                    )
                }
            }
        }
        return null
    }

    fun buildQualityOptions(
        tracks: Tracks,
        selectedTier: VideoQualityTier,
        currentChannelId: String?,
        currentChannelTitle: String,
        liveChannelQueue: List<LiveChannel>,
        deviceProfile: Device4kProfile? = null
    ): List<VideoQualityOption> {
        // 1. Collect real video variants from ExoPlayer Tracks (HLS/DASH multivariant)
        data class TrackVariant(
            val groupIdx: Int,
            val trackIdx: Int,
            val format: Format,
            val supported: Boolean,
            val tier: VideoQualityTier
        )

        val videoTracks = mutableListOf<TrackVariant>()
        tracks.groups.forEachIndexed { gIdx, group ->
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (tIdx in 0 until group.length) {
                    val fmt = group.getTrackFormat(tIdx)
                    if (fmt.width > 0 && fmt.height > 0) {
                        videoTracks.add(
                            TrackVariant(
                                groupIdx = gIdx,
                                trackIdx = tIdx,
                                format = fmt,
                                supported = group.isTrackSupported(tIdx),
                                tier = tierForResolution(fmt.width, fmt.height)
                            )
                        )
                    }
                }
            }
        }

        // 2. Collect real alternative Live TV channels supplied by the provider with matching base name
        val baseName = normalizeChannelBaseName(currentChannelTitle)
        val siblingChannelsByTier = if (baseName.length >= 3 && liveChannelQueue.size > 1) {
            liveChannelQueue
                .filter { ch ->
                    ch.id != currentChannelId && normalizeChannelBaseName(ch.name) == baseName
                }
                .mapNotNull { ch ->
                    val tier = detectChannelTierFromName(ch.name)
                    if (tier != null) tier to ch else null
                }
                .toMap()
        } else {
            emptyMap()
        }

        val currentNameTier = detectChannelTierFromName(currentChannelTitle)
        val hasMultipleAbrTracks = videoTracks.map { it.tier }.distinct().size > 1
        val currentTrack = videoTracks.firstOrNull()

        val options = mutableListOf<VideoQualityOption>()
        options.add(
            VideoQualityOption(
                tier = VideoQualityTier.AUTO,
                label = VideoQualityTier.AUTO.label,
                detail = if (hasMultipleAbrTracks) {
                    "Adaptive bitrate across ${videoTracks.size} provider variants"
                } else {
                    currentTrack?.let {
                        "Provider source (${it.format.width}×${it.format.height} • ${
                            CodecCapabilityInspector.codecDisplayName(it.format.sampleMimeType, it.format.codecs)
                        })"
                    } ?: "Use provider default stream"
                },
                available = true,
                deviceSupported = true,
                sourceType = VideoQualitySourceType.ABR_AUTO,
                selected = selectedTier == VideoQualityTier.AUTO
            )
        )

        val orderedTiers = listOf(
            VideoQualityTier.UHD_4K,
            VideoQualityTier.FULL_HD,
            VideoQualityTier.HD,
            VideoQualityTier.SD
        )

        for (tier in orderedTiers) {
            val abrMatch = videoTracks
                .filter { it.tier == tier }
                .maxByOrNull { it.format.bitrate }
            val siblingMatch = siblingChannelsByTier[tier]
            val matchesCurrentSingleSource = !hasMultipleAbrTracks &&
                (currentTrack?.tier == tier || (currentTrack == null && currentNameTier == tier))

            val option = when {
                abrMatch != null -> {
                    val mime = abrMatch.format.sampleMimeType
                    val codecSummary = deviceProfile?.summaryForMime(mime)
                    val deviceOk = abrMatch.supported && (
                        tier != VideoQualityTier.UHD_4K || codecSummary == null || codecSummary.supports4k
                        )
                    val codecShort = CodecCapabilityInspector.codecDisplayName(mime, abrMatch.format.codecs)
                        .substringBefore(" (")
                    val resText = "${abrMatch.format.width}×${abrMatch.format.height} • $codecShort"
                    VideoQualityOption(
                        tier = tier,
                        label = tier.label,
                        detail = if (deviceOk) resText else "$resText (Unsupported on device)",
                        available = true,
                        deviceSupported = deviceOk,
                        sourceType = VideoQualitySourceType.ABR_TRACK,
                        selected = selectedTier == tier,
                        groupIndex = abrMatch.groupIdx,
                        trackIndex = abrMatch.trackIdx
                    )
                }
                siblingMatch != null -> {
                    val deviceOk = tier != VideoQualityTier.UHD_4K ||
                        deviceProfile == null ||
                        deviceProfile.hevc.supports4k ||
                        deviceProfile.avc.supports4k
                    VideoQualityOption(
                        tier = tier,
                        label = tier.label,
                        detail = "Provider feed: ${siblingMatch.name}",
                        available = true,
                        deviceSupported = deviceOk,
                        sourceType = VideoQualitySourceType.PROVIDER_CHANNEL,
                        selected = selectedTier == tier,
                        alternativeChannel = siblingMatch
                    )
                }
                matchesCurrentSingleSource -> {
                    val resText = currentTrack?.let {
                        "${it.format.width}×${it.format.height}"
                    } ?: "Active channel stream"
                    VideoQualityOption(
                        tier = tier,
                        label = tier.label,
                        detail = "Active stream ($resText)",
                        available = true,
                        deviceSupported = currentTrack?.supported ?: true,
                        sourceType = VideoQualitySourceType.CURRENT_SOURCE,
                        selected = selectedTier == tier
                    )
                }
                else -> null
            }
            if (option != null) {
                options.add(option)
            }
        }

        return options
    }

    fun applyPreferredQualityConstraint(
        player: Player,
        tier: VideoQualityTier
    ) {
        val builder = player.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (tier == VideoQualityTier.AUTO) {
            builder.clearVideoSizeConstraints()
        } else {
            builder.setMaxVideoSize(tier.maxWidth, tier.maxHeight)
        }
        player.trackSelectionParameters = builder.build()
    }

    fun applyAbrQualitySelection(
        player: Player,
        tracks: Tracks,
        option: VideoQualityOption
    ) {
        val builder = player.trackSelectionParameters.buildUpon()
        when (option.tier) {
            VideoQualityTier.AUTO -> {
                builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                builder.clearVideoSizeConstraints()
            }
            else -> {
                val gIdx = option.groupIndex
                val tIdx = option.trackIndex
                val group = if (gIdx != null) tracks.groups.getOrNull(gIdx) else null
                if (group != null && tIdx != null && group.type == C.TRACK_TYPE_VIDEO && group.isTrackSupported(tIdx)) {
                    builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, tIdx))
                } else {
                    builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    builder.setMaxVideoSize(option.tier.maxWidth, option.tier.maxHeight)
                }
            }
        }
        player.trackSelectionParameters = builder.build()
    }
}
