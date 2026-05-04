package com.ai.phoneagent.vdiso

import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IInterface
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import com.ai.phoneagent.ShizukuBridge
import java.lang.reflect.Proxy

/**
 * Shizuku VirtualDisplay 引擎（虚拟隔离核心）。
 *
 * **用途**
 * - 通过 Shizuku 包装的系统服务 binder（见 [ShizukuServiceHub]）反射调用 `IDisplayManager`，创建 VirtualDisplay。
 * - 默认输出到内部的离屏帧接收链路（GL 分发 + ImageReader/纹理）以支持：
 * - 虚拟屏截图（给模型识别/决策）
 * - 在需要“0 bitmap 预览”时，支持把 VirtualDisplay 输出 surface 临时切换到 UI 的 `SurfaceView/TextureView`。
 * - 提供聚焦显示能力（通过 InputManager 的 `setFocusedDisplay` best-effort），减少黑屏/输入路由异常。
 *
 * **典型用法**
 * - 创建/复用虚拟屏：`ensureStarted(Args(...))`
 * - 切换输出：`setOutputSurface(surface)` / `restoreOutputSurfaceToImageReader()`
 * - 截图：`captureLatestBitmap()`（由上层转换成 base64）
 * - 聚焦：`ensureFocusedDisplay(displayId)`
 *
 * **引用路径（常见）**
 * - Kotlin：`VirtualDisplayController`（统一入口与状态管理）。
 * - Kotlin：`FloatingStatusService`（工具箱预览/切换输出 surface）。
 *
 * **使用注意事项**
 * - 强依赖 Shizuku 权限与系统服务反射：不同 ROM/Android 版本可能存在 API 差异，本实现大量采用候选方法匹配 + best-effort。
 * - 注意线程模型：启动/停止为同步方法，但内部图像与 GL 线程在后台运行，停止时要避免资源泄露。
 */
object ShizukuVirtualDisplayEngine {

    private const val TAG = "AriesVdIsoEngine"

    data class Args(
            val name: String = "AutoGLM-Virtual",
            val width: Int = 1080,
            val height: Int = 1920,
            val dpi: Int = 142,
            val refreshRate: Float = 0f,
            val rotatesWithContent: Boolean = false,
            val ownerPackage: String = "com.android.shell",
    )

    @Volatile private var displayId: Int? = null

    @Volatile private var vdCallback: Any? = null

    @Volatile private var currentOutputSurface: Surface? = null

    @Volatile private var glDispatcher: VdGlFrameDispatcher? = null

    @Volatile private var latestContentWidth: Int = 0

    @Volatile private var latestContentHeight: Int = 0

    private val frameLock = Any()

    @Volatile private var stopping: Boolean = false

    private fun setVirtualDisplaySurfaceBestEffort(callback: Any, surface: Surface): Result<Unit> {
        // 通过 IDisplayManager.setVirtualDisplaySurface*(...) 反射切换 VirtualDisplay 的输出 Surface。
        // 用途：
        // - 工具箱展开（虚拟隔离模式）时把输出切到 SurfaceView.surface，实现 0 bitmap 的直出预览。
        // - 工具箱收起/切换回主屏模式时把输出切回 ImageReader.surface，便于继续做虚拟屏截图/识别。
        return runCatching {
            val displayManager = ShizukuServiceHub.getDisplayManager()

            val cbInterface =
                    runCatching {
                                Class.forName("android.hardware.display.IVirtualDisplayCallback")
                            }
                            .getOrNull()
            val asBinder =
                    callback.javaClass.methods.firstOrNull { m ->
                        m.name == "asBinder" &&
                                m.parameterTypes.isEmpty() &&
                                m.returnType.name == "android.os.IBinder"
                    }
            val binder = asBinder?.invoke(callback)

            val candidates =
                    displayManager.javaClass.methods.filter { m ->
                        m.name == "setVirtualDisplaySurface" ||
                                m.name == "setVirtualDisplaySurfaceAsync"
                    }

            for (m in candidates) {
                try {
                    val p = m.parameterTypes
                    when {
                        p.size == 2 &&
                                cbInterface != null &&
                                p[0].isAssignableFrom(cbInterface) &&
                                p[1] == Surface::class.java -> {
                            m.invoke(displayManager, callback, surface)
                            return@runCatching
                        }
                        p.size == 2 &&
                                p[0].name == "android.hardware.display.IVirtualDisplayCallback" &&
                                p[1] == Surface::class.java -> {
                            m.invoke(displayManager, callback, surface)
                            return@runCatching
                        }
                        p.size == 2 &&
                                p[0].name == "android.os.IBinder" &&
                                p[1] == Surface::class.java &&
                                binder != null -> {
                            m.invoke(displayManager, binder, surface)
                            return@runCatching
                        }
                        p.size == 2 &&
                                IInterface::class.java.isAssignableFrom(p[0]) &&
                                p[1] == Surface::class.java -> {
                            m.invoke(displayManager, callback, surface)
                            return@runCatching
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "setVirtualDisplaySurface failed on ${m.name}", t)
                }
            }
            throw IllegalStateException("No setVirtualDisplaySurface method matched")
        }
    }

    @Synchronized
    fun ensureStarted(args: Args = Args()): Result<Int> {
        // 入口：确保虚拟屏已启动。
        // 注意：VirtualDisplay 启动时默认输出到 ImageReader.surface，用于缓存帧（截图/识别）。
        // 当需要 0 bitmap 预览时，外部会调用 setOutputSurface(surfaceViewSurface) 临时切换输出。
        val existing = displayId
        if (existing != null && glDispatcher != null && vdCallback != null) {
            return Result.success(existing)
        }
        return start(args)
    }

    @Synchronized
    fun start(args: Args = Args()): Result<Int> {
        Log.i(
                TAG,
                "ShizukuVirtualDisplayEngine.start: width=${args.width}, height=${args.height}, dpi=${args.dpi}"
        )

        // 启动 Shizuku VirtualDisplay，并用 ImageReader 接收 RGBA 帧。
        // 帧接收线程在 HandlerThread 上运行，避免阻塞主线程。
        return runCatching {
            stop()

            stopping = false

            Log.i(TAG, "Starting VdGlFrameDispatcher...")
            val dispatcher = VdGlFrameDispatcher()
            dispatcher.start(args.width, args.height)
            glDispatcher = dispatcher

            // 等待 GL 初始化完成，拿到 VirtualDisplay 的输入 Surface（SurfaceTexture）。
            val deadline = SystemClock.uptimeMillis() + 1500L
            var input: Surface? = null
            while (SystemClock.uptimeMillis() <= deadline) {
                input = dispatcher.getInputSurface()
                if (input != null && input.isValid) break
                try {
                    Thread.sleep(10L)
                } catch (_: InterruptedException) {
                    break
                }
            }
            val inputSurface =
                    input
                            ?: run {
                                Log.e(TAG, "GL input surface not ready - input is null")
                                throw IllegalStateException("GL input surface not ready")
                            }
            Log.i(TAG, "GL input surface ready: $inputSurface")
            currentOutputSurface = inputSurface

            // 在第一帧到达前，用创建时的目标分辨率作为内容尺寸兜底。
            synchronized(frameLock) {
                latestContentWidth = args.width
                latestContentHeight = args.height
            }

            val created = createVirtualDisplay(args, inputSurface)
            vdCallback = created.second
            displayId = created.first

            Log.i(TAG, "started: displayId=${displayId} size=${args.width}x${args.height}")
            displayId ?: throw IllegalStateException("displayId is null")
        }
    }

    fun getDisplayId(): Int? = displayId

    fun isStarted(): Boolean = displayId != null && glDispatcher != null && vdCallback != null

    fun getLatestFrameTimeMs(): Long = glDispatcher?.getLatestFrameTimeMs() ?: 0L

    fun getLatestContentSize(): Pair<Int, Int> {
        // latestBitmap 可能是带 rowStride padding 的“padded bitmap”。
        // 这里返回的 content size 才是虚拟屏真实内容宽高，用于 UI 比例计算/裁剪绘制。
        val d = glDispatcher
        if (d != null) return d.getContentSize()
        val (w, h) = synchronized(frameLock) { latestContentWidth to latestContentHeight }
        return w to h
    }

    fun captureLatestBitmap(): Result<Bitmap> {
        val d =
                glDispatcher
                        ?: return Result.failure(IllegalStateException("GL dispatcher not started"))
        val bmp =
                d.captureBitmapBlocking()
                        ?: return Result.failure(IllegalStateException("No captured frame"))
        return Result.success(bmp)
    }

    @Synchronized
    fun stop() {
        stopping = true

        val dispatcher = glDispatcher
        glDispatcher = null
        runCatching { dispatcher?.stop() }

        synchronized(frameLock) {
            // reset timestamps/size hints
            latestContentWidth = 0
            latestContentHeight = 0
        }

        val cb = vdCallback
        vdCallback = null
        displayId = null

        currentOutputSurface = null

        if (cb != null) {
            runCatching { releaseVirtualDisplayBestEffort(cb) }
        }
    }

    @Synchronized
    fun setOutputSurface(surface: Surface): Result<Unit> {
        // OpenGL 分发架构下：VirtualDisplay 固定输出到中转 SurfaceTexture。
        // 这里的 setOutputSurface 仅用于设置“预览输出目标”（悬浮窗 TextureView 的 Surface）。
        val d =
                glDispatcher
                        ?: return Result.failure(IllegalStateException("GL dispatcher not started"))
        if (stopping) return Result.failure(IllegalStateException("Engine stopping"))
        d.setPreviewSurface(surface)
        return Result.success(Unit)
    }

    @Synchronized
    fun restoreOutputSurfaceToImageReader(): Result<Unit> {
        // OpenGL 分发架构下：截图走 dispatcher 的离屏 ImageReader，不需要切换 VirtualDisplay 输出。
        val d =
                glDispatcher
                        ?: return Result.failure(IllegalStateException("GL dispatcher not started"))
        d.setPreviewSurface(null)
        return Result.success(Unit)
    }

    fun ensureFocusedDisplay(targetDisplayId: Int): Result<Unit> {
        return runCatching {
            val inputManager = ShizukuServiceHub.getInputManager()
            val cls = inputManager.javaClass

            val getFocused =
                    cls.methods.firstOrNull { m ->
                        m.name == "getFocusedDisplayId" && m.parameterTypes.isEmpty()
                    }
            val setFocused =
                    cls.methods.firstOrNull { m ->
                        m.name == "setFocusedDisplay" &&
                                m.parameterTypes.size == 1 &&
                                m.parameterTypes[0] == Int::class.javaPrimitiveType
                    }

            if (setFocused == null) return@runCatching

            if (getFocused != null) {
                val current = runCatching { getFocused.invoke(inputManager) as? Int }.getOrNull()
                if (current != null && current == targetDisplayId) return@runCatching
            }

            setFocused.invoke(inputManager, targetDisplayId)
        }
                .onFailure { t ->
                    Log.w(TAG, "ensureFocusedDisplay failed: target=$targetDisplayId", t)
                }
    }

    /** 将系统焦点显示恢复到默认主屏（displayId = 0）。 用于虚拟屏操作完成后，避免物理按键（如返回键）被路由到虚拟屏。 */
    fun restoreFocusToDefaultDisplay(): Result<Unit> {
        return runCatching {
            val inputManager = ShizukuServiceHub.getInputManager()
            val cls = inputManager.javaClass
            val getFocused =
                    cls.methods.firstOrNull { m ->
                        m.name == "getFocusedDisplayId" && m.parameterTypes.isEmpty()
                    }
            val setFocused =
                    cls.methods.firstOrNull { m ->
                        m.name == "setFocusedDisplay" &&
                                m.parameterTypes.size == 1 &&
                                m.parameterTypes[0] == Int::class.javaPrimitiveType
                    }
                            ?: return@runCatching

            // 仅在当前不是主屏时才切换，避免无效 IPC
            if (getFocused != null) {
                val current = runCatching { getFocused.invoke(inputManager) as? Int }.getOrNull()
                if (current == 0) return@runCatching
            }

            setFocused.invoke(inputManager, 0) // Display.DEFAULT_DISPLAY = 0
        }
                .onFailure { t -> Log.w(TAG, "restoreFocusToDefaultDisplay failed", t) }
    }

    private fun createVirtualDisplay(args: Args, surface: Surface): Pair<Int, Any> {
        val displayManager = ShizukuServiceHub.getDisplayManager()

        val callback = createVirtualDisplayCallbackProxy()

        // 打印所有 createVirtualDisplay 方法签名用于调试
        val allCreateMethods =
                displayManager.javaClass.methods.filter { it.name == "createVirtualDisplay" }
        Log.i(TAG, "Found ${allCreateMethods.size} createVirtualDisplay methods:")
        allCreateMethods.forEach { m ->
            Log.i(TAG, "  - ${m.name}: ${m.parameterTypes.map { it.name }.joinToString()}")
        }

        // 尝试多种方法签名匹配
        val createMethod =
                allCreateMethods.firstOrNull { m ->
                    val p = m.parameterTypes
                    when (p.size) {
                        4 -> {
                            // 标准签名: VirtualDisplayConfig, IVirtualDisplayCallback,
                            // IMediaProjection, String
                            p[0].name.contains("VirtualDisplayConfig") &&
                                    (p[1].name.contains("IVirtualDisplayCallback") ||
                                            p[1].name.contains("IInterface")) &&
                                    (p[2].name.contains("IMediaProjection") ||
                                            p[2] == java.lang.Void.TYPE ||
                                            p[2] == null) &&
                                    p[3] == String::class.java
                        }
                        3 -> {
                            // 某些版本可能是: VirtualDisplayConfig, IBinder, String
                            p[0].name.contains("VirtualDisplayConfig") &&
                                    p[1].name.contains("IBinder") &&
                                    p[2] == String::class.java
                        }
                        else -> false
                    }
                }
                        ?: run {
                            // 如果没有找到4参数的方法，尝试找任何名为 createVirtualDisplay 的方法
                            allCreateMethods.firstOrNull()
                                    ?: throw NoSuchMethodException(
                                            "No createVirtualDisplay method found"
                                    )
                        }

        Log.i(
                TAG,
                "Using createVirtualDisplay: ${createMethod.name}: ${createMethod.parameterTypes.map { it.name }.joinToString()}"
        )

        val packageName = args.ownerPackage
        val flags = buildFlags(rotatesWithContent = args.rotatesWithContent)
        val trustedFlag =
                getVirtualDisplayFlagBestEffort(
                        "VIRTUAL_DISPLAY_FLAG_TRUSTED",
                        fallback = (1 shl 10)
                )
        val flagsWithoutTrusted = if (trustedFlag != null) (flags and trustedFlag.inv()) else flags

        val config = buildVirtualDisplayConfig(args, surface, flags)

        // 根据参数数量调用
        val id =
                try {
                    when (createMethod.parameterTypes.size) {
                        4 ->
                                createMethod.invoke(
                                        displayManager,
                                        config,
                                        callback,
                                        null,
                                        packageName
                                ) as
                                        Int
                        3 ->
                                createMethod.invoke(
                                        displayManager,
                                        config,
                                        callback,
                                        packageName
                                ) as
                                        Int
                        else ->
                                throw IllegalStateException(
                                        "Unsupported parameter count: ${createMethod.parameterTypes.size}"
                                )
                    }
                } catch (t: Throwable) {
                    val cause =
                            (t as? java.lang.reflect.InvocationTargetException)?.targetException
                                    ?: t
                    if (cause is SecurityException &&
                                    trustedFlag != null &&
                                    (flags and trustedFlag) != 0
                    ) {
                        Log.w(
                                TAG,
                                "createVirtualDisplay with TRUSTED failed, retry without TRUSTED",
                                cause
                        )
                        val configNoTrust =
                                buildVirtualDisplayConfig(args, surface, flagsWithoutTrusted)
                        when (createMethod.parameterTypes.size) {
                            4 ->
                                    createMethod.invoke(
                                            displayManager,
                                            configNoTrust,
                                            callback,
                                            null,
                                            packageName
                                    ) as
                                            Int
                            3 ->
                                    createMethod.invoke(
                                            displayManager,
                                            configNoTrust,
                                            callback,
                                            packageName
                                    ) as
                                            Int
                            else ->
                                    throw IllegalStateException(
                                            "Unsupported parameter count: ${createMethod.parameterTypes.size}"
                                    )
                        }
                    } else {
                        throw t
                    }
                }

        Log.i(TAG, "createVirtualDisplay ok: displayId=$id flags=$flags")

        applyImePolicyBestEffort(id)

        return id to callback
    }

    private fun applyImePolicyBestEffort(displayId: Int) {
        runCatching {
            val wm = ShizukuServiceHub.getWindowManager()
            val wmClass = wm.javaClass

            // 完全隔离模式：虚拟屏禁用 IME 渲染
            // setShouldShowIme(displayId, false) → 不在 VD 上显示键盘
            val shouldShowIme =
                    wmClass.methods.firstOrNull { m ->
                        m.name == "setShouldShowIme" &&
                                m.parameterTypes.size == 2 &&
                                m.parameterTypes[0] == Int::class.javaPrimitiveType &&
                                m.parameterTypes[1] == Boolean::class.javaPrimitiveType
                    }
            if (shouldShowIme != null) {
                shouldShowIme.invoke(wm, displayId, false)
            }

            // DISPLAY_IME_POLICY_HIDE = 2 → 完全隐藏虚拟屏上的 IME
            // 文本输入通过 clipboard+paste 或 input text --display 命令实现，不需要可视键盘
            val DISPLAY_IME_POLICY_HIDE = 2
            val DISPLAY_IME_POLICY_FALLBACK_DISPLAY = 1
            val setImePolicy =
                    wmClass.methods.firstOrNull { m ->
                        m.name == "setDisplayImePolicy" &&
                                m.parameterTypes.size == 2 &&
                                m.parameterTypes[0] == Int::class.javaPrimitiveType &&
                                m.parameterTypes[1] == Int::class.javaPrimitiveType
                    }
            if (setImePolicy != null) {
                setImePolicy.invoke(
                        wm,
                        Display.DEFAULT_DISPLAY,
                        DISPLAY_IME_POLICY_FALLBACK_DISPLAY
                )
                try {
                    setImePolicy.invoke(wm, displayId, DISPLAY_IME_POLICY_HIDE)
                } catch (t: Throwable) {
                    // 回退到 FALLBACK_DISPLAY
                    setImePolicy.invoke(
                            wm,
                            Display.DEFAULT_DISPLAY,
                            DISPLAY_IME_POLICY_FALLBACK_DISPLAY
                    )
                    throw t
                }
            }
        }
                .onFailure { t ->
                    Log.w(TAG, "applyImePolicyBestEffort failed: displayId=$displayId", t)
                    runCatching {
                        if (!ShizukuBridge.pingBinder() || !ShizukuBridge.hasPermission())
                                return@runCatching

                        val DISPLAY_IME_POLICY_HIDE = 2
                        val candidates =
                                listOf(
                                        "cmd window set-display-ime-policy $displayId $DISPLAY_IME_POLICY_HIDE",
                                        "cmd window setDisplayImePolicy $displayId $DISPLAY_IME_POLICY_HIDE",
                                        "wm set-display-ime-policy $displayId $DISPLAY_IME_POLICY_HIDE",
                                        "wm setDisplayImePolicy $displayId $DISPLAY_IME_POLICY_HIDE",
                                        "settings put global force_desktop_mode_on_external_displays 1",
                                        "settings put global enable_freeform_support 1",
                                        "wm set-pc-mode 1",
                                )
                        for (c in candidates) {
                            val r = ShizukuBridge.execResult(c)
                            if (r.exitCode == 0) break
                        }
                    }
                }
    }

    private fun buildFlags(rotatesWithContent: Boolean): Int {
        val publicFlag =
                getVirtualDisplayFlagBestEffort("VIRTUAL_DISPLAY_FLAG_PUBLIC", fallback = null) ?: 0
        val presentationFlag =
                getVirtualDisplayFlagBestEffort(
                        "VIRTUAL_DISPLAY_FLAG_PRESENTATION",
                        fallback = (1 shl 1)
                )
                        ?: 0
        val trustedFlag =
                getVirtualDisplayFlagBestEffort(
                        "VIRTUAL_DISPLAY_FLAG_TRUSTED",
                        fallback = (1 shl 10)
                )

        // 不使用 OWN_CONTENT_ONLY：后台虚拟屏需要承载 Chrome/B 站等第三方 App。
        // 带上该 flag 后，部分系统会只显示本应用自己的窗口，导致截图一直是空帧/黑帧。
        var flags = publicFlag

        flags = flags or presentationFlag
        if (trustedFlag != null) {
            flags = flags or trustedFlag
        }

        // supports-touch (hidden in some versions)
        flags = flags or (1 shl 6)
        // destroy-content-on-removal
        flags = flags or (1 shl 8)

        if (rotatesWithContent) {
            flags = flags or (1 shl 7)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            // own display group / always unlocked / touch feedback disabled
            flags = flags or (1 shl 11)
            flags = flags or (1 shl 12)
            flags = flags or (1 shl 13)
            if (Build.VERSION.SDK_INT >= 34) {
                flags = flags or (1 shl 14)
                flags = flags or (1 shl 15)
            }
        }

        return flags
    }

    private fun getVirtualDisplayFlagBestEffort(name: String, fallback: Int?): Int? {
        return runCatching {
            val dm = Class.forName("android.hardware.display.DisplayManager")
            dm.getField(name).getInt(null)
        }
                .getOrElse { fallback }
    }

    private fun buildVirtualDisplayConfig(args: Args, surface: Surface, flags: Int): Any {
        val builderClass = Class.forName("android.hardware.display.VirtualDisplayConfig\$Builder")
        val ctor =
                builderClass.getConstructor(
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                )
        val builder = ctor.newInstance(args.name, args.width, args.height, args.dpi)

        builderClass
                .getMethod("setSurface", android.view.Surface::class.java)
                .invoke(builder, surface)
        builderClass.getMethod("setFlags", Int::class.javaPrimitiveType).invoke(builder, flags)

        val setRequestedRefreshRate =
                builderClass.methods.firstOrNull { m ->
                    m.name == "setRequestedRefreshRate" &&
                            m.parameterTypes.size == 1 &&
                            m.parameterTypes[0] == Float::class.javaPrimitiveType
                }
        if (setRequestedRefreshRate != null) {
            setRequestedRefreshRate.invoke(builder, args.refreshRate)
        }

        return builderClass.getMethod("build").invoke(builder)
    }

    private fun createVirtualDisplayCallbackProxy(): Any {
        val callbackInterfaceClass =
                Class.forName("android.hardware.display.IVirtualDisplayCallback")
        val binder = Binder()
        return Proxy.newProxyInstance(
                callbackInterfaceClass.classLoader,
                arrayOf(callbackInterfaceClass, IInterface::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "asBinder" -> binder
                else -> Unit
            }
        }
    }

    private fun releaseVirtualDisplayBestEffort(callback: Any) {
        val displayManager =
                runCatching { ShizukuServiceHub.getDisplayManager() }.getOrNull() ?: return

        val cbInterface =
                runCatching { Class.forName("android.hardware.display.IVirtualDisplayCallback") }
                        .getOrNull()
        val asBinder =
                callback.javaClass.methods.firstOrNull { m ->
                    m.name == "asBinder" &&
                            m.parameterTypes.isEmpty() &&
                            m.returnType.name == "android.os.IBinder"
                }
        val binder = asBinder?.invoke(callback)

        val candidates =
                displayManager.javaClass.methods.filter { m ->
                    val n = m.name
                    n == "releaseVirtualDisplay" ||
                            n == "destroyVirtualDisplay" ||
                            n == "removeVirtualDisplay"
                }

        for (m in candidates) {
            try {
                val p = m.parameterTypes
                when {
                    p.size == 1 && cbInterface != null && p[0].isAssignableFrom(cbInterface) -> {
                        m.invoke(displayManager, callback)
                        Log.i(
                                TAG,
                                "releaseVirtualDisplay ok via ${m.name}(IVirtualDisplayCallback)"
                        )
                        return
                    }
                    p.size == 1 &&
                            p[0].name == "android.hardware.display.IVirtualDisplayCallback" -> {
                        m.invoke(displayManager, callback)
                        Log.i(
                                TAG,
                                "releaseVirtualDisplay ok via ${m.name}(IVirtualDisplayCallback)"
                        )
                        return
                    }
                    p.size == 1 && p[0].name == "android.os.IBinder" && binder != null -> {
                        m.invoke(displayManager, binder)
                        Log.i(TAG, "releaseVirtualDisplay ok via ${m.name}(IBinder)")
                        return
                    }
                    p.size == 1 && IInterface::class.java.isAssignableFrom(p[0]) -> {
                        m.invoke(displayManager, callback)
                        Log.i(TAG, "releaseVirtualDisplay ok via ${m.name}(IInterface)")
                        return
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "releaseVirtualDisplay failed on ${m.name}", t)
            }
        }
    }
}
