package com.ai.assistance.operit.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.ai.assistance.operit.util.AppLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * 全局图片池管理器
 * 支持内存缓存和本地持久化缓存，使用LRU策略
 */
object ImagePoolManager {
    private const val TAG = "ImagePoolManager"
    
    // 可配置的池子大小限制
    var maxPoolSize = 20 // 默认最多缓存20张图片
        set(value) {
            if (value > 0) {
                field = value
                AppLogger.d(TAG, "池子大小限制已更新为: $value")
            }
        }
    
    // 本地缓存目录（需要在使用前初始化）
    private var cacheDir: File? = null

    // 图片数据类，包含base64编码和MIME类型
    data class ImageData(
        val base64: String,
        val mimeType: String
    )
    
    /**
     * 初始化图片池，设置本地缓存目录
     * @param cacheDirPath 本地缓存目录路径
     */
    fun initialize(cacheDirPath: File, preloadNow: Boolean = true) {
        cacheDir = File(cacheDirPath, "image_pool")
        if (!cacheDir!!.exists()) {
            cacheDir!!.mkdirs()
            AppLogger.d(TAG, "创建图片缓存目录: ${cacheDir!!.absolutePath}")
        }
        if (preloadNow) {
            loadAllFromDisk()
        }
    }

    // LRU缓存：LinkedHashMap with accessOrder=true
    private val imagePool = object : LinkedHashMap<String, ImageData>(
        16,
        0.75f,
        true // accessOrder = true 表示按访问顺序排序
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ImageData>?): Boolean {
            val shouldRemove = size > maxPoolSize
            if (shouldRemove && eldest != null) {
                AppLogger.d(TAG, "池子已满，移除最旧的图片: ${eldest.key}")
                // 从磁盘删除
                deleteFromDisk(eldest.key)
            }
            return shouldRemove
        }
    }

    /**
     * 添加图片到池子
     * @param filePath 图片文件路径
     * @return 图片ID（UUID格式），如果失败返回"error"
     */
    @Synchronized
    fun addImage(filePath: String): String {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                AppLogger.e(TAG, "文件不存在或不是文件: $filePath")
                return "error"
            }

            val mimeType = getMimeTypeFromFile(file)
            if (mimeType == null) {
                AppLogger.e(TAG, "无法识别的图片格式: $filePath")
                return "error"
            }

            val fileBytes = try {
                FileInputStream(file).use { it.readBytes() }
            } catch (e: Exception) {
                AppLogger.e(TAG, "读取文件失败", e)
                return "error"
            }

            // Gemini 支持 JPEG, PNG, WEBP, HEIC, GIF. 我们将非支持格式统一转为PNG
            val supportedMimeTypes = listOf("image/jpeg", "image/png", "image/gif", "image/webp")

            val (finalBytes, finalMimeType) = if (!supportedMimeTypes.contains(mimeType)) {
                AppLogger.d(TAG, "尝试转换不受支持的图片格式: $mimeType -> image/png")
                try {
                    val bitmap = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size)
                    if (bitmap == null) {
                        AppLogger.e(TAG, "无法将文件解码为位图: $filePath")
                        return "error"
                    }
                    ByteArrayOutputStream().use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        val pngBytes = outputStream.toByteArray()
                        bitmap.recycle()
                        Pair(pngBytes, "image/png")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "图片转换失败: $filePath", e)
                    return "error"
                }
            } else {
                Pair(fileBytes, mimeType)
            }

            val base64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP)

            val id = UUID.randomUUID().toString()
            val imageData = ImageData(base64, finalMimeType)
            imagePool[id] = imageData
            
            // 保存到本地缓存
            saveToDisk(id, imageData)

            AppLogger.d(TAG, "成功添加图片到池子: $id, MIME类型: $finalMimeType, 大小: ${base64.length} 字符")
            return id
        } catch (e: Exception) {
            AppLogger.e(TAG, "添加图片时发生异常: $filePath", e)
            return "error"
        }
    }

    @Synchronized
    fun addImageFromBase64(base64: String, mimeType: String): String {
        return try {
            val id = UUID.randomUUID().toString()
            val imageData = ImageData(base64, mimeType)
            imagePool[id] = imageData
            saveToDisk(id, imageData)
            AppLogger.d(TAG, "成功从base64添加图片到池子: $id, MIME类型: $mimeType, 大小: ${base64.length} 字符")
            id
        } catch (e: Exception) {
            AppLogger.e(TAG, "从base64添加图片时发生异常", e)
            "error"
        }
    }

    /**
     * 获取图片的base64编码
     * @param id 图片ID
     * @return base64编码的图片数据，不存在返回null
     */
    @Synchronized
    fun getImage(id: String): ImageData? {
        // 先从内存缓存获取
        var imageData = imagePool[id]
        if (imageData != null) {
            AppLogger.d(TAG, "从内存缓存获取图片: $id")
            return imageData
        }
        
        // 如果内存中没有，尝试从磁盘加载
        imageData = loadFromDisk(id)
        if (imageData != null) {
            AppLogger.d(TAG, "从磁盘缓存加载图片到内存: $id")
            imagePool[id] = imageData
            return imageData
        }
        
        AppLogger.w(TAG, "图片不存在: $id")
        return null
    }

    /**
     * 获取图片的MIME类型
     * @param id 图片ID
     * @return MIME类型，不存在返回null
     */
    @Synchronized
    fun getImageMimeType(id: String): String? {
        return imagePool[id]?.mimeType
    }

    /**
     * 移除图片
     * @param id 图片ID
     */
    @Synchronized
    fun removeImage(id: String) {
        if (imagePool.remove(id) != null) {
            AppLogger.d(TAG, "从内存缓存移除图片: $id")
        }
        deleteFromDisk(id)
    }

    /**
     * 清空所有图片
     */
    @Synchronized
    fun clear() {
        imagePool.clear()
        clearDiskCache()
        AppLogger.d(TAG, "清空图片池和磁盘缓存")
    }

    /**
     * 获取当前池子大小
     */
    @Synchronized
    fun size(): Int = imagePool.size

    /**
     * 从文件获取MIME类型
     */
    private fun getMimeTypeFromFile(file: File): String? {
        val extension = file.extension.lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-ico"
            else -> {
                // 尝试通过读取文件头判断
                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    options.outMimeType
                } catch (e: Exception) {
                    AppLogger.e(TAG, "无法通过文件头识别MIME类型", e)
                    null
                }
            }
        }
    }

    /**
     * 将文件转换为base64编码
     */
    private fun convertFileToBase64(file: File): String? {
        return try {
            FileInputStream(file).use { inputStream ->
                val bytes = inputStream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取文件失败", e)
            null
        }
    }
    
    /**
     * 保存图片数据到磁盘
     */
    private fun saveToDisk(id: String, imageData: ImageData) {
        if (cacheDir == null) {
            AppLogger.w(TAG, "缓存目录未初始化，跳过磁盘保存")
            return
        }
        
        try {
            val dataFile = File(cacheDir, "$id.dat")
            val metaFile = File(cacheDir, "$id.meta")
            
            // 保存base64数据
            FileOutputStream(dataFile).use { it.write(imageData.base64.toByteArray()) }
            // 保存MIME类型
            FileOutputStream(metaFile).use { it.write(imageData.mimeType.toByteArray()) }
            
            AppLogger.d(TAG, "图片已保存到磁盘: $id")
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存图片到磁盘失败: $id", e)
        }
    }
    
    /**
     * 从磁盘加载单个图片
     */
    private fun loadFromDisk(id: String): ImageData? {
        if (cacheDir == null) return null
        
        try {
            val dataFile = File(cacheDir, "$id.dat")
            val metaFile = File(cacheDir, "$id.meta")
            
            if (!dataFile.exists() || !metaFile.exists()) {
                return null
            }
            
            val base64 = FileInputStream(dataFile).use { String(it.readBytes()) }
            val mimeType = FileInputStream(metaFile).use { String(it.readBytes()) }
            
            return ImageData(base64, mimeType)
        } catch (e: Exception) {
            AppLogger.e(TAG, "从磁盘加载图片失败: $id", e)
            return null
        }
    }
    
    /**
     * 启动时从磁盘加载所有缓存的图片
     */
    @Synchronized
    fun preloadFromDisk() {
        loadAllFromDisk()
    }

    @Synchronized
    private fun loadAllFromDisk() {
        if (cacheDir == null || !cacheDir!!.exists()) return
        
        try {
            val files = cacheDir!!.listFiles { file -> file.extension == "dat" } ?: return
            
            var loadedCount = 0
            for (file in files) {
                val id = file.nameWithoutExtension
                val imageData = loadFromDisk(id)
                if (imageData != null) {
                    imagePool[id] = imageData
                    loadedCount++
                }
            }
            
            AppLogger.d(TAG, "从磁盘加载了 $loadedCount 张图片到内存")
        } catch (e: Exception) {
            AppLogger.e(TAG, "从磁盘加载图片失败", e)
        }
    }
    
    /**
     * 从磁盘删除图片
     */
    private fun deleteFromDisk(id: String) {
        if (cacheDir == null) return
        
        try {
            val dataFile = File(cacheDir, "$id.dat")
            val metaFile = File(cacheDir, "$id.meta")
            
            dataFile.delete()
            metaFile.delete()
            
            AppLogger.d(TAG, "从磁盘删除图片: $id")
        } catch (e: Exception) {
            AppLogger.e(TAG, "从磁盘删除图片失败: $id", e)
        }
    }
    
    /**
     * 清空磁盘缓存
     */
    private fun clearDiskCache() {
        if (cacheDir == null || !cacheDir!!.exists()) return
        
        try {
            cacheDir!!.listFiles()?.forEach { it.delete() }
            AppLogger.d(TAG, "已清空磁盘缓存")
        } catch (e: Exception) {
            AppLogger.e(TAG, "清空磁盘缓存失败", e)
        }
    }
}

