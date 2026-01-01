package com.ai.phoneagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class PhoneAgentAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: PhoneAgentAccessibilityService? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    data class ScreenshotData(
            val width: Int,
            val height: Int,
            val base64Png: String,
    )

    fun currentAppPackage(): String {
        return rootInActiveWindow?.packageName?.toString().orEmpty()
    }

    suspend fun tryCaptureScreenshotBase64(): ScreenshotData? {
        if (Build.VERSION.SDK_INT < 30) return null
        return suspendCancellableCoroutine { cont ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                takeScreenshot(
                        0,
                        executor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(
                                    screenshot: AccessibilityService.ScreenshotResult
                            ) {
                                try {
                                    val hw =
                                            Bitmap.wrapHardwareBuffer(
                                                    screenshot.hardwareBuffer,
                                                    screenshot.colorSpace
                                            )
                                    if (hw == null) {
                                        if (cont.isActive) cont.resume(null)
                                        return
                                    }

                                    val bmp = hw.copy(Bitmap.Config.ARGB_8888, false)
                                    hw.recycle()
                                    val out = ByteArrayOutputStream()
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    val bytes = out.toByteArray()
                                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                    val result = ScreenshotData(bmp.width, bmp.height, base64)
                                    bmp.recycle()
                                    if (cont.isActive) cont.resume(result)
                                } catch (_: Exception) {
                                    if (cont.isActive) cont.resume(null)
                                } finally {
                                    runCatching {
                                                screenshot.hardwareBuffer.close()
                                            }
                                    runCatching { executor.shutdown() }
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                runCatching { executor.shutdown() }
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                )
            } catch (_: Exception) {
                runCatching { executor.shutdown() }
                if (cont.isActive) cont.resume(null)
            } finally {
                cont.invokeOnCancellation { runCatching { executor.shutdownNow() } }
            }
        }
    }

    fun dumpUiTree(maxNodes: Int = 200): String {
        val root = rootInActiveWindow ?: return "(no active window)"
        val lines = StringBuilder()
        lines.appendLine("pkg=${root.packageName}")

        val q = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        q.add(root to 0)
        var count = 0
        while (q.isNotEmpty() && count < maxNodes) {
            val (node, depth) = q.removeFirst()
            count++
            val indent = "  ".repeat(depth.coerceAtMost(6))
            val r = Rect()
            node.getBoundsInScreen(r)
            val text = node.text?.toString()?.replace("\n", " ")?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.replace("\n", " ")?.trim().orEmpty()
            val vid = node.viewIdResourceName?.trim().orEmpty()
            val cls = node.className?.toString()?.trim().orEmpty()
            val flags =
                    buildString {
                                if (node.isClickable) append("C")
                                if (node.isEditable) append("E")
                                if (node.isFocusable) append("F")
                                if (node.isFocused) append("* ")
                            }
                            .trim()

            val summary =
                    listOf(
                                    if (text.isNotBlank()) "text=\"${text.take(40)}\"" else "",
                                    if (desc.isNotBlank()) "desc=\"${desc.take(40)}\"" else "",
                                    if (vid.isNotBlank()) "id=${vid.take(60)}" else "",
                                    if (cls.isNotBlank()) "cls=${cls.take(40)}" else "",
                                    "b=${r.left},${r.top},${r.right},${r.bottom}",
                                    if (flags.isNotBlank()) "flags=$flags" else ""
                            )
                            .filter { it.isNotBlank() }
                            .joinToString(" ")

            lines.append(indent).append("- ").appendLine(summary)

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child -> q.add(child to (depth + 1)) }
            }
        }
        if (count >= maxNodes) {
            lines.appendLine("(truncated, maxNodes=$maxNodes)")
        }
        return lines.toString().trim()
    }

    fun setTextOnFocused(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused =
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                        ?: findFirstEditableFocused(root) ?: return false

        if (!focused.isFocused) {
            focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findFirstEditableFocused(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var guard = 0
        while (q.isNotEmpty() && guard < 600) {
            guard++
            val n = q.removeFirst()
            if (n.isEditable && (n.isFocused || n.isFocusable)) {
                return n
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return null
    }

    fun runDemo() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({ swipeUp() }, 450)
        mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 1100)
    }

    fun click(x: Float, y: Float, durationMs: Long = 60L) {
        val p =
                Path().apply {
                    moveTo(x, y)
                    lineTo(x, y)
                }
        val stroke = GestureDescription.StrokeDescription(p, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun swipe(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Long = 300L,
    ) {
        val p =
                Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
        val stroke = GestureDescription.StrokeDescription(p, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun swipeUp() {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f
        val y1 = dm.heightPixels * 0.78f
        val y2 = dm.heightPixels * 0.32f
        swipe(x, y1, x, y2, 360L)
    }
}
