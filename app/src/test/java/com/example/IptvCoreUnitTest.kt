package com.example

import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import com.example.data.model.LiveChannel
import com.example.data.model.MediaContentType
import com.example.data.network.M3uParser
import com.example.data.network.StreamUrlResolver
import com.example.data.network.XtreamClient
import com.example.player.AudioTrackHelper
import com.example.player.CodecCapabilityInspector
import com.example.player.CodecCapabilitySummary
import com.example.player.Device4kProfile
import com.example.player.StreamDiagnostics
import com.example.player.VideoDecoderMode
import com.example.player.VideoPlayerEngine
import com.example.player.VideoQualityHelper
import com.example.player.VideoQualitySourceType
import com.example.player.VideoQualityTier
import com.example.player.VideoSurfaceMode
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class IptvCoreUnitTest {

    @Test
    fun m3uParser_extractsChannelsMoviesAndSeries() {
        val sample = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news1" tvg-logo="https://img.example.com/news.png" group-title="News",Global News HD
            http://stream.example.com:8080/live/user/pass/101.m3u8
            #EXTINF:-1 tvg-logo="https://img.example.com/movie.jpg" group-title="Hindi Movies",Cinema Hit 2026
            http://stream.example.com:8080/movie/user/pass/555.mp4
            #EXTINF:-1 tvg-logo="https://img.example.com/show.jpg" group-title="Drama Series",Show Name S01E02
            http://stream.example.com:8080/series/user/pass/901.mkv
        """.trimIndent()

        val catalog = M3uParser.parse(sample, "pl_1")
        assertEquals(1, catalog.liveChannels.size)
        assertEquals("Global News HD", catalog.liveChannels[0].name)
        assertEquals("News", catalog.liveChannels[0].categoryName)
        assertEquals("https://img.example.com/news.png", catalog.liveChannels[0].logoUrl)

        assertEquals(1, catalog.vodMovies.size)
        assertEquals("Cinema Hit 2026", catalog.vodMovies[0].title)

        assertEquals(1, catalog.seriesList.size)
        assertEquals("Show Name", catalog.seriesList[0].title)
        val details = catalog.m3uSeriesDetails[catalog.seriesList[0].id]
        assertEquals(1, details?.seasons?.get(1)?.size)
        assertEquals(2, details?.seasons?.get(1)?.first()?.episodeNumber)
    }

    @Test
    fun xtreamUrlNormalizer_preservesHttpHttpsAndCustomPorts() {
        val client = XtreamClient()
        assertEquals("http://tv.example.com:8080", client.normalizeServerBaseUrl("http://tv.example.com:8080/"))
        assertEquals("https://secure.example.com:8443", client.normalizeServerBaseUrl("https://secure.example.com:8443"))
        assertEquals("http://192.168.1.50:25461", client.normalizeServerBaseUrl("192.168.1.50:25461/"))
        assertNull(client.normalizeServerBaseUrl("   "))
    }

    @Test
    fun streamUrlResolver_buildsLiveVodSeriesAndRedactsCredentials() {
        val tsOnlyLive = StreamUrlResolver.buildXtreamLiveCandidates(
            baseUrl = "http://portal.example.com:8080",
            username = "alice@1",
            password = "s3cr#t",
            streamId = "402",
            allowedOutputFormats = listOf("ts")
        )
        assertTrue(tsOnlyLive.first().endsWith("/live/alice%401/s3cr%23t/402.ts"))
        assertTrue(tsOnlyLive.any { it.endsWith("/402.m3u8") })
        assertTrue(tsOnlyLive.any { it.endsWith("/402") })

        val vod = StreamUrlResolver.buildXtreamVodCandidates(
            baseUrl = "http://portal.example.com:8080",
            username = "alice",
            password = "secret",
            streamId = "900",
            containerExtension = ".mkv"
        )
        assertEquals("http://portal.example.com:8080/movie/alice/secret/900.mkv", vod.first())
        assertTrue(vod.contains("http://portal.example.com:8080/movie/alice/secret/900.mp4"))

        val derived = StreamUrlResolver.deriveFallbackCandidates(
            primaryUrl = "http://portal.example.com:8080/live/alice/secret/402.m3u8",
            contentType = MediaContentType.LIVE
        )
        assertTrue(derived.contains("http://portal.example.com:8080/live/alice/secret/402.ts"))
        assertTrue(derived.contains("http://portal.example.com:8080/alice/secret/402"))

        val redacted = StreamUrlResolver.redactCredentials("Failed at http://portal.example.com:8080/live/alice/secret/402.ts?token=abc")
        assertFalse(redacted.contains("alice"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("abc"))
        assertTrue(redacted.contains("http://portal.example.com:8080/live/***/***/402.ts"))

        val basicRedacted = StreamUrlResolver.redactCredentials("Failed at http://alice:s3cr3t@portal.example.com:8080/stream.ts")
        assertFalse(basicRedacted.contains("alice"))
        assertFalse(basicRedacted.contains("s3cr3t"))
        assertTrue(basicRedacted.contains("http://***:***@portal.example.com:8080/stream.ts"))
    }

    @Test
    fun streamDiagnostics_detectsContainerSignaturesAndProbesHttpRedirectsAnd401() = runTest {
        val hlsBytes = "#EXTM3U\n#EXT-X-VERSION:3".toByteArray()
        assertEquals("HLS", StreamDiagnostics.detectByteSignature(hlsBytes, hlsBytes.size))
        assertEquals("MPEG-TS", StreamDiagnostics.detectByteSignature(byteArrayOf(0x47, 0x40, 0x00, 0x10), 4))
        assertEquals("MP4", StreamDiagnostics.detectByteSignature(byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()), 8))
        assertEquals("HTML", StreamDiagnostics.detectByteSignature("<html><body>403</body></html>".toByteArray(), 29))

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var capturedUserAgent = ""
        var capturedAuthHeader = ""
        server.createContext("/live/user/pass/101.m3u8") { exchange ->
            exchange.responseHeaders.add("Location", "/edge/stream.m3u8")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/edge/stream.m3u8") { exchange ->
            capturedUserAgent = exchange.requestHeaders.getFirst("User-Agent").orEmpty()
            val body = "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:6.0,\nseg0.ts\n".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/auth/basic.ts") { exchange ->
            capturedAuthHeader = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/live/user/pass/101.m3u8"
            val probe = StreamDiagnostics.probeStream(url)
            assertTrue(probe.dnsStatus.contains("Resolved"))
            assertTrue(probe.httpStatus.contains("302") && probe.httpStatus.contains("200"))
            assertEquals("HLS (.m3u8)", probe.detectedFormat)
            assertTrue(capturedUserAgent.isNotBlank())

            val authUrl = "http://alice:secret@127.0.0.1:${server.address.port}/auth/basic.ts"
            val authProbe = StreamDiagnostics.probeStream(authUrl)
            assertTrue(capturedAuthHeader.startsWith("Basic "))
            assertTrue(authProbe.httpStatus.contains("401"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun audioTrackHelper_matchesHindiPunjabiEnglish() {
        val expanded = AudioTrackHelper.expandPreferredLanguageCodes(AudioTrackHelper.DEFAULT_PRIORITY)
        assertEquals(listOf("hi", "hin", "pa", "pan", "en", "eng"), expanded)

        assertTrue(AudioTrackHelper.matchesLanguageRaw("hin", "Hindi Audio", "hi"))
        assertFalse(AudioTrackHelper.matchesLanguageRaw("hin", "Hindi Audio", "pa"))
        assertTrue(AudioTrackHelper.matchesLanguageRaw("und", "Punjabi 5.1", "pa"))
        assertTrue(AudioTrackHelper.matchesLanguageRaw("en", null, "en"))

        val vlcTracks = listOf(1 to "English [Stereo]", 2 to "Punjabi 5.1", 3 to "Hindi Dolby")
        assertEquals(3, AudioTrackHelper.findBestTrackIdByLanguage(vlcTracks, listOf("hi", "pa", "en")))
        assertEquals(2, AudioTrackHelper.findBestTrackIdByLanguage(vlcTracks, listOf("hi", "pa", "en"), sessionPreferredLang = "pa"))
    }

    @Test
    fun codecCapabilityInspector_reports4kCompatibilityAndSoftwareNames() {
        assertTrue(CodecCapabilityInspector.isSoftwareDecoderName("c2.android.hevc.decoder"))
        assertTrue(CodecCapabilityInspector.isSoftwareDecoderName("OMX.google.h264.decoder"))
        assertFalse(CodecCapabilityInspector.isSoftwareDecoderName("c2.qti.hevc.decoder"))

        assertEquals("HEVC / H.265 (video/hevc)", CodecCapabilityInspector.codecDisplayName(MimeTypes.VIDEO_H265))
        assertEquals("AV1 (video/av01)", CodecCapabilityInspector.codecDisplayName(MimeTypes.VIDEO_AV1))

        val no4kHevcProfile = Device4kProfile(
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
                supports4k = true,
                supports4k60 = false,
                supports10BitHevc = false,
                maxWidth = 3840,
                maxHeight = 2160
            )
        )

        val hevcMsg = CodecCapabilityInspector.buildCompatibilityMessage(
            mimeType = MimeTypes.VIDEO_H265,
            streamWidth = 3840,
            streamHeight = 2160,
            profile = no4kHevcProfile
        )
        assertNotNull(hevcMsg)
        assertTrue(hevcMsg!!.contains("1920×1080"))

        val av1Msg = CodecCapabilityInspector.buildCompatibilityMessage(
            mimeType = MimeTypes.VIDEO_AV1,
            streamWidth = 3840,
            streamHeight = 2160,
            profile = no4kHevcProfile
        )
        assertNotNull(av1Msg)
        assertTrue(av1Msg!!.contains("no decoder"))

        val fhdMsg = CodecCapabilityInspector.buildCompatibilityMessage(
            mimeType = MimeTypes.VIDEO_H265,
            streamWidth = 1920,
            streamHeight = 1080,
            profile = no4kHevcProfile
        )
        assertNull(fhdMsg)
    }

    @Test
    fun videoQualityHelper_matchesProviderFeedsWithoutInventingStreams() {
        assertEquals("sports one", VideoQualityHelper.normalizeChannelBaseName("Sports One 4K UHD HEVC"))
        assertEquals("sports one", VideoQualityHelper.normalizeChannelBaseName("Sports One [FHD]"))
        assertEquals("sports one", VideoQualityHelper.normalizeChannelBaseName("Sports One HD"))
        assertEquals(VideoQualityTier.UHD_4K, VideoQualityHelper.detectChannelTierFromName("Sports One 4K"))
        assertEquals(VideoQualityTier.FULL_HD, VideoQualityHelper.detectChannelTierFromName("Sports One 1080p"))
        assertEquals(VideoQualityTier.HD, VideoQualityHelper.detectChannelTierFromName("Sports One HD"))
        assertEquals(VideoQualityTier.SD, VideoQualityHelper.detectChannelTierFromName("Sports One SD"))

        assertEquals(VideoQualityTier.UHD_4K, VideoQualityHelper.tierForResolution(3840, 2160))
        assertEquals(VideoQualityTier.FULL_HD, VideoQualityHelper.tierForResolution(1920, 1080))
        assertEquals(VideoQualityTier.HD, VideoQualityHelper.tierForResolution(1280, 720))

        val ch4k = LiveChannel(
            id = "1",
            number = 1,
            name = "Sports One 4K",
            logoUrl = "",
            categoryId = "cat_1",
            categoryName = "Sports",
            streamUrl = "http://s/1.ts",
            playlistId = "pl_1"
        )
        val chFhd = LiveChannel(
            id = "2",
            number = 2,
            name = "Sports One FHD",
            logoUrl = "",
            categoryId = "cat_1",
            categoryName = "Sports",
            streamUrl = "http://s/2.ts",
            playlistId = "pl_1"
        )
        val chOther = LiveChannel(
            id = "3",
            number = 3,
            name = "News Two HD",
            logoUrl = "",
            categoryId = "cat_2",
            categoryName = "News",
            streamUrl = "http://s/3.ts",
            playlistId = "pl_1"
        )

        val options = VideoQualityHelper.buildQualityOptions(
            tracks = Tracks.EMPTY,
            selectedTier = VideoQualityTier.UHD_4K,
            currentChannelId = "1",
            currentChannelTitle = "Sports One 4K",
            liveChannelQueue = listOf(ch4k, chFhd, chOther),
            deviceProfile = null
        )

        val autoOpt = options.first { it.tier == VideoQualityTier.AUTO }
        val fourKOpt = options.first { it.tier == VideoQualityTier.UHD_4K }
        val fhdOpt = options.first { it.tier == VideoQualityTier.FULL_HD }
        val hdOpt = options.firstOrNull { it.tier == VideoQualityTier.HD }
        val sdOpt = options.firstOrNull { it.tier == VideoQualityTier.SD }

        assertTrue(autoOpt.available)
        assertTrue(fourKOpt.available)
        assertEquals(VideoQualitySourceType.CURRENT_SOURCE, fourKOpt.sourceType)
        assertTrue(fhdOpt.available)
        assertEquals(VideoQualitySourceType.PROVIDER_CHANNEL, fhdOpt.sourceType)
        assertEquals("2", fhdOpt.alternativeChannel?.id)
        // HD and SD are NOT supplied by provider — must be omitted rather than fabricated
        assertNull(hdOpt)
        assertNull(sdOpt)

        assertEquals(VideoDecoderMode.AUTO, VideoDecoderMode.fromStored("unknown"))
        assertEquals(VideoDecoderMode.HARDWARE, VideoDecoderMode.fromStored("HARDWARE"))
        assertEquals(VideoDecoderMode.SOFTWARE, VideoDecoderMode.fromStored("software"))
        assertEquals(VideoSurfaceMode.TEXTURE_VIEW, VideoSurfaceMode.fromStored("TEXTURE_VIEW"))
        assertEquals(VideoPlayerEngine.EXOPLAYER, VideoPlayerEngine.fromStored(null))
        assertEquals(VideoPlayerEngine.VLC, VideoPlayerEngine.fromStored("vlc"))
        assertEquals(VideoPlayerEngine.EXTERNAL, VideoPlayerEngine.fromStored("EXTERNAL"))
    }
}
