package com.ai.phoneagent.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConversationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AriesDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var instance: AriesDatabase? = null

        fun getInstance(context: Context): AriesDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AriesDatabase::class.java,
                    "aries.db",
                ).build().also { instance = it }
            }
        }
    }
}
