package com.example

import androidx.annotation.OptIn
import androidx.media3.common.ColorInfo
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.test.core.app.ApplicationProvider
import com.example.player.CodecCapabilityInspector
import com.example.player.CodecCapabilitySummary
import com.example.player.Device4kProfile
import com.example.player.StreamDiagnostics
import com.example.player.UhMediaCodecSelector
import com.example.player.VideoDecoderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(UnstableApi::class)
class PlaybackDiagnosticsRobolectricTest {

    @Test
    fun streamDiagnostics_classifiesDecoderVsEndpointErrorsAndBuilds4kMessage() {
        val decoderErr = PlaybackException(
            "Decoder init failed",
            null,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )
        assertTrue(StreamDiagnostics.isDecoderOrCodecError(decoderErr))
        assertFalse(StreamDiagnostics.isEndpointFormatError(decoderErr))

        val no4kProfile = Device4kProfile(
            hevc = CodecCapabilitySummary(
                mimeType = MimeTypes.VIDEO_H265,
                displayName = "HEVC / H.265",
                decoderName = "c2.vendor.hevc.decoder",
                hardwareSupported = true,
                supports4k = false,
                supports4k60 = false,
                supports10BitHevc = false,
                maxWidth = 1920,
                maxHeight = 1080
            ),
            avc = CodecCapabilitySummary(
                mimeType = MimeTypes.VIDEO_H264,
                displayName = "H.264 / AVC",
                decoderName = "c2.vendor.avc.decoder",
                hardwareSupported = true,
                supports4k = false,
                supports4k60 = false,
                supports10BitHevc = false,
                maxWidth = 1920,
                maxHeight = 1080
            ),
            av1 = CodecCapabilitySummary(
                mimeType = MimeTypes.VIDEO_AV1,
                displayName = "AV1",
                decoderName = "Unavailable",
                hardwareSupported = false,
                supports4k = false,
                supports4k60 = false,
                supports10BitHevc = false,
                maxWidth = 0,
                maxHeight = 0
            ),
            vp9 = CodecCapabilitySummary(
                mimeType = MimeTypes.VIDEO_VP9,
                displayName = "VP9",
                decoderName = "c2.android.vp9.decoder",
                hardwareSupported = false,
                supports4k = false,
                supports4k60 = false,
                supports10BitHevc = false,
                maxWidth = 1920,
                maxHeight = 1080
            )
        )

        val (code, detail) = StreamDiagnostics.extractErrorDetails(
            error = decoderErr,
            videoMimeHint = MimeTypes.VIDEO_H265,
            streamWidth = 3840,
            streamHeight = 2160,
            device4kProfile = no4kProfile
        )
        assertTrue(code.contains("ERROR_CODE_DECODER_INIT_FAILED"))
        assertTrue(detail.contains("1920×1080"))

        val containerErr = PlaybackException(
            "Unrecognized container",
            null,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        )
        assertFalse(StreamDiagnostics.isDecoderOrCodecError(containerErr))
        assertTrue(StreamDiagnostics.isEndpointFormatError(containerErr))
    }

    @Test
    fun codecCapabilityInspector_and_uhMediaCodecSelector_behavePredictably() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val profile = CodecCapabilityInspector.inspectDevice4kCapabilities(context)
        assertTrue(profile.compactSummaryText().contains("HEVC 4K:"))
        assertEquals("SDR", CodecCapabilityInspector.hdrFormatLabel(null, MimeTypes.VIDEO_H265))
        assertEquals("Dolby Vision", CodecCapabilityInspector.hdrFormatLabel(null, MimeTypes.VIDEO_DOLBY_VISION))
        assertEquals(
            "HDR10 / PQ",
            CodecCapabilityInspector.hdrFormatLabel(
                ColorInfo.Builder()
                    .setColorSpace(androidx.media3.common.C.COLOR_SPACE_BT2020)
                    .setColorRange(androidx.media3.common.C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(androidx.media3.common.C.COLOR_TRANSFER_ST2084)
                    .build(),
                MimeTypes.VIDEO_H265
            )
        )

        val swDecoder = MediaCodecInfo.newInstance(
            /* name = */ "c2.android.hevc.decoder",
            /* mimeType = */ MimeTypes.VIDEO_H265,
            /* codecMimeType = */ MimeTypes.VIDEO_H265,
            /* capabilities = */ null,
            /* hardwareAccelerated = */ false,
            /* softwareOnly = */ true,
            /* vendor = */ false,
            /* forceDisableAdaptive = */ false,
            /* forceSecure = */ false
        )
        val hwDecoder = MediaCodecInfo.newInstance(
            /* name = */ "c2.qti.hevc.decoder",
            /* mimeType = */ MimeTypes.VIDEO_H265,
            /* codecMimeType = */ MimeTypes.VIDEO_H265,
            /* capabilities = */ null,
            /* hardwareAccelerated = */ true,
            /* softwareOnly = */ false,
            /* vendor = */ true,
            /* forceDisableAdaptive = */ false,
            /* forceSecure = */ false
        )

        val autoList = UhMediaCodecSelector.selectVideoDecoders(listOf(swDecoder, hwDecoder), VideoDecoderMode.AUTO)
        assertEquals(listOf("c2.qti.hevc.decoder", "c2.android.hevc.decoder"), autoList.map { it.name })

        val hwList = UhMediaCodecSelector.selectVideoDecoders(listOf(swDecoder, hwDecoder), VideoDecoderMode.HARDWARE)
        assertEquals(listOf("c2.qti.hevc.decoder"), hwList.map { it.name })

        val swList = UhMediaCodecSelector.selectVideoDecoders(listOf(hwDecoder, swDecoder), VideoDecoderMode.SOFTWARE)
        assertEquals(listOf("c2.android.hevc.decoder", "c2.qti.hevc.decoder"), swList.map { it.name })
    }

    @Test
    fun streamDiagnostics_treatsHttp401AsAuthErrorAndNeverAsDecoderError() {
        val dataSpec = androidx.media3.datasource.DataSpec(android.net.Uri.parse("http://portal.example.com:8080/live/u/p/1.ts"))
        val http401 = androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException(
            401,
            "Unauthorized",
            null,
            emptyMap(),
            dataSpec,
            ByteArray(0)
        )
        val wrapped = PlaybackException(
            "HTTP 401",
            http401,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )

        assertTrue(StreamDiagnostics.isHttpAuthError(wrapped))
        assertEquals(401, StreamDiagnostics.extractHttpStatusCode(wrapped))
        assertFalse(StreamDiagnostics.isDecoderOrCodecError(wrapped))
        assertFalse(StreamDiagnostics.isEndpointFormatError(wrapped))

        val (_, detail) = StreamDiagnostics.extractErrorDetails(wrapped)
        assertTrue(detail.contains("401 Unauthorized"))
        assertTrue(StreamDiagnostics.extractHttpErrorLabel(wrapped).contains("401 Unauthorized"))
    }
}
