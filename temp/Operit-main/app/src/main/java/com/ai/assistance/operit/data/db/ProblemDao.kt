package com.ai.assistance.operit.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 问题记录的数据访问对象
 */
@Dao
interface ProblemDao {
    /**
     * 插入新问题记录，如果主键已存在则替换
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblem(problem: ProblemEntity)

    /**
     * 批量插入问题记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblems(problems: List<ProblemEntity>)

    /**
     * 获取所有问题记录
     */
    @Query("SELECT * FROM problem_records ORDER BY timestamp DESC")
    suspend fun getAllProblems(): List<ProblemEntity>

    /**
     * 获取所有问题记录作为Flow，用于实时数据更新
     */
    @Query("SELECT * FROM problem_records ORDER BY timestamp DESC")
    fun getAllProblemsFlow(): Flow<List<ProblemEntity>>

    /**
     * 根据UUID获取问题记录
     */
    @Query("SELECT * FROM problem_records WHERE uuid = :uuid")
    suspend fun getProblemById(uuid: String): ProblemEntity?

    /**
     * 根据UUID列表获取多个问题记录
     */
    @Query("SELECT * FROM problem_records WHERE uuid IN (:uuids) ORDER BY timestamp DESC")
    suspend fun getProblemsByIds(uuids: List<String>): List<ProblemEntity>

    /**
     * 删除问题记录
     */
    @Query("DELETE FROM problem_records WHERE uuid = :uuid")
    suspend fun deleteProblem(uuid: String)

    /**
     * 使用SQL LIKE进行文本搜索
     * 这是最基本的搜索，将由向量搜索补充
     */
    @Query("SELECT * FROM problem_records WHERE query LIKE '%' || :searchText || '%' OR summary LIKE '%' || :searchText || '%' ORDER BY timestamp DESC")
    suspend fun searchByText(searchText: String): List<ProblemEntity>

    /**
     * 获取问题数量
     */
    @Query("SELECT COUNT(*) FROM problem_records")
    suspend fun getProblemCount(): Int

    /**
     * 更新问题记录的向量数据
     */
    @Query("UPDATE problem_records SET vectorData = :vectorData WHERE uuid = :uuid")
    suspend fun updateVectorData(uuid: String, vectorData: ByteArray?)

    /**
     * 获取没有向量数据的问题记录
     */
    @Query("SELECT * FROM problem_records WHERE vectorData IS NULL")
    suspend fun getProblemsWithoutVectorData(): List<ProblemEntity>

    /**
     * 清空整个表
     */
    @Query("DELETE FROM problem_records")
    suspend fun clearAll()
} 