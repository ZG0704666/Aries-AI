package com.ai.assistance.operit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.data.dao.ChatDao
import com.ai.assistance.operit.data.dao.MessageDao
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.MessageEntity

/** 应用数据库，包含问题记录表、聊天表和消息表 */
@Database(
        entities = [ProblemEntity::class, ChatEntity::class, MessageEntity::class],
        version = 10,
        exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    /** 获取问题记录DAO */
    abstract fun problemDao(): ProblemDao

    /** 获取聊天DAO */
    abstract fun chatDao(): ChatDao

    /** 获取消息DAO */
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // 定义从版本1到2的迁移
        private val MIGRATION_1_2 =
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 创建chats表
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `chats` (
                                `id` TEXT NOT NULL,
                                `title` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                `inputTokens` INTEGER NOT NULL DEFAULT 0,
                                `outputTokens` INTEGER NOT NULL DEFAULT 0,
                                PRIMARY KEY(`id`)
                            )
                        """.trimIndent())
                        
                        // 创建messages表
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `messages` (
                                `messageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `chatId` TEXT NOT NULL,
                                `sender` TEXT NOT NULL,
                                `content` TEXT NOT NULL,
                                `timestamp` INTEGER NOT NULL,
                                `orderIndex` INTEGER NOT NULL,
                                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                            )
                        """.trimIndent())
                        
                        // 为messages表创建索引
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId` ON `messages` (`chatId`)")
                    }
                }

        // 定义从版本2到3的迁移
        private val MIGRATION_2_3 =
                object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 向chats表添加group列
                        db.execSQL("ALTER TABLE chats ADD COLUMN `group` TEXT")
                    }
                }

        // 定义从版本3到4的迁移
        private val MIGRATION_3_4 =
                object : Migration(3, 4) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 向chats表添加displayOrder列，并用updatedAt填充现有数据
                        db.execSQL(
                                "ALTER TABLE chats ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0"
                        )
                        db.execSQL("UPDATE chats SET displayOrder = updatedAt")
                    }
                }
        
        // 定义从版本4到5的迁移
        private val MIGRATION_4_5 =
                object : Migration(4, 5) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 向chats表添加workspace列
                        db.execSQL("ALTER TABLE chats ADD COLUMN `workspace` TEXT")
                    }
                }

        // 定义从版本5到6的迁移
        private val MIGRATION_5_6 =
                object : Migration(5, 6) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 检查currentWindowSize列是否已存在，如果不存在则添加
                        try {
                            db.execSQL("ALTER TABLE chats ADD COLUMN `currentWindowSize` INTEGER NOT NULL DEFAULT 0")
                        } catch (_: Exception){

                        }
                    }
                }

        // 定义从版本6到7的迁移
        private val MIGRATION_6_7 =
                object : Migration(6, 7) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 向messages表添加roleName列
                        db.execSQL("ALTER TABLE messages ADD COLUMN `roleName` TEXT NOT NULL DEFAULT ''")
                    }
                }

        // 定义从版本7到8的迁移
        private val MIGRATION_7_8 =
                object : Migration(7, 8) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 向chats表添加parentChatId列
                        db.execSQL("ALTER TABLE chats ADD COLUMN `parentChatId` TEXT")
                        // 向chats表添加characterCardName列（用于绑定角色卡）
                        db.execSQL("ALTER TABLE chats ADD COLUMN `characterCardName` TEXT")
                    }
                }

        // 定义从版本8到9的迁移
        private val MIGRATION_8_9 =
                object : Migration(8, 9) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 向messages表添加provider列（供应商）
                        db.execSQL("ALTER TABLE messages ADD COLUMN `provider` TEXT NOT NULL DEFAULT ''")
                        // 向messages表添加modelName列（模型名称）
                        db.execSQL("ALTER TABLE messages ADD COLUMN `modelName` TEXT NOT NULL DEFAULT ''")
                    }
                }

        // 定义从版本9到10的迁移
        private val MIGRATION_9_10 =
                object : Migration(9, 10) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 向chats表添加locked列（锁定聊天，禁止删除）
                        try {
                            db.execSQL("ALTER TABLE chats ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
                        } catch (_: Exception) {

                        }
                    }
                }

        /** 获取数据库实例，单例模式 */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "app_database"
                                        )
                                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10) // 添加新的迁移
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
