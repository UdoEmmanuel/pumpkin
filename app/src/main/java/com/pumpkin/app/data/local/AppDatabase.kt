package com.pumpkin.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MessageEntity::class, ChatEntity::class, DraftEntity::class],
    // Bumped from 7: MessageEntity gained voice-note fields (type/audioData/
    // audioDurationMs). fallbackToDestructiveMigration() below wipes and
    // recreates the local (ephemeral, server-backed) tables on any version
    // increase — remember to bump this again for the next schema change.
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun draftDao(): DraftDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    // PRD 4.3: local-only ephemeral cache, not a synced/backed-up store.
                    "pumpkin-local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
