package com.pumpkin.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Deliberately its own table rather than a column on ChatEntity: every
// server-driven chat upsert (chat:new/chat:updated, or the periodic
// getChats() resync) uses OnConflictStrategy.REPLACE, which would silently
// wipe an in-progress draft the instant any of those fired. A draft is
// purely local, so it lives in a table nothing server-driven ever touches.
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val chatId: String,
    val text: String
)
