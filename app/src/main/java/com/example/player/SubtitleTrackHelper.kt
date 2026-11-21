package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

data class SubtitleTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val vlcTrackId: Int = -1,
    val languageCode: String,
    val label: String,
    val selected: Boolean
)

@OptIn(UnstableApi::class)
object SubtitleTrackHelper {

    fun extractSubtitleTracks(tracks: Tracks): List<SubtitleTrackOption> {
        val textGroups = tracks.groups.mapIndexedNotNull { index, group ->
            if (group.type == C.TRACK_TYPE_TEXT) index to group else null
        }
        return textGroups.flatMap { (groupIdx, group) ->
            (0 until group.length).mapNotNull { trackIdx ->
                if (!group.isTrackSupported(trackIdx)) return@mapNotNull null
                val format = group.getTrackFormat(trackIdx)
                SubtitleTrackOption(
                    groupIndex = groupIdx,
                    trackIndex = trackIdx,
                    languageCode = AudioTrackHelper.normalizeLangCode(format.language, format.label),
                    label = buildDisplayLabel(format, trackIdx),
                    selected = group.isTrackSelected(trackIdx)
                )
            }
        }
    }

    fun selectSubtitleTrack(
        player: Player,
        tracks: Tracks,
        option: SubtitleTrackOption?
    ) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            val group = tracks.groups.getOrNull(option.groupIndex) ?: return
            if (group.type != C.TRACK_TYPE_TEXT) return
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        player.trackSelectionParameters = builder.build()
    }

    private fun buildDisplayLabel(format: Format, trackIdx: Int): String {
        val langName = AudioTrackHelper.languageDisplayName(
            AudioTrackHelper.normalizeLangCode(format.language, format.label)
        )
        val customLabel = format.label?.trim().orEmpty()
        return when {
            customLabel.isNotBlank() && langName != "Default Audio" && !customLabel.equals(langName, ignoreCase = true) ->
                "$langName • $customLabel"
            customLabel.isNotBlank() -> customLabel
            langName != "Default Audio" -> langName
            else -> "Subtitle ${trackIdx + 1}"
        }
    }
}
