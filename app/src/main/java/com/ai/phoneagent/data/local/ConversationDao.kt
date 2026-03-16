package com.ai.phoneagent.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Upsert
    suspend fun upsertAll(items: List<ConversationEntity>)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()

    @Query("DELETE FROM conversations WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<Long>)
}
