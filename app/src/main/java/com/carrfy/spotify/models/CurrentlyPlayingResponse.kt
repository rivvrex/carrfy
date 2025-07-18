package com.carrfy.spotify.models

import com.google.gson.annotations.SerializedName

data class CurrentlyPlayingResponse(
    @SerializedName("item")
    val item: Track?,
    @SerializedName("is_playing")
    val isPlaying: Boolean?
)



 