package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.MediaContentType
import com.example.data.model.PlaylistHiddenCategories
import com.example.data.model.SavedPlaylist
import com.example.data.model.UiState
import com.example.data.model.UserPreferencesDoc
import com.example.data.model.WatchHistoryDoc
import com.example.data.network.PlaylistCatalog
import com.example.player.PlayerLocalSettings
import com.example.player.PublicSampleStream
import com.example.ui.theme.UhBlack
import com.example.ui.theme.UhBorder
import com.example.ui.theme.UhCrimson
import com.example.ui.theme.UhLiveGreen
import com.example.ui.theme.UhStarGold

@Composable
fun SettingsScreen(
    userEmail: String,
    userDisplayName: String,
    playlists: List<SavedPlaylist>,
    activePlaylistId: String?,
    rawCatalog: PlaylistCatalog?,
    hiddenCategoriesByPlaylist: Map<String, PlaylistHiddenCategories>,
    preferences: UserPreferencesDoc,
    playerLocalSettings: PlayerLocalSettings,
    watchHistoryState: UiState<List<WatchHistoryDoc>>,
    onAddPlaylistClick: () -> Unit,
    onSelectPlaylist: (SavedPlaylist) -> Unit,
    onRefreshPlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onSavePreferences: (List<String>, Boolean, Boolean, String) -> Unit,
    onUpdatePlayerLocalSettings: (PlayerLocalSettings) -> Unit,
    onSetWatchHistoryEnabled: (Boolean) -> Unit,
    onSetShowFavouriteLiveFirst: (Boolean) -> Unit,
    onToggleCategoryVisibility: (String, MediaContentType, String, Boolean) -> Unit,
    onShowAllCategories: (String) -> Unit,
    onHideAllCategories: (String, PlaylistCatalog?) -> Unit,
    onResetCategoriesToDefault: (String) -> Unit,
    onPlaySampleStream: (PublicSampleStream) -> Unit,
    onClearWatchHistory: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val historyItems = (watchHistoryState as? UiState.Success)?.data.orEmpty()
    val effectivePlaylist = playlists.firstOrNull { it.id == activePlaylistId } ?: playlists.firstOrNull()
    val effectivePlaylistId = effectivePlaylist?.id.orEmpty()
    val effectiveHidden = hiddenCategoriesByPlaylist[effectivePlaylistId] ?: PlaylistHiddenCategories()

    var manageCategoriesTab by remember { mutableStateOf<CategoryManageTab?>(null) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showHideAllConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UhBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "settings_title") {
            Text(
                text = "Settings & Playlists",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Google Account Card
        item(key = "google_account") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UhBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userDisplayName.ifBlank { "Google Account" },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (userEmail.isNotBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Synced with Firebase Auth & Firestore",
                            style = MaterialTheme.typography.labelSmall,
                            color = UhLiveGreen
                        )
                    }
                    OutlinedButton(
                        onClick = onSignOutClick,
                        modifier = Modifier.testTag("settings_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }
        }

        // Connected Playlists Section
        item(key = "playlists_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IPTV Playlists (${playlists.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Button(
                    onClick = onAddPlaylistClick,
                    colors = ButtonDefaults.buttonColors(containerColor = UhCrimson),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("settings_add_playlist_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (playlists.isEmpty()) {
            item(key = "no_playlists_notice") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No saved playlists. Tap Add to connect an Xtream Codes account or M3U URL.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                val isActive = playlist.id == effectivePlaylistId
                Surface(
                    color = if (isActive) UhCrimson.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isActive) UhCrimson else UhBorder, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectPlaylist(playlist) }
                        .testTag("playlist_card_${playlist.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Filled.CheckCircle else Icons.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = if (isActive) UhCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(playlist.type.name)
                                    append(" • ")
                                    append(playlist.accountStatus)
                                    if (playlist.expirationDate.isNotBlank()) {
                                        append(" • Exp: ${playlist.expirationDate}")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isActive) {
                            IconButton(onClick = onRefreshPlaylist) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh playlist")
                            }
                        }
                        IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete playlist",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // PLAYBACK Section
        item(key = "section_playback") {
            SettingsCategoryHeading("PLAYBACK")
        }

        item(key = "player_prefs_card") {
            SettingsPlayerPreferencesCard(
                preferences = preferences,
                playerLocalSettings = playerLocalSettings,
                onSavePreferences = onSavePreferences,
                onUpdatePlayerLocalSettings = onUpdatePlayerLocalSettings,
                onPlaySampleStream = onPlaySampleStream
            )
        }

        item(key = "audio_priority_card") {
            SettingsAudioPriorityCard(
                preferences = preferences,
                onSavePreferences = onSavePreferences
            )
        }

        // LIVE TV Section
        item(key = "section_live_tv") {
            SettingsCategoryHeading("LIVE TV")
        }

        item(key = "live_tv_settings_card") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UhBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = UhStarGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Favourites First",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (playerLocalSettings.showFavouriteLiveFirst) {
                                    "ON — Favourite channels appear at the top of Live TV"
                                } else {
                                    "OFF — Live TV displays channels in normal category order"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = playerLocalSettings.showFavouriteLiveFirst,
                        onCheckedChange = onSetShowFavouriteLiveFirst,
                        colors = SwitchDefaults.colors(checkedTrackColor = UhCrimson),
                        modifier = Modifier.testTag("settings_show_fav_live_first_switch")
                    )
                }
            }
        }

        // PRIVACY Section
        item(key = "section_privacy") {
            SettingsCategoryHeading("PRIVACY")
        }

        item(key = "privacy_settings_card") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UhBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Watch History",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (playerLocalSettings.watchHistoryEnabled) {
                                    "ON — Recording recently watched items & Continue Watching progress"
                                } else {
                                    "OFF — New watch history & progress paused (existing history kept)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playerLocalSettings.watchHistoryEnabled,
                            onCheckedChange = onSetWatchHistoryEnabled,
                            colors = SwitchDefaults.colors(checkedTrackColor = UhCrimson),
                            modifier = Modifier.testTag("settings_watch_history_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = UhBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showClearHistoryConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_clear_watch_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clear Watch History",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // CONTENT MANAGEMENT Section
        item(key = "section_content_management") {
            SettingsCategoryHeading("CONTENT MANAGEMENT")
        }

        item(key = "content_management_card") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UhBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (effectivePlaylist != null) {
                            "Playlist: ${effectivePlaylist.name}"
                        } else {
                            "No Active Playlist Connected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (effectiveHidden.hiddenCount() == 0) {
                            "All provider categories are visible."
                        } else {
                            "${effectiveHidden.hiddenCount()} categories hidden for this playlist"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (effectiveHidden.hiddenCount() > 0) UhCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ManageCategoryRow(
                        icon = Icons.Filled.LiveTv,
                        title = "Manage Live TV Categories",
                        subtitle = "${rawCatalog?.liveCategories?.size ?: 0} categories • ${effectiveHidden.liveCategoryIds.size} hidden",
                        enabled = effectivePlaylist != null,
                        onClick = { manageCategoriesTab = CategoryManageTab.LIVE },
                        testTag = "manage_live_categories_row"
                    )
                    HorizontalDivider(color = UhBorder)
                    ManageCategoryRow(
                        icon = Icons.Filled.Movie,
                        title = "Manage Movie Categories",
                        subtitle = "${rawCatalog?.vodCategories?.size ?: 0} categories • ${effectiveHidden.movieCategoryIds.size} hidden",
                        enabled = effectivePlaylist != null,
                        onClick = { manageCategoriesTab = CategoryManageTab.MOVIE },
                        testTag = "manage_movie_categories_row"
                    )
                    HorizontalDivider(color = UhBorder)
                    ManageCategoryRow(
                        icon = Icons.Filled.Tv,
                        title = "Manage Series Categories",
                        subtitle = "${rawCatalog?.seriesCategories?.size ?: 0} categories • ${effectiveHidden.seriesCategoryIds.size} hidden",
                        enabled = effectivePlaylist != null,
                        onClick = { manageCategoriesTab = CategoryManageTab.SERIES },
                        testTag = "manage_series_categories_row"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onShowAllCategories(effectivePlaylistId) },
                            enabled = effectivePlaylist != null,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_show_all_categories")
                        ) {
                            Text("Show All", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showHideAllConfirm = true },
                            enabled = effectivePlaylist != null && rawCatalog != null,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_hide_all_categories")
                        ) {
                            Text("Hide All", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onResetCategoriesToDefault(effectivePlaylistId) },
                            enabled = effectivePlaylist != null,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_reset_categories")
                        ) {
                            Text("Reset", maxLines = 1)
                        }
                    }
                }
            }
        }

        // Recently Watched Section
        item(key = "history_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.History, contentDescription = null, tint = UhCrimson)
                Text(
                    text = "Recently Watched",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (historyItems.isEmpty()) {
            item(key = "history_empty") {
                Text(
                    text = if (playerLocalSettings.watchHistoryEnabled) {
                        "Channels, movies, and episodes you watch appear here."
                    } else {
                        "No saved watch history. Recording is currently OFF."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(historyItems.take(15), key = { it.id }) { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.contentType} • ${item.subtitle}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item(key = "free_notice") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UhBorder, RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = stringResource(R.string.free_app_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }

    val openTab = manageCategoriesTab
    if (openTab != null && effectivePlaylist != null) {
        ManageCategoriesDialog(
            playlistName = effectivePlaylist.name,
            playlistId = effectivePlaylist.id,
            rawCatalog = rawCatalog,
            hiddenCategories = effectiveHidden,
            initialTab = openTab,
            onToggleCategoryVisibility = { type, categoryId, visible ->
                onToggleCategoryVisibility(effectivePlaylist.id, type, categoryId, visible)
            },
            onShowAllCategories = { onShowAllCategories(effectivePlaylist.id) },
            onHideAllCategories = { onHideAllCategories(effectivePlaylist.id, rawCatalog) },
            onResetToDefault = { onResetCategoriesToDefault(effectivePlaylist.id) },
            onDismiss = { manageCategoriesTab = null }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear Watch History?") },
            text = {
                Text(
                    "This will permanently delete your saved viewing history and Continue Watching playback progress from this device and your synced Google account."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryConfirm = false
                        onClearWatchHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UhCrimson),
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text("Clear History")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHideAllConfirm && effectivePlaylist != null) {
        AlertDialog(
            onDismissRequest = { showHideAllConfirm = false },
            title = { Text("Hide All Categories?") },
            text = {
                Text(
                    "All Live TV, Movie, and TV Series categories for \"${effectivePlaylist.name}\" will be hidden until you turn them back ON."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHideAllConfirm = false
                        onHideAllCategories(effectivePlaylist.id, rawCatalog)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UhCrimson),
                    modifier = Modifier.testTag("confirm_settings_hide_all_button")
                ) {
                    Text("Hide All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsCategoryHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = UhCrimson,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ManageCategoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) UhCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
