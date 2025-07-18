package com.carrfy.spotify.models

import com.google.gson.annotations.SerializedName

data class ArtistTopTracksResponse(
    @SerializedName("tracks")
    val tracks: List<Track>?
) 