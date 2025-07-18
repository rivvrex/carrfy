package com.carrfy.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrfy.spotify.SpotifyRepository
import com.carrfy.spotify.models.Track
import com.carrfy.spotify.models.Artist
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import android.widget.Toast
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.rememberDismissState
import androidx.palette.graphics.Palette
import coil.request.ImageRequest
import coil.ImageLoader
import coil.request.SuccessResult
import androidx.compose.ui.graphics.Brush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(artistId: String, onBack: () -> Unit, onDominantImageUrlChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var artist by remember { mutableStateOf<Artist?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tracks, artist) {
        val imageUrl = artist?.images?.firstOrNull()?.url ?: tracks.firstOrNull()?.album?.images?.firstOrNull()?.url
        onDominantImageUrlChange(imageUrl)
    }

    LaunchedEffect(artistId) {
        try {
            val repo = SpotifyRepository(context)
            val artistResponse = repo.getArtist(artistId)
            if (artistResponse != null) {
                artist = artistResponse
            }
            val tracksResponse = repo.getArtistTopTracks(artistId)
            if (tracksResponse != null) {
                tracks = tracksResponse.tracks ?: emptyList()
            }
            if (tracks.isEmpty()) {
                artist?.name?.let { artistName ->
                    val searchResponse = repo.search("artist:$artistName")
                    tracks = searchResponse?.tracks?.items?.filter { track ->
                        track.artists?.any { it.id == artistId } == true
                    } ?: emptyList()
                }
            }
            if (tracks.isEmpty()) {
                error = "No tracks found for this artist."
            }
        } catch (e: Exception) {
            error = "Failed to load artist data: ${e.localizedMessage}"
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
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                artist?.name ?: "Artist", 
                fontSize = 24.sp, 
                color = Color.White, 
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        when {
            isLoading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
            error != null -> Text(error!!, color = Color.Red, modifier = Modifier.padding(16.dp))
            tracks.isEmpty() -> Text("No tracks found.", color = Color.Red, modifier = Modifier.padding(16.dp))
            else -> {
                // Responsive layout for artist info and tracks
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left side - Artist Info
                    Column(
                        modifier = Modifier.weight(0.4f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Artist Image
                        artist?.images?.firstOrNull()?.url?.let { imageUrl ->
                            Image(
                                painter = rememberAsyncImagePainter(imageUrl),
                                contentDescription = "Artist Image",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(60.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Artist Genres
                        artist?.genres?.let { genres ->
                            if (genres.isNotEmpty()) {
                                Text(
                                    "Genres: ${genres.joinToString(", ")}",
                                    color = Color.LightGray,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                        }
                    }
                    
                    // Right side - Tracks List
                    Column(
                        modifier = Modifier.weight(0.6f)
                    ) {
                        Text(
                            "Top Tracks",
                            fontSize = 20.sp,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight()
                        ) {
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
                                        ArtistTrackItem(
                                            track = track,
                                            onPlay = {
                                                scope.launch {
                                                    val repo = SpotifyRepository(context)
                                                    val allTrackUris = tracks.mapNotNull { it.uri }
                                                    val currentTrackIndex = tracks.indexOf(track)
                                                    val reorderedUris = if (currentTrackIndex >= 0) {
                                                        val beforeCurrent = allTrackUris.take(currentTrackIndex)
                                                        val fromCurrent = allTrackUris.drop(currentTrackIndex)
                                                        fromCurrent + beforeCurrent
                                                    } else {
                                                        allTrackUris
                                                    }
                                                    repo.replaceQueue(reorderedUris)
                                                    Toast.makeText(context, "Added ${tracks.size} songs by ${artist?.name} to queue", Toast.LENGTH_SHORT).show()
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
}

@Composable
fun ArtistTrackItem(track: Track, onPlay: () -> Unit) {
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
                track.album?.name ?: "",
                color = Color.LightGray,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
    }
} 