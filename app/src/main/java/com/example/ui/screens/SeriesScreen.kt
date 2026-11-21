package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ContinueWatchingDoc
import com.example.data.model.MediaContentType
import com.example.data.model.SeriesDetails
import com.example.data.model.SeriesEpisode
import com.example.data.model.TvSeries
import com.example.data.model.UiState
import com.example.data.network.PlaylistCatalog
import com.example.ui.components.CategoryFilterRow
import com.example.ui.components.PosterArtwork
import com.example.ui.components.PosterCard
import com.example.ui.theme.UhBlack
import com.example.ui.theme.UhBorder
import com.example.ui.theme.UhCrimson
import com.example.ui.theme.UhMuted

@Composable
fun SeriesScreen(
    catalogState: UiState<PlaylistCatalog>,
    selectedSeriesDetail: UiState<SeriesDetails>?,
    continueWatchingList: List<ContinueWatchingDoc>,
    isFavouriteCheck: (String, MediaContentType, String) -> Boolean,
    onOpenSeriesDetail: (TvSeries) -> Unit,
    onCloseSeriesDetail: () -> Unit,
    onPlayEpisode: (SeriesEpisode, List<SeriesEpisode>, Long) -> Unit,
    onToggleSeriesFavourite: (TvSeries) -> Unit
) {
    if (selectedSeriesDetail != null) {
        BackHandler { onCloseSeriesDetail() }
        SeriesDetailView(
            state = selectedSeriesDetail,
            continueWatchingList = continueWatchingList,
            isFavouriteCheck = isFavouriteCheck,
            onBack = onCloseSeriesDetail,
            onPlayEpisode = onPlayEpisode,
            onToggleSeriesFavourite = onToggleSeriesFavourite
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    val catalog = (catalogState as? UiState.Success)?.data
    val categories = catalog?.seriesCategories.orEmpty()
    val allSeries = catalog?.seriesList.orEmpty()

    LaunchedEffect(categories) {
        if (selectedCategoryId != null && categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = null
        }
    }

    val filteredSeries = remember(allSeries, searchQuery, selectedCategoryId) {
        allSeries.filter { s ->
            val matchesCat = selectedCategoryId == null || s.categoryId == selectedCategoryId
            val matchesQuery = searchQuery.isBlank() ||
                s.title.contains(searchQuery, ignoreCase = true) ||
                s.categoryName.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UhBlack)
    ) {
        Text(
            text = "TV Series",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search TV series...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("series_search_input")
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (categories.isNotEmpty()) {
            CategoryFilterRow(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onSelectCategory = { selectedCategoryId = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filteredSeries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Tv,
                        contentDescription = null,
                        tint = UhMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (allSeries.isEmpty()) "No TV series available in active playlist." else "No series match your search.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredSeries, key = { it.id }) { series ->
                    val fav = isFavouriteCheck(series.id, MediaContentType.SERIES, series.playlistId)
                    PosterCard(
                        title = series.title,
                        subtitle = series.categoryName,
                        imageUrl = series.posterUrl,
                        badgeText = "SERIES",
                        isFavourite = fav,
                        onClick = { onOpenSeriesDetail(series) },
                        onToggleFavourite = { onToggleSeriesFavourite(series) },
                        testTag = "series_grid_${series.id}"
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailView(
    state: UiState<SeriesDetails>,
    continueWatchingList: List<ContinueWatchingDoc>,
    isFavouriteCheck: (String, MediaContentType, String) -> Boolean,
    onBack: () -> Unit,
    onPlayEpisode: (SeriesEpisode, List<SeriesEpisode>, Long) -> Unit,
    onToggleSeriesFavourite: (TvSeries) -> Unit
) {
    when (state) {
        is UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UhBlack),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = UhCrimson)
            }
        }
        is UiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UhBlack)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }
        is UiState.Success -> {
            val details = state.data
            val series = details.series
            val seasonKeys = remember(details) { details.seasons.keys.sorted() }
            var selectedSeason by remember(details) { mutableIntStateOf(seasonKeys.firstOrNull() ?: 1) }
            LaunchedEffect(seasonKeys) {
                if (selectedSeason !in seasonKeys && seasonKeys.isNotEmpty()) {
                    selectedSeason = seasonKeys.first()
                }
            }
            val episodes = details.seasons[selectedSeason].orEmpty()
            val allEpisodesOrdered = remember(details) {
                seasonKeys.flatMap { details.seasons[it].orEmpty() }
            }
            val isFav = isFavouriteCheck(series.id, MediaContentType.SERIES, series.playlistId)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UhBlack),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                item(key = "series_hero") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        PosterArtwork(
                            imageUrl = series.backdropUrl.ifBlank { series.posterUrl },
                            contentDescription = series.title,
                            fallbackType = "SERIES",
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.3f), UhBlack)
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("series_detail_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { onToggleSeriesFavourite(series) }) {
                                Icon(
                                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favourite series",
                                    tint = if (isFav) UhCrimson else Color.White
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = series.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            if (series.genre.isNotBlank() || series.rating.isNotBlank()) {
                                Text(
                                    text = listOfNotNull(
                                        series.genre.takeIf { it.isNotBlank() },
                                        series.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (series.plot.isNotBlank()) {
                    item(key = "series_plot") {
                        Text(
                            text = series.plot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                if (seasonKeys.isNotEmpty()) {
                    item(key = "season_tabs") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(seasonKeys, key = { it }) { seasonNum ->
                                val selected = seasonNum == selectedSeason
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedSeason = seasonNum },
                                    label = { Text("Season $seasonNum") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = UhCrimson,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                items(episodes, key = { it.id }) { ep ->
                    val cw = continueWatchingList.firstOrNull {
                        it.itemId == ep.id && it.contentType == "EPISODE"
                    }
                    val resumeMs = cw?.positionMs ?: 0L
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .border(1.dp, UhBorder, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPlayEpisode(ep, allEpisodesOrdered, resumeMs) }
                            .testTag("episode_row_${ep.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                PosterArtwork(
                                    imageUrl = ep.thumbnailUrl,
                                    contentDescription = ep.title,
                                    fallbackType = "SERIES",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "E${ep.episodeNumber} • ${ep.title}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = buildString {
                                    if (ep.duration.isNotBlank()) append(ep.duration)
                                    if (resumeMs > 5000L) {
                                        if (isNotEmpty()) append(" • ")
                                        append("Resume")
                                    }
                                }
                                if (sub.isNotBlank()) {
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UhCrimson
                                    )
                                }
                                if (ep.plot.isNotBlank()) {
                                    Text(
                                        text = ep.plot,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
