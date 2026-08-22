package com.pumpkin.app.data.remote.api.dto

data class UserDto(val id: String, val email: String, val displayName: String)
data class SyncUserRequest(val displayName: String)
data class UpdateDisplayNameRequest(val displayName: String)
