package com.ai.phoneagent.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 会话持久化数据库（Room）。
 *
 * 采用单例懒构建，进程内共享同一实例。
 */
@Database(
    entities = [ConversationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AriesDatabase : RoomDatabase() {
    /** 会话数据访问对象。 */
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var instance: AriesDatabase? = null

        /** 双重检查锁获取单例。 */
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
