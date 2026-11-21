package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.GoogleAuthHelper
import com.example.data.firebase.UserSyncRepository
import com.example.data.local.PlaylistVaultRepository
import com.example.data.model.MediaContentType
import com.example.data.model.PlaybackRequest
import com.example.data.model.UiState
import com.example.data.model.UserPreferencesDoc
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveTvScreen
import com.example.ui.screens.MoviesScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.PlaylistLoginDialog
import com.example.ui.screens.SeriesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.UhBlack
import com.example.ui.theme.UhCrimson
import com.example.ui.theme.UhIptvTheme
import com.example.ui.theme.UhSurface
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_HOME = "home"
const val ROUTE_LIVE = "live"
const val ROUTE_MOVIES = "movies"
const val ROUTE_SERIES = "series"
const val ROUTE_SETTINGS = "settings"

private data class NavTabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navTabs = listOf(
    NavTabItem(ROUTE_HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavTabItem(ROUTE_LIVE, "Live TV", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
    NavTabItem(ROUTE_MOVIES, "Movies", Icons.Filled.Movie, Icons.Outlined.Movie),
    NavTabItem(ROUTE_SERIES, "Series", Icons.Filled.Tv, Icons.Outlined.Tv),
    NavTabItem(ROUTE_SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UhIptvTheme {
                UhIptvRootApp()
            }
        }
    }
}

@Composable
private fun UhIptvRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            GoogleAuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedMainShell(
                currentUserId = uid,
                userEmail = user.email.orEmpty(),
                userDisplayName = user.displayName.orEmpty(),
                onSignOut = {
                    GoogleAuthHelper.signOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null }
                    )
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedMainShell(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String,
    onSignOut: () -> Unit
) {
    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app: Application = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(
                    currentUserId = currentUserId,
                    userSyncRepository = UserSyncRepository(db),
                    playlistVaultRepository = PlaylistVaultRepository(app)
                )
            }
        }
    )

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()
    val rawCatalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val visibleCatalogState by viewModel.visibleCatalogState.collectAsStateWithLifecycle()
    val hiddenCategoriesByPlaylist by viewModel.hiddenCategoriesByPlaylist.collectAsStateWithLifecycle()
    val preferencesState by viewModel.preferencesState.collectAsStateWithLifecycle()
    val visibleFavouritesState by viewModel.visibleFavouritesState.collectAsStateWithLifecycle()
    val visibleContinueWatchingState by viewModel.visibleContinueWatchingState.collectAsStateWithLifecycle()
    val visibleWatchHistoryState by viewModel.visibleWatchHistoryState.collectAsStateWithLifecycle()
    val playlistAuthState by viewModel.playlistAuthState.collectAsStateWithLifecycle()
    val epgCache by viewModel.epgCache.collectAsStateWithLifecycle()
    val selectedMovieDetail by viewModel.selectedMovieDetail.collectAsStateWithLifecycle()
    val selectedSeriesDetail by viewModel.selectedSeriesDetail.collectAsStateWithLifecycle()
    val activePlayback by viewModel.activePlayback.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val playerLocalSettings by viewModel.playerLocalSettings.collectAsStateWithLifecycle()

    val preferences = (preferencesState as? UiState.Success)?.data
        ?: UserPreferencesDoc(userId = currentUserId)
    val continueWatchingList = (visibleContinueWatchingState as? UiState.Success)?.data.orEmpty()
    val rawCatalog = (rawCatalogState as? UiState.Success)?.data
    val catalog = (visibleCatalogState as? UiState.Success)?.data

    var showPlaylistDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UhBlack)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = UhBlack,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = UhSurface,
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            navTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = UhCrimson,
                                        selectedTextColor = Color.White,
                                        indicatorColor = UhCrimson.copy(alpha = 0.18f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(containerColor = UhSurface) {
                            navTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = UhCrimson,
                                        selectedTextColor = Color.White,
                                        indicatorColor = UhCrimson.copy(alpha = 0.18f)
                                    )
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                activePlaylist = activePlaylist,
                                catalogState = visibleCatalogState,
                                continueWatchingState = visibleContinueWatchingState,
                                favouritesState = visibleFavouritesState,
                                isFavouriteCheck = viewModel::isFavourite,
                                onAddPlaylistClick = {
                                    viewModel.resetPlaylistAuthState()
                                    showPlaylistDialog = true
                                },
                                onRefreshPlaylist = viewModel::refreshActivePlaylist,
                                onNavigateTab = navigateToTab,
                                onPlayLiveChannel = viewModel::startLiveChannelPlayback,
                                onOpenMovie = viewModel::openMovieDetail,
                                onOpenSeries = { series ->
                                    viewModel.openSeriesDetail(series)
                                    navigateToTab(ROUTE_SERIES)
                                },
                                onResumeContinueWatching = viewModel::resumeContinueWatching,
                                onPlayFavourite = { fav ->
                                    when (fav.contentType) {
                                        "LIVE" -> {
                                            val ch = catalog?.liveChannels?.firstOrNull { it.id == fav.itemId }
                                            if (ch != null) {
                                                viewModel.startLiveChannelPlayback(ch, catalog.liveChannels)
                                            } else if (fav.streamUrl.isNotBlank()) {
                                                val cands = com.example.data.network.StreamUrlResolver.deriveFallbackCandidates(
                                                    fav.streamUrl,
                                                    MediaContentType.LIVE
                                                )
                                                viewModel.updateActivePlaybackRequest(
                                                    PlaybackRequest(
                                                        itemId = fav.itemId,
                                                        contentType = MediaContentType.LIVE,
                                                        title = fav.title,
                                                        subtitle = fav.categoryName,
                                                        posterUrl = fav.posterUrl,
                                                        streamUrl = cands.firstOrNull() ?: fav.streamUrl,
                                                        playlistId = fav.playlistId,
                                                        candidateUrls = cands
                                                    )
                                                )
                                            }
                                        }
                                        "MOVIE" -> {
                                            val mov = catalog?.vodMovies?.firstOrNull { it.id == fav.itemId }
                                            if (mov != null) {
                                                viewModel.openMovieDetail(mov)
                                            } else if (fav.streamUrl.isNotBlank()) {
                                                val cands = com.example.data.network.StreamUrlResolver.deriveFallbackCandidates(
                                                    fav.streamUrl,
                                                    MediaContentType.MOVIE
                                                )
                                                viewModel.updateActivePlaybackRequest(
                                                    PlaybackRequest(
                                                        itemId = fav.itemId,
                                                        contentType = MediaContentType.MOVIE,
                                                        title = fav.title,
                                                        subtitle = fav.categoryName,
                                                        posterUrl = fav.posterUrl,
                                                        streamUrl = cands.firstOrNull() ?: fav.streamUrl,
                                                        playlistId = fav.playlistId,
                                                        candidateUrls = cands
                                                    )
                                                )
                                            }
                                        }
                                        else -> {
                                            val ser = catalog?.seriesList?.firstOrNull { it.id == fav.itemId }
                                            if (ser != null) {
                                                viewModel.openSeriesDetail(ser)
                                                navigateToTab(ROUTE_SERIES)
                                            }
                                        }
                                    }
                                },
                                onToggleFavourite = viewModel::toggleFavourite
                            )
                        }

                        composable(ROUTE_LIVE) {
                            LiveTvScreen(
                                catalogState = visibleCatalogState,
                                epgCache = epgCache,
                                showFavouritesFirst = playerLocalSettings.showFavouriteLiveFirst,
                                isFavouriteCheck = viewModel::isFavourite,
                                onRequestEpg = viewModel::loadEpgForChannel,
                                onPlayChannel = viewModel::startLiveChannelPlayback,
                                onToggleFavourite = { ch ->
                                    viewModel.toggleFavourite(
                                        itemId = ch.id,
                                        contentType = MediaContentType.LIVE,
                                        title = ch.name,
                                        posterUrl = ch.logoUrl,
                                        categoryName = ch.categoryName,
                                        streamUrl = ch.streamUrl,
                                        playlistId = ch.playlistId
                                    )
                                }
                            )
                        }

                        composable(ROUTE_MOVIES) {
                            MoviesScreen(
                                catalogState = visibleCatalogState,
                                continueWatchingList = continueWatchingList,
                                selectedMovieDetail = selectedMovieDetail,
                                isFavouriteCheck = viewModel::isFavourite,
                                onOpenMovieDetail = viewModel::openMovieDetail,
                                onCloseMovieDetail = viewModel::closeMovieDetail,
                                onPlayMovie = viewModel::startMoviePlayback,
                                onToggleFavourite = { movie ->
                                    viewModel.toggleFavourite(
                                        itemId = movie.id,
                                        contentType = MediaContentType.MOVIE,
                                        title = movie.title,
                                        posterUrl = movie.posterUrl,
                                        categoryName = movie.categoryName,
                                        streamUrl = movie.streamUrl,
                                        playlistId = movie.playlistId
                                    )
                                }
                            )
                        }

                        composable(ROUTE_SERIES) {
                            SeriesScreen(
                                catalogState = visibleCatalogState,
                                selectedSeriesDetail = selectedSeriesDetail,
                                continueWatchingList = continueWatchingList,
                                isFavouriteCheck = viewModel::isFavourite,
                                onOpenSeriesDetail = viewModel::openSeriesDetail,
                                onCloseSeriesDetail = viewModel::closeSeriesDetail,
                                onPlayEpisode = viewModel::startEpisodePlayback,
                                onToggleSeriesFavourite = { series ->
                                    viewModel.toggleFavourite(
                                        itemId = series.id,
                                        contentType = MediaContentType.SERIES,
                                        title = series.title,
                                        posterUrl = series.posterUrl,
                                        categoryName = series.categoryName,
                                        streamUrl = "",
                                        playlistId = series.playlistId
                                    )
                                }
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                userEmail = userEmail,
                                userDisplayName = userDisplayName,
                                playlists = playlists,
                                activePlaylistId = activePlaylist?.id,
                                rawCatalog = rawCatalog,
                                hiddenCategoriesByPlaylist = hiddenCategoriesByPlaylist,
                                preferences = preferences,
                                playerLocalSettings = playerLocalSettings,
                                watchHistoryState = visibleWatchHistoryState,
                                onAddPlaylistClick = {
                                    viewModel.resetPlaylistAuthState()
                                    showPlaylistDialog = true
                                },
                                onSelectPlaylist = viewModel::switchActivePlaylist,
                                onRefreshPlaylist = viewModel::refreshActivePlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onSavePreferences = viewModel::updatePreferences,
                                onUpdatePlayerLocalSettings = viewModel::updatePlayerLocalSettings,
                                onSetWatchHistoryEnabled = viewModel::setWatchHistoryEnabled,
                                onSetShowFavouriteLiveFirst = viewModel::setShowFavouriteLiveFirst,
                                onToggleCategoryVisibility = viewModel::setCategoryVisibility,
                                onShowAllCategories = viewModel::showAllCategories,
                                onHideAllCategories = viewModel::hideAllCategories,
                                onResetCategoriesToDefault = viewModel::resetCategoriesToDefault,
                                onPlaySampleStream = viewModel::startPublicSamplePlayback,
                                onClearWatchHistory = viewModel::clearWatchHistory,
                                onSignOutClick = onSignOut
                            )
                        }
                    }
                }
            }
        }

        if (showPlaylistDialog) {
            PlaylistLoginDialog(
                authState = playlistAuthState,
                onConnectXtream = viewModel::connectXtreamPlaylist,
                onConnectM3u = viewModel::connectM3uPlaylist,
                onDismiss = {
                    showPlaylistDialog = false
                    viewModel.resetPlaylistAuthState()
                }
            )
        }

        // Fullscreen Media3 ExoPlayer overlay
        val playback = activePlayback
        if (playback != null) {
            val isFav = viewModel.isFavourite(playback.itemId, playback.contentType, playback.playlistId)
            LaunchedEffect(playback.itemId) {
                if (playback.contentType == MediaContentType.LIVE) {
                    catalog?.liveChannels?.firstOrNull { it.id == playback.itemId }?.let {
                        viewModel.loadEpgForChannel(it)
                    }
                }
            }
            PlayerScreen(
                request = playback,
                preferences = preferences,
                playerLocalSettings = playerLocalSettings,
                epgPrograms = epgCache[playback.itemId].orEmpty(),
                isFavourite = isFav,
                onSwitchRequest = viewModel::updateActivePlaybackRequest,
                onUpdatePlayerSettings = viewModel::updatePlayerLocalSettings,
                onSaveProgress = viewModel::savePlaybackProgress,
                onToggleFavourite = { req ->
                    viewModel.toggleFavourite(
                        itemId = req.itemId,
                        contentType = req.contentType,
                        title = req.title,
                        posterUrl = req.posterUrl,
                        categoryName = req.subtitle,
                        streamUrl = req.streamUrl,
                        playlistId = req.playlistId
                    )
                },
                onBack = viewModel::closePlayback
            )
        }
    }
}
