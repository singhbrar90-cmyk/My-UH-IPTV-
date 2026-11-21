package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.UserSyncGateway
import com.example.data.local.PlaylistVaultRepository
import com.example.data.model.ContentVisibilityFilter
import com.example.data.model.ContinueWatchingDoc
import com.example.data.model.FavouriteDoc
import com.example.data.model.LiveChannel
import com.example.data.model.MediaCategory
import com.example.data.model.MediaContentType
import com.example.data.model.PlaybackRequest
import com.example.data.model.PlaylistHiddenCategories
import com.example.data.model.PlaylistType
import com.example.data.model.SavedPlaylist
import com.example.data.model.TvSeries
import com.example.data.model.UiState
import com.example.data.model.UserPreferencesDoc
import com.example.data.model.VodMovie
import com.example.data.model.WatchHistoryDoc
import com.example.data.network.M3uParser
import com.example.data.network.PlaylistCatalog
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsAndVisibilityRobolectricTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun m3uSeries_preservesParsedGroupTitleAsCategory() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Punjabi Shows",Dil Diyan S01E01
            http://stream.example.com:8080/series/u/p/1.mkv
        """.trimIndent()
        val catalog = M3uParser.parse(m3u, "pl_m3u")
        assertEquals(1, catalog.seriesCategories.size)
        assertEquals("Punjabi Shows", catalog.seriesCategories[0].id)
        assertEquals("Punjabi Shows", catalog.seriesList[0].categoryId)
        assertEquals("Punjabi Shows", catalog.seriesList[0].categoryName)
    }

    @Test
    fun watchHistoryToggle_gatesRecordingAndClearDeletesHistoryAndProgress() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = PlaylistVaultRepository(context)
        val fakeSync = FakeUserSyncGateway()

        val vm = MainViewModel(
            currentUserId = "uid_1",
            userSyncRepository = fakeSync,
            playlistVaultRepository = vault
        )
        advanceUntilIdle()

        // Default is ON
        assertTrue(vm.playerLocalSettings.first().watchHistoryEnabled)

        val liveCh = LiveChannel("ch_1", 1, "News HD", "", "cat_news", "News", "http://s/1.ts", playlistId = "pl_1")
        val movie = VodMovie("mov_1", "Action Hit", "", "", "cat_act", "Action", "http://s/m.mp4", playlistId = "pl_1")

        // Record while ON
        vm.startLiveChannelPlayback(liveCh, listOf(liveCh))
        vm.savePlaybackProgress(
            PlaybackRequest("mov_1", MediaContentType.MOVIE, "Action Hit", "Action", "", "http://s/m.mp4", "pl_1"),
            positionMs = 45_000L,
            durationMs = 120_000L,
            selectedAudioLang = "hi"
        )
        advanceUntilIdle()

        assertEquals(1, fakeSync.historyFlow.value.size)
        assertEquals(1, fakeSync.continueWatchingFlow.value.size)

        // Toggle OFF: existing history MUST NOT be deleted, and new playback must NOT record or upload
        vm.setWatchHistoryEnabled(false)
        advanceUntilIdle()
        assertFalse(vault.playerLocalSettingsFlow.first().watchHistoryEnabled)
        assertEquals(1, fakeSync.historyFlow.value.size)
        assertEquals(1, fakeSync.continueWatchingFlow.value.size)

        vm.startMoviePlayback(movie)
        vm.savePlaybackProgress(
            PlaybackRequest("mov_2", MediaContentType.MOVIE, "Blocked Entry", "Drama", "", "http://s/m2.mp4", "pl_1"),
            positionMs = 30_000L,
            durationMs = 90_000L,
            selectedAudioLang = "pa"
        )
        advanceUntilIdle()
        assertEquals(1, fakeSync.historyFlow.value.size)
        assertEquals(1, fakeSync.continueWatchingFlow.value.size)

        // Persist across simulated restart (re-creating Vault + ViewModel reading same DataStore)
        val restartedVault = PlaylistVaultRepository(context)
        assertFalse(restartedVault.playerLocalSettingsFlow.first().watchHistoryEnabled)

        // Clear Watch History deletes BOTH watchHistory and continueWatching
        vm.clearWatchHistory()
        advanceUntilIdle()
        assertTrue(fakeSync.historyFlow.value.isEmpty())
        assertTrue(fakeSync.continueWatchingFlow.value.isEmpty())

        // Turn ON again -> resumes recording
        vm.setWatchHistoryEnabled(true)
        advanceUntilIdle()
        vm.startMoviePlayback(movie)
        advanceUntilIdle()
        assertEquals(1, fakeSync.historyFlow.value.size)
        assertEquals("mov_1", fakeSync.historyFlow.value.first().itemId)
    }

    @Test
    fun liveTvFavouritesFirst_ordersFavouritesTopWithoutDuplicatesAndRestoresNormalOrderWhenOff() {
        val ch1 = LiveChannel("1", 1, "Alpha News", "", "c1", "News", "http://s/1.ts", playlistId = "pl_1")
        val ch2 = LiveChannel("2", 2, "Beta Sports", "", "c2", "Sports", "http://s/2.ts", playlistId = "pl_1")
        val ch3 = LiveChannel("3", 3, "Gamma Cinema", "", "c3", "Movies", "http://s/3.ts", playlistId = "pl_1")
        val ch4 = LiveChannel("4", 4, "Delta Music", "", "c4", "Music", "http://s/4.ts", playlistId = "pl_1")
        val favIds = setOf("2", "4")

        val favFirstGroups = ContentVisibilityFilter.partitionLiveChannelsForDisplay(
            channels = listOf(ch1, ch2, ch3, ch4),
            showFavouritesFirst = true,
            isFavourite = { it.id in favIds }
        )
        assertEquals(listOf("2", "4"), favFirstGroups.favouriteChannels.map { it.id })
        assertEquals(listOf("1", "3"), favFirstGroups.otherChannels.map { it.id })
        assertEquals(listOf("2", "4", "1", "3"), favFirstGroups.orderedQueue.map { it.id })
        // Zero duplicates across the two sections
        val combinedIds = favFirstGroups.favouriteChannels.map { it.id } + favFirstGroups.otherChannels.map { it.id }
        assertEquals(4, combinedIds.toSet().size)

        val normalGroups = ContentVisibilityFilter.partitionLiveChannelsForDisplay(
            channels = listOf(ch1, ch2, ch3, ch4),
            showFavouritesFirst = false,
            isFavourite = { it.id in favIds }
        )
        assertTrue(normalGroups.favouriteChannels.isEmpty())
        assertEquals(listOf("1", "2", "3", "4"), normalGroups.otherChannels.map { it.id })
    }

    @Test
    fun categoryHiding_filtersBrowsingHomeFavouritesContinueWatchingSearchAndPreservesSavedDataPerPlaylist() =
        runTest(testDispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val vault = PlaylistVaultRepository(context)
            val fakeSync = FakeUserSyncGateway()

            val catalogA = PlaylistCatalog(
                liveCategories = listOf(MediaCategory("lc1", "News"), MediaCategory("lc2", "Adult")),
                liveChannels = listOf(
                    LiveChannel("ch_news", 1, "World News", "", "lc1", "News", "http://s/1.ts", playlistId = "pl_a"),
                    LiveChannel("ch_adult", 2, "Night Show", "", "lc2", "Adult", "http://s/2.ts", playlistId = "pl_a")
                ),
                vodCategories = listOf(MediaCategory("vc1", "Action"), MediaCategory("vc2", "Horror")),
                vodMovies = listOf(
                    VodMovie("m_act", "Fast Hero", "", "", "vc1", "Action", "http://s/m1.mp4", playlistId = "pl_a"),
                    VodMovie("m_hor", "Scary Night", "", "", "vc2", "Horror", "http://s/m2.mp4", playlistId = "pl_a")
                ),
                seriesCategories = listOf(MediaCategory("sc1", "Comedy"), MediaCategory("sc2", "Crime")),
                seriesList = listOf(
                    TvSeries("s_com", "Laugh Club", "", "", "sc1", "Comedy", playlistId = "pl_a"),
                    TvSeries("s_cri", "Dark Case", "", "", "sc2", "Crime", playlistId = "pl_a")
                )
            )

            // Pre-save favourites and continue watching across categories
            fakeSync.favouritesFlow.value = listOf(
                FavouriteDoc(id = "f1", userId = "uid_1", itemId = "ch_adult", contentType = "LIVE", title = "Night Show", categoryName = "Adult", playlistId = "pl_a"),
                FavouriteDoc(id = "f2", userId = "uid_1", itemId = "ch_news", contentType = "LIVE", title = "World News", categoryName = "News", playlistId = "pl_a")
            )
            fakeSync.continueWatchingFlow.value = listOf(
                ContinueWatchingDoc(id = "cw1", userId = "uid_1", itemId = "m_hor", contentType = "MOVIE", title = "Scary Night", subtitle = "Horror", playlistId = "pl_a"),
                ContinueWatchingDoc(id = "cw2", userId = "uid_1", itemId = "ep_1", contentType = "EPISODE", title = "Dark Case E1", seriesId = "s_cri", playlistId = "pl_a"),
                ContinueWatchingDoc(id = "cw3", userId = "uid_1", itemId = "m_act", contentType = "MOVIE", title = "Fast Hero", subtitle = "Action", playlistId = "pl_a")
            )
            fakeSync.historyFlow.value = listOf(
                WatchHistoryDoc(id = "h1", userId = "uid_1", itemId = "m_hor", contentType = "MOVIE", title = "Scary Night", subtitle = "Horror", playlistId = "pl_a"),
                WatchHistoryDoc(id = "h2", userId = "uid_1", itemId = "m_act", contentType = "MOVIE", title = "Fast Hero", subtitle = "Action", playlistId = "pl_a")
            )

            val hiddenA = PlaylistHiddenCategories(
                liveCategoryIds = setOf("lc2"),
                movieCategoryIds = setOf("vc2"),
                seriesCategoryIds = setOf("sc2")
            )
            vault.setHiddenCategoriesForPlaylist("pl_a", hiddenA)
            vault.upsertPlaylist(SavedPlaylist("pl_a", "Playlist A", PlaylistType.M3U, m3uUrl = "http://a.m3u"), makeActive = true)
            vault.upsertPlaylist(SavedPlaylist("pl_b", "Playlist B", PlaylistType.M3U, m3uUrl = "http://b.m3u"), makeActive = false)
            advanceUntilIdle()

            // Verify filtered catalog hides lc2, vc2, sc2 and their items
            val filteredA = ContentVisibilityFilter.filterCatalog(catalogA, hiddenA)
            assertEquals(listOf("lc1"), filteredA.liveCategories.map { it.id })
            assertEquals(listOf("ch_news"), filteredA.liveChannels.map { it.id })
            assertEquals(listOf("vc1"), filteredA.vodCategories.map { it.id })
            assertEquals(listOf("m_act"), filteredA.vodMovies.map { it.id })
            assertEquals(listOf("sc1"), filteredA.seriesCategories.map { it.id })
            assertEquals(listOf("s_com"), filteredA.seriesList.map { it.id })

            val hiddenMap = vault.hiddenCategoriesFlow.first()
            // Separate per playlist: Playlist B has no hidden categories
            assertFalse(hiddenMap.containsKey("pl_b"))
            assertEquals(hiddenA, hiddenMap["pl_a"])

            // Favourites display hides ch_adult while Firestore retains both f1 and f2
            val visibleFavs = ContentVisibilityFilter.filterVisibleFavourites(
                favourites = fakeSync.favouritesFlow.value,
                rawCatalog = catalogA,
                activePlaylistId = "pl_a",
                hiddenByPlaylist = hiddenMap
            )
            assertEquals(listOf("ch_news"), visibleFavs.map { it.itemId })
            assertEquals(2, fakeSync.favouritesFlow.value.size)

            // Continue Watching and History hide items from hidden categories while preserving records
            val visibleCw = ContentVisibilityFilter.filterVisibleContinueWatching(
                items = fakeSync.continueWatchingFlow.value,
                rawCatalog = catalogA,
                activePlaylistId = "pl_a",
                hiddenByPlaylist = hiddenMap
            )
            assertEquals(listOf("m_act"), visibleCw.map { it.itemId })
            assertEquals(3, fakeSync.continueWatchingFlow.value.size)

            val visibleHist = ContentVisibilityFilter.filterVisibleWatchHistory(
                items = fakeSync.historyFlow.value,
                rawCatalog = catalogA,
                activePlaylistId = "pl_a",
                hiddenByPlaylist = hiddenMap
            )
            assertEquals(listOf("m_act"), visibleHist.map { it.itemId })

            // Re-enable categories -> all items & favourites & continue watching reappear
            val restoredHidden = hiddenA.withCategoryVisibility(MediaContentType.LIVE, "lc2", visible = true)
            vault.setHiddenCategoriesForPlaylist("pl_a", restoredHidden)
            val reenabledFavs = ContentVisibilityFilter.filterVisibleFavourites(
                favourites = fakeSync.favouritesFlow.value,
                rawCatalog = catalogA,
                activePlaylistId = "pl_a",
                hiddenByPlaylist = vault.hiddenCategoriesFlow.first()
            )
            assertEquals(2, reenabledFavs.size)

            // Persist across restart & playlist sync
            val restartedVault = PlaylistVaultRepository(context)
            assertEquals(restoredHidden, restartedVault.hiddenCategoriesFlow.first()["pl_a"])
        }

    private class FakeUserSyncGateway : UserSyncGateway {
        val prefsFlow = MutableStateFlow(UserPreferencesDoc(userId = "uid_1"))
        val visibilityFlow = MutableStateFlow<Map<String, PlaylistHiddenCategories>>(emptyMap())
        val favouritesFlow = MutableStateFlow<List<FavouriteDoc>>(emptyList())
        val continueWatchingFlow = MutableStateFlow<List<ContinueWatchingDoc>>(emptyList())
        val historyFlow = MutableStateFlow<List<WatchHistoryDoc>>(emptyList())

        override fun observePreferences(userId: String): Flow<UserPreferencesDoc> = prefsFlow

        override suspend fun savePreferences(
            userId: String,
            audioPriority: List<String>,
            autoAudioEnabled: Boolean,
            preferHardwareDecoder: Boolean,
            defaultPlayerResizeMode: String,
            watchHistoryEnabled: Boolean,
            showFavouriteLiveChannelsFirst: Boolean
        ): Result<Unit> {
            prefsFlow.value = prefsFlow.value.copy(
                audioPriority = audioPriority,
                autoAudioEnabled = autoAudioEnabled,
                preferHardwareDecoder = preferHardwareDecoder,
                defaultPlayerResizeMode = defaultPlayerResizeMode,
                watchHistoryEnabled = watchHistoryEnabled,
                showFavouriteLiveChannelsFirst = showFavouriteLiveChannelsFirst
            )
            return Result.success(Unit)
        }

        override fun observeCategoryVisibility(userId: String): Flow<Map<String, PlaylistHiddenCategories>> =
            visibilityFlow

        override suspend fun saveCategoryVisibility(
            userId: String,
            playlistId: String,
            hidden: PlaylistHiddenCategories
        ): Result<Unit> {
            visibilityFlow.value = visibilityFlow.value + (playlistId to hidden)
            return Result.success(Unit)
        }

        override fun observeFavourites(userId: String): Flow<List<FavouriteDoc>> = favouritesFlow

        override suspend fun toggleFavourite(
            userId: String,
            itemId: String,
            contentType: MediaContentType,
            title: String,
            posterUrl: String,
            categoryName: String,
            streamUrl: String,
            playlistId: String
        ): Result<Boolean> {
            val typeStr = when (contentType) {
                MediaContentType.LIVE -> "LIVE"
                MediaContentType.MOVIE -> "MOVIE"
                else -> "SERIES"
            }
            val current = favouritesFlow.value
            val exists = current.any { it.itemId == itemId && it.playlistId == playlistId }
            favouritesFlow.value = if (exists) {
                current.filterNot { it.itemId == itemId && it.playlistId == playlistId }
            } else {
                current + FavouriteDoc(
                    id = "${playlistId}_$itemId",
                    userId = userId,
                    itemId = itemId,
                    contentType = typeStr,
                    title = title,
                    posterUrl = posterUrl,
                    categoryName = categoryName,
                    streamUrl = streamUrl,
                    playlistId = playlistId
                )
            }
            return Result.success(!exists)
        }

        override fun observeContinueWatching(userId: String): Flow<List<ContinueWatchingDoc>> = continueWatchingFlow

        override suspend fun upsertContinueWatching(
            userId: String,
            request: PlaybackRequest,
            positionMs: Long,
            durationMs: Long,
            selectedAudioLang: String
        ): Result<Unit> {
            val doc = ContinueWatchingDoc(
                id = "${request.playlistId}_${request.itemId}",
                userId = userId,
                itemId = request.itemId,
                contentType = request.contentType.name,
                title = request.title,
                subtitle = request.subtitle,
                posterUrl = request.posterUrl,
                streamUrl = request.streamUrl,
                playlistId = request.playlistId,
                seriesId = request.seriesId,
                seasonNumber = request.seasonNumber.toLong(),
                episodeNumber = request.episodeNumber.toLong(),
                positionMs = positionMs,
                durationMs = durationMs,
                preferredAudioLang = selectedAudioLang
            )
            continueWatchingFlow.value = continueWatchingFlow.value.filterNot { it.itemId == request.itemId } + doc
            return Result.success(Unit)
        }

        override fun observeWatchHistory(userId: String): Flow<List<WatchHistoryDoc>> = historyFlow

        override suspend fun recordWatchHistory(userId: String, request: PlaybackRequest): Result<Unit> {
            val doc = WatchHistoryDoc(
                id = "${request.playlistId}_${request.itemId}",
                userId = userId,
                itemId = request.itemId,
                contentType = request.contentType.name,
                title = request.title,
                subtitle = request.subtitle,
                posterUrl = request.posterUrl,
                playlistId = request.playlistId
            )
            historyFlow.value = historyFlow.value.filterNot { it.itemId == request.itemId } + doc
            return Result.success(Unit)
        }

        override suspend fun clearWatchHistory(userId: String): Result<Unit> {
            historyFlow.value = emptyList()
            continueWatchingFlow.value = emptyList()
            return Result.success(Unit)
        }
    }
}
