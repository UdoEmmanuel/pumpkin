package com.pumpkin.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE chatId = :chatId")
    suspend fun get(chatId: String): DraftEntity?

    // Backs the chat list's "Draft" indicator — one row per chat with an
    // unsent draft.
    @Query("SELECT * FROM drafts")
    fun observeAll(): Flow<List<DraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE chatId = :chatId")
    suspend fun clear(chatId: String)
}
