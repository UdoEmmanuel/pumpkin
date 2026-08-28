package com.pumpkin.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pumpkin.app.data.model.Message

// PRD 4.3 / 4.4: this table is the local ephemeral cache only — rows are
// removed as soon as a delete signal arrives from the server (or the TTL
// sweep fires), never kept as a permanent history.
@Entity(
    tableName = "messages",
    indices = [Index(value = ["chatId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long,
    val deliveredAt: Long?,
    val readAt: Map<String, Long>,
    val exitedAtAfterRead: Map<String, Long>,
    val deletedAt: Long?,
    val replyToMessageId: String? = null,
    val replyToSenderId: String? = null,
    val replyToText: String? = null,
    val reactions: Map<String, String> = emptyMap(),
    val editedAt: Long? = null
) {
    fun toModel() = Message(
        id = id,
        chatId = chatId,
        senderId = senderId,
        text = text,
        sentAt = sentAt,
        deliveredAt = deliveredAt,
        readAt = readAt,
        exitedAtAfterRead = exitedAtAfterRead,
        deletedAt = deletedAt,
        replyToMessageId = replyToMessageId,
        replyToSenderId = replyToSenderId,
        replyToText = replyToText,
        reactions = reactions,
        editedAt = editedAt
    )

    companion object {
        fun fromModel(message: Message) = MessageEntity(
            id = message.id,
            chatId = message.chatId,
            senderId = message.senderId,
            text = message.text,
            sentAt = message.sentAt,
            deliveredAt = message.deliveredAt,
            readAt = message.readAt,
            exitedAtAfterRead = message.exitedAtAfterRead,
            deletedAt = message.deletedAt,
            replyToMessageId = message.replyToMessageId,
            replyToSenderId = message.replyToSenderId,
            replyToText = message.replyToText,
            reactions = message.reactions,
            editedAt = message.editedAt
        )
    }
}
