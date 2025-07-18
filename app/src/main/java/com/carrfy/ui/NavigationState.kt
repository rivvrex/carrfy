package com.carrfy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed class Screen {
    object Main : Screen()
    object Login : Screen()
    object Signup : Screen()
    object PlaylistDetail : Screen()
    object ArtistDetail : Screen()
}

data class NavigationState(
    val currentScreen: Screen = Screen.Main,
    val selectedDestination: SidebarDestination = SidebarDestination.NOW_PLAYING,
    val playlistId: String? = null,
    val artistId: String? = null,
    val userId: String? = null,
    val userName: String = "User"
)

class NavigationManager {
    var navigationState by mutableStateOf(NavigationState())
        private set
    
    fun navigateToMain(userId: String, userName: String) {
        navigationState = NavigationState(
            currentScreen = Screen.Main,
            userId = userId,
            userName = userName
        )
    }

    fun navigateToLogin() {
        navigationState = NavigationState(currentScreen = Screen.Login)
    }

    fun navigateToSignup() {
        navigationState = NavigationState(currentScreen = Screen.Signup)
    }
    
    fun navigateToPlaylistDetail(playlistId: String) {
        navigationState = navigationState.copy(
            currentScreen = Screen.PlaylistDetail,
            playlistId = playlistId
        )
    }
    
    fun navigateToArtistDetail(artistId: String) {
        navigationState = navigationState.copy(
            currentScreen = Screen.ArtistDetail,
            artistId = artistId
        )
    }
    
    fun selectSidebarDestination(destination: SidebarDestination) {
        navigationState = navigationState.copy(
            currentScreen = Screen.Main,
            selectedDestination = destination
        )
    }
    
    fun goBack() {
        navigationState = NavigationState(currentScreen = Screen.Main)
    }

    fun logout() {
        navigationState = NavigationState(currentScreen = Screen.Login, userId = null, userName = "")
    }
} 