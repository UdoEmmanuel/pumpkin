package com.pumpkin.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pumpkin.app.data.model.Chat

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val participantIds: List<String>,
    val participantNames: Map<String, String>,
    val createdAt: Long
) {
    // typing is intentionally omitted — it's a live-only Firestore signal,
    // never mirrored into Room (see Chat.typing kdoc).
    fun toModel() = Chat(id, participantIds, participantNames, createdAt = createdAt)

    companion object {
        fun fromModel(chat: Chat) =
            ChatEntity(chat.id, chat.participantIds, chat.participantNames, chat.createdAt)
    }
}
