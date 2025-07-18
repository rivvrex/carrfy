package com.carrfy.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages Carrfy user authentication and data
 */
class CarrfyAuthManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val sharedPrefs = context.getSharedPreferences("carrfy_auth", Context.MODE_PRIVATE)

    companion object {
        private const val SPOTIFY_PUBLIC_API = "https://api.spotify.com/v1"
        private const val SPOTIFY_SEARCH_URL = "https://www.spotify.com/search/"
    }

    /**
     * Register new Carrfy user
     */
    suspend fun registerUser(email: String, password: String, displayName: String): Result<CarrfyUser> {
        return runCatching {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: error("Authentication failed: No user returned")

                val carrfyUser = CarrfyUser(
                    id = firebaseUser.uid,
                    email = email,
                    displayName = displayName
                )

                firestore.collection("users").document(firebaseUser.uid)
                    .set(carrfyUser)
                    .await()

                carrfyUser
            } catch (e: Exception) {
                throw Exception("Registration failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}", e)
            }
        }
    }

    /**
     * Login Carrfy user
     */
    suspend fun loginUser(email: String, password: String): Result<CarrfyUser> {
        return runCatching {
            try {
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: error("Authentication failed: No user returned")

                val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
                doc.toObject(CarrfyUser::class.java) ?: error("User profile not found in database")
            } catch (e: Exception) {
                throw Exception("Login failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}", e)
            }
        }
    }

    /**
     * Get current logged-in user
     */
    suspend fun getCurrentUser(): CarrfyUser? {
        return withContext(Dispatchers.IO) {
            val firebaseUser = auth.currentUser ?: return@withContext null
            try {
                val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
                doc.toObject(CarrfyUser::class.java)
            } catch (e: Exception) {
                android.util.Log.e("CarrfyAuthManager", "Error getting current user: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Save user listening record
     */
    suspend fun recordTrackListen(trackUri: String, trackName: String, artistName: String, durationMs: Int = 0) {
        val firebaseUser = auth.currentUser ?: return
        
        val listenRecord = ListenRecord(
            trackUri = trackUri,
            trackName = trackName,
            artistName = artistName,
            durationMs = durationMs
        )

        firestore.collection("users").document(firebaseUser.uid)
            .collection("listening_history")
            .add(listenRecord)
            .await()
    }

    /**
     * Get user's listening history
     */
    suspend fun getListeningHistory(limit: Int = 100): List<ListenRecord> {
        val firebaseUser = auth.currentUser ?: return emptyList()

        return try {
            firestore.collection("users").document(firebaseUser.uid)
                .collection("listening_history")
                .orderBy("listenedAt")
                .limit(limit.toLong())
                .get()
                .await()
                .toObjects(ListenRecord::class.java)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Save imported playlist
     */
    suspend fun saveImportedPlaylist(playlist: ImportedPlaylist): Result<Unit> {
        val firebaseUser = auth.currentUser ?: return Result.failure(Exception("Not authenticated"))

        return runCatching {
            firestore.collection("users").document(firebaseUser.uid)
                .collection("imported_playlists")
                .document(playlist.id)
                .set(playlist, SetOptions.merge())
                .await()
        }
    }

    /**
     * Get all imported playlists for user
     */
    suspend fun getImportedPlaylists(): List<ImportedPlaylist> {
        val firebaseUser = auth.currentUser ?: return emptyList()

        return try {
            firestore.collection("users").document(firebaseUser.uid)
                .collection("imported_playlists")
                .get()
                .await()
                .toObjects(ImportedPlaylist::class.java)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Logout user
     */
    fun logoutUser() {
        auth.signOut()
        sharedPrefs.edit().clear().apply()
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean = auth.currentUser != null

    /**
     * Get public Spotify playlist info (no OAuth needed)
     */
    suspend fun getSpotifyPlaylistInfo(playlistId: String): Result<SpotifyPlaylistInfo> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val url = URL("$SPOTIFY_PUBLIC_API/playlists/$playlistId")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Carrfy/1.0")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                check(connection.responseCode in 200..299) { "Failed to fetch: ${connection.responseCode}" }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val obj = JsonParser.parseString(response).asJsonObject

                SpotifyPlaylistInfo(
                    id = obj.get("id")?.asString ?: playlistId,
                    name = obj.get("name")?.asString ?: "Unnamed",
                    description = obj.get("description")?.asString,
                    images = obj.get("images")?.asJsonArray?.mapNotNull { img ->
                        mapOf("url" to img.asJsonObject.get("url")?.asString.orEmpty())
                    },
                    trackCount = obj.get("tracks")?.asJsonObject?.get("total")?.asInt,
                    owner = mapOf("name" to (obj.get("owner")?.asJsonObject?.get("display_name")?.asString ?: ""))
                )
            }
        }
    }
}
