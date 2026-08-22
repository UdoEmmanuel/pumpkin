package com.pumpkin.app.data.remote.api.dto

import com.pumpkin.app.data.model.Chat

data class ChatDto(
    val id: String,
    val participantIds: List<String>,
    val participantNames: Map<String, String>,
    val createdAt: Long
) {
    fun toModel() = Chat(id, participantIds, participantNames, createdAt = createdAt)
}

data class StartChatRequest(val partnerEmail: String)
