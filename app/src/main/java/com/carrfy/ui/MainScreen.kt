package com.carrfy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.carrfy.ui.extractDominantColor
import androidx.compose.ui.platform.LocalContext
import com.carrfy.auth.CarrfyAuthManager
import com.carrfy.spotify.SpotifyRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val navigationManager = remember { NavigationManager() }
    val navigationState = navigationManager.navigationState
    val context = LocalContext.current
    val authManager = remember { CarrfyAuthManager(context) }
    val auth = remember { FirebaseAuth.getInstance() }
    val repo = remember(context) { SpotifyRepository(context) }
    val scope = rememberCoroutineScope()
    
    var dominantImageUrl by remember { mutableStateOf<String?>(null) }
    var targetBgColor by remember { mutableStateOf(Color(0xFF512DA8)) }
    var showProfileModal by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 180),
        label = "bgColor"
    )

    // Check authentication on launch
    LaunchedEffect(Unit) {
        scope.launch {
            val currentUser = authManager.getCurrentUser()
            if (currentUser != null) {
                navigationManager.navigateToMain(currentUser.id, currentUser.displayName)
            } else {
                navigationManager.navigateToLogin()
            }
        }
    }

    // Extract dominant color when image URL changes
    LaunchedEffect(dominantImageUrl) {
        targetBgColor = extractDominantColor(context, dominantImageUrl)
    }

    LaunchedEffect(navigationState.currentScreen, navigationState.selectedDestination) {
        if (navigationState.currentScreen == Screen.Main && navigationState.selectedDestination == SidebarDestination.NOW_PLAYING) {
            runCatching {
                repo.getCurrentlyPlaying()
            }.getOrNull()?.item?.album?.images?.firstOrNull()?.url?.let { url ->
                dominantImageUrl = url
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgColor, bgColor.copy(alpha = 0.7f), Color.Black)
                )
            )
    ) {
        // Show Auth Screens
        when (navigationState.currentScreen) {
            Screen.Login -> {
                LoginScreen(
                    onLoginSuccess = { _ ->
                        scope.launch {
                            val user = authManager.getCurrentUser()
                            if (user != null) {
                                navigationManager.navigateToMain(user.id, user.displayName)
                            }
                        }
                    },
                    onSignupClick = {
                        navigationManager.navigateToSignup()
                    }
                )
            }
            Screen.Signup -> {
                SignupScreen(
                    onSignupSuccess = { _ ->
                        scope.launch {
                            val user = authManager.getCurrentUser()
                            if (user != null) {
                                navigationManager.navigateToMain(user.id, user.displayName)
                            }
                        }
                    },
                    onLoginClick = {
                        navigationManager.navigateToLogin()
                    }
                )
            }
            Screen.Main -> {
                // Main App UI
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Sidebar(
                        selected = navigationState.selectedDestination,
                        userName = navigationState.userName,
                        onSelect = { destination ->
                            navigationManager.selectSidebarDestination(destination)
                        },
                        onProfileClick = {
                            showProfileModal = true
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    ) {
                        when (navigationState.selectedDestination) {
                            SidebarDestination.NOW_PLAYING -> NowPlayingScreen(
                                onDominantImageUrlChange = { dominantImageUrl = it }
                            )
                            SidebarDestination.RECENTS -> RecentsScreen(
                                onDominantImageUrlChange = { dominantImageUrl = it }
                            )
                            SidebarDestination.LIBRARY -> LibraryScreen(
                                onPlaylistClick = { playlistId ->
                                    navigationManager.navigateToPlaylistDetail(playlistId)
                                },
                                onDominantImageUrlChange = { dominantImageUrl = it }
                            )
                            SidebarDestination.RADIO -> RadioScreen(
                                onDominantImageUrlChange = { dominantImageUrl = it }
                            )
                            SidebarDestination.SEARCH -> SearchScreen(
                                onDominantImageUrlChange = { dominantImageUrl = it }
                            )
                        }
                    }
                }
            }
            Screen.PlaylistDetail -> {
                navigationState.playlistId?.let { playlistId ->
                    PlaylistTracksScreen(
                        playlistId = playlistId,
                        onBack = { navigationManager.goBack() },
                        onDominantImageUrlChange = { dominantImageUrl = it }
                    )
                }
            }
            Screen.ArtistDetail -> {
                navigationState.artistId?.let { artistId ->
                    ArtistDetailScreen(
                        artistId = artistId,
                        onBack = { navigationManager.goBack() },
                        onDominantImageUrlChange = { dominantImageUrl = it }
                    )
                }
            }
        }

        // Profile Modal
        if (showProfileModal) {
            ProfileModal(
                userName = navigationState.userName,
                userEmail = "User Email",
                onDismiss = { showProfileModal = false },
                onLogout = {
                    authManager.logoutUser()
                    navigationManager.navigateToLogin()
                    showProfileModal = false
                },
                onImportPlaylist = { playlistUrl, onResult ->
                    scope.launch {
                        try {
                            val result = repo.importPlaylistByUrl(playlistUrl, auth.currentUser?.uid ?: "")
                            result.onSuccess { (playlistId, _) ->
                                if (playlistId.isNotEmpty()) {
                                    // Success - callback with empty error string
                                    onResult("")
                                    // Refresh library - navigate to Library tab
                                    navigationManager.selectSidebarDestination(SidebarDestination.LIBRARY)
                                } else {
                                    onResult("Failed to import playlist")
                                }
                            }
                            result.onFailure { error ->
                                onResult("Import failed: ${error.localizedMessage ?: "Unknown error"}")
                            }
                        } catch (e: Exception) {
                            onResult("Error: ${e.localizedMessage ?: e.message ?: "Unknown error"}")
                        }
                    }
                }
            )
        }
    }
}