package com.pumpkin.app.data.model

// PRD 7 / 3: 1:1 only for this phase — participantIds always has exactly 2 entries.
data class Chat(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    // Denormalized uid -> displayName snapshot taken at chat-creation time, so
    // the chat list can show a partner's name without an extra lookup per row.
    val participantNames: Map<String, String> = emptyMap(),
    // uid -> nickname set for that uid within THIS chat. Checked ahead of
    // participantNames wherever a name is displayed — see
    // otherParticipantName — but never overwrites it: participantNames still
    // holds the real name for anywhere a nickname isn't in play.
    val nicknames: Map<String, String> = emptyMap(),
    // uid -> last-typed-at epoch millis (0 = not typing). Live-only signal,
    // not mirrored into Room — see ChatRepository.observeTypingTimestamp.
    val typing: Map<String, Long> = emptyMap(),
    val createdAt: Long = 0L
) {
    fun otherParticipantId(currentUserId: String): String? =
        participantIds.firstOrNull { it != currentUserId }

    fun otherParticipantName(currentUserId: String): String? =
        otherParticipantId(currentUserId)?.let { nicknames[it] ?: participantNames[it] }
}
