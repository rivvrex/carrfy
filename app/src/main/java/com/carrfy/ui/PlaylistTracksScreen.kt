package com.carrfy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrfy.spotify.SpotifyRepository
import com.carrfy.spotify.models.Track
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
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
fun PlaylistTracksScreen(playlistId: String, onBack: () -> Unit, onDominantImageUrlChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlistName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tracks) {
        val imageUrl = tracks.firstOrNull()?.album?.images?.firstOrNull()?.url
        onDominantImageUrlChange(imageUrl)
    }

    LaunchedEffect(playlistId) {
        try {
            val repo = SpotifyRepository(context)
            val response = repo.getPlaylistTracks(playlistId)
            if (response == null) {
                error = "Failed to load playlist tracks."
            } else {
                val responseTracks = response.tracks?.items?.mapNotNull { it.track } ?: emptyList()
                tracks = responseTracks
                playlistName = response.name ?: "Playlist"
                if (tracks.isEmpty()) {
                    error = "No tracks found in this playlist."
                }
            }
        } catch (e: Exception) {
            error = "Failed to load playlist: ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        // ... remove background/gradient logic ...
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(playlistName, fontSize = 24.sp, color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
        
        Spacer(Modifier.height(16.dp))
        
        when {
            isLoading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
            error != null -> Text(error!!, color = Color.Red, modifier = Modifier.padding(16.dp))
            tracks.isEmpty() -> Text("No tracks found.", color = Color.Red, modifier = Modifier.padding(16.dp))
            else -> {
                LazyColumn {
                    items(tracks) { track ->
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
                                PlaylistTrackItem(
                                    track = track,
                                    onPlay = {
                                        scope.launch {
                                            val repo = SpotifyRepository(context)
                                            val currentTrackIndex = tracks.indexOf(track)
                                            if (currentTrackIndex >= 0) {
                                                repo.playPlaylistFrom(playlistId, currentTrackIndex)
                                                Toast.makeText(context, "Playing this song from playlist", Toast.LENGTH_SHORT).show()
                                            }
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

@Composable
fun PlaylistTrackItem(track: Track, onPlay: () -> Unit) {
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