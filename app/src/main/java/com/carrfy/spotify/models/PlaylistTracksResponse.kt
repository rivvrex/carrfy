package com.carrfy.spotify.models

import com.google.gson.annotations.SerializedName

data class PlaylistTracksResponse(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("tracks")
    val tracks: PlaylistTracksInfo?
)

data class PlaylistTracksInfo(
    @SerializedName("items")
    val items: List<PlaylistTrackItem>?,
    @SerializedName("total")
    val total: Int?
)

data class PlaylistTrackItem(
    @SerializedName("track")
    val track: Track?
)