package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.UserSyncGateway
import com.example.data.firebase.UserSyncRepository
import com.example.data.local.PlaylistVaultRepository
import com.example.data.model.ContentVisibilityFilter
import com.example.data.model.ContinueWatchingDoc
import com.example.data.model.EpgProgram
import com.example.data.model.FavouriteDoc
import com.example.data.model.LiveChannel
import com.example.data.model.MediaContentType
import com.example.data.model.PlaybackRequest
import com.example.data.model.PlaylistHiddenCategories
import com.example.data.model.PlaylistType
import com.example.data.model.SavedPlaylist
import com.example.data.model.SeriesDetails
import com.example.data.model.SeriesEpisode
import com.example.data.model.TvSeries
import com.example.data.model.UiState
import com.example.data.model.UserPreferencesDoc
import com.example.data.model.VodMovie
import com.example.data.model.WatchHistoryDoc
import com.example.data.network.M3uRepository
import com.example.data.network.PlaylistCatalog
import com.example.data.network.XtreamClient
import com.example.data.network.XtreamValidationResult
import com.example.player.PlayerLocalSettings
import com.example.player.VideoDecoderMode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlaylistAuthUiState {
    data object Idle : PlaylistAuthUiState
    data object Connecting : PlaylistAuthUiState
    data class Error(val message: String) : PlaylistAuthUiState
    data class Connected(val message: String) : PlaylistAuthUiState
}

class MainViewModel(
    private val currentUserId: String,
    private val userSyncRepository: UserSyncGateway,
    private val playlistVaultRepository: PlaylistVaultRepository,
    private val xtreamClient: XtreamClient = XtreamClient(),
    private val m3uRepository: M3uRepository = M3uRepository()
) : ViewModel() {

    val playlists: StateFlow<List<SavedPlaylist>> = playlistVaultRepository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val activePlaylistId: StateFlow<String?> = playlistVaultRepository.activePlaylistIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activePlaylist: StateFlow<SavedPlaylist?> = combine(playlists, activePlaylistId) { list, activeId ->
        list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val playerLocalSettings: StateFlow<PlayerLocalSettings> = playlistVaultRepository.playerLocalSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayerLocalSettings())

    val preferencesState: StateFlow<UiState<UserPreferencesDoc>> =
        userSyncRepository.observePreferences(currentUserId)
            .map<UserPreferencesDoc, UiState<UserPreferencesDoc>> { UiState.Success(it) }
            .catch { error -> handleStreamError(error, "preferences") { emit(UiState.Error(it)) } }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                UiState.Success(UserPreferencesDoc(userId = currentUserId))
            )

    private val cloudCategoryVisibilityState: StateFlow<Map<String, PlaylistHiddenCategories>> =
        userSyncRepository.observeCategoryVisibility(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing categoryVisibility", firestoreError ?: error)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    val hiddenCategoriesByPlaylist: StateFlow<Map<String, PlaylistHiddenCategories>> = combine(
        playlistVaultRepository.hiddenCategoriesFlow,
        cloudCategoryVisibilityState
    ) { localMap, cloudMap ->
        if (cloudMap.isEmpty()) {
            localMap
        } else {
            val merged = LinkedHashMap<String, PlaylistHiddenCategories>(cloudMap.size + localMap.size)
            cloudMap.forEach { (playlistId, hidden) ->
                if (!localMap.containsKey(playlistId) && !hidden.isEmpty()) {
                    merged[playlistId] = hidden
                }
            }
            merged.putAll(localMap)
            merged
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    val favouritesState: StateFlow<UiState<List<FavouriteDoc>>> =
        userSyncRepository.observeFavourites(currentUserId)
            .map<List<FavouriteDoc>, UiState<List<FavouriteDoc>>> { UiState.Success(it) }
            .catch { error -> handleStreamError(error, "favourites") { emit(UiState.Error(it)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val continueWatchingState: StateFlow<UiState<List<ContinueWatchingDoc>>> =
        userSyncRepository.observeContinueWatching(currentUserId)
            .map<List<ContinueWatchingDoc>, UiState<List<ContinueWatchingDoc>>> { UiState.Success(it) }
            .catch { error -> handleStreamError(error, "continueWatching") { emit(UiState.Error(it)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val watchHistoryState: StateFlow<UiState<List<WatchHistoryDoc>>> =
        userSyncRepository.observeWatchHistory(currentUserId)
            .map<List<WatchHistoryDoc>, UiState<List<WatchHistoryDoc>>> { UiState.Success(it) }
            .catch { error -> handleStreamError(error, "watchHistory") { emit(UiState.Error(it)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val _catalogState = MutableStateFlow<UiState<PlaylistCatalog>>(
        UiState.Success(
            PlaylistCatalog(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        )
    )
    val catalogState: StateFlow<UiState<PlaylistCatalog>> = _catalogState.asStateFlow()

    val visibleCatalogState: StateFlow<UiState<PlaylistCatalog>> = combine(
        catalogState,
        activePlaylist,
        hiddenCategoriesByPlaylist
    ) { rawState, playlist, hiddenMap ->
        when (rawState) {
            is UiState.Success -> {
                val hidden = playlist?.id?.let { hiddenMap[it] } ?: PlaylistHiddenCategories()
                UiState.Success(ContentVisibilityFilter.filterCatalog(rawState.data, hidden))
            }
            else -> rawState
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        UiState.Success(
            PlaylistCatalog(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        )
    )

    val visibleFavouritesState: StateFlow<UiState<List<FavouriteDoc>>> = combine(
        favouritesState,
        catalogState,
        activePlaylist,
        hiddenCategoriesByPlaylist
    ) { favState, rawCatalogState, playlist, hiddenMap ->
        when (favState) {
            is UiState.Success -> {
                val rawCatalog = (rawCatalogState as? UiState.Success)?.data
                UiState.Success(
                    ContentVisibilityFilter.filterVisibleFavourites(
                        favourites = favState.data,
                        rawCatalog = rawCatalog,
                        activePlaylistId = playlist?.id,
                        hiddenByPlaylist = hiddenMap
                    )
                )
            }
            else -> favState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val visibleContinueWatchingState: StateFlow<UiState<List<ContinueWatchingDoc>>> = combine(
        continueWatchingState,
        catalogState,
        activePlaylist,
        hiddenCategoriesByPlaylist
    ) { cwState, rawCatalogState, playlist, hiddenMap ->
        when (cwState) {
            is UiState.Success -> {
                val rawCatalog = (rawCatalogState as? UiState.Success)?.data
                UiState.Success(
                    ContentVisibilityFilter.filterVisibleContinueWatching(
                        items = cwState.data,
                        rawCatalog = rawCatalog,
                        activePlaylistId = playlist?.id,
                        hiddenByPlaylist = hiddenMap
                    )
                )
            }
            else -> cwState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val visibleWatchHistoryState: StateFlow<UiState<List<WatchHistoryDoc>>> = combine(
        watchHistoryState,
        catalogState,
        activePlaylist,
        hiddenCategoriesByPlaylist
    ) { histState, rawCatalogState, playlist, hiddenMap ->
        when (histState) {
            is UiState.Success -> {
                val rawCatalog = (rawCatalogState as? UiState.Success)?.data
                UiState.Success(
                    ContentVisibilityFilter.filterVisibleWatchHistory(
                        items = histState.data,
                        rawCatalog = rawCatalog,
                        activePlaylistId = playlist?.id,
                        hiddenByPlaylist = hiddenMap
                    )
                )
            }
            else -> histState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val _playlistAuthState = MutableStateFlow<PlaylistAuthUiState>(PlaylistAuthUiState.Idle)
    val playlistAuthState: StateFlow<PlaylistAuthUiState> = _playlistAuthState.asStateFlow()

    private val _epgCache = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val epgCache: StateFlow<Map<String, List<EpgProgram>>> = _epgCache.asStateFlow()

    private val _selectedMovieDetail = MutableStateFlow<VodMovie?>(null)
    val selectedMovieDetail: StateFlow<VodMovie?> = _selectedMovieDetail.asStateFlow()

    private val _selectedSeriesDetail = MutableStateFlow<UiState<SeriesDetails>?>(null)
    val selectedSeriesDetail: StateFlow<UiState<SeriesDetails>?> = _selectedSeriesDetail.asStateFlow()

    private val _activePlayback = MutableStateFlow<PlaybackRequest?>(null)
    val activePlayback: StateFlow<PlaybackRequest?> = _activePlayback.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var lastLoadedPlaylistId: String? = null
    private var hasHydratedRemotePrefs = false
    private var localPrivacyOrLiveEditedInSession = false

    init {
        viewModelScope.launch {
            activePlaylist.collect { playlist ->
                if (playlist != null && playlist.id != lastLoadedPlaylistId) {
                    syncPlaylistCatalog(playlist)
                } else if (playlist == null) {
                    lastLoadedPlaylistId = null
                    _catalogState.value = UiState.Success(
                        PlaylistCatalog(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
                    )
                }
            }
        }
        viewModelScope.launch {
            cloudCategoryVisibilityState.collect { cloudMap ->
                if (cloudMap.isNotEmpty()) {
                    playlistVaultRepository.mergeHiddenCategoriesFromCloud(cloudMap)
                }
            }
        }
        viewModelScope.launch {
            preferencesState.collect { state ->
                val remote = (state as? UiState.Success)?.data ?: return@collect
                if (!hasHydratedRemotePrefs && !localPrivacyOrLiveEditedInSession && remote.createdAt != null) {
                    hasHydratedRemotePrefs = true
                    playlistVaultRepository.setWatchHistoryEnabled(remote.watchHistoryEnabled)
                    playlistVaultRepository.setShowFavouriteLiveFirst(remote.showFavouriteLiveChannelsFirst)
                }
            }
        }
    }

    fun connectXtreamPlaylist(
        playlistName: String,
        serverUrl: String,
        username: String,
        password: String
    ) {
        viewModelScope.launch {
            _playlistAuthState.value = PlaylistAuthUiState.Connecting
            val normalizedBase = xtreamClient.normalizeServerBaseUrl(serverUrl)
            val cleanUser = username.trim()
            val stableId = if (normalizedBase != null && cleanUser.isNotBlank()) {
                playlists.value.firstOrNull {
                    it.type == PlaylistType.XTREAM &&
                        it.serverUrl.equals(normalizedBase, ignoreCase = true) &&
                        it.username == cleanUser
                }?.id ?: "xt_${UserSyncRepository.safeDocId("${normalizedBase.lowercase()}|$cleanUser").take(24)}"
            } else {
                null
            }
            when (
                val result = xtreamClient.authenticateAndValidate(
                    playlistName = playlistName,
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    existingPlaylistId = stableId
                )
            ) {
                is XtreamValidationResult.Failure -> {
                    _playlistAuthState.value = PlaylistAuthUiState.Error(result.errorMessage)
                }
                is XtreamValidationResult.Authenticated -> {
                    playlistVaultRepository.upsertPlaylist(result.playlist, makeActive = true)
                    lastLoadedPlaylistId = null
                    _playlistAuthState.value = PlaylistAuthUiState.Connected(
                        "Connected to ${result.playlist.name} (${result.playlist.accountStatus})"
                    )
                    syncPlaylistCatalog(result.playlist)
                }
            }
        }
    }

    fun connectM3uPlaylist(
        playlistName: String,
        m3uUrl: String
    ) {
        viewModelScope.launch {
            _playlistAuthState.value = PlaylistAuthUiState.Connecting
            val cleanUrl = m3uUrl.trim()
            val stableId = playlists.value.firstOrNull {
                it.type == PlaylistType.M3U && it.m3uUrl == cleanUrl
            }?.id ?: "m3u_${UserSyncRepository.safeDocId(cleanUrl).take(24)}"
            val result = m3uRepository.fetchAndValidatePlaylist(
                playlistName = playlistName,
                m3uUrl = cleanUrl,
                existingPlaylistId = stableId
            )
            result.fold(
                onSuccess = { (saved, catalog) ->
                    playlistVaultRepository.upsertPlaylist(saved, makeActive = true)
                    lastLoadedPlaylistId = saved.id
                    _catalogState.value = UiState.Success(catalog)
                    _playlistAuthState.value = PlaylistAuthUiState.Connected(
                        "Loaded ${catalog.liveChannels.size} channels, ${catalog.vodMovies.size} movies, ${catalog.seriesList.size} series"
                    )
                },
                onFailure = { error ->
                    _playlistAuthState.value = PlaylistAuthUiState.Error(
                        error.localizedMessage ?: "Failed to load M3U playlist"
                    )
                }
            )
        }
    }

    fun resetPlaylistAuthState() {
        _playlistAuthState.value = PlaylistAuthUiState.Idle
    }

    fun switchActivePlaylist(playlist: SavedPlaylist) {
        viewModelScope.launch {
            playlistVaultRepository.setActivePlaylist(playlist.id)
            syncPlaylistCatalog(playlist)
        }
    }

    fun refreshActivePlaylist() {
        val current = activePlaylist.value ?: return
        viewModelScope.launch {
            syncPlaylistCatalog(current)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistVaultRepository.deletePlaylist(playlistId)
            if (lastLoadedPlaylistId == playlistId) {
                lastLoadedPlaylistId = null
            }
        }
    }

    private suspend fun syncPlaylistCatalog(playlist: SavedPlaylist) {
        _catalogState.value = UiState.Loading
        lastLoadedPlaylistId = playlist.id
        when (playlist.type) {
            PlaylistType.XTREAM -> {
                val res = xtreamClient.fetchFullCatalog(playlist)
                res.fold(
                    onSuccess = { catalog ->
                        _catalogState.value = UiState.Success(catalog)
                        playlistVaultRepository.upsertPlaylist(
                            playlist.copy(lastSyncedAtMs = System.currentTimeMillis()),
                            makeActive = false
                        )
                    },
                    onFailure = { error ->
                        _catalogState.value = UiState.Error(
                            error.localizedMessage ?: "Failed to load Xtream catalog"
                        )
                    }
                )
            }
            PlaylistType.M3U -> {
                val res = m3uRepository.fetchAndValidatePlaylist(
                    playlistName = playlist.name,
                    m3uUrl = playlist.m3uUrl,
                    existingPlaylistId = playlist.id
                )
                res.fold(
                    onSuccess = { (updated, catalog) ->
                        _catalogState.value = UiState.Success(catalog)
                        playlistVaultRepository.upsertPlaylist(updated, makeActive = false)
                    },
                    onFailure = { error ->
                        _catalogState.value = UiState.Error(
                            error.localizedMessage ?: "Failed to refresh M3U playlist"
                        )
                    }
                )
            }
        }
    }

    fun loadEpgForChannel(channel: LiveChannel) {
        val playlist = playlists.value.firstOrNull { it.id == channel.playlistId } ?: activePlaylist.value ?: return
        if (playlist.type != PlaylistType.XTREAM) return
        if (_epgCache.value.containsKey(channel.id)) return
        viewModelScope.launch {
            val programs = xtreamClient.fetchShortEpg(playlist, channel.id)
            _epgCache.value = _epgCache.value + (channel.id to programs)
        }
    }

    fun openMovieDetail(movie: VodMovie) {
        _selectedMovieDetail.value = movie
        val playlist = playlists.value.firstOrNull { it.id == movie.playlistId } ?: activePlaylist.value ?: return
        viewModelScope.launch {
            val enriched = xtreamClient.fetchVodMovieDetails(playlist, movie)
            if (_selectedMovieDetail.value?.id == movie.id) {
                _selectedMovieDetail.value = enriched
            }
        }
    }

    fun closeMovieDetail() {
        _selectedMovieDetail.value = null
    }

    fun openSeriesDetail(series: TvSeries) {
        val playlist = playlists.value.firstOrNull { it.id == series.playlistId } ?: activePlaylist.value
        val currentCatalog = (_catalogState.value as? UiState.Success)?.data
        val m3uDetails = currentCatalog?.m3uSeriesDetails?.get(series.id)
        if (m3uDetails != null) {
            _selectedSeriesDetail.value = UiState.Success(m3uDetails)
            return
        }
        if (playlist == null || playlist.type != PlaylistType.XTREAM) {
            _selectedSeriesDetail.value = UiState.Error("Series details unavailable for this playlist.")
            return
        }
        _selectedSeriesDetail.value = UiState.Loading
        viewModelScope.launch {
            val result = xtreamClient.fetchSeriesDetails(playlist, series)
            result.fold(
                onSuccess = { details -> _selectedSeriesDetail.value = UiState.Success(details) },
                onFailure = { err ->
                    _selectedSeriesDetail.value = UiState.Error(
                        err.localizedMessage ?: "Failed to load seasons and episodes"
                    )
                }
            )
        }
    }

    fun closeSeriesDetail() {
        _selectedSeriesDetail.value = null
    }

    fun startLiveChannelPlayback(channel: LiveChannel, queue: List<LiveChannel>) {
        loadEpgForChannel(channel)
        val candidates = com.example.data.network.StreamUrlResolver.deriveFallbackCandidates(
            primaryUrl = channel.streamUrl,
            contentType = MediaContentType.LIVE,
            explicitCandidates = channel.candidateUrls
        )
        val request = PlaybackRequest(
            itemId = channel.id,
            contentType = MediaContentType.LIVE,
            title = channel.name,
            subtitle = channel.categoryName,
            posterUrl = channel.logoUrl,
            streamUrl = candidates.firstOrNull() ?: channel.streamUrl,
            playlistId = channel.playlistId,
            liveChannelQueue = queue.ifEmpty { listOf(channel) },
            candidateUrls = candidates
        )
        launchPlayback(request)
    }

    fun startMoviePlayback(movie: VodMovie, resumeMs: Long = 0L) {
        val cw = (continueWatchingState.value as? UiState.Success)?.data
            ?.firstOrNull { it.itemId == movie.id && it.contentType == "MOVIE" }
        val resolvedResume = if (resumeMs > 0L) resumeMs else cw?.positionMs ?: 0L
        val candidates = com.example.data.network.StreamUrlResolver.deriveFallbackCandidates(
            primaryUrl = movie.streamUrl,
            contentType = MediaContentType.MOVIE,
            explicitCandidates = movie.candidateUrls
        )
        val request = PlaybackRequest(
            itemId = movie.id,
            contentType = MediaContentType.MOVIE,
            title = movie.title,
            subtitle = movie.categoryName,
            posterUrl = movie.posterUrl,
            streamUrl = candidates.firstOrNull() ?: movie.streamUrl,
            playlistId = movie.playlistId,
            resumePositionMs = resolvedResume,
            preferredAudioLang = cw?.preferredAudioLang.orEmpty(),
            candidateUrls = candidates
        )
        launchPlayback(request)
    }

    fun startEpisodePlayback(episode: SeriesEpisode, seasonEpisodes: List<SeriesEpisode>, resumeMs: Long = 0L) {
        val cw = (continueWatchingState.value as? UiState.Success)?.data
            ?.firstOrNull { it.itemId == episode.id && it.contentType == "EPISODE" }
        val resolvedResume = if (resumeMs > 0L) resumeMs else cw?.positionMs ?: 0L
        val candidates = com.example.data.network.StreamUrlResolver.deriveFallbackCandidates(
            primaryUrl = episode.streamUrl,
            contentType = MediaContentType.EPISODE,
            explicitCandidates = episode.candidateUrls
        )
        val request = PlaybackRequest(
            itemId = episode.id,
            contentType = MediaContentType.EPISODE,
            title = episode.title,
            subtitle = "${episode.seriesTitle} • S${episode.seasonNumber} E${episode.episodeNumber}",
            posterUrl = episode.thumbnailUrl,
            streamUrl = candidates.firstOrNull() ?: episode.streamUrl,
            playlistId = episode.playlistId,
            seriesId = episode.seriesId,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            resumePositionMs = resolvedResume,
            preferredAudioLang = cw?.preferredAudioLang.orEmpty(),
            episodeQueue = seasonEpisodes,
            candidateUrls = candidates
        )
        launchPlayback(request)
    }

    fun startPublicSamplePlayback(sample: com.example.player.PublicSampleStream) {
        _activePlayback.value = PlaybackRequest(
            itemId = sample.id,
            contentType = MediaContentType.MOVIE,
            title = sample.title,
            subtitle = sample.subtitle,
            posterUrl = "",
            streamUrl = sample.url,
            playlistId = "diagnostic_sample",
            candidateUrls = listOf(sample.url),
            isDiagnosticSample = true
        )
    }

    fun resumeContinueWatching(doc: ContinueWatchingDoc) {
        val type = when (doc.contentType) {
            "MOVIE" -> MediaContentType.MOVIE
            "EPISODE" -> MediaContentType.EPISODE
            else -> MediaContentType.LIVE
        }
        val visibleCatalog = (visibleCatalogState.value as? UiState.Success)?.data
        val queue = if (type == MediaContentType.LIVE && visibleCatalog != null) {
            visibleCatalog.liveChannels
        } else {
            emptyList()
        }
        val candidates = com.example.data.network.StreamUrlResolver.deriveFallbackCandidates(
            primaryUrl = doc.streamUrl,
            contentType = type
        )
        val request = PlaybackRequest(
            itemId = doc.itemId,
            contentType = type,
            title = doc.title,
            subtitle = doc.subtitle,
            posterUrl = doc.posterUrl,
            streamUrl = candidates.firstOrNull() ?: doc.streamUrl,
            playlistId = doc.playlistId,
            seriesId = doc.seriesId,
            seasonNumber = doc.seasonNumber.toInt(),
            episodeNumber = doc.episodeNumber.toInt(),
            resumePositionMs = doc.positionMs,
            preferredAudioLang = doc.preferredAudioLang,
            liveChannelQueue = queue,
            candidateUrls = candidates
        )
        launchPlayback(request)
    }

    private fun launchPlayback(request: PlaybackRequest) {
        _activePlayback.value = request
        if (!request.isDiagnosticSample && playerLocalSettings.value.watchHistoryEnabled) {
            viewModelScope.launch {
                userSyncRepository.recordWatchHistory(currentUserId, request)
            }
        }
    }

    fun updateActivePlaybackRequest(request: PlaybackRequest) {
        _activePlayback.value = request
        if (!request.isDiagnosticSample && playerLocalSettings.value.watchHistoryEnabled) {
            viewModelScope.launch {
                userSyncRepository.recordWatchHistory(currentUserId, request)
            }
        }
    }

    fun closePlayback() {
        _activePlayback.value = null
    }

    fun savePlaybackProgress(
        request: PlaybackRequest,
        positionMs: Long,
        durationMs: Long,
        selectedAudioLang: String
    ) {
        if (request.streamUrl.isBlank() || request.isDiagnosticSample) return
        if (!playerLocalSettings.value.watchHistoryEnabled) return
        viewModelScope.launch {
            userSyncRepository.upsertContinueWatching(
                userId = currentUserId,
                request = request,
                positionMs = positionMs,
                durationMs = durationMs,
                selectedAudioLang = selectedAudioLang
            )
        }
    }

    fun toggleFavourite(
        itemId: String,
        contentType: MediaContentType,
        title: String,
        posterUrl: String,
        categoryName: String,
        streamUrl: String,
        playlistId: String
    ) {
        viewModelScope.launch {
            val result = userSyncRepository.toggleFavourite(
                userId = currentUserId,
                itemId = itemId,
                contentType = contentType,
                title = title,
                posterUrl = posterUrl,
                categoryName = categoryName,
                streamUrl = streamUrl,
                playlistId = playlistId
            )
            result.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to update favourites"
            }
        }
    }

    fun isFavourite(itemId: String, contentType: MediaContentType, playlistId: String): Boolean {
        val list = (favouritesState.value as? UiState.Success)?.data ?: return false
        val expectedType = when (contentType) {
            MediaContentType.LIVE -> "LIVE"
            MediaContentType.MOVIE -> "MOVIE"
            MediaContentType.SERIES, MediaContentType.EPISODE -> "SERIES"
        }
        return list.any { it.itemId == itemId && it.contentType == expectedType && it.playlistId == playlistId }
    }

    fun updatePreferences(
        audioPriority: List<String>,
        autoAudioEnabled: Boolean,
        preferHardwareDecoder: Boolean,
        defaultPlayerResizeMode: String
    ) {
        viewModelScope.launch {
            val local = playerLocalSettings.value
            val res = userSyncRepository.savePreferences(
                userId = currentUserId,
                audioPriority = audioPriority,
                autoAudioEnabled = autoAudioEnabled,
                preferHardwareDecoder = preferHardwareDecoder,
                defaultPlayerResizeMode = defaultPlayerResizeMode,
                watchHistoryEnabled = local.watchHistoryEnabled,
                showFavouriteLiveChannelsFirst = local.showFavouriteLiveFirst
            )
            res.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Could not save preferences"
            }
        }
    }

    fun updatePlayerLocalSettings(settings: PlayerLocalSettings) {
        viewModelScope.launch {
            playlistVaultRepository.updatePlayerLocalSettings(settings)
            val currentPrefs = (preferencesState.value as? UiState.Success)?.data
                ?: UserPreferencesDoc(userId = currentUserId)
            val shouldPreferHw = settings.videoDecoderMode != VideoDecoderMode.SOFTWARE
            if (currentPrefs.preferHardwareDecoder != shouldPreferHw ||
                currentPrefs.watchHistoryEnabled != settings.watchHistoryEnabled ||
                currentPrefs.showFavouriteLiveChannelsFirst != settings.showFavouriteLiveFirst
            ) {
                userSyncRepository.savePreferences(
                    userId = currentUserId,
                    audioPriority = currentPrefs.audioPriority,
                    autoAudioEnabled = currentPrefs.autoAudioEnabled,
                    preferHardwareDecoder = shouldPreferHw,
                    defaultPlayerResizeMode = currentPrefs.defaultPlayerResizeMode,
                    watchHistoryEnabled = settings.watchHistoryEnabled,
                    showFavouriteLiveChannelsFirst = settings.showFavouriteLiveFirst
                )
            }
        }
    }

    fun setWatchHistoryEnabled(enabled: Boolean) {
        localPrivacyOrLiveEditedInSession = true
        viewModelScope.launch {
            playlistVaultRepository.setWatchHistoryEnabled(enabled)
            val currentPrefs = (preferencesState.value as? UiState.Success)?.data
                ?: UserPreferencesDoc(userId = currentUserId)
            userSyncRepository.savePreferences(
                userId = currentUserId,
                audioPriority = currentPrefs.audioPriority,
                autoAudioEnabled = currentPrefs.autoAudioEnabled,
                preferHardwareDecoder = currentPrefs.preferHardwareDecoder,
                defaultPlayerResizeMode = currentPrefs.defaultPlayerResizeMode,
                watchHistoryEnabled = enabled,
                showFavouriteLiveChannelsFirst = playerLocalSettings.value.showFavouriteLiveFirst
            )
        }
    }

    fun setShowFavouriteLiveFirst(enabled: Boolean) {
        localPrivacyOrLiveEditedInSession = true
        viewModelScope.launch {
            playlistVaultRepository.setShowFavouriteLiveFirst(enabled)
            val currentPrefs = (preferencesState.value as? UiState.Success)?.data
                ?: UserPreferencesDoc(userId = currentUserId)
            userSyncRepository.savePreferences(
                userId = currentUserId,
                audioPriority = currentPrefs.audioPriority,
                autoAudioEnabled = currentPrefs.autoAudioEnabled,
                preferHardwareDecoder = currentPrefs.preferHardwareDecoder,
                defaultPlayerResizeMode = currentPrefs.defaultPlayerResizeMode,
                watchHistoryEnabled = playerLocalSettings.value.watchHistoryEnabled,
                showFavouriteLiveChannelsFirst = enabled
            )
        }
    }

    fun setCategoryVisibility(
        playlistId: String,
        contentType: MediaContentType,
        categoryId: String,
        visible: Boolean
    ) {
        if (playlistId.isBlank() || categoryId.isBlank()) return
        val current = hiddenCategoriesByPlaylist.value[playlistId] ?: PlaylistHiddenCategories()
        val updated = current.withCategoryVisibility(contentType, categoryId, visible)
        viewModelScope.launch {
            playlistVaultRepository.setHiddenCategoriesForPlaylist(playlistId, updated)
            userSyncRepository.saveCategoryVisibility(currentUserId, playlistId, updated)
        }
    }

    fun showAllCategories(playlistId: String) {
        if (playlistId.isBlank()) return
        val empty = PlaylistHiddenCategories()
        viewModelScope.launch {
            playlistVaultRepository.setHiddenCategoriesForPlaylist(playlistId, empty)
            userSyncRepository.saveCategoryVisibility(currentUserId, playlistId, empty)
            _statusBanner.value = "All categories set to visible"
        }
    }

    fun hideAllCategories(playlistId: String, rawCatalog: PlaylistCatalog?) {
        if (playlistId.isBlank() || rawCatalog == null) return
        val allHidden = PlaylistHiddenCategories(
            liveCategoryIds = (rawCatalog.liveCategories.map { it.id } + rawCatalog.liveChannels.map { it.categoryId })
                .filter { it.isNotBlank() }.toSet(),
            movieCategoryIds = (rawCatalog.vodCategories.map { it.id } + rawCatalog.vodMovies.map { it.categoryId })
                .filter { it.isNotBlank() }.toSet(),
            seriesCategoryIds = (rawCatalog.seriesCategories.map { it.id } + rawCatalog.seriesList.map { it.categoryId })
                .filter { it.isNotBlank() }.toSet()
        )
        viewModelScope.launch {
            playlistVaultRepository.setHiddenCategoriesForPlaylist(playlistId, allHidden)
            userSyncRepository.saveCategoryVisibility(currentUserId, playlistId, allHidden)
            _statusBanner.value = "All categories hidden for active playlist"
        }
    }

    fun resetCategoriesToDefault(playlistId: String) {
        if (playlistId.isBlank()) return
        val empty = PlaylistHiddenCategories()
        viewModelScope.launch {
            playlistVaultRepository.setHiddenCategoriesForPlaylist(playlistId, empty)
            userSyncRepository.saveCategoryVisibility(currentUserId, playlistId, empty)
            _statusBanner.value = "Category visibility reset to default (all visible)"
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            val res = userSyncRepository.clearWatchHistory(currentUserId)
            res.onSuccess {
                _statusBanner.value = "Watch history and playback progress cleared"
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to clear history"
            }
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    private suspend inline fun handleStreamError(
        error: Throwable,
        streamName: String,
        emitError: (String) -> Unit
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $streamName", rootError)
        emitError(rootError.localizedMessage ?: "Failed to sync $streamName")
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
