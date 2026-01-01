package com.ai.assistance.operit.core.subpack

import android.content.Context
import android.graphics.Bitmap
import com.ai.assistance.operit.util.AppLogger
import com.android.apksig.ApkSigner
import dev.rushii.arsc.*
import java.io.*
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import net.dongliu.apk.parser.ApkFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.io.IOUtils
import org.w3c.dom.Element
import pxb.android.axml.Axml
import pxb.android.axml.AxmlReader
import pxb.android.axml.AxmlVisitor
import pxb.android.axml.AxmlWriter

/** APK逆向工程工具类 使用Android标准库和专业库实现APK的解压、修改和重新打包 */
class ApkReverseEngineer(private val context: Context) {
    companion object {
        private const val TAG = "ApkReverseEngineer"
        private const val TEMP_DIR = "apk_reverse_temp"
        private const val ANDROID_MANIFEST = "AndroidManifest.xml"
    }

    private val tempDir: File by lazy {
        File(context.cacheDir, TEMP_DIR).apply { if (!exists()) mkdirs() }
    }

    /**
     * 获取APK基本信息
     * @param apkFile APK文件
     * @return 包名和版本信息的Map
     */
    fun getApkInfo(apkFile: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            // 使用apk-parser库解析APK文件
            ApkFile(apkFile).use { apkParser ->
                val apkMeta = apkParser.apkMeta
                result["package"] = apkMeta.packageName
                result["versionName"] = apkMeta.versionName
                result["versionCode"] = apkMeta.versionCode.toString()
                result["appName"] = apkMeta.name
                result["minSdkVersion"] = apkMeta.minSdkVersion.toString()
                result["targetSdkVersion"] = apkMeta.targetSdkVersion.toString()

                // 获取权限列表
                val permissions = apkMeta.usesPermissions.joinToString(", ")
                if (permissions.isNotEmpty()) {
                    result["permissions"] = permissions
                }

                // 获取功能列表
                val features = apkMeta.usesFeatures.map { it.name ?: "(未命名功能)" }.joinToString(", ")
                if (features.isNotEmpty()) {
                    result["features"] = features
                }
            }

            AppLogger.d(TAG, "成功解析APK信息: 包名=${result["package"]}, 版本=${result["versionName"]}")
            return result
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取APK信息失败", e)
            return mapOf("error" to (e.message ?: "未知错误"))
        }
    }

    /**
     * 解压APK文件到临时目录
     * @param apkFile APK文件
     * @return 解压后的目录
     */
    fun extractApk(apkFile: File): File {
        val extractDir = File(tempDir, apkFile.nameWithoutExtension)
        if (extractDir.exists()) extractDir.deleteRecursively()
        extractDir.mkdirs()

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryDestination = File(extractDir, entry.name)

                    if (entry.isDirectory) {
                        entryDestination.mkdirs()
                    } else {
                        entryDestination.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(entryDestination).use { output ->
                                IOUtils.copy(input, output)
                            }
                        }
                    }
                }
            }
            AppLogger.d(TAG, "APK解压成功: ${extractDir.absolutePath}")
            return extractDir
        } catch (e: Exception) {
            AppLogger.e(TAG, "APK解压失败", e)
            throw RuntimeException("APK解压失败: ${e.message}")
        }
    }

    /**
     * 修改APK包名 - 通过修改AndroidManifest.xml文件
     * @param extractedDir 解压后的APK目录
     * @param newPackageName 新包名
     * @return 是否修改成功
     */
    fun modifyPackageName(extractedDir: File, newPackageName: String): Boolean {
        val manifestFile = File(extractedDir, ANDROID_MANIFEST)
        if (!manifestFile.exists()) {
            AppLogger.e(TAG, "未找到AndroidManifest.xml文件")
            return false
        }

        try {
            // 读取二进制AndroidManifest.xml文件
            val manifestBytes = FileInputStream(manifestFile).use { it.readBytes() }

            // 通过AxmlReader读取AXML文件
            val reader = AxmlReader(manifestBytes)

            // 创建Axml数据结构
            val axml = Axml()
            reader.accept(axml)

            // 查找manifest元素并修改package属性
            var oldPackageName = ""
            var packageFound = false
            for (node in axml.firsts) {
                if (node.name == "manifest") {
                    // 遍历属性找到package
                    for (attr in node.attrs) {
                        if (attr.name == "package") {
                            oldPackageName = attr.value as String
                            AppLogger.d(TAG, "找到原始包名: $oldPackageName, 将替换为: $newPackageName")
                            // 修改包名
                            attr.value = newPackageName
                            packageFound = true
                            break
                        }
                    }

                    // 如果没找到package属性，添加一个
                    if (!packageFound) {
                        val attr = Axml.Node.Attr()
                        attr.name = "package"
                        attr.ns = null
                        attr.resourceId = -1
                        attr.type = AxmlVisitor.TYPE_STRING
                        attr.value = newPackageName
                        node.attrs.add(attr)
                        packageFound = true
                    }
                    break
                }
            }

            if (!packageFound || oldPackageName.isEmpty()) {
                AppLogger.e(TAG, "未在AndroidManifest.xml中找到manifest元素或package属性")
                return false
            }

            // 遍历所有节点并替换引用旧包名的属性
            // 除了 oldPackageName.MainActivity 之外的所有引用
            replacePackageReferences(axml, oldPackageName, newPackageName)

            // 创建AXML写入器生成修改后的二进制文件
            val writer = AxmlWriter()
            axml.accept(writer)
            val modifiedBytes = writer.toByteArray()

            // 备份原始文件
            val backupFile = File(extractedDir, "${ANDROID_MANIFEST}.bak")
            if (backupFile.exists()) backupFile.delete()
            manifestFile.renameTo(backupFile)

            // 写入修改后的文件
            FileOutputStream(manifestFile).use { it.write(modifiedBytes) }

            AppLogger.d(TAG, "成功修改AndroidManifest.xml中的包名和相关引用")
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "修改包名时发生异常", e)
            return false
        }
    }

    /**
     * 替换AXML中所有引用旧包名的属性
     * @param axml AXML数据结构
     * @param oldPackageName 旧包名
     * @param newPackageName 新包名
     */
    private fun replacePackageReferences(
            axml: Axml,
            oldPackageName: String,
            newPackageName: String
    ) {
        // 递归处理所有节点
        fun processNode(node: Axml.Node) {
            // 处理当前节点的属性
            for (attr in node.attrs) {
                if (attr.value is String) {
                    val strValue = attr.value as String

                    // 特殊情况：保留对MainActivity的引用不变
                    if (strValue == "$oldPackageName.MainActivity" ||
                                    strValue.endsWith(".$oldPackageName.MainActivity")
                    ) {
                        AppLogger.d(TAG, "保留MainActivity引用不变: $strValue")
                        continue
                    }

                    // 替换所有其他引用旧包名的情况
                    if (strValue.contains(oldPackageName)) {
                        val newValue = strValue.replace(oldPackageName, newPackageName)
                        AppLogger.d(TAG, "替换包名引用: $strValue -> $newValue")
                        attr.value = newValue
                    }
                }
            }

            // 递归处理子节点
            for (childNode in node.children) {
                processNode(childNode)
            }
        }

        // 处理所有顶级节点
        for (node in axml.firsts) {
            processNode(node)
        }
    }

    /**
     * 修改应用名称 - 通过修改AndroidManifest.xml文件或strings.xml
     * @param extractedDir 解压后的APK目录
     * @param newAppName 新应用名称
     * @return 是否修改成功
     */
    fun modifyAppName(extractedDir: File, newAppName: String): Boolean {
        try {
            val manifestFile = File(extractedDir, ANDROID_MANIFEST)
            if (manifestFile.exists()) {
                // 使用二进制方式直接修改AndroidManifest.xml
                try {
                    // 读取二进制AndroidManifest.xml文件
                    val manifestBytes = FileInputStream(manifestFile).use { it.readBytes() }

                    // 通过AxmlReader读取AXML文件
                    val reader = AxmlReader(manifestBytes)

                    // 创建Axml数据结构
                    val axml = Axml()
                    reader.accept(axml)

                    // 查找application元素并修改label属性
                    var labelModified = false
                    for (node in axml.firsts) {
                        if (node.name == "manifest") {
                            // 查找application节点
                            for (childNode in node.children) {
                                if (childNode.name == "application") {
                                    // 查找label属性
                                    var labelAttr: Axml.Node.Attr? = null
                                    for (attr in childNode.attrs) {
                                        if (attr.name == "label" &&
                                                        (attr.ns == null ||
                                                                attr.ns ==
                                                                        "http://schemas.android.com/apk/res/android")
                                        ) {
                                            // 修改标签值
                                            attr.value = newAppName
                                            labelModified = true
                                            break
                                        }
                                    }

                                    // 如果没有找到label属性，添加一个
                                    if (!labelModified) {
                                        val attr = Axml.Node.Attr()
                                        attr.name = "label"
                                        attr.ns = "http://schemas.android.com/apk/res/android"
                                        attr.resourceId = -1
                                        attr.type = AxmlVisitor.TYPE_STRING
                                        attr.value = newAppName
                                        childNode.attrs.add(attr)
                                        labelModified = true
                                    }
                                    break
                                }
                            }
                            break
                        }
                    }

                    if (labelModified) {
                        // 创建AXML写入器生成修改后的二进制文件
                        val writer = AxmlWriter()
                        axml.accept(writer)
                        val modifiedBytes = writer.toByteArray()

                        // 备份原始文件
                        val backupFile = File(extractedDir, "${ANDROID_MANIFEST}.bak")
                        if (backupFile.exists()) backupFile.delete()
                        manifestFile.renameTo(backupFile)

                        // 写入修改后的文件
                        FileOutputStream(manifestFile).use { it.write(modifiedBytes) }

                        AppLogger.d(TAG, "已在AndroidManifest.xml中更新应用名称为: $newAppName")
                        return true
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "修改AndroidManifest.xml中的应用名称失败: ${e.message}", e)
                    // 尝试备用方法 - 修改strings.xml
                }
            }

            // 如果无法修改清单文件或清单文件不存在，尝试修改strings.xml
            val success = modifyAppNameInStrings(extractedDir, newAppName)
            if (success) {
                return true
            }

            AppLogger.e(TAG, "无法修改应用名称，既找不到AndroidManifest.xml中的label属性，也找不到strings.xml中的app_name")
            return false
        } catch (e: Exception) {
            AppLogger.e(TAG, "修改应用名称失败", e)
            return false
        }
    }

    /** 修改应用名称 - 通过修改strings.xml资源文件 */
    fun modifyVersion(extractedDir: File, newVersionName: String, newVersionCode: String): Boolean {
        val manifestFile = File(extractedDir, ANDROID_MANIFEST)
        if (!manifestFile.exists()) {
            AppLogger.e(TAG, "未找到AndroidManifest.xml文件")
            return false
        }

        try {
            val manifestBytes = FileInputStream(manifestFile).use { it.readBytes() }
            val reader = AxmlReader(manifestBytes)
            val axml = Axml()
            reader.accept(axml)

            var manifestNode: Axml.Node? = null
            for (node in axml.firsts) {
                if (node.name == "manifest") {
                    manifestNode = node
                    break
                }
            }

            if (manifestNode == null) {
                AppLogger.e(TAG, "未在AndroidManifest.xml中找到manifest元素")
                return false
            }

            val androidNs = manifestNode.attrs.find { it.name == "versionCode" }?.ns ?: "http://schemas.android.com/apk/res/android"

            // 修改或添加versionName
            var versionNameAttr = manifestNode.attrs.find { it.name == "versionName" && it.ns == androidNs }
            if (versionNameAttr != null) {
                versionNameAttr.value = newVersionName
            } else {
                versionNameAttr = Axml.Node.Attr().apply {
                    name = "versionName"
                    ns = androidNs
                    resourceId = -1
                    type = AxmlVisitor.TYPE_STRING
                    value = newVersionName
                }
                manifestNode.attrs.add(versionNameAttr)
            }

            // 修改或添加versionCode
            var versionCodeAttr = manifestNode.attrs.find { it.name == "versionCode" && it.ns == androidNs }
            if (versionCodeAttr != null) {
                versionCodeAttr.value = newVersionCode.toIntOrNull() ?: 1
                versionCodeAttr.type = AxmlVisitor.TYPE_INT_HEX
            } else {
                versionCodeAttr = Axml.Node.Attr().apply {
                    name = "versionCode"
                    ns = androidNs
                    resourceId = -1
                    type = AxmlVisitor.TYPE_INT_HEX
                    value = newVersionCode.toIntOrNull() ?: 1
                }
                manifestNode.attrs.add(versionCodeAttr)
            }

            val writer = AxmlWriter()
            axml.accept(writer)
            val modifiedBytes = writer.toByteArray()

            FileOutputStream(manifestFile).use { it.write(modifiedBytes) }

            AppLogger.d(TAG, "成功修改版本为: $newVersionName ($newVersionCode)")
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "修改版本信息时发生异常", e)
            return false
        }
    }

    /** 修改应用名称 - 通过修改strings.xml资源文件 */
    private fun modifyAppNameInStrings(extractedDir: File, newAppName: String): Boolean {
        try {
            // 查找values目录中的strings.xml
            val resDir = File(extractedDir, "res")
            val valuesDirs =
                    resDir.listFiles { file -> file.isDirectory && file.name.startsWith("values") }
                            ?: return false

            var success = false

            for (valuesDir in valuesDirs) {
                val stringsFile = File(valuesDir, "strings.xml")
                if (stringsFile.exists()) {
                    // 解析strings.xml
                    val factory = DocumentBuilderFactory.newInstance()
                    val builder = factory.newDocumentBuilder()
                    val document = builder.parse(stringsFile)

                    // 查找app_name字符串
                    val strings = document.getElementsByTagName("string")
                    var appNameFound = false

                    for (i in 0 until strings.length) {
                        val element = strings.item(i) as Element
                        if (element.getAttribute("name") == "app_name") {
                            element.textContent = newAppName
                            appNameFound = true
                            break
                        }
                    }

                    // 如果未找到app_name，创建一个
                    if (!appNameFound) {
                        val stringElement = document.createElement("string")
                        stringElement.setAttribute("name", "app_name")
                        stringElement.textContent = newAppName
                        document.documentElement.appendChild(stringElement)
                    }

                    // 保存修改
                    val transformerFactory = TransformerFactory.newInstance()
                    val transformer = transformerFactory.newTransformer()
                    val source = DOMSource(document)
                    val result = StreamResult(stringsFile)
                    transformer.transform(source, result)

                    success = true
                    AppLogger.d(TAG, "已在 ${stringsFile.path} 中更新应用名称为: $newAppName")
                }
            }

            return success
        } catch (e: Exception) {
            AppLogger.e(TAG, "修改strings.xml中的应用名称失败", e)
            return false
        }
    }

    /**
     * 更换应用图标
     * @param extractedDir 解压后的APK目录
     * @param newIconBitmap 新图标位图
     * @return 是否修改成功
     */
    fun changeAppIcon(extractedDir: File, newIconBitmap: Bitmap): Boolean {
        try {
            AppLogger.d(TAG, "开始更换应用图标，提供的图标尺寸: ${newIconBitmap.width}x${newIconBitmap.height}")
            val resDir = File(extractedDir, "res")
            if (!resDir.exists()) {
                AppLogger.e(TAG, "res目录不存在: ${resDir.absolutePath}")
                return false
            }

            // 直接替换已知的图标文件 - 这些是混淆后的短名称图标
            val knownIconFiles =
                    listOf("yn.png", "N3.png", "9w.png", "FS.png", "RJ.png", "o-.png").map {
                        File(resDir, it)
                    }

            var success = false
            var replacedCount = 0

            // 替换已知的图标文件
            for (iconFile in knownIconFiles) {
                if (iconFile.exists()) {
                    try {
                        AppLogger.d(TAG, "找到已知图标文件: ${iconFile.absolutePath}, 大小: ${iconFile.length()}字节")

                        // 根据文件大小确定合适的输出尺寸
                        val size =
                                when {
                                    iconFile.length() > 40000 -> 192 // 大文件使用更高分辨率
                                    iconFile.length() > 20000 -> 144
                                    iconFile.length() > 10000 -> 96
                                    iconFile.length() > 5000 -> 72
                                    else -> 48
                                }

                        // 缩放新图标
                        val scaledIcon = scaleBitmap(newIconBitmap, size)

                        // 保存到文件
                        FileOutputStream(iconFile).use { output ->
                            val format =
                                    when (iconFile.extension.lowercase()) {
                                        "webp" -> Bitmap.CompressFormat.WEBP
                                        "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                                        else -> Bitmap.CompressFormat.PNG
                                    }
                            scaledIcon.compress(format, 100, output)
                        }

                        replacedCount++
                        success = true
                        AppLogger.d(TAG, "成功替换图标: ${iconFile.absolutePath}")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "替换图标文件失败: ${iconFile.absolutePath}, 错误: ${e.message}")
                    }
                } else {
                    AppLogger.d(TAG, "未找到已知图标文件: ${iconFile.absolutePath}")
                }
            }

            AppLogger.d(TAG, "图标替换总结: 成功替换 $replacedCount 个图标文件")
            return success
        } catch (e: Exception) {
            AppLogger.e(TAG, "替换应用图标失败: ${e.message}", e)
            return false
        }
    }

    /** 递归收集目录中所有图片文件 */
    private fun collectAllImageFiles(dir: File, result: MutableList<File>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                collectAllImageFiles(file, result)
            } else if (file.isFile &&
                            (file.extension.equals("png", ignoreCase = true) ||
                                    file.extension.equals("webp", ignoreCase = true) ||
                                    file.extension.equals("jpg", ignoreCase = true))
            ) {
                result.add(file)
            }
        }
    }

    /** 根据文件路径确定图标尺寸 */
    private fun determineIconSize(iconFile: File): Int {
        return when {
            iconFile.path.contains("xxxhdpi") -> 192
            iconFile.path.contains("xxhdpi") -> 144
            iconFile.path.contains("xhdpi") -> 96
            iconFile.path.contains("hdpi") -> 72
            iconFile.path.contains("mdpi") -> 48
            else -> 96 // 默认尺寸
        }
    }

    /** 按指定尺寸缩放位图 */
    private fun scaleBitmap(source: Bitmap, size: Int): Bitmap {
        return Bitmap.createScaledBitmap(source, size, size, true)
    }

    /**
     * 重新打包APK文件
     * @param extractedDir 解压后的APK目录
     * @param outputApk 输出的APK文件
     * @return 是否打包成功
     */
    fun repackageApk(extractedDir: File, outputApk: File): Boolean {
        try {
            if (outputApk.exists()) outputApk.delete()
            outputApk.parentFile?.mkdirs()

            // 创建一个临时文件用于存储未对齐的APK
            val tempUnalignedApk =
                    File(outputApk.parentFile, "${outputApk.nameWithoutExtension}_unaligned.apk")
            if (tempUnalignedApk.exists()) tempUnalignedApk.delete()

            // 将解压的目录内容打包成未对齐的APK
            ZipArchiveOutputStream(FileOutputStream(tempUnalignedApk)).use { zipOut ->
                addDirToZip(extractedDir, extractedDir, zipOut)
            }

            AppLogger.d(TAG, "APK初步打包完成，准备进行zipalign对齐: ${tempUnalignedApk.absolutePath}")

            val aligned = zipalign(tempUnalignedApk, outputApk, 4)

            // 删除临时未对齐APK
            tempUnalignedApk.delete()

            if (!aligned) {
                AppLogger.e(TAG, "APK对齐失败")
                return false
            }

            AppLogger.d(TAG, "APK重新打包成功并完成4字节对齐: ${outputApk.absolutePath}")
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "APK重新打包失败", e)
            return false
        }
    }

    /**
     * 对APK文件进行zipalign处理
     * @param inputApk 输入的APK文件
     * @param outputApk 输出的APK文件
     * @param alignment 对齐字节数（通常为4）
     * @return 是否对齐成功
     */
    fun zipalign(inputApk: File, outputApk: File, alignment: Int): Boolean {
        try {
            if (outputApk.exists()) outputApk.delete()

            AppLogger.d(
                    TAG,
                    "使用zipalign-java库进行${alignment}字节对齐: ${inputApk.absolutePath} -> ${outputApk.absolutePath}"
            )

            // 使用zipalign-java库进行对齐
            val rafIn = RandomAccessFile(inputApk, "r")
            val fos = FileOutputStream(outputApk)

            // 对.so文件使用16KB边界对齐，其他文件使用4字节对齐
            com.iyxan23.zipalignjava.ZipAlign.alignZip(rafIn, fos, alignment, 4 * 1024)

            rafIn.close()
            fos.close()

            AppLogger.d(TAG, "APK对齐完成")
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "zipalign处理失败", e)
            return false
        }
    }

    /** 递归添加目录到ZIP文件 */
    private fun addDirToZip(rootDir: File, currentDir: File, zipOut: ZipArchiveOutputStream) {
        currentDir.listFiles()?.forEach { file ->
            val relativePath =
                    file.absolutePath.substring(rootDir.absolutePath.length + 1).replace("\\", "/")

            if (file.isDirectory) {
                if (file.listFiles()?.isNotEmpty() == true) {
                    addDirToZip(rootDir, file, zipOut)
                } else {
                    val entry = ZipArchiveEntry("$relativePath/")
                    zipOut.putArchiveEntry(entry)
                    zipOut.closeArchiveEntry()
                }
            } else {
                val entry = ZipArchiveEntry(relativePath)

                // 判断是否应该不压缩存储
                val shouldStore = shouldStoreWithoutCompression(relativePath)
                if (shouldStore) {
                    // 设置为STORED(不压缩)模式
                    entry.method = ZipArchiveEntry.STORED

                    // STORED模式要求预先设置文件大小和CRC32校验值
                    entry.size = file.length()
                    entry.time = file.lastModified()

                    // 计算CRC32值
                    val crc = calculateFileCrc32(file)
                    entry.crc = crc
                } else {
                    // 默认使用DEFLATED(压缩)模式
                    entry.method = ZipArchiveEntry.DEFLATED
                }

                zipOut.putArchiveEntry(entry)
                FileInputStream(file).use { input -> IOUtils.copy(input, zipOut) }
                zipOut.closeArchiveEntry()
            }
        }
    }

    /**
     * 判断文件是否应该不压缩存储
     * @param filePath 文件路径
     * @return 如果应该不压缩存储返回true，否则返回false
     */
    private fun shouldStoreWithoutCompression(filePath: String): Boolean {
        // 检查文件名或扩展名
        return when {
            // 关键的APK文件
            filePath.endsWith("/AndroidManifest.xml") || filePath == "AndroidManifest.xml" -> true
            filePath.endsWith("/resources.arsc") || filePath == "resources.arsc" -> true
            filePath.endsWith(".dex") -> true

            // META-INF目录中的签名文件
            filePath.startsWith("META-INF/") &&
                    (filePath.endsWith(".SF") ||
                            filePath.endsWith(".RSA") ||
                            filePath.endsWith(".DSA") ||
                            filePath == "META-INF/MANIFEST.MF") -> true

            // 默认压缩
            else -> false
        }
    }

    /**
     * 计算文件的CRC32校验值
     * @param file 需要计算CRC32的文件
     * @return CRC32值
     */
    private fun calculateFileCrc32(file: File): Long {
        val crc = java.util.zip.CRC32()
        try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    crc.update(buffer, 0, bytesRead)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "计算CRC32失败: ${e.message}", e)
        }
        return crc.value
    }

    /** 清理临时文件 */
    fun cleanup() {
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
            AppLogger.d(TAG, "临时文件清理完成")
        }
    }

    /**
     * 重新签名APK
     * @param unsignedApk 未签名的APK文件
     * @param keyStoreFile 密钥库文件
     * @param keyStorePassword 密钥库密码
     * @param keyAlias 密钥别名
     * @param keyPassword 密钥密码
     * @param outputApk 签名后的APK文件
     * @return 包含签名结果和错误消息的Pair，成功时第二个值为null
     */
    fun signApk(
            unsignedApk: File,
            keyStoreFile: File,
            keyStorePassword: String,
            keyAlias: String,
            keyPassword: String,
            outputApk: File
    ): Pair<Boolean, String?> {
        try {
            if (!unsignedApk.exists()) {
                val message = "未签名的APK文件不存在: ${unsignedApk.absolutePath}"
                AppLogger.e(TAG, message)
                return Pair(false, message)
            }

            if (!keyStoreFile.exists()) {
                val message = "密钥库文件不存在: ${keyStoreFile.absolutePath}"
                AppLogger.e(TAG, message)
                return Pair(false, message)
            }

            AppLogger.d(TAG, "开始签名APK，使用密钥: ${keyStoreFile.absolutePath}, 别名: $keyAlias")
            AppLogger.d(TAG, "密钥文件大小: ${keyStoreFile.length()}字节")

            if (outputApk.exists()) outputApk.delete()
            outputApk.parentFile?.mkdirs()

            // 首先尝试使用PKCS12格式加载密钥库
            val pkcs12Result =
                    trySignWithKeyStoreType(
                            unsignedApk,
                            keyStoreFile,
                            keyStorePassword,
                            keyAlias,
                            keyPassword,
                            outputApk,
                            "PKCS12"
                    )
            if (pkcs12Result.first) {
                return Pair(true, null)
            }

            // 如果PKCS12失败，尝试使用JKS格式
            val jksResult =
                    trySignWithKeyStoreType(
                            unsignedApk,
                            keyStoreFile,
                            keyStorePassword,
                            keyAlias,
                            keyPassword,
                            outputApk,
                            "JKS"
                    )
            if (jksResult.first) {
                return Pair(true, null)
            }

            val errorMessage =
                    "使用PKCS12和JKS格式均无法加载密钥库进行签名。\nPKCS12错误: ${pkcs12Result.second}\nJKS错误: ${jksResult.second}"
            AppLogger.e(TAG, errorMessage)
            return Pair(false, errorMessage)
        } catch (e: Exception) {
            val errorMessage = "APK签名失败: ${e.message}"
            AppLogger.e(TAG, errorMessage, e)
            return Pair(false, errorMessage)
        }
    }

    /** 尝试使用指定格式的密钥库进行签名 */
    private fun trySignWithKeyStoreType(
            unsignedApk: File,
            keyStoreFile: File,
            keyStorePassword: String,
            keyAlias: String,
            keyPassword: String,
            outputApk: File,
            keyStoreType: String
    ): Pair<Boolean, String?> {
        try {
            AppLogger.d(TAG, "尝试以$keyStoreType 格式加载密钥库")

            // 使用KeyStoreHelper获取密钥库实例
            val keyStore = KeyStoreHelper.getKeyStoreInstance(keyStoreType)
            if (keyStore == null) {
                val errorMessage = "获取$keyStoreType 密钥库实例失败"
                AppLogger.e(TAG, errorMessage)
                return Pair(false, errorMessage)
            }

            FileInputStream(keyStoreFile).use { input ->
                try {
                    keyStore.load(input, keyStorePassword.toCharArray())
                    AppLogger.d(TAG, "成功以$keyStoreType 格式加载密钥库")
                } catch (e: Exception) {
                    val errorMessage = "加载$keyStoreType 密钥库失败: ${e.message}"
                    AppLogger.e(TAG, errorMessage)
                    return Pair(false, errorMessage)
                }

                // 获取可用的别名
                val aliases = keyStore.aliases()
                val aliasList = mutableListOf<String>()
                while (aliases.hasMoreElements()) {
                    aliasList.add(aliases.nextElement())
                }

                if (aliasList.isEmpty()) {
                    val errorMessage = "$keyStoreType 密钥库中没有任何密钥别名"
                    AppLogger.e(TAG, errorMessage)
                    return Pair(false, errorMessage)
                } else {
                    AppLogger.d(TAG, "$keyStoreType 密钥库中的别名: ${aliasList.joinToString()}")

                    // 如果指定的别名不存在，但有其他别名，使用第一个别名
                    if (!aliasList.contains(keyAlias) && aliasList.isNotEmpty()) {
                        AppLogger.w(TAG, "指定的别名'$keyAlias'不存在，将使用可用的别名: ${aliasList[0]}")
                        val actualKeyAlias = aliasList[0]
                        return signWithKeyStore(
                                keyStore,
                                unsignedApk,
                                actualKeyAlias,
                                keyPassword,
                                outputApk
                        )
                    }
                }

                return signWithKeyStore(keyStore, unsignedApk, keyAlias, keyPassword, outputApk)
            }
        } catch (e: Exception) {
            val errorMessage = "以$keyStoreType 格式加载密钥库失败: ${e.message}"
            AppLogger.e(TAG, errorMessage, e)
            return Pair(false, errorMessage)
        }
    }

    /** 使用已加载的KeyStore进行签名 */
    private fun signWithKeyStore(
            keyStore: KeyStore,
            unsignedApk: File,
            keyAlias: String,
            keyPassword: String,
            outputApk: File
    ): Pair<Boolean, String?> {
        try {
            // 获取私钥
            val key = keyStore.getKey(keyAlias, keyPassword.toCharArray())
            if (key == null) {
                val errorMessage = "在密钥库中找不到别名为'$keyAlias'的密钥"
                AppLogger.e(TAG, errorMessage)
                return Pair(false, errorMessage)
            }

            if (key !is PrivateKey) {
                val errorMessage = "找到的密钥不是私钥类型: ${key.javaClass.name}"
                AppLogger.e(TAG, errorMessage)
                return Pair(false, errorMessage)
            }
            val privateKey = key

            // 获取证书链
            val certificateChain = keyStore.getCertificateChain(keyAlias)
            if (certificateChain == null || certificateChain.isEmpty()) {
                val errorMessage = "无法获取别名为'$keyAlias'的证书链"
                AppLogger.e(TAG, errorMessage)
                return Pair(false, errorMessage)
            }

            val x509CertificateChain =
                    certificateChain.map { cert ->
                        if (cert !is X509Certificate) {
                            val errorMessage = "证书不是X509Certificate类型: ${cert.javaClass.name}"
                            AppLogger.e(TAG, errorMessage)
                            return Pair(false, errorMessage)
                        }
                        cert as X509Certificate
                    }

            // 使用ApkSigner进行签名
            val signer =
                    ApkSigner.SignerConfig.Builder(keyAlias, privateKey, x509CertificateChain)
                            .build()
            val signerConfigs = listOf(signer)

            val apkSigner =
                    ApkSigner.Builder(signerConfigs)
                            .setInputApk(unsignedApk)
                            .setOutputApk(outputApk)
                            .setMinSdkVersion(26) // 根据项目实际最低SDK版本调整
                            .build()

            try {
                apkSigner.sign()
            } catch (e: Exception) {
                val errorMessage = "ApkSigner执行失败: ${e.message}"
                AppLogger.e(TAG, errorMessage, e)
                return Pair(false, errorMessage)
            }

            AppLogger.d(TAG, "APK签名完成: ${outputApk.absolutePath}")
            return Pair(true, null)
        } catch (e: Exception) {
            val errorMessage = "使用KeyStore签名APK失败: ${e.message}"
            AppLogger.e(TAG, errorMessage, e)
            return Pair(false, errorMessage)
        }
    }
}
