package com.carrfy.spotify.models

import com.google.gson.annotations.SerializedName

data class DevicesResponse(
    @SerializedName("devices")
    val devices: List<Device>?
)

data class Device(
    @SerializedName("id")
    val id: String?,
    @SerializedName("is_active")
    val isActive: Boolean?,
    @SerializedName("is_private_session")
    val isPrivateSession: Boolean?,
    @SerializedName("is_restricted")
    val isRestricted: Boolean?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("type")
    val type: String?,
    @SerializedName("volume_percent")
    val volumePercent: Int?
) 
 
 
 