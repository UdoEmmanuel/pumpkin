package com.pumpkin.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpkin.app.data.model.Message
import com.pumpkin.app.data.remote.socket.PresenceUpdate
import com.pumpkin.app.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TYPING_BROADCAST_THROTTLE_MS = 1_200L
private const val TYPING_EXPIRY_MS = 3_000L

class ChatViewModel(
    private val repository: ChatRepository,
    private val chatId: String,
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

    init {
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

    fun send(text: String) {
        if (text.isBlank()) return
        // Optimistic clear, same as before this owned the field — plus the
        // draft it was backed by, so a sent message doesn't leave a stale
        // "Draft" indicator behind on the chat list.
        _input.value = ""
        repository.saveDraftAsync(chatId, "")
        viewModelScope.launch {
            try {
                repository.sendMessage(chatId, currentUserId, text)
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

    /** Called on every keystroke in the input field — typing broadcasts are throttled, draft saves are not (cheap local writes). */
    fun onInputChanged(text: String) {
        _input.value = text
        repository.saveDraftAsync(chatId, text)

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
        super.onCleared()
    }
}

private fun tickerFlow() = flow {
    while (true) {
        emit(Unit)
        delay(1_000)
    }
}
