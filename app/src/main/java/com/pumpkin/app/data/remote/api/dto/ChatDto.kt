package com.pumpkin.app.data.remote.api.dto

import com.pumpkin.app.data.model.Chat

data class ChatDto(
    val id: String,
    val participantIds: List<String>,
    val participantNames: Map<String, String>,
    val nicknames: Map<String, String> = emptyMap(),
    val createdAt: Long
) {
    fun toModel() = Chat(id, participantIds, participantNames, nicknames, createdAt = createdAt)
}

data class StartChatRequest(val partnerEmail: String)
data class SetNicknameRequest(val targetUserId: String, val nickname: String)
