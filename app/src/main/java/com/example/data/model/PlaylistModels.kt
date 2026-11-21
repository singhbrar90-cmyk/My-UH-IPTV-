package com.example.data.model

import androidx.annotation.Keep

enum class PlaylistType {
    XTREAM,
    M3U
}

@Keep
data class SavedPlaylist(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val accountStatus: String = "Active",
    val expirationDate: String = "",
    val maxConnections: String = "",
    val lastSyncedAtMs: Long = 0L,
    val allowedOutputFormats: List<String> = emptyList()
)

enum class MediaContentType {
    LIVE,
    MOVIE,
    EPISODE,
    SERIES
}

data class MediaCategory(
    val id: String,
    val name: String
)

data class LiveChannel(
    val id: String,
    val number: Int,
    val name: String,
    val logoUrl: String,
    val categoryId: String,
    val categoryName: String,
    val streamUrl: String,
    val epgChannelId: String = "",
    val playlistId: String,
    val candidateUrls: List<String> = emptyList()
)

data class EpgProgram(
    val id: String,
    val title: String,
    val description: String,
    val startTimeLabel: String,
    val endTimeLabel: String,
    val isLiveNow: Boolean
)

data class VodMovie(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val categoryId: String,
    val categoryName: String,
    val streamUrl: String,
    val containerExtension: String = "mp4",
    val rating: String = "",
    val year: String = "",
    val plot: String = "",
    val genre: String = "",
    val duration: String = "",
    val playlistId: String,
    val candidateUrls: List<String> = emptyList()
)

data class TvSeries(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val categoryId: String,
    val categoryName: String,
    val rating: String = "",
    val year: String = "",
    val plot: String = "",
    val genre: String = "",
    val playlistId: String
)

data class SeriesEpisode(
    val id: String,
    val seriesId: String,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String,
    val duration: String,
    val thumbnailUrl: String,
    val streamUrl: String,
    val playlistId: String,
    val candidateUrls: List<String> = emptyList()
)

data class SeriesDetails(
    val series: TvSeries,
    val seasons: Map<Int, List<SeriesEpisode>>
)

data class PlaybackRequest(
    val itemId: String,
    val contentType: MediaContentType,
    val title: String,
    val subtitle: String,
    val posterUrl: String,
    val streamUrl: String,
    val playlistId: String,
    val seriesId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val resumePositionMs: Long = 0L,
    val preferredAudioLang: String = "",
    val liveChannelQueue: List<LiveChannel> = emptyList(),
    val episodeQueue: List<SeriesEpisode> = emptyList(),
    val candidateUrls: List<String> = emptyList(),
    val isDiagnosticSample: Boolean = false
)

data class AudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val languageCode: String,
    val label: String,
    val channels: Int,
    val bitrate: Int,
    val selected: Boolean
)
