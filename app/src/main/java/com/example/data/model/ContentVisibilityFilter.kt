package com.example.data.model

import androidx.annotation.Keep
import com.example.data.network.PlaylistCatalog

@Keep
data class PlaylistHiddenCategories(
    val liveCategoryIds: Set<String> = emptySet(),
    val movieCategoryIds: Set<String> = emptySet(),
    val seriesCategoryIds: Set<String> = emptySet()
) {
    fun hiddenSetFor(contentType: MediaContentType): Set<String> = when (contentType) {
        MediaContentType.LIVE -> liveCategoryIds
        MediaContentType.MOVIE -> movieCategoryIds
        MediaContentType.SERIES, MediaContentType.EPISODE -> seriesCategoryIds
    }

    fun isCategoryVisible(contentType: MediaContentType, categoryId: String): Boolean {
        return categoryId !in hiddenSetFor(contentType)
    }

    fun withCategoryVisibility(
        contentType: MediaContentType,
        categoryId: String,
        visible: Boolean
    ): PlaylistHiddenCategories = when (contentType) {
        MediaContentType.LIVE -> copy(
            liveCategoryIds = if (visible) liveCategoryIds - categoryId else liveCategoryIds + categoryId
        )
        MediaContentType.MOVIE -> copy(
            movieCategoryIds = if (visible) movieCategoryIds - categoryId else movieCategoryIds + categoryId
        )
        MediaContentType.SERIES, MediaContentType.EPISODE -> copy(
            seriesCategoryIds = if (visible) seriesCategoryIds - categoryId else seriesCategoryIds + categoryId
        )
    }

    fun hiddenCount(): Int = liveCategoryIds.size + movieCategoryIds.size + seriesCategoryIds.size

    fun isEmpty(): Boolean =
        liveCategoryIds.isEmpty() && movieCategoryIds.isEmpty() && seriesCategoryIds.isEmpty()
}

data class LiveTvDisplayGroups(
    val favouriteChannels: List<LiveChannel>,
    val otherChannels: List<LiveChannel>,
    val orderedQueue: List<LiveChannel>
)

object ContentVisibilityFilter {

    fun filterCatalog(
        catalog: PlaylistCatalog,
        hidden: PlaylistHiddenCategories
    ): PlaylistCatalog {
        if (hidden.isEmpty()) return catalog
        return catalog.copy(
            liveCategories = catalog.liveCategories.filter { it.id !in hidden.liveCategoryIds },
            liveChannels = catalog.liveChannels.filter { it.categoryId !in hidden.liveCategoryIds },
            vodCategories = catalog.vodCategories.filter { it.id !in hidden.movieCategoryIds },
            vodMovies = catalog.vodMovies.filter { it.categoryId !in hidden.movieCategoryIds },
            seriesCategories = catalog.seriesCategories.filter { it.id !in hidden.seriesCategoryIds },
            seriesList = catalog.seriesList.filter { it.categoryId !in hidden.seriesCategoryIds }
        )
    }

    fun partitionLiveChannelsForDisplay(
        channels: List<LiveChannel>,
        showFavouritesFirst: Boolean,
        isFavourite: (LiveChannel) -> Boolean
    ): LiveTvDisplayGroups {
        if (!showFavouritesFirst || channels.isEmpty()) {
            return LiveTvDisplayGroups(
                favouriteChannels = emptyList(),
                otherChannels = channels,
                orderedQueue = channels
            )
        }
        val favs = ArrayList<LiveChannel>()
        val others = ArrayList<LiveChannel>(channels.size)
        for (channel in channels) {
            if (isFavourite(channel)) {
                favs.add(channel)
            } else {
                others.add(channel)
            }
        }
        return LiveTvDisplayGroups(
            favouriteChannels = favs,
            otherChannels = others,
            orderedQueue = if (favs.isEmpty()) others else favs + others
        )
    }

    fun filterVisibleFavourites(
        favourites: List<FavouriteDoc>,
        rawCatalog: PlaylistCatalog?,
        activePlaylistId: String?,
        hiddenByPlaylist: Map<String, PlaylistHiddenCategories>
    ): List<FavouriteDoc> {
        if (favourites.isEmpty() || hiddenByPlaylist.isEmpty()) return favourites
        return favourites.filter { fav ->
            val hidden = hiddenByPlaylist[fav.playlistId] ?: return@filter true
            if (hidden.isEmpty()) return@filter true
            val contentType = when (fav.contentType) {
                "LIVE" -> MediaContentType.LIVE
                "MOVIE" -> MediaContentType.MOVIE
                else -> MediaContentType.SERIES
            }
            isItemVisible(
                itemId = fav.itemId,
                seriesId = if (contentType == MediaContentType.SERIES) fav.itemId else "",
                contentType = contentType,
                itemPlaylistId = fav.playlistId,
                categoryHint = fav.categoryName,
                rawCatalog = if (fav.playlistId == activePlaylistId) rawCatalog else null,
                hidden = hidden
            )
        }
    }

    fun filterVisibleContinueWatching(
        items: List<ContinueWatchingDoc>,
        rawCatalog: PlaylistCatalog?,
        activePlaylistId: String?,
        hiddenByPlaylist: Map<String, PlaylistHiddenCategories>
    ): List<ContinueWatchingDoc> {
        if (items.isEmpty() || hiddenByPlaylist.isEmpty()) return items
        return items.filter { item ->
            val hidden = hiddenByPlaylist[item.playlistId] ?: return@filter true
            if (hidden.isEmpty()) return@filter true
            val contentType = when (item.contentType) {
                "MOVIE" -> MediaContentType.MOVIE
                "EPISODE" -> MediaContentType.EPISODE
                else -> MediaContentType.LIVE
            }
            isItemVisible(
                itemId = item.itemId,
                seriesId = item.seriesId,
                contentType = contentType,
                itemPlaylistId = item.playlistId,
                categoryHint = if (contentType == MediaContentType.EPISODE) "" else item.subtitle,
                rawCatalog = if (item.playlistId == activePlaylistId) rawCatalog else null,
                hidden = hidden
            )
        }
    }

    fun filterVisibleWatchHistory(
        items: List<WatchHistoryDoc>,
        rawCatalog: PlaylistCatalog?,
        activePlaylistId: String?,
        hiddenByPlaylist: Map<String, PlaylistHiddenCategories>
    ): List<WatchHistoryDoc> {
        if (items.isEmpty() || hiddenByPlaylist.isEmpty()) return items
        return items.filter { item ->
            val hidden = hiddenByPlaylist[item.playlistId] ?: return@filter true
            if (hidden.isEmpty()) return@filter true
            val contentType = when (item.contentType) {
                "MOVIE" -> MediaContentType.MOVIE
                "EPISODE" -> MediaContentType.EPISODE
                else -> MediaContentType.LIVE
            }
            isItemVisible(
                itemId = item.itemId,
                seriesId = "",
                contentType = contentType,
                itemPlaylistId = item.playlistId,
                categoryHint = item.subtitle,
                rawCatalog = if (item.playlistId == activePlaylistId) rawCatalog else null,
                hidden = hidden
            )
        }
    }

    private fun isItemVisible(
        itemId: String,
        seriesId: String,
        contentType: MediaContentType,
        itemPlaylistId: String,
        categoryHint: String,
        rawCatalog: PlaylistCatalog?,
        hidden: PlaylistHiddenCategories
    ): Boolean {
        val hiddenIds = hidden.hiddenSetFor(contentType)
        if (hiddenIds.isEmpty()) return true

        if (rawCatalog != null) {
            when (contentType) {
                MediaContentType.LIVE -> {
                    val matched = rawCatalog.liveChannels.firstOrNull {
                        it.id == itemId && it.playlistId == itemPlaylistId
                    }
                    if (matched != null) return matched.categoryId !in hiddenIds
                    val hiddenNames = rawCatalog.liveCategories
                        .filter { it.id in hiddenIds }
                        .map { it.name.trim().lowercase() }
                        .toSet()
                    if (categoryHint.isNotBlank() && categoryHint.trim().lowercase() in hiddenNames) {
                        return false
                    }
                }
                MediaContentType.MOVIE -> {
                    val matched = rawCatalog.vodMovies.firstOrNull {
                        it.id == itemId && it.playlistId == itemPlaylistId
                    }
                    if (matched != null) return matched.categoryId !in hiddenIds
                    val hiddenNames = rawCatalog.vodCategories
                        .filter { it.id in hiddenIds }
                        .map { it.name.trim().lowercase() }
                        .toSet()
                    if (categoryHint.isNotBlank() && categoryHint.trim().lowercase() in hiddenNames) {
                        return false
                    }
                }
                MediaContentType.SERIES, MediaContentType.EPISODE -> {
                    val lookupId = seriesId.ifBlank { itemId }
                    val matchedBySeriesId = rawCatalog.seriesList.firstOrNull {
                        it.id == lookupId && it.playlistId == itemPlaylistId
                    }
                    if (matchedBySeriesId != null) return matchedBySeriesId.categoryId !in hiddenIds

                    // Episode watch history items lack seriesId directly; resolve from m3u details or series title prefix
                    val matchedSeriesByEpisode = rawCatalog.m3uSeriesDetails.values.firstOrNull { details ->
                        details.seasons.values.flatten().any { ep -> ep.id == itemId }
                    }?.series
                    if (matchedSeriesByEpisode != null) {
                        return matchedSeriesByEpisode.categoryId !in hiddenIds
                    }
                    val seriesTitlePrefix = categoryHint.substringBefore(" • ").trim()
                    if (seriesTitlePrefix.isNotBlank()) {
                        val matchedByTitle = rawCatalog.seriesList.firstOrNull {
                            it.title.equals(seriesTitlePrefix, ignoreCase = true) && it.playlistId == itemPlaylistId
                        }
                        if (matchedByTitle != null) return matchedByTitle.categoryId !in hiddenIds
                    }
                    val hiddenNames = rawCatalog.seriesCategories
                        .filter { it.id in hiddenIds }
                        .map { it.name.trim().lowercase() }
                        .toSet()
                    if (categoryHint.isNotBlank() && categoryHint.trim().lowercase() in hiddenNames) {
                        return false
                    }
                }
            }
        }

        // Fallback when raw catalog is not yet synced: check direct ID match on categoryHint (e.g. M3U group-title)
        if (categoryHint.isNotBlank() && hiddenIds.any { it.equals(categoryHint.trim(), ignoreCase = true) }) {
            return false
        }
        return true
    }
}
