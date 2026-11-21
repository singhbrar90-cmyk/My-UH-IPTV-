package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ContinueWatchingDoc
import com.example.data.model.FavouriteDoc
import com.example.data.model.LiveChannel
import com.example.data.model.MediaContentType
import com.example.data.model.PlaybackRequest
import com.example.data.model.SavedPlaylist
import com.example.data.model.TvSeries
import com.example.data.model.UiState
import com.example.data.model.VodMovie
import com.example.data.network.PlaylistCatalog
import com.example.ui.components.LiveChannelTileCard
import com.example.ui.components.PosterArtwork
import com.example.ui.components.PosterCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.UhBlack
import com.example.ui.theme.UhBorder
import com.example.ui.theme.UhCrimson

@Composable
fun HomeScreen(
    activePlaylist: SavedPlaylist?,
    catalogState: UiState<PlaylistCatalog>,
    continueWatchingState: UiState<List<ContinueWatchingDoc>>,
    favouritesState: UiState<List<FavouriteDoc>>,
    isFavouriteCheck: (String, MediaContentType, String) -> Boolean,
    onAddPlaylistClick: () -> Unit,
    onRefreshPlaylist: () -> Unit,
    onNavigateTab: (String) -> Unit,
    onPlayLiveChannel: (LiveChannel, List<LiveChannel>) -> Unit,
    onOpenMovie: (VodMovie) -> Unit,
    onOpenSeries: (TvSeries) -> Unit,
    onResumeContinueWatching: (ContinueWatchingDoc) -> Unit,
    onPlayFavourite: (FavouriteDoc) -> Unit,
    onToggleFavourite: (String, MediaContentType, String, String, String, String, String) -> Unit
) {
    val catalog = (catalogState as? UiState.Success)?.data
    val continueItems = (continueWatchingState as? UiState.Success)?.data.orEmpty()
    val favourites = (favouritesState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UhBlack),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Top Brand Bar + Active Playlist Pill
        item(key = "top_brand") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = UhCrimson,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "UH",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "UH IPTV",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = activePlaylist?.name ?: "No playlist connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (activePlaylist != null) {
                        IconButton(
                            onClick = onRefreshPlaylist,
                            modifier = Modifier.testTag("home_refresh_playlist_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh playlist"
                            )
                        }
                    }
                    IconButton(
                        onClick = onAddPlaylistClick,
                        modifier = Modifier.testTag("home_add_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add playlist"
                        )
                    }
                }
            }
        }

        // 2. Featured Content Banner
        item(key = "featured_banner") {
            val featuredMovie = catalog?.vodMovies?.firstOrNull()
            val featuredSeries = catalog?.seriesList?.firstOrNull()
            val featuredLive = catalog?.liveChannels?.firstOrNull()
            FeaturedBanner(
                featuredMovie = featuredMovie,
                featuredSeries = featuredSeries,
                featuredLive = featuredLive,
                isFavouriteCheck = isFavouriteCheck,
                onPlayMovie = onOpenMovie,
                onPlaySeries = onOpenSeries,
                onPlayLive = { ch -> onPlayLiveChannel(ch, catalog?.liveChannels.orEmpty()) },
                onAddPlaylistClick = onAddPlaylistClick,
                onToggleFavourite = onToggleFavourite
            )
        }

        // Loading / Error Banner for active catalog
        if (catalogState is UiState.Loading) {
            item(key = "catalog_loading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = UhCrimson,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Syncing channels, movies & series from provider...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (catalogState is UiState.Error) {
            item(key = "catalog_error") {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = catalogState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = onRefreshPlaylist) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Empty State Onboarding Card if no playlist is connected
        if (activePlaylist == null) {
            item(key = "empty_playlist_card") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, UhBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_playlist_cinema_1795258611373),
                            contentDescription = "Connect IPTV Playlist",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.no_playlists_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.no_playlists_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onAddPlaylistClick,
                            colors = ButtonDefaults.buttonColors(containerColor = UhCrimson),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("empty_connect_playlist_button")
                        ) {
                            Text("Connect Xtream or M3U Playlist", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Continue Watching Row
        if (continueItems.isNotEmpty()) {
            item(key = "cw_header") {
                SectionHeader(title = "Continue Watching")
            }
            item(key = "cw_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(continueItems, key = { it.id }) { item ->
                        val fraction = if (item.durationMs > 0L) {
                            item.positionMs.toFloat() / item.durationMs.toFloat()
                        } else {
                            0.1f
                        }
                        PosterCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            imageUrl = item.posterUrl,
                            badgeText = item.contentType,
                            progressFraction = fraction,
                            onClick = { onResumeContinueWatching(item) },
                            testTag = "cw_card_${item.itemId}"
                        )
                    }
                }
            }
        }

        // 4. Live TV Row
        val liveChannels = catalog?.liveChannels.orEmpty()
        if (liveChannels.isNotEmpty()) {
            item(key = "live_header") {
                SectionHeader(
                    title = "Live TV",
                    actionLabel = "See All",
                    onActionClick = { onNavigateTab("live") }
                )
            }
            item(key = "live_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(liveChannels.take(20), key = { it.id }) { channel ->
                        val fav = isFavouriteCheck(channel.id, MediaContentType.LIVE, channel.playlistId)
                        LiveChannelTileCard(
                            channelNumber = channel.number,
                            name = channel.name,
                            category = channel.categoryName,
                            logoUrl = channel.logoUrl,
                            isFavourite = fav,
                            onClick = { onPlayLiveChannel(channel, liveChannels) },
                            onToggleFavourite = {
                                onToggleFavourite(
                                    channel.id,
                                    MediaContentType.LIVE,
                                    channel.name,
                                    channel.logoUrl,
                                    channel.categoryName,
                                    channel.streamUrl,
                                    channel.playlistId
                                )
                            },
                            testTag = "home_live_${channel.id}"
                        )
                    }
                }
            }
        }

        // 5. Movies Row
        val movies = catalog?.vodMovies.orEmpty()
        if (movies.isNotEmpty()) {
            item(key = "movies_header") {
                SectionHeader(
                    title = "Movies",
                    actionLabel = "See All",
                    onActionClick = { onNavigateTab("movies") }
                )
            }
            item(key = "movies_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(movies.take(20), key = { it.id }) { movie ->
                        val fav = isFavouriteCheck(movie.id, MediaContentType.MOVIE, movie.playlistId)
                        PosterCard(
                            title = movie.title,
                            subtitle = movie.categoryName,
                            imageUrl = movie.posterUrl,
                            badgeText = if (movie.rating.isNotBlank()) "★ ${movie.rating}" else "HD",
                            isFavourite = fav,
                            onClick = { onOpenMovie(movie) },
                            onToggleFavourite = {
                                onToggleFavourite(
                                    movie.id,
                                    MediaContentType.MOVIE,
                                    movie.title,
                                    movie.posterUrl,
                                    movie.categoryName,
                                    movie.streamUrl,
                                    movie.playlistId
                                )
                            },
                            testTag = "home_movie_${movie.id}"
                        )
                    }
                }
            }
        }

        // 6. TV Series Row
        val seriesList = catalog?.seriesList.orEmpty()
        if (seriesList.isNotEmpty()) {
            item(key = "series_header") {
                SectionHeader(
                    title = "TV Series",
                    actionLabel = "See All",
                    onActionClick = { onNavigateTab("series") }
                )
            }
            item(key = "series_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(seriesList.take(20), key = { it.id }) { series ->
                        val fav = isFavouriteCheck(series.id, MediaContentType.SERIES, series.playlistId)
                        PosterCard(
                            title = series.title,
                            subtitle = series.categoryName,
                            imageUrl = series.posterUrl,
                            badgeText = "SERIES",
                            isFavourite = fav,
                            onClick = { onOpenSeries(series) },
                            onToggleFavourite = {
                                onToggleFavourite(
                                    series.id,
                                    MediaContentType.SERIES,
                                    series.title,
                                    series.posterUrl,
                                    series.categoryName,
                                    "",
                                    series.playlistId
                                )
                            },
                            testTag = "home_series_${series.id}"
                        )
                    }
                }
            }
        }

        // 7. Favourites Row
        if (favourites.isNotEmpty()) {
            item(key = "fav_header") {
                SectionHeader(title = "Favourites")
            }
            item(key = "fav_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(favourites, key = { it.id }) { fav ->
                        val type = when (fav.contentType) {
                            "LIVE" -> MediaContentType.LIVE
                            "MOVIE" -> MediaContentType.MOVIE
                            else -> MediaContentType.SERIES
                        }
                        PosterCard(
                            title = fav.title,
                            subtitle = fav.categoryName,
                            imageUrl = fav.posterUrl,
                            badgeText = fav.contentType,
                            isFavourite = true,
                            onClick = { onPlayFavourite(fav) },
                            onToggleFavourite = {
                                onToggleFavourite(
                                    fav.itemId,
                                    type,
                                    fav.title,
                                    fav.posterUrl,
                                    fav.categoryName,
                                    fav.streamUrl,
                                    fav.playlistId
                                )
                            },
                            testTag = "home_fav_${fav.itemId}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedBanner(
    featuredMovie: VodMovie?,
    featuredSeries: TvSeries?,
    featuredLive: LiveChannel?,
    isFavouriteCheck: (String, MediaContentType, String) -> Boolean,
    onPlayMovie: (VodMovie) -> Unit,
    onPlaySeries: (TvSeries) -> Unit,
    onPlayLive: (LiveChannel) -> Unit,
    onAddPlaylistClick: () -> Unit,
    onToggleFavourite: (String, MediaContentType, String, String, String, String, String) -> Unit
) {
    val bannerTitle: String
    val bannerSubtitle: String
    val bannerImage: String
    val badgeLabel: String

    when {
        featuredMovie != null -> {
            bannerTitle = featuredMovie.title
            bannerSubtitle = featuredMovie.categoryName.ifBlank { "Featured Movie" }
            bannerImage = featuredMovie.backdropUrl.ifBlank { featuredMovie.posterUrl }
            badgeLabel = "FEATURED MOVIE"
        }
        featuredSeries != null -> {
            bannerTitle = featuredSeries.title
            bannerSubtitle = featuredSeries.categoryName.ifBlank { "Featured Series" }
            bannerImage = featuredSeries.backdropUrl.ifBlank { featuredSeries.posterUrl }
            badgeLabel = "FEATURED SERIES"
        }
        featuredLive != null -> {
            bannerTitle = featuredLive.name
            bannerSubtitle = featuredLive.categoryName.ifBlank { "Live Broadcast" }
            bannerImage = featuredLive.logoUrl
            badgeLabel = "LIVE NOW"
        }
        else -> {
            bannerTitle = "Ready for Ultra-Crisp IPTV"
            bannerSubtitle = "Hindi • Punjabi • English Auto Audio Selection"
            bannerImage = ""
            badgeLabel = "UH IPTV PLAYER"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(250.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, UhBorder, RoundedCornerShape(18.dp))
    ) {
        if (bannerImage.isNotBlank()) {
            PosterArtwork(
                imageUrl = bannerImage,
                contentDescription = bannerTitle,
                fallbackType = "MOVIE",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_hero_featured_backdrop_1795258611665),
                contentDescription = bannerTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.65f),
                            UhBlack.copy(alpha = 0.96f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        ) {
            Surface(
                color = UhCrimson,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = bannerTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = bannerSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        when {
                            featuredMovie != null -> onPlayMovie(featuredMovie)
                            featuredSeries != null -> onPlaySeries(featuredSeries)
                            featuredLive != null -> onPlayLive(featuredLive)
                            else -> onAddPlaylistClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("featured_play_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (featuredMovie != null || featuredSeries != null || featuredLive != null) "Play Now" else "Add Playlist",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (featuredMovie != null) {
                    val isFav = isFavouriteCheck(featuredMovie.id, MediaContentType.MOVIE, featuredMovie.playlistId)
                    OutlinedButton(
                        onClick = {
                            onToggleFavourite(
                                featuredMovie.id,
                                MediaContentType.MOVIE,
                                featuredMovie.title,
                                featuredMovie.posterUrl,
                                featuredMovie.categoryName,
                                featuredMovie.streamUrl,
                                featuredMovie.playlistId
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favourite",
                            tint = if (isFav) UhCrimson else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isFav) "Saved" else "Favourite", color = Color.White)
                    }
                }
            }
        }
    }
}
