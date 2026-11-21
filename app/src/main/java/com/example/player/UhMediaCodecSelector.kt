package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import java.util.concurrent.atomic.AtomicReference

/**
 * Custom MediaCodecSelector that honors the user's VideoDecoderMode (Auto, Hardware, Software)
 * without forcing software decoding for unsupported high-resolution streams.
 */
@OptIn(UnstableApi::class)
class UhMediaCodecSelector(
    initialMode: VideoDecoderMode = VideoDecoderMode.AUTO
) : MediaCodecSelector {

    private val modeRef = AtomicReference(initialMode)

    var decoderMode: VideoDecoderMode
        get() = modeRef.get()
        set(value) {
            modeRef.set(value)
        }

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val rawInfos = MediaCodecUtil.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder
        )
        if (!MimeTypes.isVideo(mimeType)) {
            return rawInfos
        }
        return selectVideoDecoders(rawInfos, modeRef.get())
    }

    companion object {
        fun selectVideoDecoders(
            rawInfos: List<MediaCodecInfo>,
            mode: VideoDecoderMode
        ): List<MediaCodecInfo> {
            val hardware = rawInfos.filter { isHardwareCodec(it) }
            val software = rawInfos.filter { !isHardwareCodec(it) }
            return when (mode) {
                VideoDecoderMode.AUTO -> {
                    // Hardware decoders first; software kept only as fallback when hardware fails/absent.
                    hardware + software
                }
                VideoDecoderMode.HARDWARE -> {
                    // Use hardware decoders when supported; do not force software decoding if HW exists.
                    hardware.ifEmpty { rawInfos }
                }
                VideoDecoderMode.SOFTWARE -> {
                    // Prefer software decoders when available, falling back to hardware if absent.
                    software + hardware
                }
            }
        }

        fun isHardwareCodec(info: MediaCodecInfo): Boolean {
            return info.hardwareAccelerated &&
                !info.softwareOnly &&
                !CodecCapabilityInspector.isSoftwareDecoderName(info.name)
        }
    }
}
