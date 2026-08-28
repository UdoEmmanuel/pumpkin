package com.pumpkin.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpkin.app.data.model.Message
import com.pumpkin.app.data.remote.CurrentChatTracker
import com.pumpkin.app.data.remote.socket.PresenceUpdate
import com.pumpkin.app.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TYPING_BROADCAST_THROTTLE_MS = 1_200L
private const val TYPING_EXPIRY_MS = 3_000L

class ChatViewModel(
    private val repository: ChatRepository,
    val chatId: String,
    val currentUserId: String
) : ViewModel() {

    val messages: StateFlow<List<Message>> = repository.observeMessages(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Live, not one-shot: a one-shot read right as this screen opens can race
    // the chat-list Firestore listener repopulating Room after a database
    // reset (e.g. right after an app update that bumped the schema version)
    // — if Room was momentarily empty at that exact instant, a one-shot read
    // would return null forever, permanently stuck showing a "?" avatar with
    // no name, and (since isPartnerTyping and the read-receipt ticks below
    // both depend on otherParticipantId being non-null) no typing indicator
    // and no checkmarks either.
    private val chatFlow = repository.observeChat(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val otherParticipantId: StateFlow<String?> = chatFlow
        .map { it?.otherParticipantId(currentUserId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val otherParticipantName: StateFlow<String?> = chatFlow
        .map { it?.otherParticipantName(currentUserId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    // Owned here (not as Compose-local state in ChatScreen) so it survives
    // navigating away and back, and so it can be seeded from — and persisted
    // as — a draft (see repository.getDraft/saveDraft) that outlives this
    // ViewModel too, i.e. survives the app being closed entirely.
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    // Re-subscribes to the typing timestamp whenever otherParticipantId
    // resolves, then re-evaluates recency every tick so the indicator clears
    // itself ~3s after the partner stops typing even without a new Firestore
    // write announcing that (Firestore listeners only fire on writes, not on
    // time passing).
    val isPartnerTyping: StateFlow<Boolean> = channelFlow {
        var lastKnownTimestamp = 0L
        launch {
            otherParticipantId.collect { otherId ->
                if (otherId != null) {
                    repository.observeTypingTimestamp(chatId, otherId).collect {
                        lastKnownTimestamp = it
                    }
                }
            }
        }
        launch {
            tickerFlow().collect {
                send(
                    lastKnownTimestamp > 0 &&
                        System.currentTimeMillis() - lastKnownTimestamp < TYPING_EXPIRY_MS
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var lastTypingBroadcastAt = 0L

    private val _partnerPresence = MutableStateFlow<PresenceUpdate?>(null)
    val partnerPresence: StateFlow<PresenceUpdate?> = _partnerPresence.asStateFlow()

    // The raw nickname (not falling back to the real name, unlike
    // otherParticipantName) — null means "no nickname set", which is what
    // the nickname-editing dialog needs to show an empty field rather than
    // pre-filling it with the partner's real name.
    val currentNickname: StateFlow<String?> = combine(chatFlow, otherParticipantId) { chat, otherId ->
        if (chat != null && otherId != null) chat.nicknames[otherId] else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The nickname the PARTNER set for ME in this chat — synced the same way
    // as currentNickname (both live on the shared Chat.nicknames map, see
    // server/src/models/Chat.js), just keyed by my own uid instead of
    // theirs. Surfaced in ChatScreen so a nickname set for you is actually
    // visible on your side, not just the setter's.
    val myNickname: StateFlow<String?> = chatFlow
        .map { it?.nicknames?.get(currentUserId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _nicknameError = MutableStateFlow<String?>(null)
    val nicknameError: StateFlow<String?> = _nicknameError.asStateFlow()

    fun setNickname(nickname: String) {
        val otherId = otherParticipantId.value ?: return
        viewModelScope.launch {
            repository.setNickname(chatId, otherId, nickname)
                .onSuccess { _nicknameError.value = null }
                .onFailure { _nicknameError.value = it.message ?: "Couldn't set nickname" }
        }
    }

    fun clearNicknameError() {
        _nicknameError.value = null
    }

    private val _replyingTo = MutableStateFlow<Message?>(null)
    val replyingTo: StateFlow<Message?> = _replyingTo.asStateFlow()

    /** Called from the swipe-to-reply gesture on a message bubble. */
    fun startReply(message: Message) {
        _replyingTo.value = message
    }

    fun cancelReply() {
        _replyingTo.value = null
    }

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage.asStateFlow()

    /** Called from a message bubble's long-press menu (own messages only — see server/src/socket/index.js). */
    fun startEdit(message: Message) {
        _editingMessage.value = message
        _input.value = message.text
    }

    fun cancelEdit() {
        _editingMessage.value = null
        _input.value = ""
    }

    /** Routes to send() or the in-flight edit depending on whether one's active — bound to the same send button. */
    fun onSendClicked(text: String) {
        val editing = _editingMessage.value
        if (editing != null) submitEdit(editing.id, text) else send(text)
    }

    private fun submitEdit(messageId: String, text: String) {
        if (text.isBlank()) return
        _input.value = ""
        _editingMessage.value = null
        viewModelScope.launch {
            repository.editMessage(chatId, messageId, text)
                .onFailure { _sendError.value = it.message ?: "Couldn't edit message" }
        }
    }

    init {
        // Tells PumpkinMessagingService "the user is actually looking at
        // this chat right now" — see CurrentChatTracker kdoc for why plain
        // app-foreground state isn't specific enough on its own.
        CurrentChatTracker.openChatId = chatId

        repository.startMessageSync(chatId, currentUserId)

        viewModelScope.launch {
            _input.value = repository.getDraft(chatId) ?: ""
        }

        // One-shot fetch of the partner's current status the moment we know
        // who they are, then switch to live updates for as long as we're
        // typing/observing this screen.
        viewModelScope.launch {
            otherParticipantId.collect { otherId ->
                if (otherId != null) {
                    _partnerPresence.value = repository.getPresence(otherId)
                }
            }
        }
        viewModelScope.launch {
            repository.observePresenceUpdates().collect { update ->
                if (update.userId == otherParticipantId.value) {
                    _partnerPresence.value = update
                }
            }
        }
    }

    /** Called once a press-and-hold recording (see ChatScreen's mic button) finishes. */
    fun sendVoiceNote(base64Audio: String, durationMs: Long) {
        val replyTo = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            try {
                repository.sendMessage(
                    chatId, currentUserId, text = "",
                    replyToMessageId = replyTo?.id,
                    replyToSenderId = replyTo?.senderId,
                    replyToText = replyTo?.text,
                    type = "voice",
                    audioData = base64Audio,
                    audioDurationMs = durationMs
                )
                _sendError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Couldn't send voice note"
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        val replyTo = _replyingTo.value
        // Optimistic clear, same as before this owned the field — plus the
        // draft it was backed by, so a sent message doesn't leave a stale
        // "Draft" indicator behind on the chat list.
        _input.value = ""
        _replyingTo.value = null
        repository.saveDraftAsync(chatId, "")
        viewModelScope.launch {
            try {
                repository.sendMessage(
                    chatId, currentUserId, text,
                    replyToMessageId = replyTo?.id,
                    replyToSenderId = replyTo?.senderId,
                    replyToText = replyTo?.text
                )
                _sendError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PumpkinSend", "sendMessage failed for chat=$chatId", e)
                _sendError.value = e.javaClass.simpleName + ": " + (e.message ?: "unknown error")
            }
        }
        broadcastTyping(isTyping = false, force = true)
    }

    /** Called from a message bubble's reaction picker. Same emoji tapped again clears it. */
    fun onReact(messageId: String, emoji: String) {
        viewModelScope.launch {
            runCatching { repository.reactToMessage(chatId, messageId, emoji) }
        }
    }

    /** Called on every keystroke in the input field — typing broadcasts are throttled, draft saves are not (cheap local writes). */
    fun onInputChanged(text: String) {
        _input.value = text
        // Don't let text being edited into an existing message masquerade as
        // a draft of a new one — if editing gets cancelled, that would
        // otherwise leave the edited text sitting there as a stale draft.
        if (_editingMessage.value == null) {
            repository.saveDraftAsync(chatId, text)
        }

        val now = System.currentTimeMillis()
        if (text.isBlank()) {
            broadcastTyping(isTyping = false, force = true)
        } else if (now - lastTypingBroadcastAt > TYPING_BROADCAST_THROTTLE_MS) {
            broadcastTyping(isTyping = true, force = false)
        }
    }

    private fun broadcastTyping(isTyping: Boolean, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastTypingBroadcastAt <= TYPING_BROADCAST_THROTTLE_MS) return
        lastTypingBroadcastAt = now
        viewModelScope.launch {
            try {
                repository.setTyping(chatId, currentUserId, isTyping)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort — not worth surfacing a typing-indicator failure to the user.
            }
        }
    }

    /** PRD 4.4: call when a message becomes visible on screen. */
    fun onMessageRead(messageId: String) {
        viewModelScope.launch {
            repository.markRead(chatId, messageId, currentUserId)
        }
    }

    /**
     * PRD 4.4: call once, when the screen truly leaves composition (not on
     * every message list update — see the ChatScreen effect for why that
     * distinction matters). Routed through the repository's own scope since
     * this fires right as this ViewModel is being cleared.
     */
    fun onExitAfterRead(messageId: String) {
        repository.markExitedAfterReadAsync(chatId, messageId, currentUserId)
    }

    override fun onCleared() {
        repository.setTypingAsync(chatId, currentUserId, isTyping = false)
        if (CurrentChatTracker.openChatId == chatId) CurrentChatTracker.openChatId = null
        super.onCleared()
    }
}

private fun tickerFlow() = flow {
    while (true) {
        emit(Unit)
        delay(1_000)
    }
}
