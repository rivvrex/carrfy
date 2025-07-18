package com.carrfy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.carrfy.spotify.models.Artist
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.background

@Composable
fun ArtistsScreen(onArtistClick: (String) -> Unit = {}, onDominantImageUrlChange: (String?) -> Unit) {
    val context = LocalContext.current
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(artists) {
        val imageUrl = artists.firstOrNull()?.images?.firstOrNull()?.url
        onDominantImageUrlChange(imageUrl)
    }

    LaunchedEffect(Unit) {
        try {
            val repo = SpotifyRepository(context)
            val response = repo.getTopArtists()
            artists = response?.items ?: emptyList()
            if (artists.isEmpty()) error = "No artists found."
        } catch (e: Exception) {
            error = "Failed to load artists: ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Artists", fontSize = 28.sp, color = Color.White, modifier = Modifier.padding(bottom = 24.dp))
        
        when {
            isLoading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(16.dp))
            error != null -> Text(error!!, color = Color.Red, modifier = Modifier.padding(16.dp))
            artists.isEmpty() -> Text("No artists found.", color = Color.Red, modifier = Modifier.padding(16.dp))
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(artists) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = { 
                                artist.id?.let { id -> onArtistClick(id) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistCard(artist: Artist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Artist Image
        artist.images?.firstOrNull()?.url?.let { imageUrl ->
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = "Artist Image",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp)),
                contentScale = ContentScale.Crop
            )
        } ?: run {
            // Placeholder if no image
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text("?", color = Color.White, fontSize = 24.sp)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Artist Name
        Text(
            artist.name ?: "Unknown Artist",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        
        // Genres (if available)
        artist.genres?.take(2)?.joinToString(", ")?.let { genres ->
            Text(
                genres,
                color = Color.LightGray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
} 