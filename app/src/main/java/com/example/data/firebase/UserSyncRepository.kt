package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.CategoryVisibilityDoc
import com.example.data.model.ContinueWatchingDoc
import com.example.data.model.FavouriteDoc
import com.example.data.model.MediaContentType
import com.example.data.model.PlaybackRequest
import com.example.data.model.PlaylistHiddenCategories
import com.example.data.model.UserPreferencesDoc
import com.example.data.model.WatchHistoryDoc
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

interface UserSyncGateway {
    fun observePreferences(userId: String): Flow<UserPreferencesDoc>
    suspend fun savePreferences(
        userId: String,
        audioPriority: List<String>,
        autoAudioEnabled: Boolean,
        preferHardwareDecoder: Boolean,
        defaultPlayerResizeMode: String,
        watchHistoryEnabled: Boolean = true,
        showFavouriteLiveChannelsFirst: Boolean = true
    ): Result<Unit>

    fun observeCategoryVisibility(userId: String): Flow<Map<String, PlaylistHiddenCategories>>
    suspend fun saveCategoryVisibility(
        userId: String,
        playlistId: String,
        hidden: PlaylistHiddenCategories
    ): Result<Unit>

    fun observeFavourites(userId: String): Flow<List<FavouriteDoc>>
    suspend fun toggleFavourite(
        userId: String,
        itemId: String,
        contentType: MediaContentType,
        title: String,
        posterUrl: String,
        categoryName: String,
        streamUrl: String,
        playlistId: String
    ): Result<Boolean>

    fun observeContinueWatching(userId: String): Flow<List<ContinueWatchingDoc>>
    suspend fun upsertContinueWatching(
        userId: String,
        request: PlaybackRequest,
        positionMs: Long,
        durationMs: Long,
        selectedAudioLang: String
    ): Result<Unit>

    fun observeWatchHistory(userId: String): Flow<List<WatchHistoryDoc>>
    suspend fun recordWatchHistory(
        userId: String,
        request: PlaybackRequest
    ): Result<Unit>

    suspend fun clearWatchHistory(userId: String): Result<Unit>
}

class UserSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : UserSyncGateway {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user Firestore operation." }
        return uid
    }

    override fun observePreferences(userId: String): Flow<UserPreferencesDoc> = flow {
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid).collection("preferences").document(PREF_DOC_ID)
        val path = docRef.path
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserPreferencesDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: UserPreferencesDoc(id = PREF_DOC_ID, userId = uid)
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    override suspend fun savePreferences(
        userId: String,
        audioPriority: List<String>,
        autoAudioEnabled: Boolean,
        preferHardwareDecoder: Boolean,
        defaultPlayerResizeMode: String,
        watchHistoryEnabled: Boolean,
        showFavouriteLiveChannelsFirst: Boolean
    ): Result<Unit> {
        var path = "users/$userId/preferences/$PREF_DOC_ID"
        return try {
            val uid = requireUserId(userId)
            val cleanPriority = audioPriority.map { it.trim().take(16) }.filter { it.isNotBlank() }.take(5)
                .ifEmpty { listOf("hi", "pa", "en") }
            val resizeMode = defaultPlayerResizeMode.takeIf { it in setOf("FIT", "FILL", "ZOOM") } ?: "FIT"
            val docRef = db.collection("users").document(uid).collection("preferences").document(PREF_DOC_ID)
            path = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "audioPriority" to cleanPriority,
                        "autoAudioEnabled" to autoAudioEnabled,
                        "preferHardwareDecoder" to preferHardwareDecoder,
                        "defaultPlayerResizeMode" to resizeMode,
                        "watchHistoryEnabled" to watchHistoryEnabled,
                        "showFavouriteLiveChannelsFirst" to showFavouriteLiveChannelsFirst,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "audioPriority" to cleanPriority,
                        "autoAudioEnabled" to autoAudioEnabled,
                        "preferHardwareDecoder" to preferHardwareDecoder,
                        "defaultPlayerResizeMode" to resizeMode,
                        "watchHistoryEnabled" to watchHistoryEnabled,
                        "showFavouriteLiveChannelsFirst" to showFavouriteLiveChannelsFirst,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override fun observeCategoryVisibility(userId: String): Flow<Map<String, PlaylistHiddenCategories>> = flow {
        val uid = requireUserId(userId)
        val colRef = db.collection("users").document(uid).collection("categoryVisibility")
        val path = colRef.path
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        val parsed = doc.toObject(
                            CategoryVisibilityDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: return@mapNotNull null
                        val plId = parsed.playlistId.ifBlank { doc.id }
                        plId to PlaylistHiddenCategories(
                            liveCategoryIds = parsed.liveCategoryIds.toSet(),
                            movieCategoryIds = parsed.movieCategoryIds.toSet(),
                            seriesCategoryIds = parsed.seriesCategoryIds.toSet()
                        )
                    }.toMap()
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    override suspend fun saveCategoryVisibility(
        userId: String,
        playlistId: String,
        hidden: PlaylistHiddenCategories
    ): Result<Unit> {
        val cleanPlaylistId = playlistId.trim().take(128)
        if (cleanPlaylistId.isBlank()) return Result.success(Unit)
        val docId = safeDocId(cleanPlaylistId)
        var path = "users/$userId/categoryVisibility/$docId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("categoryVisibility").document(docId)
            path = docRef.path
            val liveList = hidden.liveCategoryIds.map { it.take(128) }.filter { it.isNotBlank() }.take(1000)
            val movieList = hidden.movieCategoryIds.map { it.take(128) }.filter { it.isNotBlank() }.take(1000)
            val seriesList = hidden.seriesCategoryIds.map { it.take(128) }.filter { it.isNotBlank() }.take(1000)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "liveCategoryIds" to liveList,
                        "movieCategoryIds" to movieList,
                        "seriesCategoryIds" to seriesList,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "playlistId" to cleanPlaylistId,
                        "liveCategoryIds" to liveList,
                        "movieCategoryIds" to movieList,
                        "seriesCategoryIds" to seriesList,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override fun observeFavourites(userId: String): Flow<List<FavouriteDoc>> = flow {
        val uid = requireUserId(userId)
        val colRef = db.collection("users").document(uid).collection("favourites")
        val path = colRef.path
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FavouriteDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

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
        val safeDocId = safeDocId("${playlistId}_${contentType.name}_$itemId")
        var path = "users/$userId/favourites/$safeDocId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("favourites").document(safeDocId)
            path = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.delete().await()
                Result.success(false)
            } else {
                val safeType = when (contentType) {
                    MediaContentType.LIVE -> "LIVE"
                    MediaContentType.MOVIE -> "MOVIE"
                    MediaContentType.SERIES, MediaContentType.EPISODE -> "SERIES"
                }
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "itemId" to itemId.take(160),
                        "contentType" to safeType,
                        "title" to title.trim().ifBlank { "Untitled" }.take(200),
                        "posterUrl" to posterUrl.take(1000),
                        "categoryName" to categoryName.take(150),
                        "streamUrl" to streamUrl.take(2000),
                        "playlistId" to playlistId.take(128),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override fun observeContinueWatching(userId: String): Flow<List<ContinueWatchingDoc>> = flow {
        val uid = requireUserId(userId)
        val colRef = db.collection("users").document(uid).collection("continueWatching")
        val path = colRef.path
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ContinueWatchingDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    override suspend fun upsertContinueWatching(
        userId: String,
        request: PlaybackRequest,
        positionMs: Long,
        durationMs: Long,
        selectedAudioLang: String
    ): Result<Unit> {
        val docKey = if (request.contentType == MediaContentType.EPISODE && request.seriesId.isNotBlank()) {
            safeDocId("${request.playlistId}_SERIES_${request.seriesId}")
        } else {
            safeDocId("${request.playlistId}_${request.contentType.name}_${request.itemId}")
        }
        var path = "users/$userId/continueWatching/$docKey"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("continueWatching").document(docKey)
            path = docRef.path
            val safeType = when (request.contentType) {
                MediaContentType.MOVIE -> "MOVIE"
                MediaContentType.EPISODE -> "EPISODE"
                else -> "LIVE"
            }
            val safePos = positionMs.coerceAtLeast(0L)
            val safeDur = durationMs.coerceAtLeast(0L)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "itemId" to request.itemId.take(160),
                        "contentType" to safeType,
                        "title" to request.title.trim().ifBlank { "Untitled" }.take(200),
                        "subtitle" to request.subtitle.take(200),
                        "posterUrl" to request.posterUrl.take(1000),
                        "streamUrl" to request.streamUrl.take(2000),
                        "playlistId" to request.playlistId.take(128),
                        "seriesId" to request.seriesId.take(160),
                        "seasonNumber" to request.seasonNumber.coerceIn(0, 500),
                        "episodeNumber" to request.episodeNumber.coerceIn(0, 5000),
                        "positionMs" to safePos,
                        "durationMs" to safeDur,
                        "preferredAudioLang" to selectedAudioLang.take(32),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "itemId" to request.itemId.take(160),
                        "contentType" to safeType,
                        "title" to request.title.trim().ifBlank { "Untitled" }.take(200),
                        "subtitle" to request.subtitle.take(200),
                        "posterUrl" to request.posterUrl.take(1000),
                        "streamUrl" to request.streamUrl.take(2000),
                        "playlistId" to request.playlistId.take(128),
                        "seriesId" to request.seriesId.take(160),
                        "seasonNumber" to request.seasonNumber.coerceIn(0, 500),
                        "episodeNumber" to request.episodeNumber.coerceIn(0, 5000),
                        "positionMs" to safePos,
                        "durationMs" to safeDur,
                        "preferredAudioLang" to selectedAudioLang.take(32),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override fun observeWatchHistory(userId: String): Flow<List<WatchHistoryDoc>> = flow {
        val uid = requireUserId(userId)
        val colRef = db.collection("users").document(uid).collection("watchHistory")
        val path = colRef.path
        emitAll(
            colRef.orderBy("watchedAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WatchHistoryDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    override suspend fun recordWatchHistory(
        userId: String,
        request: PlaybackRequest
    ): Result<Unit> {
        val docKey = safeDocId("${request.playlistId}_${request.contentType.name}_${request.itemId}")
        var path = "users/$userId/watchHistory/$docKey"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("watchHistory").document(docKey)
            path = docRef.path
            val safeType = when (request.contentType) {
                MediaContentType.MOVIE -> "MOVIE"
                MediaContentType.EPISODE -> "EPISODE"
                else -> "LIVE"
            }
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "title" to request.title.trim().ifBlank { "Untitled" }.take(200),
                        "subtitle" to request.subtitle.take(200),
                        "posterUrl" to request.posterUrl.take(1000),
                        "playlistId" to request.playlistId.take(128),
                        "watchedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "itemId" to request.itemId.take(160),
                        "contentType" to safeType,
                        "title" to request.title.trim().ifBlank { "Untitled" }.take(200),
                        "subtitle" to request.subtitle.take(200),
                        "posterUrl" to request.posterUrl.take(1000),
                        "playlistId" to request.playlistId.take(128),
                        "watchedAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun clearWatchHistory(userId: String): Result<Unit> {
        val colPath = "users/$userId/watchHistory+continueWatching"
        return try {
            val uid = requireUserId(userId)
            val historyDocs = db.collection("users").document(uid).collection("watchHistory").get().await()
            val cwDocs = db.collection("users").document(uid).collection("continueWatching").get().await()
            val refsToDelete: List<DocumentReference> =
                historyDocs.documents.map { it.reference } + cwDocs.documents.map { it.reference }
            refsToDelete.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colPath)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    companion object {
        const val PREF_DOC_ID = "default"

        fun safeDocId(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
