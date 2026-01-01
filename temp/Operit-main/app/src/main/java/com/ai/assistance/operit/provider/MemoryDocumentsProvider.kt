package com.ai.assistance.operit.provider

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Log
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class MemoryDocumentsProvider : DocumentsProvider() {

    companion object {
        private const val TAG = "MemoryDocumentsProvider"
        private const val AUTHORITY = "com.ai.assistance.operit.documents.memory"
        private const val ROOT_ID = "memory_root"

        private const val DOC_ID_ROOT = "root"
        private const val DOC_ID_PROFILE_PREFIX = "profile:"
        private const val DOC_ID_DIR_PREFIX = "dir:"
        private const val DOC_ID_MEM_PREFIX = "mem:"
        private const val DOC_ID_UNCATEGORIZED = "__uncategorized__"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }

    private val repositoryCache: MutableMap<String, MemoryRepository> = mutableMapOf()
    private val writeBackExecutor = Executors.newSingleThreadExecutor()

    override fun attachInfo(context: Context?, info: ProviderInfo?) {
        Log.e(
            TAG,
            "attachInfo context=${context?.packageName} authority=${info?.authority} exported=${info?.exported}"
        )
        super.attachInfo(context, info)
    }

    override fun onCreate(): Boolean {
        return try {
            val context = context ?: return false
            AppLogger.bindContext(context)
            Log.e(TAG, "onCreate package=${context.packageName}")
            AppLogger.i(TAG, "MemoryDocumentsProvider initialized for ${context.packageName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            AppLogger.e(TAG, "Failed to initialize provider", e)
            false
        }
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String? {
        Log.e(
            TAG,
            "moveDocument source=$sourceDocumentId sourceParent=$sourceParentDocumentId targetParent=$targetParentDocumentId"
        )
        AppLogger.i(
            TAG,
            "moveDocument source=$sourceDocumentId sourceParent=$sourceParentDocumentId targetParent=$targetParentDocumentId"
        )

        return try {
            val source = parseDocumentId(sourceDocumentId)
            val targetParent = parseDocumentId(targetParentDocumentId)

            if (targetParent is DocRef.Root) {
                throw IllegalArgumentException("Cannot move into root")
            }
            if (targetParent is DocRef.Memory) {
                throw IllegalArgumentException("Target parent is not a directory: $targetParentDocumentId")
            }

            try {
                when (parseDocumentId(sourceParentDocumentId)) {
                    is DocRef.Memory -> throw IllegalArgumentException("Source parent is not a directory: $sourceParentDocumentId")
                    else -> {
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "moveDocument: failed to parse sourceParentDocumentId=$sourceParentDocumentId", e)
            }

            when (source) {
                is DocRef.Root -> throw IllegalArgumentException("Cannot move root")
                is DocRef.Profile -> throw IllegalArgumentException("Cannot move profile")

                is DocRef.Memory -> {
                    val sourceProfileId = source.profileId
                    if (targetParent.profileId != sourceProfileId) {
                        throw IllegalArgumentException("Cross-profile move is not supported")
                    }

                    val repo = getRepository(sourceProfileId)
                    val memory = runBlocking { repo.findMemoryByUuid(source.uuid) }
                        ?: throw FileNotFoundException("Memory not found: ${source.uuid}")

                    val newFolderPath: String? = when (targetParent) {
                        is DocRef.Profile -> null
                        is DocRef.Directory -> if (targetParent.path == DOC_ID_UNCATEGORIZED) null else targetParent.path
                        is DocRef.Memory -> throw IllegalArgumentException("Target parent is not a directory")
                        is DocRef.Root -> throw IllegalArgumentException("Cannot move into root")
                    }

                    runBlocking {
                        repo.updateMemory(
                            memory = memory,
                            newTitle = memory.title,
                            newContent = memory.content,
                            newContentType = memory.contentType,
                            newSource = memory.source,
                            newCredibility = memory.credibility,
                            newImportance = memory.importance,
                            newFolderPath = newFolderPath,
                            newTags = null
                        )
                    }

                    sourceDocumentId
                }

                is DocRef.Directory -> {
                    val sourceProfileId = source.profileId
                    if (targetParent.profileId != sourceProfileId) {
                        throw IllegalArgumentException("Cross-profile move is not supported")
                    }
                    if (source.path == DOC_ID_UNCATEGORIZED) {
                        throw IllegalArgumentException("Cannot move uncategorized directory")
                    }
                    if (targetParent is DocRef.Directory && targetParent.path == DOC_ID_UNCATEGORIZED) {
                        throw IllegalArgumentException("Cannot move a directory into uncategorized")
                    }

                    if (targetParent is DocRef.Directory) {
                        if (targetParent.path == source.path || targetParent.path.startsWith(source.path + "/")) {
                            throw IllegalArgumentException("Cannot move a directory into itself")
                        }
                    }

                    val leafName = source.path.substringAfterLast('/')
                    val newPath = when (targetParent) {
                        is DocRef.Profile -> leafName
                        is DocRef.Directory -> targetParent.path + "/" + leafName
                        is DocRef.Memory -> throw IllegalArgumentException("Target parent is not a directory")
                        is DocRef.Root -> throw IllegalArgumentException("Cannot move into root")
                    }

                    if (newPath == source.path) {
                        return sourceDocumentId
                    }

                    val repo = getRepository(sourceProfileId)
                    val existingPaths = runBlocking { repo.getAllFolderPaths() }
                        .filter { it.isNotBlank() && it != "未分类" }

                    val conflict = existingPaths.any { p ->
                        (p == newPath || p.startsWith(newPath + "/")) &&
                            !(p == source.path || p.startsWith(source.path + "/"))
                    }
                    if (conflict) {
                        throw IllegalStateException("Target folder already exists: $newPath")
                    }

                    val ok = runBlocking { repo.renameFolder(source.path, newPath) }
                    if (!ok) {
                        throw IllegalStateException("Failed to move folder: ${source.path} -> $newPath")
                    }
                    DOC_ID_DIR_PREFIX + sourceProfileId + ":" + newPath
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "moveDocument failed", e)
            AppLogger.e(TAG, "moveDocument failed", e)
            throw e
        }
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Log.e(TAG, "queryRoots projection=${projection?.joinToString()}")
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "application/json\ntext/plain\n*/*")
        row.add(
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
        )
        row.add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_info_details)
        row.add(DocumentsContract.Root.COLUMN_TITLE, "Operit Memory Library")
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, "Access memory items")
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, DOC_ID_ROOT)
        row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, 0L)

        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        Log.e(TAG, "queryDocument documentId=$documentId projection=${projection?.joinToString()}")
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeDocument(result, documentId)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        Log.e(
            TAG,
            "queryChildDocuments parent=$parentDocumentId projection=${projection?.joinToString()} sortOrder=$sortOrder"
        )
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val prefs = UserPreferencesManager.getInstance(context ?: throw IllegalStateException("Context is null"))

        when (val parent = parseDocumentId(parentDocumentId)) {
            is DocRef.Root -> {
                val profileIds = runBlocking { prefs.profileListFlow.first() }
                profileIds.forEach { profileId ->
                    val profile = runBlocking { prefs.getUserPreferencesFlow(profileId).first() }
                    includeProfile(result, profile)
                }
            }

            is DocRef.Profile -> {
                val repo = getRepository(parent.profileId)
                val folderPaths = runBlocking { repo.getAllFolderPaths() }

                val normalizedFolders = normalizeFolders(folderPaths)
                val topLevel = normalizedFolders
                    .mapNotNull { it.split('/').firstOrNull()?.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                if (folderPaths.contains("未分类")) {
                    includeDirectory(result, parent.profileId, DOC_ID_UNCATEGORIZED, "未分类")
                }

                topLevel.forEach { name ->
                    includeDirectory(result, parent.profileId, name, name)
                }
            }

            is DocRef.Directory -> {
                val repo = getRepository(parent.profileId)
                val folderPaths = runBlocking { repo.getAllFolderPaths() }

                if (parent.path == DOC_ID_UNCATEGORIZED) {
                    includeMemoriesForFolder(result, parent.profileId, repo, folderPaths = folderPaths, folderPath = null)
                    return result
                }

                val normalizedFolders = normalizeFolders(folderPaths)
                val directSubfolders = normalizedFolders
                    .filter { it.startsWith(parent.path + "/") }
                    .mapNotNull { it.removePrefix(parent.path + "/").split('/').firstOrNull() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                directSubfolders.forEach { childName ->
                    val childPath = parent.path + "/" + childName
                    includeDirectory(result, parent.profileId, childPath, childName)
                }

                includeMemoriesForFolder(result, parent.profileId, repo, folderPaths = folderPaths, folderPath = parent.path)
            }

            is DocRef.Memory -> return result
        }

        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        Log.e(TAG, "openDocument documentId=$documentId mode=$mode")
        AppLogger.i(TAG, "openDocument documentId=$documentId mode=$mode")
        val ref = parseDocumentId(documentId)
        if (ref !is DocRef.Memory) {
            throw FileNotFoundException("Document is not a file: $documentId")
        }

        val repo = getRepository(ref.profileId)

        val wantsWrite = mode.contains('w') || mode.contains('W')
        return if (!wantsWrite) {
            val jsonString = runBlocking {
                val memory = repo.findMemoryByUuid(ref.uuid)
                    ?: throw FileNotFoundException("Memory not found: ${ref.uuid}")
                buildMemoryJson(memory)
            }

            val bytes = jsonString.toByteArray(StandardCharsets.UTF_8)

            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            thread(name = "MemoryDocPipeWriter") {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                        out.write(bytes)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to write memory document", e)
                    try {
                        writeSide.close()
                    } catch (_: Exception) {
                    }
                }
            }

            readSide
        } else {
            val context = context ?: throw IllegalStateException("Context is null")

            val jsonString = runBlocking {
                val memory = repo.findMemoryByUuid(ref.uuid)
                    ?: throw FileNotFoundException("Memory not found: ${ref.uuid}")
                buildMemoryJson(memory)
            }

            val tempFile = File(context.cacheDir, "memory_doc_${ref.uuid}_${System.currentTimeMillis()}.json")
            tempFile.writeText(jsonString)
            AppLogger.i(TAG, "openDocument(write) tempFile=${tempFile.absolutePath}")

            val accessMode = ParcelFileDescriptor.parseMode(mode)
            val handler = Handler(Looper.getMainLooper())

            ParcelFileDescriptor.open(tempFile, accessMode, handler) {
                val tempPath = tempFile.absolutePath
                AppLogger.i(TAG, "openDocument(write) onClose uuid=${ref.uuid} tempPath=$tempPath")
                writeBackExecutor.execute {
                    try {
                        AppLogger.i(TAG, "writeBack start uuid=${ref.uuid}")
                        val writtenText = File(tempPath).readText()
                        applyWrittenContentToMemory(repo, ref.uuid, writtenText)
                        AppLogger.i(TAG, "writeBack done uuid=${ref.uuid} chars=${writtenText.length}")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to apply written content for ${ref.uuid}", e)
                    } finally {
                        try {
                            File(tempPath).delete()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String? {
        AppLogger.i(TAG, "createDocument parent=$parentDocumentId mimeType=$mimeType displayName=$displayName")
        val parent = parseDocumentId(parentDocumentId)
        if (parent is DocRef.Memory) {
            throw IllegalArgumentException("Parent is not a directory: $parentDocumentId")
        }
        if (parent is DocRef.Root) {
            throw IllegalArgumentException("Cannot create profiles from provider")
        }

        val repo = getRepository(parent.profileId)

        val cleanName = displayName.trim().trim('/').trim()
        if (cleanName.isBlank()) {
            throw IllegalArgumentException("displayName is blank")
        }
        if (cleanName.contains('/')) {
            throw IllegalArgumentException("displayName contains '/': $displayName")
        }
        if (cleanName.contains(':')) {
            throw IllegalArgumentException("displayName contains ':': $displayName")
        }

        return if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            val newFolderPath = when (parent) {
                is DocRef.Profile -> cleanName
                is DocRef.Directory -> {
                    if (parent.path == DOC_ID_UNCATEGORIZED) cleanName else parent.path + "/" + cleanName
                }
                is DocRef.Memory -> throw IllegalArgumentException("Parent is not a directory")
                is DocRef.Root -> throw IllegalArgumentException("Cannot create profiles from provider")
            }

            val ok = runBlocking { repo.createFolder(newFolderPath) }
            if (!ok) {
                throw IllegalStateException("Failed to create folder: $newFolderPath")
            }
            DOC_ID_DIR_PREFIX + parent.profileId + ":" + newFolderPath
        } else {
            val title = if (cleanName.endsWith(".json", ignoreCase = true)) {
                cleanName.dropLast(5)
            } else {
                cleanName
            }

            val folderPathForMemory: String? = when (parent) {
                is DocRef.Profile -> null
                is DocRef.Directory -> if (parent.path == DOC_ID_UNCATEGORIZED) null else parent.path
                is DocRef.Memory -> null
                is DocRef.Root -> throw IllegalArgumentException("Cannot create profiles from provider")
            }

            val memory = Memory(
                title = title,
                content = "",
                contentType = "text/plain",
                source = "documents_provider",
                folderPath = folderPathForMemory
            )

            runBlocking {
                repo.saveMemory(memory)
            }

            DOC_ID_MEM_PREFIX + parent.profileId + ":" + memory.uuid
        }
    }

    override fun deleteDocument(documentId: String) {
        AppLogger.i(TAG, "deleteDocument documentId=$documentId")
        when (val ref = parseDocumentId(documentId)) {
            is DocRef.Root -> throw IllegalArgumentException("Cannot delete root")
            is DocRef.Profile -> throw IllegalArgumentException("Cannot delete profile from provider")
            is DocRef.Memory -> {
                val repo = getRepository(ref.profileId)
                val memory = runBlocking { repo.findMemoryByUuid(ref.uuid) }
                    ?: throw FileNotFoundException("Memory not found: ${ref.uuid}")
                val ok = runBlocking { repo.deleteMemoryAndIndex(memory.id) }
                if (!ok) {
                    throw IllegalStateException("Failed to delete memory: ${ref.uuid}")
                }
            }
            is DocRef.Directory -> {
                val repo = getRepository(ref.profileId)
                if (ref.path == DOC_ID_UNCATEGORIZED) {
                    throw IllegalArgumentException("Cannot delete uncategorized directory")
                }

                val memories = runBlocking { repo.getMemoriesByFolderPath(ref.path) }
                val uuids = memories.map { it.uuid }.toSet()
                val ok = runBlocking { repo.deleteMemoriesByUuids(uuids) }
                if (!ok) {
                    throw IllegalStateException("Failed to delete folder and its contents: ${ref.path}")
                }
            }
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        AppLogger.i(TAG, "renameDocument documentId=$documentId displayName=$displayName")
        val prefs = UserPreferencesManager.getInstance(context ?: throw IllegalStateException("Context is null"))
        val cleanName = displayName.trim().trim('/').trim()
        if (cleanName.isBlank()) {
            throw IllegalArgumentException("displayName is blank")
        }
        if (cleanName.contains('/')) {
            throw IllegalArgumentException("displayName contains '/': $displayName")
        }
        if (cleanName.contains(':')) {
            throw IllegalArgumentException("displayName contains ':': $displayName")
        }

        return when (val ref = parseDocumentId(documentId)) {
            is DocRef.Root -> throw IllegalArgumentException("Cannot rename root")

            is DocRef.Profile -> {
                val profile = runBlocking { prefs.getUserPreferencesFlow(ref.profileId).first() }
                runBlocking {
                    prefs.updateProfile(profile.copy(name = cleanName))
                }
                DOC_ID_PROFILE_PREFIX + ref.profileId
            }

            is DocRef.Directory -> {
                val repo = getRepository(ref.profileId)
                if (ref.path == DOC_ID_UNCATEGORIZED) {
                    throw IllegalArgumentException("Cannot rename uncategorized directory")
                }

                val parentPrefix = ref.path.substringBeforeLast('/', "")
                val newPath = if (parentPrefix.isBlank()) cleanName else parentPrefix + "/" + cleanName
                val ok = runBlocking { repo.renameFolder(ref.path, newPath) }
                if (!ok) {
                    throw IllegalStateException("Failed to rename folder: ${ref.path} -> $newPath")
                }
                DOC_ID_DIR_PREFIX + ref.profileId + ":" + newPath
            }

            is DocRef.Memory -> {
                val repo = getRepository(ref.profileId)
                val memory = runBlocking { repo.findMemoryByUuid(ref.uuid) }
                    ?: throw FileNotFoundException("Memory not found: ${ref.uuid}")

                val newTitle = if (cleanName.endsWith(".json", ignoreCase = true)) {
                    cleanName.dropLast(5)
                } else {
                    cleanName
                }

                runBlocking {
                    repo.updateMemory(
                        memory = memory,
                        newTitle = newTitle,
                        newContent = memory.content,
                        newContentType = memory.contentType,
                        newSource = memory.source,
                        newCredibility = memory.credibility,
                        newImportance = memory.importance,
                        newFolderPath = memory.folderPath,
                        newTags = null
                    )
                }
                DOC_ID_MEM_PREFIX + ref.profileId + ":" + ref.uuid
            }
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        Log.e(TAG, "isChildDocument parent=$parentDocumentId doc=$documentId")
        val parent = try {
            parseDocumentId(parentDocumentId)
        } catch (_: Exception) {
            return false
        }
        val child = try {
            parseDocumentId(documentId)
        } catch (_: Exception) {
            return false
        }

        return when (parent) {
            is DocRef.Root -> {
                when (child) {
                    is DocRef.Profile,
                    is DocRef.Directory,
                    is DocRef.Memory -> true

                    is DocRef.Root -> false
                }
            }

            is DocRef.Profile -> {
                when (child) {
                    is DocRef.Directory -> child.profileId == parent.profileId
                    is DocRef.Memory -> child.profileId == parent.profileId
                    else -> false
                }
            }

            is DocRef.Directory -> {
                when (child) {
                    is DocRef.Directory -> {
                        child.profileId == parent.profileId &&
                            (child.path == parent.path || child.path.startsWith(parent.path + "/"))
                    }

                    is DocRef.Memory -> {
                        if (child.profileId != parent.profileId) return false
                        val repo = getRepository(parent.profileId)
                        val memory = runBlocking { repo.findMemoryByUuid(child.uuid) } ?: return false
                        val fp = memory.folderPath
                        if (parent.path == DOC_ID_UNCATEGORIZED) {
                            fp.isNullOrEmpty()
                        } else {
                            fp == parent.path
                        }
                    }

                    else -> false
                }
            }

            is DocRef.Memory -> false
        }
    }

    private fun includeDocument(result: MatrixCursor, documentId: String) {
        when (val ref = parseDocumentId(documentId)) {
            is DocRef.Root -> {
                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_ROOT)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Operit Memory")
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0L)
                row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
            }

            is DocRef.Profile -> {
                val prefs = UserPreferencesManager.getInstance(context ?: throw IllegalStateException("Context is null"))
                val profile = runBlocking { prefs.getUserPreferencesFlow(ref.profileId).first() }
                val displayName = getProfileDisplayName(profile)

                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_PROFILE_PREFIX + ref.profileId)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0L)
                row.add(
                    DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                )
                row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
            }

            is DocRef.Directory -> {
                val repo = getRepository(ref.profileId)
                var flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                if (ref.path != DOC_ID_UNCATEGORIZED) {
                    flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                    flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                    flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_MOVE
                }

                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_DIR_PREFIX + ref.profileId + ":" + ref.path)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, ref.displayName)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0L)
                row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
                row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
            }

            is DocRef.Memory -> {
                val repo = getRepository(ref.profileId)
                val memory = runBlocking { repo.findMemoryByUuid(ref.uuid) }
                    ?: throw FileNotFoundException("Memory not found: ${ref.uuid}")

                val displayName = buildMemoryDisplayName(
                    title = memory.title,
                    uuid = memory.uuid,
                    countInFolder = countMemoriesWithSameTitleInFolder(repo, memory)
                )
                val lastModified = memory.updatedAt.time
                val size = buildMemoryJson(memory).toByteArray(StandardCharsets.UTF_8).size.toLong()

                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_MEM_PREFIX + ref.profileId + ":" + ref.uuid)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/json")
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified)
                row.add(
                    DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                        DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                        DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                        DocumentsContract.Document.FLAG_SUPPORTS_MOVE
                )
                row.add(DocumentsContract.Document.COLUMN_SIZE, size)
            }
        }
    }

    private fun includeProfile(result: MatrixCursor, profile: PreferenceProfile) {
        val displayName = getProfileDisplayName(profile)
        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_PROFILE_PREFIX + profile.id)
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0L)
        row.add(
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        )
        row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
    }

    private fun includeDirectory(result: MatrixCursor, profileId: String, path: String, displayName: String) {
        var flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        if (path != DOC_ID_UNCATEGORIZED) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_MOVE
        }

        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_DIR_PREFIX + profileId + ":" + path)
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0L)
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
    }

    private fun includeMemoriesForFolder(
        result: MatrixCursor,
        profileId: String,
        repo: MemoryRepository,
        folderPaths: List<String>,
        folderPath: String?
    ) {
        val targetFolderPath = folderPath ?: "未分类"

        val memoriesInScope = runBlocking {
            repo.searchMemories(query = "*", folderPath = targetFolderPath, semanticThreshold = 0.0f)
        }

        val directMemories = memoriesInScope
            .filter { it.title != ".folder_placeholder" && it.title != "文件夹说明" }
            .filter {
                val fp = it.folderPath
                if (folderPath == null) {
                    fp.isNullOrEmpty()
                } else {
                    fp == folderPath
                }
            }

        val titleCounts = directMemories
            .groupingBy { it.title }
            .eachCount()

        directMemories
            .sortedBy { it.title.lowercase() }
            .forEach { memory ->
                val displayName = buildMemoryDisplayName(memory.title, memory.uuid, titleCounts[memory.title] ?: 0)
                val lastModified = memory.updatedAt.time
                val size = buildMemoryJson(memory).toByteArray(StandardCharsets.UTF_8).size.toLong()

                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_MEM_PREFIX + profileId + ":" + memory.uuid)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/json")
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified)
                row.add(
                    DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                        DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                        DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                        DocumentsContract.Document.FLAG_SUPPORTS_MOVE
                )
                row.add(DocumentsContract.Document.COLUMN_SIZE, size)
            }
    }

    private fun getProfileDisplayName(profile: PreferenceProfile): String {
        return profile.name
            .ifBlank { profile.personality }
            .ifBlank { profile.id }
    }

    private fun countMemoriesWithSameTitleInFolder(repo: MemoryRepository, memory: Memory): Int {
        val targetFolderPath = if (memory.folderPath.isNullOrEmpty()) "未分类" else memory.folderPath
        val memoriesInScope = runBlocking {
            repo.searchMemories(query = "*", folderPath = targetFolderPath, semanticThreshold = 0.0f)
        }

        val directMemories = memoriesInScope
            .filter { it.title != ".folder_placeholder" && it.title != "文件夹说明" }
            .filter {
                val fp = it.folderPath
                if (memory.folderPath.isNullOrEmpty()) {
                    fp.isNullOrEmpty()
                } else {
                    fp == memory.folderPath
                }
            }

        return directMemories.count { it.title == memory.title }
    }

    private fun applyWrittenContentToMemory(repo: MemoryRepository, uuid: String, writtenText: String) {
        runBlocking {
            val memory = repo.findMemoryByUuid(uuid) ?: return@runBlocking
            try {
                val json = JSONObject(writtenText)
                val newTitle = json.optString("title", memory.title)
                val newContent = json.optString("content", memory.content)
                val newContentType = json.optString("contentType", memory.contentType)
                val newSource = json.optString("source", memory.source)
                val newCredibility = json.optDouble("credibility", memory.credibility.toDouble()).toFloat()
                val newImportance = json.optDouble("importance", memory.importance.toDouble()).toFloat()

                val folderPathRaw = json.opt("folderPath")
                val folderPathStr = if (folderPathRaw == null || folderPathRaw == JSONObject.NULL) {
                    null
                } else {
                    folderPathRaw.toString()
                }
                val newFolderPath = when {
                    folderPathStr.isNullOrBlank() -> null
                    folderPathStr == "未分类" -> null
                    else -> folderPathStr
                }

                repo.updateMemory(
                    memory = memory,
                    newTitle = newTitle,
                    newContent = newContent,
                    newContentType = newContentType,
                    newSource = newSource,
                    newCredibility = newCredibility,
                    newImportance = newImportance,
                    newFolderPath = newFolderPath,
                    newTags = null
                )
            } catch (_: Exception) {
                repo.updateMemory(
                    memory = memory,
                    newTitle = memory.title,
                    newContent = writtenText,
                    newContentType = "text/plain",
                    newSource = memory.source,
                    newCredibility = memory.credibility,
                    newImportance = memory.importance,
                    newFolderPath = memory.folderPath,
                    newTags = null
                )
            }
        }
    }

    private fun normalizeFolders(folderPaths: List<String>): List<String> {
        val paths = folderPaths
            .filter { it.isNotBlank() && it != "未分类" }
            .flatMap { full ->
                val parts = full.split('/').filter { it.isNotBlank() }
                val prefixes = mutableListOf<String>()
                var current = ""
                parts.forEach { part ->
                    current = if (current.isEmpty()) part else "$current/$part"
                    prefixes.add(current)
                }
                prefixes
            }
            .distinct()
            .sorted()
        return paths
    }

    private fun buildMemoryDisplayName(title: String, uuid: String, countInFolder: Int = 0): String {
        val base = sanitizeFileName(if (title.isBlank()) "untitled" else title)
        return if (countInFolder > 1) {
            "${base}_${uuid.take(8)}.json"
        } else {
            "$base.json"
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace("/", "_")
            .replace("\\", "_")
            .replace(":", "_")
            .replace("*", "_")
            .replace("?", "_")
            .replace("\"", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("|", "_")
            .trim()
            .ifBlank { "untitled" }
    }

    private fun buildMemoryJson(memory: com.ai.assistance.operit.data.model.Memory): String {
        val obj = JSONObject()
        obj.put("uuid", memory.uuid)
        obj.put("title", memory.title)
        obj.put("content", memory.content)
        obj.put("contentType", memory.contentType)
        obj.put("source", memory.source)
        obj.put("credibility", memory.credibility)
        obj.put("importance", memory.importance)
        obj.put("folderPath", memory.folderPath)
        obj.put("isDocumentNode", memory.isDocumentNode)
        obj.put("documentPath", memory.documentPath)
        obj.put("createdAt", memory.createdAt.time)
        obj.put("updatedAt", memory.updatedAt.time)
        obj.put("lastAccessedAt", memory.lastAccessedAt.time)
        return obj.toString(2)
    }

    private fun parseDocumentId(documentId: String): DocRef {
        return when {
            documentId == DOC_ID_ROOT -> DocRef.Root
            documentId.startsWith(DOC_ID_PROFILE_PREFIX) -> {
                val profileId = documentId.removePrefix(DOC_ID_PROFILE_PREFIX)
                DocRef.Profile(profileId = profileId)
            }
            documentId.startsWith(DOC_ID_DIR_PREFIX) -> {
                val encoded = documentId.removePrefix(DOC_ID_DIR_PREFIX)
                val parts = encoded.split(":", limit = 2)
                if (parts.size != 2) throw FileNotFoundException("Invalid directory documentId: $documentId")
                val profileId = parts[0]
                val path = parts[1]
                val displayName = if (path == DOC_ID_UNCATEGORIZED) {
                    "未分类"
                } else {
                    path.split('/').lastOrNull().orEmpty().ifBlank { path }
                }
                DocRef.Directory(profileId = profileId, path = path, displayName = displayName)
            }
            documentId.startsWith(DOC_ID_MEM_PREFIX) -> {
                val encoded = documentId.removePrefix(DOC_ID_MEM_PREFIX)
                val parts = encoded.split(":", limit = 2)
                if (parts.size != 2) throw FileNotFoundException("Invalid memory documentId: $documentId")
                DocRef.Memory(profileId = parts[0], uuid = parts[1])
            }
            else -> throw FileNotFoundException("Unknown documentId: $documentId")
        }
    }

    private fun getRepository(profileId: String): MemoryRepository {
        val context = context ?: throw IllegalStateException("Context is null")
        return synchronized(repositoryCache) {
            repositoryCache.getOrPut(profileId) { MemoryRepository(context, profileId) }
        }
    }

    private sealed class DocRef {
        abstract val profileId: String

        object Root : DocRef() {
            override val profileId: String
                get() = throw IllegalStateException("Root has no profileId")
        }

        data class Profile(override val profileId: String) : DocRef()

        data class Directory(
            override val profileId: String,
            val path: String,
            val displayName: String
        ) : DocRef()

        data class Memory(
            override val profileId: String,
            val uuid: String
        ) : DocRef()
    }
}
