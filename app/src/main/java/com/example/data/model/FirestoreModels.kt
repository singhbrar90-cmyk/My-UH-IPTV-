package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserPreferencesDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val audioPriority: List<String> = listOf("hi", "pa", "en"),
    val autoAudioEnabled: Boolean = true,
    val preferHardwareDecoder: Boolean = true,
    val defaultPlayerResizeMode: String = "FIT",
    val watchHistoryEnabled: Boolean = true,
    val showFavouriteLiveChannelsFirst: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CategoryVisibilityDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val playlistId: String = "",
    val liveCategoryIds: List<String> = emptyList(),
    val movieCategoryIds: List<String> = emptyList(),
    val seriesCategoryIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FavouriteDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemId: String = "",
    val contentType: String = "LIVE",
    val title: String = "",
    val posterUrl: String = "",
    val categoryName: String = "",
    val streamUrl: String = "",
    val playlistId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ContinueWatchingDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemId: String = "",
    val contentType: String = "MOVIE",
    val title: String = "",
    val subtitle: String = "",
    val posterUrl: String = "",
    val streamUrl: String = "",
    val playlistId: String = "",
    val seriesId: String = "",
    val seasonNumber: Long = 0L,
    val episodeNumber: Long = 0L,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val preferredAudioLang: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WatchHistoryDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemId: String = "",
    val contentType: String = "LIVE",
    val title: String = "",
    val subtitle: String = "",
    val posterUrl: String = "",
    val playlistId: String = "",
    @ServerTimestamp val watchedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
