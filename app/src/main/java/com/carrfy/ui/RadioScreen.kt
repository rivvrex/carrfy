package com.carrfy.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrfy.spotify.SpotifyRepository
import com.carrfy.spotify.models.Track
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch

@Composable
fun RadioScreen(onDominantImageUrlChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { SpotifyRepository(context) }

    var stations by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedStationUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        stations = try {
            repo.getRadioStations()
        } catch (e: Exception) {
            error = "Failed to load radio stations"
            emptyList()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(stations) {
        val imageUrl = stations.firstOrNull()?.album?.images?.firstOrNull()?.url
        onDominantImageUrlChange(imageUrl)
    }

    LaunchedEffect(Unit) {
        runCatching { repo.getCurrentlyPlaying() }
            .getOrNull()
            ?.item
            ?.uri
            ?.let { selectedStationUri = it }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Radio",
            fontSize = 28.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            error != null -> {
                Text(
                    text = error ?: "Unknown error",
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            }
            stations.isEmpty() -> {
                Text(
                    text = "No radio stations available.",
                    color = Color.LightGray,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                LazyColumn {
                    items(stations) { station ->
                        val isSelected = selectedStationUri == station.uri
                        RadioStationRow(
                            station = station,
                            isSelected = isSelected,
                            onPlay = {
                                val uri = station.uri
                                if (uri.isNullOrBlank()) {
                                    Toast.makeText(context, "Station stream unavailable", Toast.LENGTH_SHORT).show()
                                    return@RadioStationRow
                                }

                                scope.launch {
                                    when (val result = repo.playTrack(uri)) {
                                        is SpotifyRepository.PlayResult.Success -> {
                                            selectedStationUri = uri
                                            onDominantImageUrlChange(station.album?.images?.firstOrNull()?.url)
                                            Toast.makeText(context, "Playing ${station.name}", Toast.LENGTH_SHORT).show()
                                        }
                                        is SpotifyRepository.PlayResult.Error -> {
                                            Toast.makeText(context, "Playback error: ${result.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {
                                            Toast.makeText(context, "Could not start station", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
} 

@Composable
private fun RadioStationRow(station: Track, isSelected: Boolean, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Color(0x334CAF50) else Color(0x22161616))
            .clickable { onPlay() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        station.album?.images?.firstOrNull()?.url?.let { imageUrl ->
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = station.name,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.DarkGray)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = station.name ?: "Radio Station",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFD32F2F))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(3.dp))
            Text(
                text = station.artists?.firstOrNull()?.name ?: "Radio",
                color = if (isSelected) Color(0xFFB9F6CA) else Color.LightGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}