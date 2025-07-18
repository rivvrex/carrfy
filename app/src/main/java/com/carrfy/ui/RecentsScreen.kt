package com.carrfy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
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
import android.widget.Toast
import androidx.compose.foundation.background
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
fun RecentsScreen(onDominantImageUrlChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recents by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastRefreshTime by remember { mutableStateOf(0L) }

    // Function to refresh recents (custom recents only)
    fun refreshRecents() {
        scope.launch {
            try {
                isLoading = true
                error = null
                println("=== REFRESHING CUSTOM RECENTS ===")
                val repo = SpotifyRepository(context)
                
                val customRecents = repo.getCustomRecents()
                println("Custom recents count: ${customRecents.size}")
                if (customRecents.isNotEmpty()) {
                    println("Custom recents tracks: ${customRecents.take(3).map { it.name }}")
                    recents = customRecents
                    error = null
                    println("✅ Using custom recents with ${recents.size} tracks")
                } else {
                    error = "No recent tracks found. Play some tracks first!"
                    println("❌ No custom recents found")
                }
                lastRefreshTime = System.currentTimeMillis()
                println("=== END REFRESHING CUSTOM RECENTS ===")
            } catch (e: Exception) {
                error = "Failed to load recents: ${e.localizedMessage}"
                println("❌ Recents error: ${e.message}")
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }



    LaunchedEffect(recents) {
        val imageUrl = recents.firstOrNull()?.album?.images?.firstOrNull()?.url
        onDominantImageUrlChange(imageUrl)
    }

    // Initial load
    LaunchedEffect(Unit) {
        refreshRecents()
    }

    // Periodic refresh every 10 seconds when screen is active (much faster)
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000) // 10 seconds instead of 30
            
            // First, check if there's a current track and add it to recents
            try {
                val repo = SpotifyRepository(context)
                val currentTrack = repo.getCurrentlyPlaying()?.item
                if (currentTrack != null) {
                    println("Periodic check: Adding current track to recents: ${currentTrack.name}")
                    repo.addToRecents(currentTrack)
                }
            } catch (e: Exception) {
                println("Periodic check error: ${e.message}")
            }
            
            // Then refresh display with custom recents only (no Spotify API)
            try {
                val repo = SpotifyRepository(context)
                val customRecents = repo.getCustomRecents()
                if (customRecents.isNotEmpty()) {
                    recents = customRecents
                    error = null
                    println("Periodic refresh: Using custom recents (${recents.size} tracks)")
                }
            } catch (e: Exception) {
                println("Periodic refresh error: ${e.message}")
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Header with title and refresh button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recents", fontSize = 28.sp, color = Color.White)
                IconButton(
                    onClick = { 
                        // Test: Add current track to recents if playing
                        scope.launch {
                            try {
                                val repo = SpotifyRepository(context)
                                val currentTrack = repo.getCurrentlyPlaying()?.item
                                if (currentTrack != null) {
                                    println("Testing: Adding current track to recents")
                                    repo.addToRecents(currentTrack)
                                    
                                    // Refresh display with custom recents only (don't call Spotify API)
                                    val customRecents = repo.getCustomRecents()
                                    if (customRecents.isNotEmpty()) {
                                        recents = customRecents
                                        error = null
                                        println("✅ Refreshed display with custom recents: ${recents.size} tracks")
                                    }
                                } else {
                                    Toast.makeText(context, "No track currently playing", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Test failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
            }
            
            when {
                isLoading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(16.dp))
                error != null -> Text(error!!, color = Color.Red, modifier = Modifier.padding(16.dp))
                recents.isEmpty() -> Text("No recent tracks found.", color = Color.Red, modifier = Modifier.padding(16.dp))
                else -> {
                    LazyColumn {
                        items(recents) { track ->
                            val dismissState = rememberDismissState(
                                confirmValueChange = {
                                    if (it == DismissValue.DismissedToStart) {
                                        // Add to queue on swipe
                                        track.uri?.let { uri ->
                                            scope.launch {
                                                val repo = SpotifyRepository(context)
                                                repo.addToQueue(uri)
                                                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        // Spring back
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
                                                println("RecentsScreen - Playing track: ${track.name}")
                                                println("RecentsScreen - Track URI: $uri")
                                                scope.launch {
                                                    val repo = SpotifyRepository(context)
                                                    when (val result = repo.playTrack(uri)) {
                                                        is SpotifyRepository.PlayResult.Success -> {
                                                            // Refresh recents after playing a track
                                                            delay(500) // Much faster refresh after playing
                                                            // Refresh display with custom recents
                                                            val customRecents = repo.getCustomRecents()
                                                            if (customRecents.isNotEmpty()) {
                                                                recents = customRecents
                                                                error = null
                                                                println("✅ Refreshed recents after playing: ${recents.size} tracks")
                                                            }
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
                                                }
                                            } ?: run {
                                                println("RecentsScreen - Track URI is null for: ${track.name}")
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
fun TrackItem(track: Track, onPlay: () -> Unit) {
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