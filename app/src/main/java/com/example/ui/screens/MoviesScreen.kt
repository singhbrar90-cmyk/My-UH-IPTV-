package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ContinueWatchingDoc
import com.example.data.model.MediaContentType
import com.example.data.model.UiState
import com.example.data.model.VodMovie
import com.example.data.network.PlaylistCatalog
import com.example.ui.components.CategoryFilterRow
import com.example.ui.components.PosterArtwork
import com.example.ui.components.PosterCard
import com.example.ui.theme.UhBlack
import com.example.ui.theme.UhCrimson
import com.example.ui.theme.UhMuted

@Composable
fun MoviesScreen(
    catalogState: UiState<PlaylistCatalog>,
    continueWatchingList: List<ContinueWatchingDoc>,
    selectedMovieDetail: VodMovie?,
    isFavouriteCheck: (String, MediaContentType, String) -> Boolean,
    onOpenMovieDetail: (VodMovie) -> Unit,
    onCloseMovieDetail: () -> Unit,
    onPlayMovie: (VodMovie, Long) -> Unit,
    onToggleFavourite: (VodMovie) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    val catalog = (catalogState as? UiState.Success)?.data
    val categories = catalog?.vodCategories.orEmpty()
    val allMovies = catalog?.vodMovies.orEmpty()

    androidx.compose.runtime.LaunchedEffect(categories) {
        if (selectedCategoryId != null && categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = null
        }
    }

    val filteredMovies = remember(allMovies, searchQuery, selectedCategoryId) {
        allMovies.filter { movie ->
            val matchesCat = selectedCategoryId == null || movie.categoryId == selectedCategoryId
            val matchesQuery = searchQuery.isBlank() ||
                movie.title.contains(searchQuery, ignoreCase = true) ||
                movie.categoryName.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UhBlack)
    ) {
        Text(
            text = "Movies",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search movies...") },
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
                .testTag("movies_search_input")
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

        if (filteredMovies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = UhMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (allMovies.isEmpty()) "No movies available in active playlist." else "No movies match your search.",
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
                items(filteredMovies, key = { it.id }) { movie ->
                    val cw = continueWatchingList.firstOrNull { it.itemId == movie.id && it.contentType == "MOVIE" }
                    val progress = if (cw != null && cw.durationMs > 0L) {
                        cw.positionMs.toFloat() / cw.durationMs.toFloat()
                    } else null
                    val fav = isFavouriteCheck(movie.id, MediaContentType.MOVIE, movie.playlistId)

                    PosterCard(
                        title = movie.title,
                        subtitle = movie.categoryName,
                        imageUrl = movie.posterUrl,
                        badgeText = if (movie.rating.isNotBlank()) "★ ${movie.rating}" else movie.containerExtension.uppercase(),
                        isFavourite = fav,
                        progressFraction = progress,
                        onClick = { onOpenMovieDetail(movie) },
                        onToggleFavourite = { onToggleFavourite(movie) },
                        testTag = "movie_grid_${movie.id}"
                    )
                }
            }
        }
    }

    if (selectedMovieDetail != null) {
        val cw = continueWatchingList.firstOrNull {
            it.itemId == selectedMovieDetail.id && it.contentType == "MOVIE"
        }
        val isFav = isFavouriteCheck(
            selectedMovieDetail.id,
            MediaContentType.MOVIE,
            selectedMovieDetail.playlistId
        )
        MovieDetailDialog(
            movie = selectedMovieDetail,
            resumePositionMs = cw?.positionMs ?: 0L,
            isFavourite = isFav,
            onPlay = { resumePos ->
                onCloseMovieDetail()
                onPlayMovie(selectedMovieDetail, resumePos)
            },
            onToggleFavourite = { onToggleFavourite(selectedMovieDetail) },
            onDismiss = onCloseMovieDetail
        )
    }
}

@Composable
private fun MovieDetailDialog(
    movie: VodMovie,
    resumePositionMs: Long,
    isFavourite: Boolean,
    onPlay: (Long) -> Unit,
    onToggleFavourite: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    PosterArtwork(
                        imageUrl = movie.backdropUrl.ifBlank { movie.posterUrl },
                        contentDescription = movie.title,
                        fallbackType = "MOVIE",
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close movie detail",
                            tint = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                val meta = listOfNotNull(
                    movie.categoryName.takeIf { it.isNotBlank() },
                    movie.year.takeIf { it.isNotBlank() },
                    movie.duration.takeIf { it.isNotBlank() },
                    movie.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }
                ).joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (movie.plot.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = movie.plot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { onPlay(resumePositionMs) },
                        colors = ButtonDefaults.buttonColors(containerColor = UhCrimson),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("movie_detail_play_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (resumePositionMs > 5000L) "Resume Movie" else "Watch Movie",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onToggleFavourite,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favourite",
                            tint = if (isFavourite) UhCrimson else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
