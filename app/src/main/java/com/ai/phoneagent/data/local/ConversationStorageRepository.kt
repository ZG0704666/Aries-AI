package com.ai.phoneagent.data.local

import android.content.Context
import androidx.room.withTransaction
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 会话记录的本地持久化仓库（基于 Room）。
 *
 * 通过 DAO 读写会话实体，消息列表以 JSON 序列化后存入 `messagesJson` 字段。
 */
class ConversationStorageRepository(
    context: Context,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
) {
    private val database = AriesDatabase.getInstance(context)
    private val dao = database.conversationDao()

/** 读取全部会话并反序列化消息列表。 */
    suspend fun loadConversations(): List<ConversationRecord> {
        return dao.getAll().map { entity ->
            ConversationRecord(
                id = entity.id,
                title = entity.title,
                updatedAt = entity.updatedAt,
                messages =
                    json.decodeFromString(
                        ListSerializer(StoredMessageRecord.serializer()),
                        entity.messagesJson,
                    ),
            )
        }
    }

/** 事务式持久化：整体覆盖会话列表，并删除已不存在的记录。 */
    suspend fun persistConversations(records: List<ConversationRecord>) {
        database.withTransaction {
            if (records.isEmpty()) {
                dao.clearAll()
                return@withTransaction
            }

            dao.upsertAll(
                records.map { record ->
                    ConversationEntity(
                        id = record.id,
                        title = record.title,
                        updatedAt = record.updatedAt,
                        messagesJson =
                            json.encodeToString(
                                ListSerializer(StoredMessageRecord.serializer()),
                                record.messages,
                            ),
                    )
                },
            )
            dao.deleteMissing(records.map { it.id })
        }
    }
}
