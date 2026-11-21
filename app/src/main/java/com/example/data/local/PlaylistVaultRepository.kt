package com.example.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.PlaylistHiddenCategories
import com.example.data.model.SavedPlaylist
import com.example.player.PlayerLocalSettings
import com.example.player.VideoDecoderMode
import com.example.player.VideoPlayerEngine
import com.example.player.VideoQualityTier
import com.example.player.VideoSurfaceMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playlistVaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "uh_iptv_vault")

class PlaylistVaultRepository(context: Context) {
    private val appContext = context.applicationContext
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, SavedPlaylist::class.java)
    private val adapter = moshi.adapter<List<SavedPlaylist>>(listType)
    private val hiddenMapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        PlaylistHiddenCategories::class.java
    )
    private val hiddenMapAdapter = moshi.adapter<Map<String, PlaylistHiddenCategories>>(hiddenMapType)

    private val encryptedPlaylistsKey = stringPreferencesKey("encrypted_playlists_v1")
    private val activePlaylistIdKey = stringPreferencesKey("active_playlist_id_v1")
    private val videoPlayerEngineKey = stringPreferencesKey("video_player_engine_v1")
    private val videoDecoderModeKey = stringPreferencesKey("video_decoder_mode_v1")
    private val defaultQualityTierKey = stringPreferencesKey("default_video_quality_v1")
    private val showTechnicalInfoKey = booleanPreferencesKey("show_technical_playback_info_v1")
    private val videoSurfaceModeKey = stringPreferencesKey("video_surface_mode_v1")
    private val watchHistoryEnabledKey = booleanPreferencesKey("watch_history_enabled_v1")
    private val showFavouriteLiveFirstKey = booleanPreferencesKey("show_favourite_live_first_v1")
    private val hiddenCategoriesByPlaylistKey = stringPreferencesKey("hidden_categories_by_playlist_v1")

    val playlistsFlow: Flow<List<SavedPlaylist>> = appContext.playlistVaultDataStore.data.map { prefs ->
        val rawEncrypted = prefs[encryptedPlaylistsKey] ?: return@map emptyList()
        try {
            val json = CryptoManager.decrypt(rawEncrypted)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt local playlist vault", e)
            emptyList()
        }
    }

    val activePlaylistIdFlow: Flow<String?> = appContext.playlistVaultDataStore.data.map { prefs ->
        prefs[activePlaylistIdKey]
    }

    val playerLocalSettingsFlow: Flow<PlayerLocalSettings> = appContext.playlistVaultDataStore.data.map { prefs ->
        val tier = VideoQualityTier.entries.firstOrNull {
            it.name.equals(prefs[defaultQualityTierKey], ignoreCase = true)
        } ?: VideoQualityTier.AUTO
        PlayerLocalSettings(
            preferredPlayerEngine = VideoPlayerEngine.fromStored(prefs[videoPlayerEngineKey]),
            videoDecoderMode = VideoDecoderMode.fromStored(prefs[videoDecoderModeKey]),
            defaultQualityTier = tier,
            showTechnicalPlaybackInfo = prefs[showTechnicalInfoKey] ?: false,
            videoSurfaceMode = VideoSurfaceMode.fromStored(prefs[videoSurfaceModeKey]),
            watchHistoryEnabled = prefs[watchHistoryEnabledKey] ?: true,
            showFavouriteLiveFirst = prefs[showFavouriteLiveFirstKey] ?: true
        )
    }

    val hiddenCategoriesFlow: Flow<Map<String, PlaylistHiddenCategories>> = appContext.playlistVaultDataStore.data.map { prefs ->
        decodeHiddenCategories(prefs[hiddenCategoriesByPlaylistKey])
    }

    suspend fun updatePlayerLocalSettings(settings: PlayerLocalSettings) {
        appContext.playlistVaultDataStore.edit { prefs ->
            prefs[videoPlayerEngineKey] = settings.preferredPlayerEngine.name
            prefs[videoDecoderModeKey] = settings.videoDecoderMode.name
            prefs[defaultQualityTierKey] = settings.defaultQualityTier.name
            prefs[showTechnicalInfoKey] = settings.showTechnicalPlaybackInfo
            prefs[videoSurfaceModeKey] = settings.videoSurfaceMode.name
            prefs[watchHistoryEnabledKey] = settings.watchHistoryEnabled
            prefs[showFavouriteLiveFirstKey] = settings.showFavouriteLiveFirst
        }
    }

    suspend fun setWatchHistoryEnabled(enabled: Boolean) {
        appContext.playlistVaultDataStore.edit { prefs ->
            prefs[watchHistoryEnabledKey] = enabled
        }
    }

    suspend fun setShowFavouriteLiveFirst(enabled: Boolean) {
        appContext.playlistVaultDataStore.edit { prefs ->
            prefs[showFavouriteLiveFirstKey] = enabled
        }
    }

    suspend fun setHiddenCategoriesForPlaylist(playlistId: String, hidden: PlaylistHiddenCategories) {
        if (playlistId.isBlank()) return
        appContext.playlistVaultDataStore.edit { prefs ->
            val current = decodeHiddenCategories(prefs[hiddenCategoriesByPlaylistKey]).toMutableMap()
            if (hidden.isEmpty()) {
                current.remove(playlistId)
            } else {
                current[playlistId] = hidden
            }
            prefs[hiddenCategoriesByPlaylistKey] = hiddenMapAdapter.toJson(current)
        }
    }

    suspend fun mergeHiddenCategoriesFromCloud(cloudMap: Map<String, PlaylistHiddenCategories>) {
        if (cloudMap.isEmpty()) return
        appContext.playlistVaultDataStore.edit { prefs ->
            val current = decodeHiddenCategories(prefs[hiddenCategoriesByPlaylistKey]).toMutableMap()
            var changed = false
            cloudMap.forEach { (playlistId, hidden) ->
                if (!current.containsKey(playlistId) && !hidden.isEmpty()) {
                    current[playlistId] = hidden
                    changed = true
                }
            }
            if (changed) {
                prefs[hiddenCategoriesByPlaylistKey] = hiddenMapAdapter.toJson(current)
            }
        }
    }

    suspend fun upsertPlaylist(playlist: SavedPlaylist, makeActive: Boolean = true) {
        appContext.playlistVaultDataStore.edit { prefs ->
            val current = decodePlaylists(prefs[encryptedPlaylistsKey])
            val updated = current.filterNot { it.id == playlist.id } + playlist
            prefs[encryptedPlaylistsKey] = CryptoManager.encrypt(adapter.toJson(updated))
            if (makeActive || prefs[activePlaylistIdKey].isNullOrBlank()) {
                prefs[activePlaylistIdKey] = playlist.id
            }
        }
    }

    suspend fun setActivePlaylist(playlistId: String) {
        appContext.playlistVaultDataStore.edit { prefs ->
            prefs[activePlaylistIdKey] = playlistId
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        appContext.playlistVaultDataStore.edit { prefs ->
            val current = decodePlaylists(prefs[encryptedPlaylistsKey])
            val remaining = current.filterNot { it.id == playlistId }
            prefs[encryptedPlaylistsKey] = CryptoManager.encrypt(adapter.toJson(remaining))
            if (prefs[activePlaylistIdKey] == playlistId) {
                val nextId = remaining.firstOrNull()?.id
                if (nextId != null) {
                    prefs[activePlaylistIdKey] = nextId
                } else {
                    prefs.remove(activePlaylistIdKey)
                }
            }
        }
    }

    private fun decodePlaylists(rawEncrypted: String?): List<SavedPlaylist> {
        if (rawEncrypted.isNullOrBlank()) return emptyList()
        return try {
            adapter.fromJson(CryptoManager.decrypt(rawEncrypted)) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode playlists", e)
            emptyList()
        }
    }

    private fun decodeHiddenCategories(rawJson: String?): Map<String, PlaylistHiddenCategories> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        return try {
            hiddenMapAdapter.fromJson(rawJson) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode hidden categories", e)
            emptyMap()
        }
    }

    private companion object {
        const val TAG = "PlaylistVault"
    }
}
