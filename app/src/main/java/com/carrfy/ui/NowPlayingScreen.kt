package com.carrfy.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.carrfy.spotify.SpotifyRepository
import com.carrfy.spotify.models.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.carrfy.spotify.models.CurrentlyPlayingResponse
import com.carrfy.playback.PlayerManager

@Composable
fun NowPlayingScreen(onDominantImageUrlChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { SpotifyRepository(context) }
    val player = remember(context) { PlayerManager.getPlayer(context) }

    var track by remember { mutableStateOf<Track?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var queueSize by remember { mutableStateOf(0) }
    var isGestureInProgress by remember { mutableStateOf(false) }
    var lastGestureTime by remember { mutableStateOf(0L) }
    var lastSentDominantImageUrl by remember { mutableStateOf<String?>(null) }

    suspend fun refreshCurrentTrack() {
        try {
            val response = repo.getCurrentlyPlaying()
            val newTrack = response.item
            val newIsPlaying = response.isPlaying ?: false
            val newImageUrl = newTrack?.album?.images?.firstOrNull()?.url

            if (newTrack?.id != track?.id) {
                track = newTrack
                if (newTrack != null) {
                    repo.addToRecents(newTrack)
                }
            }

            if (newImageUrl != lastSentDominantImageUrl) {
                onDominantImageUrlChange(newImageUrl)
                lastSentDominantImageUrl = newImageUrl
            }

            isPlaying = newIsPlaying
            queueSize = repo.getQueueSize()
            error = if (track == null) "Nothing is currently playing." else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (track == null) {
                error = "Failed to load now playing: ${e.localizedMessage}"
            }
        }
    }

    suspend fun applyPlaybackResponse(response: CurrentlyPlayingResponse?) {
        val newTrack = response?.item
        val newIsPlaying = response?.isPlaying ?: false
        track = newTrack
        isPlaying = newIsPlaying
        queueSize = repo.getQueueSize()

        val imageUrl = newTrack?.album?.images?.firstOrNull()?.url
        if (imageUrl != lastSentDominantImageUrl) {
            onDominantImageUrlChange(imageUrl)
            lastSentDominantImageUrl = imageUrl
        }

        configurePlayerForUrl(player, resolvePlaybackUrl(newTrack), newIsPlaying)
    }

    LaunchedEffect(Unit) {
        // Set up track completion listener for auto-skip
        PlayerManager.setOnTrackCompletedListener {
            scope.launch {
                try {
                    android.util.Log.d("NowPlayingScreen", "Track completed, skipping to next")
                    val response = repo.next()
                    val newTrack = response?.item
                    val newImageUrl = newTrack?.album?.images?.firstOrNull()?.url

                    track = newTrack
                    isPlaying = true // FORCE play on auto-skip
                    queueSize = repo.getQueueSize()

                    if (newImageUrl != lastSentDominantImageUrl) {
                        onDominantImageUrlChange(newImageUrl)
                        lastSentDominantImageUrl = newImageUrl
                    }

                    // Configure player to PLAY the next track immediately
                    configurePlayerForUrl(player, resolvePlaybackUrl(newTrack), true)
                } catch (e: Exception) {
                    android.util.Log.e("NowPlayingScreen", "Error auto-skipping track: ${e.message}")
                }
            }
        }
        
        // Start playback monitor globally
        com.carrfy.playback.PlaybackMonitor.start(context)
        
        // Set listener for track changes
        com.carrfy.playback.PlaybackMonitor.setOnTrackChangeListener { newTrackId ->
            scope.launch {
                refreshCurrentTrack()
            }
        }
        
        refreshCurrentTrack()
        while (true) {
            delay(200) // Very fast refresh when NowPlayingScreen is active
            refreshCurrentTrack()
        }
    }

    LaunchedEffect(track?.id, isPlaying) {
        configurePlayerForUrl(player, resolvePlaybackUrl(track), isPlaying)
    }

    // IMPORTANT: Do NOT dispose the player here - it's a global singleton
    // Disposing it would stop playback when switching tabs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    val currentTime = System.currentTimeMillis()
                    if (!isGestureInProgress && (currentTime - lastGestureTime) > 600) {
                        isGestureInProgress = true
                        lastGestureTime = currentTime
                        scope.launch {
                            try {
                                if (dragAmount > 20) {
                                    applyPlaybackResponse(repo.previous())
                                } else if (dragAmount < -20) {
                                    applyPlaybackResponse(repo.next())
                                }
                                delay(40)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Toast.makeText(context, "Navigation failed", Toast.LENGTH_SHORT).show()
                            } finally {
                                isGestureInProgress = false
                            }
                        }
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Now Playing",
                    fontSize = 28.sp,
                    color = Color.White
                )
                if (queueSize > 0) {
                    Text(
                        "$queueSize track${if (queueSize != 1) "s" else ""} in queue",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        modifier = Modifier.clickable {
                            scope.launch {
                                val queueInfo = repo.getQueueInfo()
                                val queueDetails = queueInfo.trackUris.take(5).mapNotNull { uri ->
                                    repo.getTrackInfo(uri)
                                }
                                val trackNames = queueDetails.map { it.name ?: "Unknown" }.joinToString(", ")
                                Toast.makeText(context, "Queue: $trackNames", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
            IconButton(onClick = { scope.launch { refreshCurrentTrack() } }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            track?.album?.images?.firstOrNull()?.url?.let { imageUrl ->
                Image(
                    painter = rememberAsyncImagePainter(imageUrl),
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    scope.launch {
                                        try {
                                            val response = if (isPlaying) repo.pause() else repo.play()
                                            applyPlaybackResponse(response)
                                        } catch (e: Exception) {
                                            if (e is CancellationException) throw e
                                            Toast.makeText(context, "Playback control failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onLongPress = {
                                    if (queueSize > 0) {
                                        scope.launch {
                                            try {
                                                repo.clearQueue()
                                                Toast.makeText(context, "Queue cleared", Toast.LENGTH_SHORT).show()
                                                refreshCurrentTrack()
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                Toast.makeText(context, "Failed to clear queue", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        },
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                track?.name ?: "Unknown Track",
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                track?.artists?.joinToString(", ") { it.name ?: "" } ?: "",
                color = Color.LightGray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error ?: "",
                    color = Color(0xFFFFB3B3),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        IconButton(
            onClick = {
                scope.launch {
                    isShuffleOn = !isShuffleOn
                    repo.toggleShuffle(isShuffleOn)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp)
        ) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = if (isShuffleOn) "Shuffle On" else "Shuffle Off",
                tint = if (isShuffleOn) Color.Green else Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        IconButton(
            onClick = {
                scope.launch {
                    repeatMode = when (repeatMode) {
                        RepeatMode.OFF -> RepeatMode.CONTEXT
                        RepeatMode.CONTEXT -> RepeatMode.TRACK
                        RepeatMode.TRACK -> RepeatMode.OFF
                    }
                    repo.setRepeatMode(repeatMode)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp)
        ) {
            Icon(
                when (repeatMode) {
                    RepeatMode.OFF -> Icons.Default.Repeat
                    RepeatMode.CONTEXT -> Icons.Default.RepeatOne
                    RepeatMode.TRACK -> Icons.Default.Repeat
                },
                contentDescription = when (repeatMode) {
                    RepeatMode.OFF -> "Repeat Off"
                    RepeatMode.CONTEXT -> "Repeat One"
                    RepeatMode.TRACK -> "Repeat All"
                },
                tint = if (repeatMode != RepeatMode.OFF) Color.Green else Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

enum class RepeatMode {
    OFF, CONTEXT, TRACK
}
