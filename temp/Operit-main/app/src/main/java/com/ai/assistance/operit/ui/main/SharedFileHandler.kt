package com.ai.assistance.operit.ui.main

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton to handle shared files from external apps
 * Manages the state flow of shared file URIs between MainActivity and AIChatScreen
 */
object SharedFileHandler {
    private val _sharedFiles = MutableStateFlow<List<Uri>?>(null)
    val sharedFiles: StateFlow<List<Uri>?> = _sharedFiles
    
    /**
     * Set the shared files to be processed
     */
    fun setSharedFiles(uris: List<Uri>) {
        _sharedFiles.value = uris
    }
    
    /**
     * Clear the shared files after processing
     */
    fun clearSharedFiles() {
        _sharedFiles.value = null
    }
}

