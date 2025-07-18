package com.carrfy.spotify.models

data class SearchResponse(
    val tracks: TracksResponse?
)

data class TracksResponse(
    val items: List<Track>
) 