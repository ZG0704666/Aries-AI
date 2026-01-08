package com.ai.phoneagent

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.widget.ActionMenuView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ai.phoneagent.databinding.ActivityMainBinding
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ChatRequestMessage
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
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private data class UiMessage(
            val author: String,
            val content: String,
            val isUser: Boolean,
    )

    private data class Conversation(
            val id: Long,
            var title: String,
            val messages: MutableList<UiMessage>,
            var updatedAt: Long,
    )

    private lateinit var binding: ActivityMainBinding

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

    private var voiceInputAnimJob: Job? = null
    private var savedInputText: String = ""

    private var pendingSendAfterVoice: Boolean = false

    // 滑动手势相关
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeTracking = false
    private var originalContentTopPadding = 0
    
    // 小窗模式相关
    private var isAnimatingToMiniWindow = false
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1235
    private var pendingEnterMiniWindowAfterNotifPerm: Boolean = false

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

    private val permGuideShownPref = "perm_guide_shown"

    @Volatile private var suppressApiInputWatcher: Boolean = false
    @Volatile private var apiNeedsRecheckToastShown: Boolean = false

    private fun persistConversations() {
        try {
            val json = com.google.gson.Gson().toJson(conversations)
            prefs.edit()
                    .putString(conversationsKey, json)
                    .putLong(activeConversationIdKey, activeConversation?.id ?: -1L)
                    .apply()
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
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setupEdgeToEdge()

        setupToolbar()

        setupDrawer()

        setupInputBar()

        restoreApiKey()

        setupKeyboardListener()

        elevateAiBar()

        if (!tryRestoreConversations()) {
            startNewChat(clearUi = true)
        }

        binding.topAppBar.post { attachAnimatedBorderRing(binding.topAppBar, 2f, 18f) }
        binding.inputContainer.post { attachAnimatedBorderRing(binding.inputContainer, 2f, 20f) }

        binding.btnVoice.isEnabled = true

        initSherpaModel()
    }

    private fun setupEdgeToEdge() {
        originalContentTopPadding = binding.contentRoot.paddingTop
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        WindowCompat.getInsetsController(window, window.decorView)?.let {
            it.isAppearanceLightStatusBars = true
            it.isAppearanceLightNavigationBars = true
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

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            vibrateLight()
            hideKeyboard()
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.topAppBar.setOnMenuItemClickListener { item ->
            vibrateLight()
            when (item.itemId) {
                R.id.action_new_chat -> {
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
        if (prefs.getBoolean(permGuideShownPref, false)) return
        if (supportFragmentManager.findFragmentByTag(PermissionBottomSheet.TAG) != null) return
        runCatching {
            PermissionBottomSheet().show(supportFragmentManager, PermissionBottomSheet.TAG)
            prefs.edit().putBoolean(permGuideShownPref, true).apply()
        }
    }

    private fun offsetTopBarIcons() {
        val upOffsetPx = -7f * resources.displayMetrics.density
        val floatWinOffsetPx = -6f * resources.displayMetrics.density
        binding.topAppBar.post {
            for (i in 0 until binding.topAppBar.childCount) {
                val child = binding.topAppBar.getChildAt(i)
                if (child is ActionMenuView) {
                    for (j in 0 until child.childCount) {
                        val menuChild = child.getChildAt(j)
                        val tag = menuChild.contentDescription?.toString() ?: ""
                        if (tag.contains("小窗") || tag.contains("floating", true)) {
                            menuChild.translationY = floatWinOffsetPx
                        } else {
                            menuChild.translationY = upOffsetPx
                        }
                    }
                } else if (child is ImageButton) {
                    child.translationY = upOffsetPx
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        handleReturnFromFloatingWindow()

        restoreApiKey()
        maybeShowPermissionBottomSheet()

        // 设置消息同步监听器
        setupMessageSyncListener()

        // 防止返回应用后气泡底部的复制/重试按钮被隐藏
        revealActionAreasForMessages()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
        }
        handleReturnFromFloatingWindow()
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
                    val author = if (isUser) "我" else "Aries"
                    
                    // 检查是否已存在该消息（避免重复）
                    val exists = c.messages.any { it.content == content && it.isUser == isUser }
                    if (!exists) {
                        c.messages.add(UiMessage(author = author, content = content, isUser = isUser))
                        c.updatedAt = System.currentTimeMillis()
                        if (isUser) {
                            appendComplexUserMessage(author, content, animate = false)
                        } else {
                            appendComplexAiMessage(author, content, animate = false, timeCostMs = 0)
                        }
                        persistConversations()
                    }
                }
            }
            
            override fun onMessagesCleared() {
                runOnUiThread {
                    binding.messagesContainer.removeAllViews()
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
                    .trim()

            // 跳过空消息和"思考中"消息
            if (content.isBlank() || content == "思考中...") continue

            // 检查是否已存在该消息
            val exists = c.messages.any { it.content == content && it.isUser == isUser }
            if (!exists) {
                val author = if (isUser) "我" else "Aries"
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
        
        // 收集当前聊天消息传递给小窗
        val messagesList = ArrayList<String>()
        activeConversation?.messages?.forEach { msg ->
            messagesList.add("${if (msg.isUser) "我" else "Aries"}: ${msg.content}")
        }

        // 启动悬浮窗服务，传递消息和位置
        FloatingChatService.start(
            this@MainActivity,
            messages = messagesList,
            fromX = targetX,
            fromY = targetY,
            fromWidth = miniWindowWidth,
            fromHeight = miniWindowHeight,
            showDelayMs = 120L,
        )

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

        if (requestCode == 100 &&
                        pendingStartVoice &&
                        grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {

            pendingStartVoice = false

            startLocalVoiceInput()
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
    }

    private fun setupDrawer() {

        val header = binding.navigationView.getHeaderView(0)

        val apiInput = header.findViewById<android.widget.EditText>(R.id.apiInput)

        val apiStatus = header.findViewById<TextView>(R.id.apiStatus)

        val btnCheck = header.findViewById<android.widget.Button>(R.id.btnCheckApi)
        binding.drawerLayout.setScrimColor(Color.TRANSPARENT)
        binding.drawerLayout.setStatusBarBackgroundColor(Color.TRANSPARENT)
        binding.drawerLayout.setStatusBarBackground(null)
        binding.navigationView.setBackgroundColor(Color.parseColor("#F5F8FF"))
        binding.navigationView.itemTextColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue_glass_text))
        
        binding.drawerLayout.addDrawerListener(
                object : DrawerLayout.SimpleDrawerListener() {
                    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                        val w = binding.navigationView.width.toFloat()
                        val tx = w * slideOffset
                        binding.contentRoot.translationX = tx
                    }

                    override fun onDrawerClosed(drawerView: View) {
                        binding.contentRoot.translationX = 0f
                    }

                    override fun onDrawerOpened(drawerView: View) {
                        hideKeyboard()
                    }
                }
        )

        apiInput.post { attachAnimatedBorderRing(apiInput, 2f, 14f) }

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
                            prefs.edit()
                                    .remove(apiLastCheckKeyPref)
                                    .remove(apiLastCheckOkPref)
                                    .remove(apiLastCheckTimePref)
                                    .apply()
                            remoteApiOk = null
                            remoteApiChecking = false
                            lastCheckedApiKey = ""
                            apiStatus.text = "未检查"
                            updateStatusText()
                            return
                        }

                        prefs.edit()
                                .remove(apiLastCheckKeyPref)
                                .remove(apiLastCheckOkPref)
                                .remove(apiLastCheckTimePref)
                                .apply()
                        remoteApiOk = null
                        remoteApiChecking = false
                        lastCheckedApiKey = ""
                        apiStatus.text = "请检查API配置"
                        updateStatusText()

                        if (!apiNeedsRecheckToastShown) {
                            Toast.makeText(this@MainActivity, "请检查API配置", Toast.LENGTH_SHORT).show()
                            apiNeedsRecheckToastShown = true
                        }
                    }
                }
        )

        btnCheck.setOnClickListener {
            vibrateLight()
            val textRaw = apiInput.text.toString()
            val tagKey = (apiInput.tag as? String).orEmpty()
            val key =
                    (if (textRaw.contains("*") && textRaw == maskKey(tagKey)) tagKey else textRaw)
                            .trim()

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

            apiNeedsRecheckToastShown = false

            startApiCheck(key = key, force = true)
        }

        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_automation -> {
                    vibrateLight()
                    startActivity(android.content.Intent(this, AutomationActivityNew::class.java))
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_about -> {

                    vibrateLight()

                    startActivity(Intent(this, AboutActivity::class.java))

                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
            }

            true
        }
    }

    private fun restoreApiKey() {

        val saved = prefs.getString("api_key", "") ?: ""

        val header = binding.navigationView.getHeaderView(0)

        val apiInput = header.findViewById<android.widget.EditText>(R.id.apiInput)
        val apiStatus = header.findViewById<TextView>(R.id.apiStatus)

        apiInput.tag = saved
        suppressApiInputWatcher = true
        apiInput.setText(maskKey(saved))
        suppressApiInputWatcher = false

        if (saved.isBlank()) {
            apiStatus.text = "未检查"
            remoteApiOk = null
            remoteApiChecking = false
            updateStatusText()
            return
        }

        val lastKey = prefs.getString(apiLastCheckKeyPref, "").orEmpty().trim()
        val hasLast = prefs.contains(apiLastCheckOkPref)
        if (hasLast && lastKey.isNotBlank() && lastKey == saved.trim()) {
            val ok = prefs.getBoolean(apiLastCheckOkPref, false)
            remoteApiOk = ok
            remoteApiChecking = false
            lastCheckedApiKey = saved
            apiStatus.text = if (ok) "API 可用" else "API 检查失败"
            updateStatusText()
            return
        }

        remoteApiOk = null
        remoteApiChecking = false
        lastCheckedApiKey = saved
        apiStatus.text = "未检查"
        updateStatusText()
    }

    private fun startApiCheck(key: String, force: Boolean) {
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
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { AutoGlmClient.checkApi(k) }
            if (seq != apiCheckSeq) return@launch

            remoteApiChecking = false
            remoteApiOk = ok
            apiStatus.text = if (ok) "API 可用" else "API 检查失败"
            prefs.edit()
                    .putString(apiLastCheckKeyPref, k)
                    .putBoolean(apiLastCheckOkPref, ok)
                    .putLong(apiLastCheckTimePref, System.currentTimeMillis())
                    .apply()
            updateStatusText()
        }
    }

    private fun updateStatusText() {
        val text =
                when {
                    remoteApiChecking && offlineModelReady -> "已配置语音模型 | API 检查中..."
                    remoteApiChecking -> "检查中..."
                    remoteApiOk == true && offlineModelReady -> "已连接模型 | 语音模型已就绪"
                    remoteApiOk == true -> "已连接模型"
                    remoteApiOk == false && offlineModelReady -> "未连接 | 语音模型已就绪"
                    remoteApiOk == false -> "未连接"
                    offlineModelReady -> "语音模型已就绪"
                    else -> getString(R.string.status_disconnected)
                }
        binding.statusText.text = text
    }

    private fun maskKey(raw: String): String {
        if (raw.isBlank()) return ""
        return if (raw.length <= 6) raw else raw.substring(0, 6) + "*".repeat(raw.length - 6)
    }

    /** 轻微震动反馈 */
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

    private fun setupInputBar() {

        binding.btnSend.setOnClickListener {
            vibrateLight()
            if (isListening || voiceInputAnimJob != null) {
                pendingSendAfterVoice = true
                hideKeyboard()
                stopLocalVoiceInput(triggerRecognizerStop = true)
                return@setOnClickListener
            }

            val text = binding.inputMessage.text.toString().trim()

            if (text.isBlank()) {

                Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()

                return@setOnClickListener
            }

            hideKeyboard()
            sendMessage(text)
        }

        binding.btnVoice.setOnClickListener {
            vibrateLight()
            if (isListening) {

                stopLocalVoiceInput()
            } else {

                ensureAudioPermission { startLocalVoiceInput() }
            }
        }

        binding.inputMessage.addTextChangedListener(
                object : TextWatcher {

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {

                        binding.inputBar.requestLayout()
                    }

                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun afterTextChanged(s: Editable?) {}
                }
        )

        // 部分设备从后台返回后首次点击输入框不弹出键盘，这里在触摸时主动请求焦点并唤起软键盘
        binding.inputMessage.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN && !binding.inputMessage.isFocused) {
                binding.inputMessage.requestFocus()
                binding.inputMessage.post {
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.inputMessage, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            // 返回 false 让 EditText 继续处理点击（光标、选择等）
            false
        }
    }

    private fun hideKeyboard() {

        val imm =
                getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager

        imm.hideSoftInputFromWindow(binding.inputMessage.windowToken, 0)

        binding.inputMessage.clearFocus()
    }

    private fun elevateAiBar() {

        val aiBar = binding.topAppBar

        aiBar.elevation = 12f

        aiBar.setBackgroundResource(R.drawable.rounded_top_bar_bg)

        val params = aiBar.layoutParams as LinearLayout.LayoutParams

        params.topMargin = resources.getDimensionPixelSize(R.dimen.ai_bar_margin_top)

        aiBar.layoutParams = params
    }

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
            if (imeVisible && !lastImeVisible) {
                binding.scrollArea.post {
                    binding.scrollArea.fullScroll(android.view.View.FOCUS_DOWN)
                }
            }
            lastImeVisible = imeVisible

            insets
        }

        val nav = binding.navigationView
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
        val now = System.currentTimeMillis()
        val c = Conversation(id = now, title = "", messages = mutableListOf(), updatedAt = now)
        conversations.add(0, c)
        activeConversation = c
        if (clearUi) {
            binding.messagesContainer.removeAllViews()
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
        for (m in conversation.messages) {
            // 历史消息全部使用新的复杂气泡（如果是AI），确保视觉风格统一
            if (!m.isUser) {
                // 无论是包含 <think> 还是普通消息，都使用 appendComplexAiMessage
                // 使用 animate = false 立即显示
                appendComplexAiMessage(m.author, m.content, animate = false, timeCostMs = 0)
            } else {
                appendComplexUserMessage(m.author, m.content, animate = false)
            }
        }
        
        // 渲染完后滚动到底部
        binding.messagesContainer.post {
            (binding.messagesContainer.parent as? android.widget.ScrollView)?.fullScroll(
                android.view.View.FOCUS_DOWN
            )
        }
    }

    private fun sendMessage(text: String, resendUser: Boolean = true) {

        if (isRequestInFlight) {
            Toast.makeText(this, "正在生成回复，请稍后…", Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = prefs.getString("api_key", "") ?: ""

        if (apiKey.isBlank()) {

            Toast.makeText(this, "请先在边栏配置 API Key", Toast.LENGTH_SHORT).show()

            binding.drawerLayout.openDrawer(GravityCompat.START)

            return
        }

        val c = requireActiveConversation()
        if (c.title.isBlank()) {
            c.title = text.take(18)
        }
        
        if (resendUser) {
            c.messages.add(UiMessage(author = "我", content = text, isUser = true))
            c.updatedAt = System.currentTimeMillis()
            persistConversations()

            appendComplexUserMessage("我", text, animate = true)
            
            // 同步消息到悬浮窗（如果运行中）
            if (FloatingChatService.isRunning()) {
                FloatingChatService.getInstance()?.addMessage("我: $text", isUser = true)
            }

            binding.inputMessage.text?.clear()
        }

        showThinking()
        
        val startTime = System.currentTimeMillis()

        isRequestInFlight = true
        lifecycleScope.launch {
            try {
                // 【修复】构建完整的对话历史，让AI能看到上下文
                val chatHistory = buildChatHistory(c)

                var reply: String? = null
                var lastError: Throwable? = null
                val maxAttempts = 2
                for (attempt in 1..maxAttempts) {
                    val result =
                        runCatching {
                            withContext(Dispatchers.IO) {
                                AutoGlmClient.sendChat(
                                    apiKey = apiKey,
                                    messages = chatHistory
                                )
                            }
                        }
                    reply = result.getOrNull()
                    if (!reply.isNullOrBlank()) break
                    lastError = result.exceptionOrNull()
                    if (attempt < maxAttempts) delay(250L * attempt)
                }
                        
                val timeCost = System.currentTimeMillis() - startTime

                val finalReply =
                    if (!reply.isNullOrBlank()) {
                        reply!!
                    } else {
                        val hint = lastError?.message?.take(60)?.trim().orEmpty()
                        if (hint.isNotBlank()) "（请求失败：$hint）" else "（请求失败，请稍后点击重试）"
                    }

                val cc = requireActiveConversation()
                cc.messages.add(UiMessage(author = "Aries", content = finalReply, isUser = false))
                cc.updatedAt = System.currentTimeMillis()
                persistConversations()

                removeThinking()
                
                // 【优化】使用复杂布局显示 AI 回复（支持思考过程和流式动画）
                appendComplexAiMessage("Aries", finalReply, animate = true, timeCostMs = timeCost)
                
                // 同步 AI 回复到悬浮窗（如果运行中）
                if (FloatingChatService.isRunning()) {
                    FloatingChatService.getInstance()?.addMessage("Aries: $finalReply", isUser = false)
                }
            } finally {
                isRequestInFlight = false
            }
        }
    }
    
    /**
     * 构建完整的对话历史，传递给AI模型
     * 包含系统提示和最近的对话上下文
     */
    private fun buildChatHistory(conversation: Conversation): List<ChatRequestMessage> {
        val history = mutableListOf<ChatRequestMessage>()
        
        // 添加系统提示
        history.add(ChatRequestMessage(
            role = "system",
            content = "你是Aries AI，一个全能AI助手，旨在解决用户提出的任何任务。请用简洁、友好的方式回复用户。如果问题较复杂，请先进行思考，思考过程用 <think>...</think> 包裹，然后再给出最终答复。"
        ))
        
        // 添加对话历史（最多保留最近10轮对话，避免上下文过长）
        val recentMessages = conversation.messages.takeLast(20) // 10轮对话 = 20条消息
        for (msg in recentMessages) {
            val content = if (!msg.isUser) {
                // 历史记录传递给模型时，如果包含 <think>，是否保留？
                // 通常保留可以让模型知道之前的思考逻辑，但也可能浪费 token。
                // 这里选择保留完整内容。
                msg.content
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
        timeCostMs: Long
    ) {
        // 1. Inflate 复杂布局
        val view = layoutInflater.inflate(R.layout.item_ai_message_complex, binding.messagesContainer, false)
        binding.messagesContainer.addView(view)
        
        val thinkingLayout = view.findViewById<LinearLayout>(R.id.thinking_layout)
        val thinkingHeader = view.findViewById<LinearLayout>(R.id.thinking_header)
        val thinkingText = view.findViewById<TextView>(R.id.thinking_text)
        val thinkingIndicator = view.findViewById<TextView>(R.id.thinking_indicator_text)
        val messageContent = view.findViewById<TextView>(R.id.message_content)
        val authorName = view.findViewById<TextView>(R.id.ai_author_name)
        
        // 解析内容
        val thinkRegex = "<think>([\\s\\S]*?)</think>([\\s\\S]*)".toRegex()
        val match = thinkRegex.find(fullContent)
        
        val thinkContent = match?.groupValues?.get(1)?.trim()
        val realContent = match?.groupValues?.get(2)?.trim() ?: fullContent
        
        // 设置作者名
        authorName.text = author
        authorName.visibility = View.VISIBLE
        
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
        
        smoothScrollToBottom()

        if (!animate) {
            // 无动画直接显示
            if (!thinkContent.isNullOrBlank()) thinkingText.text = thinkContent
            messageContent.text = realContent
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
            // 复制时是否包含思考过程？DeepSeek 通常只复制正文
            val clip = android.content.ClipData.newPlainText("AI Reply", realContent)
            cm.setPrimaryClip(clip)
            Toast.makeText(this@MainActivity, "已复制内容", Toast.LENGTH_SHORT).show()
        }

        val btnRetry = view.findViewById<View>(R.id.btn_retry)
        btnRetry.setOnClickListener {
            // 重试逻辑：获取上一条用户消息，重新发送
            val c = activeConversation
            if (c != null && c.messages.isNotEmpty()) {
                val lastUserMsg = c.messages.findLast { it.isUser }
                if (lastUserMsg != null) {
                    sendMessage(lastUserMsg.content, resendUser = false)
                }
            }
        }
        
        if (!animate) {
            // 如果非动画模式（如历史记录），直接显示操作栏
            view.findViewById<View>(R.id.action_area).visibility = View.VISIBLE
        }
    }
    
    /**
     * 丝滑滚动到底部
     */
    private fun smoothScrollToBottom() {
        binding.messagesContainer.post {
            val scrollView = binding.messagesContainer.parent as? android.widget.ScrollView ?: return@post
            // 检查是否需要滚动：如果已经在底部附近，则跟随滚动
            val viewHeight = binding.messagesContainer.height
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
     * 用户消息复杂气泡：淡水蓝背景，右侧对齐，并与底部输入栏左右边界保持一致。
     */
    private fun appendComplexUserMessage(author: String, content: String, animate: Boolean) {
        val bubble = layoutInflater.inflate(R.layout.item_user_message_complex, binding.messagesContainer, false)
        val tv = bubble.findViewById<TextView>(R.id.message_content)
        val authorTv = bubble.findViewById<TextView>(R.id.user_author_name)
        authorTv.text = author
        authorTv.visibility = View.GONE

        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        // 用 row 容器把气泡贴到右侧（row 宽度 match_parent）
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        row.addView(
            bubble,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // 左侧留出空间制造“对话层次”，右侧贴边由 row + ScrollView padding 保证
                setMargins(dp(48), dp(8), 0, dp(8))
            }
        )

        binding.messagesContainer.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        smoothScrollToBottom()

        if (!animate) {
            tv.text = content
            return
        }

        lifecycleScope.launch {
            val sb = StringBuilder()
            val chunkSize = 2
            var idx = 0
            while (idx < content.length) {
                val end = minOf(idx + chunkSize, content.length)
                sb.append(content.substring(idx, end))
                tv.text = sb.toString()
                idx = end
                smoothScrollToBottom()
                delay(10)
            }
            tv.text = content
        }
    }
    
    /**
     * 【优化】使用StringBuilder批量更新方式显示消息
     * 参考Operit的groupBy算法，每次收到一点就拼接，然后刷新整个文本
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
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.blue_glass_text))
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

        binding.messagesContainer.post {
            (binding.messagesContainer.parent as? android.widget.ScrollView)?.fullScroll(
                    android.view.View.FOCUS_DOWN
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
                    '。', '！', '？', '.', '!', '?' -> 80L
                    '，', '、', '；', ',', ';', '：', ':' -> 50L
                    '\n' -> 60L
                    else -> 25L
                }
                delay(delayMs)
            }
            
            // 最终滚动到底部
            binding.messagesContainer.post {
                (binding.messagesContainer.parent as? android.widget.ScrollView)?.fullScroll(
                        android.view.View.FOCUS_DOWN
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
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.blue_glass_text))
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
        binding.messagesContainer.post {
            (binding.messagesContainer.parent as? android.widget.ScrollView)?.fullScroll(
                    android.view.View.FOCUS_DOWN
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

                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.blue_glass_text))
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

        binding.messagesContainer.post {
            (binding.messagesContainer.parent as? android.widget.ScrollView)?.fullScroll(
                    android.view.View.FOCUS_DOWN
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

        authorName.text = "Aries"
        authorName.visibility = View.VISIBLE
        thinkingLayout.visibility = View.GONE
        actionArea.visibility = View.GONE

        messageContent.text = "正在思考"
        messageContent.setTextColor(Color.parseColor("#80505050"))

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
        binding.messagesContainer.post {
            (binding.messagesContainer.parent as? android.widget.ScrollView)?.fullScroll(
                    android.view.View.FOCUS_DOWN
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
                Toast.makeText(this@MainActivity, "本地语音模型已就绪 (Sherpa-ncnn)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "语音模型初始化失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 启动"正在语音输入..."的点动画 */
    private fun startVoiceInputAnimation() {
        voiceInputAnimJob?.cancel()
        savedInputText = binding.inputMessage.text?.toString().orEmpty()
        voiceInputAnimJob = lifecycleScope.launch {
            var dotCount = 1
            while (true) {
                val dots = ".".repeat(dotCount)
                binding.inputMessage.setText("正在语音输入$dots")
                binding.inputMessage.setSelection(binding.inputMessage.text?.length ?: 0)
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

    private fun startLocalVoiceInput() {
        val recognizer = sherpaSpeechRecognizer
        if (recognizer == null || !recognizer.isReady()) {
            Toast.makeText(this, "模型加载中…", Toast.LENGTH_SHORT).show()
            return
        }

        if (isListening) return

        voicePrefix = binding.inputMessage.text?.toString().orEmpty().trim().let { prefix ->
            if (prefix.isBlank()) "" else if (prefix.endsWith(" ")) prefix else "$prefix "
        }

        // 开始"正在语音输入..."动画
        startVoiceInputAnimation()

        recognizer.startListening(object : SherpaSpeechRecognizer.RecognitionListener {
            override fun onPartialResult(text: String) {
                runOnUiThread {
                    // 有识别结果时，停止动画并显示实际文字
                    stopVoiceInputAnimation()
                    val txt = (voicePrefix + text).trimStart()
                    binding.inputMessage.setText(txt)
                    binding.inputMessage.setSelection(binding.inputMessage.text?.length ?: 0)
                }
            }

            override fun onResult(text: String) {
                runOnUiThread {
                    stopVoiceInputAnimation()
                    val txt = (voicePrefix + text).trimStart()
                    binding.inputMessage.setText(txt)
                    binding.inputMessage.setSelection(binding.inputMessage.text?.length ?: 0)
                }
            }

            override fun onFinalResult(text: String) {
                runOnUiThread {
                    stopVoiceInputAnimation()
                    val txt = (voicePrefix + text).trimStart()
                    binding.inputMessage.setText(if (txt.isBlank()) savedInputText else txt)
                    binding.inputMessage.setSelection(binding.inputMessage.text?.length ?: 0)

                    val shouldSend = pendingSendAfterVoice
                    pendingSendAfterVoice = false
                    stopLocalVoiceInput(triggerRecognizerStop = false)

                    if (shouldSend) {
                        val toSend = binding.inputMessage.text.toString().trim()
                        if (toSend.isBlank()) {
                            Toast.makeText(this@MainActivity, "请输入内容", Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }
                        hideKeyboard()
                        sendMessage(toSend)
                    }
                }
            }

            override fun onError(exception: Exception) {
                runOnUiThread {
                    stopVoiceInputAnimation()
                    // 恢复原来的文字
                    binding.inputMessage.setText(savedInputText)
                    Toast.makeText(this@MainActivity, "识别失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                    pendingSendAfterVoice = false
                    stopLocalVoiceInput(triggerRecognizerStop = false)
                }
            }

            override fun onTimeout() {
                runOnUiThread {
                    stopVoiceInputAnimation()
                    // 恢复原来的文字
                    binding.inputMessage.setText(savedInputText)
                    Toast.makeText(this@MainActivity, "语音识别超时", Toast.LENGTH_SHORT).show()
                    pendingSendAfterVoice = false
                    stopLocalVoiceInput(triggerRecognizerStop = false)
                }
            }
        })

        isListening = true
        startMicAnimation()
    }

    private fun stopLocalVoiceInput(triggerRecognizerStop: Boolean = true) {
        val recognizer = sherpaSpeechRecognizer
        stopVoiceInputAnimation()

        val currentText = binding.inputMessage.text?.toString().orEmpty()
        if (currentText.startsWith("正在语音输入")) {
            binding.inputMessage.setText(savedInputText)
            binding.inputMessage.setSelection(binding.inputMessage.text?.length ?: 0)
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
        stopMicAnimation()
    }

    private fun startMicAnimation() {

        if (micAnimator != null) return

        val sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f)

        val sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f)

        val a = PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.75f)

        micAnimator =
                ObjectAnimator.ofPropertyValuesHolder(binding.btnVoice, sx, sy, a).apply {
                    duration = 520

                    repeatCount = ObjectAnimator.INFINITE

                    repeatMode = ObjectAnimator.REVERSE

                    interpolator = LinearInterpolator()

                    start()
                }
    }

    private fun stopMicAnimation() {

        micAnimator?.cancel()

        micAnimator = null

        binding.btnVoice.scaleX = 1f

        binding.btnVoice.scaleY = 1f

        binding.btnVoice.alpha = 1f
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
        val displayed = conversations.filter { it.messages.isNotEmpty() }.toMutableList()
        if (displayed.isEmpty()) {
            Toast.makeText(this, "暂无历史对话", Toast.LENGTH_SHORT).show()
            return
        }

        val contentView = layoutInflater.inflate(R.layout.dialog_history, null)
        val rv = contentView.findViewById<RecyclerView>(R.id.rvHistory)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter =
                ConversationAdapter(
                        items = displayed,
                        onClick = { conv ->
                            activeConversation = conv
                            renderConversation(conv)
                            persistConversations()
                        }
                )
        rv.adapter = adapter

        val helper =
                ItemTouchHelper(
                        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                            override fun onMove(
                                    recyclerView: RecyclerView,
                                    viewHolder: RecyclerView.ViewHolder,
                                    target: RecyclerView.ViewHolder
                            ): Boolean = false

                            override fun onSwiped(
                                    viewHolder: RecyclerView.ViewHolder,
                                    direction: Int
                            ) {
                                val pos = viewHolder.bindingAdapterPosition
                                if (pos == RecyclerView.NO_POSITION) return
                                val removed = displayed.removeAt(pos)
                                conversations.removeAll { it.id == removed.id }

                                if (activeConversation?.id == removed.id) {
                                    activeConversation = null
                                    startNewChat(clearUi = true)
                                }
                                adapter.notifyItemRemoved(pos)
                                persistConversations()
                            }
                        }
                )
        helper.attachToRecyclerView(rv)

        val dialog =
                AlertDialog.Builder(this)
                        .setTitle("历史对话")
                        .setView(contentView)
                        .setNegativeButton("关闭", null)
                        .create()

        adapter.onItemSelected = { dialog.dismiss() }
        dialog.show()
    }

    private class ConversationAdapter(
            private val items: List<Conversation>,
            private val onClick: (Conversation) -> Unit,
    ) : RecyclerView.Adapter<ConversationAdapter.VH>() {

        var onItemSelected: (() -> Unit)? = null

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.tvTitle)
            val subtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v =
                    android.view.LayoutInflater.from(parent.context)
                            .inflate(R.layout.item_conversation, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            holder.title.text = if (c.title.isBlank()) "（未命名对话）" else c.title
            val last = c.messages.lastOrNull()?.content.orEmpty()
            holder.subtitle.text = last.take(28)
            holder.itemView.setOnClickListener {
                onClick(c)
                onItemSelected?.invoke()
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun attachAnimatedRing(target: View, strokeDp: Float) {
        val strokePx = strokeDp * target.resources.displayMetrics.density
        val blue = Color.rgb(63, 169, 255)
        val cyan = Color.rgb(160, 235, 255)
        val pink = Color.rgb(255, 90, 210)
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
                        val cx = bounds.left + w / 2f
                        val cy = bounds.top + h / 2f
                        val r = (minOf(w, h) / 2f) - maxHalf
                        if (r <= 0f) return
                        if (shader == null || cx != shaderCx || cy != shaderCy) {
                            shader =
                                    SweepGradient(cx, cy, intArrayOf(Color.argb(255, Color.red(blue), Color.green(blue), Color.blue(blue)), Color.argb(240, Color.red(cyan), Color.green(cyan), Color.blue(cyan)), Color.argb(220, Color.red(blue), Color.green(blue), Color.blue(blue)), Color.argb(220, Color.red(pink), Color.green(pink), Color.blue(pink)), Color.argb(150, Color.red(pink), Color.green(pink), Color.blue(pink)), Color.argb(60, Color.red(pink), Color.green(pink), Color.blue(pink)), Color.argb(0, Color.red(blue), Color.green(blue), Color.blue(blue)), Color.argb(0, Color.red(blue), Color.green(blue), Color.blue(blue))), floatArrayOf(0f, 0.08f, 0.14f, 0.22f, 0.30f, 0.40f, 0.48f, 1f))
                            shaderCx = cx
                            shaderCy = cy
                        }
                        shader?.let {
                            shaderMatrix.setRotate(angle, cx, cy)
                            it.setLocalMatrix(shaderMatrix)
                            glowPaint.shader = it
                            corePaint.shader = it
                        }
                        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
                        canvas.drawOval(oval, glowPaint)
                        canvas.drawOval(oval, corePaint)
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
                    angle = start + (it.animatedValue as Float)
                    drawable.invalidateSelf()
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
                    angle = start + (it.animatedValue as Float)
                    drawable.invalidateSelf()
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
        val blue = Color.rgb(63, 169, 255)
        val cyan = Color.rgb(160, 235, 255)
        val pink = Color.rgb(255, 90, 210)
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
                            glowPaint.shader = it
                            corePaint.shader = it
                        }
                        val half = maxHalf
                        val rect =
                                RectF(
                                        bounds.left + half,
                                        bounds.top + half,
                                        bounds.right - half,
                                        bounds.bottom - half
                                )
                        canvas.drawRoundRect(rect, cornerPx, cornerPx, glowPaint)
                        canvas.drawRoundRect(rect, cornerPx, cornerPx, corePaint)
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
                    angle = start + (it.animatedValue as Float)
                    drawable.invalidateSelf()
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
                    angle = start + (it.animatedValue as Float)
                    drawable.invalidateSelf()
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

        stopLocalVoiceInput()
    }

    override fun onDestroy() {

        super.onDestroy()
        
        // 清除消息同步监听器，防止内存泄漏
        FloatingChatService.setMessageSyncListener(null)

        stopLocalVoiceInput()

        sherpaSpeechRecognizer?.shutdown()

        sherpaSpeechRecognizer = null
    }
}