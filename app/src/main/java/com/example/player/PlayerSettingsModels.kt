package com.example.player

enum class VideoPlayerEngine(val label: String, val subtitle: String) {
    EXOPLAYER(
        label = "ExoPlayer (Default)",
        subtitle = "Android Media3 ExoPlayer 1.11.1 with MediaCodec hardware acceleration & diagnostics"
    ),
    VLC(
        label = "VLC Player",
        subtitle = "LibVLC 3.7.7 engine with wide IPTV container support & software fallback"
    ),
    EXTERNAL(
        label = "External Player",
        subtitle = "Launch stream in another installed Android video player app"
    );

    companion object {
        fun fromStored(value: String?): VideoPlayerEngine {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EXOPLAYER
        }
    }
}

enum class VideoDecoderMode(val label: String, val subtitle: String) {
    AUTO(
        label = "Auto Hardware",
        subtitle = "Prefer hardware decoder; fallback on compatible streams"
    ),
    HARDWARE(
        label = "Hardware (when supported)",
        subtitle = "Use hardware decoding where supported; never force software 4K"
    ),
    SOFTWARE(
        label = "Software (when supported)",
        subtitle = "Use software decoding fallback where supported"
    );

    companion object {
        fun fromStored(value: String?): VideoDecoderMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
        }
    }
}

enum class VideoSurfaceMode(val label: String, val subtitle: String) {
    AUTO(
        label = "Auto (SurfaceView + Fallback)",
        subtitle = "Uses SurfaceView for 4K/HDR and falls back to TextureView if frames stall"
    ),
    SURFACE_VIEW(
        label = "SurfaceView (4K / HDR)",
        subtitle = "Hardware zero-copy surface; best for 4K and HDR"
    ),
    TEXTURE_VIEW(
        label = "TextureView (Compatibility)",
        subtitle = "GPU texture compositing if SurfaceView shows a black screen"
    );

    companion object {
        fun fromStored(value: String?): VideoSurfaceMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
        }
    }
}

data class PlayerLocalSettings(
    val preferredPlayerEngine: VideoPlayerEngine = VideoPlayerEngine.EXOPLAYER,
    val videoDecoderMode: VideoDecoderMode = VideoDecoderMode.AUTO,
    val defaultQualityTier: VideoQualityTier = VideoQualityTier.AUTO,
    val showTechnicalPlaybackInfo: Boolean = false,
    val videoSurfaceMode: VideoSurfaceMode = VideoSurfaceMode.AUTO,
    val watchHistoryEnabled: Boolean = true,
    val showFavouriteLiveFirst: Boolean = true
)
