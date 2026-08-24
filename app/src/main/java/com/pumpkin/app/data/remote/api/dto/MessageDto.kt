package com.pumpkin.app.data.remote.api.dto

import com.pumpkin.app.data.model.Message

data class MessageDto(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long,
    val deliveredAt: Long?,
    val readAt: Map<String, Long>,
    val exitedAtAfterRead: Map<String, Long>,
    val replyToMessageId: String? = null,
    val replyToSenderId: String? = null,
    val replyToText: String? = null
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
        replyToMessageId = replyToMessageId,
        replyToSenderId = replyToSenderId,
        replyToText = replyToText
    )
}
