package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.example.data.model.AudioTrackOption
import java.util.Locale

@OptIn(UnstableApi::class)
object AudioTrackHelper {

    val DEFAULT_PRIORITY = listOf("hi", "pa", "en")

    fun expandPreferredLanguageCodes(priority: List<String>): List<String> {
        val expanded = linkedSetOf<String>()
        priority.forEach { code ->
            when (code.lowercase(Locale.ROOT)) {
                "hi", "hin", "hindi" -> {
                    expanded.add("hi")
                    expanded.add("hin")
                }
                "pa", "pan", "punjabi", "panjabi" -> {
                    expanded.add("pa")
                    expanded.add("pan")
                }
                "en", "eng", "english" -> {
                    expanded.add("en")
                    expanded.add("eng")
                }
                else -> expanded.add(code.lowercase(Locale.ROOT))
            }
        }
        return expanded.toList()
    }

    fun applyLanguagePriorityParameters(
        player: Player,
        priority: List<String>,
        autoEnabled: Boolean
    ) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (autoEnabled) {
            val codes = expandPreferredLanguageCodes(priority)
            builder.setPreferredAudioLanguages(*codes.toTypedArray())
        } else {
            builder.setPreferredAudioLanguages()
        }
        player.trackSelectionParameters = builder.build()
    }

    /**
     * Inspects available ExoPlayer audio tracks and automatically selects the best match
     * following the priority order (default: Hindi -> Punjabi -> English -> default track).
     * Returns the extracted list of audio options for the track selector UI.
     */
    fun extractAndAutoSelectAudioTracks(
        player: Player,
        tracks: Tracks,
        priority: List<String>,
        autoEnabled: Boolean,
        manualOverrideActive: Boolean,
        sessionPreferredLang: String = ""
    ): List<AudioTrackOption> {
        val audioGroups = tracks.groups.mapIndexedNotNull { index, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) index to group else null
        }

        if (!manualOverrideActive && autoEnabled && audioGroups.isNotEmpty()) {
            val effectivePriority = if (sessionPreferredLang.isNotBlank()) {
                listOf(sessionPreferredLang) + priority
            } else {
                priority
            }
            val bestCandidate = findBestAudioTrack(audioGroups, effectivePriority)
            if (bestCandidate != null) {
                val (group, trackIndex) = bestCandidate
                if (!group.isTrackSelected(trackIndex)) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                        )
                        .build()
                }
            }
        }

        return audioGroups.flatMap { (groupIdx, group) ->
            (0 until group.length).mapNotNull { trackIdx ->
                if (!group.isTrackSupported(trackIdx)) return@mapNotNull null
                val format = group.getTrackFormat(trackIdx)
                AudioTrackOption(
                    groupIndex = groupIdx,
                    trackIndex = trackIdx,
                    languageCode = normalizeLangCode(format.language, format.label),
                    label = buildDisplayLabel(format, trackIdx),
                    channels = format.channelCount.takeIf { it > 0 } ?: 2,
                    bitrate = format.bitrate.takeIf { it > 0 } ?: 0,
                    selected = group.isTrackSelected(trackIdx)
                )
            }
        }
    }

    fun selectManualTrack(
        player: Player,
        tracks: Tracks,
        option: AudioTrackOption
    ) {
        val group = tracks.groups.getOrNull(option.groupIndex) ?: return
        if (group.type != C.TRACK_TYPE_AUDIO) return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun findBestTrackIdByLanguage(
        tracks: List<Pair<Int, String>>,
        priority: List<String>,
        sessionPreferredLang: String = ""
    ): Int? {
        val effectivePriority = if (sessionPreferredLang.isNotBlank()) {
            listOf(sessionPreferredLang) + priority
        } else {
            priority
        }
        for (targetLang in effectivePriority) {
            for ((trackId, rawName) in tracks) {
                if (trackId < 0) continue
                if (matchesLanguageRaw(rawName, rawName, targetLang)) {
                    return trackId
                }
            }
        }
        return null
    }

    private fun findBestAudioTrack(
        audioGroups: List<Pair<Int, Tracks.Group>>,
        priority: List<String>
    ): Pair<Tracks.Group, Int>? {
        for (targetLang in priority) {
            for ((_, group) in audioGroups) {
                for (trackIdx in 0 until group.length) {
                    if (!group.isTrackSupported(trackIdx)) continue
                    val format = group.getTrackFormat(trackIdx)
                    if (matchesLanguage(format, targetLang)) {
                        return group to trackIdx
                    }
                }
            }
        }
        return null
    }

    fun matchesLanguage(format: Format, targetCode: String): Boolean {
        return matchesLanguageRaw(format.language, format.label, targetCode)
    }

    fun matchesLanguageRaw(rawLanguage: String?, rawLabel: String?, targetCode: String): Boolean {
        val lang = rawLanguage?.lowercase(Locale.ROOT).orEmpty()
        val label = rawLabel?.lowercase(Locale.ROOT).orEmpty()
        return when (targetCode.lowercase(Locale.ROOT)) {
            "hi", "hin", "hindi" ->
                lang in setOf("hi", "hin", "hi-in", "hindi") ||
                    label.contains("hindi") || label.contains("hin") || label.contains("हिंदी")
            "pa", "pan", "punjabi", "panjabi" ->
                lang in setOf("pa", "pan", "pa-in", "punjabi", "panjabi") ||
                    label.contains("punjabi") || label.contains("panjabi") || label.contains("ਪੰਜਾਬੀ")
            "en", "eng", "english" ->
                lang in setOf("en", "eng", "en-us", "en-gb", "english") ||
                    label.contains("english") || label.contains("eng")
            else -> lang == targetCode.lowercase(Locale.ROOT) || label.contains(targetCode.lowercase(Locale.ROOT))
        }
    }

    fun normalizeLangCode(rawLang: String?, rawLabel: String?): String {
        val lang = rawLang?.lowercase(Locale.ROOT).orEmpty()
        val label = rawLabel?.lowercase(Locale.ROOT).orEmpty()
        return when {
            lang.startsWith("hi") || label.contains("hindi") -> "hi"
            lang.startsWith("pa") || lang == "pan" || label.contains("punjabi") || label.contains("panjabi") -> "pa"
            lang.startsWith("en") || label.contains("english") -> "en"
            lang.isNotBlank() -> lang
            else -> "und"
        }
    }

    fun languageDisplayName(code: String): String {
        return when (code.lowercase(Locale.ROOT)) {
            "hi", "hin" -> "Hindi"
            "pa", "pan" -> "Punjabi"
            "en", "eng" -> "English"
            "und", "" -> "Default Audio"
            else -> Locale.forLanguageTag(code).displayLanguage.ifBlank { code.uppercase(Locale.ROOT) }
        }
    }

    private fun buildDisplayLabel(format: Format, trackIdx: Int): String {
        val langName = languageDisplayName(normalizeLangCode(format.language, format.label))
        val customLabel = format.label?.trim().orEmpty()
        val ch = when (format.channelCount) {
            6 -> "5.1"
            8 -> "7.1"
            2 -> "Stereo"
            1 -> "Mono"
            else -> if (format.channelCount > 0) "${format.channelCount}ch" else ""
        }
        val base = if (customLabel.isNotBlank() && !customLabel.equals(langName, ignoreCase = true)) {
            "$langName • $customLabel"
        } else {
            if (langName == "Default Audio") "Track ${trackIdx + 1}" else langName
        }
        return if (ch.isNotBlank()) "$base ($ch)" else base
    }
}
