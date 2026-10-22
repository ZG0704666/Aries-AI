package com.ai.phoneagent.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** 会话表数据访问对象。 */
@Dao
interface ConversationDao {
    /** 按更新时间倒序读取全部会话。 */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ConversationEntity>

    /** 批量插入或更新会话。 */
    @Upsert
    suspend fun upsertAll(items: List<ConversationEntity>)

    /** 清空全部会话。 */
    @Query("DELETE FROM conversations")
    suspend fun clearAll()

    /** 删除不在给定 ID 列表中的会话，用于清理已不存在的记录。 */
    @Query("DELETE FROM conversations WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<Long>)
}
