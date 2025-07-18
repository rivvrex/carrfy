package com.carrfy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrfy.spotify.SpotifyRepository
import com.carrfy.spotify.models.Track
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.rememberDismissState
import androidx.palette.graphics.Palette
import coil.request.ImageRequest
import coil.ImageLoader
import coil.request.SuccessResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onDominantImageUrlChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchResults) {
        val imageUrl = searchResults.firstOrNull()?.album?.images?.firstOrNull()?.url
        onDominantImageUrlChange(imageUrl)
    }

    // Debounced search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            delay(500) // Wait 500ms after user stops typing
            scope.launch {
                try {
                    isLoading = true
                    error = null
                    val repo = SpotifyRepository(context)
                    val response = repo.search(searchQuery)
                    searchResults = response?.tracks?.items ?: emptyList()
                } catch (e: Exception) {
                    error = "Search failed: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        } else {
            searchResults = emptyList()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            Text("Search", fontSize = 28.sp, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
            
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search for tracks, albums, artists...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            
            Spacer(Modifier.height(16.dp))
            
            when {
                isLoading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
                error != null -> Text(error!!, color = Color.Red, modifier = Modifier.padding(16.dp))
                searchQuery.length < 2 -> Text("Type at least 2 characters to search", color = Color.Gray, modifier = Modifier.padding(16.dp))
                searchResults.isEmpty() && searchQuery.isNotEmpty() -> Text("No results found", color = Color.Gray, modifier = Modifier.padding(16.dp))
                else -> {
                    LazyColumn {
                        items(searchResults) { track ->
                            val dismissState = rememberDismissState(
                                confirmValueChange = {
                                    if (it == DismissValue.DismissedToStart) {
                                        track.uri?.let { uri ->
                                            scope.launch {
                                                val repo = SpotifyRepository(context)
                                                repo.addToQueue(uri)
                                                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        false
                                    } else {
                                        false
                                    }
                                }
                            )
                            SwipeToDismiss(
                                state = dismissState,
                                directions = setOf(DismissDirection.EndToStart),
                                background = {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(Color.Transparent),
                                        contentAlignment = Alignment.CenterEnd
                                    ) { }
                                },
                                dismissContent = {
                                    TrackItem(
                                        track = track,
                                        onPlay = {
                                            track.uri?.let { uri ->
                                                println("SearchScreen - Playing track: ${track.name}")
                                                println("SearchScreen - Track URI: $uri")
                                                scope.launch {
                                                    try {
                                                        // Immediately configure local player with track
                                                        val player = com.carrfy.playback.PlayerManager.getPlayer(context)
                                                        val playbackUrl = resolvePlaybackUrl(track)
                                                        configurePlayerForUrl(player, playbackUrl, true)
                                                        
                                                        // Start autoplay on Spotify backend
                                                        val repo = SpotifyRepository(context)
                                                        when (val result = repo.playTrackWithAutoplay(uri, 20)) {
                                                            is SpotifyRepository.PlayResult.Success -> {
                                                                Toast.makeText(context, "Playing: ${track.name}", Toast.LENGTH_SHORT).show()
                                                            }
                                                            is SpotifyRepository.PlayResult.NoActiveDevice -> {
                                                                Toast.makeText(context, "Please open Spotify on any device and start playing a song first", Toast.LENGTH_LONG).show()
                                                            }
                                                            is SpotifyRepository.PlayResult.NoToken -> {
                                                                Toast.makeText(context, "Authentication error", Toast.LENGTH_SHORT).show()
                                                            }
                                                            is SpotifyRepository.PlayResult.NoPermission -> {
                                                                Toast.makeText(context, "No permission to control playback", Toast.LENGTH_SHORT).show()
                                                            }
                                                            is SpotifyRepository.PlayResult.Error -> {
                                                                Toast.makeText(context, "Playback error: ${result.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("SearchScreen", "Error playing track: ${e.message}", e)
                                                        Toast.makeText(context, "Error playing track", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } ?: run {
                                                println("SearchScreen - Track URI is null for: ${track.name}")
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTrackItem(track: Track, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Image
        track.album?.images?.firstOrNull()?.url?.let { imageUrl ->
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = "Track Art",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } ?: run {
            // Placeholder if no image
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text("?", color = Color.White)
            }
        }
        
        Spacer(Modifier.width(12.dp))
        
        // Track Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                track.name ?: "Unknown Track",
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1
            )
            Text(
                track.artists?.joinToString(", ") { it.name ?: "" } ?: "",
                color = Color.LightGray,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
    }
} 