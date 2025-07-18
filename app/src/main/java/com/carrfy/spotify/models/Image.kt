package com.carrfy.spotify.models

import com.google.gson.annotations.SerializedName

data class Image(
    @SerializedName("url")
    val url: String?,
    @SerializedName("height")
    val height: Int?,
    @SerializedName("width")
    val width: Int?
) 