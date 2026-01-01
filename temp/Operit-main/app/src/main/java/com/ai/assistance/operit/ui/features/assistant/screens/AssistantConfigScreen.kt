package com.ai.assistance.operit.ui.features.assistant.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.assistant.components.AvatarConfigSection
import com.ai.assistance.operit.ui.features.assistant.components.AvatarPreviewSection
import com.ai.assistance.operit.ui.features.assistant.components.HowToImportSection
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel

/** 助手配置屏幕 提供DragonBones模型预览和相关配置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConfigScreen() {
        val context = LocalContext.current
        val viewModel: AssistantConfigViewModel =
                viewModel(factory = AssistantConfigViewModel.Factory(context))
        val uiState by viewModel.uiState.collectAsState()

        // 启动文件选择器
        val zipFileLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                                result.data?.data?.let { uri ->
                                        // 导入选择的zip文件
                                        viewModel.importAvatarFromZip(uri)
                                }
                        }
                }

        // 打开文件选择器的函数
        val openZipFilePicker = {
                val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/zip"
                                putExtra(
                                        Intent.EXTRA_MIME_TYPES,
                                        arrayOf("application/zip", "application/x-zip-compressed")
                                )
                        }
                zipFileLauncher.launch(intent)
        }

        val snackbarHostState = remember { SnackbarHostState() }
        val scrollState = rememberScrollState(initial = uiState.scrollPosition)

        // 在 Composable 函数中获取字符串资源，以便在 LaunchedEffect 中使用
        val operationSuccessString = context.getString(R.string.operation_success)
        val errorOccurredString = context.getString(R.string.error_occurred_simple)

        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        viewModel.updateScrollPosition(position)
                }
        }

        // 显示操作结果的 SnackBar
        LaunchedEffect(uiState.operationSuccess, uiState.errorMessage) {
                if (uiState.operationSuccess) {
                        snackbarHostState.showSnackbar(operationSuccessString)
                        viewModel.clearOperationSuccess()
                } else if (uiState.errorMessage != null) {
                        snackbarHostState.showSnackbar(uiState.errorMessage ?: errorOccurredString)
                        viewModel.clearErrorMessage()
                }
        }

        CustomScaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                        // 主要内容
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(paddingValues)
                                                .padding(horizontal = 12.dp)
                                                .verticalScroll(scrollState)
                        ) {
                                // Avatar预览区域
                                AvatarPreviewSection(
                                        modifier = Modifier.fillMaxWidth().height(300.dp),
                                        uiState = uiState,
                                        onDeleteCurrentModel =
                                                uiState.currentAvatarConfig?.let { model ->
                                                        { viewModel.deleteAvatar(model.id) }
                                                }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                AvatarConfigSection(
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        onImportClick = { openZipFilePicker() }
                                )

                                HowToImportSection()

                                // 底部空间
                                Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 加载指示器覆盖层
                        if (uiState.isLoading || uiState.isImporting) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.7f)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator()
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text =
                                                                if (uiState.isImporting) stringResource(R.string.importing_model)
                                                                else stringResource(R.string.processing),
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                        }
                                }
                        }
                }
        }
}
