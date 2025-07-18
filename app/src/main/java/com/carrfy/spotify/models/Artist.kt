package com.carrfy.spotify.models

import com.google.gson.annotations.SerializedName

data class Artist(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("images")
    val images: List<Image>? = null,
    @SerializedName("genres")
    val genres: List<String>? = null
) 