package com.pumpkin.app.data.remote.api

import com.pumpkin.app.data.remote.api.dto.ChatDto
import com.pumpkin.app.data.remote.api.dto.FcmTokenRequest
import com.pumpkin.app.data.remote.api.dto.MessageDto
import com.pumpkin.app.data.remote.api.dto.StartChatRequest
import com.pumpkin.app.data.remote.api.dto.SyncUserRequest
import com.pumpkin.app.data.remote.api.dto.UpdateDisplayNameRequest
import com.pumpkin.app.data.remote.api.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ChatApi {
    @GET("api/chats")
    suspend fun getChats(): List<ChatDto>

    @GET("api/chats/{chatId}/messages")
    suspend fun getMessages(@Path("chatId") chatId: String): List<MessageDto>

    @POST("api/chats/start")
    suspend fun startChat(@Body request: StartChatRequest): ChatDto

    @DELETE("api/chats/{chatId}")
    suspend fun deleteChat(@Path("chatId") chatId: String)

    // Cascades to every chat's participantNames server-side — replaced the
    // old dedicated /api/chats/participant-name endpoint (see server/src/routes/users.js).
    @PATCH("api/users/me")
    suspend fun updateDisplayName(@Body request: UpdateDisplayNameRequest): UserDto

    @GET("api/users/me")
    suspend fun getCurrentUser(): UserDto

    @POST("api/users/sync")
    suspend fun syncUser(@Body request: SyncUserRequest): UserDto

    @POST("api/users/me/fcm-token")
    suspend fun registerFcmToken(@Body request: FcmTokenRequest)
}
