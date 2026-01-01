package com.ai.assistance.operit.core.application

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.ai.assistance.operit.util.AppLogger
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.Configuration as WorkConfiguration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.core.workflow.WorkflowSchedulerInitializer
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.initAndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.initUserPreferencesManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.services.OnnxEmbeddingService
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageFactory
import com.ai.assistance.operit.util.GlobalExceptionHandler
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.MediaPoolManager
import com.ai.assistance.operit.util.SerializationSetup
import com.ai.assistance.operit.util.TextSegmenter
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.core.tools.agent.ShowerController
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.ai.assistance.operit.core.tools.system.shower.OperitShowerShellRunner
import com.ai.assistance.showerclient.ShowerEnvironment
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Application class for Operit */
class OperitApplication : Application(), ImageLoaderFactory, WorkConfiguration.Provider {

    companion object {
        /** Global JSON instance with custom serializers */
        lateinit var json: Json
            private set

        // 全局应用实例
        lateinit var instance: OperitApplication
            private set

        // 全局ImageLoader实例，用于高效缓存图片
        lateinit var globalImageLoader: ImageLoader
            private set

        private const val TAG = "OperitApplication"
    }

    // 应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 懒加载数据库实例
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        val startTime = System.currentTimeMillis()
        instance = this

        // 每次应用冷启动时重置上一轮日志，避免日志无限增长
        AppLogger.resetLogFile()

        AppLogger.d(TAG, "【启动计时】应用启动开始")
        AppLogger.d(TAG, "【启动计时】实例初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize ActivityLifecycleManager to track the current activity
        ActivityLifecycleManager.initialize(this)
        AppLogger.d(TAG, "【启动计时】ActivityLifecycleManager初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize AIMessageManager
        AIMessageManager.initialize(this)
        AppLogger.d(TAG, "【启动计时】AIMessageManager初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        startGlobalAIForegroundService()
        AppLogger.d(TAG, "【启动计时】AIForegroundService 启动完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize Embedding Service asynchronously in background
        // Using ONNX-based multilingual model for better Chinese support
        applicationScope.launch {
            val embeddingStartTime = System.currentTimeMillis()
            OnnxEmbeddingService.initialize(this@OperitApplication)
            AppLogger.d(TAG, "【启动计时】OnnxEmbeddingService初始化完成（异步） - ${System.currentTimeMillis() - embeddingStartTime}ms")
        }

        // Initialize ANR monitor
        // AnrMonitor.start()

        // 在所有其他初始化之前设置全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this))
        AppLogger.d(TAG, "【启动计时】全局异常处理器设置完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize the JSON serializer with our custom module
        json = Json {
            serializersModule = SerializationSetup.module
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }
        AppLogger.d(TAG, "【启动计时】JSON序列化器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化用户偏好管理器
        val defaultProfileName = applicationContext.getString(R.string.default_profile)
        initUserPreferencesManager(applicationContext, defaultProfileName)
        AppLogger.d(TAG, "【启动计时】用户偏好管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化Android权限偏好管理器
        initAndroidPermissionPreferences(applicationContext)
        AppLogger.d(TAG, "【启动计时】Android权限偏好管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化功能提示词管理器
        applicationScope.launch {
            val characterStartTime = System.currentTimeMillis()
            CharacterCardManager.getInstance(applicationContext).initializeIfNeeded()
            AppLogger.d(TAG, "【启动计时】功能提示词管理器初始化完成（异步） - ${System.currentTimeMillis() - characterStartTime}ms")
        }

        // 初始化自定义表情
        applicationScope.launch {
            val emojiStartTime = System.currentTimeMillis()
            CustomEmojiRepository.getInstance(applicationContext).initializeBuiltinEmojis()
            AppLogger.d(TAG, "【启动计时】自定义表情初始化完成（异步） - ${System.currentTimeMillis() - emojiStartTime}ms")
        }

        // 在最早时机初始化并应用语言设置
        initializeAppLanguage()
        AppLogger.d(TAG, "【启动计时】语言设置初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化AndroidShellExecutor上下文
        AndroidShellExecutor.setContext(applicationContext)
        AppLogger.d(TAG, "【启动计时】AndroidShellExecutor初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化 Shower 虚拟屏客户端的 ShellRunner 环境
        ShowerEnvironment.shellRunner = OperitShowerShellRunner
        AppLogger.d(TAG, "【启动计时】ShowerEnvironment.shellRunner 已配置 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化PDFBox资源加载器
        PDFBoxResourceLoader.init(getApplicationContext());
        AppLogger.d(TAG, "【启动计时】PDFBox资源加载器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化语言支持
        LanguageFactory.init()
        AppLogger.d(TAG, "【启动计时】语言工厂初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化TextSegmenter
        applicationScope.launch {
            val segmenterStartTime = System.currentTimeMillis()
            TextSegmenter.initialize(applicationContext)
            AppLogger.d(TAG, "【启动计时】TextSegmenter初始化完成（异步） - ${System.currentTimeMillis() - segmenterStartTime}ms")
        }
        
        // Initialize WaifuMessageProcessor
        WaifuMessageProcessor.initialize(applicationContext)
        AppLogger.d(TAG, "【启动计时】WaifuMessageProcessor初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 预加载数据库
        applicationScope.launch {
            val dbStartTime = System.currentTimeMillis()
            // 简单访问数据库以触发初始化
            database.problemDao().getProblemCount()
            AppLogger.d(TAG, "【启动计时】数据库预加载完成（异步） - ${System.currentTimeMillis() - dbStartTime}ms")
        }

        // 初始化全局图片加载器，设置强大的缓存策略
        // 创建自定义 OkHttp 客户端，增加超时时间以支持慢速图片服务器
        val imageOkHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // 连接超时：30秒（默认10秒）
                .readTimeout(60, TimeUnit.SECONDS)    // 读取超时：60秒（默认10秒）
                .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时：30秒（默认10秒）
                .retryOnConnectionFailure(true)       // 连接失败时自动重试
                .build()
        
        globalImageLoader =
                ImageLoader.Builder(this)
                        .okHttpClient(imageOkHttpClient) // 使用自定义 OkHttp 客户端
                        .crossfade(true)
                        .respectCacheHeaders(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCache {
                            DiskCache.Builder()
                                    .directory(filesDir.resolve("image_cache"))
                                    .maxSizeBytes(50 * 1024 * 1024) // 50MB磁盘缓存上限，比百分比更精确
                                    .build()
                        }
                        .memoryCache {
                            // 设置内存缓存最大大小为应用可用内存的15%
                            coil.memory.MemoryCache.Builder(this).maxSizePercent(0.15).build()
                        }
                        .build()
        AppLogger.d(TAG, "【启动计时】全局图片加载器初始化完成（超时配置：连接30s/读取60s） - ${System.currentTimeMillis() - startTime}ms")
        
        // 初始化图片池管理器，支持本地持久化缓存
        ImagePoolManager.initialize(filesDir, preloadNow = false)
        AppLogger.d(TAG, "【启动计时】图片池管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化媒体池管理器（音频/视频），支持本地持久化缓存
        MediaPoolManager.initialize(filesDir, preloadNow = false)
        AppLogger.d(TAG, "【启动计时】媒体池管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 启动后重任务统一后台串行执行，避免多个大任务同时跑导致首屏掉帧
        applicationScope.launch(Dispatchers.Default) {
            // 给 UI 一个缓冲时间先完成首屏渲染
            kotlinx.coroutines.delay(800)

            val imagePreloadStartTime = System.currentTimeMillis()
            withContext(Dispatchers.IO) { ImagePoolManager.preloadFromDisk() }
            AppLogger.d(TAG, "【启动计时】图片池磁盘预加载完成（异步/串行） - ${System.currentTimeMillis() - imagePreloadStartTime}ms")

            val mediaPreloadStartTime = System.currentTimeMillis()
            withContext(Dispatchers.IO) { MediaPoolManager.preloadFromDisk() }
            AppLogger.d(TAG, "【启动计时】媒体池磁盘预加载完成（异步/串行） - ${System.currentTimeMillis() - mediaPreloadStartTime}ms")

            val toolStartTime = System.currentTimeMillis()
            val toolHandler = AIToolHandler.getInstance(this@OperitApplication)
            toolHandler.registerDefaultTools()
            AppLogger.d(TAG, "【启动计时】AIToolHandler初始化并注册工具完成（异步/串行） - ${System.currentTimeMillis() - toolStartTime}ms")
        }
        
        // 初始化工作流调度器（异步）
        applicationScope.launch {
            val schedulerStartTime = System.currentTimeMillis()
            WorkflowSchedulerInitializer.initialize(applicationContext)
            AppLogger.d(TAG, "【启动计时】WorkflowScheduler初始化完成（异步） - ${System.currentTimeMillis() - schedulerStartTime}ms")
        }

        // 在应用启动时尝试绑定无障碍服务提供者（解决后台绑定限制问题）
        applicationScope.launch {
            AppLogger.d(TAG, "【启动计时】开始预绑定无障碍服务提供者...")
            val bindStartTime = System.currentTimeMillis()
            try {
                val bound = com.ai.assistance.operit.data.repository.UIHierarchyManager.bindToService(this@OperitApplication)
                AppLogger.d(TAG, "【启动计时】无障碍服务预绑定完成（异步） - 结果: $bound, 耗时: ${System.currentTimeMillis() - bindStartTime}ms")
            } catch (e: Exception) {
                AppLogger.e(TAG, "无障碍服务预绑定失败", e)
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        AppLogger.d(TAG, "【启动计时】应用启动全部完成 - 总耗时: ${totalTime}ms")
    }

    /**
     * 实现 ImageLoaderFactory 接口
     * 让 Coil 使用我们配置的全局 ImageLoader（带有自定义超时设置）
     */
    override fun newImageLoader(): ImageLoader {
        return globalImageLoader
    }

    /**
     * 实现 WorkConfiguration.Provider 接口
     * 提供 WorkManager 的配置，确保 WorkManager 被正确初始化
     */
    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) AppLogger.DEBUG else AppLogger.INFO)
            .build()

    private fun startGlobalAIForegroundService() {
        try {
            if (!AIForegroundService.isRunning.get()) {
                val intent = Intent(this, AIForegroundService::class.java).apply {
                    putExtra(AIForegroundService.EXTRA_STATE, AIForegroundService.STATE_IDLE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动 AIForegroundService 失败: ${e.message}", e)
        }
    }

    /** 初始化应用语言设置 */
    private fun initializeAppLanguage() {
        try {
            // 同步获取已保存的语言设置
            val languageCode = runBlocking {
                try {
                    // 使用更安全的方式检查preferencesManager
                    val manager = runCatching { preferencesManager }.getOrNull()
                    if (manager != null) {
                        manager.appLanguage.first()
                    } else {
                        UserPreferencesManager.DEFAULT_LANGUAGE
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "获取语言设置失败", e)
                    UserPreferencesManager.DEFAULT_LANGUAGE
                }
            }

            AppLogger.d(TAG, "获取语言设置: $languageCode")

            // 立即应用语言设置
            val locale = Locale(languageCode)
            // 设置默认语言
            Locale.setDefault(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 使用AppCompatDelegate API
                val localeList = LocaleListCompat.create(locale)
                AppCompatDelegate.setApplicationLocales(localeList)
                AppLogger.d(TAG, "使用AppCompatDelegate设置语言: $languageCode")
            } else {
                // 较旧版本Android - 此处使用的部分更新将在attachBaseContext中完成更完整更新
                val config = Configuration()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val localeList = LocaleList(locale)
                    LocaleList.setDefault(localeList)
                    config.setLocales(localeList)
                } else {
                    config.locale = locale
                }

                resources.updateConfiguration(config, resources.displayMetrics)
                AppLogger.d(TAG, "使用Configuration设置语言: $languageCode")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "初始化语言设置失败", e)
        }
    }

    override fun attachBaseContext(base: Context) {
        // 在基础上下文附加前应用语言设置
        try {
            val code = LocaleUtils.getCurrentLanguage(base)
            val locale = Locale(code)
            val config = Configuration(base.resources.configuration)

            // 设置语言配置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
            } else {
                config.locale = locale
                Locale.setDefault(locale)
            }

            // 使用createConfigurationContext创建新的上下文
            val context = base.createConfigurationContext(config)
            super.attachBaseContext(context)
            AppLogger.d(TAG, "成功应用基础上下文语言: $code")
        } catch (e: Exception) {
            AppLogger.e(TAG, "应用基础上下文语言失败", e)
            super.attachBaseContext(base)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        
        try {
            if (AIForegroundService.isRunning.get()) {
                val intent = Intent(applicationContext, AIForegroundService::class.java)
                stopService(intent)
                AppLogger.d(TAG, "应用终止，已停止 AIForegroundService")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "终止时停止 AIForegroundService 失败: ${e.message}", e)
        }

        // 清理终端管理器和SSH连接
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Terminal.getInstance(applicationContext).destroy()
                AppLogger.d(TAG, "应用终止，已清理所有终端会话和SSH连接")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理终端管理器失败: ${e.message}", e)
        }
        
        // 在应用终止时关闭LocalWebServer服务器
        try {
            val webServer = LocalWebServer.getInstance(applicationContext, LocalWebServer.ServerType.WORKSPACE)
            if (webServer.isRunning()) {
                webServer.stop()
                AppLogger.d(TAG, "应用终止，已关闭本地Web服务器")
            }
            val computerServer = LocalWebServer.getInstance(applicationContext, LocalWebServer.ServerType.COMPUTER)
            if (computerServer.isRunning()) {
                computerServer.stop()
                AppLogger.d(TAG, "应用终止，已关闭AI电脑服务器")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "关闭本地Web服务器失败: ${e.message}", e)
        }

        // 在应用终止时，关闭虚拟屏幕 Overlay 并断开 Shower WebSocket 连接
        try {
            VirtualDisplayOverlay.hideAll()
        } catch (e: Exception) {
            AppLogger.e(TAG, "终止时隐藏 VirtualDisplayOverlay 失败: ${e.message}", e)
        }
        try {
            ShowerController.shutdown()
        } catch (e: Exception) {
            AppLogger.e(TAG, "终止时关闭 ShowerController 失败: ${e.message}", e)
        }
    }
}
