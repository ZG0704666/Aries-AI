package com.ai.assistance.operit.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.ai.assistance.operit.util.AppLogger
import androidx.core.content.edit
import com.ai.assistance.operit.core.avatar.common.factory.AvatarModelFactory
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Data class representing the persisted configuration of a single avatar.
 * This is what gets stored in SharedPreferences.
 */
data class AvatarConfig(
    val id: String,
    val name: String,
    val type: AvatarType,
    val isBuiltIn: Boolean,
    /** Type-specific data, e.g., file paths, settings. */
    val data: Map<String, Any>
) {
    fun getBasePath(): String? {
        return (data["folderPath"] as? String) ?: (data["basePath"] as? String)
    }
}

/**
 * Data class for avatar instance settings like scale and position.
 */
data class AvatarInstanceSettings(
    val scale: Float = 1.0f,
    val translateX: Float = 0f,
    val translateY: Float = 0f
)

/**
 * Data class for global avatar settings.
 */
data class AvatarSettings(
    val currentAvatarId: String?
)

/**
 * Defines a contract for type-specific avatar persistence logic.
 * Each supported AvatarType should have an implementation of this delegate.
 */
interface AvatarPersistenceDelegate {
    val type: AvatarType

    /**
     * Scans a directory to find and parse model configurations for this delegate's avatar type.
     * @param directory The directory to scan. It could be a user directory or a temporary
     *                  directory from a ZIP import.
     * @param isBuiltIn Flag to mark if the scanned models are built-in assets.
     * @return A list of [AvatarConfig] found in the directory.
     */
    fun scanDirectory(directory: File, isBuiltIn: Boolean): List<AvatarConfig>
}

/**
 * Persistence delegate for DragonBones models.
 */
class DragonBonesPersistenceDelegate : AvatarPersistenceDelegate {
    override val type = AvatarType.DRAGONBONES

    override fun scanDirectory(directory: File, isBuiltIn: Boolean): List<AvatarConfig> {
        val allConfigs = mutableListOf<AvatarConfig>()
        if (!directory.exists() || !directory.isDirectory) return allConfigs

        val skeletonFile = directory.listFiles { f -> f.isFile && f.extension == "json" && !f.name.endsWith("_tex.json") }?.firstOrNull()
        if (skeletonFile == null) return allConfigs

        val modelName = skeletonFile.nameWithoutExtension.removeSuffix("_ske")
        val textureJsonFile = File(directory, "${modelName}_tex.json")
        val textureImageFile = File(directory, "${modelName}_tex.png")

        if (textureJsonFile.exists() && textureImageFile.exists()) {
            val config = AvatarConfig(
                id = if (isBuiltIn) "built_in_db_${directory.name}" else "user_db_${directory.name}_${System.currentTimeMillis()}",
                name = directory.name,
                type = AvatarType.DRAGONBONES,
                isBuiltIn = isBuiltIn,
                data = mapOf(
                    "folderPath" to directory.absolutePath,
                    "skeletonFile" to skeletonFile.name,
                    "textureJsonFile" to textureJsonFile.name,
                    "textureImageFile" to textureImageFile.name,
                    "isBuiltIn" to isBuiltIn
                )
            )
            allConfigs.add(config)
        }
        return allConfigs
    }
}

/**
 * Persistence delegate for WebP models.
 */
class WebPPersistenceDelegate : AvatarPersistenceDelegate {
    override val type = AvatarType.WEBP

    override fun scanDirectory(directory: File, isBuiltIn: Boolean): List<AvatarConfig> {
        val allConfigs = mutableListOf<AvatarConfig>()
        if (!directory.exists() || !directory.isDirectory) return allConfigs

        // Check if directory contains any WebP files
        val webpFiles = directory.listFiles { file ->
            file.isFile && file.extension.equals("webp", ignoreCase = true)
        } ?: emptyArray()

        if (webpFiles.isNotEmpty()) {
            val config = AvatarConfig(
                id = if (isBuiltIn) "built_in_webp_${directory.name}" else "user_webp_${directory.name}_${System.currentTimeMillis()}",
                name = directory.name,
                type = AvatarType.WEBP,
                isBuiltIn = isBuiltIn,
                data = mapOf(
                    "basePath" to directory.absolutePath,
                    "webpFiles" to webpFiles.map { it.name }
                )
            )
            allConfigs.add(config)
        }
        return allConfigs
    }
}

/**
 * A generic repository for managing all types of virtual avatars.
 * It handles loading, saving, and managing avatar configurations from both
 * built-in assets and user-provided files.
 */
class AvatarRepository(
    private val context: Context,
    private val modelFactory: AvatarModelFactory
) {

    companion object {
        private const val TAG = "AvatarRepository"
        private const val PREFS_NAME = "avatar_preferences"
        private const val KEY_CONFIGS = "avatar_configs"
        private const val KEY_SETTINGS = "avatar_settings"
        private const val KEY_INSTANCE_SETTINGS = "avatar_instance_settings"

        private const val ASSETS_AVATAR_DIR = "pets"
        private const val USER_AVATAR_DIR = "avatars"

        @Volatile private var INSTANCE: AvatarRepository? = null

        fun getInstance(context: Context, modelFactory: AvatarModelFactory): AvatarRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AvatarRepository(context.applicationContext, modelFactory).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val delegates: Map<AvatarType, AvatarPersistenceDelegate> = listOf(
        DragonBonesPersistenceDelegate(),
        WebPPersistenceDelegate()
    ).associateBy { it.type }

    private val _configs = MutableStateFlow<List<AvatarConfig>>(emptyList())
    val configs: StateFlow<List<AvatarConfig>> = _configs.asStateFlow()

    private val _currentAvatar = MutableStateFlow<AvatarModel?>(null)
    val currentAvatar: StateFlow<AvatarModel?> = _currentAvatar.asStateFlow()
    
    private val _instanceSettings = MutableStateFlow<Map<String, AvatarInstanceSettings>>(emptyMap())
    val instanceSettings: StateFlow<Map<String, AvatarInstanceSettings>> = _instanceSettings.asStateFlow()
    
    private val userAvatarDir: File by lazy {
        File(context.getExternalFilesDir(null), USER_AVATAR_DIR)
    }

    init {
        userAvatarDir.mkdirs()
        synchronizeAssets()
        loadAvatars()
    }

    private fun synchronizeAssets() {
        try {
            val assetManager = context.assets
            val avatarTypeDirs = assetManager.list(ASSETS_AVATAR_DIR)?.filter {
                try { assetManager.list("$ASSETS_AVATAR_DIR/$it")?.isNotEmpty() == true } catch (e: Exception) { false }
            } ?: return

            for (typeDir in avatarTypeDirs) {
                val modelFolders = assetManager.list("$ASSETS_AVATAR_DIR/$typeDir") ?: continue
                for (modelFolder in modelFolders) {
                    val destDir = File(userAvatarDir, modelFolder)
                    if (!destDir.exists()) {
                        AppLogger.d(TAG, "Populating asset model '$modelFolder' of type '$typeDir'")
                        copyAssetDirectory("$ASSETS_AVATAR_DIR/$typeDir/$modelFolder", destDir.absolutePath)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error synchronizing assets: ${e.message}", e)
        }
    }

    private fun loadAvatars() {
        val configsFromPrefs = loadConfigsFromPrefs()
        val configsFromDisk = scanUserAvatarDirectory()
        
        val finalConfigs = (configsFromDisk.map { disk -> configsFromPrefs.find { it.id == disk.id } ?: disk } +
                configsFromPrefs.filter { pref -> configsFromDisk.none { it.id == pref.id } && pref.getBasePath()?.let { File(it).exists() } == true })
            .distinctBy { it.id }

        _configs.value = finalConfigs
        saveConfigsToPrefs(finalConfigs)

        _instanceSettings.value = loadInstanceSettingsFromPrefs()

        val settings = loadSettingsFromPrefs()
        updateCurrentAvatar(settings.currentAvatarId)
    }

    private fun scanUserAvatarDirectory(): List<AvatarConfig> {
        val modelFolders = userAvatarDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        return modelFolders.flatMap { folder ->
            delegates.values.flatMap { delegate ->
                val isBuiltIn = isPathFromAssets(folder.path)
                delegate.scanDirectory(folder, isBuiltIn)
            }
        }
    }
    
    private fun isPathFromAssets(path: String): Boolean {
        // A simple heuristic to determine if a model was copied from assets.
        // This could be improved by storing metadata.
        return _configs.value.any { it.isBuiltIn && it.getBasePath() == path }
    }

    suspend fun refreshAvatars() = withContext(Dispatchers.IO) {
        loadAvatars()
    }
    
    fun switchAvatar(avatarId: String) {
        val currentSettings = loadSettingsFromPrefs()
        if (currentSettings.currentAvatarId != avatarId) {
            saveSettingsToPrefs(currentSettings.copy(currentAvatarId = avatarId))
            updateCurrentAvatar(avatarId)
        }
    }

    private fun updateCurrentAvatar(targetId: String?) {
        val config = _configs.value.find { it.id == targetId }
            ?: _configs.value.firstOrNull()

        if (config == null) {
            _currentAvatar.value = null
            return
        }
        
        _currentAvatar.value = modelFactory.createModel(
            id = config.id,
            name = config.name,
            type = config.type,
            data = config.data
        )

        if (config.id != loadSettingsFromPrefs().currentAvatarId) {
            saveSettingsToPrefs(AvatarSettings(currentAvatarId = config.id))
        }
    }

    suspend fun deleteAvatar(avatarId: String): Boolean = withContext(Dispatchers.IO) {
        val config = _configs.value.find { it.id == avatarId } ?: return@withContext false
        if (config.isBuiltIn) {
            AppLogger.w(TAG, "Cannot delete a built-in avatar.")
            return@withContext false
        }

        val folderPath = config.getBasePath()
        if (folderPath == null) {
            AppLogger.e(TAG, "Avatar config for ${config.id} is missing folderPath or basePath.")
            return@withContext false
        }
        val modelDir = File(folderPath)

        val deletionSucceeded = if (modelDir.exists()) {
            modelDir.deleteRecursively()
        } else {
            AppLogger.w(TAG, "Avatar directory to delete did not exist, proceeding to remove config entry: $folderPath")
            true // If directory doesn't exist, we can still proceed to remove it from config
        }

        if (deletionSucceeded) {
            val updatedConfigs = _configs.value.filter { it.id != avatarId }
            _configs.value = updatedConfigs
            saveConfigsToPrefs(updatedConfigs)

            if (loadSettingsFromPrefs().currentAvatarId == avatarId) {
                updateCurrentAvatar(updatedConfigs.firstOrNull()?.id)
            }
            AppLogger.i(TAG, "Successfully removed avatar config: ${config.name}")
            true
        } else {
            AppLogger.e(TAG, "Failed to delete avatar directory: $folderPath")
            false
        }
    }
    
    fun updateAvatarSettings(avatarId: String, newSettings: AvatarInstanceSettings) {
        val updatedSettings = _instanceSettings.value.toMutableMap()
        updatedSettings[avatarId] = newSettings
        _instanceSettings.value = updatedSettings
        saveInstanceSettingsToPrefs(updatedSettings)
    }

    fun getAvatarSettings(avatarId: String): AvatarInstanceSettings {
        return _instanceSettings.value[avatarId] ?: AvatarInstanceSettings()
    }
    
    suspend fun importAvatarFromZip(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "avatar_import_${System.currentTimeMillis()}")
        try {
            tempDir.mkdirs()
            context.contentResolver.openInputStream(uri)?.use {
                ZipInputStream(it).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val file = File(tempDir, entry.name)
                        if(entry.isDirectory) {
                           file.mkdirs()
                        } else {
                           file.outputStream().use(zis::copyTo)
                        }
                        entry = zis.nextEntry
                    }
                }
            } ?: return@withContext false
            
            val foundConfigs = tempDir.listFiles {f -> f.isDirectory}?.flatMap { folder ->
                delegates.values.flatMap { delegate -> delegate.scanDirectory(folder, false) }
            } ?: emptyList()
            
            if (foundConfigs.isEmpty()) {
                AppLogger.w(TAG, "No valid avatar configs found in the imported ZIP.")
                return@withContext false
            }

            foundConfigs.forEach { config ->
                val sourcePath = config.getBasePath()
                if (sourcePath != null) {
                    val sourceDir = File(sourcePath)
                    if (sourceDir.exists()) {
                        val targetDir = File(userAvatarDir, sourceDir.name)
                        sourceDir.copyRecursively(targetDir, true)
                        AppLogger.i(TAG, "Imported avatar model: ${sourceDir.name}")
                    } else {
                        AppLogger.w(TAG, "Source directory does not exist for imported config: ${config.id} at $sourcePath")
                    }
                } else {
                    AppLogger.w(TAG, "Could not find path for imported config: ${config.id}")
                }
            }
            refreshAvatars()
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import avatar from ZIP", e)
            false
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun copyAssetDirectory(srcPath: String, dstPath: String) {
        val assetManager = context.assets
        val files = assetManager.list(srcPath) ?: return
        File(dstPath).mkdirs()
        files.forEach { file ->
            try {
                assetManager.open("$srcPath/$file").use { input ->
                    FileOutputStream("$dstPath/$file").use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                copyAssetDirectory("$srcPath/$file", "$dstPath/$file")
            }
        }
    }

    private fun loadConfigsFromPrefs(): List<AvatarConfig> {
        val json = prefs.getString(KEY_CONFIGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AvatarConfig>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing avatar configs from JSON", e)
            emptyList()
        }
    }

    private fun saveConfigsToPrefs(configs: List<AvatarConfig>) {
        val json = gson.toJson(configs)
        prefs.edit { putString(KEY_CONFIGS, json) }
    }

    private fun loadInstanceSettingsFromPrefs(): Map<String, AvatarInstanceSettings> {
        val json = prefs.getString(KEY_INSTANCE_SETTINGS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, AvatarInstanceSettings>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing instance settings from JSON", e)
            emptyMap()
        }
    }

    private fun saveInstanceSettingsToPrefs(settings: Map<String, AvatarInstanceSettings>) {
        val json = gson.toJson(settings)
        prefs.edit { putString(KEY_INSTANCE_SETTINGS, json) }
    }

    private fun loadSettingsFromPrefs(): AvatarSettings {
        val json = prefs.getString(KEY_SETTINGS, null)
        return if (json != null) {
            try {
                gson.fromJson(json, AvatarSettings::class.java)
            } catch (e: Exception) {
                AvatarSettings(null)
            }
        } else {
            AvatarSettings(null)
        }
    }

    private fun saveSettingsToPrefs(settings: AvatarSettings) {
        val json = gson.toJson(settings)
        prefs.edit { putString(KEY_SETTINGS, json) }
    }
} 