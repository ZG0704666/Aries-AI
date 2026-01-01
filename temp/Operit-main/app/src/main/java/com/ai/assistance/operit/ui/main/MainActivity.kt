package com.ai.assistance.operit.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.migration.ChatHistoryMigrationManager
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.data.updates.UpdateManager
import com.ai.assistance.operit.data.updates.UpdateStatus
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.agreement.screens.AgreementScreen
import com.ai.assistance.operit.ui.features.migration.screens.MigrationScreen
import com.ai.assistance.operit.ui.features.permission.screens.PermissionGuideScreen
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingScreenWithState
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingState
import com.ai.assistance.operit.ui.theme.OperitTheme
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.util.AnrMonitor
import com.ai.assistance.operit.util.LocaleUtils
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    // ======== 屏幕方向变更状态 ========
    private var showOrientationChangeDialog by mutableStateOf(false)

    private var lastOrientation: Int? = null

    // ======== 工具和管理器 ========
    private lateinit var toolHandler: AIToolHandler
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var agreementPreferences: AgreementPreferences
    private var updateCheckPerformed = false
    private lateinit var anrMonitor: AnrMonitor
    private lateinit var mcpRepository: MCPRepository

    // ======== 导航状态 ========
    private var showPreferencesGuide by mutableStateOf(false)

    // ======== MCP插件状态 ========
    private val pluginLoadingState = PluginLoadingState()

    // ======== 数据迁移状态 ========
    private lateinit var migrationManager: ChatHistoryMigrationManager
    private var showMigrationScreen by mutableStateOf(false)

    // ======== 双击返回退出相关变量 ========
    private var backPressedTime: Long = 0
    private val backPressedInterval: Long = 2000 // 两次点击的时间间隔，单位为毫秒

    // UpdateManager实例
    private lateinit var updateManager: UpdateManager

    // 是否显示权限引导界面
    private var showPermissionGuide by mutableStateOf(false)

    // 是否已完成权限和迁移检查
    private var initialChecksDone = false

    // 存储待处理的分享文件URIs
    private var pendingSharedFileUris: List<Uri>? = null

    // 通知权限请求启动器
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            AppLogger.d(TAG, "通知权限已授予")
        } else {
            AppLogger.d(TAG, "通知权限被拒绝")
            Toast.makeText(this, getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    private fun handleGitHubOAuthCode(code: String) {
        lifecycleScope.launch {
            try {
                val githubApiService = GitHubApiService(this@MainActivity)
                val githubAuth = GitHubAuthPreferences.getInstance(this@MainActivity)

                val tokenResult = githubApiService.getAccessToken(code)
                tokenResult.fold(
                    onSuccess = { tokenResponse ->
                        githubAuth.updateAccessToken(tokenResponse.access_token, tokenResponse.token_type)

                        val userResult = githubApiService.getCurrentUser()
                        userResult.fold(
                            onSuccess = { user ->
                                githubAuth.saveAuthInfo(
                                    accessToken = tokenResponse.access_token,
                                    tokenType = tokenResponse.token_type,
                                    userInfo = user
                                )
                                Toast.makeText(
                                    this@MainActivity,
                                    "登录成功，欢迎 ${user.login}！",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onFailure = { error ->
                                AppLogger.e(TAG, "Failed to get user info", error)
                                Toast.makeText(
                                    this@MainActivity,
                                    "获取用户信息失败: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    },
                    onFailure = { error ->
                        AppLogger.e(TAG, "Failed to get access token", error)
                        Toast.makeText(
                            this@MainActivity,
                            "登录失败: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Exception during GitHub callback handling", e)
                Toast.makeText(
                    this@MainActivity,
                    "登录过程中发生错误: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // 获取当前设置的语言
        val code = LocaleUtils.getCurrentLanguage(newBase)
        val locale = Locale(code)
        val config = Configuration(newBase.resources.configuration)

        // 设置语言配置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            Locale.setDefault(locale)
        }

        // 使用createConfigurationContext创建新的本地化上下文
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
        AppLogger.d(TAG, "MainActivity应用语言设置: $code")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastOrientation = resources.configuration.orientation
        AppLogger.d(TAG, "onCreate: Android SDK version: ${Build.VERSION.SDK_INT}")

        // Set window background to solid color to prevent system theme leaking through
        window.setBackgroundDrawableResource(android.R.color.black)

        // Handle the intent that started the activity
        handleIntent(intent)

        // 语言设置已在Application中初始化，这里无需重复

        initializeComponents()
        cleanTemporaryFiles()
        anrMonitor.start()
        setupPreferencesListener()
        configureDisplaySettings()

        // 设置上下文以便获取插件元数据
        pluginLoadingState.setAppContext(applicationContext)

        // 设置跳过加载的回调
        pluginLoadingState.setOnSkipCallback {
            AppLogger.d(TAG, "用户跳过了插件加载过程")
            Toast.makeText(this, getString(R.string.plugin_loading_skipped), Toast.LENGTH_SHORT).show()
        }

        // 设置初始界面 - 显示加载占位符
        setAppContent()

        // 初始化并设置更新管理器
        setupUpdateManager()

        // 只在首次创建时执行检查（非配置变更）
        if (savedInstanceState == null) {
            // 进行必要的初始检查
            performInitialChecks()
        } else {
            // 配置变更时不重新检查，直接显示主界面
            initialChecksDone = true
        }

        // 设置双击返回退出
        setupBackPressHandler()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // 重要：更新当前Intent
        AppLogger.d(TAG, "onNewIntent: Received intent with action: ${intent?.action}")
        val isGitHubOAuthCallback = intent?.data?.let { uri ->
            uri.scheme == "operit" && uri.host == "github-oauth-callback"
        } == true
        handleIntent(intent)

        if (isGitHubOAuthCallback) {
            return
        }
        
        // 如果是文件分享，立即处理
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND) {
            processPendingSharedFiles()
        }
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "operit" && uri.host == "github-oauth-callback") {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                AppLogger.d(TAG, "GitHub OAuth code received from onCreate: $code")
                handleGitHubOAuthCode(code)
            } else {
                val error = uri.getQueryParameter("error")
                AppLogger.e(TAG, "GitHub OAuth error from onCreate: $error")
            }
            return
        }
        
        // Handle opened and shared files
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                // Handle "Open with" action
                intent.data?.let { uri ->
                    pendingSharedFileUris = listOf(uri)
                    AppLogger.d(TAG, "Received file to open: $uri")
                }
            }
            Intent.ACTION_SEND -> {
                // Handle "Share" action
                @Suppress("DEPRECATION")
                val uri = if (Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                uri?.let {
                    pendingSharedFileUris = listOf(it)
                    AppLogger.d(TAG, "Received shared file: $it")
                }
            }
        }
    }

    // ======== 设置初始占位内容 ========

    // ======== 执行初始化检查 ========
    private fun performInitialChecks() {
        lifecycleScope.launch {
            // 1. 检查通知权限（Android 13+）
            checkNotificationPermission()

            // 2. 检查权限级别设置
            checkPermissionLevelSet()

            // 3. 检查是否需要数据迁移
            if (!showPermissionGuide && agreementPreferences.isAgreementAccepted()) {
                try {
                    val needsMigration = migrationManager.needsMigration()
                    AppLogger.d(TAG, "数据迁移检查: 需要迁移=$needsMigration")

                    showMigrationScreen = needsMigration

                    // 如果不需要迁移，直接启动插件加载
                    if (!needsMigration) {
                        startPluginLoading()
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "数据迁移检查失败", e)
                    // 检查失败，跳过迁移直接加载插件
                    startPluginLoading()
                }
            }

            // 标记完成初始检查
            initialChecksDone = true

            // 设置应用内容
            setAppContent()
        }
    }

    // ======== 检查数据迁移 ========
    private fun checkMigrationNeeded() {
        lifecycleScope.launch {
            try {
                // 检查是否需要迁移数据
                val needsMigration = migrationManager.needsMigration()
                AppLogger.d(TAG, "数据迁移检查: 需要迁移=$needsMigration")

                if (needsMigration) {
                    showMigrationScreen = true
                    setAppContent()
                } else {
                    // 不需要迁移，显示插件加载界面
                    startPluginLoading()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "数据迁移检查失败", e)
                // 检查失败，跳过迁移直接加载插件
                startPluginLoading()
            }
        }
    }

    // ======== 启动插件加载 ========
    private fun startPluginLoading() {
        // 显示插件加载界面
        pluginLoadingState.show()

        // 启动超时检测（30秒）
        pluginLoadingState.startTimeoutCheck(30000L, lifecycleScope)

        // 初始化MCP服务器并启动插件
        // 轻微延迟让首帧 Compose 完成，避免启动阶段后台重任务立刻抢占导致掉帧
        lifecycleScope.launch {
            delay(500)
            pluginLoadingState.initializeMCPServer(applicationContext, lifecycleScope)
        }
    }

    // ======== 处理待处理的分享文件 ========
    private fun processPendingSharedFiles() {
        val uris = pendingSharedFileUris
        if (uris == null) {
            AppLogger.d(TAG, "No pending shared files to process")
            return
        }
        
        AppLogger.d(TAG, "Processing ${uris.size} pending shared file(s)")
        uris.forEachIndexed { index, uri ->
            AppLogger.d(TAG, "  [$index] URI: $uri")
        }
        
        lifecycleScope.launch {
            try {
                // Pass the URIs to the chat screen via SharedFileHandler
                SharedFileHandler.setSharedFiles(uris)
                AppLogger.d(TAG, "Successfully passed shared files to SharedFileHandler")
                pendingSharedFileUris = null
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to process shared files", e)
                Toast.makeText(
                    this@MainActivity,
                    "处理分享文件失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                pendingSharedFileUris = null
            }
        }
    }

    // 配置双击返回退出的处理器
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - backPressedTime > backPressedInterval) {
                            // 第一次点击，显示提示
                            backPressedTime = currentTime
                            Toast.makeText(this@MainActivity, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
                        } else {
                            // 第二次点击，退出应用
                            finish()
                        }
                    }
                }
        )
    }


    override fun onDestroy() {
        super.onDestroy()
        AppLogger.d(TAG, "onDestroy called")

        // 确保隐藏加载界面
        pluginLoadingState.hide()

        // 主界面销毁时，确保关闭虚拟屏幕 Overlay 并断开 Shower WebSocket 连接
        try {
            VirtualDisplayOverlay.hideAll()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error hiding VirtualDisplayOverlay in MainActivity.onDestroy", e)
        }

        anrMonitor.stop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLogger.d(TAG, "onConfigurationChanged: new orientation=${newConfig.orientation}, last orientation=${lastOrientation}")

        // 屏幕方向变化时，确保加载界面不可见
        pluginLoadingState.hide()

        // 仅当方向确实发生变化时才处理
        if (newConfig.orientation != lastOrientation) {
            // 记录变化前的方向
            val orientationBeforeChange = lastOrientation
            // 更新最后的方向记录
            lastOrientation = newConfig.orientation

            // 检查是否是“转回去”的操作
            if (showOrientationChangeDialog && newConfig.orientation == orientationBeforeChange) {
                // 如果是，隐藏弹窗并结束
                showOrientationChangeDialog = false
                return
            }
            
            // 如果不是“转回去”，或者弹窗还未显示，则显示弹窗
            showOrientationChangeDialog = true
        }
    }

    // ======== 初始化组件 ========
    private fun initializeComponents() {
        // 初始化工具处理器（工具注册已在Application中完成）
        toolHandler = AIToolHandler.getInstance(this)

        // 初始化MCP仓库
        mcpRepository = MCPRepository(this)

        anrMonitor = AnrMonitor(this, lifecycleScope)

        // 初始化用户偏好管理器并直接检查初始化状态
        preferencesManager = UserPreferencesManager.getInstance(this)
        showPreferencesGuide = !preferencesManager.isPreferencesInitialized()
        AppLogger.d(
                TAG,
                "初始化检查: 用户偏好已初始化=${!showPreferencesGuide}，将${if(showPreferencesGuide) "" else "不"}显示引导界面"
        )

        // 初始化协议偏好管理器
        agreementPreferences = AgreementPreferences(this)

        // 初始化数据迁移管理器
        migrationManager = ChatHistoryMigrationManager(this)
    }

    // ======== 检查通知权限 ========
    private fun checkNotificationPermission() {
        // Android 13 (API 33) 及以上需要请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    AppLogger.d(TAG, "通知权限已授予")
                }
                shouldShowRequestPermissionRationale(permission) -> {
                    // 用户之前拒绝过，显示说明并再次请求
                    AppLogger.d(TAG, "需要显示通知权限说明")
                    Toast.makeText(
                        this,
                        getString(R.string.notification_permission_rationale),
                        Toast.LENGTH_LONG
                    ).show()
                    notificationPermissionLauncher.launch(permission)
                }
                else -> {
                    // 直接请求权限
                    AppLogger.d(TAG, "请求通知权限")
                    notificationPermissionLauncher.launch(permission)
                }
            }
        } else {
            // Android 13 以下不需要运行时通知权限
            AppLogger.d(TAG, "Android 版本 < 13，无需请求通知权限")
        }
    }

    // ======== 检查权限级别设置 ========
    private fun checkPermissionLevelSet() {
        // 检查是否已设置权限级别
        val permissionLevel = androidPermissionPreferences.getPreferredPermissionLevel()
        AppLogger.d(TAG, "当前权限级别: $permissionLevel")
        showPermissionGuide = permissionLevel == null
        AppLogger.d(
                TAG,
                "权限级别检查: 已设置=${!showPermissionGuide}, 将${if(showPermissionGuide) "" else "不"}显示权限引导界面"
        )
    }

    // ======== 偏好监听器设置 ========
    private fun setupPreferencesListener() {
        // 监听偏好变化
        lifecycleScope.launch {
            preferencesManager.getUserPreferencesFlow().collect { profile ->
                // 只有当状态变化时才更新UI
                val newValue = !profile.isInitialized
                if (showPreferencesGuide != newValue) {
                    AppLogger.d(TAG, "偏好变更: 从 $showPreferencesGuide 变为 $newValue")
                    showPreferencesGuide = newValue
                    setAppContent()
                }
            }
        }
    }

    // ======== 显示与性能配置 ========
    private fun configureDisplaySettings() {
        // 1. 请求持续的高性能模式 (API 31+)
        // 这会提示系统为应用提供持续的高性能，避免CPU/GPU降频。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.setSustainedPerformanceMode(true)
                AppLogger.d(TAG, "已成功请求持续高性能模式。")
            } catch (e: Exception) {
                // 在某些设备上，此模式可能不可用或不支持。
                AppLogger.w(TAG, "请求持续高性能模式失败。", e)
            }
        }

        // 2. 设置应用以最高刷新率运行
        // 高刷新率优化：通过设置窗口属性确保流畅
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 为Android 11+设备优化高刷新率
            val highestMode = getHighestRefreshRate()
            if (highestMode > 0) {
                window.attributes.preferredDisplayModeId = highestMode
                AppLogger.d(TAG, "设置窗口首选显示模式ID: $highestMode")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 为Android 6.0-10设备优化高刷新率
            val refreshRate = getDeviceRefreshRate()
            if (refreshRate > 60f) {
                window.attributes.preferredRefreshRate = refreshRate
                AppLogger.d(TAG, "设置窗口首选刷新率: $refreshRate Hz")
            }
        }

        // 启用硬件加速以提高渲染性能
        window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
    }

    // ======== 设置应用内容 ========
    private fun setAppContent() {
        setContent {
            OperitTheme {
                Box {
                    // 如果初始化检查未完成，则显示一个占位符，避免在检查完成前显示不完整的界面
                    if (!initialChecksDone) {
                        // 在这里可以放置一个加载指示器，或者一个空白屏幕
                        // 为了简单起见，我们暂时留空，因为检查过程很快
                    } else {
                        // 检查是否需要显示用户协议
                        if (!agreementPreferences.isAgreementAccepted()) {
                            AgreementScreen(
                                    onAgreementAccepted = {
                                        agreementPreferences.setAgreementAccepted(true)
                                        // 协议接受后，检查权限级别设置
                                        lifecycleScope.launch {
                                            // 确保使用非阻塞方式更新UI
                                            delay(300) // 短暂延迟确保UI状态更新
                                            checkPermissionLevelSet()
                                            // 重新设置应用内容
                                            setAppContent()
                                        }
                                    }
                            )
                        }
                        // 检查是否需要显示数据迁移界面
                        else if (showMigrationScreen) {
                            MigrationScreen(
                                    migrationManager = migrationManager,
                                    onComplete = {
                                        showMigrationScreen = false
                                        // 迁移完成后，启动插件加载
                                        startPluginLoading()
                                        // 重新设置应用内容
                                        setAppContent()
                                    }
                            )
                        }
                        // 检查是否需要显示权限引导界面
                        else if (showPermissionGuide) {
                            PermissionGuideScreen(
                                    onComplete = {
                                        showPermissionGuide = false
                                        // 权限设置完成后，重新设置应用内容
                                        setAppContent()
                                    }
                            )
                        }
                        // 显示主应用界面
                        else {
                            // 处理待处理的分享文件
                            processPendingSharedFiles()
                            
                            // 主应用界面 (始终存在于底层)
                            OperitApp(
                                    initialNavItem =
                                            when {
                                                showPreferencesGuide -> NavItem.UserPreferencesGuide
                                                else -> NavItem.AiChat
                                            },
                                    toolHandler = toolHandler
                            )
                        }
                    }
                    // 插件加载界面 (带有淡出效果) - 始终在最上层
                    PluginLoadingScreenWithState(
                            loadingState = pluginLoadingState,
                            modifier = Modifier.zIndex(10f) // 确保加载界面在最上层
                    )
                }

                // 方向改变时显示对话框
                if (showOrientationChangeDialog) {
                    OrientationChangeDialog(
                        onConfirm = {
                            showOrientationChangeDialog = false
                            // 重新创建Activity以重新加载页面
                            recreate()
                        },
                        onDismiss = {
                            showOrientationChangeDialog = false
                        }
                    )
                }
            }
        }
    }

    // ======== 设置更新管理器 ========
    private fun setupUpdateManager() {
        // 获取UpdateManager实例
        updateManager = UpdateManager.getInstance(this)

        // 观察更新状态
        updateManager.updateStatus.observe(
                this,
                Observer { status ->
                    if (status is UpdateStatus.Available) {
                        showUpdateNotification(status)
                    }
                }
        )

        // 自动检查更新
        lifecycleScope.launch {
            // 延迟几秒，等待应用完全启动
            delay(3000)
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        if (updateCheckPerformed) return
        updateCheckPerformed = true

        val appVersion =
                try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: PackageManager.NameNotFoundException) {
                    "未知"
                }

        // 使用UpdateManager检查更新
        lifecycleScope.launch {
            try {
                updateManager.checkForUpdatesSilently(appVersion)
                // 不需要显式处理更新状态，因为我们已经设置了观察者
            } catch (e: Exception) {
                AppLogger.e(TAG, "更新检查失败: ${e.message}")
            }
        }
    }

    private fun showUpdateNotification(updateInfo: UpdateStatus.Available) {
        val currentVersion =
                try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: Exception) {
                    "未知"
                }

        AppLogger.d(TAG, "发现新版本: ${updateInfo.newVersion}，当前版本: $currentVersion")

        // 显示更新提示
        val updateMessage = "发现新版本 ${updateInfo.newVersion}，请前往「关于」页面查看详情"
        Toast.makeText(this, updateMessage, Toast.LENGTH_LONG).show()
    }

    private fun getHighestRefreshRate(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayModes = display?.supportedModes ?: return 0
            var maxRefreshRate = 60f // Default to 60Hz
            var highestModeId = 0

            for (mode in displayModes) {
                if (mode.refreshRate > maxRefreshRate) {
                    maxRefreshRate = mode.refreshRate
                    highestModeId = mode.modeId
                }
            }
            AppLogger.d(TAG, "Selected display mode with refresh rate: $maxRefreshRate Hz")
            return highestModeId
        }
        return 0
    }

    private fun getDeviceRefreshRate(): Float {
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val display =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION") windowManager.defaultDisplay
                }

        var refreshRate = 60f // Default refresh rate

        if (display != null) {
            try {
                @Suppress("DEPRECATION") val modes = display.supportedModes
                for (mode in modes) {
                    if (mode.refreshRate > refreshRate) {
                        refreshRate = mode.refreshRate
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error getting refresh rate", e)
            }
        }

        AppLogger.d(TAG, "Selected refresh rate: $refreshRate Hz")
        return refreshRate
    }

    /** 清理临时文件目录 删除Download/Operit/cleanOnExit目录中的所有文件 */
    private fun cleanTemporaryFiles() {
        lifecycleScope.launch {
            try {
                val tempDir = java.io.File("/sdcard/Download/Operit/cleanOnExit")
                if (tempDir.exists() && tempDir.isDirectory) {
                    // 确保.nomedia文件存在
                    val noMediaFile = java.io.File(tempDir, ".nomedia")
                    if (!noMediaFile.exists()) {
                        noMediaFile.createNewFile()
                    }

                    AppLogger.d(TAG, "开始清理临时文件目录: ${tempDir.absolutePath}")

                    fun deleteRecursively(file: java.io.File, isRoot: Boolean = false): Int {
                        var deletedCount = 0
                        if (file.isDirectory) {
                            val children = file.listFiles()
                            children?.forEach { child ->
                                // 递归删除所有子目录和文件，是否为根目录通过 isRoot 控制目录本身是否删除
                                deletedCount += deleteRecursively(child, isRoot = false)
                            }
                            // 根目录本身不删除，只删除其子目录
                            if (!isRoot && file.exists()) {
                                if (file.delete()) {
                                    // 目录删除不计入文件数量统计
                                }
                            }
                        } else if (file.isFile) {
                            // 保留根目录下的 .nomedia 文件，其余全部删除
                            val isRootNoMedia =
                                    (file.parentFile?.absolutePath == tempDir.absolutePath &&
                                            file.name == ".nomedia")
                            if (!isRootNoMedia && file.delete()) {
                                deletedCount++
                            }
                        }
                        return deletedCount
                    }

                    val totalDeleted = deleteRecursively(tempDir, isRoot = true)
                    AppLogger.d(TAG, "已删除${totalDeleted}个临时文件")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "清理临时文件失败", e)
            }
        }
    }
}

@Composable
private fun OrientationChangeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.dialog_title_orientation_change)) },
        text = { Text(text = stringResource(id = R.string.dialog_message_orientation_change)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.dialog_button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_button_dismiss))
            }
        }
    )
}
