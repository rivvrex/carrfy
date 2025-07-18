package com.carrfy.spotify.models

import com.google.gson.annotations.SerializedName

data class Playlist(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String?,
    @SerializedName("images")
    val images: List<Image>?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("owner")
    val owner: PlaylistOwner?,
    @SerializedName("tracks")
    val tracks: PlaylistTracksInfo?
)

data class PlaylistOwner(
    @SerializedName("id")
    val id: String?,
    @SerializedName("display_name")
    val displayName: String?
) 
 
 
 