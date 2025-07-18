package com.carrfy.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Global singleton that manages the ExoPlayer instance
 * Ensures playback continues regardless of which tab is active
 */
object PlayerManager {
    private const val TAG = "PlayerManager"
    private var player: ExoPlayer? = null
    private var context: Context? = null
    private var onTrackCompletedListener: (() -> Unit)? = null
    private var playerEventListener: Player.Listener? = null
    
    /**
     * Get or create the global ExoPlayer instance
     */
    fun getPlayer(appContext: Context): ExoPlayer {
        if (player == null) {
            context = appContext.applicationContext
            val newPlayer = ExoPlayer.Builder(appContext).build()
            newPlayer.setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            
            // Set up listener for track completion
            playerEventListener = object : Player.Listener {
                override fun onPlaybackStateChanged(@Player.State state: Int) {
                    when (state) {
                        Player.STATE_ENDED -> {
                            Log.d(TAG, "Track ended, triggering completion")
                            onTrackCompletedListener?.invoke()
                        }
                        Player.STATE_READY -> {
                            Log.d(TAG, "Player ready")
                        }
                        Player.STATE_BUFFERING -> {
                            Log.d(TAG, "Player buffering")
                        }
                    }
                }
            }
            newPlayer.addListener(playerEventListener!!)
            
            Log.d(TAG, "ExoPlayer instance created")
            player = newPlayer
        }
        return player!!
    }
    
    /**
     * Set a callback for when track completes
     */
    fun setOnTrackCompletedListener(listener: (() -> Unit)?) {
        onTrackCompletedListener = listener
    }
    
    /**
     * Check if player is initialized
     */
    fun isInitialized(): Boolean = player != null
    
    /**
     * Get the current player or null
     */
    fun getPlayerOrNull(): ExoPlayer? = player
    
    /**
     * Release the player resource
     * Call this only when app is truly shutting down
     */
    fun release() {
        player?.let {
            try {
                playerEventListener?.let { listener ->
                    it.removeListener(listener)
                }
                it.pause()
                it.stop()
                it.release()
                Log.d(TAG, "ExoPlayer released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing player: ${e.message}")
            }
        }
        player = null
        playerEventListener = null
    }
    
    /**
     * Reset player state without releasing
     */
    fun reset() {
        player?.let {
            try {
                it.pause()
                it.stop()
                it.clearMediaItems()
                Log.d(TAG, "Player reset")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting player: ${e.message}")
            }
        }
    }
}
