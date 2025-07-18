package com.carrfy.auth

import com.google.gson.annotations.SerializedName

/**
 * Carrfy user account
 */
data class CarrfyUser(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val preferences: UserPreferences = UserPreferences(),
    val listeningHistory: List<ListenRecord> = emptyList(),
    val importedPlaylists: List<ImportedPlaylist> = emptyList()
)

/**
 * User preferences and settings
 */
data class UserPreferences(
    val autoplay: Boolean = true,
    val quality: String = "high",
    val language: String = "en",
    val theme: String = "dark",
    val notificationsEnabled: Boolean = true
)

/**
 * Track listening record
 */
data class ListenRecord(
    val trackUri: String = "",
    val trackName: String = "",
    val artistName: String = "",
    val listenedAt: Long = System.currentTimeMillis(),
    val durationMs: Int = 0
)

/**
 * Imported playlist metadata
 */
data class ImportedPlaylist(
    val id: String = "",
    val name: String = "",
    val source: String = "",  // "spotify", "youtube_music", "apple_music", "amazon_music"
    val sourceId: String = "",  // ID or URL from source
    val trackCount: Int = 0,
    val importedAt: Long = System.currentTimeMillis(),
    val tracks: List<String> = emptyList()  // Track URIs
)

/**
 * Spotify playlist info for public API
 */
data class SpotifyPlaylistInfo(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val images: List<Map<String, String>>? = null,
    @SerializedName("track_count")
    val trackCount: Int? = null,
    val owner: Map<String, String>? = null
)

/**
 * YouTube Music playlist (parsed from URL)
 */
data class YouTubePlaylistInfo(
    val id: String = "",
    val name: String = "",
    val trackCount: Int = 0,
    val thumbnail: String? = null
)

/**
 * Generic imported playlist result
 */
data class PlaylistImportResult(
    val success: Boolean = false,
    val playlistId: String = "",
    val playlistName: String = "",
    val tracksImported: Int = 0,
    val source: String = "",
    val message: String = "",
    val failedTracks: Int = 0
)

