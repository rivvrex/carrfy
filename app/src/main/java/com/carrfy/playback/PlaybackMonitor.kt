package com.carrfy.playback

import android.content.Context
import android.util.Log
import com.carrfy.spotify.SpotifyRepository
import kotlinx.coroutines.*

/**
 * Global playback monitor that continuously updates player state
 * Runs regardless of which screen is visible
 */
object PlaybackMonitor {
    private const val TAG = "PlaybackMonitor"
    private var monitorJob: Job? = null
    private var scope: CoroutineScope? = null
    private var repo: SpotifyRepository? = null
    private var onTrackChangeListener: ((trackId: String?) -> Unit)? = null
    private var lastTrackId: String? = null

    /**
     * Start monitoring playback state globally
     */
    fun start(context: Context) {
        val isRunning = monitorJob?.isActive ?: false
        if (isRunning) {
            Log.d(TAG, "Monitor already running")
            return
        }

        Log.d(TAG, "Starting playback monitor")
        repo = SpotifyRepository(context)
        scope = CoroutineScope(Dispatchers.Default + Job())

        val newScope = scope
        if (newScope != null) {
            monitorJob = newScope.launch {
                while (isActive) {
                    try {
                        delay(200)
                        val currentRepo = repo
                        if (currentRepo != null) {
                            try {
                                val response = currentRepo.getCurrentlyPlaying()
                                val newTrackId = response.item?.id
                                if (newTrackId != lastTrackId) {
                                    lastTrackId = newTrackId
                                    Log.d(TAG, "Track changed to: $newTrackId")
                                    onTrackChangeListener?.invoke(newTrackId)
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Error checking playback state: ${e.message}")
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Monitor loop error: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Stop monitoring
     */
    fun stop() {
        Log.d(TAG, "Stopping playback monitor")
        monitorJob?.cancel()
        scope?.cancel()
        monitorJob = null
        scope = null
    }

    /**
     * Set listener for track changes
     */
    fun setOnTrackChangeListener(listener: ((trackId: String?) -> Unit)?) {
        onTrackChangeListener = listener
    }
}
