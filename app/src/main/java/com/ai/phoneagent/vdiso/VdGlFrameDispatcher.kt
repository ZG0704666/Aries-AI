package com.ai.phoneagent.vdiso

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.media.Image
import android.media.ImageReader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface

/**
 * VirtualDisplay 帧分发器（OpenGL）。
 *
 * **用途**
 * - 将 VirtualDisplay 的输出绑定到内部 `SurfaceTexture`（OES 外部纹理），在 GL 线程中进行两路分发：
 * - 预览输出：渲染到可选的 `previewSurface`（悬浮窗 TextureView/SurfaceView）。
 * - 截图输出：渲染到离屏 `ImageReader`（captureSurface），供上层读取最新帧并转为 Bitmap/base64。
 * - 目标：实现"预览与截图互不干扰"，同时减少频繁的 bitmap 拷贝开销。
 *
 * **引用路径（常见）**
 * - `ShizukuVirtualDisplayEngine`：启动时创建并持有本分发器。
 *
 * **使用注意事项**
 * - EGL/GL 资源必须在同一线程创建与释放：本类使用 `HandlerThread` 维护 GL 线程。
 * - 预览 Surface 可能随 UI 生命周期变化：设置/清空预览时需确保线程安全与资源释放。
 */
class VdGlFrameDispatcher {

    private var width: Int = 0
    private var height: Int = 0

    @Volatile private var inputSurfaceTexture: SurfaceTexture? = null

    @Volatile private var inputSurface: Surface? = null

    @Volatile private var imageReader: ImageReader? = null

    @Volatile private var previewSurface: Surface? = null

    @Volatile private var glThread: HandlerThread? = null

    @Volatile private var glHandler: Handler? = null

    @Volatile private var eglDisplay: EGLDisplay? = null

    @Volatile private var eglContext: EGLContext? = null

    @Volatile private var eglConfig: EGLConfig? = null

    @Volatile private var eglPreviewSurface: EGLSurface? = null

    @Volatile private var eglCaptureSurface: EGLSurface? = null

    @Volatile private var eglPbufferSurface: EGLSurface? = null

    @Volatile private var program: Int = 0

    @Volatile private var aPos: Int = -1

    @Volatile private var aTex: Int = -1

    @Volatile private var uTex: Int = -1

    @Volatile private var uSTMatrix: Int = -1

    @Volatile private var oesTexId: Int = 0

    @Volatile private var lastFrameTimeMs: Long = 0L
    @Volatile private var lastPreviewRenderTimeMs: Long = 0L

    private val frameSync = Object()
    private var frameAvailable: Boolean = false

    private val renderSync = Object()
    private var previewRenderPosted: Boolean = false
    private val renderLock = Object()

    @Volatile private var initError: Throwable? = null

    fun start(targetWidth: Int, targetHeight: Int) {
        stop()

        width = targetWidth
        height = targetHeight
        initError = null

        val ht = HandlerThread("VdIsoGL")
        ht.start()
        glThread = ht
        glHandler = Handler(ht.looper)

        glHandler?.post {
            try {
                initGlOnThread()
            } catch (t: Throwable) {
                initError = t
                Log.e("AriesVdGl", "initGlOnThread failed", t)
            }
        }

        // 等待初始化完成或超时
        val deadline = SystemClock.uptimeMillis() + 2000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (initError != null) {
                Log.e("AriesVdGl", "GL init error: $initError")
                break
            }
            if (inputSurface != null) {
                break
            }
            Thread.sleep(10L)
        }
    }

    fun stop() {
        val h = glHandler
        val t = glThread
        glHandler = null
        glThread = null

        if (h != null) {
            h.post { releaseGlOnThread() }
        }

        runCatching { t?.quitSafely() }

        runCatching { previewSurface?.release() }
        previewSurface = null

        runCatching { inputSurface?.release() }
        inputSurface = null

        inputSurfaceTexture = null

        runCatching { imageReader?.close() }
        imageReader = null

        width = 0
        height = 0
        lastFrameTimeMs = 0L
        lastPreviewRenderTimeMs = 0L
    }

    fun getInputSurface(): Surface? = inputSurface

    fun getLatestFrameTimeMs(): Long = lastFrameTimeMs

    fun getContentSize(): Pair<Int, Int> = width to height

    fun setPreviewSurface(surface: Surface?) {
        val h = glHandler ?: return
        h.post { setPreviewSurfaceOnThread(surface) }
    }

    fun captureBitmapBlocking(timeoutMs: Long = 800L): Bitmap? {
        val h = glHandler ?: return null
        val latch = java.util.concurrent.CountDownLatch(1)
        val out = arrayOfNulls<Bitmap>(1)
        h.post {
            out[0] = captureBitmapOnThread(timeoutMs)
            latch.countDown()
        }
        runCatching { latch.await(timeoutMs + 300L, java.util.concurrent.TimeUnit.MILLISECONDS) }
        return out[0]
    }

    private fun initGlOnThread() {
        Log.i("AriesVdGl", "initGlOnThread: starting EGL init...")
        val d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        eglDisplay = d
        if (d == EGL14.EGL_NO_DISPLAY) {
            Log.e("AriesVdGl", "EGL_NO_DISPLAY")
            throw IllegalStateException("EGL_NO_DISPLAY")
        }
        Log.i("AriesVdGl", "EGL display obtained: $d")

        val version = IntArray(2)
        if (!EGL14.eglInitialize(d, version, 0, version, 1)) {
            throw IllegalStateException("eglInitialize failed")
        }

        val attribList =
                intArrayOf(
                        EGL14.EGL_RED_SIZE,
                        8,
                        EGL14.EGL_GREEN_SIZE,
                        8,
                        EGL14.EGL_BLUE_SIZE,
                        8,
                        EGL14.EGL_ALPHA_SIZE,
                        8,
                        EGL14.EGL_RENDERABLE_TYPE,
                        EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_SURFACE_TYPE,
                        EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
                        EGL14.EGL_NONE,
                )

        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(d, attribList, 0, configs, 0, configs.size, num, 0)) {
            throw IllegalStateException("eglChooseConfig failed")
        }
        val cfg = configs[0] ?: throw IllegalStateException("No EGLConfig")
        eglConfig = cfg

        val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val ctx = EGL14.eglCreateContext(d, cfg, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
        eglContext = ctx
        if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT)
                throw IllegalStateException("eglCreateContext failed")

        // Before any GLES* call, we must have a current EGL context.
        // Create a tiny Pbuffer surface and make it current.
        val pbufferAttrib =
                intArrayOf(
                        EGL14.EGL_WIDTH,
                        1,
                        EGL14.EGL_HEIGHT,
                        1,
                        EGL14.EGL_NONE,
                )
        val pb = EGL14.eglCreatePbufferSurface(d, cfg, pbufferAttrib, 0)
        if (pb == null || pb == EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("eglCreatePbufferSurface failed")
        }
        eglPbufferSurface = pb
        if (!EGL14.eglMakeCurrent(d, pb, pb, ctx)) {
            throw IllegalStateException("eglMakeCurrent(pbuffer) failed")
        }

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTexId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        )

        val st = SurfaceTexture(oesTexId)
        st.setDefaultBufferSize(width, height)
        val callbackHandler = glHandler ?: Handler(Looper.myLooper() ?: Looper.getMainLooper())
        st.setOnFrameAvailableListener(
                { _: SurfaceTexture ->
                    synchronized(frameSync) {
                        frameAvailable = true
                        frameSync.notifyAll()
                    }

                    // Never render directly inside the callback: it may arrive on a non-GL thread
                    // and can be re-entrant.
                    // Post at most one pending preview render to GL thread.
                    val now = SystemClock.uptimeMillis()
                    if (now - lastPreviewRenderTimeMs < PREVIEW_FRAME_INTERVAL_MS) {
                        return@setOnFrameAvailableListener
                    }
                    val h = glHandler ?: return@setOnFrameAvailableListener
                    synchronized(renderSync) {
                        if (previewRenderPosted) return@setOnFrameAvailableListener
                        previewRenderPosted = true
                    }
                    h.post {
                        try {
                            val ps = eglPreviewSurface
                            if (ps != null) {
                                renderLatestToSurface(ps, width, height)
                                lastPreviewRenderTimeMs = SystemClock.uptimeMillis()
                            }
                        } finally {
                            synchronized(renderSync) { previewRenderPosted = false }
                        }
                    }
                },
                callbackHandler
        )

        inputSurfaceTexture = st
        inputSurface = Surface(st)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)

        program = createProgram(VS, FS)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTex = GLES20.glGetAttribLocation(program, "aTex")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uSTMatrix = GLES20.glGetUniformLocation(program, "uSTMatrix")
    }

    private fun releaseGlOnThread() {
        runCatching { setPreviewSurfaceOnThread(null) }

        runCatching {
            eglCaptureSurface?.let { s ->
                val d = eglDisplay
                if (d != null) {
                    EGL14.eglDestroySurface(d, s)
                }
            }
        }
        eglCaptureSurface = null

        runCatching {
            eglPbufferSurface?.let { s ->
                val d = eglDisplay
                if (d != null) {
                    EGL14.eglDestroySurface(d, s)
                }
            }
        }
        eglPbufferSurface = null

        val st = inputSurfaceTexture
        inputSurfaceTexture = null
        runCatching { st?.release() }

        runCatching { inputSurface?.release() }
        inputSurface = null

        runCatching { imageReader?.close() }
        imageReader = null

        if (program != 0) {
            runCatching { GLES20.glDeleteProgram(program) }
            program = 0
        }

        if (oesTexId != 0) {
            val arr = intArrayOf(oesTexId)
            runCatching { GLES20.glDeleteTextures(1, arr, 0) }
            oesTexId = 0
        }

        val d = eglDisplay
        val c = eglContext
        eglDisplay = null
        eglContext = null
        eglConfig = null

        if (d != null) {
            runCatching {
                EGL14.eglMakeCurrent(
                        d,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                )
            }
            if (c != null && c != EGL14.EGL_NO_CONTEXT) {
                runCatching { EGL14.eglDestroyContext(d, c) }
            }
            runCatching { EGL14.eglTerminate(d) }
        }
    }

    private fun setPreviewSurfaceOnThread(surface: Surface?) {
        val d = eglDisplay ?: return
        val cfg = eglConfig ?: return

        val old = eglPreviewSurface
        eglPreviewSurface = null
        if (old != null) {
            runCatching { EGL14.eglDestroySurface(d, old) }
        }

        val oldSurface = previewSurface
        previewSurface = surface
        if (oldSurface != null && oldSurface !== surface) {
            runCatching { oldSurface.release() }
        }
        if (surface == null) return

        val attrib = intArrayOf(EGL14.EGL_NONE)
        val es = EGL14.eglCreateWindowSurface(d, cfg, surface, attrib, 0)
        if (es == null || es == EGL14.EGL_NO_SURFACE) {
            return
        }
        eglPreviewSurface = es

        lastPreviewRenderTimeMs = 0L
    }

    private fun captureBitmapOnThread(timeoutMs: Long): Bitmap? {
        val d = eglDisplay ?: return null
        val cfg = eglConfig ?: return null
        val reader = imageReader ?: return null

        val surface = reader.surface
        if (eglCaptureSurface == null) {
            val attrib = intArrayOf(EGL14.EGL_NONE)
            val es = EGL14.eglCreateWindowSurface(d, cfg, surface, attrib, 0)
            if (es == null || es == EGL14.EGL_NO_SURFACE) return null
            eglCaptureSurface = es
        }

        waitForFrame(timeoutMs)
        val es = eglCaptureSurface ?: return null
        renderLatestToSurface(es, width, height)

        val img = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return null
        try {
            return imageToBitmap(img)
        } finally {
            runCatching { img.close() }
        }
    }

    private fun waitForFrame(timeoutMs: Long) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        synchronized(frameSync) {
            while (!frameAvailable && SystemClock.uptimeMillis() < deadline) {
                runCatching { frameSync.wait(16L) }
            }
            frameAvailable = false
        }
    }

    private fun renderLatestToSurface(eglSurface: EGLSurface, outW: Int, outH: Int) {
        synchronized(renderLock) {
            renderLatestToSurfaceLocked(eglSurface, outW, outH)
        }
    }

    private fun renderLatestToSurfaceLocked(eglSurface: EGLSurface, outW: Int, outH: Int) {
        val d = eglDisplay ?: return
        val ctx = eglContext ?: return

        if (eglSurface == EGL14.EGL_NO_SURFACE) return
        if (ctx == EGL14.EGL_NO_CONTEXT) return
        if (!EGL14.eglMakeCurrent(d, eglSurface, eglSurface, ctx)) return

        val st = inputSurfaceTexture ?: return
        runCatching { st.updateTexImage() }
        lastFrameTimeMs = SystemClock.uptimeMillis()

        // Query real EGL surface size (TextureView window surface may not be 1:1 with virtual
        // content size).
        // We always render full-surface (no letterbox): UI side must enforce the correct aspect
        // ratio.
        val surfaceW = queryEglSurfaceInt(d, eglSurface, EGL14.EGL_WIDTH).takeIf { it > 0 } ?: outW
        val surfaceH = queryEglSurfaceInt(d, eglSurface, EGL14.EGL_HEIGHT).takeIf { it > 0 } ?: outH

        GLES20.glViewport(0, 0, surfaceW, surfaceH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val vertices =
                floatArrayOf(
                        -1f,
                        -1f,
                        1f,
                        -1f,
                        -1f,
                        1f,
                        1f,
                        1f,
                )
        // Base texture coords in normal (not manually flipped) orientation.
        // SurfaceTexture's transform matrix will handle the actual sampling region + orientation.
        val tex =
                floatArrayOf(
                        0f,
                        0f,
                        1f,
                        0f,
                        0f,
                        1f,
                        1f,
                        1f,
                )

        val vb =
                java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
                        .order(java.nio.ByteOrder.nativeOrder())
                        .asFloatBuffer()
        vb.put(vertices).position(0)

        val tb =
                java.nio.ByteBuffer.allocateDirect(tex.size * 4)
                        .order(java.nio.ByteOrder.nativeOrder())
                        .asFloatBuffer()
        tb.put(tex).position(0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glUniform1i(uTex, 0)

        // Apply SurfaceTexture transform matrix (crop padding / orientation) via fragment shader.
        val m = FloatArray(16)
        runCatching { st.getTransformMatrix(m) }
        GLES20.glUniformMatrix4fv(uSTMatrix, 1, false, m, 0)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vb)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, tb)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)

        // Presentation time must be set before swapping buffers on many drivers.
        EGLExt.eglPresentationTimeANDROID(d, eglSurface, System.nanoTime())
        EGL14.eglSwapBuffers(d, eglSurface)
    }

    private fun queryEglSurfaceInt(display: EGLDisplay, surface: EGLSurface, what: Int): Int {
        val out = IntArray(1)
        return if (EGL14.eglQuerySurface(display, surface, what, out, 0)) out[0] else 0
    }

    private fun createProgram(vsSrc: String, fsSrc: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val link = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, link, 0)
        if (link[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw IllegalStateException("link failed: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return p
    }

    private fun loadShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        val fixed = src.trimIndent().trim()
        GLES20.glShaderSource(s, fixed)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            val kind = if (type == GLES20.GL_VERTEX_SHADER) "vertex" else "fragment"
            throw IllegalStateException("compile failed ($kind): $log\n---source---\n$fixed")
        }
        return s
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes.firstOrNull() ?: throw IllegalStateException("No planes")
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        // Strictly use rowStride to create padded bitmap; do NOT assume width-based byte offsets.
        val paddedWidth = if (pixelStride > 0) rowStride / pixelStride else image.width
        val bmp = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bmp.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
    }

    companion object {
        private const val PREVIEW_FRAME_INTERVAL_MS = 120L

        private const val VS =
                """attribute vec4 aPos;
attribute vec2 aTex;
varying vec2 vTex;
void main() {
    gl_Position = aPos;
    vTex = aTex;
}
"""

        private const val FS =
                """#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTex;
uniform samplerExternalOES uTex;
uniform mat4 uSTMatrix;
void main() {
    vec4 t = uSTMatrix * vec4(vTex, 0.0, 1.0);
    gl_FragColor = texture2D(uTex, t.xy);
}
"""
    }
}
