package com.carrfy.spotify.models

import com.google.gson.annotations.SerializedName

data class PlaylistsResponse(
    @SerializedName("items")
    val items: List<Playlist>?
)
 