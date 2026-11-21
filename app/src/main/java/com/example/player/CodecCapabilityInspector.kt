package com.example.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import java.util.Locale

data class CodecCapabilitySummary(
    val mimeType: String,
    val displayName: String,
    val decoderName: String,
    val hardwareSupported: Boolean,
    val supports4k: Boolean,
    val supports4k60: Boolean,
    val supports10BitHevc: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFps4k: Int = 0,
    val maxFps1080p: Int = 0
)

data class Device4kProfile(
    val hevc: CodecCapabilitySummary,
    val avc: CodecCapabilitySummary,
    val av1: CodecCapabilitySummary,
    val vp9: CodecCapabilitySummary,
    val supportedDisplayHdrTypes: List<String> = emptyList()
) {
    fun summaryForMime(mimeType: String?): CodecCapabilitySummary? {
        return when (mimeType?.lowercase(Locale.ROOT)) {
            MimeTypes.VIDEO_H265 -> hevc
            MimeTypes.VIDEO_H264 -> avc
            MimeTypes.VIDEO_AV1 -> av1
            MimeTypes.VIDEO_VP9 -> vp9
            else -> null
        }
    }

    fun compactSummaryText(): String {
        val parts = listOf(
            "HEVC 4K: ${if (hevc.supports4k && hevc.hardwareSupported) "HW Yes" else if (hevc.supports4k) "SW Only" else "No"}",
            "H.264 4K: ${if (avc.supports4k && avc.hardwareSupported) "HW Yes" else if (avc.supports4k) "SW Only" else "No"}",
            "AV1 4K: ${if (av1.supports4k && av1.hardwareSupported) "HW Yes" else if (av1.supports4k) "SW Only" else "No"}"
        )
        return parts.joinToString(" • ")
    }
}

object CodecCapabilityInspector {

    private const val UHD_WIDTH = 3840
    private const val UHD_HEIGHT = 2160

    fun inspectDevice4kCapabilities(context: Context? = null): Device4kProfile {
        val allDecoders = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        }.getOrDefault(emptyList())

        val hdrTypes = if (context != null) inspectDisplayHdrTypes(context) else emptyList()

        return Device4kProfile(
            hevc = inspectMime(allDecoders, MimeTypes.VIDEO_H265, "HEVC / H.265"),
            avc = inspectMime(allDecoders, MimeTypes.VIDEO_H264, "H.264 / AVC"),
            av1 = inspectMime(allDecoders, MimeTypes.VIDEO_AV1, "AV1"),
            vp9 = inspectMime(allDecoders, MimeTypes.VIDEO_VP9, "VP9"),
            supportedDisplayHdrTypes = hdrTypes
        )
    }

    @Suppress("DEPRECATION")
    fun inspectDisplayHdrTypes(context: Context): List<String> {
        return runCatching<List<String>> {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
            val types = display.hdrCapabilities?.supportedHdrTypes ?: return emptyList()
            buildList {
                for (type in types) {
                    when (type) {
                        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> add("Dolby Vision")
                        Display.HdrCapabilities.HDR_TYPE_HDR10 -> add("HDR10")
                        Display.HdrCapabilities.HDR_TYPE_HLG -> add("HLG")
                        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> add("HDR10+")
                    }
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    @OptIn(UnstableApi::class)
    fun hdrFormatLabel(colorInfo: ColorInfo?, mimeType: String?): String {
        if (mimeType.equals(MimeTypes.VIDEO_DOLBY_VISION, ignoreCase = true)) {
            return "Dolby Vision"
        }
        if (colorInfo == null || !ColorInfo.isTransferHdr(colorInfo)) {
            return "SDR"
        }
        return when (colorInfo.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> "HDR10 / PQ"
            C.COLOR_TRANSFER_HLG -> "HLG"
            else -> "HDR"
        }
    }

    private fun inspectMime(
        decoders: List<MediaCodecInfo>,
        mimeType: String,
        displayName: String
    ): CodecCapabilitySummary {
        var bestHardware: CodecCapabilitySummary? = null
        var bestSoftware: CodecCapabilitySummary? = null

        for (info in decoders) {
            val supportsType = info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            if (!supportsType) continue

            val caps = runCatching { info.getCapabilitiesForType(mimeType) }.getOrNull() ?: continue
            val videoCaps = caps.videoCapabilities ?: continue

            val isHardware = isHardwareCodec(info)
            val maxW = videoCaps.supportedWidths.upper
            val maxH = videoCaps.supportedHeights.upper
            val supports4k = runCatching { videoCaps.isSizeSupported(UHD_WIDTH, UHD_HEIGHT) }.getOrDefault(false)
            val supports4k60 = runCatching { videoCaps.areSizeAndRateSupported(UHD_WIDTH, UHD_HEIGHT, 60.0) }.getOrDefault(false)
            val maxFps4k = if (supports4k) {
                runCatching {
                    videoCaps.getSupportedFrameRatesFor(UHD_WIDTH, UHD_HEIGHT).upper.toInt()
                }.getOrDefault(if (supports4k60) 60 else 30)
            } else 0
            val maxFps1080p = if (runCatching { videoCaps.isSizeSupported(1920, 1080) }.getOrDefault(false)) {
                runCatching {
                    videoCaps.getSupportedFrameRatesFor(1920, 1080).upper.toInt()
                }.getOrDefault(60)
            } else 0
            val supports10Bit = mimeType == MimeTypes.VIDEO_H265 && caps.profileLevels.any {
                it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                    it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                    it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
            }

            val candidate = CodecCapabilitySummary(
                mimeType = mimeType,
                displayName = displayName,
                decoderName = info.name,
                hardwareSupported = isHardware,
                supports4k = supports4k,
                supports4k60 = supports4k60,
                supports10BitHevc = supports10Bit,
                maxWidth = maxW,
                maxHeight = maxH,
                maxFps4k = maxFps4k,
                maxFps1080p = maxFps1080p
            )

            if (isHardware) {
                if (bestHardware == null || scoreSummary(candidate) > scoreSummary(bestHardware)) {
                    bestHardware = candidate
                }
            } else {
                if (bestSoftware == null || scoreSummary(candidate) > scoreSummary(bestSoftware)) {
                    bestSoftware = candidate
                }
            }
        }

        return bestHardware ?: bestSoftware ?: CodecCapabilitySummary(
            mimeType = mimeType,
            displayName = displayName,
            decoderName = "Unavailable",
            hardwareSupported = false,
            supports4k = false,
            supports4k60 = false,
            supports10BitHevc = false,
            maxWidth = 0,
            maxHeight = 0
        )
    }

    private fun scoreSummary(summary: CodecCapabilitySummary): Int {
        var score = 0
        if (summary.hardwareSupported) score += 10_000
        if (summary.supports4k) score += 1_000
        if (summary.supports4k60) score += 200
        if (summary.supports10BitHevc) score += 50
        score += (summary.maxWidth * summary.maxHeight) / 100_000
        return score
    }

    private fun isHardwareCodec(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated && !info.isSoftwareOnly
        } else {
            !isSoftwareDecoderName(info.name)
        }
    }

    fun isSoftwareDecoderName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.startsWith("omx.google.") ||
            lower.startsWith("c2.android.") ||
            lower.contains(".sw.") ||
            lower.contains("ffmpeg") ||
            lower.contains("software")
    }

    fun codecDisplayName(mimeType: String?, codecs: String? = null): String {
        val mime = mimeType?.lowercase(Locale.ROOT).orEmpty()
        val codecStr = codecs?.lowercase(Locale.ROOT).orEmpty()
        return when {
            mime == MimeTypes.VIDEO_H265 || codecStr.startsWith("hvc") || codecStr.startsWith("hev") ->
                "HEVC / H.265 ($mime)"
            mime == MimeTypes.VIDEO_H264 || codecStr.startsWith("avc") ->
                "H.264 / AVC ($mime)"
            mime == MimeTypes.VIDEO_AV1 || codecStr.startsWith("av01") ->
                "AV1 ($mime)"
            mime == MimeTypes.VIDEO_VP9 || codecStr.startsWith("vp9") || codecStr.startsWith("vp09") ->
                "VP9 ($mime)"
            mime == MimeTypes.VIDEO_MPEG2 ->
                "MPEG-2 Video ($mime)"
            mime == MimeTypes.AUDIO_AAC ->
                "AAC ($mime)"
            mime == MimeTypes.AUDIO_AC3 ->
                "Dolby Digital AC-3 ($mime)"
            mime == MimeTypes.AUDIO_E_AC3 || mime == MimeTypes.AUDIO_E_AC3_JOC ->
                "Dolby Digital Plus E-AC-3 ($mime)"
            mime == MimeTypes.AUDIO_DTS || mime == MimeTypes.AUDIO_DTS_HD ->
                "DTS ($mime)"
            mime == MimeTypes.AUDIO_OPUS ->
                "Opus ($mime)"
            mime == MimeTypes.AUDIO_MPEG ->
                "MP3 / MPEG Audio ($mime)"
            mime.isNotBlank() -> mime
            else -> "Unknown"
        }
    }

    fun buildCompatibilityMessage(
        mimeType: String?,
        streamWidth: Int,
        streamHeight: Int,
        profile: Device4kProfile
    ): String? {
        val summary = profile.summaryForMime(mimeType)
        val is4kStream = streamWidth >= 3800 || streamHeight >= 2100
        if (summary == null) {
            return if (is4kStream && !mimeType.isNullOrBlank()) {
                "4K codec $mimeType is not supported by this device's media decoders. Try an HD or Full HD stream from your provider if available."
            } else {
                null
            }
        }
        val codecName = summary.displayName
        val maxResLabel = if (summary.maxWidth > 0 && summary.maxHeight > 0) {
            "${summary.maxWidth}×${summary.maxHeight}"
        } else {
            "none"
        }
        return when {
            summary.decoderName == "Unavailable" ->
                "This device has no decoder for $codecName. Select an H.264 Full HD or HD feed if your provider supplies one."
            is4kStream && !summary.supports4k ->
                "This 4K $codecName stream (${streamWidth}×${streamHeight}) exceeds your device's maximum supported resolution ($maxResLabel). Select Full HD / HD in Video Quality if supplied by your provider."
            is4kStream && !summary.hardwareSupported ->
                "Hardware decoding for 4K $codecName is not supported on this device (software decoder only: ${summary.decoderName}), which may cause decoding failure or stutter. Switch to Full HD / HD if supplied."
            else -> null
        }
    }
}
