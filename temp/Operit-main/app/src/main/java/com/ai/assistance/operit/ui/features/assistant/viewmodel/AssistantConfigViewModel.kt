package com.ai.assistance.operit.ui.features.assistant.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.impl.factory.AvatarModelFactoryImpl
import com.ai.assistance.operit.data.repository.AvatarConfig
import com.ai.assistance.operit.data.repository.AvatarInstanceSettings
import com.ai.assistance.operit.data.repository.AvatarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 助手配置视图模型 负责管理助手的UI状态和业务逻辑 */
class AssistantConfigViewModel(
    private val repository: AvatarRepository,
    private val context: Context
) : ViewModel() {

    // UI状态
    data class UiState(
            val isLoading: Boolean = false,
            val avatarConfigs: List<AvatarConfig> = emptyList(),
            val currentAvatarConfig: AvatarConfig? = null,
            val currentAvatarModel: AvatarModel? = null,
            val config: AvatarInstanceSettings? = null,
            val errorMessage: String? = null,
            val operationSuccess: Boolean = false,
            val scrollPosition: Int = 0,
            val isImporting: Boolean = false
    )

    // 当前UI状态
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // 合并模型和配置流，以确保UI状态的一致性
        viewModelScope.launch {
            combine(
                repository.configs,
                repository.currentAvatar,
                repository.instanceSettings
            ) { configs, currentAvatar, instanceSettings ->
                val currentSettings = currentAvatar?.let { instanceSettings[it.id] } ?: AvatarInstanceSettings()
                val currentConfig = currentAvatar?.let { avatar -> configs.find { it.id == avatar.id } }
                Triple(configs, currentConfig, currentSettings to currentAvatar)
            }.collectLatest { (configs, currentConfig, settingsAndAvatar) ->
                val (currentSettings, currentAvatar) = settingsAndAvatar
                updateUiState(
                    avatarConfigs = configs,
                    currentAvatarConfig = currentConfig,
                    currentAvatarModel = currentAvatar,
                    config = currentSettings
                )
            }
        }
    }

    /** 更新UI状态 */
    private fun updateUiState(
            isLoading: Boolean? = null,
            avatarConfigs: List<AvatarConfig>? = null,
            currentAvatarConfig: AvatarConfig? = null,
            currentAvatarModel: AvatarModel? = null,
            config: AvatarInstanceSettings? = null,
            errorMessage: String? = null,
            operationSuccess: Boolean? = null,
            isImporting: Boolean? = null
    ) {
        val currentState = _uiState.value
        _uiState.value =
                currentState.copy(
                        isLoading = isLoading ?: currentState.isLoading,
                        avatarConfigs = avatarConfigs ?: currentState.avatarConfigs,
                        currentAvatarConfig = currentAvatarConfig ?: currentState.currentAvatarConfig,
                        currentAvatarModel = currentAvatarModel ?: currentState.currentAvatarModel,
                        config = config ?: currentState.config,
                        errorMessage = errorMessage,
                        operationSuccess = operationSuccess ?: currentState.operationSuccess,
                        scrollPosition = currentState.scrollPosition,
                        isImporting = isImporting ?: currentState.isImporting
                )
    }

    /** 切换模型 */
    fun switchAvatar(modelId: String) {
        viewModelScope.launch { repository.switchAvatar(modelId) }
    }

    /** 更新缩放比例 */
    fun updateScale(scale: Float) {
        val currentConfig = _uiState.value.config ?: return
        val avatarId = _uiState.value.currentAvatarConfig?.id ?: return
        val updatedConfig = currentConfig.copy(scale = scale)
        viewModelScope.launch { repository.updateAvatarSettings(avatarId, updatedConfig) }
    }

    /** 更新X轴偏移 */
    fun updateTranslateX(translateX: Float) {
        val currentConfig = _uiState.value.config ?: return
        val avatarId = _uiState.value.currentAvatarConfig?.id ?: return
        val updatedConfig = currentConfig.copy(translateX = translateX)
        viewModelScope.launch { repository.updateAvatarSettings(avatarId, updatedConfig) }
    }

    /** 更新Y轴偏移 */
    fun updateTranslateY(translateY: Float) {
        val currentConfig = _uiState.value.config ?: return
        val avatarId = _uiState.value.currentAvatarConfig?.id ?: return
        val updatedConfig = currentConfig.copy(translateY = translateY)
        viewModelScope.launch { repository.updateAvatarSettings(avatarId, updatedConfig) }
    }

    /** 删除用户模型 */
    fun deleteAvatar(modelId: String) {
        updateUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val success = repository.deleteAvatar(modelId)
                updateUiState(
                        isLoading = false,
                        operationSuccess = success,
                        errorMessage = if (!success) context.getString(R.string.error_occurred_simple) else null
                )
            } catch (e: Exception) {
                updateUiState(
                        isLoading = false,
                        operationSuccess = false,
                        errorMessage = context.getString(R.string.error_occurred, e.message)
                )
            }
        }
    }

    /** 导入模型ZIP文件 */
    fun importAvatarFromZip(uri: Uri) {
        updateUiState(isLoading = true, isImporting = true)
        viewModelScope.launch {
            try {
                val success = repository.importAvatarFromZip(uri)
                updateUiState(
                        isLoading = false,
                        isImporting = false,
                        operationSuccess = success,
                        errorMessage = if (!success) context.getString(R.string.error_occurred_simple) else null
                )
            } catch (e: Exception) {
                updateUiState(
                        isLoading = false,
                        isImporting = false,
                        operationSuccess = false,
                        errorMessage = context.getString(R.string.error_occurred, e.message)
                )
            }
        }
    }

    /** 清除错误消息 */
    fun clearErrorMessage() {
        updateUiState(errorMessage = null)
    }

    /** 清除操作成功状态 */
    fun clearOperationSuccess() {
        updateUiState(operationSuccess = false)
    }

    /** 更新错误消息 */
    fun updateErrorMessage(message: String?) {
        updateUiState(errorMessage = message)
    }

    /** 更新滚动位置 */
    fun updateScrollPosition(position: Int) {
        _uiState.value = _uiState.value.copy(scrollPosition = position)
    }

    /** ViewModel工厂类 */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AssistantConfigViewModel::class.java)) {
                val modelFactory = AvatarModelFactoryImpl()
                val repository = AvatarRepository.getInstance(context, modelFactory)
                return AssistantConfigViewModel(repository, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
