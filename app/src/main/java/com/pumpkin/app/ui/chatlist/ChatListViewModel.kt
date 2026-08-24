package com.pumpkin.app.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpkin.app.data.model.Chat
import com.pumpkin.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A chat row plus how many of the partner's messages this user hasn't read yet, and whether an unsent draft exists. */
data class ChatListItem(val chat: Chat, val unreadCount: Int, val hasDraft: Boolean)

class ChatListViewModel(
    private val repository: ChatRepository,
    val currentUserId: String
) : ViewModel() {
    // PRD-adjacent privacy choice (per user request): the list never shows
    // message content, only whether there's something new — so unread count
    // is the only thing derived from message bodies here, never the text.
    val chatItems: StateFlow<List<ChatListItem>> =
        combine(
            repository.observeChats(),
            repository.observeAllMessages(),
            repository.observeAllDrafts()
        ) { chats, messages, drafts ->
            chats.map { chat ->
                val unread = messages.count {
                    it.chatId == chat.id &&
                        it.senderId != currentUserId &&
                        !it.readAt.containsKey(currentUserId)
                }
                ChatListItem(chat, unread, hasDraft = !drafts[chat.id].isNullOrBlank())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _newChatError = MutableStateFlow<String?>(null)
    val newChatError: StateFlow<String?> = _newChatError.asStateFlow()

    init {
        // Mirrors every chat this user is a participant in from Firestore
        // into Room — otherwise a chat the *other* participant started would
        // never show up here.
        repository.startChatListSync(currentUserId)
    }

    /** PRD 3 pairing step: look a partner up by email and start (or reuse) a 1:1 chat with them. */
    fun startChat(partnerEmail: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            repository.startChatWithEmail(currentUserId, partnerEmail)
                .onSuccess { chat ->
                    _newChatError.value = null
                    onCreated(chat.id)
                }
                .onFailure { error ->
                    _newChatError.value = error.message ?: "Couldn't start chat"
                }
        }
    }

    fun clearNewChatError() {
        _newChatError.value = null
    }

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
                .onSuccess { _deleteError.value = null }
                .onFailure { _deleteError.value = it.message ?: "Couldn't delete chat" }
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }
}
