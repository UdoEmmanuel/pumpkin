package com.pumpkin.app.data.remote.socket

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.pumpkin.app.data.remote.ServerConfig
import com.pumpkin.app.data.remote.api.dto.ChatDto
import com.pumpkin.app.data.remote.api.dto.MessageDto
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

data class TypingUpdate(val chatId: String, val userId: String, val isTyping: Boolean)
data class DeletedMessage(val chatId: String, val messageId: String)
data class DeletedChat(val chatId: String)
data class PresenceUpdate(val userId: String, val online: Boolean, val lastSeenAt: Long?)

/**
 * Thin wrapper around socket.io-client turning its callback-based API into
 * Kotlin Flows/suspend functions — the real-time replacement for Firestore's
 * addSnapshotListener calls that ChatRepository used before this migration.
 */
class PumpkinSocket {
    private val gson = Gson()
    private var socket: Socket? = null

    private val _messageNew = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val messageNew: SharedFlow<MessageDto> = _messageNew

    private val _messageUpdated = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val messageUpdated: SharedFlow<MessageDto> = _messageUpdated

    private val _messageDeleted = MutableSharedFlow<DeletedMessage>(extraBufferCapacity = 64)
    val messageDeleted: SharedFlow<DeletedMessage> = _messageDeleted

    private val _chatNew = MutableSharedFlow<ChatDto>(extraBufferCapacity = 16)
    val chatNew: SharedFlow<ChatDto> = _chatNew

    private val _chatUpdated = MutableSharedFlow<ChatDto>(extraBufferCapacity = 16)
    val chatUpdated: SharedFlow<ChatDto> = _chatUpdated

    private val _chatDeleted = MutableSharedFlow<DeletedChat>(extraBufferCapacity = 16)
    val chatDeleted: SharedFlow<DeletedChat> = _chatDeleted

    private val _typingUpdate = MutableSharedFlow<TypingUpdate>(extraBufferCapacity = 64)
    val typingUpdate: SharedFlow<TypingUpdate> = _typingUpdate

    private val _presenceUpdate = MutableSharedFlow<PresenceUpdate>(extraBufferCapacity = 64)
    val presenceUpdate: SharedFlow<PresenceUpdate> = _presenceUpdate

    val isConnected: Boolean get() = socket?.connected() == true

    /** Connects once (no-op if already connected) using the current Firebase ID token. */
    suspend fun connect() = withContext(Dispatchers.IO) {
        if (isConnected) return@withContext
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext
        val token = runCatching { Tasks.await(user.getIdToken(false)).token }.getOrNull()
            ?: return@withContext

        val options = IO.Options.builder().build()
        options.auth = mapOf("token" to token)
        val s = IO.socket(ServerConfig.SOCKET_URL, options)

        s.on("message:new") { args -> (args.getOrNull(0) as? JSONObject)?.let { emit(_messageNew, it, MessageDto::class.java) } }
        s.on("message:updated") { args -> (args.getOrNull(0) as? JSONObject)?.let { emit(_messageUpdated, it, MessageDto::class.java) } }
        s.on("message:deleted") { args ->
            (args.getOrNull(0) as? JSONObject)?.let {
                _messageDeleted.tryEmit(DeletedMessage(it.getString("chatId"), it.getString("messageId")))
            }
        }
        s.on("chat:new") { args -> (args.getOrNull(0) as? JSONObject)?.let { emit(_chatNew, it, ChatDto::class.java) } }
        s.on("chat:updated") { args -> (args.getOrNull(0) as? JSONObject)?.let { emit(_chatUpdated, it, ChatDto::class.java) } }
        s.on("chat:deleted") { args ->
            (args.getOrNull(0) as? JSONObject)?.let { _chatDeleted.tryEmit(DeletedChat(it.getString("chatId"))) }
        }
        s.on("typing:update") { args ->
            (args.getOrNull(0) as? JSONObject)?.let {
                _typingUpdate.tryEmit(
                    TypingUpdate(it.getString("chatId"), it.getString("userId"), it.getBoolean("isTyping"))
                )
            }
        }
        s.on("presence:update") { args ->
            (args.getOrNull(0) as? JSONObject)?.let { _presenceUpdate.tryEmit(it.toPresenceUpdate()) }
        }
        // Without this, a handshake rejected by the server (stale/expired
        // token, clock skew) failed completely silently: engine.io's own
        // reconnection logic keeps retrying with this same socket instance —
        // and the same now-fixed auth token from options.auth above — so it
        // can wedge itself into retrying forever with a token that will
        // never be accepted. Clearing `socket` here just makes `isConnected`
        // correctly report false, so the next call to connect() (app
        // foreground, opening a chat, etc.) builds a fresh Socket with a
        // newly-fetched token instead of assuming this dead one is fine.
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            android.util.Log.w("PumpkinSocket", "Socket connect_error: ${args.getOrNull(0)}")
            if (socket === s) socket = null
        }

        s.connect()
        socket = s
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    fun joinChat(chatId: String) {
        socket?.emit("chat:join", chatId)
    }

    suspend fun sendMessage(
        chatId: String,
        text: String,
        replyToMessageId: String? = null,
        replyToSenderId: String? = null,
        replyToText: String? = null,
        type: String = "text",
        audioData: String? = null,
        audioDurationMs: Long? = null,
        waveform: List<Float>? = null
    ): Result<MessageDto> =
        emitWithAck(
            "message:send",
            JSONObject().put("chatId", chatId).put("text", text)
                .put("replyToMessageId", replyToMessageId)
                .put("replyToSenderId", replyToSenderId)
                .put("replyToText", replyToText)
                .put("type", type)
                .put("audioData", audioData)
                .put("audioDurationMs", audioDurationMs)
                .put("waveform", waveform?.let { samples -> JSONArray(samples) })
        ) { response ->
            (response.get("message") as JSONObject).let { gson.fromJson(it.toString(), MessageDto::class.java) }
        }

    suspend fun editMessage(chatId: String, messageId: String, text: String): Result<MessageDto> =
        emitWithAck(
            "message:edit",
            JSONObject().put("chatId", chatId).put("messageId", messageId).put("text", text)
        ) { response ->
            (response.get("message") as JSONObject).let { gson.fromJson(it.toString(), MessageDto::class.java) }
        }

    /** "Delete for everyone" — server only allows the original sender, see socket/index.js. */
    suspend fun deleteMessage(chatId: String, messageId: String): Result<MessageDto> =
        emitWithAck(
            "message:delete",
            JSONObject().put("chatId", chatId).put("messageId", messageId)
        ) { response ->
            (response.get("message") as JSONObject).let { gson.fromJson(it.toString(), MessageDto::class.java) }
        }

    suspend fun react(chatId: String, messageId: String, emoji: String): Result<MessageDto> =
        emitWithAck(
            "message:react",
            JSONObject().put("chatId", chatId).put("messageId", messageId).put("emoji", emoji)
        ) { response ->
            (response.get("message") as JSONObject).let { gson.fromJson(it.toString(), MessageDto::class.java) }
        }

    suspend fun markRead(chatId: String, messageId: String): Result<Unit> =
        emitWithAck("message:read", JSONObject().put("chatId", chatId).put("messageId", messageId)) {}

    suspend fun markExited(chatId: String, messageId: String): Result<Unit> =
        emitWithAck("message:exit", JSONObject().put("chatId", chatId).put("messageId", messageId)) {}

    suspend fun markDelivered(chatId: String, messageId: String): Result<Unit> =
        emitWithAck("message:delivered", JSONObject().put("chatId", chatId).put("messageId", messageId)) {}

    fun setTyping(chatId: String, isTyping: Boolean) {
        socket?.emit("typing", JSONObject().put("chatId", chatId).put("isTyping", isTyping))
    }

    /** One-shot fetch of a user's current status — call once when a chat screen opens, then rely on [presenceUpdate] to stay current. */
    suspend fun getPresence(userId: String): PresenceUpdate = suspendCancellableCoroutine { cont ->
        val s = socket
        if (s == null || !s.connected()) {
            cont.resume(PresenceUpdate(userId, online = false, lastSeenAt = null))
            return@suspendCancellableCoroutine
        }
        s.emit("presence:get", arrayOf<Any>(userId)) { args ->
            val json = args.getOrNull(0) as? JSONObject
            cont.resume(json?.toPresenceUpdate(userId) ?: PresenceUpdate(userId, online = false, lastSeenAt = null))
        }
    }

    private fun JSONObject.toPresenceUpdate(fallbackUserId: String? = null): PresenceUpdate = PresenceUpdate(
        userId = if (has("userId")) getString("userId") else requireNotNull(fallbackUserId),
        online = optBoolean("online", false),
        lastSeenAt = if (isNull("lastSeenAt")) null else optLong("lastSeenAt")
    )

    private fun <T> emit(flow: MutableSharedFlow<T>, json: JSONObject, clazz: Class<T>) {
        flow.tryEmit(gson.fromJson(json.toString(), clazz))
    }

    private suspend fun <T> emitWithAck(
        event: String,
        payload: JSONObject,
        onAck: (JSONObject) -> T
    ): Result<T> {
        val s = socket
        if (s == null || !s.connected()) {
            return Result.failure(IllegalStateException("Not connected to server"))
        }
        // Bounded with withTimeoutOrNull: this socket.io-client version (2.1.1)
        // has no built-in ack timeout, so without this a dropped/oversized
        // packet that never gets an ack left the suspended coroutine hanging
        // forever — no failure, no success, the caller just never resumes.
        // If the timeout wins, cont.isActive below goes false, so a late ack
        // that arrives afterward is a harmless no-op instead of a crash from
        // resuming an already-cancelled continuation.
        val result = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Result<T>> { cont ->
                // socket.io-client's ack overload requires the args as an
                // explicit Object[] (not varargs) alongside the Ack —
                // emit(event, args...) and emit(event, args, ack) are
                // different overloads, and Kotlin won't let a trailing
                // lambda merge into a vararg call site.
                s.emit(event, arrayOf<Any>(payload)) { args ->
                    val response = args.getOrNull(0) as? JSONObject
                    val outcome = when {
                        response == null -> Result.failure(IllegalStateException("No response from server"))
                        response.optBoolean("ok", false) -> Result.success(onAck(response))
                        else -> Result.failure(IllegalStateException(response.optString("error", "Request failed")))
                    }
                    if (cont.isActive) cont.resume(outcome)
                }
            }
        }
        return result ?: Result.failure(IllegalStateException("Request timed out"))
    }
}

