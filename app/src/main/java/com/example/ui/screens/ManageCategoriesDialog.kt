package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MediaCategory
import com.example.data.model.MediaContentType
import com.example.data.model.PlaylistHiddenCategories
import com.example.data.network.PlaylistCatalog
import com.example.ui.theme.UhBlack
import com.example.ui.theme.UhBorder
import com.example.ui.theme.UhCrimson

enum class CategoryManageTab(val label: String) {
    LIVE("📺 Live TV"),
    MOVIE("🎬 Movies"),
    SERIES("📺 TV Series")
}

@Composable
fun ManageCategoriesDialog(
    playlistName: String,
    playlistId: String,
    rawCatalog: PlaylistCatalog?,
    hiddenCategories: PlaylistHiddenCategories,
    initialTab: CategoryManageTab = CategoryManageTab.LIVE,
    onToggleCategoryVisibility: (MediaContentType, String, Boolean) -> Unit,
    onShowAllCategories: () -> Unit,
    onHideAllCategories: () -> Unit,
    onResetToDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var showHideAllConfirmation by remember { mutableStateOf(false) }

    val liveCategories = rawCatalog?.liveCategories.orEmpty()
    val movieCategories = rawCatalog?.vodCategories.orEmpty()
    val seriesCategories = rawCatalog?.seriesCategories.orEmpty()

    val itemCounts: Map<Pair<MediaContentType, String>, Int> = remember(rawCatalog) {
        if (rawCatalog == null) {
            emptyMap()
        } else {
            buildMap {
                rawCatalog.liveChannels.groupingBy { it.categoryId }.eachCount().forEach { (id, count) ->
                    put(MediaContentType.LIVE to id, count)
                }
                rawCatalog.vodMovies.groupingBy { it.categoryId }.eachCount().forEach { (id, count) ->
                    put(MediaContentType.MOVIE to id, count)
                }
                rawCatalog.seriesList.groupingBy { it.categoryId }.eachCount().forEach { (id, count) ->
                    put(MediaContentType.SERIES to id, count)
                }
            }
        }
    }

    val (currentList, currentType) = when (activeTab) {
        CategoryManageTab.LIVE -> liveCategories to MediaContentType.LIVE
        CategoryManageTab.MOVIE -> movieCategories to MediaContentType.MOVIE
        CategoryManageTab.SERIES -> seriesCategories to MediaContentType.SERIES
    }

    val filteredList = remember(currentList, searchQuery) {
        if (searchQuery.isBlank()) {
            currentList
        } else {
            currentList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = UhBlack,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manage Categories",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$playlistName • ${hiddenCategories.hiddenCount()} hidden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("manage_categories_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close category manager",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CategoryManageTab.entries.forEach { tab ->
                        val count = when (tab) {
                            CategoryManageTab.LIVE -> liveCategories.size
                            CategoryManageTab.MOVIE -> movieCategories.size
                            CategoryManageTab.SERIES -> seriesCategories.size
                        }
                        val selected = activeTab == tab
                        FilterChip(
                            selected = selected,
                            onClick = { activeTab = tab },
                            label = { Text("${tab.label} ($count)", maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = UhCrimson,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("manage_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter categories...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear filter")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manage_categories_search")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onShowAllCategories,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_show_all_categories")
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Show All", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showHideAllConfirmation = true },
                        enabled = rawCatalog != null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_hide_all_categories")
                    ) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hide All", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onResetToDefault,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_reset_categories")
                    ) {
                        Text("Reset", maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (rawCatalog == null || filteredList.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (rawCatalog == null) {
                                "Sync a playlist first to manage its provider categories."
                            } else {
                                "No categories match \"$searchQuery\" in ${activeTab.label}."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { "${playlistId}_${currentType.name}_${it.id}" }) { cat: MediaCategory ->
                            val isVisible = hiddenCategories.isCategoryVisible(currentType, cat.id)
                            val count = itemCounts[currentType to cat.id] ?: 0
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, UhBorder, RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = when (currentType) {
                                                MediaContentType.LIVE -> Icons.Filled.LiveTv
                                                MediaContentType.MOVIE -> Icons.Filled.Movie
                                                else -> Icons.Filled.Tv
                                            },
                                            contentDescription = null,
                                            tint = if (isVisible) UhCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cat.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isVisible) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (isVisible) "$count items • Visible" else "$count items • Hidden",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = isVisible,
                                        onCheckedChange = { checked ->
                                            onToggleCategoryVisibility(currentType, cat.id, checked)
                                        },
                                        colors = SwitchDefaults.colors(checkedTrackColor = UhCrimson),
                                        modifier = Modifier.testTag("category_toggle_${currentType.name.lowercase()}_${cat.id}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHideAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showHideAllConfirmation = false },
            title = { Text("Hide All Categories?") },
            text = {
                Text(
                    "All Live TV, Movie, and TV Series categories for \"$playlistName\" will be hidden across Home, browsing, Favourites display, Continue Watching, and search until turned back ON."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHideAllConfirmation = false
                        onHideAllCategories()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UhCrimson),
                    modifier = Modifier.testTag("confirm_hide_all_categories_button")
                ) {
                    Text("Hide All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideAllConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
