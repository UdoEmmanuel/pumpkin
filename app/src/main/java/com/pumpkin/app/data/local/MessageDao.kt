package com.pumpkin.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY sentAt ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    // Backs chat-list unread counts — computed in Kotlin (grouped by chatId)
    // rather than in SQL, since readAt is a JSON-encoded map column.
    @Query("SELECT * FROM messages")
    fun observeAllMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    // PRD 4.4: purge once the server confirms both participants read + exited.
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun delete(messageId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllForChat(chatId: String)

    /**
     * Full reconcile of one chat's local cache against the server's current
     * message list — clears whatever was there first. See
     * ChatRepository.startMessageSync for why upsert-only isn't enough.
     */
    @Transaction
    suspend fun replaceAllForChat(chatId: String, messages: List<MessageEntity>) {
        deleteAllForChat(chatId)
        upsertAll(messages)
    }
}
