package com.evacsense.auth

import com.google.gson.annotations.SerializedName

/**
 * Data structure representing a user account within the EvacSense system.
 */
data class User(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("role") val role: String,
    @SerializedName("department") val department: String?,
    @SerializedName("hasPhoto") val hasPhoto: Boolean?
)
