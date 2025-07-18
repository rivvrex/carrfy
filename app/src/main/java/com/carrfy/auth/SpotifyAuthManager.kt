package com.carrfy.auth

import android.util.Base64
import android.util.Log
import com.carrfy.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*

/**
 * Manages Spotify API authentication using Client Credentials flow
 */
object SpotifyAuthManager {
    private const val TAG = "SpotifyAuthManager"
    private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
    
    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0
    
    /**
     * Get a valid Spotify API access token, using cached token if available and not expired
     */
    suspend fun getAccessToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Return cached token if valid
                if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt) {
                    Log.d(TAG, "Using cached access token")
                    return@withContext cachedToken
                }
                
                // Request new token
                Log.d(TAG, "Requesting new Spotify access token")
                val clientId = BuildConfig.SPOTIFY_CLIENT_ID
                val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET
                
                if (clientId == "NOT_SET" || clientSecret == "NOT_SET") {
                    Log.e(TAG, "Spotify credentials not set in local.properties")
                    return@withContext null
                }
                
                // Create Basic Auth header
                val credentials = "$clientId:$clientSecret"
                val encodedCredentials = Base64.encodeToString(
                    credentials.toByteArray(),
                    Base64.NO_WRAP
                )
                
                // Make POST request
                val connection = (URL(TOKEN_ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Basic $encodedCredentials")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connectTimeout = 5000
                    readTimeout = 5000
                    doOutput = true
                }
                
                // Send body
                val body = "grant_type=client_credentials"
                connection.outputStream.bufferedWriter().use {
                    it.write(body)
                    it.flush()
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Token endpoint response code: $responseCode")
                
                if (responseCode !in 200..299) {
                    Log.e(TAG, "Failed to get access token: $responseCode")
                    return@withContext null
                }
                
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Token response length: ${response.length}")
                
                val jsonObj = JsonParser.parseString(response).asJsonObject
                val token = jsonObj.get("access_token")?.asString
                val expiresIn = jsonObj.get("expires_in")?.asInt ?: 3600
                
                if (token != null) {
                    cachedToken = token
                    tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L) - 60000 // Refresh 1 min before expiry
                    Log.d(TAG, "Successfully obtained access token, expires in $expiresIn seconds")
                    token
                } else {
                    Log.e(TAG, "No access_token in response")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting access token: ${e.message}", e)
                null
            }
        }
    }
}
