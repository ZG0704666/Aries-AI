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

    private var thinkingView: TextView? = null

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

    override fun onResume() {
        super.onResume()
        restoreApiKey()
        maybeShowPermissionBottomSheet()
        
        // 设置消息同步监听器
        setupMessageSyncListener()
        
        // 检查是否从悬浮窗返回，播放入场动画并同步消息
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
                    val content = message.removePrefix("我: ").removePrefix("AI: ")
                    val author = if (isUser) "我" else "AI"
                    
                    // 检查是否已存在该消息（避免重复）
                    val exists = c.messages.any { it.content == content && it.isUser == isUser }
                    if (!exists) {
                        c.messages.add(UiMessage(author = author, content = content, isUser = isUser))
                        c.updatedAt = System.currentTimeMillis()
                        appendMessageInstant(author, content, isUser)
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
        
        // 同步悬浮窗中的消息到主界面
        syncMessagesFromFloatingWindow()
        
        val fromX = intent?.getIntExtra("from_x", 0) ?: 0
        val fromY = intent?.getIntExtra("from_y", 0) ?: 0
        val fromWidth = intent?.getIntExtra("from_width", 0) ?: 0
        val fromHeight = intent?.getIntExtra("from_height", 0) ?: 0
        
        if (fromWidth <= 0 || fromHeight <= 0) return
        
        // 播放从小窗展开的动画
        playExpandFromMiniWindowAnimation(fromX.toFloat(), fromY.toFloat(), fromWidth.toFloat(), fromHeight.toFloat())
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
        for (msg in floatingMessages!!) {
            val isUser = msg.startsWith("我:")
            val content = msg.removePrefix("我: ").removePrefix("AI: ").trim()
            
            // 跳过空消息和"思考中"消息
            if (content.isBlank() || content == "思考中...") continue
            
            // 检查是否已存在该消息
            val exists = c.messages.any { it.content == content && it.isUser == isUser }
            if (!exists) {
                val author = if (isUser) "我" else "AI"
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
     * 从小窗展开到全屏的动画
     */
    private fun playExpandFromMiniWindowAnimation(fromX: Float, fromY: Float, fromWidth: Float, fromHeight: Float) {
        val contentView = binding.contentRoot
        
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        
        // 计算初始缩放比例
        val scaleX = fromWidth / screenWidth
        val scaleY = fromHeight / screenHeight
        val scale = minOf(scaleX, scaleY)
        
        // 设置初始状态
        contentView.pivotX = fromX + fromWidth / 2
        contentView.pivotY = fromY + fromHeight / 2
        contentView.scaleX = scale
        contentView.scaleY = scale
        contentView.alpha = 0.7f
        
        // 展开动画
        contentView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator(2f))
            .setListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    contentView.pivotX = contentView.width / 2f
                    contentView.pivotY = contentView.height / 2f
                }
            })
            .start()
    }

    private fun maybeShowPermissionBottomSheet() {
        if (prefs.getBoolean(permGuideShownPref, false)) return
        if (supportFragmentManager.findFragmentByTag(PermissionBottomSheet.TAG) != null) return

        runCatching {
            PermissionBottomSheet().show(supportFragmentManager, PermissionBottomSheet.TAG)
            prefs.edit().putBoolean(permGuideShownPref, true).apply()
        }
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

        // 轻微上移导航与菜单图标，确保与标题对齐
        offsetTopBarIcons()
    }

    private fun offsetTopBarIcons() {
        // 只将小窗按钮下移1dp，其余上移7dp
        val upOffsetPx = -7f * resources.displayMetrics.density
        val floatWinOffsetPx = -6f * resources.displayMetrics.density
        binding.topAppBar.post {
            for (i in 0 until binding.topAppBar.childCount) {
                val child = binding.topAppBar.getChildAt(i)
                if (child is ActionMenuView) {
                    // ActionMenuView 里有所有右侧菜单按钮
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
                    // 左侧导航按钮
                    child.translationY = upOffsetPx
                }
            }
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
        val margin = 20 * density
        
        // 小窗位置：右下角
        val targetX = displayMetrics.widthPixels - miniWindowWidth - margin
        val targetY = displayMetrics.heightPixels - miniWindowHeight - margin - 100 * density
        
        // 计算目标缩放比例
        val scaleX = miniWindowWidth / displayMetrics.widthPixels
        val scaleY = miniWindowHeight / displayMetrics.heightPixels
        val targetScale = minOf(scaleX, scaleY)
        
        // 计算小窗中心点作为缩放中心
        val targetCenterX = targetX + miniWindowWidth / 2
        val targetCenterY = targetY + miniWindowHeight / 2
        
        // 设置缩放中心点为小窗目标中心
        contentView.pivotX = targetCenterX
        contentView.pivotY = targetCenterY
        
        // 收集当前聊天消息传递给小窗
        val messagesList = ArrayList<String>()
        activeConversation?.messages?.forEach { msg ->
            messagesList.add("${if (msg.isUser) "我" else "AI"}: ${msg.content}")
        }
        
        // 使用 ValueAnimator 实现更平滑的非线性动画
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 380
        // 使用弹性减速曲线，类似微信语音通话效果
        animator.interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        
        val startScaleX = 1f
        val startScaleY = 1f
        val startAlpha = 1f
        val endAlpha = 0f
        
        animator.addUpdateListener { anim ->
            val progress = anim.animatedValue as Float
            // 使用 easeOutExpo 曲线增强减速效果
            val easedProgress = 1 - Math.pow(1.0 - progress.toDouble(), 3.0).toFloat()
            
            contentView.scaleX = startScaleX + (targetScale - startScaleX) * easedProgress
            contentView.scaleY = startScaleY + (targetScale - startScaleY) * easedProgress
            contentView.alpha = startAlpha + (endAlpha - startAlpha) * easedProgress
            
            // 添加轻微的旋转效果增加灵动感
            contentView.rotation = 2f * progress * (1 - progress)
        }
        
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // 启动悬浮窗服务，传递消息和位置
                FloatingChatService.start(
                    this@MainActivity,
                    messages = messagesList,
                    fromX = targetX,
                    fromY = targetY,
                    fromWidth = miniWindowWidth,
                    fromHeight = miniWindowHeight
                )
                
                // 重置视图状态
                contentView.scaleX = 1f
                contentView.scaleY = 1f
                contentView.alpha = 1f
                contentView.rotation = 0f
                contentView.pivotX = contentView.width / 2f
                contentView.pivotY = contentView.height / 2f
                
                isAnimatingToMiniWindow = false
                
                // 最小化 Activity（移到后台）
                moveTaskToBack(true)
            }
        })
        
        animator.start()
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
                    startActivity(android.content.Intent(this, AutomationActivity::class.java))
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_about -> {

                    vibrateLight()

                    Toast.makeText(this, "Phone Agent(稳定版)", Toast.LENGTH_SHORT).show()

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
            appendMessageInstant(m.author, m.content, m.isUser)
        }
    }

    private fun sendMessage(text: String) {

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
        c.messages.add(UiMessage(author = "我", content = text, isUser = true))
        c.updatedAt = System.currentTimeMillis()
        persistConversations()

        appendMessageTyping("我", text, true)
        
        // 同步消息到悬浮窗（如果运行中）
        if (FloatingChatService.isRunning()) {
            FloatingChatService.getInstance()?.addMessage("我: $text", isUser = true)
        }

        binding.inputMessage.text?.clear()
        showThinking()

        lifecycleScope.launch {
            val reply =
                    withContext(Dispatchers.IO) {
                        AutoGlmClient.sendChat(
                                apiKey = apiKey,
                                messages = listOf(ChatRequestMessage(role = "user", content = text))
                        )
                    }

            val finalReply = reply ?: "（无回复或请求失败）"

            val cc = requireActiveConversation()
            cc.messages.add(UiMessage(author = "模型", content = finalReply, isUser = false))
            cc.updatedAt = System.currentTimeMillis()
            persistConversations()

            removeThinking()
            appendMessageTyping("模型", finalReply, false, true)
            
            // 同步 AI 回复到悬浮窗（如果运行中）
            if (FloatingChatService.isRunning()) {
                FloatingChatService.getInstance()?.addMessage("AI: $finalReply", isUser = false)
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
                                    if (isUser) R.drawable.bubble_user else R.drawable.bubble_bot
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
                                    if (isUser) R.drawable.bubble_user else R.drawable.bubble_bot
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
        val tv =
                TextView(this).apply {
                    text = "模型：正在思考"
                    setPadding(20, 12, 20, 12)
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bubble_bot)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.blue_glass_text))
                }
        val lp =
                LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        .apply {
                            setMargins(12, 8, 12, 8)
                            gravity = Gravity.START
                        }
        binding.messagesContainer.addView(tv, lp)
        thinkingView = tv
        lifecycleScope.launch {
            var n = 0
            while (thinkingView === tv) {
                val dots = ".".repeat(n % 4)
                tv.text = "模型：正在思考$dots"
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
        val tv = thinkingView ?: return
        binding.messagesContainer.removeView(tv)
        thinkingView = null
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