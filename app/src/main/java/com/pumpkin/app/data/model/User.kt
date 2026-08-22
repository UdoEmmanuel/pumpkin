package com.pumpkin.app.data.model

// PRD 7: no account discovery / social graph — participants are provisioned
// manually (hardcoded pairing or a simple invite code), not looked up publicly.
data class User(
    val id: String = "",
    val displayName: String = "",
    // Used to look partners up by email when starting a new chat (PRD 3).
    val email: String = "",
    val createdAt: Long = 0L
)
