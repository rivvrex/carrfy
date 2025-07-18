package com.carrfy.spotify.models

import com.google.gson.annotations.SerializedName

data class Album(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("images")
    val images: List<Image>?
) 