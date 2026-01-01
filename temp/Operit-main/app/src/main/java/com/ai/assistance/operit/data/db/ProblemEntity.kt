package com.ai.assistance.operit.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.ai.assistance.operit.api.chat.library.ProblemLibraryTool
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 问题记录的数据库实体 */
@Entity(tableName = "problem_records")
@TypeConverters(StringListConverter::class)
data class ProblemEntity(
        @PrimaryKey val uuid: String,
        val query: String,
        val solution: String,
        val tools: List<String>,
        val summary: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        val vectorData: ByteArray? = null // 用于存储预计算的向量数据
) {
    // ByteArray需要自定义equals和hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProblemEntity

        if (uuid != other.uuid) return false
        if (query != other.query) return false
        if (solution != other.solution) return false
        if (tools != other.tools) return false
        if (summary != other.summary) return false
        if (timestamp != other.timestamp) return false
        if (vectorData != null) {
            if (other.vectorData == null) return false
            if (!vectorData.contentEquals(other.vectorData)) return false
        } else if (other.vectorData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + query.hashCode()
        result = 31 * result + solution.hashCode()
        result = 31 * result + tools.hashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (vectorData?.contentHashCode() ?: 0)
        return result
    }

    // 转换为ProblemRecord
    fun toProblemRecord(): ProblemLibraryTool.ProblemRecord {
        return ProblemLibraryTool.ProblemRecord(
                uuid = uuid,
                query = query,
                solution = solution,
                tools = tools,
                summary = summary,
                timestamp = timestamp
        )
    }

    companion object {
        // 从ProblemRecord转换为ProblemEntity
        fun fromProblemRecord(
            record: ProblemLibraryTool.ProblemRecord,
            vectorData: ByteArray? = null
        ): ProblemEntity {
            return ProblemEntity(
                    uuid = record.uuid,
                    query = record.query,
                    solution = record.solution,
                    tools = record.tools,
                    summary = record.summary,
                    timestamp = record.timestamp,
                    vectorData = vectorData
            )
        }
    }
}

/** 用于将String列表转换为SQLite可存储的格式 */
class StringListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return value?.let { json.encodeToString(it) } ?: "[]"
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
