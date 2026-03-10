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
import android.content.res.ColorStateList
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
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.EditText
import com.ai.phoneagent.helper.AutomationMessageParser
import com.ai.phoneagent.helper.AutomationTimelineEntry
import com.ai.phoneagent.helper.AutomationTimelineFormatter
import com.ai.phoneagent.helper.StreamRenderHelper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.widget.ActionMenuView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ai.phoneagent.databinding.ActivityMainBinding
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ChatRequestMessage
import com.ai.phoneagent.net.LocalMnnInferenceEngine
import com.ai.phoneagent.net.ModelScopeModelDownloader
import com.ai.phoneagent.updates.UpdateStartupCoordinator
import com.ai.phoneagent.system.startActivityWithMaterialForwardTransition
import kotlinx.coroutines.Dispatchers
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.ai.phoneagent.core.automation.ActivityAutomationInstructionGateway
import com.ai.phoneagent.core.automation.AutomationLogBridge
import com.ai.phoneagent.core.prompt.MainChatPromptRepository
import com.ai.phoneagent.ui.inputbar.InputState
import com.ai.phoneagent.ui.inputbar.InputBar
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.livedata.observeAsState
import com.ai.phoneagent.data.AttachmentInfo
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.ui.drawer.ConversationDrawer
import com.ai.phoneagent.ui.drawer.DrawerConversationUiItem
import com.ai.phoneagent.ui.history.ConversationHistoryDialog
import com.ai.phoneagent.ui.history.ConversationHistoryItemUi
import com.ai.phoneagent.ui.messages.ConversationTranscript
import com.ai.phoneagent.ui.messages.TranscriptAutomationUi
import com.ai.phoneagent.ui.messages.TranscriptMessageUi
import com.ai.phoneagent.viewmodel.ChatViewModel
import com.ai.phoneagent.ui.topbar.MainTopBar
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    
    // ViewModel for managing chat and attachment state
    private val chatViewModel: ChatViewModel by viewModels()

    companion object {
        const val EXTRA_SCROLL_TO_BOTTOM = "extra_scroll_to_bottom"
        const val EXTRA_SHOW_AUTOMATION_STOP = "extra_show_automation_stop"
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

    private data class AutomationMessageRef(
            val conversationId: Long,
            val messageIndex: Int,
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var onboardingOverlay: MainOnboardingOverlay

    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }

    private val conversations = mutableListOf<Conversation>()

    private var activeConversation: Conversation? = null

    private var sherpaSpeechRecognizer: SherpaSpeechRecognizer? = null

    private var isListening = false

    private var pendingStartVoice = false

    private var voicePrefix: String = ""

    private var micAnimator: ObjectAnimator? = null

    private var thinkingView: View? = null
    private var thinkingTextView: TextView? = null

    // 防止并发请求导致重试时更容易出现空回复/失败提示
    private var isRequestInFlight: Boolean = false
    
    // 用于停止生成的标志
    private var shouldStopGeneration: Boolean = false

    private var voiceInputAnimJob: Job? = null
    private var savedInputText: String = ""

    private var pendingSendAfterVoice: Boolean = false
    private var voiceSessionSeed: Long = 0L
    @Volatile private var activeVoiceSessionId: Long = 0L

    // 滑动手势相关
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeTracking = false
    private var originalContentTopPadding = 0
    
    // 小窗模式相关
    private var isAnimatingToMiniWindow = false
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1235
    private val CAMERA_PERMISSION_REQUEST_CODE = 1236
    private var pendingEnterMiniWindowAfterNotifPerm: Boolean = false
    private var pendingAutomationLogUiRefresh: Boolean = false
    private var automationLogReceiverRegistered: Boolean = false
    private var activeAutomationPanelConversationId: Long = -1L
    private var activeAutomationPanelMessageIndex: Int = -1
    private var activeAutomationPanelLogContainer: LinearLayout? = null
    private var activeAutomationPanelStatusView: TextView? = null
    private var activeAutomationPanelConfirmButton: View? = null
    private var activeAutomationPanelConfirmTextView: TextView? = null
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

    private val permGuideShownPref = "perm_guide_shown"

    private val inputTextState = mutableStateOf("")
    private val inputBarState = mutableStateOf<InputState>(InputState.Idle)
    private val voiceAmplitudeState = mutableStateOf(0f)
    private val agentModeEnabledState = mutableStateOf(false)
    private val statusTextState = mutableStateOf("")
    private val statusVisibleState = mutableStateOf(false)
    private val drawerSearchQueryState = mutableStateOf("")
    private val drawerConversationItemsState = mutableStateOf<List<DrawerConversationUiItem>>(emptyList())
    private val drawerEmptyMessageState = mutableStateOf("")
    private val transcriptItemsState = mutableStateOf<List<TranscriptMessageUi>>(emptyList())
    private val streamingTranscriptItemState = mutableStateOf<TranscriptMessageUi?>(null)
    private val streamingTranscriptConversationIdState = mutableStateOf<Long?>(null)

    // Aries附件上传相关 - 简化为只保留 ActivityResultLauncher
    private lateinit var ariesImagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var ariesPdfPickerLauncher: ActivityResultLauncher<String>
    private lateinit var ariesFilePickerLauncher: ActivityResultLauncher<String>
    private lateinit var ariesCameraLauncher: ActivityResultLauncher<Uri>
    private var tempCameraUri: Uri? = null
    
    // 附件预览状态（由 ViewModel 管理，UI 层仅负责显示）
    private var attachmentPreviewView: View? = null
    private val attachmentThumbnailCache = LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(64)

    @Volatile private var suppressApiInputWatcher: Boolean = false
    @Volatile private var suppressModelSwitchWatcher: Boolean = false
    @Volatile private var apiNeedsRecheckToastShown: Boolean = false
    @Volatile private var qwenDownloadInFlight: Boolean = false
    @Volatile private var localModelReady: Boolean = false
    private lateinit var apiInput: EditText
    private lateinit var apiStatus: TextView
    private lateinit var apiThirdPartySwitch: MaterialSwitch
    private lateinit var localModelSwitch: MaterialSwitch
    private lateinit var localModelSwitchRow: View
    private lateinit var apiRemoteConfigContainer: View
    private lateinit var apiThirdPartyContainer: View
    private lateinit var apiBaseUrlInput: EditText
    private lateinit var apiModelInput: EditText
    private var qwenDownloadButton: MaterialButton? = null
    private lateinit var drawerPanel: View
    private val pendingQwenDownloadIds = linkedSetOf<Long>()
    private var qwenDownloadReceiverRegistered = false

    private fun persistConversations() {
        try {
            val json = com.google.gson.Gson().toJson(conversations)
            prefs.edit()
                    .putString(conversationsKey, json)
                    .putLong(activeConversationIdKey, activeConversation?.id ?: -1L)
                    .apply()
            if (::drawerPanel.isInitialized) {
                refreshDrawerConversationItems()
            }
        } catch (_: Exception) {
        }
    }

    private fun tryRestoreConversations(): Boolean {
        val json = prefs.getString(conversationsKey, null) ?: return false
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<Conversation>>() {}.type
            val list: List<Conversation> = com.google.gson.Gson().fromJson(json, type) ?: emptyList()
            conversations.clear()
            conversations.addAll(list.toMutableList())

            val activeId = prefs.getLong(activeConversationIdKey, -1L)
            activeConversation = conversations.firstOrNull { it.id == activeId } ?: conversations.firstOrNull()

            binding.messagesContainer.removeAllViews()
            activeConversation?.let { renderConversation(it) }
            if (::drawerPanel.isInitialized) {
                refreshDrawerConversationItems()
            }
            true
        } catch (_: Exception) {
            false
        }
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
    
    /**
     * TODO: 显示附件预览（由 ViewModel 状态驱动）
     */
    private fun showAttachmentPreview(attachment: Any) {
        // TODO: 实现附件预览
        // 移除旧的预览视图
        // hideAttachmentPreview()
        
        // val previewView = layoutInflater.inflate(
        //     R.layout.aries_selected_file_preview,
        //     binding.messagesContainer,
        //     false
        // )
        
        // previewView.findViewById<ImageView>(R.id.ariesSelectedFileIcon).apply {
        //     val iconRes = when (attachment.attachmentType) {
        //         AriesAttachmentType.IMAGE -> R.drawable.ic_aries_image
        //         AriesAttachmentType.PDF -> R.drawable.ic_aries_pdf
        //         AriesAttachmentType.DOCUMENT -> R.drawable.ic_aries_document
        //         else -> R.drawable.ic_aries_file
        //     }
        //     setImageResource(iconRes)
        // }
        
        // previewView.findViewById<TextView>(R.id.ariesSelectedFileName).text = attachment.fileName
        // previewView.findViewById<TextView>(R.id.ariesSelectedFileSize).text = 
        //     formatFileSize(attachment.fileSize)
        
        // previewView.findViewById<ImageButton>(R.id.ariesBtnRemoveFile).setOnClickListener {
        //     chatViewModel.clearAttachment()
        // }
        
        // previewView.visibility = View.VISIBLE
        // attachmentPreviewView = previewView
        
        // binding.messagesContainer.addView(previewView, 0)
        
        // binding.scrollArea.post {
        //     binding.scrollArea.smoothScrollTo(0, 0)
        // }
    }
    
    /**
     * 隐藏附件预览
     */
    private fun hideAttachmentPreview() {
        attachmentPreviewView?.let { view ->
            view.animate()
                .alpha(0f)
                .translationY(-20f)
                .setDuration(200)
                .withEndAction {
                    binding.messagesContainer.removeView(view)
                    attachmentPreviewView = null
                }
                .start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        onboardingOverlay = MainOnboardingOverlay(this)

        // 设置附件观察者（使用 ViewModel）
        setupAttachmentObservers()
        
        // 设置附件选择器
        setupAriesAttachment()

        setupEdgeToEdge()

        checkUserAgreement()

        setupMainChrome()

        setupComposeDrawer()
        restorePendingQwenDownloadIds()
        registerQwenDownloadReceiverIfNeeded()
        reconcilePendingQwenDownloads()

        setupMessageTranscript()
        setupInputBar()
        registerAutomationLogReceiverIfNeeded()

        restoreApiKey()

        setupKeyboardListener()

        elevateAiBar()

        if (!tryRestoreConversations()) {
            startNewChat(clearUi = true)
        }

        initSherpaModel()

        silentCheckUpdatesOnLaunch()
        refreshMainChatPromptOnLaunch()
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
        originalContentTopPadding = binding.contentRoot.paddingTop
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val useLightSystemBarIcons = resources.getBoolean(R.bool.m3t_light_system_bars)
        WindowCompat.getInsetsController(window, window.decorView)?.let {
            it.isAppearanceLightStatusBars = useLightSystemBarIcons
            it.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }
        binding.drawerLayout.fitsSystemWindows = false
        binding.navigationView.fitsSystemWindows = false
        binding.contentRoot.fitsSystemWindows = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.contentRoot.setPadding(
                binding.contentRoot.paddingLeft,
                originalContentTopPadding + sys.top,
                binding.contentRoot.paddingRight,
                maxOf(sys.bottom, ime.bottom),
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.drawerLayout)
    }

    private fun setupMainChrome() {
        binding.topBarCompose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        binding.topBarCompose.setContent {
            AriesMaterialTheme {
                MainTopBar(
                    statusText = statusTextState.value,
                    statusVisible = statusVisibleState.value,
                    onToggleStatus = {
                        statusVisibleState.value = !statusVisibleState.value
                    },
                    onOpenDrawer = {
                        if (!onboardingOverlay.isShowing()) {
                            vibrateLight()
                            hideKeyboard()
                            binding.drawerLayout.openDrawer(GravityCompat.START)
                        }
                    },
                    onNewChat = {
                        vibrateLight()
                        startNewChat(clearUi = true)
                    },
                    onOpenFloatingWindow = {
                        vibrateLight()
                        enterMiniWindowMode()
                    },
                )
            }
        }
    }

    private fun setupMessageTranscript() {
        binding.messagesCompose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        binding.messagesCompose.setContent {
            AriesMaterialTheme {
                ConversationTranscript(
                    items =
                        buildList {
                            addAll(transcriptItemsState.value)
                            if (streamingTranscriptConversationIdState.value == activeConversation?.id) {
                                streamingTranscriptItemState.value?.let { add(it) }
                            }
                        },
                    onCopyMessage = { item -> copyTranscriptMessage(item.copyText) },
                    onRetryMessage = { item ->
                        val retryText = item.retryText.orEmpty()
                        if (retryText.isBlank()) {
                            Toast.makeText(this@MainActivity, "未找到可重试的用户问题", Toast.LENGTH_SHORT).show()
                        } else {
                            retryMessage(retryText)
                        }
                    },
                    onAutomationAction = { item -> handleTranscriptAutomationAction(item) },
                )
            }
        }
        syncMessageTranscript()
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
    }

    private fun updateStreamingTranscript(
        retryText: String?,
        thinking: String,
        answer: String,
    ) {
        streamingTranscriptConversationIdState.value = activeConversation?.id
        streamingTranscriptItemState.value =
            TranscriptMessageUi(
                conversationId = activeConversation?.id ?: -1L,
                messageIndex = -1,
                id = "streaming-assistant",
                author = "Aries AI",
                body =
                    answer.ifBlank {
                        if (thinking.isBlank()) getString(R.string.message_streaming_placeholder) else ""
                    },
                thinking = thinking.ifBlank { null },
                isUser = false,
                attachments = emptyList(),
                isAutomation = false,
                copyText = answer,
                retryText = retryText,
                isStreaming = true,
            )
    }

    private fun clearStreamingTranscript() {
        streamingTranscriptConversationIdState.value = null
        streamingTranscriptItemState.value = null
    }

    private fun updateStreamingTranscriptFromBuffers(
        retryText: String?,
        reasoning: CharSequence,
        answer: CharSequence,
    ) {
        updateStreamingTranscript(
            retryText = retryText,
            thinking = reasoning.toString(),
            answer = answer.toString(),
        )
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
                isUser = true,
                attachments =
                    message.attachments
                        .orEmpty()
                        .map { attachment ->
                            attachment.fileName.ifBlank { File(attachment.filePath).name.ifBlank { attachment.filePath } }
                        },
                isAutomation = false,
                copyText = "",
                retryText = null,
            )
        }

        val messageRef = AutomationMessageRef(conversationId = conversationId, messageIndex = index)
        val (contentWithoutLogMarkers, embeddedAutomationLogs) = extractAutomationLogMarkers(message.content)
        val (contentWithoutConfirmedMarker, hasConfirmedMarker) =
            extractAutomationConfirmedMarker(contentWithoutLogMarkers)
        val (contentWithoutConfirmMarker, confirmInstruction) =
            extractAutomationConfirmInstruction(contentWithoutConfirmedMarker)
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
            author = message.author.ifBlank { "Aries AI" },
            body = displayBody,
            thinking = thinking,
            isUser = false,
            attachments = emptyList(),
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
    ): TranscriptAutomationUi? {
        if (command.isNullOrBlank()) return null

        val hasTerminalLog = logs.any { isAutomationTerminalLog(it) }
        val isTerminatePending = isAutomationTerminatePending(messageRef)
        val status =
            when {
                isTerminatePending -> getString(R.string.automation_scene_stop_requested)
                hasConfirm -> getString(R.string.automation_scene_need_confirm)
                hasTerminalLog -> getString(R.string.automation_scene_finished)
                logs.isNotEmpty() -> getString(R.string.automation_scene_running)
                hasConfirmed -> getString(R.string.automation_scene_confirmed)
                else -> getString(R.string.automation_scene_not_ready)
            }

        val countdownSeconds = automationCountdownSeconds[messageRef]
        val actionLabel: String?
        val actionEnabled: Boolean
        val isDestructive: Boolean
        val confirmInstruction: String?

        when {
            hasConfirmed && hasTerminalLog -> {
                actionLabel = getString(R.string.automation_confirmed)
                actionEnabled = false
                isDestructive = false
                confirmInstruction = null
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
                        getString(R.string.automation_confirm)
                    }
                actionEnabled = true
                isDestructive = false
                confirmInstruction = command
            }
            else -> {
                actionLabel = null
                actionEnabled = false
                isDestructive = false
                confirmInstruction = null
            }
        }

        return TranscriptAutomationUi(
            command = command,
            status = status,
            logs = logs,
            actionLabel = actionLabel,
            actionEnabled = actionEnabled,
            isDestructive = isDestructive,
            confirmInstruction = confirmInstruction,
        )
    }

    private fun syncTranscriptForAutomationMessage(messageRef: AutomationMessageRef?) {
        val ref = messageRef ?: return
        if (activeConversation?.id == ref.conversationId) {
            syncMessageTranscript(activeConversation)
        }
    }

    private fun handleTranscriptAutomationAction(item: TranscriptMessageUi) {
        val automation = item.automation ?: return
        if (!item.isAutomation || !automation.actionEnabled) return

        val messageRef = AutomationMessageRef(item.conversationId, item.messageIndex)
        if (!automation.confirmInstruction.isNullOrBlank()) {
            clearAutomationAutoConfirm(messageRef)
            val readyState = resolveAutomationReadyState()
            if (!readyState.ready) {
                Toast.makeText(
                    this,
                    resolveAutomationNotReadyToast(readyState.reason),
                    Toast.LENGTH_LONG,
                ).show()
                return
            }

            val dispatchResult =
                ActivityAutomationInstructionGateway.dispatchFromAdvancedAi(
                    context = this,
                    instruction = automation.confirmInstruction,
                )
            if (dispatchResult.success) {
                markAutomationCommandConfirmed(automation.confirmInstruction, messageRef)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.automation_dispatch_failed, dispatchResult.message),
                    Toast.LENGTH_SHORT,
                ).show()
            }
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

    private fun copyTranscriptMessage(text: String) {
        if (text.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("AI Reply", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制内容", Toast.LENGTH_SHORT).show()
    }

    private fun setupComposeDrawer() {
        drawerPanel = findViewById(R.id.drawerPanel)
        val drawerComposeView = drawerPanel.findViewById<ComposeView>(R.id.drawerComposeView)
        apiInput = drawerPanel.findViewById(R.id.apiInput)
        apiStatus = drawerPanel.findViewById(R.id.apiStatus)
        apiRemoteConfigContainer = drawerPanel.findViewById(R.id.apiRemoteConfigContainer)
        apiThirdPartySwitch = drawerPanel.findViewById(R.id.swUseThirdPartyApi)
        localModelSwitch = drawerPanel.findViewById(R.id.swUseLocalModel)
        localModelSwitchRow = drawerPanel.findViewById(R.id.localModelSwitchRow)
        apiThirdPartyContainer = drawerPanel.findViewById(R.id.apiThirdPartyContainer)
        apiBaseUrlInput = drawerPanel.findViewById(R.id.apiBaseUrlInput)
        apiModelInput = drawerPanel.findViewById(R.id.apiModelInput)
        qwenDownloadButton = drawerPanel.findViewById(R.id.btnDownloadQwenModel)
        localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(this)

        drawerComposeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        drawerComposeView.setContent {
            AriesMaterialTheme {
                ConversationDrawer(
                    searchQuery = drawerSearchQueryState.value,
                    items = drawerConversationItemsState.value,
                    emptyMessage = drawerEmptyMessageState.value,
                    onSearchQueryChange = { query ->
                        drawerSearchQueryState.value = query
                        refreshDrawerConversationItems()
                    },
                    onConversationClick = { conversationId ->
                        val target = conversations.firstOrNull { it.id == conversationId }
                        if (target != null) {
                            activeConversation = target
                            renderConversation(target)
                            persistConversations()
                            binding.drawerLayout.closeDrawer(GravityCompat.START)
                        }
                    },
                    onSettingsClick = {
                        vibrateLight()
                        navigateFromDrawer {
                            startActivityWithMaterialForwardTransition(
                                Intent(this@MainActivity, DrawerSettingsActivity::class.java),
                            )
                        }
                    },
                )
            }
        }

        binding.drawerLayout.setScrimColor(ContextCompat.getColor(this, R.color.m3t_drawer_scrim))
        binding.drawerLayout.setStatusBarBackgroundColor(Color.TRANSPARENT)
        binding.drawerLayout.setStatusBarBackground(null)
        binding.drawerLayout.addDrawerListener(
            object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    isDrawerMoving = slideOffset > 0f && slideOffset < 1f
                }

                override fun onDrawerClosed(drawerView: View) {
                    isDrawerMoving = false
                    runPendingDrawerNavigationAction()
                }

                override fun onDrawerOpened(drawerView: View) {
                    isDrawerMoving = false
                    hideKeyboard()
                }
            },
        )

        restoreApiKey()
        refreshDrawerConversationItems()
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

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            if (onboardingOverlay.isShowing()) return@setNavigationOnClickListener
            vibrateLight()
            hideKeyboard()
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // 统一优化工具栏按钮的点击视觉：去掉默认灰色背景阴影，改为缩放缩放+透明度脉冲
        binding.topAppBar.post {
            for (i in 0 until binding.topAppBar.childCount) {
                val child = binding.topAppBar.getChildAt(i)
                if (child is ActionMenuView) {
                    for (j in 0 until child.childCount) {
                        val menuChild = child.getChildAt(j)
                        menuChild.background = null // 去除默认背景
                        menuChild.isClickable = true
                    }
                } else if (child is ImageButton) {
                    child.background = null // 去除默认背景
                }
            }
        }

        binding.topAppBar.setOnMenuItemClickListener { item ->
            vibrateLight()
            // 通用图标动画
            findViewById<View>(item.itemId)?.let { view ->
                view.animate()
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .alpha(0.6f)
                    .setDuration(120)
                    .withEndAction {
                        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
                    }
                    .start()
            }

            when (item.itemId) {
                R.id.action_new_chat -> {
                    // 如果当前已经是空的新对话，则提示并跳过
                    if (activeConversation?.messages?.isEmpty() == true) {
                        Toast.makeText(this, "您已处于新对话中！", Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }

                    startNewChat(clearUi = true)
                    true
                }
                R.id.action_history -> {
                    showHistoryDialog()
                    true
                }
                R.id.action_floating_window -> {
                    enterMiniWindowMode()
                    true
                }
                else -> false
            }
        }

        offsetTopBarIcons()
    }

    private fun maybeShowPermissionBottomSheet() {
        onboardingOverlay.showPermissionOnlyIfNeeded()
    }

    private fun offsetTopBarIcons() {
        binding.topAppBar.post {
            val toolbar = binding.topAppBar
            val toolbarTitle = toolbar.title?.toString().orEmpty()

            var titleView: TextView? = null
            for (i in 0 until toolbar.childCount) {
                val child = toolbar.getChildAt(i)
                if (child is TextView && child.text?.toString() == toolbarTitle) {
                    titleView = child
                    break
                }
            }
            if (titleView == null) {
                for (i in 0 until toolbar.childCount) {
                    val child = toolbar.getChildAt(i)
                    if (child is TextView && child.text?.isNotBlank() == true) {
                        titleView = child
                        break
                    }
                }
            }

            val title = titleView ?: return@post
            val titleCenterY = title.top + title.height / 2f

            for (i in 0 until toolbar.childCount) {
                val child = toolbar.getChildAt(i)
                if (child is ActionMenuView) {
                    for (j in 0 until child.childCount) {
                        val menuChild = child.getChildAt(j)
                        val menuCenterY = menuChild.top + menuChild.height / 2f
                        menuChild.translationY = titleCenterY - menuCenterY
                    }
                } else if (child is ImageButton) {
                    val navCenterY = child.top + child.height / 2f
                    child.translationY = titleCenterY - navCenterY
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        handleReturnFromFloatingWindow()
        handleAutomationOverlayReturnIntent()

        restoreApiKey()
        reconcilePendingQwenDownloads()
        maybeShowPermissionBottomSheet()
        onboardingOverlay.onResume()

        // 设置消息同步监听器
        setupMessageSyncListener()

        if (pendingAutomationLogUiRefresh) {
            activeConversation?.let { renderConversation(it) }
            pendingAutomationLogUiRefresh = false
        }

        // 防止返回应用后气泡底部的复制/重试按钮被隐藏
        revealActionAreasForMessages()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
        }
        handleReturnFromFloatingWindow()
        handleAutomationOverlayReturnIntent()
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
                    binding.messagesContainer.removeAllViews()
                    clearAutomationPanelRuntimeRefs()
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
        binding.contentRoot.alpha = 0.92f
        binding.contentRoot.post {
            binding.contentRoot.animate().cancel()
            binding.contentRoot.animate()
                .alpha(1f)
                .setDuration(120)
                .start()
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

        binding.messagesContentHost.post {
            revealActionAreasForMessages()
            smoothScrollToBottom()
        }
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

        // 如果服务未运行，从 SharedPreferences 恢复消息
        if (floatingMessages == null || floatingMessages.isEmpty()) {
            try {
                val floatingPrefs = getSharedPreferences("floating_chat_prefs", MODE_PRIVATE)
                val json = floatingPrefs.getString("floating_messages", null)
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
            val floatingPrefs = getSharedPreferences("floating_chat_prefs", MODE_PRIVATE)
            floatingPrefs.edit().remove("floating_messages").apply()
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
        updateQwenDownloadButtonState()
        updateStatusText()
    }

    private fun updateLocalModelSwitchAvailabilityUi() {
        if (!::localModelSwitchRow.isInitialized || !::localModelSwitch.isInitialized) return

        if (localModelReady) {
            localModelSwitchRow.visibility = View.VISIBLE
            return
        }

        localModelSwitchRow.visibility = View.GONE

        val prefEnabled = prefs.getBoolean(apiUseLocalModelPref, false)
        val switchEnabled = localModelSwitch.isChecked
        if (prefEnabled || switchEnabled) {
            suppressModelSwitchWatcher = true
            localModelSwitch.isChecked = false
            suppressModelSwitchWatcher = false
            prefs.edit().putBoolean(apiUseLocalModelPref, false).apply()
            applyLocalModelUiState(false)
            onApiConfigPotentiallyChanged(showNeedsCheckMessage = false)
        } else {
            applyLocalModelUiState(false)
        }
    }

    private fun updateQwenDownloadButtonState() {
        val button = qwenDownloadButton ?: return
        val shouldShow = prefs.getBoolean(localModelDownloadButtonVisiblePref, false)
        if (!shouldShow) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        when {
            qwenDownloadInFlight -> {
                button.isEnabled = false
                button.text = getString(R.string.m3t_sidebar_qwen_download_preparing)
            }
            localModelReady -> {
                button.isEnabled = true
                button.text = getString(R.string.m3t_sidebar_qwen_download_ready)
            }
            else -> {
                button.isEnabled = true
                button.text = getString(R.string.m3t_sidebar_qwen_download)
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
        apiRemoteConfigContainer.visibility = if (enabled) View.GONE else View.VISIBLE
        updateStatusText()
    }

    private fun clearAutomationPanelRuntimeRefs() {
        activeAutomationPanelConversationId = -1L
        activeAutomationPanelMessageIndex = -1
        activeAutomationPanelLogContainer = null
        activeAutomationPanelStatusView = null
        activeAutomationPanelConfirmButton = null
        activeAutomationPanelConfirmTextView = null
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
        if (messageRef != null) return messageRef
        val cid = activeAutomationPanelConversationId
        val idx = activeAutomationPanelMessageIndex
        return if (cid > 0L && idx >= 0) {
            AutomationMessageRef(cid, idx)
        } else {
            null
        }
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

    private fun appendAutomationLogToPanelUi(logLine: String) {
        val normalized = normalizeAutomationLogLine(logLine)
        val logContainer = activeAutomationPanelLogContainer
        if (logContainer != null) {
            @Suppress("UNCHECKED_CAST")
            val timeline =
                (logContainer.tag as? MutableList<AutomationTimelineEntry>)
                    ?: mutableListOf<AutomationTimelineEntry>().also { logContainer.tag = it }
            AutomationTimelineFormatter.appendEntry(timeline, normalized)
            renderAutomationTimelineRows(logContainer, timeline)
        }
        if (isAutomationTerminalLog(normalized)) {
            val statusView = activeAutomationPanelStatusView
            val button = activeAutomationPanelConfirmButton
            val textView = activeAutomationPanelConfirmTextView
            val iconView = button?.findViewById<ImageView?>(R.id.iv_confirm_icon)
            configureAutomationFinishedButton(
                button = button,
                textView = textView,
                iconView = iconView,
                statusView = statusView
            )
        } else {
            val statusView = activeAutomationPanelStatusView
            val finishedText = getString(R.string.automation_scene_finished)
            if (statusView?.text?.toString() != finishedText) {
                statusView?.text = getString(R.string.automation_scene_running)
            }
        }
    }

    private fun appendAutomationLogAsAiMessage(rawLogLine: String) {
        val logLine = filterAutomationLogForHome(rawLogLine) ?: return
        if (logLine.isBlank()) return

        if (appendAutomationLogToExistingPanel(logLine)) {
            return
        }

        val messageContent = "【自动化】$logLine"
        val c = requireActiveConversation()
        val last = c.messages.lastOrNull()
        if (last != null && !last.isUser && last.content == messageContent) {
            return
        }

        c.messages.add(
                UiMessage(
                        author = "Aries AI",
                        content = messageContent,
                        isUser = false,
                )
        )
        c.updatedAt = System.currentTimeMillis()

        val canRenderNow =
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                        activeConversation?.id == c.id
        if (canRenderNow) {
            syncMessageTranscript(c)
            smoothScrollToBottom()
        } else {
            pendingAutomationLogUiRefresh = true
        }
        syncMessageTranscript(c)
        persistConversations()
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
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
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
        val contentView = binding.contentRoot
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        
        // 目标小窗尺寸和位置
        val miniWindowWidth = 280 * density
        val miniWindowHeight = 360 * density

        val floatingPrefs = getSharedPreferences("floating_chat_prefs", MODE_PRIVATE)
        val fallbackX = ((displayMetrics.widthPixels - miniWindowWidth) / 2f).toInt()
        val fallbackY = ((displayMetrics.heightPixels - miniWindowHeight) / 2f).toInt()
        val savedX = floatingPrefs.getInt("window_x", Int.MIN_VALUE)
        val savedY = floatingPrefs.getInt("window_y", Int.MIN_VALUE)
        val targetX = (if (savedX != Int.MIN_VALUE) savedX else fallbackX).toFloat()
        val targetY = (if (savedY != Int.MIN_VALUE) savedY else fallbackY).toFloat()
        
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
            contentView.animate()
                .alpha(0.85f)
                .scaleX(0.985f)
                .scaleY(0.985f)
                .setDuration(140)
                .withEndAction {
                    moveTaskToBack(true)
                    contentView.alpha = 1f
                    contentView.scaleX = 1f
                    contentView.scaleY = 1f
                }
                .start()
        }.recoverCatching {
            moveTaskToBack(true)
        }
    }
    
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
                // 权限获取后自动进入小窗模式
                enterMiniWindowMode()
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能使用小窗模式", Toast.LENGTH_SHORT).show()
            }
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

    private fun setupDrawer() {

        val header = binding.navigationView.getHeaderView(0)

        apiInput = header.findViewById<EditText>(R.id.apiInput)
        apiStatus = header.findViewById<TextView>(R.id.apiStatus)
        apiRemoteConfigContainer = header.findViewById<View>(R.id.apiRemoteConfigContainer)
        apiThirdPartySwitch = header.findViewById<MaterialSwitch>(R.id.swUseThirdPartyApi)
        localModelSwitch = header.findViewById<MaterialSwitch>(R.id.swUseLocalModel)
        localModelSwitchRow = header.findViewById<View>(R.id.localModelSwitchRow)
        apiThirdPartyContainer = header.findViewById<View>(R.id.apiThirdPartyContainer)
        apiBaseUrlInput = header.findViewById<EditText>(R.id.apiBaseUrlInput)
        apiModelInput = header.findViewById<EditText>(R.id.apiModelInput)
        localModelReady = ModelScopeModelDownloader.isQwen35ModelReady(this)

        apiThirdPartySwitch.isChecked = prefs.getBoolean(apiUseThirdPartyPref, false)
        apiThirdPartyContainer.visibility =
                if (apiThirdPartySwitch.isChecked) View.VISIBLE else View.GONE
        val savedLocalModelEnabled = prefs.getBoolean(apiUseLocalModelPref, false)
        val initialLocalModelEnabled = savedLocalModelEnabled && localModelReady
        if (savedLocalModelEnabled && !localModelReady) {
            prefs.edit().putBoolean(apiUseLocalModelPref, false).apply()
        }
        localModelSwitch.isChecked = initialLocalModelEnabled
        applyLocalModelUiState(initialLocalModelEnabled)
        updateLocalModelSwitchAvailabilityUi()

        apiThirdPartySwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressModelSwitchWatcher) return@setOnCheckedChangeListener
            apiThirdPartyContainer.visibility = if (checked) View.VISIBLE else View.GONE
            prefs.edit().putBoolean(apiUseThirdPartyPref, checked).apply()
            apiNeedsRecheckToastShown = false
            onApiConfigPotentiallyChanged(showNeedsCheckMessage = checked)
        }
        localModelSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressModelSwitchWatcher) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(apiUseLocalModelPref, checked).apply()
            applyLocalModelUiState(checked)
            onApiConfigPotentiallyChanged(showNeedsCheckMessage = !checked)
            if (checked) {
                Toast.makeText(
                    this,
                    getString(R.string.m3t_sidebar_local_model_switch_to_qwen),
                    Toast.LENGTH_SHORT
                ).show()
                if (!localModelReady && pendingQwenDownloadIds.isEmpty()) {
                    Toast.makeText(
                        this,
                        getString(R.string.m3t_sidebar_local_model_not_ready),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        apiBaseUrlInput.setText(prefs.getString(apiThirdPartyBaseUrlPref, AutoGlmClient.DEFAULT_BASE_URL))
        apiModelInput.setText(prefs.getString(apiThirdPartyModelPref, AutoGlmClient.DEFAULT_MODEL))

        val btnCheck = header.findViewById<android.widget.Button>(R.id.btnCheckApi)
        val btnPasteApiInput = header.findViewById<View>(R.id.btnPasteApiInput)
        val btnDownloadQwenModel = header.findViewById<MaterialButton>(R.id.btnDownloadQwenModel)
        qwenDownloadButton = btnDownloadQwenModel
        updateQwenDownloadButtonState()

        val btnGetApiKey = header.findViewById<View>(R.id.btnGetApiKey)
        btnGetApiKey?.setOnClickListener {
            runCatching {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://open.bigmodel.cn/usercenter/proj-mgmt/apikeys")
                )
                startActivity(intent)
            }
        }
        btnPasteApiInput?.setOnClickListener {
            vibrateLight()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val pasted =
                    clipboard?.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(this)
                            ?.toString()
                            ?.trim()
                            .orEmpty()
            if (pasted.isBlank()) {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            apiInput.tag = ""
            apiInput.requestFocus()
            apiInput.setText(pasted)
            apiInput.setSelection(apiInput.text?.length ?: 0)
            Toast.makeText(this, "已粘贴 API Key", Toast.LENGTH_SHORT).show()
        }

        btnDownloadQwenModel?.setOnClickListener {
            if (qwenDownloadInFlight) return@setOnClickListener
            qwenDownloadInFlight = true
            updateQwenDownloadButtonState()

            lifecycleScope.launch {
                val result = ModelScopeModelDownloader.enqueueQwen35Downloads(this@MainActivity)

                qwenDownloadInFlight = false
                updateQwenDownloadButtonState()

                result.onSuccess { enqueueResult ->
                    if (enqueueResult.downloadIds.isNotEmpty()) {
                        pendingQwenDownloadIds.addAll(enqueueResult.downloadIds)
                        persistPendingQwenDownloadIds()
                    }
                    refreshLocalModelReadyState()

                    val message =
                        when {
                            enqueueResult.enqueuedCount > 0 ->
                                getString(
                                    R.string.m3t_sidebar_qwen_download_summary_format,
                                    enqueueResult.enqueuedCount,
                                    enqueueResult.skippedCount,
                                    enqueueResult.targetDir
                                )
                            enqueueResult.skippedCount > 0 ->
                                getString(R.string.m3t_sidebar_qwen_download_cached)
                            else ->
                                getString(R.string.m3t_sidebar_qwen_download_enqueued)
                        }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    updateQwenDownloadButtonState()
                    val reason =
                        err.message?.trim().orEmpty().ifBlank {
                            getString(R.string.update_download_failed_unknown)
                        }
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.m3t_sidebar_qwen_download_failed_format, reason),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        binding.drawerLayout.setScrimColor(Color.TRANSPARENT)
        binding.drawerLayout.setStatusBarBackgroundColor(Color.TRANSPARENT)
        binding.drawerLayout.setStatusBarBackground(null)
        binding.navigationView.setBackgroundColor(
            ContextCompat.getColor(this, R.color.m3t_drawer_background)
        )
        binding.navigationView.itemTextColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.m3t_on_surface))
        val isNightMode =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
        
        binding.drawerLayout.addDrawerListener(
                object : DrawerLayout.SimpleDrawerListener() {
                    private var isHardwareLayerSet = false
                    private val interpolator = android.view.animation.DecelerateInterpolator(1.2f)

                    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                        isDrawerMoving = slideOffset > 0 && slideOffset < 1
                        
                        // 使用减速插值器优化视觉曲线，使动画在开始时更轻快，结束时更柔和
                        val t = interpolator.getInterpolation(slideOffset)
                        
                        if (!isHardwareLayerSet && isDrawerMoving) {
                            binding.contentRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            drawerView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            isHardwareLayerSet = true
                        }

                        val w = drawerView.width.toFloat()
                        val density = resources.displayMetrics.density
                        
                        // 1. 缩放效果：主界面平滑缩小，增加空间感 (使用插值后的 t)
                        val scale = 1f - (t * 0.12f)
                        binding.contentRoot.scaleX = scale
                        binding.contentRoot.scaleY = scale
                        
                        // 2. 透明度：主界面轻微变暗
                        binding.contentRoot.alpha = if (isNightMode) 1f else 1f - (t * 0.3f)
                        
                        // 3. 位移补偿：侧边栏推开主界面的同时，保持平滑平移
                        binding.contentRoot.translationX = w * t
                        
                        // 4. 圆角平滑：
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            val cornerRadius = 32f * density * t
                            binding.contentRoot.outlineProvider = object : android.view.ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: android.graphics.Outline) {
                                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                                }
                            }
                            binding.contentRoot.clipToOutline = t > 0
                        }
                        
                        // 5. 侧边栏本身也平滑淡入
                        drawerView.alpha = if (isNightMode) 1f else 0.5f + (0.5f * t)
                    }

                    override fun onDrawerClosed(drawerView: View) {
                        isDrawerMoving = false
                        binding.contentRoot.translationX = 0f
                        binding.contentRoot.scaleX = 1f
                        binding.contentRoot.scaleY = 1f
                        binding.contentRoot.alpha = 1f
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            binding.contentRoot.clipToOutline = false
                        }
                        // 停止滑动后释放硬件层，节省内存
                        binding.contentRoot.setLayerType(View.LAYER_TYPE_NONE, null)
                        drawerView.setLayerType(View.LAYER_TYPE_NONE, null)
                        isHardwareLayerSet = false
                        runPendingDrawerNavigationAction()
                    }

                    override fun onDrawerOpened(drawerView: View) {
                        hideKeyboard()
                        // 停止滑动后释放硬件层
                        binding.contentRoot.setLayerType(View.LAYER_TYPE_NONE, null)
                        drawerView.setLayerType(View.LAYER_TYPE_NONE, null)
                        isHardwareLayerSet = false
                    }

                    override fun onDrawerStateChanged(newState: Int) {
                        // 当用户开始触摸或程序开始自动滚动时，确保键盘收起，避免布局抖动
                        if (newState == DrawerLayout.STATE_DRAGGING) {
                            hideKeyboard()
                        }
                    }
                }
        )

        apiInput.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}

                    override fun afterTextChanged(s: Editable?) {
                        if (suppressApiInputWatcher) return

                        val displayed = s?.toString().orEmpty()
                        val tagKey = (apiInput.tag as? String).orEmpty()
                        val savedKey = prefs.getString("api_key", "").orEmpty()

                        val isMaskedUnchanged =
                                displayed.contains("*") && displayed == maskKey(tagKey)

                        if (isMaskedUnchanged && tagKey.isNotBlank() && tagKey == savedKey) {
                            apiNeedsRecheckToastShown = false
                            return
                        }

                        if (displayed.isBlank()) {
                            onApiConfigChanged(clearApiValue = true)
                            return
                        }

                        onApiConfigPotentiallyChanged()
                    }
                }
        )

        val thirdPartyConfigWatcher =
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}

                    override fun afterTextChanged(s: Editable?) {
                        if (suppressApiInputWatcher || !apiThirdPartySwitch.isChecked) return

                        if (apiBaseUrlInput.isFocused) {
                            prefs.edit()
                                    .putString(apiThirdPartyBaseUrlPref, apiBaseUrlInput.text.toString().trim())
                                    .apply()
                        }
                        if (apiModelInput.isFocused) {
                            prefs.edit()
                                    .putString(apiThirdPartyModelPref, apiModelInput.text.toString().trim())
                                    .apply()
                        }

                        onApiConfigPotentiallyChanged()
                    }
                }
        apiBaseUrlInput.addTextChangedListener(thirdPartyConfigWatcher)
        apiModelInput.addTextChangedListener(thirdPartyConfigWatcher)

        btnCheck.setOnClickListener {
            vibrateLight()
            val key = resolveApiKeyFromInput()

            if (key.isBlank()) {

                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()

                return@setOnClickListener
            }

            prefs.edit().putString("api_key", key).apply()

            apiInput.tag = key
            suppressApiInputWatcher = true
            apiInput.setText(maskKey(key))
            apiInput.setSelection(apiInput.text?.length ?: 0)
            suppressApiInputWatcher = false

            val useThirdParty = apiThirdPartySwitch.isChecked
            val baseUrlSnapshot = resolveApiBaseUrl()
            val modelSnapshot = resolveApiModel()
            if (useThirdParty) {
                // 检查前先固化第三方配置，避免开关切换后读到不一致的临时状态
                prefs.edit()
                        .putString(apiThirdPartyBaseUrlPref, baseUrlSnapshot)
                        .putString(apiThirdPartyModelPref, modelSnapshot)
                        .apply()
            }

            apiNeedsRecheckToastShown = false

            startApiCheck(
                    key = key,
                    baseUrl = baseUrlSnapshot,
                    model = modelSnapshot,
                    useThirdParty = useThirdParty,
                    force = true,
            )
        }

        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_automation -> {
                    vibrateLight()
                    navigateFromDrawer {
                        val intent = Intent(this, AutomationActivityNew::class.java)
                        startActivityWithMaterialForwardTransition(intent)
                    }
                }
                R.id.nav_about -> {
                    vibrateLight()
                    navigateFromDrawer {
                        val intent = Intent(this, AboutActivity::class.java)
                        startActivityWithMaterialForwardTransition(intent)
                    }
                }
            }

            true
        }
    }

    private fun navigateFromDrawer(action: () -> Unit) {
        if (onboardingOverlay.isShowing()) return

        pendingDrawerNavigationAction = action
        hideKeyboard()

        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START, false)
        } else {
            runPendingDrawerNavigationAction()
        }
    }

    private fun runPendingDrawerNavigationAction() {
        val pendingAction = pendingDrawerNavigationAction ?: return
        pendingDrawerNavigationAction = null
        binding.drawerLayout.post { pendingAction.invoke() }
    }

    private fun restoreApiKey() {

        val saved = prefs.getString("api_key", "") ?: ""

        apiInput.tag = saved
        suppressApiInputWatcher = true
        suppressModelSwitchWatcher = true
        apiInput.setText(maskKey(saved))
        apiInput.setSelection(apiInput.text?.length ?: 0)
        val useThirdParty = prefs.getBoolean(apiUseThirdPartyPref, false)
        val useLocalModel = prefs.getBoolean(apiUseLocalModelPref, false)
        apiThirdPartySwitch.isChecked = useThirdParty
        localModelSwitch.isChecked = useLocalModel
        apiThirdPartyContainer.visibility =
                if (apiThirdPartySwitch.isChecked) View.VISIBLE else View.GONE
        apiBaseUrlInput.setText(
                prefs.getString(apiThirdPartyBaseUrlPref, AutoGlmClient.DEFAULT_BASE_URL)
        )
        apiModelInput.setText(
                prefs.getString(apiThirdPartyModelPref, AutoGlmClient.DEFAULT_MODEL)
        )
        applyLocalModelUiState(useLocalModel)
        suppressApiInputWatcher = false
        suppressModelSwitchWatcher = false

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
            apiStatus.text = if (ok) "API 可用" else "API 检查失败"
            updateStatusText()
            return
        }

        lastCheckedApiKey = saved
        remoteApiOk = null
        remoteApiChecking = false
        apiStatus.text = "未检查"
        updateStatusText()
    }

    private fun startApiCheck(
            key: String,
            baseUrl: String = AutoGlmClient.DEFAULT_BASE_URL,
            model: String = AutoGlmClient.DEFAULT_MODEL,
            useThirdParty: Boolean = apiThirdPartySwitch.isChecked,
            force: Boolean,
    ) {
        val k = key.trim()
        if (k.isBlank()) return

        if (!force) {
            if (remoteApiChecking) return
            if (lastCheckedApiKey == k && remoteApiOk != null) return
        }

        val header = binding.navigationView.getHeaderView(0)
        val apiStatus = header.findViewById<TextView>(R.id.apiStatus)

        remoteApiChecking = true
        remoteApiOk = null
        lastCheckedApiKey = k

        apiStatus.text = "检查中..."
        updateStatusText()

        val seq = ++apiCheckSeq
        val normalizedBaseUrl = baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        val baseUrlSecurityError = validateBaseUrlSecurity(normalizedBaseUrl)
        if (baseUrlSecurityError != null) {
            remoteApiChecking = false
            remoteApiOk = false
            apiStatus.text = "API 地址不安全"
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
            apiStatus.text = if (ok) "API 可用" else "API 检查失败"
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
            apiStatus.text = "未检查"
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
            apiStatus.text = "未检查"
            updateStatusText()
            return
        }

        val currentSig =
                apiConfigSignature(
                        apiKey = currentKey,
                        baseUrl = resolveApiBaseUrl(),
                        model = resolveApiModel(),
                        useThirdParty = apiThirdPartySwitch.isChecked,
                )
        val lastSig = prefs.getString(apiLastCheckSigPref, "").orEmpty()
        val hasLast = prefs.contains(apiLastCheckOkPref)
        if (hasLast && lastSig.isNotBlank() && lastSig == currentSig) {
            val ok = prefs.getBoolean(apiLastCheckOkPref, false)
            remoteApiOk = ok
            remoteApiChecking = false
            lastCheckedApiKey = currentKey
            apiStatus.text = if (ok) "API 可用" else "API 检查失败"
            updateStatusText()
            return
        }

        apiCheckSeq++
        remoteApiOk = null
        remoteApiChecking = false
        lastCheckedApiKey = ""
        apiStatus.text = "请检查API配置"
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
        return ::localModelSwitch.isInitialized && localModelSwitch.isChecked
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
            val storedThirdPartyBaseUrl =
                prefs.getString(apiThirdPartyBaseUrlPref, "").orEmpty()
            return normalizeBaseUrlInput(storedThirdPartyBaseUrl)
                ?: AutoGlmClient.DEFAULT_BASE_URL
        }
        if (!apiThirdPartySwitch.isChecked) return AutoGlmClient.DEFAULT_BASE_URL
        val rawUrl = apiBaseUrlInput.text?.toString().orEmpty()
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

    private fun resolveApiModel(): String {
        if (isLocalModelModeEnabled()) {
            return ModelScopeModelDownloader.QWEN35_MODEL_NAME
        }
        if (!apiThirdPartySwitch.isChecked) return AutoGlmClient.DEFAULT_MODEL
        return apiModelInput.text?.toString()?.trim().orEmpty().ifBlank { AutoGlmClient.DEFAULT_MODEL }
    }

    private fun apiConfigSignature(
            apiKey: String,
            baseUrl: String,
            model: String,
            useThirdParty: Boolean = apiThirdPartySwitch.isChecked,
    ): String {
        val normalizedBaseUrl = baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        val normalizedModel = model.ifBlank { AutoGlmClient.DEFAULT_MODEL }
        return "${if (useThirdParty) "1" else "0"}|${apiKey.trim()}|$normalizedBaseUrl|$normalizedModel"
    }

    private fun resolveApiKeyFromInput(): String {
        val displayed = apiInput.text?.toString().orEmpty()
        val tagKey = (apiInput.tag as? String).orEmpty().trim()
        val savedKey = prefs.getString("api_key", "").orEmpty().trim()

        val resolved =
                when {
                    tagKey.isNotBlank() && displayed == maskKey(tagKey) -> tagKey
                    savedKey.isNotBlank() && displayed == maskKey(savedKey) -> savedKey
                    displayed.contains("*") && savedKey.isNotBlank() -> savedKey
                    else -> displayed
                }
        return resolved.trim()
    }

    private fun updateStatusText() {
        val localModeEnabled = ::localModelSwitch.isInitialized && localModelSwitch.isChecked
        if (localModeEnabled) {
            val localText =
                when {
                    qwenDownloadInFlight || pendingQwenDownloadIds.isNotEmpty() ->
                        getString(R.string.m3t_sidebar_local_model_downloading)
                    localModelReady -> getString(R.string.m3t_sidebar_local_model_ready)
                    else -> getString(R.string.m3t_sidebar_local_model_not_ready)
                }
            binding.statusText.text = localText
            statusTextState.value = localText
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
        binding.statusText.text = text
        statusTextState.value = text
    }

    private fun maskKey(raw: String): String {
        if (raw.isBlank()) return ""
        return if (raw.length <= 6) raw else raw.substring(0, 6) + "*".repeat(raw.length - 6)
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

    private fun setupInputBar() {
        binding.inputBarCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isDarkMode = !resources.getBoolean(R.bool.m3t_light_system_bars)
                val inputColorScheme =
                    if (isDarkMode) {
                        darkColorScheme(
                            primary = colorResource(R.color.m3t_primary),
                            onPrimary = colorResource(R.color.m3t_on_primary),
                            surface = colorResource(R.color.m3t_surface),
                            surfaceVariant = colorResource(R.color.m3t_surface_container),
                            onSurface = colorResource(R.color.m3t_on_surface),
                            onSurfaceVariant = colorResource(R.color.m3t_on_surface_variant),
                            error = colorResource(R.color.m3t_error),
                        )
                    } else {
                        lightColorScheme(
                            primary = colorResource(R.color.m3t_primary),
                            onPrimary = colorResource(R.color.m3t_on_primary),
                            surface = colorResource(R.color.m3t_surface),
                            surfaceVariant = colorResource(R.color.m3t_surface_container),
                            onSurface = colorResource(R.color.m3t_on_surface),
                            onSurfaceVariant = colorResource(R.color.m3t_on_surface_variant),
                            error = colorResource(R.color.m3t_error),
                        )
                    }

                MaterialTheme(colorScheme = inputColorScheme) {
                    val text by remember { inputTextState }
                    val state by remember { inputBarState }
                    val amplitude by remember { voiceAmplitudeState }
                    val agentModeEnabled by remember { agentModeEnabledState }
                    
                    // 监听附件状态
                    val attachments by chatViewModel.attachments.collectAsState()
                    val attachmentSelectorVisible by chatViewModel.attachmentSelectorVisible.collectAsState()

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 附件预览列表
                            if (attachments.isNotEmpty()) {
                                com.ai.phoneagent.ui.components.AttachmentPreviewList(
                                    attachments = attachments,
                                    attachmentManager = chatViewModel.getAttachmentManager(),
                                    onInsertReference = { attachment ->
                                        val reference = chatViewModel.createAttachmentReference(attachment)
                                        inputTextState.value = inputTextState.value + "\n" + reference
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            
                            // 原始的 InputBar
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
                                            instruction = t
                                        )
                                    if (dispatchResult.success) {
                                        inputTextState.value = ""
                                        chatViewModel.clearAttachments()
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Agent 模式已激活，任务已转交自动化",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            this@MainActivity,
                                            dispatchResult.message,
                                            Toast.LENGTH_SHORT
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
                            // 点击加号时添加200ms震动反馈
                            vibrateMedium()
                            // 显示附件选择器
                            chatViewModel.toggleAttachmentSelector()
                        },
                        agentModeEnabled = agentModeEnabled,
                        onAgentToggle = { enabled ->
                            agentModeEnabledState.value = enabled
                            Toast.makeText(
                                this@MainActivity,
                                if (enabled) "Agent 模式已激活" else "Agent 模式未激活",
                                Toast.LENGTH_SHORT
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
                        }
                    )
                        }
                        
                        // 附件选择器面板（覆盖在底部）
                        com.ai.phoneagent.ui.components.AttachmentSelectorPanel(
                            visible = attachmentSelectorVisible,
                            attachmentManager = chatViewModel.getAttachmentManager(),
                            onDismiss = { chatViewModel.hideAttachmentSelector() }
                        )
                    }
                }
            }
        }

        binding.floatingInputLayer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncFloatingInputPadding()
        }
        binding.floatingInputLayer.post {
            syncFloatingInputPadding()
        }
    }

    private fun syncFloatingInputPadding() {
        val extraBottomPadding =
            binding.floatingInputLayer.height + resources.getDimensionPixelSize(R.dimen.m3t_spacing_md)
        binding.messagesContentHost.setPadding(
            binding.messagesContentHost.paddingLeft,
            binding.messagesContentHost.paddingTop,
            binding.messagesContentHost.paddingRight,
            extraBottomPadding,
        )
        if (shouldAutoFollowBottom(extraBottomPadding * 2)) {
            binding.scrollArea.post {
                binding.scrollArea.smoothScrollTo(0, binding.messagesContentHost.bottom)
            }
        }
    }

    private fun hasAssistantOutputInActiveConversation(): Boolean {
        return activeConversation?.messages?.any { !it.isUser && it.content.isNotBlank() } == true
    }

    private fun shouldAutoFollowBottom(thresholdPx: Int = 0): Boolean {
        if (!hasAssistantOutputInActiveConversation()) return false
        val distanceToBottom =
            binding.messagesContentHost.bottom - (binding.scrollArea.scrollY + binding.scrollArea.height)
        return distanceToBottom <= thresholdPx.coerceAtLeast(0)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.inputBarCompose.windowToken, 0)
        binding.inputBarCompose.clearFocus()
    }

    private fun elevateAiBar() = Unit

    private fun setupKeyboardListener() {

        val root = binding.drawerLayout
        val content = binding.contentRoot

        val initialLeft = content.paddingLeft
        val initialTop = content.paddingTop
        val initialRight = content.paddingRight
        val initialBottom = content.paddingBottom

        var lastImeVisible = false

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (ime.bottom > sys.bottom) ime.bottom else sys.bottom

            content.setPadding(
                    initialLeft,
                    initialTop + sys.top,
                    initialRight,
                    initialBottom + bottomInset
            )

            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible && !lastImeVisible && shouldAutoFollowBottom(resources.getDimensionPixelSize(R.dimen.m3t_spacing_xxl))) {
                binding.scrollArea.post {
                    binding.scrollArea.smoothScrollTo(0, binding.messagesContentHost.height)
                }
            }
            lastImeVisible = imeVisible

            insets
        }

        val nav = drawerPanel
        val navInitialLeft = nav.paddingLeft
        val navInitialTop = nav.paddingTop
        val navInitialRight = nav.paddingRight
        val navInitialBottom = nav.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(nav) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                    navInitialLeft,
                    navInitialTop + sys.top,
                    navInitialRight,
                    navInitialBottom + sys.bottom
            )
            insets
        }

        ViewCompat.requestApplyInsets(root)
        ViewCompat.requestApplyInsets(nav)
    }

    private fun startNewChat(clearUi: Boolean) {
        // 防止启动多个重复的空会话
        if (activeConversation != null && activeConversation!!.messages.isEmpty()) {
            Toast.makeText(this, "您已处于新对话中！", Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val c = Conversation(id = now, title = "", messages = mutableListOf(), updatedAt = now)
        conversations.add(0, c)
        activeConversation = c
        clearStreamingTranscript()
        syncMessageTranscript(c)
        
        if (clearUi) {
            // 逐步缩小收敛一气呵成向上收缩：不再只是平移，而是带有一种“消失”的速度感
            binding.messagesContentHost.animate()
                .translationY(-1000f) // 冲刺距离加大，一气呵成
                .scaleX(0.6f)         // 收缩更明显
                .scaleY(0.6f)
                .alpha(0f)            // 融入背景
                .setDuration(400)      // 稍微加快，更显果断
                .setInterpolator(AccelerateInterpolator(1.8f)) // 纯加速，无回弹
                .withEndAction {
                    binding.messagesContainer.removeAllViews()
                    clearAutomationPanelRuntimeRefs()
                    
                    // 状态瞬间回位
                    binding.messagesContentHost.translationY = 0f
                    binding.messagesContentHost.scaleX = 1f
                    binding.messagesContentHost.scaleY = 1f
                    
                    // 新对话界面原地极其自然地透出来
                    binding.messagesContentHost.animate()
                        .alpha(1.0f)
                        .setDuration(500)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                .start()
        }
        persistConversations()
    }

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

    private fun renderConversation(conversation: Conversation) {
        binding.messagesContainer.removeAllViews()
        clearAutomationPanelRuntimeRefs()
        syncMessageTranscript(conversation)
        binding.messagesContentHost.post {
            binding.scrollArea.smoothScrollTo(
                0,
                binding.messagesContentHost.height
            )
        }
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

    private fun stripAutomationMarker(rawText: String): String {
        return AutomationMessageParser.stripAutomationMarker(rawText)
    }

    private fun sendMessage(content: Any, resendUser: Boolean = true, retryMode: Boolean = false) {

        if (isRequestInFlight) {
            Toast.makeText(this, "正在生成回复，请稍后…", Toast.LENGTH_SHORT).show()
            return
        }

        val localModeEnabled = isLocalModelModeEnabled()
        val apiKey = prefs.getString("api_key", "") ?: ""

        if (localModeEnabled && !localModelReady) {
            Toast.makeText(
                this,
                getString(R.string.m3t_sidebar_local_model_not_ready),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!localModeEnabled && apiKey.isBlank()) {

            Toast.makeText(this, getString(R.string.settings_api_key_missing_entry), Toast.LENGTH_SHORT).show()
            startActivityWithMaterialForwardTransition(Intent(this, DrawerSettingsActivity::class.java))

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
        val resolvedModel = resolveApiModel()

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

            // 用户消息展示保持纯文本，附件通过图标展示
            val messageContentStr = baseUserText
            
            if (resendUser) {
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

                // 使用 StreamRenderHelper 绑定视图
                val aiView =
                        layoutInflater.inflate(
                                R.layout.item_ai_message_complex,
                                null,
                                false,
                        )
                val vh = StreamRenderHelper.bindViews(aiView)
                StreamRenderHelper.initThinkingState(vh)
                val tvModelName = aiView.findViewById<TextView>(R.id.tv_model_name)
                if (resolvedModel.isNotBlank()) {
                    tvModelName.text = resolvedModel
                    tvModelName.visibility = View.VISIBLE
                } else {
                    tvModelName.visibility = View.GONE
                }

                // 按钮事件绑定
                val retryPrompt = textForTitle
                vh.retryButton?.setOnClickListener {
                    setRetryButtonLoadingState(vh.retryButton, isLoading = true)
                    val started = retryMessage(retryPrompt)
                    if (!started) {
                        setRetryButtonLoadingState(vh.retryButton, isLoading = false)
                    }
                }

                vh.copyButton?.setOnClickListener {
                    val cm =
                            getSystemService(android.content.Context.CLIPBOARD_SERVICE) as
                                    android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("AI Reply", vh.messageContent.text)
                    cm.setPrimaryClip(clip)
                    Toast.makeText(this@MainActivity, "已复制内容", Toast.LENGTH_SHORT).show()
                }

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
                var floatingStreamStarted = false

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
                        // 重试前清理界面
                        reasoningSb.clear()
                        contentSb.clear()
                        runOnUiThread { StreamRenderHelper.initThinkingState(vh) }
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
                            reasoningSb.append(delta)
                            runOnUiThread {
                                StreamRenderHelper.processReasoningDelta(
                                    vh,
                                    delta,
                                    lifecycleScope,
                                ) { smoothScrollToBottom() }
                                updateStreamingTranscriptFromBuffers(
                                    retryText = baseUserText,
                                    reasoning = reasoningSb,
                                    answer = contentSb,
                                )
                            }
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
                            runOnUiThread {
                                StreamRenderHelper.processContentDelta(
                                    vh,
                                    delta,
                                    lifecycleScope,
                                    this@MainActivity,
                                    onScroll = { smoothScrollToBottom() },
                                    onPhaseChange = { isAnswerPhase ->
                                        if (isAnswerPhase) {
                                            StreamRenderHelper.transitionToAnswer(vh)
                                            if (
                                                vh.thinkingText.visibility == View.VISIBLE ||
                                                    vh.thinkingContentArea.visibility == View.VISIBLE
                                            ) {
                                                vh.thinkingHeader.performClick()
                                            }
                                        }
                                    },
                                )
                                updateStreamingTranscriptFromBuffers(
                                    retryText = baseUserText,
                                    reasoning = reasoningSb,
                                    answer = contentSb,
                                )
                            }

                            // 更新 contentSb（用于保存）
                            // 注意：这里我们保存原始内容，解析器会处理显示
                            contentSb.append(delta)

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
                                apiKey = apiKey,
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

                val finalContent =
                        if (shouldStopGeneration) {
                            "生成已停止"
                        } else if (streamOk) {
                            contentSb.toString()
                        } else {
                            val err = lastError?.message ?: "Unknown error"
                            "请求失败: $err"
                        }

                // 显示完成状态
                runOnUiThread {
                    StreamRenderHelper.markCompleted(vh, timeCost)
                    if (
                            vh.thinkingText.visibility == View.VISIBLE ||
                                    vh.thinkingContentArea.visibility == View.VISIBLE
                    ) {
                        vh.thinkingHeader.performClick()
                    }
                    if (!streamOk || shouldStopGeneration) {
                        // 如果失败或被停止，直接显示消息
                        vh.messageContent.text = finalContent
                    }
                }

                if (floatingStreamStarted && FloatingChatService.isRunning()) {
                    FloatingChatService.getInstance()
                            ?.finishExternalStreamAiReply(timeCost.toInt(), finalContent)
                }

                // 保存到历史 - 使用解析后的内容
                val thinkingContent = StreamRenderHelper.getThinkingText(vh)
                val renderedAnswerRaw = StreamRenderHelper.getAnswerText(vh).trim()
                val parsedFinalAnswer = parseStoredAiContent(finalContent).second.trim()
                val fallbackAnswerRaw =
                    parsedFinalAnswer.ifBlank { stripAutomationMarker(finalContent).trim() }
                val answerContentRaw =
                    if (renderedAnswerRaw.isNotBlank()) renderedAnswerRaw else fallbackAnswerRaw
                if (renderedAnswerRaw.isBlank() && answerContentRaw.isNotBlank() && streamOk && !shouldStopGeneration) {
                    runOnUiThread {
                        StreamRenderHelper.applyMarkdownToHistory(vh.messageContent, answerContentRaw)
                    }
                }
                val (answerContent, markerInAnswer) =
                        extractAutomationInstruction(answerContentRaw)
                val automationInstruction =
                        markerInAnswer ?: extractAutomationInstruction(finalContent).second

                if (answerContent != answerContentRaw) {
                    runOnUiThread {
                        StreamRenderHelper.applyMarkdownToHistory(vh.messageContent, answerContent)
                    }
                }

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
                clearStreamingTranscript()
                syncMessageTranscript(cc)
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

    /**
     * 重试按钮轻量 loading 态：禁用点击、弱化透明度、切换文案。
     */
    private fun setRetryButtonLoadingState(retryButton: View?, isLoading: Boolean) {
        val button = retryButton ?: return
        button.isEnabled = !isLoading
        button.alpha = if (isLoading) 0.65f else 1f

        val retryTextView = button.findViewById<TextView?>(R.id.tv_retry_text)
        retryTextView?.text = if (isLoading) getString(R.string.retrying) else getString(R.string.retry)

        val retryIconView = button.findViewById<ImageView?>(R.id.iv_retry_icon)
        retryIconView?.alpha = if (isLoading) 0.6f else 1f
    }

    private fun clearAutomationAutoConfirm(messageRef: AutomationMessageRef? = null) {
        var resolvedRef: AutomationMessageRef? = null
        if (messageRef != null) {
            val resolved = resolveAutomationMessageRef(messageRef) ?: return
            if (automationAutoConfirmRef != resolved) return
            resolvedRef = resolved
        } else {
            resolvedRef = automationAutoConfirmRef
        }
        automationAutoConfirmJob?.cancel()
        automationAutoConfirmJob = null
        resolvedRef?.let { automationCountdownSeconds.remove(it) }
        automationAutoConfirmRef = null
        syncTranscriptForAutomationMessage(resolvedRef)
    }

    private fun scheduleAutomationAutoConfirm(
        button: View?,
        textView: TextView?,
        task: String,
        messageRef: AutomationMessageRef?
    ) {
        clearAutomationAutoConfirm(messageRef)
        if (button == null || textView == null) return
        if (task.isBlank()) return
        if (!VirtualDisplayConfig.getAutoApproveAutomation(this)) return

        val resolvedRef = resolveAutomationMessageRef(messageRef) ?: return
        automationAutoConfirmRef = resolvedRef
        val currentJob =
            lifecycleScope.launch {
                for (secondsRemaining in 10 downTo 1) {
                    if (automationAutoConfirmRef != resolvedRef) return@launch
                    if (!button.isAttachedToWindow || !button.isEnabled) return@launch
                    automationCountdownSeconds[resolvedRef] = secondsRemaining
                    textView.text = getString(R.string.automation_confirm_countdown, secondsRemaining)
                    syncTranscriptForAutomationMessage(resolvedRef)
                    delay(1000L)
                }

                if (automationAutoConfirmRef != resolvedRef) return@launch
                if (!button.isAttachedToWindow || !button.isEnabled) return@launch
                button.performClick()
            }
        automationAutoConfirmJob = currentJob
        currentJob.invokeOnCompletion {
            if (automationAutoConfirmJob === currentJob) {
                automationCountdownSeconds.remove(resolvedRef)
                automationAutoConfirmJob = null
                automationAutoConfirmRef = null
                syncTranscriptForAutomationMessage(resolvedRef)
            }
        }
    }
    private fun bindAutomationConfirmButton(
        button: View?,
        textView: TextView?,
        instruction: String?,
        messageRef: AutomationMessageRef?,
        isConfirmed: Boolean,
        isFinished: Boolean
    ) {
        val statusView = findAutomationPanelStatusView(button)
        val task = instruction?.trim().orEmpty()
        val iconView = button?.findViewById<ImageView?>(R.id.iv_confirm_icon)

        if (isConfirmed) {
            clearAutomationAutoConfirm(messageRef)
            if (isFinished) {
                configureAutomationFinishedButton(
                    button = button,
                    textView = textView,
                    iconView = iconView,
                    statusView = statusView
                )
            } else {
                configureAutomationTerminateButton(
                    button = button,
                    textView = textView,
                    iconView = iconView,
                    statusView = statusView,
                    messageRef = messageRef,
                )
            }
            return
        }

        if (task.isBlank()) {
            clearAutomationAutoConfirm(messageRef)
            button?.visibility = View.GONE
            button?.isEnabled = false
            textView?.text = getString(R.string.automation_confirm)
            return
        }

        button?.visibility = View.VISIBLE
        button?.isEnabled = true
        button?.alpha = 1f
        textView?.text = getString(R.string.automation_confirm)
        statusView?.text = getString(R.string.automation_scene_need_confirm)
        button?.setOnClickListener {
            if (button.isEnabled.not()) return@setOnClickListener
            clearAutomationAutoConfirm(messageRef)
            button.isEnabled = false
            button.alpha = 0.7f
            textView?.text = getString(R.string.automation_confirming)

            val readyState = resolveAutomationReadyState()
            if (!readyState.ready) {
                button.isEnabled = true
                button.alpha = 1f
                textView?.text = getString(R.string.automation_not_ready_short)
                statusView?.text = getString(R.string.automation_scene_not_ready)
                Toast.makeText(
                    this@MainActivity,
                    resolveAutomationNotReadyToast(readyState.reason),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val dispatchResult =
                ActivityAutomationInstructionGateway.dispatchFromAdvancedAi(
                    context = this@MainActivity,
                    instruction = task
                )

            if (dispatchResult.success) {
                markAutomationCommandConfirmed(task, messageRef)
                configureAutomationTerminateButton(
                    button = button,
                    textView = textView,
                    iconView = iconView,
                    statusView = statusView,
                    messageRef = messageRef,
                )
            } else {
                button.isEnabled = true
                button.alpha = 1f
                textView?.text = getString(R.string.automation_confirm)
                statusView?.text = getString(R.string.automation_scene_need_confirm)
                scheduleAutomationAutoConfirm(button, textView, task, messageRef)
            }
        }
        scheduleAutomationAutoConfirm(button, textView, task, messageRef)
    }

    private fun configureAutomationTerminateButton(
        button: View?,
        textView: TextView?,
        iconView: ImageView?,
        statusView: TextView?,
        messageRef: AutomationMessageRef? = null,
    ) {
        clearAutomationAutoConfirm(messageRef)
        button?.visibility = View.VISIBLE
        button?.background = ContextCompat.getDrawable(this, R.drawable.bg_action_button_oval_danger)
        textView?.setTextColor(ContextCompat.getColor(this, R.color.m3t_on_error_container))
        iconView?.setImageResource(R.drawable.ic_stop_24)
        iconView?.setColorFilter(ContextCompat.getColor(this, R.color.m3t_on_error_container))

        if (isAutomationTerminatePending(messageRef)) {
            textView?.text = getString(R.string.automation_terminating)
            statusView?.text = getString(R.string.automation_scene_stop_requested)
            button?.isEnabled = false
            button?.alpha = 0.75f
            return
        }

        button?.isEnabled = true
        button?.alpha = 1f
        textView?.text = getString(R.string.automation_terminate)
        statusView?.text = getString(R.string.automation_scene_confirmed)
        button?.setOnClickListener {
            requestAutomationStopFromHome()
            markAutomationTerminatePending(messageRef)
            textView?.text = getString(R.string.automation_terminating)
            statusView?.text = getString(R.string.automation_scene_stop_requested)
            button.isEnabled = false
            button.alpha = 0.75f
            automationTerminateFallbackJob?.cancel()
            val resolvedRef = resolveAutomationMessageRef(messageRef)
            if (resolvedRef != null) {
                automationTerminateFallbackJob =
                    lifecycleScope.launch {
                        delay(12_000L)
                        if (automationTerminatePendingRef == resolvedRef) {
                            clearAutomationTerminatePending(resolvedRef)
                            configureAutomationTerminateButton(
                                button = button,
                                textView = textView,
                                iconView = iconView,
                                statusView = statusView,
                                messageRef = resolvedRef,
                            )
                            textView?.text = getString(R.string.automation_terminate)
                            button.alpha = 1f
                            button.isEnabled = true
                            statusView?.text = getString(R.string.automation_scene_confirmed)
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.automation_terminate_timeout_retry),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
            }
        }
    }

    private fun configureAutomationFinishedButton(
        button: View?,
        textView: TextView?,
        iconView: ImageView?,
        statusView: TextView?
    ) {
        clearAutomationTerminatePending()
        clearAutomationAutoConfirm()
        button?.visibility = View.VISIBLE
        button?.isEnabled = false
        button?.alpha = 1f
        button?.background = ContextCompat.getDrawable(this, R.drawable.bg_action_button_oval)
        textView?.setTextColor(ContextCompat.getColor(this, R.color.m3t_message_action))
        iconView?.setColorFilter(ContextCompat.getColor(this, R.color.m3t_message_action))
        statusView?.text = getString(R.string.automation_scene_finished)
        textView?.text = getString(R.string.automation_confirmed)
        iconView?.setImageResource(R.drawable.ic_check_circle_24)
        button?.setOnClickListener(null)
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
        val updated =
            (withoutConfirmed.trimEnd() + "\n[[AUTO_CONFIRMED]]")
                .trim()

        if (updated == origin.content) return

        targetConversation.messages[targetIndex] = origin.copy(content = updated)
        targetConversation.updatedAt = System.currentTimeMillis()
        syncMessageTranscript(targetConversation)
        persistConversations()
    }

    private fun findAutomationPanelStatusView(anchor: View?): TextView? {
        var cursor: View? = anchor
        while (cursor != null) {
            val found = cursor.findViewById<TextView?>(R.id.automation_panel_status)
            if (found != null) return found
            cursor = cursor.parent as? View
        }
        return null
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

    private fun renderAutomationTimelineRows(
        container: LinearLayout,
        timeline: List<AutomationTimelineEntry>
    ) {
        container.removeAllViews()

        if (timeline.isEmpty()) {
            val waitingView =
                TextView(this).apply {
                    text = getString(R.string.automation_scene_waiting)
                    setTextAppearance(this@MainActivity, R.style.TextAppearance_M3t_Body_Small)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.m3t_on_surface_variant))
                }
            container.addView(
                waitingView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            return
        }

        val rowSpacing = resources.getDimensionPixelSize(R.dimen.m3t_spacing_xs)
        timeline.forEachIndexed { index, entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val displayText = entry.displayText.trim()
            if (displayText.isNotBlank()) {
                val thoughtView =
                    TextView(this).apply {
                        text = "• $displayText"
                        setTextAppearance(this@MainActivity, R.style.TextAppearance_M3t_Body_Small)
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.m3t_on_surface_variant))
                    }
                val thoughtLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                row.addView(thoughtView, thoughtLp)
            }

            val action = entry.action?.trim().orEmpty()
            if (action.isNotBlank()) {
                val chip = createAutomationInlineChip(action)
                val chipLp =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin =
                            if (displayText.isNotBlank()) {
                                resources.getDimensionPixelSize(R.dimen.m3t_spacing_xxxs)
                            } else {
                                0
                            }
                    }
                row.addView(chip, chipLp)
            }

            val rowLp =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = rowSpacing
                }
            container.addView(row, rowLp)
        }
    }

    private fun createAutomationInlineChip(label: String): TextView {
        return TextView(this).apply {
            text = label
            setTextAppearance(this@MainActivity, R.style.TextAppearance_M3t_Body_Small)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.m3t_message_action))
            val padH = resources.getDimensionPixelSize(R.dimen.m3t_spacing_sm)
            val padV = resources.getDimensionPixelSize(R.dimen.m3t_spacing_xs)
            setPadding(padH, padV, padH, padV)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_action_button_oval)
        }
    }

    private fun extractAutomationDisplayText(logLine: String): String? {
        val normalized = normalizeAutomationLogLine(logLine)
        val thought =
            when {
                normalized.startsWith("思考：") -> normalized.substringAfter("思考：").trim()
                normalized.startsWith("修复思考：") -> normalized.substringAfter("修复思考：").trim()
                else -> ""
            }
        if (thought.isNotBlank()) return thought
        return null
    }

    private fun extractAutomationIntentTextFromOutput(logLine: String): String? {
        val normalized = normalizeAutomationLogLine(logLine)
        val outputPayload =
            when {
                normalized.startsWith("输出：") -> normalized.substringAfter("输出：").trim()
                normalized.startsWith("修复输出：") -> normalized.substringAfter("修复输出：").trim()
                else -> ""
            }
        if (outputPayload.isBlank()) return null

        val actionFromDo =
            Regex("""action\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(outputPayload)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.lowercase()
                .orEmpty()
        if (actionFromDo in setOf("type", "input", "text", "type_name")) {
            return null
        }

        val textFromDo =
            Regex("""text\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(outputPayload)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        if (!textFromDo.isNullOrBlank()) return textFromDo

        val textFromJson =
            Regex(""""text"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(outputPayload)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        if (!textFromJson.isNullOrBlank()) return textFromJson

        return null
    }

    private fun extractDescFromOutputPayload(payload: String): String? {
        val clean = payload.trim()
        if (clean.isBlank()) return null

        // 1) JSON/对象形式："desc":"..."
        Regex(""""desc"\s*:\s*"([^"]+)"""")
            .find(clean)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // 2) do(..., desc="...")
        Regex("""desc\s*=\s*"([^"]+)"""")
            .find(clean)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // 3) 文本形式：desc: ...
        Regex("""\bdesc\b\s*[:=]\s*(.+)$""", RegexOption.IGNORE_CASE)
            .find(clean)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return null
    }

    private fun mapAutomationActionLabel(rawAction: String): String {
        return when (rawAction.trim().lowercase()) {
            "tap", "click" -> "点击"
            "type", "input" -> "输入"
            "swipe" -> "滑动"
            "launch", "open", "startapp" -> "启动"
            "back" -> "返回"
            "wait" -> "等待"
            "longpress" -> "长按"
            "scroll" -> "滚动"
            "home" -> "回桌面"
            else -> rawAction.trim().ifBlank { "执行" }.take(10)
        }
    }

    private fun extractAutomationActionLabel(logLine: String): String? {
        val normalized = normalizeAutomationLogLine(logLine)
        val actionByCurrent = normalized.substringAfter("当前动作：", "").trim()
        if (actionByCurrent.isNotBlank() && actionByCurrent != normalized) {
            return actionByCurrent.take(10)
        }

        return null
    }

    private fun setAutomationPanelCollapsedState(
        content: View,
        toggleButton: ImageButton,
        collapsed: Boolean,
        animate: Boolean = true
    ) {
        val targetRotation = if (collapsed) 180f else 0f
        val contentOffset = resources.getDimensionPixelSize(R.dimen.m3t_spacing_sm).toFloat()

        content.animate().cancel()
        toggleButton.animate().cancel()
        toggleButton.contentDescription =
            getString(
                if (collapsed) {
                    R.string.automation_scene_expand
                } else {
                    R.string.automation_scene_collapse
                }
            )

        if (!animate || !content.isLaidOut) {
            content.visibility = if (collapsed) View.GONE else View.VISIBLE
            content.alpha = 1f
            content.translationY = 0f
            content.scaleY = 1f
            toggleButton.rotation = targetRotation
            toggleButton.isEnabled = true
            return
        }

        toggleButton.isEnabled = false
        toggleButton.animate().cancel()
        content.animate().cancel()
        toggleButton.animate()
            .rotation(targetRotation)
            .setDuration(260)
            .setInterpolator(OvershootInterpolator(0.45f))
            .start()

        if (collapsed) {
            if (content.visibility != View.VISIBLE) {
                toggleButton.isEnabled = true
                return
            }
            content.pivotY = 0f
            content.pivotX = (content.width * 0.5f).coerceAtLeast(0f)
            content.animate()
                .alpha(0f)
                .translationY(-contentOffset * 0.4f)
                .scaleX(0.985f)
                .scaleY(0.985f)
                .setDuration(240)
                .setInterpolator(AccelerateInterpolator(1.1f))
                .withEndAction {
                    content.visibility = View.GONE
                    content.alpha = 1f
                    content.translationY = 0f
                    content.scaleX = 1f
                    content.scaleY = 1f
                    toggleButton.isEnabled = true
                }
                .start()
        } else {
            content.visibility = View.VISIBLE
            content.alpha = 0f
            content.translationY = -contentOffset * 0.28f
            content.scaleX = 0.985f
            content.scaleY = 0.985f
            content.pivotY = 0f
            content.pivotX = (content.width * 0.5f).coerceAtLeast(0f)
            content.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(320)
                .setInterpolator(DecelerateInterpolator(1.25f))
                .withEndAction {
                    toggleButton.isEnabled = true
                }
                .start()
        }
    }

    private fun configureAutomationPanel(
        command: String,
        logs: List<String>,
        hasConfirm: Boolean,
        hasConfirmed: Boolean,
        statusView: TextView,
        commandView: TextView,
        logContainer: LinearLayout
    ) {
        commandView.text = command
        val hasTerminalLog = logs.any { isAutomationTerminalLog(it) }
        statusView.text =
            when {
                hasConfirm -> getString(R.string.automation_scene_need_confirm)
                hasTerminalLog -> getString(R.string.automation_scene_finished)
                logs.isNotEmpty() -> getString(R.string.automation_scene_running)
                hasConfirmed -> getString(R.string.automation_scene_confirmed)
                else -> getString(R.string.automation_scene_not_ready)
            }

        val timeline = AutomationTimelineFormatter.buildTimeline(logs)
        logContainer.tag = timeline
        renderAutomationTimelineRows(logContainer, timeline)
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
     * 解析并显示复杂的 AI 消息（包含思考过程）
     * 支持淡蓝色液态玻璃框、思考过程折叠、打字机动画、丝滑滚动
     */
    private fun appendComplexAiMessage(
        author: String,
        fullContent: String,
        animate: Boolean,
        timeCostMs: Long,
        retryUserText: String? = null,
        automationInstructionForConfirm: String? = null,
        messageIndexInConversation: Int? = null,
        modelName: String? = null
    ) {
        // 1. Inflate 复杂布局
        val view = layoutInflater.inflate(R.layout.item_ai_message_complex, binding.messagesContainer, false)
        binding.messagesContainer.addView(view)
        syncMessageTranscript()
        
        val thinkingLayout = view.findViewById<LinearLayout>(R.id.thinking_layout)
        val thinkingHeader = view.findViewById<LinearLayout>(R.id.thinking_header)
        val thinkingText = view.findViewById<TextView>(R.id.thinking_text)
        val thinkingIndicator = view.findViewById<TextView>(R.id.thinking_indicator_text)
        val messageContent = view.findViewById<TextView>(R.id.message_content)
        val authorName = view.findViewById<TextView>(R.id.ai_author_name)
        val btnConfirm = view.findViewById<View?>(R.id.btn_confirm)
        val tvConfirmText = view.findViewById<TextView?>(R.id.tv_confirm_text)
        val automationPanel = view.findViewById<LinearLayout>(R.id.automation_panel)
        val automationStatus = view.findViewById<TextView>(R.id.automation_panel_status)
        val automationPanelToggle = view.findViewById<ImageButton>(R.id.automation_panel_toggle)
        val automationPanelContent = view.findViewById<LinearLayout>(R.id.automation_panel_content)
        val automationCommand = view.findViewById<TextView>(R.id.automation_panel_command)
        val automationLogContainer = view.findViewById<LinearLayout>(R.id.automation_log_container)
        val tvModelName = view.findViewById<TextView>(R.id.tv_model_name)

        // 解析内容（兼容旧格式，避免展示旧分隔符）
        val (contentWithoutLogMarkers, embeddedAutomationLogs) = extractAutomationLogMarkers(fullContent)
        val (contentWithoutConfirmedMarker, hasConfirmedMarker) =
            extractAutomationConfirmedMarker(contentWithoutLogMarkers)
        val (contentWithoutConfirmMarker, confirmInstructionFromMessage) =
            extractAutomationConfirmInstruction(contentWithoutConfirmedMarker)
        val confirmInstruction =
            if (hasConfirmedMarker) null else (automationInstructionForConfirm ?: confirmInstructionFromMessage)
        val (storedThinking, storedAnswer) = parseStoredAiContent(contentWithoutConfirmMarker)
        val thinkContent = storedThinking?.trim()
        val realContent = storedAnswer.trim()
        val automationCommandText = extractAutomationCommand(realContent) ?: confirmInstruction
        val messageRef =
            if (messageIndexInConversation != null && messageIndexInConversation >= 0) {
                val cid = activeConversation?.id ?: -1L
                if (cid > 0L) {
                    AutomationMessageRef(
                        conversationId = cid,
                        messageIndex = messageIndexInConversation
                    )
                } else {
                    null
                }
            } else {
                null
            }
        val initialAutomationLogs =
            buildList {
                addAll(embeddedAutomationLogs)
                val notReadyReason =
                    realContent.lines().map { it.trim() }.firstOrNull { it.startsWith("系统未就绪：") }
                if (!notReadyReason.isNullOrBlank()) add(notReadyReason)
            }
        val isAutomationCard = !automationCommandText.isNullOrBlank()
        val isAutomationFinished = initialAutomationLogs.any { isAutomationTerminalLog(it) }
        
        // 设置作者名
        val aiHeaderRow = authorName.parent as? View
        val automationPanelLayoutParams = automationPanel.layoutParams as? ViewGroup.MarginLayoutParams
        val automationPanelDefaultTopMargin = resources.getDimensionPixelSize(R.dimen.m3t_spacing_sm)
        val automationPanelDefaultPadding = resources.getDimensionPixelSize(R.dimen.m3t_spacing_md)
        authorName.text = if (author == "Aries") "Aries AI" else author
        if (isAutomationCard) {
            aiHeaderRow?.visibility = View.GONE
            tvModelName.visibility = View.GONE
            view.setBackgroundResource(R.drawable.bg_glass_pane)
            automationPanel.background = null
            automationPanel.setPadding(0, 0, 0, 0)
            automationPanelLayoutParams?.topMargin = 0
            automationPanel.layoutParams = automationPanelLayoutParams
        } else {
            aiHeaderRow?.visibility = View.VISIBLE
            view.setBackgroundResource(R.drawable.bg_glass_pane)
            automationPanel.setBackgroundResource(R.drawable.bg_rounded_input_light)
            automationPanel.setPadding(
                automationPanelDefaultPadding,
                automationPanelDefaultPadding,
                automationPanelDefaultPadding,
                automationPanelDefaultPadding
            )
            automationPanelLayoutParams?.topMargin = automationPanelDefaultTopMargin
            automationPanel.layoutParams = automationPanelLayoutParams
            authorName.visibility = View.VISIBLE
            if (!modelName.isNullOrBlank()) {
                tvModelName.text = modelName
                tvModelName.visibility = View.VISIBLE
            } else {
                tvModelName.visibility = View.GONE
            }
        }
        // 设置思考部分交互
        if (!thinkContent.isNullOrBlank()) {
            thinkingLayout.visibility = View.VISIBLE
            val seconds = (timeCostMs / 1000).coerceAtLeast(1)
            val headerTitle = thinkingHeader.getChildAt(0) as TextView
            headerTitle.text = "已思考 (用时 ${seconds} 秒)"
            
            var isExpanded = true
            thinkingHeader.setOnClickListener {
                isExpanded = !isExpanded
                if (isExpanded) {
                    thinkingText.visibility = View.VISIBLE
                    thinkingIndicator.text = " ⌄" // Down arrow (expanded)
                    (view.findViewById<View>(R.id.thinking_content_area)).visibility = View.VISIBLE
                } else {
                    thinkingText.visibility = View.GONE
                    thinkingIndicator.text = " ›" // Right arrow (collapsed)
                    (view.findViewById<View>(R.id.thinking_content_area)).visibility = View.GONE
                }
            }
        } else {
            thinkingLayout.visibility = View.GONE
        }

        if (isAutomationCard) {
            messageContent.visibility = View.GONE
            automationPanel.visibility = View.VISIBLE
            setAutomationPanelCollapsedState(
                content = automationPanelContent,
                toggleButton = automationPanelToggle,
                collapsed = false,
                animate = false
            )
            automationPanelToggle.setOnClickListener {
                setAutomationPanelCollapsedState(
                    content = automationPanelContent,
                    toggleButton = automationPanelToggle,
                    collapsed = automationPanelContent.visibility == View.VISIBLE
                )
            }
            configureAutomationPanel(
                command = automationCommandText,
                logs = initialAutomationLogs,
                hasConfirm = !confirmInstruction.isNullOrBlank(),
                hasConfirmed = hasConfirmedMarker,
                statusView = automationStatus,
                commandView = automationCommand,
                logContainer = automationLogContainer
            )
            val conversationId = activeConversation?.id ?: -1L
            if (conversationId > 0L && messageIndexInConversation != null && messageIndexInConversation >= 0) {
                activeAutomationPanelConversationId = conversationId
                activeAutomationPanelMessageIndex = messageIndexInConversation
                activeAutomationPanelLogContainer = automationLogContainer
                activeAutomationPanelStatusView = automationStatus
                activeAutomationPanelConfirmButton = btnConfirm
                activeAutomationPanelConfirmTextView = tvConfirmText
            }
        } else {
            messageContent.visibility = View.VISIBLE
            automationPanel.visibility = View.GONE
            automationPanelToggle.setOnClickListener(null)
            automationPanelContent.visibility = View.VISIBLE
        }
        
        smoothScrollToBottom()

        if (!animate || isAutomationCard) {
            if (!thinkContent.isNullOrBlank()) {
                StreamRenderHelper.applyMarkdownToHistory(thinkingText, thinkContent)
                if (thinkingText.visibility == View.VISIBLE) {
                    thinkingHeader.performClick()
                }
            }
            if (messageContent.visibility == View.VISIBLE) {
                StreamRenderHelper.applyMarkdownToHistory(messageContent, realContent)
            }
            view.findViewById<View>(R.id.action_area).visibility = View.VISIBLE

            val btnCopy = view.findViewById<View>(R.id.btn_copy)
            btnCopy.setOnClickListener {
                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                // 复制时是否包含思考过程？Aries AI 默认只复制正文
                val clip = android.content.ClipData.newPlainText("AI Reply", realContent)
                cm.setPrimaryClip(clip)
                Toast.makeText(this@MainActivity, "已复制内容", Toast.LENGTH_SHORT).show()
            }

            val btnRetry = view.findViewById<View>(R.id.btn_retry)
            btnRetry.setOnClickListener {
                val retryText = retryUserText ?: activeConversation?.messages?.findLast { it.isUser }?.content
                if (!retryText.isNullOrBlank()) {
                    setRetryButtonLoadingState(btnRetry, isLoading = true)
                    val started = retryMessage(retryText)
                    if (!started) {
                        setRetryButtonLoadingState(btnRetry, isLoading = false)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "未找到可重试的用户问题", Toast.LENGTH_SHORT).show()
                }
            }
            bindAutomationConfirmButton(
                button = btnConfirm,
                textView = tvConfirmText,
                instruction = confirmInstruction,
                messageRef = messageRef,
                isConfirmed = hasConfirmedMarker,
                isFinished = isAutomationFinished
            )
            return
        }
        
        // 动画显示逻辑
        lifecycleScope.launch {
            // 1. 如果有思考内容，先播放思考打字机
            if (!thinkContent.isNullOrBlank()) {
                val sb = StringBuilder()
                val chunkSize = 5
                var index = 0
                while (index < thinkContent.length) {
                    val end = minOf(index + chunkSize, thinkContent.length)
                    sb.append(thinkContent.substring(index, end))
                    thinkingText.text = sb.toString()
                    index = end
                    
                    smoothScrollToBottom()
                    delay(10) // 思考过程刷快一点
                }
                thinkingText.text = thinkContent // 确保完整
                delay(200) // 思考完停顿一下
                if (thinkingText.visibility == View.VISIBLE) {
                    thinkingHeader.performClick()
                }
            }
            
            // 2. 播放正文打字机
            val sb = StringBuilder()
            val chunkSize = 2 // 正文稍微慢一点，更像打字
            var index = 0
            while (index < realContent.length) {
                val end = minOf(index + chunkSize, realContent.length)
                val chunk = realContent.substring(index, end)
                sb.append(chunk)
                messageContent.text = sb.toString()
                index = end
                
                smoothScrollToBottom()
                
                // 根据标点调整节奏
                val lastChar = chunk.lastOrNull() ?: ' '
                val d = when (lastChar) {
                    '。', '！', '？', '\n' -> 50L
                    '，', '；' -> 30L
                    else -> 10L // 默认很快，丝滑
                }
                delay(d)
            }
            messageContent.text = realContent
            
            // 动画结束后显示底部操作栏（分割线+复制/重试）
            val actionArea = view.findViewById<View>(R.id.action_area)
            actionArea.visibility = View.VISIBLE
            smoothScrollToBottom()
        }
        
        // 绑定复制和重试按钮事件
        val btnCopy = view.findViewById<View>(R.id.btn_copy)
        btnCopy.setOnClickListener {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            // 复制时是否包含思考过程？Aries AI 默认只复制正文
            val clip = android.content.ClipData.newPlainText("AI Reply", realContent)
            cm.setPrimaryClip(clip)
            Toast.makeText(this@MainActivity, "已复制内容", Toast.LENGTH_SHORT).show()
        }

        val btnRetry = view.findViewById<View>(R.id.btn_retry)
        btnRetry.setOnClickListener {
            // 重试逻辑：获取上一条用户消息，重新发送
            val retryText = retryUserText ?: activeConversation?.messages?.findLast { it.isUser }?.content
            if (!retryText.isNullOrBlank()) {
                setRetryButtonLoadingState(btnRetry, isLoading = true)
                val started = retryMessage(retryText)
                if (!started) {
                    setRetryButtonLoadingState(btnRetry, isLoading = false)
                }
            } else {
                Toast.makeText(this@MainActivity, "未找到可重试的用户问题", Toast.LENGTH_SHORT).show()
            }
        }
        bindAutomationConfirmButton(
            button = btnConfirm,
            textView = tvConfirmText,
            instruction = confirmInstruction,
            messageRef = messageRef,
            isConfirmed = hasConfirmedMarker,
            isFinished = isAutomationFinished
        )
        
        if (!animate) {
            // 如果非动画模式（如历史记录），直接显示操作栏
            view.findViewById<View>(R.id.action_area).visibility = View.VISIBLE
        }
    }
    
    /**
     * 丝滑滚动到底部
     */
    private fun smoothScrollToBottom() {
        binding.messagesContentHost.post {
            val scrollView = binding.messagesContainer.parent as? android.widget.ScrollView ?: return@post
            // 检查是否需要滚动：如果已经在底部附近，则跟随滚动
            val viewHeight = binding.messagesContentHost.height
            val scrollViewHeight = scrollView.height
            val scrollY = scrollView.scrollY
            
            // 容差值，判定是否在底部
            val isAtBottom = (viewHeight - (scrollY + scrollViewHeight)) < 300 
            
            // 强制滚动，或者仅当用户没往回滚时滚动？
            // 用户要求“同步下移”，通常是强制跟随。
            scrollView.smoothScrollTo(0, viewHeight)
        }
    }

    /**
     * 重新进入页面时，确保所有已渲染的 AI 气泡都展示底部操作区（复制/重试）。
     * 某些情况下（如动画被打断或 Activity 复用）action_area 可能保持 GONE 状态。
     */
    private fun revealActionAreasForMessages() {
        val container = binding.messagesContainer
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val actionArea = child.findViewById<View?>(R.id.action_area)
            // thinking 占位或非标准布局可能没有 action_area，这里仅对存在的进行显隐修正
            if (actionArea != null && actionArea.visibility != View.VISIBLE) {
                actionArea.visibility = View.VISIBLE
            }
        }
    }

    /**
     * 【优化】使用StringBuilder批量更新方式显示消息
    * 参考groupBy 算法，每次收到一点就拼接，然后刷新整个文本
     */
    private fun appendMessageBatch(
            author: String,
            content: String,
            isUser: Boolean,
    ) {
        val tv =
                TextView(this).apply {
                    text = "$author："
                    setPadding(20, 12, 20, 12)
                    background =
                            ContextCompat.getDrawable(
                                    this@MainActivity,
                                    if (isUser) R.drawable.bg_user_bubble_water else R.drawable.bubble_bot
                            )
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.m3t_on_surface))
                }

        val lp =
                LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        .apply {
                            setMargins(12, 8, 12, 8)
                            gravity = if (isUser) Gravity.END else Gravity.START
                        }

        binding.messagesContainer.addView(tv, lp)

        binding.messagesContentHost.post {
            (binding.messagesContainer.parent as? android.widget.ScrollView)?.smoothScrollTo(
                    0,
                    binding.messagesContentHost.height
            )
        }

        // 使用StringBuilder批量构建文本，分块更新UI
        lifecycleScope.launch {
            val sb = StringBuilder("$author：")
            val chunkSize = 5 // 每5个字符批量更新一次
            var charIndex = 0
            
            while (charIndex < content.length) {
                // 计算本次要添加的字符数
                val endIndex = minOf(charIndex + chunkSize, content.length)
                val chunk = content.substring(charIndex, endIndex)
                sb.append(chunk)
                
                // 刷新整个文本到界面
                tv.text = sb.toString()
                
                charIndex = endIndex
                
                // 根据标点符号调整延迟，让显示更自然
                val lastChar = chunk.lastOrNull() ?: ' '
                val delayMs = when (lastChar) {
                    '。', '！', '？', '.', '!', '?', ';', '：', ':' -> 80L
                    '，', '、', '；', ',', ';', '：', ':' -> 50L
                    '\n' -> 60L
                    else -> 25L
                }
                delay(delayMs)
            }
            
            // 最终滚动到底部
            binding.messagesContentHost.post {
                (binding.messagesContainer.parent as? android.widget.ScrollView)?.smoothScrollTo(
                        0,
                        binding.messagesContentHost.height
                )
            }
        }
    }

    private fun appendMessageInstant(author: String, content: String, isUser: Boolean) {
        val tv =
                TextView(this).apply {
                    text = "$author：$content"
                    setPadding(20, 12, 20, 12)
                    background =
                            ContextCompat.getDrawable(
                                    this@MainActivity,
                                    if (isUser) R.drawable.bg_user_bubble_water else R.drawable.bubble_bot
                            )
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.m3t_on_surface))
                }
        val lp =
                LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        .apply {
                            setMargins(12, 8, 12, 8)
                            gravity = if (isUser) Gravity.END else Gravity.START
                        }
        binding.messagesContainer.addView(tv, lp)
        binding.messagesContentHost.post {
            (binding.messagesContainer.parent as? android.widget.ScrollView)?.smoothScrollTo(
                    0,
                    binding.messagesContentHost.height
            )
        }
    }

    private fun appendMessageTyping(
            author: String,
            content: String,
            isUser: Boolean,
            natural: Boolean = false
    ) {

        val tv =
                TextView(this).apply {
                    text = "$author："

                    setPadding(20, 12, 20, 12)

                    background =
                            ContextCompat.getDrawable(
                                    this@MainActivity,
                                    if (isUser) R.drawable.bg_user_bubble_water else R.drawable.bubble_bot
                            )

                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.m3t_on_surface))
                }

        val lp =
                LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        .apply {
                            setMargins(12, 8, 12, 8)

                            gravity = if (isUser) Gravity.END else Gravity.START
                        }

        binding.messagesContainer.addView(tv, lp)

        binding.messagesContentHost.post {
            (binding.messagesContainer.parent as? android.widget.ScrollView)?.smoothScrollTo(
                    0,
                    binding.messagesContentHost.height
            )
        }

        lifecycleScope.launch {
            for (i in 1..content.length) {

                tv.text = "$author：${content.take(i)}"

                val ch = content[i - 1]
                val d =
                        if (natural) {
                            when (ch) {
                                '。', '，', '、', '！', '？', '；', '.', ',', '!', '?', ';', '：', ':' ->
                                        130L
                                ' ' -> 30L
                                else -> 28L
                            }
                        } else 12L
                delay(d)
            }
        }
    }

    private fun showThinking() {
        removeThinking()

        val view = layoutInflater.inflate(R.layout.item_ai_message_complex, binding.messagesContainer, false)
        val authorName = view.findViewById<TextView>(R.id.ai_author_name)
        val messageContent = view.findViewById<TextView>(R.id.message_content)
        val thinkingLayout = view.findViewById<View>(R.id.thinking_layout)
        val actionArea = view.findViewById<View>(R.id.action_area)

        authorName.text = "Aries AI"
        authorName.visibility = View.VISIBLE
        thinkingLayout.visibility = View.GONE
        actionArea.visibility = View.GONE

        messageContent.text = "正在思考"
        messageContent.setTextColor(ContextCompat.getColor(this, R.color.m3t_thinking_text))

        binding.messagesContainer.addView(view)
        thinkingView = view
        thinkingTextView = messageContent

        lifecycleScope.launch {
            var n = 0
            while (thinkingView === view) {
                val dots = ".".repeat(n % 4)
                thinkingTextView?.text = "正在思考$dots"
                n++
                delay(400)
            }
        }
        binding.messagesContentHost.post {
            (binding.messagesContainer.parent as? android.widget.ScrollView)?.smoothScrollTo(
                    0,
                    binding.messagesContentHost.height
            )
        }
    }

    private fun removeThinking() {
        val v = thinkingView ?: return
        binding.messagesContainer.removeView(v)
        thinkingView = null
        thinkingTextView = null
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
        val apiKey = prefs.getString("api_key", "") ?: ""
        if (!localModeEnabled && apiKey.isBlank()) {
            onDone(text)
            return
        }
        if (localModeEnabled && !localModelReady) {
            onDone(text)
            return
        }

        lifecycleScope.launch {
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
                    AutoGlmClient.sendChatResult(
                        apiKey = apiKey,
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
        val displayed =
            conversations
                .filter { it.messages.isNotEmpty() }
                .sortedByDescending { it.updatedAt }
                .toMutableList()
        if (displayed.isEmpty()) {
            Toast.makeText(this, getString(R.string.history_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val displayedState =
            mutableStateListOf<ConversationHistoryItemUi>().apply {
                addAll(
                    displayed.map { conversation ->
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
                )
            }

        val composeView =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    AriesMaterialTheme {
                        ConversationHistoryDialog(
                            items = displayedState,
                            onDismiss = {
                                vibrateLight()
                                dialog.dismiss()
                            },
                            onSelect = { conversationId ->
                                conversations.firstOrNull { it.id == conversationId }?.let { conversation ->
                                    activeConversation = conversation
                                    renderConversation(conversation)
                                    persistConversations()
                                }
                                vibrateLight()
                                dialog.dismiss()
                            },
                            onDelete = { conversationId ->
                                if (conversations.none { it.id == conversationId }) {
                                    return@ConversationHistoryDialog
                                }
                                displayedState.removeAll { it.id == conversationId }
                                conversations.removeAll { it.id == conversationId }
                                if (activeConversation?.id == conversationId) {
                                    activeConversation = null
                                    startNewChat(clearUi = true)
                                }
                                persistConversations()
                                if (displayedState.isEmpty()) {
                                    dialog.dismiss()
                                }
                            },
                        )
                    }
                }
            }

        dialog.setContentView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            window.setDimAmount(0f)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            val params = window.attributes
            params.windowAnimations = 0
            window.attributes = params
        }

        dialog.show()
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
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (onboardingOverlay.isShowing()) {
            swipeTracking = false
            return super.dispatchTouchEvent(ev)
        }

        val density = resources.displayMetrics.density
        val touchSlop = 12 * density
        val swipeThreshold = 80 * density
        
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = ev.rawX
                swipeStartY = ev.rawY
                swipeTracking = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeTracking) return super.dispatchTouchEvent(ev)
                
                val dx = ev.rawX - swipeStartX
                val dy = ev.rawY - swipeStartY
                
                // 检测水平右滑动作
                if (dx > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    val isOpen = binding.drawerLayout.isDrawerOpen(GravityCompat.START)
                    
                    // 右滑距离超过阈值，打开侧边栏
                    if (!isOpen && dx > swipeThreshold) {
                        binding.drawerLayout.openDrawer(GravityCompat.START, true)
                        swipeTracking = false
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                swipeTracking = false
            }
        }
        return super.dispatchTouchEvent(ev)
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
