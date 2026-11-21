package com.example.data.network

import com.example.data.model.XtreamAuthResponse
import com.example.data.model.XtreamCategoryDto
import com.example.data.model.XtreamEpgResponseDto
import com.example.data.model.XtreamLiveStreamDto
import com.example.data.model.XtreamSeriesDto
import com.example.data.model.XtreamSeriesInfoResponseDto
import com.example.data.model.XtreamVodInfoResponseDto
import com.example.data.model.XtreamVodStreamDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface XtreamApiService {
    @GET
    suspend fun authenticate(
        @Url apiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<XtreamAuthResponse>

    @GET
    suspend fun getLiveCategories(
        @Url apiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): Response<List<XtreamCategoryDto>>

    @GET
    suspend fun getLiveStreams(
        @Url apiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams"
    ): Response<List<XtreamLiveStreamDto>>

    @GET
    suspend fun getShortEpg(
        @Url apiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_id") streamId: String,
        @Query("limit") limit: Int = 4
    ): Response<XtreamEpgResponseDto>

    @GET
    suspend fun getVodCategories(
        @Url apiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): Response<List<XtreamCategoryDto>>

    @GET
    suspend fun getVodStreams(
        @Url apiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams"
    ): Response<List<XtreamVodStreamDto>>

    @GET
    suspend fun getVodInfo(
        @Url apiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_info",
        @Query("vod_id") vodId: String
    ): Response<XtreamVodInfoResponseDto>

    @GET
    suspend fun getSeriesCategories(
        @Url apiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): Response<List<XtreamCategoryDto>>

    @GET
    suspend fun getSeries(
        @Url apiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series"
    ): Response<List<XtreamSeriesDto>>

    @GET
    suspend fun getSeriesInfo(
        @Url apiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: String
    ): Response<XtreamSeriesInfoResponseDto>
}
