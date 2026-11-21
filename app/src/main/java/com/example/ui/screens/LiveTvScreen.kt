package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ContentVisibilityFilter
import com.example.data.model.EpgProgram
import com.example.data.model.LiveChannel
import com.example.data.model.MediaCategory
import com.example.data.model.MediaContentType
import com.example.data.model.UiState
import com.example.data.network.PlaylistCatalog
import com.example.ui.components.CategoryFilterRow
import com.example.ui.components.PosterArtwork
import com.example.ui.theme.UhBlack
import com.example.ui.theme.UhBorder
import com.example.ui.theme.UhCrimson
import com.example.ui.theme.UhLiveGreen
import com.example.ui.theme.UhMuted
import com.example.ui.theme.UhStarGold

@Composable
fun LiveTvScreen(
    catalogState: UiState<PlaylistCatalog>,
    epgCache: Map<String, List<EpgProgram>>,
    showFavouritesFirst: Boolean,
    isFavouriteCheck: (String, MediaContentType, String) -> Boolean,
    onRequestEpg: (LiveChannel) -> Unit,
    onPlayChannel: (LiveChannel, List<LiveChannel>) -> Unit,
    onToggleFavourite: (LiveChannel) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    val catalog = (catalogState as? UiState.Success)?.data
    val categories: List<MediaCategory> = catalog?.liveCategories.orEmpty()
    val allChannels: List<LiveChannel> = catalog?.liveChannels.orEmpty()

    LaunchedEffect(categories) {
        if (selectedCategoryId != null && categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = null
        }
    }

    val filteredChannels = remember(allChannels, searchQuery, selectedCategoryId) {
        allChannels.filter { ch ->
            val matchesCat = selectedCategoryId == null || ch.categoryId == selectedCategoryId
            val matchesQuery = searchQuery.isBlank() ||
                ch.name.contains(searchQuery, ignoreCase = true) ||
                ch.categoryName.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    val displayGroups = ContentVisibilityFilter.partitionLiveChannelsForDisplay(
        channels = filteredChannels,
        showFavouritesFirst = showFavouritesFirst,
        isFavourite = { ch -> isFavouriteCheck(ch.id, MediaContentType.LIVE, ch.playlistId) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UhBlack)
    ) {
        Text(
            text = "Live TV",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search live channels...") },
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
                .testTag("live_search_input")
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

        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.LiveTv,
                        contentDescription = null,
                        tint = UhMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (allChannels.isEmpty()) {
                            "No visible Live TV channels in active playlist."
                        } else {
                            "No channels match your filter."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showFavouritesFirst && displayGroups.favouriteChannels.isNotEmpty()) {
                    item(key = "header_fav_channels") {
                        Text(
                            text = "⭐ MY FAVOURITE CHANNELS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = UhStarGold,
                            modifier = Modifier
                                .padding(top = 4.dp, bottom = 2.dp)
                                .testTag("live_favourites_section_header")
                        )
                    }
                    items(displayGroups.favouriteChannels, key = { "fav_${it.id}" }) { channel ->
                        LiveChannelListRow(
                            channel = channel,
                            epgPrograms = epgCache[channel.id].orEmpty(),
                            isFav = true,
                            onRequestEpg = onRequestEpg,
                            onPlay = { onPlayChannel(channel, displayGroups.orderedQueue) },
                            onToggleFavourite = { onToggleFavourite(channel) }
                        )
                    }
                    if (displayGroups.otherChannels.isNotEmpty()) {
                        item(key = "header_other_channels") {
                            Text(
                                text = "All Other Channels",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(top = 8.dp, bottom = 2.dp)
                                    .testTag("live_other_section_header")
                            )
                        }
                    }
                }

                items(
                    items = displayGroups.otherChannels,
                    key = { ch ->
                        if (showFavouritesFirst && displayGroups.favouriteChannels.isNotEmpty()) {
                            "other_${ch.id}"
                        } else {
                            ch.id
                        }
                    }
                ) { channel ->
                    val isFav = isFavouriteCheck(channel.id, MediaContentType.LIVE, channel.playlistId)
                    LiveChannelListRow(
                        channel = channel,
                        epgPrograms = epgCache[channel.id].orEmpty(),
                        isFav = isFav,
                        onRequestEpg = onRequestEpg,
                        onPlay = { onPlayChannel(channel, displayGroups.orderedQueue) },
                        onToggleFavourite = { onToggleFavourite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveChannelListRow(
    channel: LiveChannel,
    epgPrograms: List<EpgProgram>,
    isFav: Boolean,
    onRequestEpg: (LiveChannel) -> Unit,
    onPlay: () -> Unit,
    onToggleFavourite: () -> Unit
) {
    LaunchedEffect(channel.id) {
        onRequestEpg(channel)
    }
    val currentProgram = epgPrograms.firstOrNull { it.isLiveNow } ?: epgPrograms.firstOrNull()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isFav) UhStarGold.copy(alpha = 0.45f) else UhBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onPlay)
            .testTag("live_channel_row_${channel.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                PosterArtwork(
                    imageUrl = channel.logoUrl,
                    contentDescription = channel.name,
                    fallbackType = "LIVE",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "#${channel.number}",
                        style = MaterialTheme.typography.labelSmall,
                        color = UhLiveGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = currentProgram?.let {
                        "Now: ${it.title} (${it.startTimeLabel}–${it.endTimeLabel})"
                    } ?: channel.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onToggleFavourite,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("live_fav_star_${channel.id}")
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (isFav) "Remove channel from favourites" else "Add channel to favourites",
                    tint = if (isFav) UhStarGold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Watch ${channel.name}",
                tint = UhCrimson,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
