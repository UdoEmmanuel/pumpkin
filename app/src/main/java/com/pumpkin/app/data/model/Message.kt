package com.pumpkin.app.data.model

enum class MessageStatus { SENT, DELIVERED, READ }

// PRD 7. readAt / exitedAtAfterRead are keyed by recipient userId — modeled as
// maps (rather than two flat fields) so a future move past 1:1 chats
// (PRD 10, "support for more than one contact") doesn't require a schema change.
data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    val sentAt: Long = 0L,
    val deliveredAt: Long? = null,
    val readAt: Map<String, Long> = emptyMap(),
    val exitedAtAfterRead: Map<String, Long> = emptyMap(),
    val deletedAt: Long? = null,
    // Swipe-to-reply — snapshotted at send time (not just an id reference)
    // so the quote still makes sense even if the original message
    // auto-deletes later (PRD 4.4).
    val replyToMessageId: String? = null,
    val replyToSenderId: String? = null,
    val replyToText: String? = null,
    // uid -> emoji, one reaction per user per message.
    val reactions: Map<String, String> = emptyMap(),
    val editedAt: Long? = null,
    // Voice notes — stored inline as base64 on the message itself (see
    // server/src/models/Message.js) rather than in external object storage,
    // so a deleted message takes its audio with it automatically.
    val type: String = "text",
    val audioData: String? = null,
    val audioDurationMs: Long? = null
) {
    val isVoiceNote: Boolean get() = type == "voice"

    fun statusFor(otherParticipantId: String): MessageStatus = when {
        readAt.containsKey(otherParticipantId) -> MessageStatus.READ
        deliveredAt != null -> MessageStatus.DELIVERED
        else -> MessageStatus.SENT
    }

    // PRD 4.4 auto-delete eligibility is now decided server-side (see
    // server/src/messageEligibility.js) — this client only ever reports
    // read/exit events and reacts to the server's "message:deleted" push, it
    // no longer computes the decision itself.
}
