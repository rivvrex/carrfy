// In com/carrfy/spotify/models/Track.kt
package com.carrfy.spotify.models

import com.google.gson.annotations.SerializedName

// Standard Track model for Spotify

data class Track(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("uri")
    val uri: String?,
    @SerializedName("artists")
    val artists: List<Artist>?,
    @SerializedName("album")
    val album: Album?,
    @SerializedName("duration_ms")
    val durationMs: Int?,
    @SerializedName("language")
    val language: String? = null,
    @SerializedName("music")
    val music: String? = null,
    @SerializedName("singers")
    val singers: String? = null,
    @SerializedName("play_count")
    val playCount: Long? = null,
    @SerializedName("year")
    val year: Int? = null,
    @SerializedName("source")
    val source: String? = null
)