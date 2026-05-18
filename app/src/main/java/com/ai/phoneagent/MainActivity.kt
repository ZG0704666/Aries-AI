/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

//                            _ooOoo_  
//                           o8888888o  
//                           88" . "88  
//                           (| -_- |)  
//                            O\ = /O  
//                        ____/`---'\____  
//                      .   ' \\| |// `.  
//                       / \\||| : |||// \  
//                     / _||||| -:- |||||- \  
//                       | | \\\ - /// | |  
//                     | \_| ''\---/'' | |  
//                      \ .-\__ `-` ___/-. /  
//                   ___`. .' /--.--\ `. . __  
//                ."" '< `.___\_<|>_/___.' >'"".  
//               | | : `- \`.;`\ _ /`;.`/ - ` : | |  
//                 \ \ `-. \_ __\ /__ _/ .-` / /  
//         ======`-.____`-.___\_____/___.-`____.-'======  
//                            `=---='  
//  
//         .............................................  
//                  佛祖保佑             永无BUG 


package com.ai.phoneagent

import android.Manifest
import android.app.DownloadManager
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import java.io.File
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.LruCache
import android.text.Editable
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.inputmethod.InputMethodManager
import com.ai.phoneagent.helper.AutomationMessageParser
import com.ai.phoneagent.helper.AutomationTimelineEntry
import com.ai.phoneagent.helper.AutomationTimelineFormatter
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ai.phoneagent.net.AriesApiClient
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ChatRequestMessage
import com.ai.phoneagent.net.LocalMnnInferenceEngine
import com.ai.phoneagent.net.ModelScopeModelDownloader
import com.ai.phoneagent.updates.UpdateStartupCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.phoneagent.speech.SherpaSpeechRecognizer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import android.app.Dialog
import android.view.Window
import android.view.WindowManager
import android.graphics.drawable.ColorDrawable
import android.view.ViewAnimationUtils
import android.text.Html
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music2
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.Image as LucideImage
import androidx.compose.material3.Text
import com.ai.phoneagent.core.automation.ActivityAutomationInstructionGateway
import com.ai.phoneagent.core.automation.AutomationInstructionRequest
import com.ai.phoneagent.core.automation.AutomationLogBridge
import com.ai.phoneagent.core.prompt.MainChatPromptRepository
import com.ai.phoneagent.ui.debug.DebugRecomposeLogger
import com.ai.phoneagent.ui.inputbar.InputState
import com.ai.phoneagent.ui.inputbar.InputBar
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.ai.phoneagent.data.AttachmentInfo
import com.ai.phoneagent.data.local.ConversationRecord
import com.ai.phoneagent.data.local.ConversationStorageRepository
import com.ai.phoneagent.data.local.StoredAttachmentRecord
import com.ai.phoneagent.data.local.StoredMessageRecord
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.data.preferences.FloatingChatPreferencesRepository
import com.ai.phoneagent.data.preferences.MainUiPreferencesRepository
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.core.designsystem.theme.ThemeColorStyle
import com.ai.phoneagent.core.designsystem.theme.ThemeMode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import com.ai.phoneagent.ui.drawer.ConversationDrawer
import com.ai.phoneagent.ui.drawer.DrawerConversationUiItem
import com.ai.phoneagent.ui.history.ConversationHistoryDialog
import com.ai.phoneagent.ui.history.ConversationHistoryItemUi
import com.ai.phoneagent.ui.messages.TranscriptAutomationUi
import com.ai.phoneagent.ui.messages.TranscriptMessageUi
import com.ai.phoneagent.ui.messages.CodeBlockPrefs
import com.ai.phoneagent.ui.messages.StreamingTranscriptBodyPreview
import com.ai.phoneagent.ui.messages.StreamingTranscriptMessageState
import com.ai.phoneagent.ui.messages.buildStreamingTranscriptBodyPreview
import com.ai.phoneagent.ui.home.HomeTranscriptPane
import com.ai.phoneagent.viewmodel.ChatViewModel
import com.ai.phoneagent.ui.topbar.MainTopBar
import com.ai.phoneagent.navigation.AriesNavGraph
import com.ai.phoneagent.navigation.Routes
import com.ai.phoneagent.ui.home.HomeScreen
import com.ai.phoneagent.viewmodel.AutomationViewModel
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DrawerState
import androidx.compose.material3.rememberDrawerState
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {

    private class AppPrefsCompat(
        private val appPrefsRepository: AppPreferencesRepository,
    ) {
        inner class Editor {
            private val stringValues = mutableMapOf<String, String?>()
            private val booleanValues = mutableMapOf<String, Boolean>()
            private val longValues = mutableMapOf<String, Long>()
            private val stringSetValues = mutableMapOf<String, Set<String>>()
            private val removeKeys = mutableSetOf<String>()

            fun putString(key: String, value: String?): Editor {
                stringValues[key] = value
                removeKeys.remove(key)
                return this
            }

            fun putBoolean(key: String, value: Boolean): Editor {
                booleanValues[key] = value
                removeKeys.remove(key)
                return this
            }

            fun putLong(key: String, value: Long): Editor {
                longValues[key] = value
                removeKeys.remove(key)
                return this
            }

            fun putStringSet(key: String, value: Set<String>): Editor {
                stringSetValues[key] = value
                removeKeys.remove(key)
                return this
            }

            fun remove(key: String): Editor {
                removeKeys.add(key)
                stringValues.remove(key)
                booleanValues.remove(key)
                longValues.remove(key)
                stringSetValues.remove(key)
                return this
            }

            fun apply() {
                removeKeys.forEach { key ->
                    when (key) {
                        "api_key" -> appPrefsRepository.writeApiConfigBlocking(removeApiKey = true)
                        "api_last_check_key" -> appPrefsRepository.setApiLastCheckKeyBlocking("")
                        "api_last_check_ok" -> appPrefsRepository.writeApiConfigBlocking(clearCheckResults = true)
                        "api_last_check_time" -> appPrefsRepository.setApiLastCheckTimeBlocking(0L)
                        "api_last_check_sig" -> appPrefsRepository.setApiLastCheckSigBlocking("")
                        "conversations_json" -> appPrefsRepository.setLegacyConversationsJsonBlocking(null)
                        "active_conversation_id" -> appPrefsRepository.setLegacyActiveConversationIdBlocking(null)
                    }
                }

                stringValues.forEach { (key, value) ->
                    when (key) {
                        "api_key" -> appPrefsRepository.setApiKeyBlocking(value.orEmpty())
                        "api_third_party_base_url" -> appPrefsRepository.setApiThirdPartyBaseUrlBlocking(value.orEmpty())
                        "api_third_party_model" -> appPrefsRepository.setApiThirdPartyModelBlocking(value.orEmpty())
                        "api_last_check_key" -> appPrefsRepository.setApiLastCheckKeyBlocking(value.orEmpty())
                        "api_last_check_sig" -> appPrefsRepository.setApiLastCheckSigBlocking(value.orEmpty())
                        "conversations_json" -> appPrefsRepository.setLegacyConversationsJsonBlocking(value)
                    }
                }

                booleanValues.forEach { (key, value) ->
                    when (key) {
                        "api_use_third_party" -> appPrefsRepository.setApiUseThirdPartyBlocking(value)
                        "api_use_local_model" -> appPrefsRepository.setApiUseLocalModelBlocking(value)
                        "api_last_check_ok" -> appPrefsRepository.setApiLastCheckOkBlocking(value)
                        "user_agreement_accepted" -> appPrefsRepository.setUserAgreementAcceptedBlocking(value)
                    }
                }

                longValues.forEach { (key, value) ->
                    when (key) {
                        "api_last_check_time" -> appPrefsRepository.setApiLastCheckTimeBlocking(value)
                        "active_conversation_id" -> appPrefsRepository.setLegacyActiveConversationIdBlocking(value)
                    }
                }

                stringSetValues.forEach { (key, value) ->
                    when (key) {
                        "qwen_pending_download_ids" -> appPrefsRepository.setQwenPendingDownloadIdsBlocking(value)
                    }
                }
            }
        }

        fun getString(key: String, defaultValue: String?): String? =
            when (key) {
                "api_key" -> appPrefsRepository.getApiKeyBlocking()
                "api_third_party_base_url" -> appPrefsRepository.getApiThirdPartyBaseUrlBlocking()
                "api_third_party_model" -> appPrefsRepository.getApiThirdPartyModelBlocking()
                "api_last_check_key" -> appPrefsRepository.getApiLastCheckKeyBlocking()
                "api_last_check_sig" -> appPrefsRepository.getApiLastCheckSigBlocking()
                "conversations_json" -> appPrefsRepository.getLegacyConversationsJsonBlocking()
                else -> defaultValue
            } ?: defaultValue

        fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            when (key) {
                "api_use_third_party" -> appPrefsRepository.getApiUseThirdPartyBlocking()
                "api_use_local_model" -> appPrefsRepository.getApiUseLocalModelBlocking()
                "api_last_check_ok" -> appPrefsRepository.getApiLastCheckOkBlocking()
                "user_agreement_accepted" -> appPrefsRepository.getUserAgreementAcceptedBlocking()
                else -> defaultValue
            }

        fun getLong(key: String, defaultValue: Long): Long =
            when (key) {
                "api_last_check_time" -> appPrefsRepository.getApiLastCheckTimeBlocking()
                "active_conversation_id" -> appPrefsRepository.getLegacyActiveConversationIdBlocking(defaultValue)
                else -> defaultValue
            }

        fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? =
            when (key) {
                "qwen_pending_download_ids" -> appPrefsRepository.getQwenPendingDownloadIdsBlocking()
                else -> defaultValue
            }

        fun contains(key: String): Boolean =
            when (key) {
                "api_last_check_ok" -> appPrefsRepository.hasApiLastCheckOkBlocking()
                else -> false
            }

        fun edit(): Editor = Editor()
    }
    
    // ViewModel for managing chat and attachment state
    private val chatViewModel: ChatViewModel by viewModels()

    companion object {
        const val EXTRA_SCROLL_TO_BOTTOM = "extra_scroll_to_bottom"
        const val EXTRA_SHOW_AUTOMATION_STOP = "extra_show_automation_stop"
        private const val STREAMING_UI_FRAME_DELAY_MS = 80L
        private const val HOME_AUTOMATION_AUTO_CONFIRM_SECONDS = 10
    }

    private data class UiMessage(
            val author: String,
            val content: String,
            val isUser: Boolean,
            val thinkingDurationMs: Long? = null,
            val attachments: List<AttachmentInfo>? = null,
    )

    private data class Conversation(
            val id: Long,
            var title: String,
            val messages: MutableList<UiMessage>,
            var updatedAt: Long,
    )

    private fun Conversation.toStorageRecord(): ConversationRecord {
        return ConversationRecord(
            id = id,
            title = title,
            updatedAt = updatedAt,
            messages =
                messages.map { message ->
                    StoredMessageRecord(
                        author = message.author,
                        content = message.content,
                        isUser = message.isUser,
                        thinkingDurationMs = message.thinkingDurationMs,
                        attachments =
                            message.attachments.orEmpty().map { attachment ->
                                StoredAttachmentRecord(
                                    filePath = attachment.filePath,
                                    fileName = attachment.fileName,
                                    mimeType = attachment.mimeType,
                                    fileSize = attachment.fileSize,
                                    content = attachment.content,
                                )
                            },
                    )
                },
        )
    }

    private fun ConversationRecord.toConversation(): Conversation {
        return Conversation(
            id = id,
            title = title,
            updatedAt = updatedAt,
            messages =
                messages.map { message ->
                    UiMessage(
                        author = message.author,
                        content = message.content,
                        isUser = message.isUser,
                        thinkingDurationMs = message.thinkingDurationMs,
                        attachments =
                            message.attachments.map { attachment ->
                                AttachmentInfo(
                                    filePath = attachment.filePath,
                                    fileName = attachment.fileName,
                                    mimeType = attachment.mimeType,
                                    fileSize = attachment.fileSize,
                                    content = attachment.content,
                                )
                            }.takeIf { it.isNotEmpty() },
                    )
                }.toMutableList(),
        )
    }

    private data class AutomationMessageRef(
            val conversationId: Long,
            val messageIndex: Int,
    )

        private data class PendingAutomationConfirmTarget(
            val messageRef: AutomationMessageRef,
            val instruction: String,
        )

    private lateinit var onboardingOverlay: MainOnboardingOverlay
    private val drawerStateHolder = mutableStateOf<DrawerState?>(null)
    private var composeScopeHolder: kotlinx.coroutines.CoroutineScope? = null
    private val scrollToBottomSignalState = mutableStateOf(0L)
    private val transcriptAnimationKeyState = mutableStateOf(0L)
    private val homeContentAlphaState = mutableStateOf(1f)
    private val homeContentScaleState = mutableStateOf(1f)
    private val showHistoryDialogState = mutableStateOf(false)
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    private val appPrefsRepository by inject<AppPreferencesRepository>()
    private val floatingChatPrefs by inject<FloatingChatPreferencesRepository>()
    private val prefs by lazy { AppPrefsCompat(appPrefsRepository) }
    private val uiPreferencesRepository by lazy { MainUiPreferencesRepository(applicationContext) }
    private val conversationStorageRepository by lazy { ConversationStorageRepository(applicationContext) }

    private val conversations = mutableListOf<Conversation>()

    private var activeConversation: Conversation? = null

    private var sherpaSpeechRecognizer: SherpaSpeechRecognizer? = null

    private var isListening = false

    private var pendingStartVoice = false

    private var voicePrefix: String = ""

    private var micAnimator: ObjectAnimator? = null


    // 防止并发请求导致重试时更容易出现空回复/失败提示
    private var isRequestInFlight: Boolean = false
    
    // 用于停止生成的标志
    private var shouldStopGeneration: Boolean = false

    private var voiceInputAnimJob: Job? = null
    private var savedInputText: String = ""

    private var pendingSendAfterVoice: Boolean = false
    private var voiceSessionSeed: Long = 0L
    @Volatile private var activeVoiceSessionId: Long = 0L

    // 小窗模式相关
    private var isAnimatingToMiniWindow = false
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1235
    private val CAMERA_PERMISSION_REQUEST_CODE = 1236
    private var pendingEnterMiniWindowAfterNotifPerm: Boolean = false
    private var pendingAutomationLogUiRefresh: Boolean = false
    private var automationLogReceiverRegistered: Boolean = false
    private var automationTerminatePendingRef: AutomationMessageRef? = null
    private var automationTerminateFallbackJob: Job? = null
    private var automationAutoConfirmRef: AutomationMessageRef? = null
    private var automationAutoConfirmJob: Job? = null
    private val automationCountdownSeconds = mutableMapOf<AutomationMessageRef, Int>()

    private val automationLogReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val line = AutomationLogBridge.extract(intent) ?: return
                    appendAutomationLogAsAiMessage(line)
                }
            }

    private val qwenDownloadReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val downloadId =
                    intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (downloadId <= 0L) return
                if (!pendingQwenDownloadIds.remove(downloadId)) return
                persistPendingQwenDownloadIds()

                val status = queryDownloadStatus(downloadId)
                if (status.first == DownloadManager.STATUS_FAILED) {
                    val reason = status.second
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.m3t_sidebar_qwen_download_failed_reason_format, reason),
                        Toast.LENGTH_LONG
                    ).show()
                    refreshLocalModelReadyState()
                    return
                }

                if (pendingQwenDownloadIds.isEmpty()) {
                    refreshLocalModelReadyState()
                    if (localModelReady) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.m3t_sidebar_qwen_download_complete),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    updateStatusText()
                }
            }
        }

    private val conversationsKey = "conversations_json"
    private val activeConversationIdKey = "active_conversation_id"

    @Volatile private var remoteApiOk: Boolean? = null
    @Volatile private var remoteApiChecking: Boolean = false
    @Volatile private var offlineModelReady: Boolean = false
    @Volatile private var apiCheckSeq: Int = 0
    @Volatile private var lastCheckedApiKey: String = ""

    private val apiLastCheckKeyPref = "api_last_check_key"
    private val apiLastCheckOkPref = "api_last_check_ok"
    private val apiLastCheckTimePref = "api_last_check_time"
    private val apiLastCheckSigPref = "api_last_check_sig"
    private val apiUseThirdPartyPref = "api_use_third_party"
    private val apiThirdPartyBaseUrlPref = "api_third_party_base_url"
    private val apiThirdPartyModelPref = "api_third_party_model"
    private val apiUseLocalModelPref = "api_use_local_model"
    private val localModelDownloadButtonVisiblePref = "local_model_download_button_visible"
    private val qwenPendingDownloadIdsPref = "qwen_pending_download_ids"

    private val inputTextState = mutableStateOf("")
    private val inputBarState = mutableStateOf<InputState>(InputState.Idle)
    private val voiceAmplitudeState = mutableStateOf(0f)
    private val agentModeEnabledState = mutableStateOf(false)
    private val statusTextState = mutableStateOf("")
    private val statusVisibleState = mutableStateOf(false)
    private val thinkingExpandedByDefaultState = mutableStateOf(false)
    private val drawerSearchQueryState = mutableStateOf("")
    private val drawerConversationItemsState = mutableStateOf<List<DrawerConversationUiItem>>(emptyList())
    private val drawerEmptyMessageState = mutableStateOf("")
    private val transcriptItemsState = mutableStateOf<List<TranscriptMessageUi>>(emptyList())
    private val streamingTranscriptItemState = mutableStateOf<StreamingTranscriptMessageState?>(null)
    private val streamingTranscriptConversationIdState = mutableStateOf<Long?>(null)

    // Aries附件上传相关 - 简化为只保留 ActivityResultLauncher
    private lateinit var ariesImagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var ariesPdfPickerLauncher: ActivityResultLauncher<String>
    private lateinit var ariesFilePickerLauncher: ActivityResultLauncher<String>
    private lateinit var ariesCameraLauncher: ActivityResultLauncher<Uri>
    private var tempCameraUri: Uri? = null
    
    private val attachmentThumbnailCache = LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(64)

    @Volatile private var apiNeedsRecheckToastShown: Boolean = false
    @Volatile private var qwenDownloadInFlight: Boolean = false
    @Volatile private var localModelReady: Boolean = false
    private var useThirdPartyApi by mutableStateOf(false)
    private var useLocalModel by mutableStateOf(false)
    private var useAriesApi by mutableStateOf(false)
    private var apiBaseUrl by mutableStateOf(AutoGlmClient.DEFAULT_BASE_URL)
    private var apiModel by mutableStateOf(AutoGlmClient.DEFAULT_MODEL)
    private var ariesSelectedModel by mutableStateOf("")
    private val navControllerState = mutableStateOf<NavHostController?>(null)
    private val routeNavigationActionState = mutableStateOf<((String) -> Unit)?>(null)
    private val pendingQwenDownloadIds = linkedSetOf<Long>()
    private var qwenDownloadReceiverRegistered = false

    private fun persistConversations() {
        val snapshot = conversations.map { it.toStorageRecord() }
        val activeConversationId = activeConversation?.id
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                conversationStorageRepository.persistConversations(snapshot)
                uiPreferencesRepository.setActiveConversationId(activeConversationId)
            }
        }
        refreshDrawerConversationItems()
    }

    private fun tryRestoreConversations(): Boolean {
        val stored =
            runCatching {
                runBlocking(Dispatchers.IO) { conversationStorageRepository.loadConversations() }
            }.getOrNull().orEmpty()
        val restoredConversations =
            if (stored.isNotEmpty()) {
                stored.map { it.toConversation() }
            } else {
                tryRestoreLegacyConversations()
            }
        if (restoredConversations.isEmpty()) return false
        return try {
            conversations.clear()
            conversations.addAll(restoredConversations)

            val activeId =
                runCatching {
                    runBlocking(Dispatchers.IO) { uiPreferencesRepository.getActiveConversationId() }
                }.getOrNull() ?: -1L
            activeConversation = conversations.firstOrNull { it.id == activeId } ?: conversations.firstOrNull()

            activeConversation?.let { renderConversation(it) }
            refreshDrawerConversationItems()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryRestoreLegacyConversations(): MutableList<Conversation> {
        val json = prefs.getString(conversationsKey, null) ?: return mutableListOf()
        return runCatching {
            val type = object : com.google.gson.reflect.TypeToken<List<Conversation>>() {}.type
            val list: List<Conversation> = com.google.gson.Gson().fromJson(json, type) ?: emptyList()
            val activeId = prefs.getLong(activeConversationIdKey, -1L)
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    conversationStorageRepository.persistConversations(list.map { it.toStorageRecord() })
                    uiPreferencesRepository.setActiveConversationId(activeId)
                    prefs.edit().remove(conversationsKey).remove(activeConversationIdKey).apply()
                }
            }
            list.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    /**
     * TODO: 设置附件观察者 - 使用 ViewModel 管理状态
     */
    private fun setupAttachmentObservers() {
        // TODO: 实现附件观察者
        // 观察附件状态
        // chatViewModel.currentAttachment.observe(this) { attachment ->
        //     if (attachment != null) {
        //         showAttachmentPreview(attachment)
        //     } else {
        //         hideAttachmentPreview()
        //     }
        // }
        
        // TODO: 观察错误消息
        // chatViewModel.errorMessage.observe(this) { error ->
        //     error?.let {
        //         Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        //         chatViewModel.clearError()
        //     }
        // }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        onboardingOverlay = MainOnboardingOverlay(
            activity = this,
            appPrefs = appPrefsRepository,
        )

        overlayPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
                    enterMiniWindowMode()
                } else {
                    Toast.makeText(this, "需要悬浮窗权限才能使用小窗模式", Toast.LENGTH_SHORT).show()
                }
            }

        setContent {
            val themeModeStr by appPrefsRepository.themeModeFlow.collectAsState(initial = "system")
            val themeColorStyleRaw by appPrefsRepository.themeColorStyleFlow.collectAsState(initial = ThemeColorStyle.DEFAULT.storageKey)
            val amoledDark by appPrefsRepository.amoledDarkEnabledFlow.collectAsState(initial = false)
            val fontScale by appPrefsRepository.chatFontScaleFlow.collectAsState(initial = 1.0f)
            val fontFamilyRaw by appPrefsRepository.chatFontFamilyFlow.collectAsState(initial = "default")
            val codeAutoWrap by appPrefsRepository.codeAutoWrapFlow.collectAsState(initial = true)
            val codeLineNumbers by appPrefsRepository.codeLineNumbersFlow.collectAsState(initial = true)
            val codeAutoCollapse by appPrefsRepository.codeAutoCollapseFlow.collectAsState(initial = false)

            // Reactively observe API config so settings changes take effect immediately
            // (no need to wait for onResume which doesn't fire on in-graph navigation)
            val currentApiKey by appPrefsRepository.apiKeyFlow.collectAsState(initial = "")
            val currentUseThirdParty by appPrefsRepository.apiUseThirdPartyFlow.collectAsState(initial = false)
            val currentUseLocalModel by appPrefsRepository.apiUseLocalModelFlow.collectAsState(initial = false)
            val currentUseAriesApi by appPrefsRepository.useAriesApiFlow.collectAsState(initial = false)
            val currentApiBaseUrl by appPrefsRepository.apiThirdPartyBaseUrlFlow.collectAsState(initial = "")
            val currentApiModel by appPrefsRepository.apiThirdPartyModelFlow.collectAsState(initial = "")
            val currentAriesModel by appPrefsRepository.ariesSelectedModelFlow.collectAsState(initial = "")

            LaunchedEffect(
                currentApiKey,
                currentUseThirdParty,
                currentUseLocalModel,
                currentUseAriesApi,
                currentApiBaseUrl,
                currentApiModel,
                currentAriesModel,
            ) {
                useThirdPartyApi = currentUseThirdParty
                useLocalModel    = currentUseLocalModel
                useAriesApi      = currentUseAriesApi
                apiBaseUrl       = currentApiBaseUrl
                apiModel         = currentApiModel
                ariesSelectedModel = currentAriesModel
                if (currentUseAriesApi) {
                    updateStatusText()
                } else {
                    onApiConfigPotentiallyChanged(showNeedsCheckMessage = false)
                }
            }

            val themeMode = when (themeModeStr.lowercase()) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            val themeColorStyle = ThemeColorStyle.fromStorage(themeColorStyleRaw)
            val resolvedFontFamily =
                when (fontFamilyRaw.lowercase()) {
                    "sans_serif" -> FontFamily.SansSerif
                    "serif" -> FontFamily.Serif
                    "monospace" -> FontFamily.Monospace
                    else -> FontFamily.Default
                }
            val codeBlockPrefs =
                CodeBlockPrefs(
                    autoWrap = codeAutoWrap,
                    lineNumbers = codeLineNumbers,
                    autoCollapse = codeAutoCollapse,
                )

            AriesMaterialTheme(
                themeMode = themeMode,
                themeColorStyle = themeColorStyle,
                amoledDark = amoledDark,
                fontScale = fontScale,
                fontFamily = resolvedFontFamily,
            ) {
                val navController = rememberNavController()
                DisposableEffect(navController) {
                    val destinationListener =
                        NavController.OnDestinationChangedListener { _, destination, _ ->
                            if (destination.route == Routes.Home.route) {
                                refreshAutomationCardsForCurrentConversation()
                            } else {
                                clearAutomationAutoConfirm()
                            }
                        }
                    navController.addOnDestinationChangedListener(destinationListener)
                    navControllerState.value = navController
                    routeNavigationActionState.value = { route ->
                        if (navController.currentDestination?.route != route) {
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                        }
                    }
                    onDispose {
                        navController.removeOnDestinationChangedListener(destinationListener)
                        if (navControllerState.value === navController) {
                            navControllerState.value = null
                        }
                        routeNavigationActionState.value = null
                    }
                }

                AriesNavGraph(
                    navController = navController,
                    homeContent = {
                        HomeRoute(
                            codeBlockPrefs = codeBlockPrefs,
                        )
                    },
                )
            }
        }

        // 设置附件观察者（使用 ViewModel）
        setupAttachmentObservers()
        
        // 设置附件选择器
        setupAriesAttachment()

        setupEdgeToEdge()

        checkUserAgreement()

        restorePendingQwenDownloadIds()
        registerQwenDownloadReceiverIfNeeded()
        reconcilePendingQwenDownloads()

        observeUiPreferences()
        syncMessageTranscript()
        registerAutomationLogReceiverIfNeeded()

        restoreApiKey()

        elevateAiBar()

        if (!tryRestoreConversations()) {
            startNewChat(clearUi = true)
        }

        initSherpaModel()

        silentCheckUpdatesOnLaunch()
        refreshMainChatPromptOnLaunch()

        // Handle automation intent extras from cold-start launch
        handleAutomationLaunchIntent()
    }

    @Composable
    private fun HomeRoute(
        codeBlockPrefs: CodeBlockPrefs,
    ) {
        DebugRecomposeLogger(scope = "HomeRoute")
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val composeScope = rememberCoroutineScope()
        val currentTranscriptItems by remember { transcriptItemsState }
        val currentStreamingConvId by remember { streamingTranscriptConversationIdState }
        val currentStreamingItem by remember { streamingTranscriptItemState }
        val currentTranscriptAnimKey by remember { transcriptAnimationKeyState }
        val currentStatusText by remember { statusTextState }
        val currentStatusVisible by remember { statusVisibleState }
        val currentDrawerSearchQuery by remember { drawerSearchQueryState }
        val currentDrawerItems by remember { drawerConversationItemsState }
        val currentDrawerEmptyMessage by remember { drawerEmptyMessageState }
        val currentContentAlpha by remember { homeContentAlphaState }
        val currentContentScale by remember { homeContentScaleState }
        val modelDisplayName = getDisplayNameForModel(resolveApiModel())
        val aiNoticeText = remember { getString(R.string.ai_generated_notice) }

        DisposableEffect(drawerState, composeScope) {
            drawerStateHolder.value = drawerState
            composeScopeHolder = composeScope
            onDispose {
                pendingDrawerNavigationAction = null
                if (drawerStateHolder.value === drawerState) {
                    drawerStateHolder.value = null
                }
                if (composeScopeHolder === composeScope) {
                    composeScopeHolder = null
                }
            }
        }

        LaunchedEffect(drawerState) {
            pendingDrawerNavigationAction = null
            refreshDrawerConversationItems()
            if (drawerState.currentValue != DrawerValue.Closed) {
                drawerState.close()
            }
        }

        val onToggleStatus = remember {
            { statusVisibleState.value = !statusVisibleState.value }
        }
        val onOpenDrawer = remember(drawerState, composeScope) {
            {
                if (!onboardingOverlay.isShowing()) {
                    vibrateLight()
                    hideKeyboard()
                    composeScope.launch { drawerState.open() }
                }
            }
        }
        val onNewChat = remember(drawerState, composeScope) {
            {
                vibrateLight()
                composeScope.launch { drawerState.close() }
                startNewChat(clearUi = true)
            }
        }
        val onOpenFloatingWindow = remember {
            {
                vibrateLight()
                enterMiniWindowMode()
            }
        }
        val onDrawerSearchQueryChange = remember {
            { query: String ->
                drawerSearchQueryState.value = query
                refreshDrawerConversationItems()
            }
        }
        val onDrawerConversationClick = remember(drawerState, composeScope) {
            { conversationId: Long ->
                val target = conversations.firstOrNull { it.id == conversationId }
                if (target != null) {
                    activeConversation = target
                    renderConversation(target, animateTransition = true)
                    persistConversations()
                    composeScope.launch { drawerState.close() }
                }
            }
        }
        val onDrawerConversationLongClick = remember {
            { conversationId: Long ->
                if (deleteConversationById(conversationId, clearUiForActive = true)) {
                    vibrateLight()
                }
            }
        }
        val onDrawerSettingsClick = remember {
            {
                vibrateLight()
                navigateToRoute(Routes.Settings.route, closeDrawerFirst = true)
            }
        }
        val onCopyMessage: (TranscriptMessageUi) -> Unit = remember {
            { item: TranscriptMessageUi ->
                copyTranscriptMessage(item.copyText)
                Unit
            }
        }
        val onRetryMessage: (TranscriptMessageUi) -> Unit = remember {
            { item: TranscriptMessageUi ->
                val retryText = item.retryText.orEmpty()
                if (retryText.isBlank()) {
                    Toast.makeText(this@MainActivity, "未找到可重试的用户问题", Toast.LENGTH_SHORT).show()
                } else {
                    retryMessage(retryText)
                }
                Unit
            }
        }
        val onEditMessage: (TranscriptMessageUi) -> Unit = remember {
            { item: TranscriptMessageUi ->
                if (isRequestInFlight) {
                    Toast.makeText(this@MainActivity, "正在生成回复，请稍后…", Toast.LENGTH_SHORT).show()
                } else {
                    val conversation = activeConversation
                    if (conversation != null) {
                        val newText = item.body
                        val msgIndex = item.messageIndex
                        if (msgIndex in conversation.messages.indices) {
                            conversation.messages[msgIndex] =
                                conversation.messages[msgIndex].copy(content = newText)
                            if (msgIndex + 1 < conversation.messages.size) {
                                conversation.messages.subList(msgIndex + 1, conversation.messages.size).clear()
                            }
                            conversation.updatedAt = System.currentTimeMillis()
                            persistConversations()
                            renderConversation(conversation)
                            sendMessage(newText, resendUser = false, retryMode = true)
                        }
                    }
                }
                Unit
            }
        }
        val onAutomationAction: (TranscriptMessageUi) -> Unit = remember {
            { item: TranscriptMessageUi ->
                handleTranscriptAutomationAction(item)
                Unit
            }
        }
        val onEmptySuggestionClick = remember {
            { suggestion: String ->
                vibrateLight()
                if (inputBarState.value !is InputState.Generating) {
                    inputBarState.value = InputState.Idle
                }
                inputTextState.value = suggestion.trim()
            }
        }
        val onDrawerClosed = remember {
            { runPendingDrawerNavigationAction() }
        }

        HomeScreen(
            drawerState = drawerState,
            drawerGesturesEnabled = !onboardingOverlay.isShowing(),
            statusText = currentStatusText,
            statusVisible = currentStatusVisible,
            onToggleStatus = onToggleStatus,
            onOpenDrawer = onOpenDrawer,
            onNewChat = onNewChat,
            onOpenFloatingWindow = onOpenFloatingWindow,
            modelName = modelDisplayName,
            drawerSearchQuery = currentDrawerSearchQuery,
            drawerItems = currentDrawerItems,
            drawerEmptyMessage = currentDrawerEmptyMessage,
            onDrawerSearchQueryChange = onDrawerSearchQueryChange,
            onDrawerConversationClick = onDrawerConversationClick,
            onDrawerConversationLongClick = onDrawerConversationLongClick,
            onDrawerSettingsClick = onDrawerSettingsClick,
            transcriptPaneContent = { bottomOverlayPadding, spacingMd, spacingXxxs ->
                HomeTranscriptRoute(
                    bottomOverlayPadding = bottomOverlayPadding,
                    spacingMd = spacingMd,
                    spacingXxxs = spacingXxxs,
                    codeBlockPrefs = codeBlockPrefs,
                    onCopyMessage = onCopyMessage,
                    onRetryMessage = onRetryMessage,
                    onEditMessage = onEditMessage,
                    onAutomationAction = onAutomationAction,
                    onEmptySuggestionClick = onEmptySuggestionClick,
                )
            },
            inputBarContent = { HomeInputBar() },
            aiNoticeText = aiNoticeText,
            contentAlpha = currentContentAlpha,
            contentScale = currentContentScale,
            onboardingContent = { onboardingOverlay.Render() },
            historyDialogContent = { HomeHistoryDialog() },
            onDrawerClosed = onDrawerClosed,
        )
    }

    @Composable
    private fun HomeTranscriptRoute(
        bottomOverlayPadding: Dp,
        spacingMd: Dp,
        spacingXxxs: Dp,
        codeBlockPrefs: CodeBlockPrefs,
        onCopyMessage: (TranscriptMessageUi) -> Unit,
        onRetryMessage: (TranscriptMessageUi) -> Unit,
        onEditMessage: (TranscriptMessageUi) -> Unit,
        onAutomationAction: (TranscriptMessageUi) -> Unit,
        onEmptySuggestionClick: (String) -> Unit,
    ) {
        DebugRecomposeLogger(scope = "HomeTranscriptRoute")
        val currentTranscriptItems by remember { transcriptItemsState }
        val currentStreamingConvId by remember { streamingTranscriptConversationIdState }
        val currentStreamingItem by remember { streamingTranscriptItemState }
        val currentTranscriptAnimKey by remember { transcriptAnimationKeyState }
        val currentThinkingExpanded by remember { thinkingExpandedByDefaultState }
        val currentScrollSignal by remember { scrollToBottomSignalState }
        val immutableTranscriptItems = remember(currentTranscriptItems) {
            currentTranscriptItems.toImmutableList()
        }

        HomeTranscriptPane(
            transcriptItems = immutableTranscriptItems,
            streamingTranscriptItem =
                currentStreamingItem?.takeIf {
                    currentStreamingConvId == activeConversation?.id
                },
            transcriptResetKey = currentTranscriptAnimKey,
            thinkingExpandedByDefault = currentThinkingExpanded,
            codeBlockPrefs = codeBlockPrefs,
            onCopyMessage = onCopyMessage,
            onRetryMessage = onRetryMessage,
            onEditMessage = onEditMessage,
            onAutomationAction = onAutomationAction,
            onEmptySuggestionClick = onEmptySuggestionClick,
            scrollToBottomSignal = currentScrollSignal,
            bottomOverlayPadding = bottomOverlayPadding,
            spacingMd = spacingMd,
            spacingXxxs = spacingXxxs,
        )
    }

    @Composable
    private fun HomeHistoryDialog() {
        val currentShowHistoryDialog by remember { showHistoryDialogState }
        if (!currentShowHistoryDialog) return

        ConversationHistoryDialog(
            items = buildHistoryDialogItems(),
            onDismiss = {
                vibrateLight()
                showHistoryDialogState.value = false
            },
            onSelect = { conversationId ->
                conversations.firstOrNull { it.id == conversationId }?.let { conversation ->
                    activeConversation = conversation
                    renderConversation(conversation, animateTransition = true)
                    persistConversations()
                }
                vibrateLight()
                showHistoryDialogState.value = false
            },
            onDelete = { conversationId ->
                if (!deleteConversationById(conversationId, clearUiForActive = true)) {
                    return@ConversationHistoryDialog
                }
                if (buildHistoryDialogItems().isEmpty()) {
                    showHistoryDialogState.value = false
                }
            },
        )
    }

    private fun refreshMainChatPromptOnLaunch() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                MainChatPromptRepository.refreshFromRemoteIfNewer(this@MainActivity)
            }
        }
    }

    private fun silentCheckUpdatesOnLaunch() {
        UpdateStartupCoordinator.silentCheckOnLaunch(this)
    }

    private fun checkUserAgreement() {
        if (!prefs.getBoolean("user_agreement_accepted", false)) {
            showUserAgreementDialog()
        }
    }

    private fun showUserAgreementDialog() {
        onboardingOverlay.showOnboarding()
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val useLightSystemBarIcons = resources.getBoolean(R.bool.m3t_light_system_bars)
        WindowCompat.getInsetsController(window, window.decorView)?.let {
            it.isAppearanceLightStatusBars = useLightSystemBarIcons
            it.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }
    }

    private fun observeUiPreferences() {
        lifecycleScope.launch {
            uiPreferencesRepository.thinkingExpandedByDefaultFlow.collect { expanded ->
                thinkingExpandedByDefaultState.value = expanded
            }
        }
    }

    private fun syncMessageTranscript(conversation: Conversation? = activeConversation) {
        transcriptItemsState.value =
            conversation
                ?.messages
                ?.mapIndexed { index, message ->
                    buildTranscriptMessageUi(
                        conversationId = conversation.id,
                        index = index,
                        message = message,
                    )
                }
                .orEmpty()
        syncAutomationAutoConfirmState(conversation)
    }

    private fun replaceStreamingTranscriptWithConversation(
        conversation: Conversation? = activeConversation,
    ) {
        Snapshot.withMutableSnapshot {
            streamingTranscriptConversationIdState.value = null
            streamingTranscriptItemState.value = null
            transcriptItemsState.value =
                conversation
                    ?.messages
                    ?.mapIndexed { index, message ->
                        buildTranscriptMessageUi(
                            conversationId = conversation.id,
                            index = index,
                            message = message,
                        )
                    }
                    .orEmpty()
        }
        syncAutomationAutoConfirmState(conversation)
    }

    private fun refreshAutomationCardsForCurrentConversation() {
        activeConversation?.let { syncMessageTranscript(it) }
    }

    private fun updateStreamingTranscript(
        retryText: String?,
        thinking: String,
        answer: String,
        bodyPreview: StreamingTranscriptBodyPreview? = null,
    ) {
        val conversationId = activeConversation?.id ?: -1L
        val futureAssistantIndex = activeConversation?.messages?.size ?: -1
        val streamingId =
            if (conversationId >= 0L && futureAssistantIndex >= 0) {
                "$conversationId-ai-$futureAssistantIndex"
            } else {
                "streaming-assistant"
            }
        val nextConversationId = conversationId.takeIf { it >= 0L }
        val nextBody = answer
        val nextThinking = thinking.ifBlank { null }

        if (streamingTranscriptConversationIdState.value != nextConversationId) {
            streamingTranscriptConversationIdState.value = nextConversationId
        }
        val existingItem = streamingTranscriptItemState.value
        if (existingItem != null &&
            existingItem.id == streamingId &&
            existingItem.conversationId == conversationId &&
            existingItem.messageIndex == futureAssistantIndex
        ) {
            existingItem.update(
                nextBody = nextBody,
                nextThinking = nextThinking,
                nextCopyText = answer,
                nextBodyPreview = bodyPreview,
            )
        } else {
            streamingTranscriptItemState.value =
                StreamingTranscriptMessageState(
                    conversationId = conversationId,
                    messageIndex = futureAssistantIndex,
                    id = streamingId,
                    author = "Aries AI",
                    retryText = retryText,
                    initialBody = nextBody,
                    initialThinking = nextThinking,
                    initialCopyText = answer,
                    initialBodyPreview = bodyPreview ?: buildStreamingTranscriptBodyPreview(nextBody),
                )
        }
    }

    private fun clearStreamingTranscript() {
        streamingTranscriptConversationIdState.value = null
        streamingTranscriptItemState.value = null
    }

    private fun updateStreamingTranscriptFromBuffers(
        retryText: String?,
        reasoning: CharSequence,
        answer: CharSequence,
        bodyPreview: StreamingTranscriptBodyPreview? = null,
    ) {
        updateStreamingTranscript(
            retryText = retryText,
            thinking = reasoning.toString(),
            answer = answer.toString(),
            bodyPreview = bodyPreview,
        )
    }

    private fun computeStreamingUiFrameDelayMs(
        reasoningLength: Int,
        answerLength: Int,
    ): Long {
        val maxLength = maxOf(reasoningLength, answerLength)
        return when {
            maxLength >= 8000 -> 220L
            maxLength >= 4000 -> 160L
            maxLength >= 2000 -> 120L
            else -> STREAMING_UI_FRAME_DELAY_MS
        }
    }

    private fun buildTranscriptMessageUi(
        conversationId: Long,
        index: Int,
        message: UiMessage,
    ): TranscriptMessageUi {
        if (message.isUser) {
            return TranscriptMessageUi(
                conversationId = conversationId,
                messageIndex = index,
                id = "$conversationId-user-$index",
                author = message.author,
                body = message.content.trim(),
                thinking = null,
                thinkingDurationMs = null,
                isUser = true,
                attachments =
                    message.attachments
                        .orEmpty()
                        .map { attachment ->
                            attachment.fileName.ifBlank { File(attachment.filePath).name.ifBlank { attachment.filePath } }
                        }
                        .toImmutableList(),
                isAutomation = false,
                copyText = message.content.trim(),
                retryText = message.content.trim(),
            )
        }

        val messageRef = AutomationMessageRef(conversationId = conversationId, messageIndex = index)
        val (contentWithoutLogMarkers, embeddedAutomationLogs) = extractAutomationLogMarkers(message.content)
        val (contentWithoutConfirmedMarker, hasConfirmedMarker) =
            extractAutomationConfirmedMarker(contentWithoutLogMarkers)
        val (contentWithoutRejectedMarker, hasRejectedMarker) =
            extractAutomationRejectedMarker(contentWithoutConfirmedMarker)
        val (contentWithoutConfirmMarker, confirmInstruction) =
            extractAutomationConfirmInstruction(contentWithoutRejectedMarker)
        val (thinking, answerRaw) = parseStoredAiContent(contentWithoutConfirmMarker)
        val cleanedAnswer = answerRaw.trim().ifBlank { stripAutomationMarker(contentWithoutConfirmMarker).trim() }
        val isAutomationMessage =
            cleanedAnswer.startsWith("【自动化】") || extractAutomationCommand(cleanedAnswer) != null
        val automationCommandText = extractAutomationCommand(cleanedAnswer) ?: confirmInstruction
        val automationLogs =
            buildList {
                addAll(embeddedAutomationLogs)
                cleanedAnswer
                    .lines()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("系统未就绪：") }
                    ?.let { add(it) }
            }
        val automationUi =
            buildTranscriptAutomationUi(
                messageRef = messageRef,
                command = automationCommandText,
                logs = automationLogs,
                hasConfirm = !confirmInstruction.isNullOrBlank(),
                hasConfirmed = hasConfirmedMarker,
                hasRejected = hasRejectedMarker,
            )
        val displayBody =
            cleanedAnswer
                .removePrefix("【自动化】")
                .trim()
                .ifBlank {
                    if (isAutomationMessage) {
                        getString(R.string.automation_scene_waiting)
                    } else {
                        cleanedAnswer
                    }
                }

        return TranscriptMessageUi(
            conversationId = conversationId,
            messageIndex = index,
            id = "$conversationId-ai-$index",
            author = "Aries AI",
            body = displayBody,
            thinking = thinking,
            thinkingDurationMs = message.thinkingDurationMs,
            isUser = false,
            attachments = persistentListOf(),
            isAutomation = isAutomationMessage,
            automation = automationUi,
            copyText = displayBody,
            retryText = findRetryTextForAssistantMessage(index),
        )
    }

    private fun buildTranscriptAutomationUi(
        messageRef: AutomationMessageRef,
        command: String?,
        logs: List<String>,
        hasConfirm: Boolean,
        hasConfirmed: Boolean,
        hasRejected: Boolean,
    ): TranscriptAutomationUi? {
        if (command.isNullOrBlank()) return null

        val hasTerminalLog = logs.any { isAutomationTerminalLog(it) }
        val isNormalFinished = isAutomationNormalFinished(logs)
        val isTerminatePending = isAutomationTerminatePending(messageRef)
        val readyStateForPendingCommand =
            if (!hasConfirm && !hasConfirmed && !hasRejected && !hasTerminalLog && logs.isEmpty()) {
                resolveAutomationReadyState()
            } else {
                null
            }
        val status =
            when {
                isTerminatePending -> getString(R.string.automation_scene_stop_requested)
                hasRejected -> getString(R.string.automation_scene_rejected)
                hasConfirm -> getString(R.string.automation_scene_need_confirm)
                hasTerminalLog -> getString(R.string.automation_scene_finished)
                logs.isNotEmpty() -> getString(R.string.automation_scene_running)
                hasConfirmed -> getString(R.string.automation_scene_confirmed)
                readyStateForPendingCommand?.ready == true -> getString(R.string.automation_scene_need_confirm)
                else -> getString(R.string.automation_scene_not_ready)
            }

        val countdownSeconds = automationCountdownSeconds[messageRef]
        val actionLabel: String?
        val actionEnabled: Boolean
        val isDestructive: Boolean
        val confirmInstruction: String?
        var retryInstruction: String? = null
        var secondaryActionLabel: String? = null
        var secondaryActionEnabled = false
        var openSetupAction = false

        when {
            hasRejected -> {
                actionLabel = getString(R.string.automation_rejected)
                actionEnabled = false
                isDestructive = false
                confirmInstruction = null
            }
            hasConfirmed && hasTerminalLog -> {
                // 已完成：显示「已执行」(disabled) + 「重试」(enabled)
                actionLabel = getString(R.string.automation_done)
                actionEnabled = false
                isDestructive = false
                confirmInstruction = null
                retryInstruction = command
                secondaryActionLabel = getString(R.string.automation_retry)
                secondaryActionEnabled = true
            }
            hasConfirmed -> {
                actionLabel =
                    if (isTerminatePending) {
                        getString(R.string.automation_terminating)
                    } else {
                        getString(R.string.automation_terminate)
                    }
                actionEnabled = !isTerminatePending
                isDestructive = true
                confirmInstruction = null
            }
            hasConfirm -> {
                actionLabel =
                    if (countdownSeconds != null) {
                        getString(R.string.automation_confirm_countdown, countdownSeconds)
                    } else {
                        getString(R.string.automation_execute_now)
                    }
                actionEnabled = true
                isDestructive = false
                confirmInstruction = command
            }
            else -> {
                // 系统未就绪：显示"去开启"按钮引导用户开启权限
                val readyState = readyStateForPendingCommand ?: resolveAutomationReadyState()
                if (!readyState.ready) {
                    val shizukuConnected = ShizukuBridge.pingBinder()
                    val shizukuGranted = if (shizukuConnected) ShizukuBridge.hasPermission() else false
                    val btnLabel = if (shizukuConnected && shizukuGranted) {
                        getString(R.string.automation_setup_enable_accessibility)
                    } else {
                        getString(R.string.automation_setup_go_enable)
                    }
                    actionLabel = null
                    actionEnabled = false
                    isDestructive = false
                    confirmInstruction = null
                    secondaryActionLabel = btnLabel
                    secondaryActionEnabled = true
                    openSetupAction = true
                } else {
                    actionLabel = getString(R.string.automation_execute_now)
                    actionEnabled = true
                    isDestructive = false
                    confirmInstruction = command
                }
            }
        }

        return TranscriptAutomationUi(
            command = command,
            status = status,
            logs = logs.toImmutableList(),
            actionLabel = actionLabel,
            actionEnabled = actionEnabled,
            isDestructive = isDestructive,
            confirmInstruction = confirmInstruction,
            autoCollapseLogs = isNormalFinished,
            retryInstruction = retryInstruction,
            secondaryActionLabel = secondaryActionLabel,
            secondaryActionEnabled = secondaryActionEnabled,
            openSetupAction = openSetupAction,
        )
    }

    private fun isAutomationNormalFinished(logs: List<String>): Boolean {
        if (logs.isEmpty()) return false
        var hasFinished = false
        logs.forEach { raw ->
            val line = normalizeAutomationLogLine(raw)
            if (line.startsWith("结束：") || line.startsWith("结束:")) {
                hasFinished = true
            }
            if (line.startsWith("异常：") || line.startsWith("异常:") || line == "已停止" || line.startsWith("已请求停止")) {
                return false
            }
        }
        return hasFinished
    }

    private fun syncTranscriptForAutomationMessage(messageRef: AutomationMessageRef?) {
        val ref = messageRef ?: return
        if (activeConversation?.id == ref.conversationId) {
            syncMessageTranscript(activeConversation)
        }
    }

    private fun handleTranscriptAutomationAction(item: TranscriptMessageUi) {
        val automation = item.automation ?: return
        if (!item.isAutomation || (!automation.actionEnabled && !automation.secondaryActionEnabled)) return

        // 处理"去开启"/"一键开启无障碍"按钮（系统未就绪时）
        if (automation.openSetupAction) {
            val shizukuConnected = ShizukuBridge.pingBinder()
            val shizukuGranted = if (shizukuConnected) ShizukuBridge.hasPermission() else false
            if (shizukuConnected && shizukuGranted) {
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        val serviceId = "$packageName/${PhoneAgentAccessibilityService::class.java.name}"
                        ShizukuBridge.execResultArgs(
                            listOf("settings", "put", "secure", "enabled_accessibility_services", serviceId),
                        )
                        ShizukuBridge.execResultArgs(
                            listOf("settings", "put", "secure", "accessibility_enabled", "1"),
                        )
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.automation_setup_accessibility_enabled), Toast.LENGTH_SHORT).show()
                        refreshAutomationCardsForCurrentConversation()
                    }
                }
            } else {
                navigateToRoute(Routes.Automation.route)
            }
            return
        }

        val messageRef = AutomationMessageRef(item.conversationId, item.messageIndex)
        if (!automation.confirmInstruction.isNullOrBlank()) {
            confirmAutomationFromHome(automation.confirmInstruction, messageRef)
            return
        }

        // 重试操作：重新执行上一次的自动化命令
        if (automation.retryInstruction != null) {
            val readyState = resolveAutomationReadyState()
            if (!readyState.ready) {
                Toast.makeText(
                    this,
                    resolveAutomationNotReadyToast(readyState.reason),
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
            resetAutomationPanelForRetry(item, automation.retryInstruction)
            AutomationViewModel.pendingLaunchArgs = AutomationViewModel.LaunchArgs(
                automationTask = automation.retryInstruction,
                automationSource = AutomationInstructionRequest.Source.ADVANCED_AI.wireValue,
                automationAutoStart = true,
                keepMainOnTop = true,
                popBackImmediately = true,
            )
            navigateToRoute(Routes.Automation.route)
            return
        }

        requestAutomationStopFromHome()
        markAutomationTerminatePending(messageRef)
        syncTranscriptForAutomationMessage(messageRef)
    }

    private fun findRetryTextForAssistantMessage(messageIndex: Int): String? {
        val conversation = activeConversation ?: return null
        if (messageIndex !in conversation.messages.indices) return null
        for (index in (messageIndex - 1) downTo 0) {
            val candidate = conversation.messages[index]
            if (candidate.isUser && candidate.content.isNotBlank()) {
                return candidate.content
            }
        }
        return conversation.messages.findLast { it.isUser && it.content.isNotBlank() }?.content
    }

    private fun resetAutomationPanelForRetry(
        item: TranscriptMessageUi,
        retryInstruction: String,
    ) {
        val conversation =
            conversations.firstOrNull { it.id == item.conversationId }
                ?: activeConversation?.takeIf { it.id == item.conversationId }
                ?: return
        val messageIndex = item.messageIndex
        if (messageIndex !in conversation.messages.indices) return

        val origin = conversation.messages[messageIndex]
        if (origin.isUser) return

        val commandBody =
            AutomationMessageParser
                .stripAutomationRuntimeMarkers(origin.content)
                .ifBlank { "待转交自动化命令：\n$retryInstruction" }
        val updated = (commandBody.trimEnd() + "\n[[AUTO_CONFIRMED]]").trim()
        if (updated == origin.content) return

        val messageRef = AutomationMessageRef(item.conversationId, messageIndex)
        clearAutomationTerminatePending(messageRef)
        clearAutomationAutoConfirm(messageRef)
        conversation.messages[messageIndex] = origin.copy(content = updated)
        conversation.updatedAt = System.currentTimeMillis()
        syncMessageTranscript(conversation)
        persistConversations()
    }

    private fun copyTranscriptMessage(text: String) {
        if (text.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("AI Reply", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制内容", Toast.LENGTH_SHORT).show()
    }

    private fun deleteConversationById(
        conversationId: Long,
        clearUiForActive: Boolean,
    ): Boolean {
        if (conversations.none { it.id == conversationId }) {
            return false
        }
        conversations.removeAll { it.id == conversationId }
        if (activeConversation?.id == conversationId) {
            activeConversation = null
            startNewChat(clearUi = clearUiForActive)
        } else {
            persistConversations()
        }
        return true
    }

    private fun refreshDrawerConversationItems() {
        val query = drawerSearchQueryState.value.trim()
        val filtered =
            conversations
                .sortedByDescending { it.updatedAt }
                .filter { conversation ->
                    if (query.isBlank()) {
                        true
                    } else {
                        val preview = buildDrawerConversationPreview(conversation)
                        conversation.title.contains(query, ignoreCase = true) ||
                            preview.contains(query, ignoreCase = true)
                    }
                }

        drawerConversationItemsState.value =
            filtered.map { conversation ->
                DrawerConversationUiItem.Conversation(
                    conversationId = conversation.id,
                    title = conversation.title.ifBlank { getString(R.string.top_bar_new_chat) },
                    preview = buildDrawerConversationPreview(conversation),
                    selected = conversation.id == activeConversation?.id,
                )
            }

        drawerEmptyMessageState.value =
            if (query.isBlank()) {
                getString(R.string.drawer_empty)
            } else {
                getString(R.string.drawer_empty_search)
            }
    }

    private fun buildDrawerConversationPreview(conversation: Conversation): String {
        val lastMessage = conversation.messages.lastOrNull() ?: return ""
        return if (lastMessage.isUser) {
            lastMessage.content.trim()
        } else {
            parseStoredAiContent(lastMessage.content).second.trim()
        }
    }

    private fun maybeShowPermissionBottomSheet() {
        onboardingOverlay.showPermissionOnlyIfNeeded()
    }

    override fun onResume() {
        super.onResume()

        handleReturnFromFloatingWindow()
        handleAutomationOverlayReturnIntent()
        refreshAutomationCardsForCurrentConversation()

        restoreApiKey()
        reconcilePendingQwenDownloads()
        maybeShowPermissionBottomSheet()
        onboardingOverlay.onResume()

        // 设置消息同步监听器
        setupMessageSyncListener()

        if (pendingAutomationLogUiRefresh) {
            activeConversation?.let { syncMessageTranscript(it) }
            pendingAutomationLogUiRefresh = false
        }

        // 防止返回应用后气泡底部的复制/重试按钮被隐藏
        revealActionAreasForMessages()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReturnFromFloatingWindow()
        handleAutomationOverlayReturnIntent()
        handleAutomationLaunchIntent()
    }

    override fun onPause() {
        super.onPause()
        // 不再清除消息同步监听器，保持双向同步
        // 监听器会在 Activity 销毁时自动失效
    }

    /**
     * 设置消息同步监听器，用于接收悬浮窗中的新消息
     */
    private fun setupMessageSyncListener() {
        FloatingChatService.setMessageSyncListener(object : FloatingChatService.Companion.MessageSyncListener {
            override fun onMessageAdded(message: String, isUser: Boolean) {
                // 在主线程更新 UI
                runOnUiThread {
                    val c = requireActiveConversation()
                    val content =
                        message
                            .removePrefix("我: ")
                            .removePrefix("AI: ")
                            .removePrefix("Aries: ")
                            .let { if (isUser) it else stripAutomationMarker(it) }
                            .trim()
                    val author = if (isUser) "我" else "Aries AI"
                    
                    // 检查是否已存在该消息（避免重复）
                    val exists = hasEquivalentConversationMessage(c, content, isUser)
                    if (!exists) {
                        c.messages.add(UiMessage(author = author, content = content, isUser = isUser))
                        c.updatedAt = System.currentTimeMillis()
                        syncMessageTranscript(c)
                        smoothScrollToBottom()
                        persistConversations()
                    }
                }
            }
            
            override fun onMessagesCleared() {
                runOnUiThread {
                    resetAutomationPanelRuntimeState()
                    clearStreamingTranscript()
                    syncMessageTranscript()
                }
            }
        })
    }
    
    /**
     * 处理从悬浮窗返回的动画和消息同步
     */
    private fun handleReturnFromFloatingWindow() {
        val fromFloating = intent?.getBooleanExtra("from_floating", false) ?: false
        if (!fromFloating) return
        
        // 清除标志避免重复触发
        intent?.removeExtra("from_floating")
        
        runCatching {
            sendBroadcast(
                Intent(FloatingChatService.ACTION_FLOATING_RETURNED).setPackage(packageName)
            )
        }

        // 【减轻闪动】禁用过渡动画，并用轻微淡入接管首帧观感
        runCatching { overridePendingTransition(0, 0) }
        homeContentAlphaState.value = 0.92f
        lifecycleScope.launch {
            delay(120)
            homeContentAlphaState.value = 1f
        }

        // 同步悬浮窗中的消息到主界面
        syncMessagesFromFloatingWindow()
    }

    private fun handleAutomationOverlayReturnIntent() {
        val currentIntent = intent ?: return
        val shouldScroll = currentIntent.getBooleanExtra(EXTRA_SCROLL_TO_BOTTOM, false)
        val shouldShowStop = currentIntent.getBooleanExtra(EXTRA_SHOW_AUTOMATION_STOP, false)
        if (!shouldScroll && !shouldShowStop) return

        currentIntent.removeExtra(EXTRA_SCROLL_TO_BOTTOM)
        currentIntent.removeExtra(EXTRA_SHOW_AUTOMATION_STOP)

        revealActionAreasForMessages()
        smoothScrollToBottom()
    }

    private fun handleAutomationLaunchIntent() {
        val currentIntent = intent ?: return
        val args = AutomationViewModel.extractLaunchArgsFromIntent(currentIntent) ?: return
        // Clear consumed extras to avoid re-processing
        currentIntent.removeExtra(AutomationViewModel.EXTRA_AUTOMATION_TASK)
        currentIntent.removeExtra(AutomationViewModel.EXTRA_AUTOMATION_SOURCE)
        currentIntent.removeExtra(AutomationViewModel.EXTRA_AUTOMATION_AUTO_START)
        currentIntent.removeExtra(AutomationViewModel.EXTRA_FORCE_TOP_ON_ENTRY)
        currentIntent.removeExtra(AutomationViewModel.EXTRA_KEEP_MAIN_ON_TOP)
        // Stash args for AutomationScreen to consume when it composes
        AutomationViewModel.pendingLaunchArgs = args
        navigateToRoute(Routes.Automation.route)
    }
    
    /**
     * 从悬浮窗同步消息到主界面
     */
    private fun syncMessagesFromFloatingWindow() {
        val c = requireActiveConversation()
        var floatingMessages: List<String>? = null

        // 首先尝试从运行中的服务获取消息
        val floatingService = FloatingChatService.getInstance()
        if (floatingService != null) {
            floatingMessages = floatingService.getMessages()
        }

        // 如果服务未运行，从本地持久化恢复消息
        if (floatingMessages == null || floatingMessages.isEmpty()) {
            try {
                val json = floatingChatPrefs.getFloatingMessagesBlocking()
                if (json != null) {
                    val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                    floatingMessages = com.google.gson.Gson().fromJson(json, type) ?: emptyList()
                }
            } catch (e: Exception) {
                floatingMessages = emptyList()
            }
        }

        if (floatingMessages?.isEmpty() != false) return

        // 解析悬浮窗消息并添加到当前对话
        for (msg in floatingMessages.orEmpty()) {
            val isUser = msg.startsWith("我:") || msg.startsWith("我: ")
            val content =
                msg.removePrefix("我: ")
                    .removePrefix("我:")
                    .removePrefix("Aries: ")
                    .removePrefix("Aries:")
                    .removePrefix("AI: ")
                    .removePrefix("AI:")
                    .let { if (isUser) it else stripAutomationMarker(it) }
                    .trim()

            // 跳过空消息和"思考中"消息
            if (content.isBlank() || content == "思考中...") continue

            // 检查是否已存在该消息
            val exists = hasEquivalentConversationMessage(c, content, isUser)
            if (!exists) {
                val author = if (isUser) "我" else "Aries AI"
                c.messages.add(UiMessage(author = author, content = content, isUser = isUser))
            }
        }

        c.updatedAt = System.currentTimeMillis()

        // 重新渲染对话
        renderConversation(c)
        persistConversations()

        // 清空浮窗消息存储（已同步完成）
        try {
            floatingChatPrefs.clearFloatingMessagesBlocking()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun registerAutomationLogReceiverIfNeeded() {
        if (automationLogReceiverRegistered) return
        val filter = IntentFilter(AutomationLogBridge.ACTION_AUTOMATION_LOG)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(automationLogReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(automationLogReceiver, filter)
            }
            automationLogReceiverRegistered = true
        } catch (_: Exception) {
            automationLogReceiverRegistered = false
        }
    }

    private fun unregisterAutomationLogReceiverIfNeeded() {
        if (!automationLogReceiverRegistered) return
        try {
            unregisterReceiver(automationLogReceiver)
        } catch (_: Exception) {
        } finally {
            automationLogReceiverRegistered = false
        }
    }

    private fun registerQwenDownloadReceiverIfNeeded() {
        if (qwenDownloadReceiverRegistered) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(qwenDownloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(qwenDownloadReceiver, filter)
            }
            qwenDownloadReceiverRegistered = true
        } catch (_: Exception) {
            qwenDownloadReceiverRegistered = false
        }
    }

    private fun unregisterQwenDownloadReceiverIfNeeded() {
        if (!qwenDownloadReceiverRegistered) return
        try {
            unregisterReceiver(qwenDownloadReceiver)
        } catch (_: Exception) {
        } finally {
            qwenDownloadReceiverRegistered = false
        }
    }

    private fun persistPendingQwenDownloadIds() {
        val values = pendingQwenDownloadIds.map { it.toString() }.toSet()
        prefs.edit().putStringSet(qwenPendingDownloadIdsPref, values).apply()
    }

    private fun restorePendingQwenDownloadIds() {
        pendingQwenDownloadIds.clear()
        val values = prefs.getStringSet(qwenPendingDownloadIdsPref, emptySet()).orEmpty()
        values.forEach { token ->
            token.toLongOrNull()?.let { id ->
                if (id > 0L) pendingQwenDownloadIds.add(id)
            }
        }
    }

    private fun queryDownloadStatus(downloadId: Long): Pair<Int, Int> {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) return DownloadManager.STATUS_FAILED to -1
        val query = DownloadManager.Query().setFilterById(downloadId)
        return runCatching {
            dm.query(query).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) {
                    return@use DownloadManager.STATUS_FAILED to -1
                }
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_FAILED
                val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                status to reason
            }
        }.getOrDefault(DownloadManager.STATUS_FAILED to -1)
    }

    private fun refreshLocalModelReadyState() {
        localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(this)
        updateLocalModelSwitchAvailabilityUi()
        updateStatusText()
    }

    private fun updateLocalModelSwitchAvailabilityUi() {
        if (!localModelReady) {
            val prefEnabled = prefs.getBoolean(apiUseLocalModelPref, false)
            if (prefEnabled || useLocalModel) {
                useLocalModel = false
            prefs.edit().putBoolean(apiUseLocalModelPref, false).apply()
            applyLocalModelUiState(false)
            onApiConfigPotentiallyChanged(showNeedsCheckMessage = false)
            } else {
                applyLocalModelUiState(false)
            }
        }
    }

    private fun reconcilePendingQwenDownloads() {
        if (pendingQwenDownloadIds.isEmpty()) {
            refreshLocalModelReadyState()
            return
        }
        var changed = false
        var failedCount = 0
        val iterator = pendingQwenDownloadIds.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            val status = queryDownloadStatus(id).first
            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                if (status == DownloadManager.STATUS_FAILED) {
                    failedCount++
                }
                iterator.remove()
                changed = true
            }
        }
        if (changed) {
            persistPendingQwenDownloadIds()
        }
        refreshLocalModelReadyState()
        if (changed && pendingQwenDownloadIds.isEmpty()) {
            when {
                localModelReady -> {
                    Toast.makeText(
                        this,
                        getString(R.string.m3t_sidebar_qwen_download_complete),
                        Toast.LENGTH_LONG
                    ).show()
                }
                failedCount > 0 -> {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.m3t_sidebar_qwen_download_failed_format,
                            getString(R.string.update_download_failed_unknown)
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun applyLocalModelUiState(enabled: Boolean) {
        useLocalModel = enabled
        updateStatusText()
    }

    private fun resetAutomationPanelRuntimeState() {
        clearAutomationTerminatePending()
        clearAutomationAutoConfirm()
    }

    private fun allocateVoiceSessionId(): Long {
        voiceSessionSeed += 1L
        return voiceSessionSeed
    }

    private fun beginVoiceSession(): Long {
        val id = allocateVoiceSessionId()
        activeVoiceSessionId = id
        return id
    }

    private fun isVoiceSessionActive(sessionId: Long): Boolean {
        return activeVoiceSessionId == sessionId && sessionId > 0L
    }

    private fun clearVoiceSession(expectedSessionId: Long? = null) {
        if (expectedSessionId == null || activeVoiceSessionId == expectedSessionId) {
            activeVoiceSessionId = 0L
        }
    }

    private fun resolveAutomationMessageRef(messageRef: AutomationMessageRef?): AutomationMessageRef? {
        return messageRef
    }

    private fun isAutomationTerminatePending(messageRef: AutomationMessageRef?): Boolean {
        val ref = resolveAutomationMessageRef(messageRef) ?: return false
        return automationTerminatePendingRef == ref
    }

    private fun markAutomationTerminatePending(messageRef: AutomationMessageRef?) {
        automationTerminatePendingRef = resolveAutomationMessageRef(messageRef)
        syncTranscriptForAutomationMessage(automationTerminatePendingRef)
    }

    private fun clearAutomationTerminatePending(messageRef: AutomationMessageRef? = null) {
        if (messageRef == null) {
            val previousRef = automationTerminatePendingRef
            automationTerminatePendingRef = null
            automationTerminateFallbackJob?.cancel()
            automationTerminateFallbackJob = null
            syncTranscriptForAutomationMessage(previousRef)
            return
        }
        val resolved = resolveAutomationMessageRef(messageRef) ?: return
        if (automationTerminatePendingRef == resolved) {
            automationTerminatePendingRef = null
            automationTerminateFallbackJob?.cancel()
            automationTerminateFallbackJob = null
            syncTranscriptForAutomationMessage(resolved)
        }
    }

    private fun hasEquivalentConversationMessage(
        conversation: Conversation,
        content: String,
        isUser: Boolean
    ): Boolean {
        val incomingRaw = content.trim()
        if (incomingRaw.isBlank()) return true
        if (isUser) {
            return conversation.messages.any { it.isUser && it.content.trim() == incomingRaw }
        }
        return AutomationMessageParser.hasEquivalentAssistantMessage(
            existingAssistantMessages =
                conversation.messages.filterNot { it.isUser }.map { it.content },
            incomingRaw = incomingRaw,
        )
    }

    private fun encodeAutomationLogMarker(logLine: String): String {
        return AutomationMessageParser.encodeAutomationLogMarker(logLine)
    }

    private fun decodeAutomationLogMarker(markerPayload: String): String? {
        return AutomationMessageParser.decodeAutomationLogMarker(markerPayload)
    }

    private fun extractAutomationLogMarkers(rawMessage: String): Pair<String, List<String>> {
        return AutomationMessageParser.extractAutomationLogMarkers(rawMessage)
    }

    private fun findLatestAutomationPanelMessageIndex(conversation: Conversation): Int {
        return conversation.messages.indexOfLast { msg ->
            if (msg.isUser) return@indexOfLast false
            val hasConfirmMarker = extractAutomationConfirmInstruction(msg.content).second != null
            val hasConfirmedMarker = extractAutomationConfirmedMarker(msg.content).second
            val hasCommandPrefix = stripAutomationMarker(msg.content).contains("待转交自动化命令：")
            hasConfirmMarker || hasConfirmedMarker || hasCommandPrefix
        }
    }

    private fun findLatestPendingAutomationConfirmTarget(
        conversation: Conversation?,
    ): PendingAutomationConfirmTarget? {
        val targetConversation = conversation ?: return null
        val targetIndex =
            targetConversation.messages.indexOfLast { msg ->
                if (msg.isUser) return@indexOfLast false
                val confirmInstruction = extractAutomationConfirmInstruction(msg.content).second
                val hasConfirmed = extractAutomationConfirmedMarker(msg.content).second
                val hasRejected = extractAutomationRejectedMarker(msg.content).second
                !confirmInstruction.isNullOrBlank() && !hasConfirmed && !hasRejected
            }
        if (targetIndex !in targetConversation.messages.indices) return null

        val instruction =
            extractAutomationConfirmInstruction(targetConversation.messages[targetIndex].content).second
                ?: return null

        return PendingAutomationConfirmTarget(
            messageRef = AutomationMessageRef(targetConversation.id, targetIndex),
            instruction = instruction,
        )
    }

    private fun isHomeAutomationAutoConfirmEnabled(): Boolean {
        if (navControllerState.value?.currentDestination?.route != Routes.Home.route) return false
        return VirtualDisplayConfig.getAutoApproveAutomation(this)
    }

    private fun syncAutomationAutoConfirmState(conversation: Conversation? = activeConversation) {
        val targetConversation = conversation ?: run {
            clearAutomationAutoConfirm()
            return
        }
        if (targetConversation.id != activeConversation?.id) {
            clearAutomationAutoConfirm()
            return
        }

        val target = findLatestPendingAutomationConfirmTarget(targetConversation) ?: run {
            clearAutomationAutoConfirm()
            return
        }

        if (!isHomeAutomationAutoConfirmEnabled()) {
            clearAutomationAutoConfirm()
            return
        }

        if (!resolveAutomationReadyState().ready) {
            clearAutomationAutoConfirm()
            return
        }

        if (
            automationAutoConfirmRef == target.messageRef &&
            automationAutoConfirmJob?.isActive == true &&
            automationCountdownSeconds[target.messageRef] != null
        ) {
            return
        }

        startAutomationAutoConfirm(target)
    }

    private fun startAutomationAutoConfirm(target: PendingAutomationConfirmTarget) {
        val previousRef = dropAutomationAutoConfirmState(syncUi = false)
        automationAutoConfirmRef = target.messageRef
        automationCountdownSeconds[target.messageRef] = HOME_AUTOMATION_AUTO_CONFIRM_SECONDS
        automationAutoConfirmJob =
            lifecycleScope.launch {
                repeat(HOME_AUTOMATION_AUTO_CONFIRM_SECONDS) { elapsedSeconds ->
                    if (automationAutoConfirmRef != target.messageRef) return@launch
                    automationCountdownSeconds[target.messageRef] =
                        HOME_AUTOMATION_AUTO_CONFIRM_SECONDS - elapsedSeconds
                    syncTranscriptForAutomationMessage(target.messageRef)
                    delay(1000L)
                }

                if (!shouldContinueAutomationAutoConfirm(target)) {
                    clearAutomationAutoConfirm(target.messageRef)
                    return@launch
                }

                confirmAutomationFromHome(target.instruction, target.messageRef)
            }

        if (previousRef != null && previousRef != target.messageRef) {
            syncTranscriptForAutomationMessage(previousRef)
        }
        syncTranscriptForAutomationMessage(target.messageRef)
    }

    private fun shouldContinueAutomationAutoConfirm(target: PendingAutomationConfirmTarget): Boolean {
        if (!isHomeAutomationAutoConfirmEnabled()) return false
        if (automationAutoConfirmRef != target.messageRef) return false
        if (!resolveAutomationReadyState().ready) return false

        val conversation = conversations.firstOrNull { it.id == target.messageRef.conversationId } ?: return false
        if (conversation.id != activeConversation?.id) return false

        return findLatestPendingAutomationConfirmTarget(conversation) == target
    }

    private fun appendAutomationLogToExistingPanel(logLine: String): Boolean {
        val conversation = activeConversation ?: return false
        val targetIndex = findLatestAutomationPanelMessageIndex(conversation)
        if (targetIndex < 0) return false

        val normalizedLogLine = normalizeAutomationLogLine(logLine)
        val msg = conversation.messages[targetIndex]
        val marker = encodeAutomationLogMarker(logLine)
        if (msg.content.contains(marker)) {
            return true
        }

        conversation.messages[targetIndex] =
            msg.copy(content = msg.content.trimEnd() + "\n" + marker)
        conversation.updatedAt = System.currentTimeMillis()

        val canRenderNow =
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                activeConversation?.id == conversation.id

        if (!canRenderNow) {
            pendingAutomationLogUiRefresh = true
        }
        if (isAutomationTerminalLog(normalizedLogLine)) {
            clearAutomationTerminatePending(
                AutomationMessageRef(
                    conversationId = conversation.id,
                    messageIndex = targetIndex,
                )
            )
        }
        syncMessageTranscript(conversation)
        persistConversations()
        return true
    }

    private fun appendAutomationLogAsAiMessage(rawLogLine: String) {
        val logLine = filterAutomationLogForHome(rawLogLine) ?: return
        if (logLine.isBlank()) return

        if (appendAutomationLogToExistingPanel(logLine)) {
            return
        }
    }

    private fun filterAutomationLogForHome(rawLogLine: String): String? {
        val line = rawLogLine.trim()
        if (line.isBlank()) return null
        if (isAutomationTerminalLog(line)) return line

        val isStepLine = line.startsWith("[Step ")
        if (!isStepLine) return null

        val keep =
                line.contains("思考：") ||
                        line.contains("修复思考：") ||
                        line.contains("输出：") ||
                        line.contains("修复输出：") ||
                        line.contains("当前动作：")
        return if (keep) line else null
    }

    private fun isAutomationTerminalLog(rawLogLine: String): Boolean {
        return AutomationMessageParser.isAutomationTerminalLog(rawLogLine)
    }

    private data class AutomationReadyState(
            val ready: Boolean,
            val reason: String,
    )

    private fun isAutomationAccessibilityEnabled(): Boolean {
        return runCatching {
            val accessibilityEnabled =
                Settings.Secure.getInt(
                    contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                ) == 1
            if (!accessibilityEnabled) return@runCatching false

            val enabledServices =
                Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()
            if (enabledServices.isBlank()) return@runCatching false

            val serviceId = "$packageName/${PhoneAgentAccessibilityService::class.java.name}"
            enabledServices.split(':').any { it.equals(serviceId, ignoreCase = true) }
        }.getOrDefault(false)
    }

    private fun resolveAutomationNotReadyToast(reason: String): String {
        val firstLine = reason.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (firstLine.isNotBlank()) {
            firstLine
        } else {
            getString(R.string.automation_scene_not_ready)
        }
    }

    private fun resolveAutomationReadyState(): AutomationReadyState {
        val accessibilityConnected = PhoneAgentAccessibilityService.instance != null
        if (accessibilityConnected) {
            return AutomationReadyState(true, "")
        }

        val accessibilityEnabled = isAutomationAccessibilityEnabled()
        if (accessibilityEnabled) {
            return AutomationReadyState(true, "")
        }

        val shizukuConnected = ShizukuBridge.pingBinder()
        val shizukuGranted = if (shizukuConnected) ShizukuBridge.hasPermission() else false
        if (shizukuConnected && shizukuGranted) {
            return AutomationReadyState(true, "")
        }

        val reason =
                when {
                    !shizukuConnected -> "⚠️ 无障碍权限未开启\n\n自动化功能需要无障碍权限才能执行。\n请前往「自动化」页面开启无障碍权限，或启动 Shizuku 服务。"
                    !shizukuGranted -> "⚠️ 无障碍权限未开启\n\n自动化功能需要无障碍权限才能执行。\n请前往「自动化」页面开启无障碍权限，或授权 Shizuku。"
                    else -> "⚠️ 自动化通道不可用\n\n请前往「自动化」页面检查权限配置。"
                }
        return AutomationReadyState(false, reason)
    }
    
    /**
     * 进入小窗模式
     */
    private fun enterMiniWindowMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted =
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                pendingEnterMiniWindowAfterNotifPerm = true
                ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                )
                return
            }
        }

        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }
        
        if (isAnimatingToMiniWindow) return
        isAnimatingToMiniWindow = true
        
        hideKeyboard()
        
        // 播放缩小动画并启动悬浮窗
        playCollapseToMiniWindowAnimation()
    }
    
    /**
     * 缩小到小窗的动画 - 类似微信语音通话缩小效果，一步到位
     * 使用非线性弹性曲线，平滑过渡到目标位置
     */
    private fun playCollapseToMiniWindowAnimation() {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        
        // 目标小窗尺寸和位置
        val miniWindowWidth = 280 * density
        val miniWindowHeight = 360 * density

        val fallbackX = ((displayMetrics.widthPixels - miniWindowWidth) / 2f).toInt()
        val fallbackY = ((displayMetrics.heightPixels - miniWindowHeight) / 2f).toInt()
        val savedX = floatingChatPrefs.getWindowXBlocking()
        val savedY = floatingChatPrefs.getWindowYBlocking()
        val targetX = (savedX.takeIf { it >= 0 } ?: fallbackX).toFloat()
        val targetY = (savedY.takeIf { it >= 0 } ?: fallbackY).toFloat()
        
        // 仅保留近期消息，避免历史过大导致小窗启动异常
        val messagesList = ArrayList<String>()
        activeConversation?.messages?.takeLast(120)?.forEach { msg ->
            val sanitizedContent =
                if (msg.isUser) {
                    msg.content.trim()
                } else {
                    stripAutomationMarker(msg.content).trim()
                }
            if (sanitizedContent.isNotBlank()) {
                messagesList.add("${if (msg.isUser) "我" else "Aries"}: $sanitizedContent")
            }
        }

        val startOk = runCatching {
            FloatingChatService.cacheMessagesForNextStart(this@MainActivity, messagesList)
            // 消息从本地缓存恢复，避免通过 Intent 传长列表触发崩溃
            FloatingChatService.start(
                this@MainActivity,
                messages = null,
                fromX = targetX,
                fromY = targetY,
                fromWidth = miniWindowWidth,
                fromHeight = miniWindowHeight,
                showDelayMs = 120L,
            )
            true
        }.getOrElse {
            Toast.makeText(this, "小窗启动失败，请重试", Toast.LENGTH_SHORT).show()
            false
        }
        if (!startOk) {
            isAnimatingToMiniWindow = false
            return
        }

        isAnimatingToMiniWindow = false

        runCatching {
            homeContentAlphaState.value = 0.85f
            homeContentScaleState.value = 0.985f
            lifecycleScope.launch {
                delay(140)
                moveTaskToBack(true)
                homeContentAlphaState.value = 1f
                homeContentScaleState.value = 1f
            }
        }.recoverCatching {
            moveTaskToBack(true)
        }
    }
    
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (onboardingOverlay.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            return
        }

        if (requestCode == 100 && pendingStartVoice) {
            pendingStartVoice = false
            val granted =
                grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                clearVoiceSession()
                Toast.makeText(this, getString(R.string.voice_permission_granted_retry_hold), Toast.LENGTH_SHORT).show()
            } else {
                PermissionSetupSupport.handleMicPermissionResult(this, grantResults)
                clearVoiceSession()
            }
        }

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            val granted =
                    grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (pendingEnterMiniWindowAfterNotifPerm && granted) {
                pendingEnterMiniWindowAfterNotifPerm = false
                enterMiniWindowMode()
            } else {
                pendingEnterMiniWindowAfterNotifPerm = false
                Toast.makeText(this, "需要通知权限以显示小窗运行通知", Toast.LENGTH_SHORT).show()
            }
        }
        
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            val granted =
                    grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                // 权限授予后，重新启动相机
                launchCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var isDrawerMoving = false
    private var pendingDrawerNavigationAction: (() -> Unit)? = null

    private fun navigateFromDrawer(action: () -> Unit) {
        if (onboardingOverlay.isShowing()) return

        pendingDrawerNavigationAction = action
        hideKeyboard()

        val drawerState = drawerStateHolder.value
        if (drawerState?.isOpen == true) {
            val scope = composeScopeHolder
            if (scope != null) {
                scope.launch { drawerState.close() }
            } else {
                // Fallback: run action directly without animation
                runPendingDrawerNavigationAction()
            }
        } else {
            runPendingDrawerNavigationAction()
        }
    }

    private fun navigateToRoute(route: String, closeDrawerFirst: Boolean = false) {
        val navigate = routeNavigationActionState.value ?: return
        if (closeDrawerFirst) {
            navigateFromDrawer {
                navigate(route)
            }
        } else {
            navigate(route)
        }
    }

    private fun runPendingDrawerNavigationAction() {
        val pendingAction = pendingDrawerNavigationAction ?: return
        pendingDrawerNavigationAction = null
        lifecycleScope.launch { pendingAction.invoke() }
    }

    private fun restoreApiKey() {

        val saved = prefs.getString("api_key", "") ?: ""
        useThirdPartyApi = prefs.getBoolean(apiUseThirdPartyPref, false)
        useLocalModel = prefs.getBoolean(apiUseLocalModelPref, false)
        useAriesApi = appPrefsRepository.getUseAriesApiBlocking()
        ariesSelectedModel = appPrefsRepository.getAriesSelectedModelBlocking()
        apiBaseUrl =
            prefs.getString(apiThirdPartyBaseUrlPref, AutoGlmClient.DEFAULT_BASE_URL)
                .orEmpty()
                .ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        apiModel =
            prefs.getString(apiThirdPartyModelPref, AutoGlmClient.DEFAULT_MODEL)
                .orEmpty()
                .ifBlank { AutoGlmClient.DEFAULT_MODEL }

        if (useLocalModel && !localModelReady) {
            useLocalModel = false
            prefs.edit().putBoolean(apiUseLocalModelPref, false).apply()
        }
        applyLocalModelUiState(useLocalModel)

        if (useAriesApi) {
            remoteApiOk = null
            remoteApiChecking = false
            updateStatusText()
            return
        }

        if (saved.isBlank()) {
            onApiConfigChanged(clearApiValue = true, showNeedsCheckMessage = false)
            remoteApiOk = null
            remoteApiChecking = false
            updateStatusText()
            return
        }

        val lastSig = prefs.getString(apiLastCheckSigPref, "").orEmpty()
        val currentSig = apiConfigSignature(apiKey = saved, baseUrl = resolveApiBaseUrl(), model = resolveApiModel())
        val hasLast = prefs.contains(apiLastCheckOkPref)
        if (hasLast && lastSig.isNotBlank() && lastSig == currentSig) {
            val ok = prefs.getBoolean(apiLastCheckOkPref, false)
            remoteApiOk = ok
            remoteApiChecking = false
            lastCheckedApiKey = saved
            updateStatusText()
            return
        }

        lastCheckedApiKey = saved
        remoteApiOk = null
        remoteApiChecking = false
        updateStatusText()
    }

    private fun startApiCheck(
            key: String,
            baseUrl: String = AutoGlmClient.DEFAULT_BASE_URL,
            model: String = AutoGlmClient.DEFAULT_MODEL,
            useThirdParty: Boolean = useThirdPartyApi,
            force: Boolean,
    ) {
        val k = key.trim()
        if (k.isBlank()) return

        if (!force) {
            if (remoteApiChecking) return
            if (lastCheckedApiKey == k && remoteApiOk != null) return
        }

        remoteApiChecking = true
        remoteApiOk = null
        lastCheckedApiKey = k

        updateStatusText()

        val seq = ++apiCheckSeq
        val normalizedBaseUrl = baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        val baseUrlSecurityError = validateBaseUrlSecurity(normalizedBaseUrl)
        if (baseUrlSecurityError != null) {
            remoteApiChecking = false
            remoteApiOk = false
            updateStatusText()
            if (force) {
                Toast.makeText(this, baseUrlSecurityError, Toast.LENGTH_LONG).show()
            }
            return
        }
        if (force) {
            maybeWarnInsecureHttpBaseUrl(normalizedBaseUrl)
        }
        val resolvedModel = model.ifBlank { AutoGlmClient.DEFAULT_MODEL }
        lifecycleScope.launch {
            val checkResult =
                    withContext(Dispatchers.IO) {
                        AutoGlmClient.checkApiDetailed(
                                apiKey = k,
                                baseUrl = normalizedBaseUrl,
                                model = resolvedModel,
                        )
                    }
            if (seq != apiCheckSeq) return@launch

            val ok = checkResult.ok
            remoteApiChecking = false
            remoteApiOk = ok
            if (!ok && force) {
                Toast.makeText(
                    this@MainActivity,
                    formatApiCheckFailureReason(checkResult.statusCode, checkResult.message),
                    Toast.LENGTH_LONG
                ).show()
            }
            prefs.edit()
                    .putString(
                            apiLastCheckSigPref,
                            apiConfigSignature(
                                    apiKey = k,
                                    baseUrl = normalizedBaseUrl,
                                    model = resolvedModel,
                                    useThirdParty = useThirdParty,
                            ),
                    )
                    .putString(apiLastCheckKeyPref, k)
                    .putBoolean(apiLastCheckOkPref, ok)
                    .putLong(apiLastCheckTimePref, System.currentTimeMillis())
                    .apply()
            updateStatusText()
        }
    }

    private fun onApiConfigChanged(clearApiValue: Boolean = false, showNeedsCheckMessage: Boolean = true) {
        if (clearApiValue) {
            apiCheckSeq++
            remoteApiOk = null
            remoteApiChecking = false
            lastCheckedApiKey = ""
            prefs.edit()
                    .remove(apiLastCheckSigPref)
                    .remove(apiLastCheckKeyPref)
                    .remove(apiLastCheckOkPref)
                    .remove(apiLastCheckTimePref)
                    .apply()
            updateStatusText()
            return
        }

        if (useAriesApi) {
            remoteApiChecking = false
            remoteApiOk = null
            lastCheckedApiKey = ""
            updateStatusText()
            return
        }

        val currentKey = prefs.getString("api_key", "").orEmpty()
        if (currentKey.isBlank()) {
            apiCheckSeq++
            remoteApiOk = null
            remoteApiChecking = false
            lastCheckedApiKey = ""
            prefs.edit()
                    .remove(apiLastCheckSigPref)
                    .remove(apiLastCheckKeyPref)
                    .remove(apiLastCheckOkPref)
                    .remove(apiLastCheckTimePref)
                    .apply()
            updateStatusText()
            return
        }

        val currentSig =
                apiConfigSignature(
                        apiKey = currentKey,
                        baseUrl = resolveApiBaseUrl(),
                        model = resolveApiModel(),
                        useThirdParty = useThirdPartyApi,
                )
        val lastSig = prefs.getString(apiLastCheckSigPref, "").orEmpty()
        val hasLast = prefs.contains(apiLastCheckOkPref)
        if (hasLast && lastSig.isNotBlank() && lastSig == currentSig) {
            val ok = prefs.getBoolean(apiLastCheckOkPref, false)
            remoteApiOk = ok
            remoteApiChecking = false
            lastCheckedApiKey = currentKey
            updateStatusText()
            return
        }

        apiCheckSeq++
        remoteApiOk = null
        remoteApiChecking = false
        lastCheckedApiKey = ""
        updateStatusText()

        if (showNeedsCheckMessage && !apiNeedsRecheckToastShown) {
            // 仅更新状态文案，避免在任务切回主界面时反复弹出干扰性提示
            apiNeedsRecheckToastShown = true
        }
    }

    private fun onApiConfigPotentiallyChanged(showNeedsCheckMessage: Boolean = true) {
        onApiConfigChanged(clearApiValue = false, showNeedsCheckMessage = showNeedsCheckMessage)
    }

    private fun isLocalModelModeEnabled(): Boolean {
        val prefEnabled = prefs.getBoolean(apiUseLocalModelPref, false)
        val enabled = prefEnabled && localModelReady
        if (useLocalModel != enabled) {
            useLocalModel = enabled
        }
        return enabled
    }

    private fun normalizeBaseUrlInput(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        return if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun resolveApiBaseUrl(): String {
        if (isLocalModelModeEnabled()) {
            return AutoGlmClient.DEFAULT_BASE_URL
        }
        if (useAriesApi) return AriesApiClient.ARIES_API_V1_BASE_URL
        if (!useThirdPartyApi) return AutoGlmClient.DEFAULT_BASE_URL
        val rawUrl = apiBaseUrl
        return normalizeBaseUrlInput(rawUrl) ?: AutoGlmClient.DEFAULT_BASE_URL
    }

    private fun validateBaseUrlSecurity(baseUrl: String): String? {
        val parsed = runCatching { Uri.parse(baseUrl.trim()) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        val host = parsed?.host?.lowercase()
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
            return "API Base URL 格式错误，请检查后重试"
        }
        if (scheme != "https" && scheme != "http") {
            return "API Base URL 必须以 https:// 或 http:// 开头"
        }
        return null
    }

    private fun maybeWarnInsecureHttpBaseUrl(baseUrl: String) {
        val parsed = runCatching { Uri.parse(baseUrl.trim()) }.getOrNull() ?: return
        val scheme = parsed.scheme?.lowercase()
        val host = parsed.host?.lowercase()
        val localHosts = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")
        if (scheme == "http" && host !in localHosts) {
            Toast.makeText(this, "当前使用 http:// 地址，API Key 可能明文传输，请确认网络安全", Toast.LENGTH_LONG)
                    .show()
        }
    }

    private fun formatApiCheckFailureReason(statusCode: Int?, message: String?): String {
        val cleanMessage = message?.trim().orEmpty()
        return when {
            statusCode != null && cleanMessage.isNotBlank() ->
                "API 检查失败：HTTP $statusCode，$cleanMessage"
            statusCode != null ->
                "API 检查失败：HTTP $statusCode"
            cleanMessage.isNotBlank() ->
                "API 检查失败：$cleanMessage"
            else ->
                "API 检查失败，请稍后重试"
        }
    }

    private fun resolveApiModel(content: Any? = null): String {
        if (isLocalModelModeEnabled()) {
            return ModelScopeModelDownloader.QWEN35_MODEL_NAME
        }
        if (useAriesApi) {
            return if (containsImagePayload(content)) {
                AriesApiClient.ARIES_VISION_MODEL
            } else {
                AriesApiClient.ARIES_CHAT_MODEL
            }
        }
        if (!useThirdPartyApi) return AutoGlmClient.DEFAULT_MODEL
        return apiModel.trim().ifBlank { AutoGlmClient.DEFAULT_MODEL }
    }

    private fun containsImagePayload(content: Any?): Boolean =
        when (content) {
            is List<*> -> {
                content.filterIsInstance<Map<*, *>>().any { item ->
                    item["type"] == "image_url"
                }
            }
            is Map<*, *> -> content["type"] == "image_url"
            else -> false
        }

    /** 获取用于显示的模型名称（简化版） */
    private fun getDisplayNameForModel(model: String): String {
        return when {
            model.contains("autoglm-phone", ignoreCase = true) -> "AutoGLM"
            model.length > 20 -> model.take(20) + "…"
            else -> model
        }
    }

    private fun apiConfigSignature(
            apiKey: String,
            baseUrl: String,
            model: String,
            useThirdParty: Boolean = useThirdPartyApi,
    ): String {
        val normalizedBaseUrl = baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        val normalizedModel = model.ifBlank { AutoGlmClient.DEFAULT_MODEL }
        val mode =
            when {
                useAriesApi -> "aries"
                useThirdParty -> "third_party"
                else -> "default"
            }
        return "$mode|${apiKey.trim()}|$normalizedBaseUrl|$normalizedModel"
    }

    private fun resolveApiKeyFromInput(): String {
        return prefs.getString("api_key", "").orEmpty().trim()
    }

    private fun resolveActiveApiKey(): String {
        return if (appPrefsRepository.getUseAriesApiBlocking()) {
            appPrefsRepository.getActiveAriesApiKeyBlocking()
        } else {
            prefs.getString("api_key", "").orEmpty()
        }.trim()
    }

    private suspend fun resolveFreshActiveApiKey(): String {
        if (!appPrefsRepository.getUseAriesApiBlocking()) {
            return prefs.getString("api_key", "").orEmpty().trim()
        }
        return appPrefsRepository.getActiveAriesApiKeyBlocking().trim()
    }

    private fun updateStatusText() {
        val localModeEnabled = useLocalModel
        if (localModeEnabled) {
            val localText =
                when {
                    qwenDownloadInFlight || pendingQwenDownloadIds.isNotEmpty() ->
                        getString(R.string.m3t_sidebar_local_model_downloading)
                    localModelReady -> getString(R.string.m3t_sidebar_local_model_ready)
                    else -> getString(R.string.m3t_sidebar_local_model_not_ready)
                }
            statusTextState.value = localText
            return
        }

        if (useAriesApi) {
            statusTextState.value = getString(R.string.settings_model_api_aries_mode)
            return
        }

        val text =
                when {
                    remoteApiChecking && offlineModelReady -> getString(R.string.status_checking_with_voice)
                    remoteApiChecking -> getString(R.string.status_checking)
                    remoteApiOk == true && offlineModelReady -> getString(R.string.status_connected_with_voice)
                    remoteApiOk == true -> getString(R.string.status_connected)
                    remoteApiOk == false && offlineModelReady -> getString(R.string.status_disconnected_with_voice)
                    remoteApiOk == false -> getString(R.string.status_disconnected_short)
                    offlineModelReady -> getString(R.string.status_ready)
                    else -> getString(R.string.status_disconnected)
                }
        statusTextState.value = text
    }

    private fun maskKey(raw: String): String {
        if (raw.isBlank()) return ""
        return if (raw.length <= 6) raw else raw.substring(0, 6) + "*".repeat(raw.length - 6)
    }

    private fun formatChatRequestFailure(t: Throwable?): String {
        return if (t is AutoGlmClient.ApiException && t.code == 429) {
            getString(R.string.chat_server_busy)
        } else {
            "请求失败: ${t?.message ?: "Unknown error"}"
        }
    }

    /** 轻微震动反馈 - 30ms */
    private fun vibrateLight() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            // vibrate may throw SecurityException on some devices if permission/implementation differs;
            // catch any throwable to avoid crashing the app when haptic feedback fails.
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            } catch (_: Throwable) {
                // ignore vibrate failures
            }
        } catch (_: Throwable) {
            // defensively ignore any unexpected errors here to prevent UI crashes
        }
    }

    /** 中等震动反馈 - 30ms */
    private fun vibrateMedium() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            } catch (_: Throwable) {
                // ignore vibrate failures
            }
        } catch (_: Throwable) {
            // defensively ignore any unexpected errors here to prevent UI crashes
        }
    }

    @Composable
    private fun HomeInputBar() {
        val attachmentManager = remember(chatViewModel) { chatViewModel.getAttachmentManager() }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HomeAttachmentPreviewSection(attachmentManager = attachmentManager)
                HomeInputBarControls()
            }

            HomeAttachmentSelectorSheet(attachmentManager = attachmentManager)
        }
    }

    @Composable
    private fun HomeAttachmentPreviewSection(
        attachmentManager: com.ai.phoneagent.helper.AttachmentManager,
    ) {
        DebugRecomposeLogger(scope = "HomeAttachmentPreview")
        val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
        val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
        val attachments by chatViewModel.attachments.collectAsState()
        if (attachments.isEmpty()) return

        com.ai.phoneagent.ui.components.AttachmentPreviewList(
            attachments = attachments,
            attachmentManager = attachmentManager,
            onInsertReference = { attachment ->
                val reference = chatViewModel.createAttachmentReference(attachment)
                inputTextState.value = inputTextState.value + "\n" + reference
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacingSm, vertical = spacingXs),
        )
    }

    @Composable
    private fun HomeAttachmentSelectorSheet(
        attachmentManager: com.ai.phoneagent.helper.AttachmentManager,
    ) {
        DebugRecomposeLogger(scope = "HomeAttachmentSelector")
        val attachmentSelectorVisible by chatViewModel.attachmentSelectorVisible.collectAsState()
        com.ai.phoneagent.ui.components.AttachmentSelectorPanel(
            visible = attachmentSelectorVisible,
            attachmentManager = attachmentManager,
            onDismiss = { chatViewModel.hideAttachmentSelector() },
        )
    }

    @Composable
    private fun HomeInputBarControls() {
        DebugRecomposeLogger(scope = "HomeInputBarControls")
        val text by remember { inputTextState }
        val state by remember { inputBarState }
        val amplitude by remember { voiceAmplitudeState }
        val agentModeEnabled by remember { agentModeEnabledState }
        val attachments by chatViewModel.attachments.collectAsState()

        InputBar(
            state = state,
            text = text,
            onTextChange = { inputTextState.value = it },
            onSend = {
                vibrateLight()
                if (state is InputState.Generating) {
                    if (!shouldStopGeneration) {
                        shouldStopGeneration = true
                        runCatching { AutoGlmClient.cancelActiveStream() }
                        Toast.makeText(this@MainActivity, "已请求终止生成", Toast.LENGTH_SHORT).show()
                    }
                    return@InputBar
                }
                val t = inputTextState.value.trim()
                if (t.isNotBlank() || chatViewModel.attachments.value.isNotEmpty()) {
                    hideKeyboard()
                    if (agentModeEnabled) {
                        val dispatchResult =
                            ActivityAutomationInstructionGateway.dispatchManual(
                                context = this@MainActivity,
                                instruction = t,
                            )
                        if (dispatchResult.success) {
                            inputTextState.value = ""
                            chatViewModel.clearAttachments()
                            Toast.makeText(
                                this@MainActivity,
                                "Agent 模式已激活，任务已转交自动化",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                dispatchResult.message,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        sendMessage(t)
                        inputTextState.value = ""
                        chatViewModel.clearAttachments()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "请输入内容或添加附件", Toast.LENGTH_SHORT).show()
                }
            },
            onVoiceStart = {
                vibrateLight()
                val sessionId = beginVoiceSession()
                ensureAudioPermission {
                    if (!isVoiceSessionActive(sessionId)) return@ensureAudioPermission
                    inputBarState.value = InputState.VoiceRecording()
                    startLocalVoiceInput(sessionId)
                }
            },
            onVoiceEnd = {
                vibrateLight()
                val sessionId = activeVoiceSessionId
                inputBarState.value = InputState.Idle
                stopLocalVoiceInput(expectedSessionId = sessionId, clearSession = true)
            },
            onVoiceCancel = {
                vibrateLight()
                val sessionId = activeVoiceSessionId
                inputBarState.value = InputState.Idle
                stopLocalVoiceInput(expectedSessionId = sessionId, clearSession = true)
            },
            onAttachmentClick = {
                vibrateMedium()
                chatViewModel.toggleAttachmentSelector()
            },
            hasAttachments = attachments.isNotEmpty(),
            agentModeEnabled = agentModeEnabled,
            onAgentToggle = { enabled ->
                agentModeEnabledState.value = enabled
                Toast.makeText(
                    this@MainActivity,
                    if (enabled) "Agent 模式已激活" else "Agent 模式未激活",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onModelSelect = {
                Toast.makeText(this@MainActivity, "模型选择器已打开", Toast.LENGTH_SHORT).show()
            },
            onModeChange = { isVoice ->
                vibrateLight()
                val currentState = inputBarState.value
                if (currentState is InputState.VoiceRecording || currentState is InputState.VoiceRecognizing) {
                    stopVoiceInputAnimation()
                    if (inputTextState.value.startsWith("正在语音输入")) {
                        inputTextState.value = savedInputText
                    }
                    val sessionId = activeVoiceSessionId
                    stopLocalVoiceInput(expectedSessionId = sessionId, clearSession = true)
                }
                inputBarState.value = if (isVoice) InputState.VoiceIdle else InputState.Idle
                if (isVoice) {
                    hideKeyboard()
                }
            },
            voiceAmplitude = amplitude,
            onUpdateCancelState = { isCancelling ->
                val current = inputBarState.value
                if (current is InputState.VoiceRecording) {
                    if (current.isCancelling != isCancelling) {
                        inputBarState.value = current.copy(isCancelling = isCancelling)
                        vibrateLight()
                    }
                }
            },
        )
    }

    private fun hasAssistantOutputInActiveConversation(): Boolean {
        return activeConversation?.messages?.any { !it.isUser && it.content.isNotBlank() } == true
    }

    private fun hideKeyboard() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        currentFocus?.clearFocus()
    }

    private fun elevateAiBar() = Unit

    private fun startNewChat(clearUi: Boolean) {
        // 防止启动多个重复的空会话
        if (isAlreadyInNewChat()) {
            // 已在新对话中，仅刷新UI状态提供视觉反馈
            if (clearUi) {
                transcriptAnimationKeyState.value = System.currentTimeMillis()
                resetAutomationPanelRuntimeState()
                syncMessageTranscript(activeConversation!!)
            }
            smoothScrollToBottom()
            return
        }

        val now = System.currentTimeMillis()
        val c = Conversation(id = now, title = "", messages = mutableListOf(), updatedAt = now)
        conversations.add(0, c)
        activeConversation = c
        clearStreamingTranscript()
        refreshDrawerConversationItems()

        if (clearUi) {
            transcriptAnimationKeyState.value = System.currentTimeMillis()
            resetAutomationPanelRuntimeState()
            syncMessageTranscript(c)
        } else {
            syncMessageTranscript(c)
        }
        smoothScrollToBottom()
        persistConversations()
    }

    private fun isAlreadyInNewChat(): Boolean =
        activeConversation?.messages?.isEmpty() == true &&
            streamingTranscriptConversationIdState.value == null

    private fun requireActiveConversation(): Conversation {
        val c = activeConversation
        if (c != null) return c
        startNewChat(clearUi = false)
        return activeConversation
                ?: Conversation(
                        id = System.currentTimeMillis(),
                        title = "",
                        messages = mutableListOf(),
                        updatedAt = System.currentTimeMillis(),
                )
    }

    private fun renderConversation(
        conversation: Conversation,
        animateTransition: Boolean = false,
    ) {
        resetAutomationPanelRuntimeState()
        if (!animateTransition) {
            syncMessageTranscript(conversation)
            smoothScrollToBottom()
            return
        }

        transcriptAnimationKeyState.value = System.currentTimeMillis()
        syncMessageTranscript(conversation)
        smoothScrollToBottom()
    }

    private fun extractAutomationInstruction(rawAnswer: String): Pair<String, String?> {
        return AutomationMessageParser.extractAutomationInstruction(rawAnswer)
    }

    private fun extractAutomationConfirmInstruction(rawMessage: String): Pair<String, String?> {
        return AutomationMessageParser.extractAutomationConfirmInstruction(rawMessage)
    }

    private fun extractAutomationConfirmedMarker(rawMessage: String): Pair<String, Boolean> {
        return AutomationMessageParser.extractAutomationConfirmedMarker(rawMessage)
    }

    private fun extractAutomationRejectedMarker(rawMessage: String): Pair<String, Boolean> {
        return AutomationMessageParser.extractAutomationRejectedMarker(rawMessage)
    }

    private fun stripAutomationMarker(rawText: String): String {
        return AutomationMessageParser.stripAutomationMarker(rawText)
    }

    private fun sendMessage(content: Any, resendUser: Boolean = true, retryMode: Boolean = false) {

        if (isRequestInFlight) {
            Toast.makeText(this, "正在生成回复，请稍后…", Toast.LENGTH_SHORT).show()
            return
        }

        val localModeEnabled = isLocalModelModeEnabled()
        val usingAriesApi = appPrefsRepository.getUseAriesApiBlocking()
        val apiKey = if (localModeEnabled) "" else resolveActiveApiKey()

        if (localModeEnabled && !localModelReady) {
            Toast.makeText(
                this,
                getString(R.string.m3t_sidebar_local_model_not_ready),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!localModeEnabled && apiKey.isBlank() && !usingAriesApi) {

            Toast.makeText(this, getString(R.string.settings_api_key_missing_entry), Toast.LENGTH_SHORT).show()
            navigateToRoute(Routes.Settings.route)

            return
        }

        val resolvedBaseUrl =
            if (localModeEnabled) AutoGlmClient.DEFAULT_BASE_URL else resolveApiBaseUrl()
        if (!localModeEnabled) {
            val baseUrlSecurityError = validateBaseUrlSecurity(resolvedBaseUrl)
            if (baseUrlSecurityError != null) {
                Toast.makeText(this, baseUrlSecurityError, Toast.LENGTH_LONG).show()
                return
            }
            maybeWarnInsecureHttpBaseUrl(resolvedBaseUrl)
        }
        val c = requireActiveConversation()
        
        // 提取文本用于标题（如果content是多模态数组，提取第一个文本）
        val textForTitle = when (content) {
            is String -> content
            is List<*> -> {
                (content.firstOrNull { 
                    it is Map<*, *> && it["type"] == "text" 
                } as? Map<*, *>)?.get("text") as? String ?: ""
            }
            else -> content.toString()
        }
        
        if (c.title.isBlank()) {
            c.title = textForTitle.take(18)
        }
        
        // 使用 ViewModel 处理附件，构建完整消息内容
        lifecycleScope.launch {
            val requestApiKey =
                if (localModeEnabled) {
                    ""
                } else if (usingAriesApi) {
                    resolveFreshActiveApiKey()
                } else {
                    apiKey
                }
            if (!localModeEnabled && requestApiKey.isBlank()) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.settings_model_api_aries_login_required),
                    Toast.LENGTH_SHORT,
                ).show()
                navigateToRoute(Routes.Settings.route)
                return@launch
            }
            val baseUserText = when (content) {
                is String -> content
                is List<*> -> {
                    content.filterIsInstance<Map<*, *>>()
                        .filter { it["type"] == "text" }
                        .joinToString("\n") { it["text"] as? String ?: "" }
                }
                else -> content.toString()
            }
            val userAttachments = if (resendUser) chatViewModel.attachments.value.toList() else emptyList()
            val messageContent: Any =
                if (resendUser && userAttachments.isNotEmpty()) {
                    chatViewModel.buildMessageWithAttachments(baseUserText, userAttachments)
                } else {
                    content
                }
            val resolvedModel = resolveApiModel(messageContent)

            // 用户消息展示保持纯文本，附件通过图标展示
            val messageContentStr = baseUserText
            
            if (resendUser) {
                rejectPendingAutomationConfirmation(c)
                c.messages.add(
                    UiMessage(
                        author = "我",
                        content = messageContentStr,
                        isUser = true,
                        attachments = userAttachments.takeIf { it.isNotEmpty() }
                    )
                )
                c.updatedAt = System.currentTimeMillis()
                syncMessageTranscript(c)
                persistConversations()
                smoothScrollToBottom()
                
                // 同步消息到悬浮窗（如果运行中）
                if (FloatingChatService.isRunning()) {
                    FloatingChatService.getInstance()?.addMessage("我: $messageContentStr", isUser = true)
                }

                inputTextState.value = ""
                
                // 发送后清除附件
                chatViewModel.clearAttachments()
            }

            isRequestInFlight = true
            
            // 重置停止标志
            shouldStopGeneration = false
            
            // 设置生成状态
            inputBarState.value = InputState.Generating
            
            try {
                val startTime = System.currentTimeMillis()

                smoothScrollToBottom()
                runOnUiThread {
                    updateStreamingTranscript(
                        retryText = baseUserText,
                        thinking = "",
                        answer = "",
                    )
                }

                // 临时变量用于构建完整内容以方便保存
                val reasoningSb = StringBuilder()
                val contentSb = StringBuilder()
                val streamBufferLock = Any()
                val streamUiScheduleLock = Any()
                var pendingStreamUiUpdate: Job? = null
                var floatingStreamStarted = false

                fun flushStreamingTranscriptUi() {
                    val (reasoningSnapshot, contentSnapshot) =
                        synchronized(streamBufferLock) {
                            reasoningSb.toString() to contentSb.toString()
                        }
                    updateStreamingTranscriptFromBuffers(
                        retryText = baseUserText,
                        reasoning = reasoningSnapshot,
                        answer = contentSnapshot,
                    )
                }

                fun requestStreamingTranscriptUiUpdate(immediate: Boolean = false) {
                    synchronized(streamUiScheduleLock) {
                        val pending = pendingStreamUiUpdate
                        if (!immediate && pending?.isActive == true) return

                        pending?.cancel()
                        pendingStreamUiUpdate =
                            lifecycleScope.launch(Dispatchers.Default) {
                                if (!immediate) {
                                    val delayMs =
                                        synchronized(streamBufferLock) {
                                            computeStreamingUiFrameDelayMs(
                                                reasoningLength = reasoningSb.length,
                                                answerLength = contentSb.length,
                                            )
                                        }
                                    delay(delayMs)
                                }

                                val (reasoningSnapshot, contentSnapshot) =
                                    synchronized(streamBufferLock) {
                                        reasoningSb.toString() to contentSb.toString()
                                    }
                                val bodyPreview =
                                    contentSnapshot
                                        .takeIf { it.isNotBlank() }
                                        ?.let(::buildStreamingTranscriptBodyPreview)

                                withContext(Dispatchers.Main) {
                                    updateStreamingTranscriptFromBuffers(
                                        retryText = baseUserText,
                                        reasoning = reasoningSnapshot,
                                        answer = contentSnapshot,
                                        bodyPreview = bodyPreview,
                                    )
                                }

                                synchronized(streamUiScheduleLock) {
                                    if (pendingStreamUiUpdate === this.coroutineContext[Job]) {
                                        pendingStreamUiUpdate = null
                                    }
                                }
                            }
                    }
                }

                fun cancelPendingStreamingTranscriptUiUpdate() {
                    synchronized(streamUiScheduleLock) {
                        pendingStreamUiUpdate?.cancel()
                        pendingStreamUiUpdate = null
                    }
                }

                if (FloatingChatService.isRunning()) {
                    FloatingChatService.getInstance()?.beginExternalStreamAiReply()
                    floatingStreamStarted = true
                }

                // 构建对话历史
                val chatHistory = buildChatHistory(c, retryMode).toMutableList()
                if (resendUser) {
                    val latestUserIndex = chatHistory.indexOfLast { it.role == "user" }
                    if (latestUserIndex >= 0) {
                        // Preserve multimodal payload (e.g. image_url list) for the current turn.
                        chatHistory[latestUserIndex] =
                            ChatRequestMessage(role = "user", content = messageContent)
                    } else {
                        chatHistory.add(ChatRequestMessage(role = "user", content = messageContent))
                    }
                } else {
                    val targetIndex = chatHistory.indexOfLast { it.role == "user" }
                    if (targetIndex >= 0) {
                        while (chatHistory.size > targetIndex + 1) {
                            chatHistory.removeAt(chatHistory.lastIndex)
                        }
                    } else {
                        val last = chatHistory.lastOrNull()
                        // 使用 messageContent（包含附件）
                        if (last == null || last.role != "user") {
                            chatHistory.add(ChatRequestMessage(role = "user", content = messageContent))
                        }
                    }
                }

                var streamOk = false
                var lastError: Throwable? = null
                val maxAttempts = 2

                for (attempt in 1..maxAttempts) {
                    if (attempt > 1) {
                        cancelPendingStreamingTranscriptUiUpdate()
                        synchronized(streamBufferLock) {
                            reasoningSb.clear()
                            contentSb.clear()
                        }
                        runOnUiThread {
                            updateStreamingTranscript(
                                retryText = baseUserText,
                                thinking = "",
                                answer = "",
                            )
                        }
                        if (FloatingChatService.isRunning()) {
                            FloatingChatService.getInstance()?.resetExternalStreamAiReply()
                        }
                    }

                    val reasoningDeltaHandler: (String) -> Unit = { delta ->
                        if (shouldStopGeneration) {
                            // stop requested; ignore incoming delta
                        } else if (delta.isNotBlank()) {
                            synchronized(streamBufferLock) {
                                reasoningSb.append(delta)
                            }
                            requestStreamingTranscriptUiUpdate()
                            if (FloatingChatService.isRunning()) {
                                FloatingChatService.getInstance()
                                    ?.appendExternalReasoningDelta(delta)
                            }
                        }
                    }

                    val contentDeltaHandler: (String) -> Unit = { delta ->
                        if (shouldStopGeneration) {
                            // stop requested; ignore incoming delta
                        } else if (delta.isNotEmpty()) {
                            synchronized(streamBufferLock) {
                                contentSb.append(delta)
                            }
                            requestStreamingTranscriptUiUpdate()

                            // 同步到悬浮窗
                            if (FloatingChatService.isRunning()) {
                                FloatingChatService.getInstance()
                                    ?.appendExternalContentDelta(delta)
                            }
                        }
                    }

                    val result =
                        if (localModeEnabled) {
                            LocalMnnInferenceEngine.sendChatStreamResult(
                                context = this@MainActivity,
                                messages = chatHistory,
                                onReasoningDelta = reasoningDeltaHandler,
                                onContentDelta = contentDeltaHandler,
                                shouldStop = { shouldStopGeneration },
                            )
                        } else {
                            AutoGlmClient.sendChatStreamResult(
                                apiKey = requestApiKey,
                                baseUrl = resolvedBaseUrl,
                                model = resolvedModel,
                                messages = chatHistory,
                                temperature = if (retryMode) 0.7f else null,
                                onReasoningDelta = reasoningDeltaHandler,
                                onContentDelta = contentDeltaHandler,
                                shouldStop = { shouldStopGeneration },
                            )
                        }

                    if (shouldStopGeneration) {
                        streamOk = true
                        break
                    }
                    if (result.isSuccess) {
                        streamOk = true
                        break
                    }
                    lastError = result.exceptionOrNull()
                    if (attempt < maxAttempts) delay(500L * attempt)
                }

                // 处理结果
                val timeCostMs = System.currentTimeMillis() - startTime
                val timeCost = timeCostMs / 1000

                cancelPendingStreamingTranscriptUiUpdate()
                flushStreamingTranscriptUi()

                val finalContent =
                        if (shouldStopGeneration) {
                            "生成已停止"
                        } else if (streamOk) {
                            synchronized(streamBufferLock) { contentSb.toString() }
                        } else {
                            formatChatRequestFailure(lastError)
                        }

                if (floatingStreamStarted && FloatingChatService.isRunning()) {
                    FloatingChatService.getInstance()
                            ?.finishExternalStreamAiReply(timeCost.toInt(), finalContent)
                }

                val parsedPersisted = parseStoredAiContent(finalContent)
                val thinkingContent =
                    synchronized(streamBufferLock) { reasoningSb.toString() }
                        .trim()
                        .ifBlank { parsedPersisted.first.orEmpty().trim() }
                val answerContentRaw =
                    parsedPersisted.second.trim().ifBlank { stripAutomationMarker(finalContent).trim() }
                val (answerContent, markerInAnswer) =
                        extractAutomationInstruction(answerContentRaw)
                val automationInstruction =
                        markerInAnswer ?: extractAutomationInstruction(finalContent).second

                val persistContent =
                        if (thinkingContent.isNotEmpty()) {
                            "<think>${thinkingContent}</think>\n${answerContent}"
                        } else if (answerContent.isNotEmpty()) {
                            answerContent
                        } else {
                            finalContent
                        }

                val cc = requireActiveConversation()
                cc.messages.add(
                    UiMessage(
                        author = "Aries AI",
                        content = persistContent,
                        isUser = false,
                        thinkingDurationMs = timeCostMs
                    )
                )
                cc.updatedAt = System.currentTimeMillis()
                replaceStreamingTranscriptWithConversation(cc)
                persistConversations()

                if (!automationInstruction.isNullOrBlank()) {
                    val readyState = resolveAutomationReadyState()
                    
                    // 如果权限未就绪，显示 Toast 提示
                    if (!readyState.ready) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                resolveAutomationNotReadyToast(readyState.reason),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    
                    val commandMessage =
                        if (readyState.ready) {
                            "待转交自动化命令：\n$automationInstruction\n[[AUTO_CONFIRM:$automationInstruction]]"
                        } else {
                            "待转交自动化命令：\n$automationInstruction\n\n${readyState.reason}"
                        }
                    cc.messages.add(
                        UiMessage(
                            author = "Aries AI",
                            content = commandMessage,
                            isUser = false
                        )
                    )
                    cc.updatedAt = System.currentTimeMillis()
                    syncMessageTranscript(cc)
                    persistConversations()
                }
            } finally {
                isRequestInFlight = false
                inputBarState.value = InputState.Idle
                clearStreamingTranscript()
            }
        }
    }

    /**
     * 真正的重试：回滚该用户问题之后的旧回答，再发起同一问题的新请求。
     * 这样不会只是“再追加一条”，而是覆盖式重试。
     */
    private fun retryMessage(retryText: String): Boolean {
        if (retryText.isBlank()) {
            Toast.makeText(this, "未找到可重试的用户问题", Toast.LENGTH_SHORT).show()
            return false
        }

        if (isRequestInFlight) {
            Toast.makeText(this, "正在生成回复，请稍后…", Toast.LENGTH_SHORT).show()
            return false
        }

        val conversation = activeConversation
        if (conversation != null) {
            val userIndex = conversation.messages.indexOfLast { it.isUser && it.content == retryText }
            if (userIndex >= 0 && userIndex + 1 < conversation.messages.size) {
                conversation.messages.subList(userIndex + 1, conversation.messages.size).clear()
                conversation.updatedAt = System.currentTimeMillis()
                persistConversations()
                renderConversation(conversation)
            }
        }

        sendMessage(retryText, resendUser = false, retryMode = true)
        return true
    }

    private fun confirmAutomationFromHome(
        instruction: String,
        messageRef: AutomationMessageRef,
    ) {
        val readyState = resolveAutomationReadyState()
        if (!readyState.ready) {
            Toast.makeText(
                this,
                resolveAutomationNotReadyToast(readyState.reason),
                Toast.LENGTH_LONG,
            ).show()
            refreshAutomationCardsForCurrentConversation()
            return
        }

        dropAutomationAutoConfirmState(syncUi = false)
        markAutomationCommandConfirmed(instruction, messageRef)
        AutomationViewModel.pendingLaunchArgs = AutomationViewModel.LaunchArgs(
            automationTask = instruction,
            automationSource = AutomationInstructionRequest.Source.ADVANCED_AI.wireValue,
            automationAutoStart = true,
            keepMainOnTop = true,
            popBackImmediately = true,
        )
        navigateToRoute(Routes.Automation.route)
    }

    private fun clearAutomationAutoConfirm(messageRef: AutomationMessageRef? = null) {
        if (messageRef != null) {
            val resolved = resolveAutomationMessageRef(messageRef) ?: return
            if (automationAutoConfirmRef != resolved) return
        }
        dropAutomationAutoConfirmState(syncUi = true)
    }

    private fun dropAutomationAutoConfirmState(syncUi: Boolean): AutomationMessageRef? {
        val previousRef = automationAutoConfirmRef
        automationAutoConfirmJob?.cancel()
        automationAutoConfirmJob = null
        previousRef?.let { automationCountdownSeconds.remove(it) }
        automationAutoConfirmRef = null
        if (syncUi) {
            syncTranscriptForAutomationMessage(previousRef)
        }
        return previousRef
    }

    private fun requestAutomationStopFromHome() {
        runCatching {
            sendBroadcast(
                Intent(VirtualScreenPreviewOverlay.ACTION_STOP_AUTOMATION).apply {
                    setPackage(packageName)
                }
            )
        }
        Toast.makeText(this, getString(R.string.automation_terminate_requested), Toast.LENGTH_SHORT).show()
    }

    private fun markAutomationCommandConfirmed(instruction: String, messageRef: AutomationMessageRef?) {
        val targetConversation =
            when {
                messageRef != null -> conversations.firstOrNull { it.id == messageRef.conversationId }
                else -> activeConversation
            } ?: return

        val targetIndex =
            when {
                messageRef != null &&
                    messageRef.messageIndex in targetConversation.messages.indices -> messageRef.messageIndex
                else -> findLatestAutomationPanelMessageIndex(targetConversation)
            }
        if (targetIndex !in targetConversation.messages.indices) return

        val origin = targetConversation.messages[targetIndex]
        val existingInstruction = extractAutomationConfirmInstruction(origin.content).second
        if (!existingInstruction.isNullOrBlank() && existingInstruction != instruction) return

        val withoutConfirm = extractAutomationConfirmInstruction(origin.content).first
        val withoutConfirmed = extractAutomationConfirmedMarker(withoutConfirm).first
        val withoutRejected = extractAutomationRejectedMarker(withoutConfirmed).first
        val updated =
            (withoutRejected.trimEnd() + "\n[[AUTO_CONFIRMED]]")
                .trim()

        if (updated == origin.content) return

        targetConversation.messages[targetIndex] = origin.copy(content = updated)
        targetConversation.updatedAt = System.currentTimeMillis()
        syncMessageTranscript(targetConversation)
        persistConversations()
    }

    private fun rejectPendingAutomationConfirmation(conversation: Conversation) {
        val targetIndex =
            conversation.messages.indexOfLast { msg ->
                if (msg.isUser) return@indexOfLast false
                val hasConfirm = extractAutomationConfirmInstruction(msg.content).second != null
                val hasConfirmed = extractAutomationConfirmedMarker(msg.content).second
                val hasRejected = extractAutomationRejectedMarker(msg.content).second
                hasConfirm && !hasConfirmed && !hasRejected
            }
        if (targetIndex !in conversation.messages.indices) return

        val origin = conversation.messages[targetIndex]
        val withoutConfirm = extractAutomationConfirmInstruction(origin.content).first
        val withoutConfirmed = extractAutomationConfirmedMarker(withoutConfirm).first
        val withoutRejected = extractAutomationRejectedMarker(withoutConfirmed).first
        val updated = (withoutRejected.trimEnd() + "\n[[AUTO_REJECTED]]").trim()
        if (updated == origin.content) return

        conversation.messages[targetIndex] = origin.copy(content = updated)
        conversation.updatedAt = System.currentTimeMillis()
        clearAutomationAutoConfirm(
            AutomationMessageRef(
                conversationId = conversation.id,
                messageIndex = targetIndex,
            ),
        )
    }

    private fun extractAutomationCommand(message: String): String? {
        val normalized = message.replace("\r\n", "\n").trim()
        if (!normalized.contains("待转交自动化命令：")) return null
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val first = lines.first()
        val inline = first.substringAfter("待转交自动化命令：", "").trim()
        if (inline.isNotBlank()) return inline

        return lines.drop(1).firstOrNull { !it.startsWith("系统未就绪：") }?.trim()?.ifBlank { null }
    }

    private fun normalizeAutomationLogLine(rawLine: String): String {
        return AutomationMessageParser.normalizeAutomationLogLine(rawLine)
    }

    /**
     * 兼容解析历史 AI 消息的多种持久化格式，避免旧分隔符直接显示到界面。
     * 支持：
     * 1) <think>...</think>\nanswer
     * 2) 꽁thinking꽁\nanswer（旧版本）
     * 3) leshootthinkingleshootanswer（更旧版本）
     * 4) 【思考开始】...【思考结束】【回答开始】...【回答结束】
     */
    private fun parseStoredAiContent(raw: String): Pair<String?, String> {
        val source = raw.trim()
        if (source.isBlank()) return null to ""

        val thinkTagRegex = "<think>([\\s\\S]*?)</think>\\s*([\\s\\S]*)".toRegex()
        thinkTagRegex.find(source)?.let { match ->
            val thinking = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val answer = match.groupValues.getOrNull(2)?.trim().orEmpty()
            return (thinking.ifBlank { null }) to stripAutomationMarker(answer)
        }

        val markerStart = source.indexOf('꽁')
        val markerEnd = if (markerStart >= 0) source.indexOf('꽁', markerStart + 1) else -1
        if (markerStart >= 0 && markerEnd > markerStart) {
            val thinking = source.substring(markerStart + 1, markerEnd).trim()
            val answer = source.substring(markerEnd + 1).trimStart('\n', '\r', ' ').trim()
            return (thinking.ifBlank { null }) to stripAutomationMarker(answer)
        }

        val leshootRegex = "leshoot([\\s\\S]*?)leshoot([\\s\\S]*)".toRegex()
        leshootRegex.find(source)?.let { match ->
            val thinking = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val answer = match.groupValues.getOrNull(2)?.trim().orEmpty()
            return (thinking.ifBlank { null }) to stripAutomationMarker(answer)
        }

        val thinkStartTag = "【思考开始】"
        val thinkEndTag = "【思考结束】"
        val answerStartTag = "【回答开始】"
        val answerEndTag = "【回答结束】"
        val thinkStartIdx = source.indexOf(thinkStartTag)
        val thinkEndIdx = source.indexOf(thinkEndTag)
        if (thinkStartIdx >= 0 && thinkEndIdx > thinkStartIdx) {
            val thinking =
                source.substring(
                    thinkStartIdx + thinkStartTag.length,
                    thinkEndIdx
                ).trim()
            var answerPart = source.substring(thinkEndIdx + thinkEndTag.length)
            val answerStartIdx = answerPart.indexOf(answerStartTag)
            if (answerStartIdx >= 0) {
                answerPart = answerPart.substring(answerStartIdx + answerStartTag.length)
            }
            val answerEndIdx = answerPart.indexOf(answerEndTag)
            if (answerEndIdx >= 0) {
                answerPart = answerPart.substring(0, answerEndIdx)
            }
            return (thinking.ifBlank { null }) to stripAutomationMarker(answerPart.trim())
        }

        if (source.contains(answerStartTag)) {
            var answerPart = source.substring(source.indexOf(answerStartTag) + answerStartTag.length)
            val answerEndIdx = answerPart.indexOf(answerEndTag)
            if (answerEndIdx >= 0) {
                answerPart = answerPart.substring(0, answerEndIdx)
            }
            return null to stripAutomationMarker(answerPart.trim())
        }

        return null to stripAutomationMarker(source.replace("꽁", "").trim())
    }
    
    /**
     * 构建完整的对话历史，传递给AI模型
     * 包含系统提示和最近的对话上下文
     */
    private fun buildChatHistory(conversation: Conversation, retryMode: Boolean = false): List<ChatRequestMessage> {
        val history = mutableListOf<ChatRequestMessage>()
        
        // 添加系统提示
        history.add(
            ChatRequestMessage(
                role = "system",
                content = MainChatPromptRepository.getMainChatSystemPrompt(this)
            )
        )

        if (retryMode) {
            val retryNonce = "retry-${System.currentTimeMillis()}"
            history.add(
                ChatRequestMessage(
                    role = "system",
                    content = """
                        以下为内部重试标识，请勿在回答中提及：$retryNonce。
                        请在保持问题语义一致的前提下，提供与之前不同表述的答案。
                    """.trimIndent()
                )
            )
        }
        
        // 添加对话历史（最多保留最近10轮对话，避免上下文过长）
        val recentMessages = conversation.messages.takeLast(20) // 10轮对话 = 20条消息
        for (msg in recentMessages) {
            val content: Any = if (!msg.isUser) {
                // 历史记录传给模型前统一清洗旧分隔符，避免把乱码标记带入上下文。
                val (thinking, answer) = parseStoredAiContent(msg.content)
                if (!thinking.isNullOrBlank()) {
                    "<think>$thinking</think>\n$answer"
                } else {
                    answer
                }
            } else if (!msg.attachments.isNullOrEmpty()) {
                chatViewModel.buildMessageWithAttachments(
                    userMessage = msg.content,
                    sourceAttachments = msg.attachments
                )
            } else {
                msg.content
            }
            
            history.add(ChatRequestMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = content
            ))
        }
        
        return history
    }

    /**
     * 丝滑滚动到底部
     */
    private fun smoothScrollToBottom() {
        scrollToBottomSignalState.value = System.currentTimeMillis()
    }

    /**
     * 重新进入页面时，确保所有已渲染的 AI 气泡都展示底部操作区（复制/重试）。
     * 某些情况下（如动画被打断或 Activity 复用）action_area 可能保持 GONE 状态。
     */
    private fun revealActionAreasForMessages() {
        Unit
    }

    private fun initSherpaModel() {
        sherpaSpeechRecognizer = SherpaSpeechRecognizer(this)
        lifecycleScope.launch {
            val success = sherpaSpeechRecognizer?.initialize() == true
            if (success) {
                offlineModelReady = true
                updateStatusText()
            } else {
                Toast.makeText(this@MainActivity, "语音模型初始化失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 启动"正在语音输入..."的点动画 */
    private fun startVoiceInputAnimation() {
        voiceInputAnimJob?.cancel()
        savedInputText = inputTextState.value
        voiceInputAnimJob = lifecycleScope.launch {
            var dotCount = 1
            while (true) {
                val dots = ".".repeat(dotCount)
                inputTextState.value = "正在语音输入$dots"
                dotCount = if (dotCount >= 3) 1 else dotCount + 1
                delay(400)
            }
        }
    }

    /** 停止"正在语音输入..."动画 */
    private fun stopVoiceInputAnimation() {
        voiceInputAnimJob?.cancel()
        voiceInputAnimJob = null
    }

    private fun applyAutoPunctuationIfNeeded(text: String, onDone: (String) -> Unit) {
        val raw = text.trim()
        if (raw.isBlank()) {
            onDone(text)
            return
        }

        val hasPunctuation = Regex("[，。！？；：,.!?;:]").containsMatchIn(raw)
        if (hasPunctuation || raw.length < 10) {
            onDone(text)
            return
        }

        val localModeEnabled = isLocalModelModeEnabled()
        val usingAriesApi = appPrefsRepository.getUseAriesApiBlocking()
        val apiKey = if (localModeEnabled) "" else resolveActiveApiKey()
        if (!localModeEnabled && apiKey.isBlank() && !usingAriesApi) {
            onDone(text)
            return
        }
        if (localModeEnabled && !localModelReady) {
            onDone(text)
            return
        }

        lifecycleScope.launch {
            val requestApiKey =
                if (localModeEnabled) {
                    ""
                } else if (usingAriesApi) {
                    resolveFreshActiveApiKey()
                } else {
                    apiKey
                }
            if (!localModeEnabled && requestApiKey.isBlank()) {
                onDone(text)
                return@launch
            }
            val punctuationMessages =
                listOf(
                    ChatRequestMessage(
                        role = "system",
                        content = "你是标点助手，只需要在不改变词序的情况下添加中文标点符号。只输出最终文本，不要解释。"
                    ),
                    ChatRequestMessage(
                        role = "user",
                        content = "为以下文本添加标点：\n$raw"
                    )
                )
            val result = withContext(Dispatchers.IO) {
                if (localModeEnabled) {
                    LocalMnnInferenceEngine.sendChatResult(
                        context = this@MainActivity,
                        messages = punctuationMessages,
                    )
                } else {
                    val resolvedBaseUrl = resolveApiBaseUrl()
                    val resolvedModel = resolveApiModel()
                    AutoGlmClient.sendChatResult(
                        apiKey = requestApiKey,
                        baseUrl = resolvedBaseUrl,
                        model = resolvedModel,
                        messages = punctuationMessages,
                        temperature = 0.0f
                    )
                }
            }

            val formatted = result.getOrNull()?.trim().orEmpty()
            val cleaned = formatted
                .removePrefix("输出：")
                .removePrefix("答案：")
                .removePrefix("标点：")
                .trim()
                .removeSurrounding("\"", "\"")
                .removeSurrounding("“", "”")
                .replace(Regex("^```.*?\n"), "")
                .replace(Regex("```$"), "")
                .trim()

            val finalText = if (cleaned.isBlank()) text else cleaned
            withContext(Dispatchers.Main) {
                onDone(finalText)
            }
        }
    }

    private fun startLocalVoiceInput(sessionId: Long) {
        if (!isVoiceSessionActive(sessionId)) return
        val recognizer = sherpaSpeechRecognizer
        if (recognizer == null || !recognizer.isReady()) {
            Toast.makeText(this, "模型加载中…", Toast.LENGTH_SHORT).show()
            inputBarState.value = InputState.Idle
            clearVoiceSession(sessionId)
            return
        }

        if (isListening) return

        voicePrefix = inputTextState.value.trim().let { prefix ->
            if (prefix.isBlank()) "" else if (prefix.endsWith(" ")) prefix else "$prefix "
        }

        // 开始"正在语音输入..."动画
        startVoiceInputAnimation()

        recognizer.startListening(object : SherpaSpeechRecognizer.RecognitionListener {
            override fun onPartialResult(text: String) {
                runOnUiThread {
                    if (!isVoiceSessionActive(sessionId)) return@runOnUiThread
                    // 有识别结果时，停止动画并显示实际文字
                    stopVoiceInputAnimation()
                    inputBarState.value = InputState.VoiceRecognizing
                    val txt = (voicePrefix + text).trimStart()
                    inputTextState.value = txt
                }
            }

            override fun onResult(text: String) {
                runOnUiThread {
                    if (!isVoiceSessionActive(sessionId)) return@runOnUiThread
                    stopVoiceInputAnimation()
                    val txt = (voicePrefix + text).trimStart()
                    inputTextState.value = txt
                }
            }

            override fun onAmplitude(amplitude: Float) {
                runOnUiThread {
                    if (!isVoiceSessionActive(sessionId)) return@runOnUiThread
                    voiceAmplitudeState.value = amplitude
                }
            }

            override fun onFinalResult(text: String) {
                runOnUiThread {
                    if (!isVoiceSessionActive(sessionId)) return@runOnUiThread
                    stopVoiceInputAnimation()
                    val txt = (voicePrefix + text).trimStart()
                    val rawText = if (txt.isBlank()) savedInputText else txt
                    inputTextState.value = rawText

                    val shouldSend = pendingSendAfterVoice
                    pendingSendAfterVoice = false
                    
                    // 仅当当前确实处于语音相关状态时才重置为 Idle
                    // 避免如果在识别过程中已经切换到了其他模式，被这里强行覆盖
                    val currentState = inputBarState.value
                    if (currentState is InputState.VoiceRecording || currentState is InputState.VoiceRecognizing) {
                        inputBarState.value = InputState.Idle
                    }
                    
                    stopLocalVoiceInput(
                        triggerRecognizerStop = false,
                        expectedSessionId = sessionId,
                        clearSession = true,
                    )

                    applyAutoPunctuationIfNeeded(rawText) { punctuated ->
                        if (inputTextState.value == rawText) {
                            inputTextState.value = punctuated
                        }
                        if (shouldSend) {
                            val toSend = punctuated.trim()
                            if (toSend.isBlank()) {
                                Toast.makeText(this@MainActivity, "请输入内容", Toast.LENGTH_SHORT).show()
                                return@applyAutoPunctuationIfNeeded
                            }
                            hideKeyboard()
                            sendMessage(toSend)
                        }
                    }
                }
            }

            override fun onError(exception: Exception) {
                runOnUiThread {
                    if (!isVoiceSessionActive(sessionId)) return@runOnUiThread
                    stopVoiceInputAnimation()
                    // 恢复原来的文字
                    inputTextState.value = savedInputText
                    Toast.makeText(this@MainActivity, "识别失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                    pendingSendAfterVoice = false
                    
                    // 同上，检查状态防止覆盖
                    val currentState = inputBarState.value
                    if (currentState is InputState.VoiceRecording || currentState is InputState.VoiceRecognizing) {
                        inputBarState.value = InputState.Idle
                    }
                    
                    stopLocalVoiceInput(
                        triggerRecognizerStop = false,
                        expectedSessionId = sessionId,
                        clearSession = true,
                    )
                }
            }

            override fun onTimeout() {
                runOnUiThread {
                    if (!isVoiceSessionActive(sessionId)) return@runOnUiThread
                    stopVoiceInputAnimation()
                    // 恢复原来的文字
                    inputTextState.value = savedInputText
                    Toast.makeText(this@MainActivity, "语音识别超时", Toast.LENGTH_SHORT).show()
                    pendingSendAfterVoice = false
                    
                    // 同上，检查状态防止覆盖
                    val currentState = inputBarState.value
                    if (currentState is InputState.VoiceRecording || currentState is InputState.VoiceRecognizing) {
                        inputBarState.value = InputState.Idle
                    }
                    
                    stopLocalVoiceInput(
                        triggerRecognizerStop = false,
                        expectedSessionId = sessionId,
                        clearSession = true,
                    )
                }
            }
        })

        isListening = true
    }

    private fun stopLocalVoiceInput(
        triggerRecognizerStop: Boolean = true,
        expectedSessionId: Long? = null,
        clearSession: Boolean = false,
    ) {
        if (expectedSessionId != null && !isVoiceSessionActive(expectedSessionId)) return
        val recognizer = sherpaSpeechRecognizer
        stopVoiceInputAnimation()

        val currentText = inputTextState.value
        if (currentText.startsWith("正在语音输入")) {
            inputTextState.value = savedInputText
        }

        if (triggerRecognizerStop) {
            if (recognizer?.isListening() == true) {
                recognizer.stopListening()
            } else {
                recognizer?.cancel()
                pendingSendAfterVoice = false
            }
        } else {
            recognizer?.cancel()
        }
        isListening = false
        if (clearSession) {
            clearVoiceSession(expectedSessionId)
        }
    }

    private fun startMicAnimation() {
        // Compose UI handles its own animations via state
    }

    private fun stopMicAnimation() {
        // Compose UI handles its own animations via state
    }

    private fun ensureAudioPermission(onGranted: () -> Unit) {

        val granted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED

        if (granted) {

            pendingStartVoice = false

            onGranted()
        } else {

            pendingStartVoice = true

            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun showHistoryDialog() {
        if (buildHistoryDialogItems().isEmpty()) {
            Toast.makeText(this, getString(R.string.history_empty), Toast.LENGTH_SHORT).show()
            return
        }
        showHistoryDialogState.value = true
    }

    private fun buildHistoryDialogItems(): List<ConversationHistoryItemUi> {
        return conversations
            .filter { it.messages.isNotEmpty() }
            .sortedByDescending { it.updatedAt }
            .map { conversation ->
                val lastMessage = conversation.messages.lastOrNull()
                val previewRaw =
                    if (lastMessage?.isUser == false) {
                        parseStoredAiContent(lastMessage.content).second
                    } else {
                        lastMessage?.content.orEmpty()
                    }
                ConversationHistoryItemUi(
                    id = conversation.id,
                    title =
                        conversation.title.ifBlank {
                            getString(R.string.history_new_chat)
                        },
                    preview = previewRaw.replace('\n', ' ').trim(),
                )
            }
    }

    private fun attachAnimatedRing(target: View, strokeDp: Float) {
        val strokePx = strokeDp * target.resources.displayMetrics.density
        val blue = ContextCompat.getColor(this, R.color.m3t_primary)
        val cyan = ContextCompat.getColor(this, R.color.m3t_primary_container)
        val pink = ContextCompat.getColor(this, R.color.m3t_tertiary)
        val maxHalf = (maxOf(strokePx * 1.6f, strokePx)) / 2f

        val glowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    isDither = true
                    style = Paint.Style.STROKE
                    strokeWidth = strokePx * 1.8f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    alpha = 180
                }
        val corePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    isDither = true
                    style = Paint.Style.STROKE
                    strokeWidth = strokePx
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
        var angle = 0f
        var shader: SweepGradient? = null
        var shaderCx = Float.NaN
        var shaderCy = Float.NaN
        val shaderMatrix = Matrix()
        val drawable =
                object : Drawable() {
                    override fun draw(canvas: Canvas) {
                        val w = bounds.width().toFloat()
                        val h = bounds.height().toFloat()
                        if (w <= 0f || h <= 0f) return
                        val cx = bounds.left + w / 2f
                        val cy = bounds.top + h / 2f
                        if (shader == null || cx != shaderCx || cy != shaderCy) {
                            shader =
                                    SweepGradient(cx, cy, intArrayOf(Color.argb(255, Color.red(blue), Color.green(blue), Color.blue(blue)), Color.argb(240, Color.red(cyan), Color.green(cyan), Color.blue(cyan)), Color.argb(220, Color.red(blue), Color.green(blue), Color.blue(blue)), Color.argb(220, Color.red(pink), Color.green(pink), Color.blue(pink)), Color.argb(150, Color.red(pink), Color.green(pink), Color.blue(pink)), Color.argb(60, Color.red(pink), Color.green(pink), Color.blue(pink)), Color.argb(0, Color.red(blue), Color.green(blue), Color.blue(blue)), Color.argb(0, Color.red(blue), Color.green(blue), Color.blue(blue))), floatArrayOf(0f, 0.08f, 0.14f, 0.22f, 0.30f, 0.40f, 0.48f, 1f))
                            shaderCx = cx
                            shaderCy = cy
                        }
                        shader?.let {
                            shaderMatrix.setRotate(angle, cx, cy)
                            it.setLocalMatrix(shaderMatrix)
                            // glowPaint.shader = it
                            // corePaint.shader = it
                        }
                        // val oval = RectF(cx - r, cy - r, cx + r, cy + r)
                        // canvas.drawOval(oval, glowPaint)
                        // canvas.drawOval(oval, corePaint)
                    }
                    override fun setAlpha(alpha: Int) {
                        glowPaint.alpha = (alpha * 0.55f).toInt().coerceIn(0, 255)
                        corePaint.alpha = alpha
                    }
                    override fun setColorFilter(colorFilter: ColorFilter?) {
                        glowPaint.colorFilter = colorFilter
                        corePaint.colorFilter = colorFilter
                    }
                    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
                }
        target.post {
            drawable.setBounds(0, 0, target.width, target.height)
            target.overlay.add(drawable)
        }
                        var fastAnimator: ValueAnimator? = null
        var slowAnimator: ValueAnimator? = null
        var startFast: (() -> Unit)? = null
        var startSlow: (() -> Unit)? = null

        startSlow = {
            val start = angle
            slowAnimator = ValueAnimator.ofFloat(0f, 1080f).apply {
                duration = 5200L * 3
                interpolator = LinearInterpolator()
                addUpdateListener {
                    if (!isDrawerMoving) {
                        angle = start + (it.animatedValue as Float)
                        drawable.invalidateSelf()
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (target.isAttachedToWindow) {
                            angle = start + 1080f
                            startFast?.invoke()
                        }
                    }
                })
                start()
            }
        }

        startFast = {
            val start = angle
            fastAnimator = ValueAnimator.ofFloat(0f, 720f).apply {
                duration = 360
                interpolator = LinearInterpolator()
                addUpdateListener {
                    if (!isDrawerMoving) {
                        angle = start + (it.animatedValue as Float)
                        drawable.invalidateSelf()
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (target.isAttachedToWindow) {
                            angle = start + 720f
                            startSlow?.invoke()
                        }
                    }
                })
                start()
            }
        }

        target.post { startFast?.invoke() }

        target.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}

                    override fun onViewDetachedFromWindow(v: View) {
                                                fastAnimator?.cancel()
                        slowAnimator?.cancel()
                        target.overlay.remove(drawable)
                    }
                }
        )
    }
    private fun attachAnimatedBorderRing(target: View, strokeDp: Float, cornerDp: Float) {
        val density = target.resources.displayMetrics.density
        val strokePx = strokeDp * density
        val cornerPx = cornerDp * density
        val overlayHostView = (target.parent as? View) ?: target
        val overlayHostGroup = target.parent as? ViewGroup
        val blue = ContextCompat.getColor(this, R.color.m3t_primary)
        val cyan = ContextCompat.getColor(this, R.color.m3t_primary_container)
        val pink = ContextCompat.getColor(this, R.color.m3t_tertiary)
        val maxHalf = (maxOf(strokePx * 1.6f, strokePx)) / 2f

        val glowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    isDither = true
                    style = Paint.Style.STROKE
                    strokeWidth = strokePx * 1.8f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    alpha = 180
                }
        val corePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    isDither = true
                    style = Paint.Style.STROKE
                    strokeWidth = strokePx
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
        var angle = 0f
        var shader: SweepGradient? = null
        var shaderCx = Float.NaN
        var shaderCy = Float.NaN
        val shaderMatrix = Matrix()
        val drawable =
                object : Drawable() {
                    override fun draw(canvas: Canvas) {
                        val w = bounds.width().toFloat()
                        val h = bounds.height().toFloat()
                        if (w <= 0f || h <= 0f) return
                        val cx = bounds.left + w / 2f
                        val cy = bounds.top + h / 2f
                        if (shader == null || cx != shaderCx || cy != shaderCy) {
                            shader =
                                    SweepGradient(cx, cy, intArrayOf(Color.argb(255, Color.red(blue), Color.green(blue), Color.blue(blue)), Color.argb(240, Color.red(cyan), Color.green(cyan), Color.blue(cyan)), Color.argb(220, Color.red(blue), Color.green(blue), Color.blue(blue)), Color.argb(220, Color.red(pink), Color.green(pink), Color.blue(pink)), Color.argb(150, Color.red(pink), Color.green(pink), Color.blue(pink)), Color.argb(60, Color.red(pink), Color.green(pink), Color.blue(pink)), Color.argb(0, Color.red(blue), Color.green(blue), Color.blue(blue)), Color.argb(0, Color.red(blue), Color.green(blue), Color.blue(blue))), floatArrayOf(0f, 0.08f, 0.14f, 0.22f, 0.30f, 0.40f, 0.48f, 1f))
                            shaderCx = cx
                            shaderCy = cy
                        }
                        shader?.let {
                            shaderMatrix.setRotate(angle, cx, cy)
                            it.setLocalMatrix(shaderMatrix)
                            // glowPaint.shader = it
                            // corePaint.shader = it
                        }
                        // val oval = RectF(cx - r, cy - r, cx + r, cy + r)
                        // canvas.drawOval(oval, glowPaint)
                        // canvas.drawOval(oval, corePaint)
                    }
                    override fun setAlpha(alpha: Int) {
                        glowPaint.alpha = (alpha * 0.55f).toInt().coerceIn(0, 255)
                        corePaint.alpha = alpha
                    }
                    override fun setColorFilter(colorFilter: ColorFilter?) {
                        glowPaint.colorFilter = colorFilter
                        corePaint.colorFilter = colorFilter
                    }
                    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
                }

        fun updateBounds() {
            if (target.width <= 0 || target.height <= 0) return
            if (overlayHostGroup != null) {
                val rect = Rect()
                target.getDrawingRect(rect)
                overlayHostGroup.offsetDescendantRectToMyCoords(target, rect)
                drawable.setBounds(rect)
            } else {
                val locTarget = IntArray(2)
                val locHost = IntArray(2)
                target.getLocationInWindow(locTarget)
                overlayHostView.getLocationInWindow(locHost)
                val l = locTarget[0] - locHost[0]
                val t = locTarget[1] - locHost[1]
                drawable.setBounds(l, t, l + target.width, t + target.height)
            }
        }

        target.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateBounds()
            drawable.invalidateSelf()
        }
        overlayHostView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateBounds()
            drawable.invalidateSelf()
        }

        target.post {
            updateBounds()
            overlayHostView.overlay.add(drawable)
        }

                        var fastAnimator: ValueAnimator? = null
        var slowAnimator: ValueAnimator? = null
        var startFast: (() -> Unit)? = null
        var startSlow: (() -> Unit)? = null

        startSlow = {
            val start = angle
            slowAnimator = ValueAnimator.ofFloat(0f, 1080f).apply {
                duration = 5200L * 3
                interpolator = LinearInterpolator()
                addUpdateListener {
                    if (!isDrawerMoving) {
                        angle = start + (it.animatedValue as Float)
                        drawable.invalidateSelf()
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (target.isAttachedToWindow) {
                            angle = start + 1080f
                            startFast?.invoke()
                        }
                    }
                })
                start()
            }
        }

        startFast = {
            val start = angle
            fastAnimator = ValueAnimator.ofFloat(0f, 720f).apply {
                duration = 360
                interpolator = LinearInterpolator()
                addUpdateListener {
                    if (!isDrawerMoving) {
                        angle = start + (it.animatedValue as Float)
                        drawable.invalidateSelf()
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (target.isAttachedToWindow) {
                            angle = start + 720f
                            startSlow?.invoke()
                        }
                    }
                })
                start()
            }
        }

        target.post { startFast?.invoke() }

        target.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}

                    override fun onViewDetachedFromWindow(v: View) {
                                                fastAnimator?.cancel()
                        slowAnimator?.cancel()
                        overlayHostView.overlay.remove(drawable)
                    }
                }
        )
    }
    override fun onStop() {

        super.onStop()

        persistConversations()

        stopLocalVoiceInput(clearSession = true)
    }

    override fun onDestroy() {

        super.onDestroy()
        
        // 清除消息同步监听器，防止内存泄漏
        FloatingChatService.setMessageSyncListener(null)
        unregisterAutomationLogReceiverIfNeeded()
        unregisterQwenDownloadReceiverIfNeeded()

        stopLocalVoiceInput(clearSession = true)
        clearAutomationTerminatePending()

        sherpaSpeechRecognizer?.shutdown()

        sherpaSpeechRecognizer = null
    }

    // ============================================
    // Aries附件上传功能
    // ============================================

    /**
     * 设置Aries附件选择器 - 简化版，逻辑移到 ViewModel
     */
    private fun setupAriesAttachment() {
        ariesImagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { 
                lifecycleScope.launch {
                    val filePath = it.toString()
                    chatViewModel.handleAttachment(filePath)
                }
            }
        }
        
        ariesPdfPickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { 
                lifecycleScope.launch {
                    val filePath = it.toString()
                    chatViewModel.handleAttachment(filePath)
                }
            }
        }
        
        ariesFilePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { 
                lifecycleScope.launch {
                    val filePath = it.toString()
                    chatViewModel.handleAttachment(filePath)
                }
            }
        }
        
        // 相机拍照 Launcher
        ariesCameraLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                tempCameraUri?.let { uri ->
                    lifecycleScope.launch {
                        chatViewModel.handleTakenPhoto(uri)
                        tempCameraUri = null
                    }
                }
            } else {
                tempCameraUri = null
            }
        }
    }
    
    /**
     * 启动相机拍照（带权限检查）
     */
    private fun launchCamera() {
        // 检查相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            // 请求相机权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            return
        }
        
        // 创建临时文件URI
        try {
            val authority = "${applicationContext.packageName}.fileprovider"
            val tmpFile = File.createTempFile("camera_", ".jpg", cacheDir).apply {
                createNewFile()
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, tmpFile)
            tempCameraUri = uri
            ariesCameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "启动相机失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
