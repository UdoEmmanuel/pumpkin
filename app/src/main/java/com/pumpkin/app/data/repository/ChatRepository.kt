package com.pumpkin.app.data.repository

import com.pumpkin.app.data.local.ChatDao
import com.pumpkin.app.data.local.ChatEntity
import com.pumpkin.app.data.local.DraftDao
import com.pumpkin.app.data.local.DraftEntity
import com.pumpkin.app.data.local.MessageDao
import com.pumpkin.app.data.local.MessageEntity
import com.pumpkin.app.data.model.Chat
import com.pumpkin.app.data.model.Message
import com.pumpkin.app.data.remote.NetworkModule
import com.pumpkin.app.data.remote.api.ChatApi
import com.pumpkin.app.data.remote.api.dto.SetNicknameRequest
import com.pumpkin.app.data.remote.api.dto.StartChatRequest
import com.pumpkin.app.data.remote.socket.PresenceUpdate
import com.pumpkin.app.data.remote.socket.PumpkinSocket
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Single point of truth for chat/message reads and writes. As of the
 * server/ migration, Firestore is no longer involved here at all — the
 * self-hosted backend (REST for one-shot reads, Socket.IO for real-time) is
 * the source of truth, Room is still the local mirror ViewModels observe,
 * same as before. Firebase Auth and the Firestore `users` collection are
 * untouched (see AuthRepository) — this migration was scoped to
 * chats/messages only, since those were what exhausted the Firestore free
 * tier's daily write quota during testing.
 */
class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val draftDao: DraftDao,
    private val api: ChatApi = NetworkModule.chatApi,
    private val socket: PumpkinSocket = NetworkModule.socket
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var globalMirrorStarted = false
    private var lastKnownUserId: String? = null

    init {
        // Presence is "has an open socket," and Android does NOT tear down
        // that socket just because the app was backgrounded — the process
        // (and its TCP connection) can keep running for a while. Without
        // this, a partner who backgrounds the app stays "online" until the
        // OS eventually kills the process or the connection times out,
        // instead of flipping to offline right away. Manually disconnecting
        // on background and reconnecting on foreground makes presence
        // actually track "is the app in front of them," matching what
        // "online" implies to a user glancing at the chat header.
        //
        // The cost of that disconnect: any message sent to this user while
        // their socket was down never arrives as a live 'message:new' event
        // — which was the only thing that (a) marked it delivered and (b)
        // put it in Room so the chat list's unread count would notice it.
        // A REST resync on every reconnect (not just the very first connect)
        // closes that gap.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                socket.disconnect()
            }

            override fun onStart(owner: LifecycleOwner) {
                repositoryScope.launch {
                    socket.connect()
                    lastKnownUserId?.let { resyncFromServer(it) }
                }
            }
        })
    }

    fun observeChats(): Flow<List<Chat>> =
        chatDao.observeChats().map { list -> list.map { it.toModel() } }

    suspend fun getChat(chatId: String): Chat? = chatDao.getChat(chatId)?.toModel()

    fun observeChat(chatId: String): Flow<Chat?> =
        chatDao.observeChat(chatId).map { it?.toModel() }

    fun observeMessages(chatId: String): Flow<List<Message>> =
        messageDao.observeMessages(chatId).map { list -> list.map { it.toModel() } }

    fun observeAllMessages(): Flow<List<Message>> =
        messageDao.observeAllMessages().map { list -> list.map { it.toModel() } }

    /**
     * Connects the socket, fetches the current chat list once over REST to
     * seed Room, and (once per process) starts mirroring every subsequent
     * real-time event into Room regardless of which chat screen is open —
     * the equivalent of the old per-chat Firestore snapshot listeners, but
     * a single global subscription since there's now one shared socket
     * connection carrying events for every chat the user is in.
     */
    fun startChatListSync(currentUserId: String) {
        lastKnownUserId = currentUserId
        repositoryScope.launch {
            socket.connect()
            startGlobalMirror(currentUserId)
            resyncFromServer(currentUserId)
        }
    }

    /**
     * Full REST resync of every chat's metadata AND messages — not just the
     * chat list. Anything that arrived while the socket was disconnected
     * (see the ON_STOP/ON_START observer above) only shows up this way:
     * live socket events can't retroactively deliver what was missed while
     * offline. Also backfills deliveredAt for anything that came in that way,
     * same as the live path in startGlobalMirror does.
     */
    private suspend fun resyncFromServer(currentUserId: String) {
        runCatching { api.getChats() }
            .onSuccess { chats ->
                chats.forEach { chatDto ->
                    chatDao.upsert(ChatEntity.fromModel(chatDto.toModel()))
                    runCatching { api.getMessages(chatDto.id) }
                        .onSuccess { messages ->
                            messageDao.replaceAllForChat(chatDto.id, messages.map { MessageEntity.fromModel(it.toModel()) })
                            messages
                                .filter { it.senderId != currentUserId && it.deliveredAt == null }
                                .forEach { runCatching { socket.markDelivered(chatDto.id, it.id) } }
                        }
                }
            }
    }

    private fun startGlobalMirror(currentUserId: String) {
        if (globalMirrorStarted) return
        globalMirrorStarted = true
        repositoryScope.launch {
            socket.messageNew.collect { dto ->
                messageDao.upsert(MessageEntity.fromModel(dto.toModel()))
                // Auto-marks delivered the moment a new message from someone
                // else arrives — deliveredAt was never wired up to anything
                // in the original Firestore version until late in that
                // implementation; doing it here from day one this time.
                if (dto.senderId != currentUserId && dto.deliveredAt == null) {
                    runCatching { socket.markDelivered(dto.chatId, dto.id) }
                }
            }
        }
        repositoryScope.launch {
            socket.messageUpdated.collect { messageDao.upsert(MessageEntity.fromModel(it.toModel())) }
        }
        repositoryScope.launch {
            socket.messageDeleted.collect { messageDao.delete(it.messageId) }
        }
        repositoryScope.launch {
            socket.chatNew.collect { chatDao.upsert(ChatEntity.fromModel(it.toModel())) }
        }
        repositoryScope.launch {
            socket.chatUpdated.collect { chatDao.upsert(ChatEntity.fromModel(it.toModel())) }
        }
        repositoryScope.launch {
            socket.chatDeleted.collect { deleteChatLocally(it.chatId) }
        }
    }

    /**
     * Joins this chat's real-time room and reconciles Room with the server's
     * current history over REST. This must be a full reconcile (clear then
     * re-fill), not just upsert-what-we-got: upsert-only left "ghost"
     * messages behind forever whenever a message was deleted (auto-delete or
     * otherwise) while a device was offline/disconnected and so never
     * received the 'message:deleted' push for it — the local copy had no
     * way to ever find out it should be gone.
     */
    fun startMessageSync(chatId: String, currentUserId: String) {
        socket.joinChat(chatId)
        repositoryScope.launch {
            runCatching { api.getMessages(chatId) }
                .onSuccess { messages ->
                    messageDao.replaceAllForChat(chatId, messages.map { MessageEntity.fromModel(it.toModel()) })
                    messages
                        .filter { it.senderId != currentUserId && it.deliveredAt == null }
                        .forEach { runCatching { socket.markDelivered(chatId, it.id) } }
                }
        }
    }

    /**
     * senderId is accepted (rather than reading it off the socket) purely to
     * keep this method's signature stable for ChatViewModel — the server
     * ignores any client-supplied identity and always uses the uid it
     * verified from the auth token, so a caller can't spoof another sender.
     */
    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        text: String,
        replyToMessageId: String? = null,
        replyToSenderId: String? = null,
        replyToText: String? = null
    ) {
        val message = socket.sendMessage(chatId, text, replyToMessageId, replyToSenderId, replyToText)
            .getOrElseNetworkError()
        messageDao.upsert(MessageEntity.fromModel(message.toModel()))
    }

    /** Empty emoji clears your reaction; the same emoji again also clears it (toggle) — see server/src/socket/index.js. */
    suspend fun reactToMessage(chatId: String, messageId: String, emoji: String) {
        val message = socket.react(chatId, messageId, emoji).getOrElseNetworkError()
        messageDao.upsert(MessageEntity.fromModel(message.toModel()))
    }

    /** Only the original sender can edit, and only while the message still exists — see server/src/socket/index.js. */
    suspend fun editMessage(chatId: String, messageId: String, text: String): Result<Unit> {
        return try {
            val message = socket.editMessage(chatId, messageId, text).getOrElseNetworkError()
            messageDao.upsert(MessageEntity.fromModel(message.toModel()))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** PRD 4.4: called when the recipient opens and reads a message. */
    suspend fun markRead(chatId: String, messageId: String, readerId: String) {
        socket.markRead(chatId, messageId).getOrElseNetworkError()
    }

    /** PRD 4.4: called when the recipient navigates away from the chat screen after reading. */
    suspend fun markExitedAfterRead(chatId: String, messageId: String, readerId: String) {
        socket.markExited(chatId, messageId).getOrElseNetworkError()
    }

    /**
     * Fire-and-forget variant that runs on [repositoryScope] instead of the
     * caller's scope — this specifically exists for the moment a chat
     * screen is left, which is exactly when ChatViewModel.viewModelScope
     * gets cancelled (the backstack entry is popped), so a suspend call on
     * that scope would be cut off mid-request before the server ever saw it.
     */
    fun markExitedAfterReadAsync(chatId: String, messageId: String, readerId: String) {
        repositoryScope.launch { runCatching { markExitedAfterRead(chatId, messageId, readerId) } }
    }

    suspend fun setTyping(chatId: String, userId: String, isTyping: Boolean) {
        socket.setTyping(chatId, isTyping)
    }

    fun setTypingAsync(chatId: String, userId: String, isTyping: Boolean) {
        repositoryScope.launch { setTyping(chatId, userId, isTyping) }
    }

    /** Emits a synthetic "last typed at" timestamp so ChatViewModel's existing 3s-expiry logic is unchanged. */
    fun observeTypingTimestamp(chatId: String, otherUserId: String): Flow<Long> =
        socket.typingUpdate
            .filter { it.chatId == chatId && it.userId == otherUserId }
            .map { if (it.isTyping) System.currentTimeMillis() else 0L }

    suspend fun getPresence(userId: String): PresenceUpdate = socket.getPresence(userId)

    fun observePresenceUpdates(): Flow<PresenceUpdate> = socket.presenceUpdate

    /**
     * PRD 3 pairing step: look the partner up by email and start (or reuse)
     * a 1:1 chat with them. The server does the lookup + create-or-reuse
     * atomically now (see server/src/routes/chats.js) — this used to be two
     * separate Firestore round-trips from the client.
     */
    suspend fun startChatWithEmail(currentUserId: String, partnerEmail: String): Result<Chat> {
        return try {
            val dto = api.startChat(StartChatRequest(partnerEmail))
            val chat = dto.toModel()
            chatDao.upsert(ChatEntity.fromModel(chat))
            Result.success(chat)
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(e.errorMessage() ?: "Couldn't start chat"))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Couldn't reach the server — is it running?"))
        }
    }

    /**
     * Deletes the chat for both participants (see server/src/routes/chats.js
     * — there's no per-user "delete for me" concept here). Removes the
     * server copy first; the local mirror only follows once that's
     * confirmed, so a failed request (offline, etc.) doesn't make the chat
     * vanish locally while it's still sitting on the server.
     */
    suspend fun deleteChat(chatId: String): Result<Unit> {
        return try {
            api.deleteChat(chatId)
            deleteChatLocally(chatId)
            Result.success(Unit)
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(e.errorMessage() ?: "Couldn't delete chat"))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Couldn't reach the server — is it running?"))
        }
    }

    /** Empty nickname clears it. Sets what [targetUserId] is called within [chatId] — see server/src/models/Chat.js. */
    suspend fun setNickname(chatId: String, targetUserId: String, nickname: String): Result<Unit> {
        return try {
            val dto = api.setNickname(chatId, SetNicknameRequest(targetUserId, nickname))
            chatDao.upsert(ChatEntity.fromModel(dto.toModel()))
            Result.success(Unit)
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(e.errorMessage() ?: "Couldn't set nickname"))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Couldn't reach the server — is it running?"))
        }
    }

    private suspend fun deleteChatLocally(chatId: String) {
        messageDao.deleteAllForChat(chatId)
        draftDao.clear(chatId)
        chatDao.delete(chatId)
    }

    fun observeDraft(chatId: String): Flow<String?> =
        draftDao.observeAll().map { it.firstOrNull { d -> d.chatId == chatId }?.text }

    suspend fun getDraft(chatId: String): String? = draftDao.get(chatId)?.text

    /** Empty/blank text clears the draft rather than storing an empty row. */
    suspend fun saveDraft(chatId: String, text: String) {
        if (text.isBlank()) draftDao.clear(chatId) else draftDao.upsert(DraftEntity(chatId, text))
    }

    fun saveDraftAsync(chatId: String, text: String) {
        repositoryScope.launch { saveDraft(chatId, text) }
    }

    /** chatId -> draft text, for the chat list's "Draft" indicator. */
    fun observeAllDrafts(): Flow<Map<String, String>> =
        draftDao.observeAll().map { list -> list.associate { it.chatId to it.text } }

    private fun HttpException.errorMessage(): String? =
        runCatching {
            val body = response()?.errorBody()?.string() ?: return null
            org.json.JSONObject(body).optString("error").ifBlank { null }
        }.getOrNull()

    private fun <T> Result<T>.getOrElseNetworkError(): T =
        getOrElse { throw IllegalStateException(it.message ?: "Request failed", it) }
}
