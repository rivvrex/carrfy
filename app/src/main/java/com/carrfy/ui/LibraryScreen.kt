package com.carrfy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrfy.spotify.SpotifyRepository
import com.carrfy.spotify.models.Playlist
import android.util.Log
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import com.carrfy.auth.CarrfyAuthManager
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LibraryScreen(onPlaylistClick: (String) -> Unit, onDominantImageUrlChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { CarrfyAuthManager(context) }
    
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var importedCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadPlaylists() {
        scope.launch {
            try {
                val repo = SpotifyRepository(context)
                val token = repo.getAccessToken()
                if (token == null) {
                    errorMessage = "No access token found. Please log in to Spotify."
                    isLoading = false
                    return@launch
                }
                val response = repo.getUserPlaylists()
                if (response == null) {
                    errorMessage = "Failed to load playlists. Please check your internet connection and try again."
                } else {
                    playlists = response.items ?: emptyList()
                    
                    // Load imported playlists count
                    try {
                        val imported = authManager.getImportedPlaylists()
                        importedCount = imported.size
                    } catch (_: Exception) {
                        importedCount = 0
                    }
                    
                    if (playlists.isEmpty() && importedCount == 0) {
                        errorMessage = "No playlists found. Make sure you have playlists in your Spotify account or import some."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error: ${e.localizedMessage ?: "Unknown error occurred"}"
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun onRefresh() {
        isRefreshing = true
        errorMessage = null
        loadPlaylists()
    }

    LaunchedEffect(playlists) {
        val imageUrl = playlists.firstOrNull()?.images?.firstOrNull()?.url
        onDominantImageUrlChange(imageUrl)
    }

    LaunchedEffect(Unit) {
        loadPlaylists()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Header with refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Your Library",
                        fontSize = 28.sp,
                        color = Color.White
                    )
                    if (importedCount > 0) {
                        Text(
                            text = "$importedCount imported playlist${if (importedCount != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
                IconButton(
                    onClick = { onRefresh() },
                    enabled = !isRefreshing
                ) {
                    Icon(
                        Icons.Default.Refresh, 
                        contentDescription = "Refresh", 
                        tint = Color.White
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            errorMessage!!,
                            color = Color.Red,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { onRefresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            enabled = !isRefreshing
                        ) {
                            Text("Retry", color = Color.Black)
                        }
                    }
                }
                playlists.isEmpty() -> {
                    Text(
                        "No Spotify playlists found. Import some using your profile!",
                        color = Color.LightGray,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(playlists) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Playlist Image
        playlist.images?.firstOrNull()?.url?.let { imageUrl ->
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = "Playlist Cover",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } ?: run {
            // Placeholder if no image
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text("?", color = Color.White, fontSize = 24.sp)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Playlist Name
        Text(
            playlist.name ?: "Unknown Playlist",
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}