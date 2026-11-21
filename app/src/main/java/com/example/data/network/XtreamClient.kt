package com.example.data.network

import android.util.Base64
import android.util.Log
import com.example.data.model.EpgProgram
import com.example.data.model.FlexibleNumberAndStringAdapter
import com.example.data.model.LiveChannel
import com.example.data.model.MediaCategory
import com.example.data.model.PlaylistType
import com.example.data.model.SavedPlaylist
import com.example.data.model.SeriesDetails
import com.example.data.model.SeriesEpisode
import com.example.data.model.TvSeries
import com.example.data.model.VodMovie
import com.example.data.model.XtreamAuthResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

data class PlaylistCatalog(
    val liveCategories: List<MediaCategory>,
    val liveChannels: List<LiveChannel>,
    val vodCategories: List<MediaCategory>,
    val vodMovies: List<VodMovie>,
    val seriesCategories: List<MediaCategory>,
    val seriesList: List<TvSeries>,
    val m3uSeriesDetails: Map<String, SeriesDetails> = emptyMap()
)

sealed interface XtreamValidationResult {
    data class Authenticated(
        val playlist: SavedPlaylist,
        val authResponse: XtreamAuthResponse
    ) : XtreamValidationResult

    data class Failure(val errorMessage: String) : XtreamValidationResult
}

object NetworkClientProvider {
    private const val STREAM_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    private val urlUserInfoAuthenticator = okhttp3.Authenticator { _, response ->
        if (response.request.header("Authorization") != null) {
            return@Authenticator null
        }
        val user = response.request.url.username
        val pass = response.request.url.password
        if (user.isBlank() && pass.isBlank()) {
            return@Authenticator null
        }
        response.request.newBuilder()
            .header("Authorization", Credentials.basic(user, pass))
            .build()
    }

    private fun applyUrlUserInfoBasicAuth(
        original: okhttp3.Request,
        builder: okhttp3.Request.Builder
    ) {
        if (original.header("Authorization").isNullOrBlank() &&
            (original.url.username.isNotBlank() || original.url.password.isNotBlank())
        ) {
            builder.header("Authorization", Credentials.basic(original.url.username, original.url.password))
        }
    }

    // Strictly uses default system TLS validation (never disables SSL certificates)
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .authenticator(urlUserInfoAuthenticator)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("User-Agent", STREAM_USER_AGENT)
                    .header("Accept", "*/*")
                applyUrlUserInfoBasicAuth(original, builder)
                chain.proceed(builder.build())
            }
            .build()
    }

    // Dedicated HTTP/1.1 streaming client for Media3 ExoPlayer to prevent HTTP/2 resets on IPTV edges
    val streamOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .authenticator(urlUserInfoAuthenticator)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                if (original.header("User-Agent").isNullOrBlank()) {
                    builder.header("User-Agent", STREAM_USER_AGENT)
                }
                if (original.header("Accept").isNullOrBlank()) {
                    builder.header("Accept", "*/*")
                }
                if (original.header("Connection").isNullOrBlank()) {
                    builder.header("Connection", "keep-alive")
                }
                applyUrlUserInfoBasicAuth(original, builder)
                chain.proceed(builder.build())
            }
            .build()
    }

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(FlexibleNumberAndStringAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val xtreamApiService: XtreamApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(XtreamApiService::class.java)
    }
}

class XtreamClient(
    private val api: XtreamApiService = NetworkClientProvider.xtreamApiService
) {
    fun normalizeServerBaseUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        if (parsed.scheme !in listOf("http", "https")) return null
        val portSuffix = if ((parsed.scheme == "http" && parsed.port == 80) ||
            (parsed.scheme == "https" && parsed.port == 443)
        ) {
            ""
        } else {
            ":${parsed.port}"
        }
        return "${parsed.scheme}://${parsed.host}$portSuffix"
    }

    suspend fun authenticateAndValidate(
        playlistName: String,
        serverUrl: String,
        username: String,
        password: String,
        existingPlaylistId: String? = null
    ): XtreamValidationResult = withContext(Dispatchers.IO) {
        val cleanName = playlistName.trim().ifBlank { "My Xtream IPTV" }
        val cleanUser = username.trim()
        val cleanPass = password
        val normalizedBase = normalizeServerBaseUrl(serverUrl)
            ?: return@withContext XtreamValidationResult.Failure(
                "Invalid server URL. Enter a valid HTTP or HTTPS URL (e.g., http://example.com:8080)."
            )
        if (cleanUser.isBlank() || cleanPass.isBlank()) {
            return@withContext XtreamValidationResult.Failure("Username and password are required.")
        }

        val apiUrl = "$normalizedBase/player_api.php"
        try {
            val response = api.authenticate(apiUrl, cleanUser, cleanPass)
            if (!response.isSuccessful) {
                val msg = when (response.code()) {
                    401, 403 -> "Authentication rejected by server (HTTP ${response.code()}). Verify username and password."
                    404 -> "Xtream API endpoint not found at $apiUrl (HTTP 404)."
                    else -> "Server returned HTTP ${response.code()}: ${response.message()}"
                }
                return@withContext XtreamValidationResult.Failure(msg)
            }

            val body = response.body()
                ?: return@withContext XtreamValidationResult.Failure("Empty or invalid JSON response from IPTV server.")

            val userInfo = body.userInfo
                ?: return@withContext XtreamValidationResult.Failure(
                    "Invalid Xtream response: missing user_info object. Verify the server supports Xtream Codes API."
                )

            val authFlag = userInfo.auth.trim()
            if (authFlag != "1" && !authFlag.equals("true", ignoreCase = true)) {
                val serverMsg = userInfo.message.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                return@withContext XtreamValidationResult.Failure(
                    "Invalid IPTV credentials or unauthorized account$serverMsg."
                )
            }

            val status = userInfo.status.trim()
            if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
                return@withContext XtreamValidationResult.Failure(
                    "Account status is '$status'. Only Active accounts can connect."
                )
            }

            val allowedFormats = userInfo.allowedOutputFormats
                .orEmpty()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }

            val playlist = SavedPlaylist(
                id = existingPlaylistId ?: UUID.randomUUID().toString(),
                name = cleanName,
                type = PlaylistType.XTREAM,
                serverUrl = normalizedBase,
                username = cleanUser,
                password = cleanPass,
                accountStatus = status.ifBlank { "Active" },
                expirationDate = formatExpDate(userInfo.expDate),
                maxConnections = userInfo.maxConnections.ifBlank { "1" },
                lastSyncedAtMs = System.currentTimeMillis(),
                allowedOutputFormats = allowedFormats
            )
            XtreamValidationResult.Authenticated(playlist, body)
        } catch (e: UnknownHostException) {
            XtreamValidationResult.Failure("Server host could not be resolved. Check the server URL and internet connection.")
        } catch (e: ConnectException) {
            XtreamValidationResult.Failure("Could not connect to $normalizedBase. Check the port and server availability.")
        } catch (e: SocketTimeoutException) {
            XtreamValidationResult.Failure("Connection timed out while contacting $normalizedBase.")
        } catch (e: SSLHandshakeException) {
            XtreamValidationResult.Failure("TLS/SSL certificate verification failed for $normalizedBase.")
        } catch (e: IOException) {
            XtreamValidationResult.Failure(
                "Network error: ${StreamUrlResolver.redactCredentials(e.localizedMessage ?: "Unable to reach IPTV server")}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Xtream authentication failure: ${e.javaClass.simpleName}")
            XtreamValidationResult.Failure(
                "Unexpected API error: ${StreamUrlResolver.redactCredentials(e.localizedMessage ?: "Malformed server response")}"
            )
        }
    }

    suspend fun fetchFullCatalog(playlist: SavedPlaylist): Result<PlaylistCatalog> = withContext(Dispatchers.IO) {
        try {
            val base = normalizeServerBaseUrl(playlist.serverUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid server URL"))
            val apiUrl = "$base/player_api.php"
            val user = playlist.username
            val pass = playlist.password

            coroutineScope {
                val liveCatsDeferred = async { safeList { api.getLiveCategories(apiUrl, user, pass).body() } }
                val liveStreamsDeferred = async { safeList { api.getLiveStreams(apiUrl, user, pass).body() } }
                val vodCatsDeferred = async { safeList { api.getVodCategories(apiUrl, user, pass).body() } }
                val vodStreamsDeferred = async { safeList { api.getVodStreams(apiUrl, user, pass).body() } }
                val seriesCatsDeferred = async { safeList { api.getSeriesCategories(apiUrl, user, pass).body() } }
                val seriesDeferred = async { safeList { api.getSeries(apiUrl, user, pass).body() } }

                val liveCats = liveCatsDeferred.await().map { MediaCategory(it.categoryId, it.categoryName.ifBlank { "General" }) }
                val liveCatMap = liveCats.associate { it.id to it.name }
                val liveChannels = liveStreamsDeferred.await().mapIndexed { index, dto ->
                    val chId = dto.streamId
                    val candidates = StreamUrlResolver.buildXtreamLiveCandidates(
                        baseUrl = base,
                        username = user,
                        password = pass,
                        streamId = chId,
                        allowedOutputFormats = playlist.allowedOutputFormats,
                        directSource = dto.directSource
                    )
                    LiveChannel(
                        id = chId,
                        number = dto.num.toIntOrNull() ?: (index + 1),
                        name = dto.name.ifBlank { "Channel ${index + 1}" },
                        logoUrl = dto.streamIcon,
                        categoryId = dto.categoryId,
                        categoryName = liveCatMap[dto.categoryId] ?: "Live TV",
                        streamUrl = candidates.firstOrNull().orEmpty(),
                        epgChannelId = dto.epgChannelId,
                        playlistId = playlist.id,
                        candidateUrls = candidates
                    )
                }

                val vodCats = vodCatsDeferred.await().map { MediaCategory(it.categoryId, it.categoryName.ifBlank { "Movies" }) }
                val vodCatMap = vodCats.associate { it.id to it.name }
                val vodMovies = vodStreamsDeferred.await().map { dto ->
                    val ext = StreamUrlResolver.cleanExtension(dto.containerExtension, "mp4")
                    val candidates = StreamUrlResolver.buildXtreamVodCandidates(
                        baseUrl = base,
                        username = user,
                        password = pass,
                        streamId = dto.streamId,
                        containerExtension = ext,
                        directSource = dto.directSource
                    )
                    VodMovie(
                        id = dto.streamId,
                        title = dto.name.ifBlank { "Untitled Movie" },
                        posterUrl = dto.streamIcon,
                        backdropUrl = dto.streamIcon,
                        categoryId = dto.categoryId,
                        categoryName = vodCatMap[dto.categoryId] ?: "Movies",
                        streamUrl = candidates.firstOrNull().orEmpty(),
                        containerExtension = ext,
                        rating = dto.rating,
                        playlistId = playlist.id,
                        candidateUrls = candidates
                    )
                }

                val seriesCats = seriesCatsDeferred.await().map { MediaCategory(it.categoryId, it.categoryName.ifBlank { "Series" }) }
                val seriesCatMap = seriesCats.associate { it.id to it.name }
                val seriesList = seriesDeferred.await().map { dto ->
                    TvSeries(
                        id = dto.seriesId,
                        title = dto.name.ifBlank { "Untitled Series" },
                        posterUrl = dto.cover,
                        backdropUrl = dto.cover,
                        categoryId = dto.categoryId,
                        categoryName = seriesCatMap[dto.categoryId] ?: "Series",
                        rating = dto.rating,
                        year = dto.releaseDate,
                        plot = dto.plot,
                        genre = dto.genre,
                        playlistId = playlist.id
                    )
                }

                Result.success(
                    PlaylistCatalog(
                        liveCategories = liveCats,
                        liveChannels = liveChannels,
                        vodCategories = vodCats,
                        vodMovies = vodMovies,
                        seriesCategories = seriesCats,
                        seriesList = seriesList
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Xtream catalog", e)
            Result.failure(e)
        }
    }

    suspend fun fetchShortEpg(playlist: SavedPlaylist, streamId: String): List<EpgProgram> = withContext(Dispatchers.IO) {
        if (playlist.type != PlaylistType.XTREAM) return@withContext emptyList()
        val base = normalizeServerBaseUrl(playlist.serverUrl) ?: return@withContext emptyList()
        try {
            val response = api.getShortEpg(
                apiUrl = "$base/player_api.php",
                username = playlist.username,
                password = playlist.password,
                streamId = streamId
            )
            val listings = response.body()?.listings.orEmpty()
            listings.map { dto ->
                EpgProgram(
                    id = dto.id.ifBlank { "${dto.start}_${dto.title}" },
                    title = decodeBase64OrPlain(dto.title),
                    description = decodeBase64OrPlain(dto.description),
                    startTimeLabel = formatEpgTime(dto.start),
                    endTimeLabel = formatEpgTime(dto.end),
                    isLiveNow = dto.nowPlaying == "1"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "EPG unavailable for stream $streamId", e)
            emptyList()
        }
    }

    suspend fun fetchVodMovieDetails(playlist: SavedPlaylist, movie: VodMovie): VodMovie = withContext(Dispatchers.IO) {
        if (playlist.type != PlaylistType.XTREAM) return@withContext movie
        val base = normalizeServerBaseUrl(playlist.serverUrl) ?: return@withContext movie
        try {
            val body = api.getVodInfo(
                apiUrl = "$base/player_api.php",
                username = playlist.username,
                password = playlist.password,
                vodId = movie.id
            ).body() ?: return@withContext movie
            val info = body.info
            val movieData = body.movieData
            val resolvedExt = StreamUrlResolver.cleanExtension(
                movieData?.containerExtension?.takeIf { it.isNotBlank() } ?: movie.containerExtension,
                "mp4"
            )
            val resolvedCandidates = StreamUrlResolver.buildXtreamVodCandidates(
                baseUrl = base,
                username = playlist.username,
                password = playlist.password,
                streamId = movie.id,
                containerExtension = resolvedExt,
                directSource = movieData?.directSource.orEmpty()
            )
            movie.copy(
                plot = info?.plot?.ifBlank { movie.plot } ?: movie.plot,
                genre = info?.genre?.ifBlank { movie.genre } ?: movie.genre,
                duration = info?.duration?.ifBlank { movie.duration } ?: movie.duration,
                year = info?.releaseDate?.ifBlank { movie.year } ?: movie.year,
                rating = info?.rating?.ifBlank { movie.rating } ?: movie.rating,
                backdropUrl = info?.movieImage?.ifBlank { movie.backdropUrl } ?: movie.backdropUrl,
                containerExtension = resolvedExt,
                streamUrl = resolvedCandidates.firstOrNull() ?: movie.streamUrl,
                candidateUrls = resolvedCandidates
            )
        } catch (e: Exception) {
            Log.w(TAG, "VOD info unavailable for ${movie.id}: ${e.javaClass.simpleName}")
            movie
        }
    }

    suspend fun fetchSeriesDetails(playlist: SavedPlaylist, series: TvSeries): Result<SeriesDetails> = withContext(Dispatchers.IO) {
        val base = normalizeServerBaseUrl(playlist.serverUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid server URL"))
        try {
            val response = api.getSeriesInfo(
                apiUrl = "$base/player_api.php",
                username = playlist.username,
                password = playlist.password,
                seriesId = series.id
            )
            val body = response.body()
                ?: return@withContext Result.failure(IllegalStateException("Empty series details response"))
            val info = body.info
            val enrichedSeries = series.copy(
                plot = info?.plot?.ifBlank { series.plot } ?: series.plot,
                genre = info?.genre?.ifBlank { series.genre } ?: series.genre,
                rating = info?.rating?.ifBlank { series.rating } ?: series.rating,
                backdropUrl = info?.cover?.ifBlank { series.backdropUrl } ?: series.backdropUrl
            )
            val seasonsMap = sortedMapOf<Int, List<SeriesEpisode>>()
            body.episodes.orEmpty().forEach { (seasonKey, dtoList) ->
                val seasonNum = seasonKey.toIntOrNull() ?: 1
                val episodes = dtoList.map { ep ->
                    val ext = StreamUrlResolver.cleanExtension(ep.containerExtension, "mp4")
                    val epNum = ep.episodeNum.toIntOrNull() ?: 1
                    val candidates = StreamUrlResolver.buildXtreamSeriesCandidates(
                        baseUrl = base,
                        username = playlist.username,
                        password = playlist.password,
                        episodeId = ep.id,
                        containerExtension = ext,
                        directSource = ep.directSource
                    )
                    SeriesEpisode(
                        id = ep.id,
                        seriesId = series.id,
                        seriesTitle = enrichedSeries.title,
                        seasonNumber = ep.season.toIntOrNull() ?: seasonNum,
                        episodeNumber = epNum,
                        title = ep.title.ifBlank { "Episode $epNum" },
                        plot = ep.info?.plot.orEmpty(),
                        duration = ep.info?.duration.orEmpty(),
                        thumbnailUrl = ep.info?.movieImage?.ifBlank { enrichedSeries.posterUrl } ?: enrichedSeries.posterUrl,
                        streamUrl = candidates.firstOrNull().orEmpty(),
                        playlistId = playlist.id,
                        candidateUrls = candidates
                    )
                }.sortedBy { it.episodeNumber }
                if (episodes.isNotEmpty()) {
                    seasonsMap[seasonNum] = episodes
                }
            }
            Result.success(SeriesDetails(enrichedSeries, seasonsMap))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch series details", e)
            Result.failure(e)
        }
    }

    private suspend fun <T> safeList(block: suspend () -> List<T>?): List<T> {
        return try {
            block() ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Optional Xtream list endpoint failed", e)
            emptyList()
        }
    }

    private fun decodeBase64OrPlain(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val decoded = Base64.decode(value, Base64.DEFAULT)
            val text = String(decoded, Charsets.UTF_8)
            if (text.all { !it.isISOControl() || it == '\n' || it == '\r' || it == '\t' }) {
                text
            } else {
                value
            }
        } catch (_: Exception) {
            value
        }
    }

    private fun formatEpgTime(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.substringAfter(" ", raw).take(5)
    }

    private fun formatExpDate(expEpochStr: String): String {
        val epochSec = expEpochStr.toLongOrNull() ?: return ""
        if (epochSec <= 0L) return "Unlimited"
        return try {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochSec * 1000L))
        } catch (_: Exception) {
            ""
        }
    }

    private companion object {
        const val TAG = "XtreamClient"
    }
}
