package com.ai.phoneagent.data.local

import android.content.Context
import androidx.room.withTransaction
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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
