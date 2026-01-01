package com.ai.assistance.operit.data.repository

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.util.AppLogger
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.data.model.CustomEmoji
import com.ai.assistance.operit.data.preferences.CustomEmojiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * 自定义表情 Repository
 * 
 * 管理自定义表情的文件操作和数据持久化
 * - 文件存储在 filesDir/custom_emoji/{category}/{uuid}.{ext}
 * - 元数据通过 CustomEmojiPreferences 存储在 DataStore
 */
class CustomEmojiRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: CustomEmojiRepository? = null

        fun getInstance(context: Context): CustomEmojiRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CustomEmojiRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private const val TAG = "CustomEmojiRepository"
        private const val EMOJI_DIR = "custom_emoji"
        
        // 支持的图片格式
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
    }

    private val preferences = CustomEmojiPreferences.getInstance(context)

    suspend fun initializeBuiltinEmojis() = withContext(Dispatchers.IO) {
        if (preferences.isBuiltinEmojisInitialized().first()) {
            return@withContext
        }

        copyBuiltinEmojisFromAssets()
        preferences.setBuiltinEmojisInitialized(true)
        AppLogger.d(TAG, "Built-in emojis initialized successfully.")
    }

    /**
     * 重置为默认表情（重新从 assets 复制）
     */
    suspend fun resetToDefault() = withContext(Dispatchers.IO) {
        try {
            // 清空所有自定义表情数据
            preferences.clearAllEmojis()
            
            // 删除所有表情文件
            val emojiDir = File(context.filesDir, EMOJI_DIR)
            if (emojiDir.exists()) {
                emojiDir.deleteRecursively()
            }
            
            // 重新复制内置表情
            copyBuiltinEmojisFromAssets()
            preferences.setBuiltinEmojisInitialized(true)
            
            AppLogger.d(TAG, "Reset to default emojis successfully.")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error resetting to default emojis", e)
            throw e
        }
    }

    private suspend fun copyBuiltinEmojisFromAssets() {
        try {
            val emojiAssetsDir = "emoji"
            val categories = context.assets.list(emojiAssetsDir)

            if (categories.isNullOrEmpty()) {
                AppLogger.w(TAG, "No built-in emoji categories found in assets.")
                return
            }

            // 显式地将所有内置类别一次性添加到 DataStore
            preferences.addCategories(categories.toList())

            categories.forEach { category ->
                val files = context.assets.list("$emojiAssetsDir/$category")
                files?.forEach { fileName ->
                    val extension = fileName.substringAfterLast('.', "")
                    if (extension.lowercase() in SUPPORTED_EXTENSIONS) {
                        val targetFileName = "${UUID.randomUUID()}.$extension"
                        val targetDir = File(context.filesDir, "$EMOJI_DIR/$category")
                        if (!targetDir.exists()) {
                            targetDir.mkdirs()
                        }
                        val targetFile = File(targetDir, targetFileName)

                        // Copy from assets to internal storage
                        context.assets.open("$emojiAssetsDir/$category/$fileName").use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Create and save metadata
                        val emoji = CustomEmoji(
                            emotionCategory = category,
                            fileName = targetFileName,
                            isBuiltInCategory = true
                        )
                        // 此处不再需要单独添加类别，因为已在前面批量添加
                        preferences.addCustomEmoji(emoji)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying built-in emojis from assets", e)
            throw e
        }
    }

    /**
     * 添加自定义表情
     * 
     * @param category 类别名称
     * @param sourceUri 源图片 URI
     * @return Result<CustomEmoji> 成功返回表情对象，失败返回异常
     */
    suspend fun addCustomEmoji(category: String, sourceUri: Uri): Result<CustomEmoji> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取文件扩展名
            val extension = getFileExtension(sourceUri) ?: return@withContext Result.failure(
                IllegalArgumentException("无法确定文件扩展名")
            )
            
            if (extension.lowercase() !in SUPPORTED_EXTENSIONS) {
                return@withContext Result.failure(
                    IllegalArgumentException("不支持的图片格式: $extension")
                )
            }

            // 2. 生成唯一文件名
            val fileName = "${UUID.randomUUID()}.$extension"

            // 3. 创建目标目录
            val categoryDir = File(context.filesDir, "$EMOJI_DIR/$category")
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            // 4. 复制文件到私有目录
            val targetFile = File(categoryDir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(
                IllegalStateException("无法读取源文件")
            )

            // 5. 创建表情对象
            val isBuiltInCategory = category in CustomEmojiPreferences.BUILTIN_EMOTIONS
            val emoji = CustomEmoji(
                emotionCategory = category,
                fileName = fileName,
                isBuiltInCategory = isBuiltInCategory
            )

            // 6. 保存元数据
            preferences.addCustomEmoji(emoji)

            AppLogger.d(TAG, "Successfully added emoji: $fileName to category: $category")
            Result.success(emoji)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error adding custom emoji", e)
            Result.failure(e)
        }
    }

    /**
     * 删除自定义表情
     * 
     * @param emojiId 表情ID
     * @return Result<Unit> 成功或失败
     */
    suspend fun deleteCustomEmoji(emojiId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. 查找表情对象
            val emoji = preferences.getCustomEmojisFlow().first()
                .find { it.id == emojiId }
                ?: return@withContext Result.failure(
                    IllegalArgumentException("表情不存在: $emojiId")
                )

            // 2. 删除文件
            val file = getEmojiFile(emoji)
            if (file.exists()) {
                file.delete()
                AppLogger.d(TAG, "Deleted file: ${file.absolutePath}")
            }

            // 3. 删除元数据
            preferences.deleteCustomEmoji(emojiId)

            AppLogger.d(TAG, "Successfully deleted emoji: $emojiId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting custom emoji", e)
            Result.failure(e)
        }
    }

    /**
     * 删除整个类别
     * 
     * @param category 类别名称
     * @return Result<Unit> 成功或失败
     */
    suspend fun deleteCategory(category: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取该类别下的所有表情
            val emojis = preferences.getEmojisForCategory(category).first()

            // 2. 删除所有文件
            emojis.forEach { emoji ->
                val file = getEmojiFile(emoji)
                if (file.exists()) {
                    file.delete()
                }
            }

            // 3. 删除目录
            val categoryDir = File(context.filesDir, "$EMOJI_DIR/$category")
            if (categoryDir.exists()) {
                categoryDir.deleteRecursively()
            }

            // 4. 删除元数据
            preferences.deleteCategory(category)

            AppLogger.d(TAG, "Successfully deleted category: $category")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting category", e)
            Result.failure(e)
        }
    }

    /**
     * 获取表情文件
     * 
     * @param emoji 表情对象
     * @return File 文件对象
     */
    fun getEmojiFile(emoji: CustomEmoji): File {
        return File(context.filesDir, "$EMOJI_DIR/${emoji.emotionCategory}/${emoji.fileName}")
    }

    /**
     * 获取表情文件的 URI（用于 AsyncImage 加载）
     * 
     * @param emoji 表情对象
     * @return Uri file:// URI
     */
    fun getEmojiUri(emoji: CustomEmoji): Uri {
        return Uri.fromFile(getEmojiFile(emoji))
    }

    /**
     * 获取指定类别的表情 Flow
     */
    fun getEmojisForCategory(category: String): Flow<List<CustomEmoji>> {
        return preferences.getEmojisForCategory(category)
    }

    /**
     * 获取所有类别
     */
    fun getAllCategories(): Flow<List<String>> {
        return preferences.getAllCategories()
    }

    /**
     * 获取所有自定义表情
     */
    fun getAllEmojis(): Flow<List<CustomEmoji>> {
        return preferences.getCustomEmojisFlow()
    }

    /**
     * 添加一个新类别
     */
    suspend fun addCategory(categoryName: String) {
        preferences.addCategory(categoryName)
    }

    /**
     * 从 URI 获取文件扩展名
     */
    private fun getFileExtension(uri: Uri): String? {
        return try {
            if ("content" == uri.scheme) {
                val mimeType = context.contentResolver.getType(uri)
                MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            } else {
                val path = uri.path
                path?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting file extension", e)
            null
        }
    }

    /**
     * 验证类别名称
     * 
     * @param categoryName 类别名称
     * @return true 如果有效
     */
    fun isValidCategoryName(categoryName: String): Boolean {
        return categoryName.matches(Regex("^[a-z0-9_]+$"))
    }

    /**
     * 检查类别是否已存在
     */
    suspend fun categoryExists(categoryName: String): Boolean {
        return getAllCategories().first().contains(categoryName)
    }
}

