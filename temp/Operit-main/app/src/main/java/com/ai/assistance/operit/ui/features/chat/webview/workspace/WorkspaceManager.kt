package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.annotation.SuppressLint
import com.ai.assistance.operit.util.AppLogger
import android.view.MotionEvent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.WebViewHandler
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeEditor
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeFormatter
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.LanguageDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.Serializable

/** 可序列化的位置数据类，用于持久化FAB位置 */
@Serializable
data class FabPosition(val x: Float = 0f, val y: Float = 0f)

/** 为[OpenFileInfo]添加扩展属性，用于判断是否为HTML文件 */
val OpenFileInfo.isHtml: Boolean
    get() = name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)

/** 为[OpenFileInfo]添加扩展属性，用于判断是否为图片文件 */
val OpenFileInfo.isImage: Boolean
    get() {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")
    }

/** VSCode风格的工作区管理器组件 集成了WebView预览和文件管理功能 */
@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceManager(
        actualViewModel: ChatViewModel,
        currentChat: ChatHistory,
        workspacePath: String,
        isVisible: Boolean,
        onExportClick: (workDir: File) -> Unit
) {
    val context = LocalContext.current
    val webViewRefreshCounter by actualViewModel.webViewRefreshCounter.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    
    // 读取工作区配置：在重新进入预览界面时从磁盘刷新
    var workspaceConfig by remember(workspacePath) {
        mutableStateOf(WorkspaceConfigReader.readConfig(workspacePath))
    }

    LaunchedEffect(isVisible, workspacePath) {
        if (isVisible) {
            workspaceConfig = WorkspaceConfigReader.readConfig(workspacePath)
        }
    }

    // 将 webViewHandler 和 webView 实例提升到 remember 中，使其在重组中保持稳定
    val webViewHandler =
            remember(context) {
                WebViewHandler(context).apply {
                    onFileChooserRequest = { intent, callback ->
                        actualViewModel.startFileChooserForResult(intent) { resultCode, data ->
                            callback(resultCode, data)
                        }
                    }
                }
            }

    val webView =
            remember(context) {
                WebView(context).apply {
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN ->
                                    v.parent.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP ->
                                    v.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    webViewHandler.configureWebView(this, WebViewHandler.WebViewMode.WORKSPACE, "workspace_preview_webview")
                    loadUrl(workspaceConfig.preview.url.ifEmpty { "http://localhost:8093" })
                }
            }

    var canWebViewGoBack by remember { mutableStateOf(false) }

    LaunchedEffect(webViewHandler) {
        webViewHandler.onCanGoBackChanged = { canGoBack ->
            canWebViewGoBack = canGoBack
        }
    }

    // 文件管理和标签状态 - 使用 rememberLocal 进行持久化
    var showFileManager by remember { mutableStateOf(false) }
    var openFiles by rememberLocal<List<OpenFileInfo>>(key = "open_files_$workspacePath", emptyList())
    var currentFileIndex by rememberLocal(key = "current_file_index_$workspacePath", -1)
    var filePreviewStates by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var unsavedFiles by rememberLocal<Set<String>>(key = "unsaved_files_$workspacePath", emptySet())
    
    // 控制可展开FAB的菜单状态
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    
    // 解绑确认对话框状态
    var showUnbindConfirmDialog by remember { mutableStateOf(false) }
    
    // 关闭文件确认对话框状态
    var fileToCloseIndex by remember { mutableStateOf(-1) }
    
    // 当前活动的编辑器引用
    var activeEditor by remember { mutableStateOf<com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.NativeCodeEditor?>(null) }

    // 监听WebView刷新计数器变化并触发刷新
    LaunchedEffect(webViewRefreshCounter) {
        if (webViewRefreshCounter > 0) {
            AppLogger.d("WorkspaceManager", "WebView refresh triggered, counter: $webViewRefreshCounter")
            // 确保webView已经加载完成后再刷新
            kotlinx.coroutines.delay(100) // 短暂延迟确保webView准备就绪
            webView.reload()
        }
    }

    // 当工作区可见时，检查文件更新
    LaunchedEffect(isVisible) {
        if (isVisible) {
            val updatedFiles = openFiles.map { fileInfo ->
                val currentFile = File(fileInfo.path)
                if (currentFile.exists() && currentFile.lastModified() > fileInfo.lastModified) {
                    // 文件已在外部被修改，重新加载内容
                    val tool = AITool("read_file_full", listOf(ToolParameter("path", fileInfo.path)))
                    val result = toolHandler.executeTool(tool)
                    if (result.success && result.result is com.ai.assistance.operit.core.tools.FileContentData) {
                        val newContent = (result.result as com.ai.assistance.operit.core.tools.FileContentData).content
                        
                        // 如果当前文件就是这个被修改的文件，则更新编辑器内容
                        if (openFiles.getOrNull(currentFileIndex)?.path == fileInfo.path) {
                             activeEditor?.replaceAllText(newContent)
                        }
                        
                        // 返回更新后的文件信息
                        fileInfo.copy(
                            content = newContent,
                            lastModified = currentFile.lastModified()
                        )
                    } else {
                        fileInfo // 加载失败，保留旧信息
                    }
                } else {
                    fileInfo // 文件未更改
                }
            }
            openFiles = updatedFiles
        }
    }
    
    // 保存文件函数
    fun saveFile(fileInfo: OpenFileInfo) {
        coroutineScope.launch {
            val tool = AITool("write_file", listOf(
                ToolParameter("path", fileInfo.path),
                ToolParameter("content", fileInfo.content)
            ))
            
            // 使用toolHandler代替actualViewModel.executeAITool
            toolHandler.executeTool(tool)
            
            // 如果是HTML文件且正在预览，刷新WebView
            if (fileInfo.isHtml && filePreviewStates[fileInfo.path] == true) {
                actualViewModel.refreshWebView()
            }
        }
    }

    // 实际执行关闭文件操作
    fun confirmCloseFile(index: Int) {
        if (index >= 0 && index < openFiles.size) {
            val fileToClose = openFiles[index]
            val updatedFiles = openFiles.toMutableList()
            updatedFiles.removeAt(index)
            openFiles = updatedFiles

            // 从未保存集合中移除
            unsavedFiles = unsavedFiles - fileToClose.path
            
            // 更新当前选中的标签
            currentFileIndex = when {
                updatedFiles.isEmpty() -> -1
                index >= updatedFiles.size -> updatedFiles.size - 1
                else -> index
            }
        }
        fileToCloseIndex = -1 // 重置待关闭文件索引
    }

    // 关闭文件标签
    fun closeFile(index: Int) {
        if (index >= 0 && index < openFiles.size) {
            val fileToClose = openFiles[index]
            // 如果文件有未保存的更改，显示确认对话框
            if (unsavedFiles.contains(fileToClose.path)) {
                fileToCloseIndex = index
            } else {
                // 否则直接关闭
                confirmCloseFile(index)
            }
        }
    }

    // 切换HTML文件预览状态
    fun togglePreview(path: String) {
        filePreviewStates =
                filePreviewStates.toMutableMap().apply { this[path] = !(this[path] ?: false) }
    }

    // 打开文件
    fun openFile(fileInfo: OpenFileInfo) {
        // 检查文件是否已经打开
        val existingIndex = openFiles.indexOfFirst { it.path == fileInfo.path }

        if (existingIndex != -1) {
            // 如果文件已经打开，切换到该标签
            currentFileIndex = existingIndex
        } else {
            // 否则添加到打开的文件列表
            // 注意：直接追加到 rememberLocal 管理的状态上
            openFiles = openFiles + fileInfo
            currentFileIndex = openFiles.size - 1

            // 初始化预览状态
            filePreviewStates =
                    filePreviewStates.toMutableMap().apply {
                        // HTML文件默认预览, 其他文件默认不预览（即编辑模式）
                        this[fileInfo.path] = fileInfo.isHtml
                    }
        }
    }

    // 新的布局根节点，使用Box来支持FAB和底部面板的覆盖
    Box(modifier = Modifier.fillMaxSize()) {
        val isBrowserPreviewVisible = isVisible && currentFileIndex == -1 && workspaceConfig.preview.type == "browser"

        BackHandler(enabled = isBrowserPreviewVisible && canWebViewGoBack) {
            if (canWebViewGoBack) {
                try {
                    webView.goBack()
                } catch (e: Exception) {
                    AppLogger.e("WorkspaceManager", "Failed to navigate WebView back", e)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 整合后的顶部栏：标签 + 动态操作
            Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shadowElevation = 2.dp,
                    modifier = Modifier.zIndex(1f) // 强制将标签栏置于顶层，防止被WebView覆盖
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 文件标签栏
                    Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        // 预览标签
                        VSCodeTab(
                                title = "预览",
                                icon = Icons.Default.Visibility,
                                isActive = currentFileIndex == -1,
                                isUnsaved = false,
                                onClose = null,
                                onClick = { currentFileIndex = -1 }
                        )

                        // 打开的文件标签
                        openFiles.forEachIndexed { index, fileInfo ->
                            VSCodeTab(
                                    title = fileInfo.name,
                                    icon = getFileIcon(fileInfo.name), // 使用统一的 getFileIcon
                                    isActive = currentFileIndex == index,
                                    isUnsaved = unsavedFiles.contains(fileInfo.path),
                                    onClose = { closeFile(index) },
                                    onClick = { currentFileIndex = index }
                            )
                        }
                    }

                    // 动态操作区域
                    val currentFile = openFiles.getOrNull(currentFileIndex)

                    // 保存按钮
                    if (currentFile != null && unsavedFiles.contains(currentFile.path)) {
                        IconButton(
                            onClick = {
                                saveFile(currentFile)
                                unsavedFiles = unsavedFiles - currentFile.path
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save File"
                            )
                        }
                    }

                    if (currentFile != null && currentFile.isHtml) {
                        val isPreview = filePreviewStates[currentFile.path] ?: false
                        IconButton(
                                onClick = { togglePreview(currentFile.path) },
                                // 限制按钮大小，使其与标签高度(40.dp)保持一致，防止撑开父布局
                                modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                    if (isPreview) Icons.Default.Edit else Icons.Default.Visibility,
                                    contentDescription = "Toggle Preview"
                            )
                        }
                    }
                }
            }

            // 主内容区域
            Box(
                    modifier =
                            Modifier.weight(1f)
                                    .background(MaterialTheme.colorScheme.surface) // 添加背景色防止闪烁
            ) {
                when {
                    // 显示WebView预览（仅当preview类型为browser时）
                    currentFileIndex == -1 && workspaceConfig.preview.type == "browser" -> {
                        AndroidView(
                                factory = { webView }, // 使用 remember 的实例
                                update = { webView ->
                                    webViewHandler.currentWebView = webView
                                },
                                modifier = Modifier.fillMaxSize()
                        )
                    }
                    // 显示命令按钮界面（当preview类型不是browser时）
                    currentFileIndex == -1 && workspaceConfig.preview.type != "browser" -> {
                        CommandButtonsView(
                            config = workspaceConfig,
                            workspacePath = workspacePath,
                            onCommandExecute = { command ->
                                // 在专属会话中执行命令
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    actualViewModel.executeCommandInWorkspace(command, workspacePath)
                                } else {
                                    // 对于旧版本Android，显示不支持提示
                                    AppLogger.w("WorkspaceManager", "Terminal features require Android 8.0+")
                                }
                            }
                        )
                    }
                    // 显示打开的文件
                    currentFileIndex in openFiles.indices -> {
                        val fileInfo = openFiles[currentFileIndex]
                        val isPreviewMode = filePreviewStates[fileInfo.path] ?: false

                        when {
                            // 图片文件：显示图片预览
                            fileInfo.isImage -> {
                                // Decode bitmap in background thread
                                var bitmap by remember(fileInfo.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
                                var isLoading by remember(fileInfo.path) { mutableStateOf(true) }
                                
                                LaunchedEffect(fileInfo.path) {
                                    bitmap = withContext(Dispatchers.IO) {
                                        try {
                                            android.graphics.BitmapFactory.decodeFile(fileInfo.path)
                                        } catch (e: Exception) {
                                            AppLogger.e("WorkspaceManager", "Failed to decode bitmap: ${fileInfo.path}", e)
                                            null
                                        }
                                    }
                                    isLoading = false
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(color = Color.White)
                                    } else {
                                        bitmap?.let { decodedBitmap ->
                                            AndroidView(
                                                factory = { context ->
                                                    android.widget.ImageView(context).apply {
                                                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                                        setImageBitmap(decodedBitmap)
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } ?: run {
                                            Text(
                                                text = "Failed to load image",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    
                                    // 图片信息叠加层
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(16.dp),
                                        color = Color.Black.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = fileInfo.name,
                                            modifier = Modifier.padding(8.dp),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            // HTML文件的预览模式：使用WebView
                            fileInfo.isHtml && isPreviewMode -> {
                                AndroidView(
                                        factory = { context ->
                                            WebView(context).apply {
                                                webViewHandler.configureWebView(this, WebViewHandler.WebViewMode.WORKSPACE, "workspace_file_preview_${fileInfo.path}")
                                            }
                                         },
                                        update = { webView ->
                                            val baseUrl = "file://${File(fileInfo.path).parent}/"
                                            webView.loadDataWithBaseURL(
                                                    baseUrl,
                                                    fileInfo.content, // 使用最新的文件内容
                                                    "text/html",
                                                    "UTF-8",
                                                    null
                                            )
                                        },
                                        modifier = Modifier.fillMaxSize()
                                )
                            }
                            // 其他所有情况：使用CodeEditor
                            else -> {
                                key(fileInfo.path) {
                                    val fileLanguage = LanguageDetector.detectLanguage(fileInfo.name)
                                    CodeEditor(
                                            code = fileInfo.content,
                                            language = fileLanguage,
                                            onCodeChange = { newContent ->
                                                val updatedFiles = openFiles.toMutableList()
                                                if (currentFileIndex in updatedFiles.indices) {
                                                    val updatedFile = openFiles[currentFileIndex].copy(content = newContent)
                                                    updatedFiles[currentFileIndex] = updatedFile
                                                    openFiles = updatedFiles

                                                    // 将文件标记为未保存
                                                    unsavedFiles = unsavedFiles + updatedFile.path
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            editorRef = { editor -> activeEditor = editor } // 传递editor引用
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 从底部弹出的文件管理器面板
        if (showFileManager) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showFileManager = false }
            )
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column {
                    // 文件管理器标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(context.getString(R.string.file_browser), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { showFileManager = false }) {
                            Icon(Icons.Default.Close, contentDescription = context.getString(R.string.close))
                        }
                    }

                    HorizontalDivider()

                    // 嵌入文件浏览器组件
                    FileBrowser(
                        initialPath = workspacePath,
                        onCancel = { showFileManager = false },
                        isManageMode = true,
                        onFileOpen = { fileInfo ->
                            openFile(fileInfo)
                            showFileManager = false
                        }
                    )
                }
            }
        }
        
        // 可展开的悬浮操作按钮菜单
        ExpandableFabMenu(
            isExpanded = isFabMenuExpanded,
            onToggle = { isFabMenuExpanded = !isFabMenuExpanded },
            exportEnabled = workspaceConfig.export.enabled,
            onExportClick = { onExportClick(File(workspacePath)) },
            onFileManagerClick = { showFileManager = true },
            onUndoClick = { activeEditor?.undo() },
            onRedoClick = { activeEditor?.redo() },
            onFormatClick = {
                // 格式化当前文件
                val currentFile = openFiles.getOrNull(currentFileIndex)
                if (currentFile != null) {
                    val language = LanguageDetector.detectLanguage(currentFile.name)
                    val formattedCode = CodeFormatter.format(currentFile.content, language)
                    
                    // 更新文件内容
                    val updatedFiles = openFiles.toMutableList()
                    updatedFiles[currentFileIndex] = currentFile.copy(content = formattedCode)
                    openFiles = updatedFiles
                    
                    // 更新编辑器显示
                    activeEditor?.replaceAllText(formattedCode)
                    
                    // 标记为未保存
                    unsavedFiles = unsavedFiles + currentFile.path
                }
                isFabMenuExpanded = false
            },
            onUnbindClick = { 
                showUnbindConfirmDialog = true
                isFabMenuExpanded = false
            },
            canFormat = openFiles.getOrNull(currentFileIndex)?.let { file ->
                val language = LanguageDetector.detectLanguage(file.name).lowercase()
                language in listOf("javascript", "js", "css", "html", "htm")
            } ?: false
        )
        
        // 解绑确认对话框
        if (showUnbindConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showUnbindConfirmDialog = false },
                title = { Text(context.getString(R.string.unbind_workspace_title)) },
                text = { Text(context.getString(R.string.unbind_workspace_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actualViewModel.unbindChatFromWorkspace(currentChat.id)
                            showUnbindConfirmDialog = false
                        }
                    ) {
                        Text(context.getString(R.string.confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnbindConfirmDialog = false }) {
                        Text(context.getString(R.string.cancel))
                    }
                }
            )
        }
        
        // 关闭文件确认对话框
        if (fileToCloseIndex != -1) {
            val file = openFiles.getOrNull(fileToCloseIndex)
            if (file != null) {
                AlertDialog(
                    onDismissRequest = { fileToCloseIndex = -1 },
                    title = { Text(context.getString(R.string.save_changes_question)) },
                    text = { Text(context.getString(R.string.file_modified_save_prompt, file.name)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                saveFile(file)
                                unsavedFiles = unsavedFiles - file.path
                                confirmCloseFile(fileToCloseIndex)
                            }
                        ) {
                            Text(context.getString(R.string.save))
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { fileToCloseIndex = -1 }) {
                                Text(context.getString(R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    confirmCloseFile(fileToCloseIndex)
                                }
                            ) {
                                Text(context.getString(R.string.dont_save))
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ExpandableFabMenu(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    exportEnabled: Boolean = true,
    onExportClick: () -> Unit,
    onFileManagerClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onFormatClick: () -> Unit,
    onUnbindClick: () -> Unit,
    canFormat: Boolean = false
) {
    val context = LocalContext.current
    
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        
        // 使用 rememberLocal 持久化FAB位置，默认为null表示使用默认右下角位置
        var fabPosition by rememberLocal<FabPosition?>("fab_menu_offset", null)
        
        // 计算实际的显示位置：如果没有自定义位置，使用右下角
        val actualX = fabPosition?.x ?: 0f
        val actualY = fabPosition?.y ?: 0f
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset { IntOffset(actualX.roundToInt(), actualY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val currentPos = fabPosition ?: FabPosition(0f, 0f)
                            val paddingPx = with(density) { 16.dp.toPx() }
                            fabPosition = FabPosition(
                                x = (currentPos.x + dragAmount.x).coerceIn(
                                    -(maxWidthPx - paddingPx * 2 - 100f),
                                    0f
                                ),
                                y = (currentPos.y + dragAmount.y).coerceIn(
                                    -(maxHeightPx - paddingPx * 2 - 100f),
                                    0f
                                )
                            )
                        }
                    },
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom
            ) {
        // 展开的菜单项
        if (isExpanded) {
            FabMenuItem(icon = Icons.Default.Undo, text = context.getString(R.string.undo), onClick = onUndoClick)
            Spacer(modifier = Modifier.height(12.dp))
            FabMenuItem(icon = Icons.Default.Redo, text = context.getString(R.string.redo), onClick = onRedoClick)
            Spacer(modifier = Modifier.height(12.dp))
            if (canFormat) {
                FabMenuItem(icon = Icons.Default.AutoFixHigh, text = context.getString(R.string.format_code), onClick = onFormatClick)
                Spacer(modifier = Modifier.height(12.dp))
            }
            FabMenuItem(icon = Icons.Default.Folder, text = context.getString(R.string.files), onClick = onFileManagerClick)
            Spacer(modifier = Modifier.height(12.dp))
            if (exportEnabled) {
                FabMenuItem(icon = Icons.Default.Upload, text = context.getString(R.string.export), onClick = onExportClick)
                Spacer(modifier = Modifier.height(12.dp))
            }
            FabMenuItem(icon = Icons.Default.LinkOff, text = context.getString(R.string.unbind), onClick = onUnbindClick)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 主切换按钮
        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.MoreVert,
                contentDescription = if (isExpanded) "关闭菜单" else "打开菜单"
            )
        }
            }
        }
    }
}

@Composable
fun FabMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 2.dp,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
        ) {
            Icon(imageVector = icon, contentDescription = text)
        }
    }
}

/** VSCode风格的标签组件 */
@Composable
fun VSCodeTab(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isActive: Boolean,
        isUnsaved: Boolean,
        onClose: (() -> Unit)? = null,
        onClick: () -> Unit
) {
    val backgroundColor =
            if (isActive) MaterialTheme.colorScheme.surface else Color.Transparent // 非活动标签背景透明

    val contentColor =
            if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant

    val bottomBorderColor = if (isActive) contentColor else Color.Transparent

    Box(
            modifier =
                    Modifier.height(40.dp) // 增加高度
                            .background(
                                    backgroundColor,
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                            .clickable(onClick = onClick)
    ) {
        Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                        text = title,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )

                if (onClose != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(22.dp).padding(2.dp)
                    ) {
                        if (isUnsaved) {
                            Icon(
                                Icons.Filled.FiberManualRecord,
                                contentDescription = "未保存",
                                modifier = Modifier.size(8.dp),
                                tint = contentColor.copy(alpha = 0.9f)
                            )
                        } else {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                modifier = Modifier.size(14.dp),
                                tint = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp)) // 保持对齐
                }
            }
            // 活动标签下划线
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(bottomBorderColor))
        }
    }
}

/**
 * 命令按钮视图组件
 * 用于非 browser 类型的预览界面，显示 config.json 中定义的命令按钮
 */
@Composable
fun CommandButtonsView(
    config: WorkspaceConfig,
    workspacePath: String,
    onCommandExecute: (CommandConfig) -> Unit
) {
    val context = LocalContext.current
    var showBrowserPreview by remember { mutableStateOf(false) }
    var refreshCounter by remember { mutableStateOf(0) }
    
    // 如果用户切换到浏览器预览，显示 WebView
    if (showBrowserPreview && config.preview.url.isNotEmpty()) {
        // 使用 remember 保存 WebView 实例以便清理
        val tempWebView = remember {
            WebView(context).apply {
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN ->
                            v.parent.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP ->
                            v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                val handler = WebViewHandler(context)
                handler.configureWebView(this, WebViewHandler.WebViewMode.WORKSPACE, "workspace_preview_${workspacePath.hashCode()}")
            }
        }
        
        // 每次刷新计数器改变时重新加载页面
        LaunchedEffect(refreshCounter) {
            tempWebView.loadUrl(config.preview.url)
        }
        
        // 清理 WebView
        DisposableEffect(Unit) {
            onDispose {
                tempWebView.stopLoading()
                tempWebView.clearHistory()
                tempWebView.removeAllViews()
                tempWebView.destroy()
            }
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { tempWebView },
                update = { webView ->
                    webView.requestFocus()
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // 返回按钮
            FloatingActionButton(
                onClick = { showBrowserPreview = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭预览")
            }
        }
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = config.title ?: "${config.projectType.uppercase()} 项目",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (config.description != null) {
            Text(
                text = config.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "点击下方按钮执行命令",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 浏览器预览按钮（可选）
        if (config.preview.showPreviewButton && config.preview.url.isNotEmpty()) {
            Button(
                onClick = { 
                    showBrowserPreview = true
                    refreshCounter++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = config.preview.previewButtonLabel,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // 显示命令按钮
        if (config.commands.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "暂无配置命令\n\n可以在 .operit/config.json 中添加命令按钮配置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            config.commands.forEach { command ->
                Button(
                    onClick = { onCommandExecute(command) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = command.label,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 项目信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "工作区路径",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = workspacePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
