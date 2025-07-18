package com.carrfy.ui

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.carrfy.spotify.models.Track

/**
 * Shared playback utilities
 */

fun previewUrlFor(trackId: String?): String? = when (trackId) {
    "track_001" -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
    "track_002" -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
    "track_003" -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
    "track_004" -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
    "track_005" -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3"
    "track_006" -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3"
    else -> null
}

fun resolvePlaybackUrl(track: Track?): String? {
    val uri = track?.uri
    if (!uri.isNullOrBlank() && (uri.startsWith("http://") || uri.startsWith("https://"))) {
        return uri
    }
    return previewUrlFor(track?.id)
}

fun configurePlayerForUrl(player: ExoPlayer, url: String?, shouldPlay: Boolean) {
    if (url.isNullOrBlank()) {
        player.pause()
        player.stop()
        return
    }

    val mediaItem = MediaItem.fromUri(Uri.parse(url))
    val currentUrl = player.currentMediaItem?.localConfiguration?.uri?.toString()
    if (currentUrl != url) {
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    player.playWhenReady = shouldPlay
    if (shouldPlay) {
        player.play()
    } else {
        player.pause()
    }
}
