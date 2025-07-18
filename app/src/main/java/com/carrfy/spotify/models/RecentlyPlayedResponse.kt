package com.carrfy.spotify.models

data class RecentlyPlayedResponse(
    val items: List<RecentlyPlayedItem>
)

data class RecentlyPlayedItem(
    val track: Track
)