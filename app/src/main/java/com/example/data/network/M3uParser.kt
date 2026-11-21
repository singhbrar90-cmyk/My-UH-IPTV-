package com.example.data.network

import com.example.data.model.LiveChannel
import com.example.data.model.MediaCategory
import com.example.data.model.PlaylistType
import com.example.data.model.SavedPlaylist
import com.example.data.model.SeriesDetails
import com.example.data.model.SeriesEpisode
import com.example.data.model.TvSeries
import com.example.data.model.VodMovie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.UUID

class M3uRepository(
    private val httpClient: OkHttpClient = NetworkClientProvider.okHttpClient
) {
    suspend fun fetchAndValidatePlaylist(
        playlistName: String,
        m3uUrl: String,
        existingPlaylistId: String? = null
    ): Result<Pair<SavedPlaylist, PlaylistCatalog>> = withContext(Dispatchers.IO) {
        val cleanUrl = m3uUrl.trim()
        val parsedUrl = cleanUrl.toHttpUrlOrNull()
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid M3U URL. Enter a valid HTTP or HTTPS playlist URL.")
            )
        if (parsedUrl.scheme !in listOf("http", "https")) {
            return@withContext Result.failure(IllegalArgumentException("Only HTTP and HTTPS M3U URLs are supported."))
        }

        try {
            val request = Request.Builder().url(parsedUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("M3U server returned HTTP ${response.code}: ${response.message}")
                    )
                }
                val content = response.body?.string().orEmpty()
                if (content.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("M3U playlist is empty."))
                }
                val playlistId = existingPlaylistId ?: UUID.randomUUID().toString()
                val catalog = M3uParser.parse(content, playlistId)
                val totalEntries = catalog.liveChannels.size + catalog.vodMovies.size + catalog.seriesList.size
                if (totalEntries == 0) {
                    return@withContext Result.failure(
                        IllegalStateException("No playable channels or media streams found in this M3U file.")
                    )
                }
                val saved = SavedPlaylist(
                    id = playlistId,
                    name = playlistName.trim().ifBlank { "M3U Playlist" },
                    type = PlaylistType.M3U,
                    m3uUrl = cleanUrl,
                    accountStatus = "Active ($totalEntries items)",
                    lastSyncedAtMs = System.currentTimeMillis()
                )
                Result.success(saved to catalog)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

object M3uParser {
    private val attrRegex = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")
    private val episodePattern = Regex("""(?i)\bS(\d{1,2})[\s._-]*E(\d{1,3})\b""")
    private val vodExtensions = setOf(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".webm")

    fun parse(rawContent: String, playlistId: String): PlaylistCatalog {
        val reader = BufferedReader(StringReader(rawContent))
        val liveChannels = mutableListOf<LiveChannel>()
        val movies = mutableListOf<VodMovie>()
        val seriesEpisodes = mutableMapOf<String, MutableList<SeriesEpisode>>()
        val seriesGroupMap = mutableMapOf<String, String>()
        val liveCategorySet = linkedSetOf<String>()
        val vodCategorySet = linkedSetOf<String>()
        val seriesCategorySet = linkedSetOf<String>()

        var pendingName = ""
        var pendingLogo = ""
        var pendingGroup = "Uncategorized"
        var pendingTvgId = ""
        var index = 0

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#EXTM3U", ignoreCase = true)) {
                return@forEachLine
            }
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val commaIdx = line.indexOf(',')
                    val header = if (commaIdx >= 0) line.substring(0, commaIdx) else line
                    pendingName = if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else ""
                    val attrs = attrRegex.findAll(header).associate {
                        it.groupValues[1].lowercase() to it.groupValues[2].trim()
                    }
                    pendingLogo = attrs["tvg-logo"].orEmpty()
                    pendingGroup = attrs["group-title"]?.ifBlank { "Uncategorized" } ?: "Uncategorized"
                    pendingTvgId = attrs["tvg-id"].orEmpty()
                    if (pendingName.isBlank()) {
                        pendingName = attrs["tvg-name"].orEmpty()
                    }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val grp = line.substringAfter(':').trim()
                    if (grp.isNotBlank()) pendingGroup = grp
                }
                !line.startsWith("#") -> {
                    index++
                    val title = pendingName.ifBlank { "Stream $index" }
                    val lowerUrl = line.lowercase()
                    val lowerGroup = pendingGroup.lowercase()
                    val epMatch = episodePattern.find(title)

                    when {
                        epMatch != null || lowerGroup.contains("series") -> {
                            val seriesName = title.replace(episodePattern, "").trim(' ', '-', '|', ':').ifBlank { title }
                            seriesCategorySet.add(pendingGroup)
                            seriesGroupMap.putIfAbsent(seriesName, pendingGroup)
                            val seasonNum = epMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                            val epNum = epMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1
                            val epList = seriesEpisodes.getOrPut(seriesName) { mutableListOf() }
                            epList.add(
                                SeriesEpisode(
                                    id = "${playlistId}_m3u_ep_$index",
                                    seriesId = "${playlistId}_series_${seriesName.hashCode()}",
                                    seriesTitle = seriesName,
                                    seasonNumber = seasonNum,
                                    episodeNumber = epNum,
                                    title = title,
                                    plot = "",
                                    duration = "",
                                    thumbnailUrl = pendingLogo,
                                    streamUrl = line,
                                    playlistId = playlistId
                                )
                            )
                        }
                        vodExtensions.any { lowerUrl.substringBefore('?').endsWith(it) } ||
                            lowerGroup.contains("movie") || lowerGroup.contains("vod") -> {
                            vodCategorySet.add(pendingGroup)
                            movies.add(
                                VodMovie(
                                    id = "${playlistId}_m3u_vod_$index",
                                    title = title,
                                    posterUrl = pendingLogo,
                                    backdropUrl = pendingLogo,
                                    categoryId = pendingGroup,
                                    categoryName = pendingGroup,
                                    streamUrl = line,
                                    playlistId = playlistId
                                )
                            )
                        }
                        else -> {
                            liveCategorySet.add(pendingGroup)
                            liveChannels.add(
                                LiveChannel(
                                    id = "${playlistId}_m3u_live_$index",
                                    number = index,
                                    name = title,
                                    logoUrl = pendingLogo,
                                    categoryId = pendingGroup,
                                    categoryName = pendingGroup,
                                    streamUrl = line,
                                    epgChannelId = pendingTvgId,
                                    playlistId = playlistId
                                )
                            )
                        }
                    }
                    pendingName = ""
                    pendingLogo = ""
                    pendingGroup = "Uncategorized"
                    pendingTvgId = ""
                }
            }
        }

        val seriesDetailsMap = mutableMapOf<String, SeriesDetails>()
        val seriesList = seriesEpisodes.map { (name, eps) ->
            val first = eps.first()
            val groupName = seriesGroupMap[name] ?: "Series"
            val seriesObj = TvSeries(
                id = first.seriesId,
                title = name,
                posterUrl = first.thumbnailUrl,
                backdropUrl = first.thumbnailUrl,
                categoryId = groupName,
                categoryName = groupName,
                playlistId = playlistId
            )
            val groupedSeasons = eps.groupBy { it.seasonNumber }.toSortedMap()
                .mapValues { (_, list) -> list.sortedBy { it.episodeNumber } }
            seriesDetailsMap[seriesObj.id] = SeriesDetails(seriesObj, groupedSeasons)
            seriesObj
        }

        return PlaylistCatalog(
            liveCategories = liveCategorySet.map { MediaCategory(it, it) },
            liveChannels = liveChannels,
            vodCategories = vodCategorySet.map { MediaCategory(it, it) },
            vodMovies = movies,
            seriesCategories = seriesCategorySet.map { MediaCategory(it, it) },
            seriesList = seriesList,
            m3uSeriesDetails = seriesDetailsMap
        )
    }
}
