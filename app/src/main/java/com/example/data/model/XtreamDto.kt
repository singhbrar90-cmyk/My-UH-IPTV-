package com.example.data.model

import androidx.annotation.Keep
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.ToJson

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class FlexibleString

class FlexibleNumberAndStringAdapter {
    @FromJson
    @FlexibleString
    fun fromJson(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.NUMBER -> reader.nextString()
            JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                ""
            }
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    @ToJson
    fun toJson(@FlexibleString value: String): String = value
}

@Keep
data class XtreamAuthResponse(
    @Json(name = "user_info") val userInfo: XtreamUserInfo? = null,
    @Json(name = "server_info") val serverInfo: XtreamServerInfo? = null
)

@Keep
data class XtreamUserInfo(
    @FlexibleString @Json(name = "username") val username: String = "",
    @FlexibleString @Json(name = "password") val password: String = "",
    @FlexibleString @Json(name = "message") val message: String = "",
    @FlexibleString @Json(name = "auth") val auth: String = "0",
    @FlexibleString @Json(name = "status") val status: String = "",
    @FlexibleString @Json(name = "exp_date") val expDate: String = "",
    @FlexibleString @Json(name = "is_trial") val isTrial: String = "",
    @FlexibleString @Json(name = "active_cons") val activeCons: String = "",
    @FlexibleString @Json(name = "max_connections") val maxConnections: String = "",
    @Json(name = "allowed_output_formats") val allowedOutputFormats: List<String>? = null
)

@Keep
data class XtreamServerInfo(
    @FlexibleString @Json(name = "url") val url: String = "",
    @FlexibleString @Json(name = "port") val port: String = "",
    @FlexibleString @Json(name = "https_port") val httpsPort: String = "",
    @FlexibleString @Json(name = "server_protocol") val serverProtocol: String = "http",
    @FlexibleString @Json(name = "timezone") val timezone: String = ""
)

@Keep
data class XtreamCategoryDto(
    @FlexibleString @Json(name = "category_id") val categoryId: String = "",
    @FlexibleString @Json(name = "category_name") val categoryName: String = ""
)

@Keep
data class XtreamLiveStreamDto(
    @FlexibleString @Json(name = "num") val num: String = "0",
    @FlexibleString @Json(name = "name") val name: String = "",
    @FlexibleString @Json(name = "stream_id") val streamId: String = "",
    @FlexibleString @Json(name = "stream_icon") val streamIcon: String = "",
    @FlexibleString @Json(name = "epg_channel_id") val epgChannelId: String = "",
    @FlexibleString @Json(name = "category_id") val categoryId: String = "",
    @FlexibleString @Json(name = "direct_source") val directSource: String = ""
)

@Keep
data class XtreamEpgResponseDto(
    @Json(name = "epg_listings") val listings: List<XtreamEpgListingDto>? = null
)

@Keep
data class XtreamEpgListingDto(
    @FlexibleString @Json(name = "id") val id: String = "",
    @FlexibleString @Json(name = "title") val title: String = "",
    @FlexibleString @Json(name = "description") val description: String = "",
    @FlexibleString @Json(name = "start") val start: String = "",
    @FlexibleString @Json(name = "end") val end: String = "",
    @FlexibleString @Json(name = "start_timestamp") val startTimestamp: String = "",
    @FlexibleString @Json(name = "stop_timestamp") val stopTimestamp: String = "",
    @FlexibleString @Json(name = "now_playing") val nowPlaying: String = "0"
)

@Keep
data class XtreamVodStreamDto(
    @FlexibleString @Json(name = "stream_id") val streamId: String = "",
    @FlexibleString @Json(name = "name") val name: String = "",
    @FlexibleString @Json(name = "stream_icon") val streamIcon: String = "",
    @FlexibleString @Json(name = "rating") val rating: String = "",
    @FlexibleString @Json(name = "category_id") val categoryId: String = "",
    @FlexibleString @Json(name = "container_extension") val containerExtension: String = "mp4",
    @FlexibleString @Json(name = "direct_source") val directSource: String = ""
)

@Keep
data class XtreamVodInfoResponseDto(
    @Json(name = "info") val info: XtreamVodInfoDto? = null,
    @Json(name = "movie_data") val movieData: XtreamVodMovieDataDto? = null
)

@Keep
data class XtreamVodMovieDataDto(
    @FlexibleString @Json(name = "stream_id") val streamId: String = "",
    @FlexibleString @Json(name = "container_extension") val containerExtension: String = "",
    @FlexibleString @Json(name = "direct_source") val directSource: String = ""
)

@Keep
data class XtreamVodInfoDto(
    @FlexibleString @Json(name = "movie_image") val movieImage: String = "",
    @FlexibleString @Json(name = "plot") val plot: String = "",
    @FlexibleString @Json(name = "genre") val genre: String = "",
    @FlexibleString @Json(name = "releasedate") val releaseDate: String = "",
    @FlexibleString @Json(name = "duration") val duration: String = "",
    @FlexibleString @Json(name = "rating") val rating: String = ""
)

@Keep
data class XtreamSeriesDto(
    @FlexibleString @Json(name = "series_id") val seriesId: String = "",
    @FlexibleString @Json(name = "name") val name: String = "",
    @FlexibleString @Json(name = "cover") val cover: String = "",
    @FlexibleString @Json(name = "plot") val plot: String = "",
    @FlexibleString @Json(name = "genre") val genre: String = "",
    @FlexibleString @Json(name = "releaseDate") val releaseDate: String = "",
    @FlexibleString @Json(name = "rating") val rating: String = "",
    @FlexibleString @Json(name = "category_id") val categoryId: String = ""
)

@Keep
data class XtreamSeriesInfoResponseDto(
    @Json(name = "info") val info: XtreamSeriesDetailInfoDto? = null,
    @Json(name = "episodes") val episodes: Map<String, List<XtreamEpisodeDto>>? = null
)

@Keep
data class XtreamSeriesDetailInfoDto(
    @FlexibleString @Json(name = "name") val name: String = "",
    @FlexibleString @Json(name = "cover") val cover: String = "",
    @FlexibleString @Json(name = "plot") val plot: String = "",
    @FlexibleString @Json(name = "genre") val genre: String = "",
    @FlexibleString @Json(name = "rating") val rating: String = ""
)

@Keep
data class XtreamEpisodeDto(
    @FlexibleString @Json(name = "id") val id: String = "",
    @FlexibleString @Json(name = "episode_num") val episodeNum: String = "1",
    @FlexibleString @Json(name = "title") val title: String = "",
    @FlexibleString @Json(name = "container_extension") val containerExtension: String = "mp4",
    @FlexibleString @Json(name = "season") val season: String = "1",
    @FlexibleString @Json(name = "direct_source") val directSource: String = "",
    @Json(name = "info") val info: XtreamEpisodeInfoDto? = null
)

@Keep
data class XtreamEpisodeInfoDto(
    @FlexibleString @Json(name = "movie_image") val movieImage: String = "",
    @FlexibleString @Json(name = "plot") val plot: String = "",
    @FlexibleString @Json(name = "duration") val duration: String = ""
)
