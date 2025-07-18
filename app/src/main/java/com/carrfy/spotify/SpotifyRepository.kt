package com.carrfy.spotify

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.carrfy.spotify.models.Album
import com.carrfy.spotify.models.Artist
import com.carrfy.spotify.models.ArtistTopTracksResponse
import com.carrfy.spotify.models.CurrentlyPlayingResponse
import com.carrfy.spotify.models.Device
import com.carrfy.spotify.models.Image
import com.carrfy.spotify.models.Playlist
import com.carrfy.spotify.models.PlaylistOwner
import com.carrfy.spotify.models.PlaylistTrackItem
import com.carrfy.spotify.models.PlaylistTracksInfo
import com.carrfy.spotify.models.PlaylistTracksResponse
import com.carrfy.spotify.models.PlaylistsResponse
import com.carrfy.spotify.models.RecentlyPlayedItem
import com.carrfy.spotify.models.RecentlyPlayedResponse
import com.carrfy.spotify.models.SearchResponse
import com.carrfy.spotify.models.TopArtistsResponse
import com.carrfy.spotify.models.Track
import com.carrfy.spotify.models.TracksResponse
import com.carrfy.ui.RepeatMode
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

class SpotifyRepository(context: Context) {
    private val appContext = context.applicationContext
    private val firebaseApp = runCatching { FirebaseApp.initializeApp(appContext) }.getOrNull()
    private val firestore: FirebaseFirestore? = runCatching {
        firebaseApp?.let { FirebaseFirestore.getInstance(it) }
    }.getOrNull()
    private val playerStateDoc = firestore?.collection("state")?.document("player")
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val MAX_RECENTS = 50
        private const val SYNC_INTERVAL_MS = 60_000L
        private const val SYNC_TIMEOUT_MS = 1_500L
        private const val SAAVN_TIMEOUT_MS = 3_500
        private const val SAAVN_SEARCH_BASE = "https://saavnapi-two.vercel.app/result/"
        private const val ITUNES_SEARCH_BASE = "https://itunes.apple.com/search"
        private const val RADIO_TIMEOUT_MS = 4_000
        private const val RADIO_BROWSER_BASE = "https://de1.api.radio-browser.info/json/stations/search?countrycode=IN&hidebroken=true&limit=40&order=clickcount&reverse=true"
        private const val AUTOPLAY_LOW_WATERMARK = 8
        private const val AUTOPLAY_FILL_SIZE = 20

        private val lock = Any()

        private var initialized = false
        private var syncInProgress = false
        private var lastSyncMs = 0L

        private var allTracks: List<Track> = emptyList()
        private var allArtists: List<Artist> = emptyList()
        private var playlistsById: Map<String, Playlist> = emptyMap()
        private var playlistTracksById: Map<String, List<Track>> = emptyMap()

        private var currentTrack: Track? = null
        private var isPlaying = false
        private var shuffleEnabled = false
        private var repeatMode: RepeatMode = RepeatMode.OFF

        private val queueUris = mutableListOf<String>()
        private val recentTracks = mutableListOf<Track>()
        private val playbackHistory = mutableListOf<String>()
        private var currentPlaylistContext: List<String>? = null
        private var radioStationsCache: List<Track> = emptyList()
        private var radioStationsCacheMs = 0L
        private var autoplayEnabled = false
        private var lastAutoplaySeedLanguage: String? = null
        private var lastAutoplaySeedArtist: String? = null

        private fun builtInRadioStations(): List<Track> {
            fun station(id: String, name: String, streamUrl: String, imageUrl: String, stationName: String): Track {
                return Track(
                    id = id,
                    name = name,
                    uri = streamUrl,
                    artists = listOf(Artist(id = "artist_$id", name = stationName, images = null, genres = listOf("radio"))),
                    album = Album(
                        id = "album_$id",
                        name = "Live Radio",
                        images = listOf(Image(imageUrl, null, null))
                    ),
                    durationMs = null
                )
            }

            return listOf(
                station(
                    id = "radio_groovesalad",
                    name = "Groove Salad",
                    streamUrl = "https://ice1.somafm.com/groovesalad-128-mp3",
                    imageUrl = "https://picsum.photos/seed/groovesalad/600/600",
                    stationName = "SomaFM"
                ),
                station(
                    id = "radio_dronezone",
                    name = "Drone Zone",
                    streamUrl = "https://ice1.somafm.com/dronezone-128-mp3",
                    imageUrl = "https://picsum.photos/seed/dronezone/600/600",
                    stationName = "SomaFM"
                ),
                station(
                    id = "radio_lush",
                    name = "Lush",
                    streamUrl = "https://ice1.somafm.com/lush-128-mp3",
                    imageUrl = "https://picsum.photos/seed/lushradio/600/600",
                    stationName = "SomaFM"
                ),
                station(
                    id = "radio_rp_global",
                    name = "Radio Paradise",
                    streamUrl = "https://stream.radioparadise.com/mp3-192",
                    imageUrl = "https://picsum.photos/seed/radioparadise/600/600",
                    stationName = "Radio Paradise"
                ),
                station(
                    id = "radio_secretagent",
                    name = "Secret Agent",
                    streamUrl = "https://ice1.somafm.com/secretagent-128-mp3",
                    imageUrl = "https://picsum.photos/seed/secretagent/600/600",
                    stationName = "SomaFM"
                )
            )
        }

        private fun initializeDataIfNeeded() {
            if (initialized) return

            val artistA = Artist(
                id = "artist_aurora_lane",
                name = "Aurora Lane",
                images = listOf(Image("https://picsum.photos/seed/aurora/500/500", 500, 500)),
                genres = listOf("electro-pop", "indie")
            )
            val artistB = Artist(
                id = "artist_midnight_drive",
                name = "Midnight Drive",
                images = listOf(Image("https://picsum.photos/seed/midnight/500/500", 500, 500)),
                genres = listOf("synthwave", "electronic")
            )
            val artistC = Artist(
                id = "artist_neon_skies",
                name = "Neon Skies",
                images = listOf(Image("https://picsum.photos/seed/neon/500/500", 500, 500)),
                genres = listOf("alt-pop", "chill")
            )
            val artistD = Artist(
                id = "artist_river_echo",
                name = "River Echo",
                images = listOf(Image("https://picsum.photos/seed/river/500/500", 500, 500)),
                genres = listOf("acoustic", "folk")
            )

            allArtists = listOf(artistA, artistB, artistC, artistD)

            fun album(seed: String, title: String): Album {
                return Album(
                    id = "album_$seed",
                    name = title,
                    images = listOf(Image("https://picsum.photos/seed/$seed/700/700", 700, 700))
                )
            }

            allTracks = listOf(
                Track("track_001", "Electric Dawn", "spotify:track:track_001", listOf(artistA), album("dawn", "City Lights"), 205000),
                Track("track_002", "Night Runner", "spotify:track:track_002", listOf(artistB), album("runner", "After Hours"), 198000),
                Track("track_003", "Cloudline", "spotify:track:track_003", listOf(artistC), album("cloud", "Skyline Dreams"), 232000),
                Track("track_004", "Paper Boats", "spotify:track:track_004", listOf(artistD), album("boats", "River Songs"), 214000),
                Track("track_005", "Starlit Avenue", "spotify:track:track_005", listOf(artistA, artistB), album("avenue", "Midtown"), 226000),
                Track("track_006", "Backseat Cinema", "spotify:track:track_006", listOf(artistB), album("cinema", "After Hours"), 201000),
                Track("track_007", "Saturn Bloom", "spotify:track:track_007", listOf(artistC), album("saturn", "Skyline Dreams"), 218000),
                Track("track_008", "June in Mono", "spotify:track:track_008", listOf(artistD), album("mono", "River Songs"), 239000),
                Track("track_009", "Velvet Signals", "spotify:track:track_009", listOf(artistA), album("signals", "City Lights"), 208000),
                Track("track_010", "Freeway Hearts", "spotify:track:track_010", listOf(artistB, artistC), album("freeway", "Night Network"), 223000)
            ) + builtInRadioStations()

            val chillPlaylistTracks = listOf("track_003", "track_007", "track_004", "track_008").mapNotNull { findTrackById(it) }
            val drivePlaylistTracks = listOf("track_001", "track_002", "track_006", "track_010").mapNotNull { findTrackById(it) }
            val focusPlaylistTracks = listOf("track_009", "track_005", "track_003", "track_001").mapNotNull { findTrackById(it) }

            val chillId = "playlist_chill"
            val driveId = "playlist_drive"
            val focusId = "playlist_focus"

            val owner = PlaylistOwner(id = "you", displayName = "You")
            val chillPlaylist = Playlist(
                id = chillId,
                name = "Evening Chill",
                images = listOf(Image("https://picsum.photos/seed/chill/600/600", 600, 600)),
                description = "Soft electronic and mellow vocals",
                owner = owner,
                tracks = PlaylistTracksInfo(items = null, total = chillPlaylistTracks.size)
            )
            val drivePlaylist = Playlist(
                id = driveId,
                name = "Night Drive",
                images = listOf(Image("https://picsum.photos/seed/drive/600/600", 600, 600)),
                description = "Synth-forward tracks for late roads",
                owner = owner,
                tracks = PlaylistTracksInfo(items = null, total = drivePlaylistTracks.size)
            )
            val focusPlaylist = Playlist(
                id = focusId,
                name = "Deep Focus",
                images = listOf(Image("https://picsum.photos/seed/focus/600/600", 600, 600)),
                description = "Clean vocals and steady rhythm",
                owner = owner,
                tracks = PlaylistTracksInfo(items = null, total = focusPlaylistTracks.size)
            )

            playlistsById = listOf(chillPlaylist, drivePlaylist, focusPlaylist).associateBy { it.id }
            playlistTracksById = mapOf(
                chillId to chillPlaylistTracks,
                driveId to drivePlaylistTracks,
                focusId to focusPlaylistTracks
            )

            currentTrack = allTracks.firstOrNull()
            isPlaying = true
            initialized = true
        }

        private fun normalizedUri(value: String): String {
            return when {
                value.startsWith("spotify:track:") -> value
                value.startsWith("saavn:track:") -> value
                value.startsWith("http://") || value.startsWith("https://") -> value
                else -> "spotify:track:$value"
            }
        }

        private fun findTrackByUri(uri: String): Track? {
            val normalized = normalizedUri(uri)
            return allTracks.firstOrNull { it.uri == normalized }
        }

        private fun findTrackById(id: String): Track? {
            return allTracks.firstOrNull { it.id == id }
        }

        private fun registerTracksInCatalog(tracks: List<Track>) {
            if (tracks.isEmpty()) return

            val existingById = allTracks.associateBy { it.id }
            allTracks = (tracks + allTracks)
                .distinctBy { it.id ?: it.uri }
                .map { incoming ->
                    existingById[incoming.id]?.copy(
                        name = incoming.name ?: existingById[incoming.id]?.name,
                        uri = incoming.uri ?: existingById[incoming.id]?.uri,
                        artists = if (incoming.artists.isNullOrEmpty()) existingById[incoming.id]?.artists else incoming.artists,
                        album = incoming.album ?: existingById[incoming.id]?.album,
                        durationMs = incoming.durationMs ?: existingById[incoming.id]?.durationMs
                    ) ?: incoming
                }
        }

        private fun isExternalPlaybackUri(uri: String?): Boolean {
            return when {
                uri.isNullOrBlank() -> false
                uri.startsWith("http://") || uri.startsWith("https://") -> true
                uri.startsWith("saavn:track:") -> true
                else -> false
            }
        }

        private fun trackUsesExternalPlayback(track: Track?): Boolean {
            return isExternalPlaybackUri(track?.uri)
        }

        private fun addTrackToRecents(track: Track) {
            recentTracks.removeAll { it.id == track.id }
            recentTracks.add(0, track)
            if (recentTracks.size > MAX_RECENTS) {
                recentTracks.removeAt(recentTracks.lastIndex)
            }
        }

        private fun setCurrentTrack(track: Track, playNow: Boolean) {
            val previousUri = currentTrack?.uri
            if (previousUri != null) {
                playbackHistory.add(previousUri)
                if (playbackHistory.size > 100) {
                    playbackHistory.removeAt(0)
                }
            }
            currentTrack = track
            isPlaying = playNow
            addTrackToRecents(track)
        }

        private fun nextFromContextOrLibrary(): Track? {
            val currentUri = currentTrack?.uri
            val contextUris = currentPlaylistContext
            if (!contextUris.isNullOrEmpty() && currentUri != null) {
                val currentIndex = contextUris.indexOf(currentUri)
                if (currentIndex >= 0 && currentIndex + 1 < contextUris.size) {
                    return findTrackByUri(contextUris[currentIndex + 1])
                }
                if (repeatMode == RepeatMode.CONTEXT) {
                    return findTrackByUri(contextUris.first())
                }
            }

            if (trackUsesExternalPlayback(currentTrack)) {
                val externalCandidates = allTracks.filter { track -> isExternalPlaybackUri(track.uri) }
                if (externalCandidates.isNotEmpty()) {
                    if (shuffleEnabled) {
                        return externalCandidates.filter { it.id != currentTrack?.id }.randomOrNull() ?: externalCandidates.firstOrNull()
                    }

                    val currentIndex = externalCandidates.indexOfFirst { it.id == currentTrack?.id }
                    if (currentIndex < 0) return externalCandidates.firstOrNull()
                    if (currentIndex + 1 < externalCandidates.size) return externalCandidates[currentIndex + 1]
                    return if (repeatMode == RepeatMode.CONTEXT) externalCandidates.firstOrNull() else externalCandidates.lastOrNull()
                }
            }

            if (allTracks.isEmpty()) return null
            if (shuffleEnabled) {
                val candidates = allTracks.filter { it.id != currentTrack?.id }
                return candidates.randomOrNull() ?: allTracks.firstOrNull()
            }

            val currentIndex = allTracks.indexOfFirst { it.id == currentTrack?.id }
            if (currentIndex < 0) return allTracks.firstOrNull()
            if (currentIndex + 1 < allTracks.size) return allTracks[currentIndex + 1]
            return if (repeatMode == RepeatMode.CONTEXT) allTracks.firstOrNull() else allTracks.lastOrNull()
        }

        private fun JsonElement?.asStringOrNull(): String? {
            return if (this != null && isJsonPrimitive) asString else null
        }

        private fun firstImageUrl(item: JsonElement?): String? {
            val flatImage = item?.asJsonObject?.get("image").asStringOrNull()
            if (!flatImage.isNullOrBlank()) return flatImage
            val images = item?.asJsonObject?.get("image")?.asJsonArray ?: return null
            for (i in images.size() - 1 downTo 0) {
                val url = images[i]?.asJsonObject?.get("url").asStringOrNull()
                if (!url.isNullOrBlank()) return url
            }
            return null
        }

        private fun firstDownloadUrl(item: JsonElement?): String? {
            val mediaUrl = item?.asJsonObject?.get("media_url").asStringOrNull()
            if (!mediaUrl.isNullOrBlank()) return mediaUrl
            val preview = item?.asJsonObject?.get("media_preview_url").asStringOrNull()
            if (!preview.isNullOrBlank()) return preview
            val downloads = item?.asJsonObject?.get("downloadUrl")?.asJsonArray ?: return null
            for (i in downloads.size() - 1 downTo 0) {
                val url = downloads[i]?.asJsonObject?.get("url").asStringOrNull()
                if (!url.isNullOrBlank()) return url
            }
            return null
        }

        private fun parseSaavnTrack(item: JsonElement): Track? {
            val obj = item.asJsonObject
            val id = obj.get("id").asStringOrNull() ?: return null
            val title = obj.get("name").asStringOrNull() ?: obj.get("song").asStringOrNull() ?: return null

            val artistNames = mutableListOf<String>()
            val primaryArtistsArray = obj.get("artists")
                ?.asJsonObject
                ?.get("primary")
                ?.asJsonArray
            if (primaryArtistsArray != null) {
                primaryArtistsArray.forEach { artistEl ->
                    val name = artistEl?.asJsonObject?.get("name").asStringOrNull()
                    if (!name.isNullOrBlank()) artistNames.add(name)
                }
            }

            if (artistNames.isEmpty()) {
                val artistCsv = obj.get("primaryArtists").asStringOrNull()
                    ?: obj.get("primary_artists").asStringOrNull()
                val fallbackNames: List<String> = artistCsv
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
                artistNames.addAll(fallbackNames)
            }

            val artists = artistNames.mapIndexed { idx, name ->
                Artist(id = "saavn_artist_${id}_$idx", name = name, images = null, genres = null)
            }

            val albumObj = obj.get("album")?.takeIf { it.isJsonObject }?.asJsonObject
            val albumName = albumObj?.get("name").asStringOrNull() ?: obj.get("album").asStringOrNull() ?: "Unknown Album"
            val albumId = albumObj?.get("id").asStringOrNull() ?: obj.get("albumid").asStringOrNull() ?: "saavn_album_$id"
            val imageUrl = firstImageUrl(item)
            val album = Album(
                id = albumId,
                name = albumName,
                images = listOfNotNull(imageUrl?.let { Image(it, null, null) })
            )

            val streamUrl = firstDownloadUrl(item)
            val language = obj.get("language").asStringOrNull()
            val music = obj.get("music").asStringOrNull()
            val singers = obj.get("singers").asStringOrNull()
            val playCount = obj.get("play_count").asStringOrNull()?.toLongOrNull()
            val year = obj.get("year").asStringOrNull()?.toIntOrNull()
            val uri = when {
                !streamUrl.isNullOrBlank() -> streamUrl
                else -> "saavn:track:$id"
            }
            val duration = obj.get("duration").asStringOrNull()?.toIntOrNull()?.times(1000)
                ?: obj.get("duration").asStringOrNull()?.toDoubleOrNull()?.toInt()?.times(1000)

            return Track(
                id = id,
                name = title,
                uri = uri,
                artists = artists,
                album = album,
                durationMs = duration,
                language = language,
                music = music,
                singers = singers,
                playCount = playCount,
                year = year,
                source = "saavn"
            )
        }

        private fun parseRadioBrowserStation(item: JsonElement): Track? {
            val obj = item.asJsonObject
            val name = obj.get("name").asStringOrNull()?.trim().orEmpty()
            val streamUrl = obj.get("url_resolved").asStringOrNull()?.trim().orEmpty()
            if (name.isBlank() || streamUrl.isBlank()) return null
            if (!(streamUrl.startsWith("http://") || streamUrl.startsWith("https://"))) return null

            val stationUuid = obj.get("stationuuid").asStringOrNull()
            val favicon = obj.get("favicon").asStringOrNull()
            val tags = obj.get("tags").asStringOrNull().orEmpty().lowercase(Locale.getDefault())
            val lowerName = name.lowercase(Locale.getDefault())

            val preferred = listOf("mirchi", "big fm", "red fm", "radio city", "air", "vividh", "fm gold")
            val isPreferred = preferred.any { key -> lowerName.contains(key) || tags.contains(key) }

            val baseId = stationUuid?.takeIf { it.isNotBlank() }
                ?: "radio_in_" + lowerName.replace("[^a-z0-9]+".toRegex(), "_").trim('_')
            val id = if (isPreferred) "aa_$baseId" else baseId

            return Track(
                id = id,
                name = name,
                uri = streamUrl,
                artists = listOf(Artist(id = "artist_$baseId", name = "Indian Radio", images = null, genres = listOf("radio", "india"))),
                album = Album(
                    id = "album_$baseId",
                    name = "Live Indian Radio",
                    images = listOfNotNull(favicon?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { Image(it, null, null) })
                ),
                durationMs = null
            )
        }
    }

    suspend fun getRadioStations(): List<Track> {
        maybeSyncFromFirebase()
        val now = System.currentTimeMillis()
        val cachedStations = synchronized(lock) {
            val cacheFresh = now - radioStationsCacheMs < 10 * 60 * 1000
            if (cacheFresh) radioStationsCache else emptyList()
        }

        val dynamicStations = if (cachedStations.isEmpty()) fetchIndianRadioStations() else cachedStations

        synchronized(lock) {
            if (dynamicStations.isNotEmpty()) {
                radioStationsCache = dynamicStations
                radioStationsCacheMs = now
            }

            val stations = (builtInRadioStations() + radioStationsCache)
                .distinctBy { it.id ?: it.uri }
                .sortedBy { it.id ?: "z" }
            val stationIds = stations.mapNotNull { it.id }.toSet()
            val nonStations = allTracks.filter { it.id !in stationIds }
            allTracks = nonStations + stations
            return stations
        }
    }
    init {
        synchronized(lock) {
            initializeDataIfNeeded()
        }
    }

    private fun normalizedRecommendationText(value: String?): String {
        return value.orEmpty()
            .lowercase(Locale.getDefault())
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\b(feat|ft|remix|version|edit|mix|live|karaoke|slowed|reverb|instrumental|original motion picture soundtrack|ost)\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun artistNames(track: Track): Set<String> {
        return track.artists.orEmpty()
            .mapNotNull { it.name?.trim()?.lowercase(Locale.getDefault()) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun artistGenres(track: Track): Set<String> {
        return track.artists.orEmpty()
            .flatMap { it.genres.orEmpty() }
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun titleTokens(track: Track): Set<String> {
        return normalizedRecommendationText(track.name)
            .split(' ')
            .map { it.trim() }
            .filter { it.length >= 3 }
            .toSet()
    }

    private fun sourceTag(track: Track): String {
        val uri = track.uri.orEmpty()
        return when {
            uri.startsWith("http://") || uri.startsWith("https://") -> "external"
            uri.startsWith("saavn:track:") -> "saavn"
            else -> "local"
        }
    }

    private fun recommendationQueriesFor(track: Track): List<String> {
        // GENRE-FIRST recommendation queries with RANDOMIZED priorities (Spotify-style)
        val language = track.language?.trim()?.takeIf { it.isNotBlank() }
        val genres = artistGenres(track).filter { it.isNotBlank() }
        val music = track.music?.trim()?.takeIf { it.isNotBlank() }
        val singers = track.singers?.trim()?.takeIf { it.isNotBlank() }

        val queries = mutableListOf<String>()

        // ALWAYS: Genre-based queries (constant foundation)
        genres.forEach { genre ->
            if (language != null) queries += "$language $genre"
            if (music != null) queries += "$genre $music"
            if (singers != null) queries += "$genre $singers"
        }

        // BUILD randomized priority combinations
        val priorityCombinations = mutableListOf<String>()
        
        // Priority options (will be randomly ordered)
        if (language != null && music != null) priorityCombinations += "$language $music"
        if (music != null && singers != null) priorityCombinations += "$music $singers"
        if (language != null && singers != null) priorityCombinations += "$language $singers"
        
        // Shuffle priority combinations for variety
        priorityCombinations.shuffle()
        queries.addAll(priorityCombinations)

        // Fallback: Pure genre/music/language only if queries sparse
        if (queries.isEmpty()) {
            genres.take(2).forEach { queries += it }
            if (music != null) queries += music
            if (language != null) queries += language
        }

        return queries
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
    }

    private fun autoplaySeedTracks(currentTrack: Track?, recentTracks: List<Track>): List<Track> {
        return listOfNotNull(currentTrack) + recentTracks
            .distinctBy { it.id ?: it.uri }
            .take(3)
    }

    private fun itunesTermQueries(track: Track): List<String> {
        // GENRE-FIRST iTunes queries with RANDOMIZED priorities (Spotify-style)
        val language = track.language?.trim()?.takeIf { it.isNotBlank() }
        val genres = artistGenres(track).filter { it.isNotBlank() }
        val music = track.music?.trim()?.takeIf { it.isNotBlank() }
        val singers = track.singers?.trim()?.takeIf { it.isNotBlank() }

        val queries = mutableListOf<String>()

        // ALWAYS: Genre-based queries (constant foundation)
        genres.forEach { genre ->
            if (language != null) queries += "$language $genre"
            if (music != null) queries += "$genre $music"
            if (singers != null) queries += "$genre $singers"
        }

        // BUILD randomized priority combinations
        val priorityCombinations = mutableListOf<String>()
        
        // Priority options (will be randomly ordered)
        if (language != null && music != null) priorityCombinations += "$language $music"
        if (music != null && singers != null) priorityCombinations += "$music $singers"
        if (language != null && singers != null) priorityCombinations += "$language $singers"
        
        // Shuffle priority combinations for variety
        priorityCombinations.shuffle()
        queries.addAll(priorityCombinations)

        // Fallback: Pure genre/music/language only if queries sparse
        if (queries.isEmpty()) {
            genres.take(2).forEach { queries += it }
            if (music != null) queries += music
            if (language != null) queries += language
        }

        return queries
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
    }

    private fun recommendationScore(seed: Track, candidate: Track): Double {
        if (seed.id != null && seed.id == candidate.id) return Double.NEGATIVE_INFINITY

        val seedGenres = artistGenres(seed)
        val candidateGenres = artistGenres(candidate)
        val seedLanguage = normalizedRecommendationText(seed.language)
        val candidateLanguage = normalizedRecommendationText(candidate.language)
        val seedMusic = normalizedRecommendationText(seed.music)
        val candidateMusic = normalizedRecommendationText(candidate.music)
        val seedSingers = normalizedRecommendationText(seed.singers)
        val candidateSingers = normalizedRecommendationText(candidate.singers)
        val seedTitleTokens = titleTokens(seed)
        val candidateTitleTokens = titleTokens(candidate)
        val seedArtists = artistNames(seed)
        val candidateArtists = artistNames(candidate)

        var score = 0.0

        // GENRE-FIRST SCORING (Spotify-style)
        // Priority 1: Genre matching (highest weight)
        val sharedGenres = seedGenres.intersect(candidateGenres)
        score += sharedGenres.size * 45.0  // Increased from 16 to 45 (most important signal)

        // Priority 2: Language matching (critical for Spotify-like behavior)
        if (seedLanguage.isNotBlank() && seedLanguage == candidateLanguage) {
            score += 55.0  // Increased from 18 to 55 (second most important)
        }

        // Priority 3: Music/Composer matching
        if (seedMusic.isNotBlank() && seedMusic == candidateMusic) score += 38.0
        if (seedSingers.isNotBlank() && seedSingers == candidateSingers) score += 32.0

        // Priority 4: Artist matching (secondary to genre)
        val sharedArtists = seedArtists.intersect(candidateArtists)
        score += sharedArtists.size * 20.0  // Decreased from 38 to 20 (not primary signal)

        // Priority 5: Year proximity
        val yearA = seed.year
        val yearB = candidate.year
        if (yearA != null && yearB != null) {
            score += maxOf(0.0, 12.0 - abs(yearA - yearB).toDouble() * 2.0)
        }

        // Priority 6: Popularity
        val candidatePlayCount = candidate.playCount ?: 0L
        if (candidatePlayCount > 0) {
            score += min(14.0, ln(candidatePlayCount.toDouble() + 1.0) / ln(10.0) * 2.8)
        }

        // ANTI-TITLE PENALTIES (prevent title-based matching)
        val sharedTitleTokens = seedTitleTokens.intersect(candidateTitleTokens)
        if (seedTitleTokens.isNotEmpty() && candidateTitleTokens.isNotEmpty()) {
            if (seedTitleTokens == candidateTitleTokens) {
                score -= 150.0  // Increased penalty for exact title match
            } else if (sharedTitleTokens.size >= 2) {
                score -= 60.0 + sharedTitleTokens.size * 10.0  // Increased penalty
            } else if (sharedTitleTokens.size == 1) {
                score -= 20.0  // Increased penalty
            }
        }

        // Source consistency (minor factor)
        if (sourceTag(seed) == sourceTag(candidate)) score += 3.0
        if (sourceTag(seed) == "external" && sourceTag(candidate) == "external") score += 6.0

        return score
    }

    private suspend fun fetchSaavnRecommendations(seed: Track, limit: Int): List<Track> {
        val queries = recommendationQueriesFor(seed)
        if (queries.isEmpty()) return emptyList()

        val fetched = mutableListOf<Track>()
        for (query in queries) {
            fetched.addAll(fetchSaavnSearchTracks(query, limit))
            if (fetched.size >= limit) break
        }

        return fetched
            .distinctBy { it.uri ?: it.id }
            .filter { candidate -> candidate.id != seed.id }
            .take(limit)
            .also { registerTracksInCatalog(it) }
    }

    private fun parseItunesTrack(item: JsonElement, languageHint: String? = null): Track? {
        val obj = item.asJsonObject
        val trackId = obj.get("trackId")?.asStringOrNull() ?: obj.get("collectionId")?.asStringOrNull() ?: return null
        val trackName = obj.get("trackName")?.asStringOrNull() ?: return null
        val artistName = obj.get("artistName")?.asStringOrNull() ?: return null
        val previewUrl = obj.get("previewUrl")?.asStringOrNull()
        val artworkUrl = obj.get("artworkUrl100")?.asStringOrNull() ?: obj.get("artworkUrl60")?.asStringOrNull()
        val genre = obj.get("primaryGenreName")?.asStringOrNull()
        val releaseDate = obj.get("releaseDate")?.asStringOrNull()
        val year = releaseDate?.take(4)?.toIntOrNull()

        return Track(
            id = "itunes_$trackId",
            name = trackName,
            uri = previewUrl ?: "itunes:track:$trackId",
            artists = listOf(
                Artist(
                    id = obj.get("artistId")?.asStringOrNull() ?: "itunes_artist_$trackId",
                    name = artistName,
                    images = null,
                    genres = listOfNotNull(genre?.takeIf { it.isNotBlank() })
                )
            ),
            album = Album(
                id = obj.get("collectionId")?.asStringOrNull() ?: "itunes_album_$trackId",
                name = obj.get("collectionName")?.asStringOrNull() ?: trackName,
                images = listOfNotNull(artworkUrl?.let { Image(it, null, null) })
            ),
            durationMs = obj.get("trackTimeMillis")?.asStringOrNull()?.toIntOrNull(),
            language = languageHint,
            music = artistName,
            singers = artistName,
            playCount = null,
            year = year,
            source = "itunes"
        )
    }

    private suspend fun fetchItunesSearchTracks(query: String, languageHint: String?, limit: Int = 25): List<Track> {
        return withContext(Dispatchers.IO) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val endpoint = "$ITUNES_SEARCH_BASE?term=$encodedQuery&media=music&entity=song&limit=$limit&country=US&explicit=No"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = SAAVN_TIMEOUT_MS
                readTimeout = SAAVN_TIMEOUT_MS
                doInput = true
                setRequestProperty("User-Agent", "Carrfy/1.0")
            }

            try {
                if (connection.responseCode !in 200..299) return@withContext emptyList()
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JsonParser.parseString(body)
                val results = root.asJsonObject.get("results")?.asJsonArray ?: return@withContext emptyList()

                return@withContext results
                    .mapNotNull { parseItunesTrack(it, languageHint = languageHint) }
                    .take(limit)
            } catch (_: Exception) {
                return@withContext emptyList()
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun fetchItunesRecommendations(seed: Track, limit: Int): List<Track> {
        val queries = itunesTermQueries(seed)
        if (queries.isEmpty()) return emptyList()

        val fetched = mutableListOf<Track>()
        for (query in queries) {
            fetched.addAll(fetchItunesSearchTracks(query, seed.language, limit))
            if (fetched.size >= limit) break
        }

        return fetched
            .distinctBy { it.uri ?: it.id }
            .filter { candidate -> candidate.id != seed.id }
            .take(limit)
            .also { registerTracksInCatalog(it) }
    }

    private suspend fun fetchSpotifySearchTracks(query: String, limit: Int = 20): List<Track> {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = com.carrfy.auth.SpotifyAuthManager.getAccessToken() ?: return@withContext emptyList()
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://api.spotify.com/v1/search?q=$encodedQuery&type=track&limit=$limit")

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                if (connection.responseCode !in 200..299) return@withContext emptyList()

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JsonParser.parseString(body).asJsonObject
                val tracks = root.get("tracks")?.asJsonObject?.get("items")?.asJsonArray ?: return@withContext emptyList()

                return@withContext tracks.mapNotNull { parseSpotifyTrack(it) }
            } catch (e: Exception) {
                android.util.Log.d("SpotifySearch", "Error searching Spotify: ${e.message}")
                emptyList()
            }
        }
    }

    private suspend fun fetchSpotifyRecommendations(seed: Track, limit: Int = 20): List<Track> {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = com.carrfy.auth.SpotifyAuthManager.getAccessToken() ?: return@withContext emptyList()
                
                // Use track ID as seed if available
                val seedTrackId = if (seed.id?.startsWith("spotify:") == true) {
                    seed.id.removePrefix("spotify:")
                } else if (seed.source == "spotify" && seed.id != null) {
                    seed.id
                } else {
                    return@withContext emptyList()
                }

                val url = URL("https://api.spotify.com/v1/recommendations?seed_tracks=$seedTrackId&limit=$limit")

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                if (connection.responseCode !in 200..299) return@withContext emptyList()

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JsonParser.parseString(body).asJsonObject
                val tracks = root.get("tracks")?.asJsonArray ?: return@withContext emptyList()

                return@withContext tracks.mapNotNull { parseSpotifyTrack(it) }
                    .filter { it.id != seed.id }
                    .also { registerTracksInCatalog(it) }
            } catch (e: Exception) {
                android.util.Log.d("SpotifyRecommendations", "Error fetching recommendations: ${e.message}")
                emptyList()
            }
        }
    }

    private fun parseSpotifyTrack(item: JsonElement): Track? {
        val obj = item.asJsonObject
        val id = obj.get("id")?.asString ?: return null
        val name = obj.get("name")?.asString ?: return null
        val spotifyUri = obj.get("uri")?.asString ?: "spotify:track:$id"
        
        // Use preview_url for playback if available, otherwise use Spotify URI
        val previewUrl = obj.get("preview_url")?.asString
        val uri = if (!previewUrl.isNullOrBlank()) previewUrl else spotifyUri

        val artists = obj.get("artists")?.asJsonArray?.mapNotNull { artist ->
            val artistObj = artist.asJsonObject
            Artist(
                id = artistObj.get("id")?.asString ?: "",
                name = artistObj.get("name")?.asString ?: "",
                images = null,
                genres = null
            )
        } ?: emptyList()

        val album = obj.get("album")?.asJsonObject?.let { albumObj ->
            Album(
                id = albumObj.get("id")?.asString ?: "",
                name = albumObj.get("name")?.asString ?: "",
                images = albumObj.get("images")?.asJsonArray?.mapNotNull { img ->
                    val imgObj = img.asJsonObject
                    Image(
                        url = imgObj.get("url")?.asString ?: "",
                        height = imgObj.get("height")?.asInt,
                        width = imgObj.get("width")?.asInt
                    )
                } ?: emptyList()
            )
        } ?: Album(id = "", name = "", images = emptyList())

        return Track(
            id = id,
            name = name,
            uri = uri,
            artists = artists,
            album = album,
            durationMs = obj.get("duration_ms")?.asInt,
            source = "spotify"
        )
    }

    private fun recentPlaybackUris(): Set<String> {
        return synchronized(lock) {
            buildSet {
                currentTrack?.uri?.let { add(it) }
                queueUris.forEach { add(it) }
                playbackHistory.takeLast(12).forEach { add(it) }
                recentTracks.take(12).mapNotNull { it.uri }.forEach { add(it) }
            }
        }
    }

    private suspend fun buildRecommendationPool(seedTracks: List<Track>, limit: Int): List<Track> {
        if (seedTracks.isEmpty()) return emptyList()

        val referenceSeeds = seedTracks.distinctBy { it.id ?: it.uri }.take(3)
        val referenceIds = referenceSeeds.mapNotNull { it.id }.toSet()
        val catalogCandidates = synchronized(lock) {
            allTracks.filter { candidate -> candidate.id !in referenceIds }
        }

        // Try Spotify recommendations first, then fall back to Saavn/iTunes
        val spotifySeeds = referenceSeeds.filter { it.source == "spotify" }
        android.util.Log.d("SpotifyRepository", "Building recommendations pool with ${spotifySeeds.size} Spotify seed tracks")
        
        val spotifyRecommendations = spotifySeeds.flatMap { seed ->
            android.util.Log.d("SpotifyRepository", "Fetching Spotify recommendations for seed: ${seed.name}")
            fetchSpotifyRecommendations(seed, limit * 2)
        }
        
        val remoteCandidates = if (spotifyRecommendations.isNotEmpty()) {
            android.util.Log.d("SpotifyRepository", "Using ${spotifyRecommendations.size} Spotify recommendations")
            spotifyRecommendations
        } else if (referenceSeeds.any { sourceTag(it) == "external" }) {
            android.util.Log.d("SpotifyRepository", "Spotify recommendations empty, falling back to Saavn/iTunes")
            referenceSeeds.flatMap { seed -> fetchSaavnRecommendations(seed, limit) + fetchItunesRecommendations(seed, limit) }
        } else {
            emptyList()
        }

        val excludedUris = recentPlaybackUris()
        val spotifyCandidates = remoteCandidates.filter { it.source == "spotify" }
        val externalCandidates = remoteCandidates.filter { sourceTag(it) == "external" || sourceTag(it) == "saavn" }
        val fallbackCandidates = if ((spotifyCandidates + externalCandidates).isNotEmpty()) emptyList() else catalogCandidates
        val mergedCandidates = (spotifyCandidates + externalCandidates + fallbackCandidates)
            .distinctBy { it.uri ?: it.id }
            .filter { candidate -> candidate.uri != null }
            .filterNot { candidate -> candidate.uri in excludedUris }

        return mergedCandidates
            .map { candidate ->
                val weightedScore = referenceSeeds.withIndex().sumOf { (index, seed) ->
                    val seedWeight = when (index) {
                        0 -> 1.0
                        1 -> 0.7
                        else -> 0.45
                    }
                    recommendationScore(seed, candidate) * seedWeight
                }
                candidate to weightedScore
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.uri ?: it.id }
            .take(limit)
    }

    private suspend fun refillAutoplayQueueIfNeeded(seedTracks: List<Track>, targetSize: Int = AUTOPLAY_FILL_SIZE) {
        if (seedTracks.isEmpty()) return

        val primarySeed = seedTracks.firstOrNull() ?: return
        val seedLanguage = primarySeed.language?.trim().takeIf { it?.isNotBlank() == true } ?: "unknown"
        val seedArtist = (primarySeed.artists?.firstOrNull()?.name ?: "unknown").trim()

        // Check if seed context changed significantly (language or primary artist)
        val seedContextChanged = synchronized(lock) {
            (lastAutoplaySeedLanguage != null && lastAutoplaySeedLanguage != seedLanguage) ||
                    (lastAutoplaySeedArtist != null && lastAutoplaySeedArtist != seedArtist)
        }

        // Clear queue when seed context changes to avoid stale recommendations
        if (seedContextChanged) {
            synchronized(lock) {
                queueUris.clear()
                lastAutoplaySeedLanguage = seedLanguage
                lastAutoplaySeedArtist = seedArtist
            }
        }

        val shouldFill = synchronized(lock) {
            autoplayEnabled && queueUris.size < AUTOPLAY_LOW_WATERMARK
        }
        if (!shouldFill) return

        val existingUris = recentPlaybackUris()
        val recommendations = buildRecommendationPool(seedTracks, targetSize * 2)
        val toAdd = recommendations
            .mapNotNull { it.uri }
            .filter { uri -> uri !in existingUris }
            .take(targetSize)

        if (toAdd.isNotEmpty()) {
            synchronized(lock) {
                queueUris.addAll(toAdd)
                lastAutoplaySeedLanguage = seedLanguage
                lastAutoplaySeedArtist = seedArtist
            }
            persistPlayerStateAsync()
        }
    }

    private suspend fun maybeSyncFromFirebase(force: Boolean = false) {
        if (firestore == null) return

        val shouldSync = synchronized(lock) {
            val now = System.currentTimeMillis()
            val due = force || now - lastSyncMs >= SYNC_INTERVAL_MS
            if (!due || syncInProgress) {
                false
            } else {
                syncInProgress = true
                true
            }
        }
        if (!shouldSync) return

        try {
            withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                syncFromFirestore()
            }
        } catch (_: Exception) {
            // Fall back to local data when Firebase is not configured or unreachable.
        } finally {
            synchronized(lock) {
                lastSyncMs = System.currentTimeMillis()
                syncInProgress = false
            }
        }
    }

    private suspend fun fetchSaavnSearchTracks(query: String, limit: Int = 25): List<Track> {
        return withContext(Dispatchers.IO) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val endpoint = "$SAAVN_SEARCH_BASE?query=$encodedQuery"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = SAAVN_TIMEOUT_MS
                readTimeout = SAAVN_TIMEOUT_MS
                doInput = true
            }

            try {
                if (connection.responseCode !in 200..299) return@withContext emptyList()
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val rootElement = JsonParser.parseString(body)
                val results = when {
                    rootElement.isJsonArray -> rootElement.asJsonArray
                    rootElement.isJsonObject -> rootElement
                        .asJsonObject
                        .get("data")
                        ?.asJsonObject
                        ?.get("results")
                        ?.asJsonArray
                    else -> null
                } ?: return@withContext emptyList()

                return@withContext results
                    .mapNotNull { parseSaavnTrack(it) }
                    .take(limit)
            } catch (_: Exception) {
                return@withContext emptyList()
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun fetchIndianRadioStations(): List<Track> {
        return withContext(Dispatchers.IO) {
            val connection = (URL(RADIO_BROWSER_BASE).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = RADIO_TIMEOUT_MS
                readTimeout = RADIO_TIMEOUT_MS
                doInput = true
                setRequestProperty("User-Agent", "Carrfy/1.0")
            }

            try {
                if (connection.responseCode !in 200..299) return@withContext emptyList()
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JsonParser.parseString(body)
                if (!root.isJsonArray) return@withContext emptyList()

                return@withContext root.asJsonArray
                    .mapNotNull { parseRadioBrowserStation(it) }
                    .distinctBy { it.uri }
                    .take(20)
            } catch (_: Exception) {
                return@withContext emptyList()
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun syncFromFirestore() {
        val db = firestore ?: return

        val artistDocs = db.collection("artists").get().await().documents
        val parsedArtists = artistDocs.map { doc ->
            Artist(
                id = doc.getString("id") ?: doc.id,
                name = doc.getString("name"),
                images = listOfNotNull(doc.getString("imageUrl")?.let { Image(it, null, null) }),
                genres = (doc.get("genres") as? List<*>)?.mapNotNull { it as? String }
            )
        }

        val artistById = parsedArtists.associateBy { it.id }
        val trackDocs = db.collection("tracks").get().await().documents
        val parsedTracks = trackDocs.map { doc ->
            val artistIds = (doc.get("artistIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val fallbackArtistNames = (doc.get("artistNames") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val artists = if (artistIds.isNotEmpty()) {
                artistIds.mapNotNull { artistById[it] }
            } else {
                fallbackArtistNames.map { name -> Artist(id = null, name = name, images = null, genres = null) }
            }

            val album = Album(
                id = doc.getString("albumId"),
                name = doc.getString("albumName"),
                images = listOfNotNull(doc.getString("albumImageUrl")?.let { Image(it, null, null) })
            )

            val id = doc.getString("id") ?: doc.id
            val uri = doc.getString("uri") ?: normalizedUri(id)
            Track(
                id = id,
                name = doc.getString("name"),
                uri = uri,
                artists = artists,
                album = album,
                durationMs = (doc.getLong("durationMs") ?: 0L).toInt().takeIf { it > 0 }
            )
        }
        val trackById = parsedTracks.associateBy { it.id }

        val playlistDocs = db.collection("playlists").get().await().documents
        val parsedPlaylists = mutableListOf<Playlist>()
        val parsedPlaylistTracks = mutableMapOf<String, List<Track>>()
        for (doc in playlistDocs) {
            val playlistId = doc.getString("id") ?: doc.id
            val trackIds = (doc.get("trackIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val tracks = trackIds.mapNotNull { trackById[it] }
            parsedPlaylists.add(
                Playlist(
                    id = playlistId,
                    name = doc.getString("name"),
                    images = listOfNotNull(doc.getString("imageUrl")?.let { Image(it, null, null) }),
                    description = doc.getString("description"),
                    owner = PlaylistOwner(id = "you", displayName = doc.getString("ownerName") ?: "You"),
                    tracks = PlaylistTracksInfo(items = null, total = tracks.size)
                )
            )
            parsedPlaylistTracks[playlistId] = tracks
        }

        synchronized(lock) {
            if (parsedArtists.isNotEmpty()) allArtists = parsedArtists
            if (parsedTracks.isNotEmpty()) allTracks = parsedTracks
            if (parsedPlaylists.isNotEmpty()) {
                playlistsById = parsedPlaylists.associateBy { it.id }
                playlistTracksById = parsedPlaylistTracks
            }
            if (currentTrack == null) currentTrack = allTracks.firstOrNull()
        }

        syncPlayerStateFromFirestore(trackById)
    }

    private suspend fun syncPlayerStateFromFirestore(trackById: Map<String?, Track>) {
        val stateDoc = playerStateDoc ?: return
        val snapshot = stateDoc.get().await()
        if (!snapshot.exists()) return

        val persistedQueue = (snapshot.get("queueUris") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val persistedRecentIds = (snapshot.get("recentTrackIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val persistedCurrentUri = snapshot.getString("currentTrackUri")
        val persistedIsPlaying = snapshot.getBoolean("isPlaying")
        val persistedShuffle = snapshot.getBoolean("shuffleEnabled")
        val persistedRepeat = snapshot.getString("repeatMode")

        synchronized(lock) {
            queueUris.clear()
            queueUris.addAll(persistedQueue)

            recentTracks.clear()
            persistedRecentIds.forEach { id ->
                trackById[id]?.let { recentTracks.add(it) }
            }

            if (persistedCurrentUri != null) {
                findTrackByUri(persistedCurrentUri)?.let { currentTrack = it }
            }
            if (persistedIsPlaying != null) {
                isPlaying = persistedIsPlaying
            }
            if (persistedShuffle != null) {
                shuffleEnabled = persistedShuffle
            }
            if (!persistedRepeat.isNullOrBlank()) {
                repeatMode = try {
                    RepeatMode.valueOf(persistedRepeat)
                } catch (_: IllegalArgumentException) {
                    repeatMode
                }
            }
        }
    }

    private suspend fun persistPlayerStateToFirestore() {
        val stateDoc = playerStateDoc ?: return

        val state = synchronized(lock) {
            mapOf(
                "queueUris" to queueUris.toList(),
                "recentTrackIds" to recentTracks.mapNotNull { it.id },
                "currentTrackUri" to currentTrack?.uri,
                "isPlaying" to isPlaying,
                "shuffleEnabled" to shuffleEnabled,
                "repeatMode" to repeatMode.name,
                "updatedAtMs" to System.currentTimeMillis()
            )
        }

        try {
            stateDoc.set(state, SetOptions.merge()).await()
        } catch (_: Exception) {
            // Keep app responsive even if persistence fails.
        }
    }

    private fun persistPlayerStateAsync() {
        backgroundScope.launch {
            persistPlayerStateToFirestore()
        }
    }

    fun getAccessToken(): String? = "local-user-token"

    fun getRefreshToken(): String? = "local-refresh-token"

    suspend fun refreshAccessToken(): String? = "local-user-token"

    suspend fun getUserPlaylists(): PlaylistsResponse {
        maybeSyncFromFirebase()
        synchronized(lock) {
            return PlaylistsResponse(items = playlistsById.values.toList())
        }
    }

    suspend fun getCurrentlyPlaying(): CurrentlyPlayingResponse {
        synchronized(lock) {
            if (currentTrack == null && allTracks.isNotEmpty()) {
                currentTrack = allTracks.first()
            }
            return CurrentlyPlayingResponse(item = currentTrack, isPlaying = isPlaying)
        }
    }

    suspend fun play(): CurrentlyPlayingResponse {
        synchronized(lock) {
            if (currentTrack == null) currentTrack = allTracks.firstOrNull()
            isPlaying = currentTrack != null
        }
        persistPlayerStateAsync()
        synchronized(lock) {
            return CurrentlyPlayingResponse(item = currentTrack, isPlaying = isPlaying)
        }
    }

    suspend fun pause(): CurrentlyPlayingResponse {
        synchronized(lock) {
            isPlaying = false
        }
        persistPlayerStateAsync()
        synchronized(lock) {
            return CurrentlyPlayingResponse(item = currentTrack, isPlaying = isPlaying)
        }
    }

    suspend fun next(): CurrentlyPlayingResponse {
        val autoplaySeed = synchronized(lock) { currentTrack }
        synchronized(lock) {
            val nextUri = if (queueUris.isNotEmpty()) queueUris.removeAt(0) else null
            val track = if (nextUri != null) findTrackByUri(nextUri) else nextFromContextOrLibrary()
            if (track != null) setCurrentTrack(track, playNow = true)
        }
        if (autoplayEnabled && autoplaySeed != null) {
            val seeds = synchronized(lock) { autoplaySeedTracks(currentTrack, recentTracks.toList()) }
            refillAutoplayQueueIfNeeded(seeds, targetSize = AUTOPLAY_FILL_SIZE)
        }
        persistPlayerStateAsync()
        synchronized(lock) {
            return CurrentlyPlayingResponse(item = currentTrack, isPlaying = isPlaying)
        }
    }

    suspend fun previous(): CurrentlyPlayingResponse {
        synchronized(lock) {
            val previousUri = playbackHistory.removeLastOrNull()
            val previousTrack = if (previousUri != null) findTrackByUri(previousUri) else null
            if (previousTrack != null) {
                currentTrack = previousTrack
                isPlaying = true
                addTrackToRecents(previousTrack)
            }
        }
        persistPlayerStateAsync()
        synchronized(lock) {
            return CurrentlyPlayingResponse(item = currentTrack, isPlaying = isPlaying)
        }
    }

    suspend fun toggleShuffle(shuffle: Boolean) {
        synchronized(lock) {
            shuffleEnabled = shuffle
        }
        persistPlayerStateAsync()
    }

    suspend fun setRepeatMode(repeatMode: RepeatMode) {
        synchronized(lock) {
            Companion.repeatMode = repeatMode
        }
        persistPlayerStateAsync()
    }

    suspend fun addToQueue(trackUri: String) {
        synchronized(lock) {
            queueUris.add(normalizedUri(trackUri))
        }
        persistPlayerStateAsync()
    }

    suspend fun replaceQueue(trackUris: List<String>) {
        synchronized(lock) {
            queueUris.clear()
            queueUris.addAll(trackUris.map { normalizedUri(it) })
        }
        persistPlayerStateAsync()
    }

    suspend fun getRecommendations(seedTracks: List<String>, limit: Int = 20): List<Track> {
        maybeSyncFromFirebase()
        val resolvedSeeds = synchronized(lock) {
            seedTracks.mapNotNull { seed -> findTrackByUri(seed) ?: findTrackById(seed) }.distinctBy { it.id ?: it.uri }
        }

        if (resolvedSeeds.isEmpty()) return emptyList()

        return buildRecommendationPool(resolvedSeeds, limit)
    }

    suspend fun getAvailableDevices(): List<Device> {
        return listOf(
            Device(
                id = "local_device_1",
                isActive = true,
                isPrivateSession = false,
                isRestricted = false,
                name = "Carrfy Device",
                type = "Smartphone",
                volumePercent = 75
            )
        )
    }

    suspend fun playTrack(trackUri: String): PlayResult {
        val result = synchronized(lock) {
            val track = findTrackByUri(trackUri) ?: return PlayResult.Error("Track not found")
            setCurrentTrack(track, playNow = true)
            PlayResult.Success
        }
        persistPlayerStateAsync()
        return result
    }

    suspend fun playTrackWithAutoplay(trackUri: String, queueSize: Int = 20): PlayResult {
        val result = playTrack(trackUri)
        if (result is PlayResult.Success) {
            synchronized(lock) {
                autoplayEnabled = true
            }
            val seeds = synchronized(lock) { autoplaySeedTracks(currentTrack, recentTracks.toList()) }
            refillAutoplayQueueIfNeeded(seeds, targetSize = queueSize)
        }
        return result
    }

    suspend fun getPlaylistTracks(playlistId: String): PlaylistTracksResponse? {
        maybeSyncFromFirebase()
        synchronized(lock) {
            val playlist = playlistsById[playlistId] ?: return null
            val tracks = playlistTracksById[playlistId].orEmpty()
            return PlaylistTracksResponse(
                id = playlist.id,
                name = playlist.name,
                tracks = PlaylistTracksInfo(
                    items = tracks.map { PlaylistTrackItem(track = it) },
                    total = tracks.size
                )
            )
        }
    }

    suspend fun getTopArtists(): TopArtistsResponse {
        maybeSyncFromFirebase()
        synchronized(lock) {
            return TopArtistsResponse(items = allArtists)
        }
    }

    suspend fun getRecentlyPlayed(): RecentlyPlayedResponse {
        synchronized(lock) {
            return RecentlyPlayedResponse(items = recentTracks.map { RecentlyPlayedItem(track = it) })
        }
    }

    suspend fun search(query: String): SearchResponse {
        maybeSyncFromFirebase()
        val trimmedRaw = query.trim()
        if (trimmedRaw.isBlank()) return SearchResponse(tracks = TracksResponse(items = emptyList()))

        // Try Spotify search first (primary provider)
        android.util.Log.d("SpotifyRepository", "Searching Spotify for: $trimmedRaw")
        val spotifyItems = fetchSpotifySearchTracks(trimmedRaw, 20)
        if (spotifyItems.isNotEmpty()) {
            android.util.Log.d("SpotifyRepository", "Found ${spotifyItems.size} Spotify tracks for: $trimmedRaw")
            synchronized(lock) {
                val existingById = allTracks.associateBy { it.id }
                allTracks = (spotifyItems + allTracks)
                    .distinctBy { it.id }
                    .map { merged ->
                        existingById[merged.id]?.copy(
                            name = merged.name ?: existingById[merged.id]?.name,
                            uri = merged.uri ?: existingById[merged.id]?.uri,
                            artists = if (merged.artists.isNullOrEmpty()) existingById[merged.id]?.artists else merged.artists,
                            album = merged.album ?: existingById[merged.id]?.album,
                            durationMs = merged.durationMs ?: existingById[merged.id]?.durationMs
                        ) ?: merged
                    }
            }
            return SearchResponse(tracks = TracksResponse(items = spotifyItems))
        }

        // Fall back to Saavn search
        android.util.Log.d("SpotifyRepository", "Spotify search returned no results, falling back to Saavn for: $trimmedRaw")
        val saavnItems = fetchSaavnSearchTracks(trimmedRaw)
        if (saavnItems.isNotEmpty()) {
            android.util.Log.d("SpotifyRepository", "Found ${saavnItems.size} Saavn tracks for: $trimmedRaw")
            synchronized(lock) {
                val existingById = allTracks.associateBy { it.id }
                allTracks = (saavnItems + allTracks)
                    .distinctBy { it.id }
                    .map { merged ->
                        existingById[merged.id]?.copy(
                            name = merged.name ?: existingById[merged.id]?.name,
                            uri = merged.uri ?: existingById[merged.id]?.uri,
                            artists = if (merged.artists.isNullOrEmpty()) existingById[merged.id]?.artists else merged.artists,
                            album = merged.album ?: existingById[merged.id]?.album,
                            durationMs = merged.durationMs ?: existingById[merged.id]?.durationMs
                        ) ?: merged
                    }
            }
            return SearchResponse(tracks = TracksResponse(items = saavnItems))
        }

        // Fall back to local catalog search
        android.util.Log.d("SpotifyRepository", "No Spotify or Saavn results, searching local catalog for: $trimmedRaw")
        synchronized(lock) {
            val trimmed = trimmedRaw.lowercase()
            val items = if (trimmed.startsWith("artist:")) {
                val name = trimmed.removePrefix("artist:").trim()
                allTracks.filter { track ->
                    track.artists.orEmpty().any { it.name.orEmpty().lowercase().contains(name) }
                }
            } else {
                allTracks.filter { track ->
                    val matchesTrack = track.name.orEmpty().lowercase().contains(trimmed)
                    val matchesArtist = track.artists.orEmpty().any { it.name.orEmpty().lowercase().contains(trimmed) }
                    val matchesAlbum = track.album?.name.orEmpty().lowercase().contains(trimmed)
                    matchesTrack || matchesArtist || matchesAlbum
                }
            }
            return SearchResponse(tracks = TracksResponse(items = items))
        }
    }

    suspend fun getArtist(artistId: String): Artist? {
        maybeSyncFromFirebase()
        synchronized(lock) {
            return allArtists.firstOrNull { it.id == artistId }
        }
    }

    suspend fun getArtistTopTracks(artistId: String): ArtistTopTracksResponse {
        maybeSyncFromFirebase()
        synchronized(lock) {
            val tracks = allTracks.filter { track -> track.artists.orEmpty().any { it.id == artistId } }
            return ArtistTopTracksResponse(tracks = tracks)
        }
    }

    suspend fun playPlaylistFrom(playlistId: String, trackIndex: Int) {
        synchronized(lock) {
            val playlistTracks = playlistTracksById[playlistId].orEmpty()
            if (playlistTracks.isEmpty()) return

            val normalizedIndex = trackIndex.coerceIn(0, playlistTracks.lastIndex)
            currentPlaylistContext = playlistTracks.mapNotNull { it.uri }
            setCurrentTrack(playlistTracks[normalizedIndex], playNow = true)
        }
        persistPlayerStateAsync()
    }

    suspend fun getQueueSize(): Int {
        synchronized(lock) {
            return queueUris.size
        }
    }

    suspend fun clearQueue() {
        synchronized(lock) {
            queueUris.clear()
        }
        persistPlayerStateAsync()
    }

    suspend fun getQueuedTracks(): List<String> {
        synchronized(lock) {
            return queueUris.toList()
        }
    }

    suspend fun getQueueInfo(): QueueInfo {
        synchronized(lock) {
            return QueueInfo(size = queueUris.size, trackUris = queueUris.toList())
        }
    }

    suspend fun addToRecents(track: Track) {
        synchronized(lock) {
            addTrackToRecents(track)
        }
        persistPlayerStateAsync()
    }

    suspend fun refreshCurrentTrackAndAddToRecents() {
        synchronized(lock) {
            currentTrack?.let { addTrackToRecents(it) }
        }
        persistPlayerStateAsync()
    }

    suspend fun getCustomRecents(): List<Track> {
        synchronized(lock) {
            return recentTracks.toList()
        }
    }

    suspend fun clearCustomRecents() {
        synchronized(lock) {
            recentTracks.clear()
        }
        persistPlayerStateAsync()
    }

    /**
     * Import a playlist by URL (Spotify, YouTube Music, Apple Music, Amazon Music)
     */
    suspend fun importPlaylistByUrl(playlistUrl: String, userId: String): Result<Pair<String, Int>> {
        return runCatching {
            android.util.Log.d("PlaylistImport", "Starting import for URL: $playlistUrl")
            
            val importer = com.carrfy.auth.PlaylistImporter()
            val result = importer.importPlaylist(playlistUrl).getOrThrow()
            
            android.util.Log.d("PlaylistImport", "Import result: ${result.playlistName}, source: ${result.source}, tracks: ${result.tracksImported}")
            
            val playlistId = "imported_${result.source}_${result.playlistId}"

            // For Spotify, fetch actual track data
            if (result.source == "spotify") {
                android.util.Log.d("PlaylistImport", "Fetching Spotify tracks for ID: ${result.playlistId}")
                val tracks = fetchSpotifyPlaylistTracks(result.playlistId)
                android.util.Log.d("PlaylistImport", "Fetched ${tracks.size} tracks from Spotify")
                
                registerTracksInCatalog(tracks)

                synchronized(lock) {
                    playlistsById = playlistsById + (playlistId to Playlist(
                        id = playlistId,
                        name = result.playlistName,
                        description = "Imported from ${result.source}",
                        images = emptyList(),
                        owner = PlaylistOwner(
                            id = userId,
                            displayName = "Imported"
                        ),
                        tracks = PlaylistTracksInfo(
                            items = tracks.map { PlaylistTrackItem(track = it) },
                            total = tracks.size
                        )
                    ))
                    playlistTracksById = playlistTracksById + (playlistId to tracks)
                }
                persistPlayerStateAsync()
                android.util.Log.d("PlaylistImport", "Successfully imported Spotify playlist: $playlistId")
                Pair(playlistId, tracks.size)
            } else {
                // For other services, store as reference for now
                android.util.Log.d("PlaylistImport", "Importing ${result.source} playlist as reference")
                synchronized(lock) {
                    playlistsById = playlistsById + (playlistId to Playlist(
                        id = playlistId,
                        name = result.playlistName,
                        description = "Imported from ${result.source}",
                        images = emptyList(),
                        owner = PlaylistOwner(
                            id = userId,
                            displayName = "Imported"
                        ),
                        tracks = null
                    ))
                }
                persistPlayerStateAsync()
                Pair(playlistId, 0)
            }
        }
    }

    /**
     * Fetch public Spotify playlist tracks (no OAuth needed)
     */
    private suspend fun fetchSpotifyPlaylistTracks(playlistId: String): List<Track> {
        return withContext(Dispatchers.IO) {
            android.util.Log.d("SpotifyRepository", "fetchSpotifyPlaylistTracks: playlistId=$playlistId")
            
            // Get access token
            val accessToken = com.carrfy.auth.SpotifyAuthManager.getAccessToken()
                ?: error("Failed to get Spotify access token")
            
            android.util.Log.d("SpotifyRepository", "Got access token, length: ${accessToken.length}")
            
            val tracks = mutableListOf<Track>()
            var offset = 0
            var shouldContinue = true

            while (shouldContinue) {
                val url = URL("https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=50&offset=$offset&fields=items(track(id,name,artists,album,duration_ms))")
                android.util.Log.d("SpotifyRepository", "Fetching tracks at offset=$offset from: $url")
                
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("User-Agent", "Carrfy/1.0")
                    connectTimeout = SAAVN_TIMEOUT_MS
                    readTimeout = SAAVN_TIMEOUT_MS
                }

                try {
                    val responseCode = connection.responseCode
                    android.util.Log.d("SpotifyRepository", "Response code: $responseCode")
                    
                    if (responseCode !in 200..299) {
                        android.util.Log.d("SpotifyRepository", "Non-success response code, stopping pagination")
                        shouldContinue = false
                        continue
                    }
                    
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    android.util.Log.d("SpotifyRepository", "Response body length: ${body.length}")
                    
                    val root = JsonParser.parseString(body)
                    val items = root.asJsonObject.get("items")?.asJsonArray
                    
                    if (items == null) {
                        android.util.Log.d("SpotifyRepository", "No items found in response")
                        shouldContinue = false
                        continue
                    }

                    android.util.Log.d("SpotifyRepository", "Found ${items.size()} items at offset=$offset")

                    for (item in items) {
                        try {
                            val track = item.asJsonObject.get("track")?.asJsonObject ?: continue
                            val id = track.get("id")?.asString ?: continue
                            val name = track.get("name")?.asString ?: "Unknown"
                            val artists = track.get("artists")?.asJsonArray?.mapNotNull { artist ->
                                val artistObj = artist.asJsonObject
                                Artist(
                                    id = artistObj.get("id")?.asString ?: "",
                                    name = artistObj.get("name")?.asString ?: "",
                                    images = null,
                                    genres = emptyList()
                                )
                            } ?: emptyList()

                            val album = track.get("album")?.asJsonObject?.let { albumObj ->
                                Album(
                                    id = albumObj.get("id")?.asString ?: "",
                                    name = albumObj.get("name")?.asString ?: "",
                                    images = albumObj.get("images")?.asJsonArray?.mapNotNull { img ->
                                        val imgObj = img.asJsonObject
                                        Image(
                                            url = imgObj.get("url")?.asString ?: "",
                                            height = imgObj.get("height")?.asInt,
                                            width = imgObj.get("width")?.asInt
                                        )
                                    } ?: emptyList()
                                )
                            } ?: Album(id = "", name = "", images = emptyList())

                            val spotifyTrack = Track(
                                id = id,
                                name = name,
                                uri = "spotify:track:$id",
                                artists = artists,
                                album = album,
                                durationMs = track.get("duration_ms")?.asInt,
                                source = "spotify"
                            )
                            tracks += spotifyTrack
                        } catch (e: Exception) {
                            // Skip malformed tracks
                            android.util.Log.d("SpotifyRepository", "Error parsing track: ${e.message}")
                        }
                    }

                    offset += 50
                    if (items.size() < 50) {
                        android.util.Log.d("SpotifyRepository", "Got fewer than 50 items, stopping pagination")
                        shouldContinue = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SpotifyRepository", "Error fetching tracks: ${e.message}", e)
                    shouldContinue = false
                }
            }
            
            android.util.Log.d("SpotifyRepository", "Total tracks fetched: ${tracks.size}")
            tracks
        }
    }


    suspend fun getTrackInfo(trackUri: String): Track? {
        synchronized(lock) {
            return findTrackByUri(trackUri)
        }
    }

    data class QueueInfo(
        val size: Int,
        val trackUris: List<String>
    )

    sealed class PlayResult {
        object Success : PlayResult()
        object NoToken : PlayResult()
        object NoActiveDevice : PlayResult()
        object NoPermission : PlayResult()
        data class Error(val message: String) : PlayResult()
    }
}
