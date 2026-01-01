package com.ai.assistance.showerclient

import android.content.Context
import android.os.IBinder
import android.util.Log
import com.ai.assistance.shower.IShowerService
import com.ai.assistance.shower.IShowerVideoSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Lightweight controller to talk to the Shower server running locally on the device.
 *
 * Responsibilities:
 * - Maintain a Binder connection to the Shower service
 * - Send commands for a specific virtual display: ensureDisplay, launchApp, tap, swipe, touch, key, screenshot
 * - Track the virtual display id and video size for this session
 */
class ShowerController {

    companion object {
        private const val TAG = "ShowerController"

        @Volatile
        private var binderService: IShowerService? = null

        private suspend fun getBinder(context: Context? = null): IShowerService? = withContext(Dispatchers.IO) {
            if (binderService?.asBinder()?.isBinderAlive == true) {
                return@withContext binderService
            }

            fun clearDeadService() {
                binderService = null
                ShowerBinderRegistry.setService(null)
            }

            val maxAttempts = if (context != null) 2 else 1
            var attempt = 0
            while (attempt < maxAttempts) {
                attempt++
                try {
                    val cachedService = ShowerBinderRegistry.getService()
                    val binder = cachedService?.asBinder()
                    val alive = binder?.isBinderAlive == true
                    Log.d(TAG, "getBinder: attempt=$attempt cachedService=$cachedService binder=$binder alive=$alive")
                    if (cachedService != null && alive) {
                        binderService = cachedService
                        Log.d(TAG, "Connected to Shower Binder service on attempt=$attempt")
                        return@withContext binderService
                    } else {
                        Log.w(TAG, "No alive Shower Binder cached in ShowerBinderRegistry on attempt=$attempt")
                        clearDeadService()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to Binder service on attempt=$attempt", e)
                    clearDeadService()
                }

                if (context != null && attempt == 1) {
                    try {
                        val ctx = context.applicationContext
                        Log.d(TAG, "getBinder: attempting to restart Shower server after connection failure")
                        val ok = ShowerServerManager.ensureServerStarted(ctx)
                        if (!ok) {
                            Log.e(TAG, "getBinder: failed to restart Shower server")
                            break
                        }
                        // Wait a bit for broadcast to propagate
                        delay(200)
                    } catch (e: Exception) {
                        Log.e(TAG, "getBinder: exception while restarting Shower server", e)
                        break
                    }
                }
            }
            null
        }
    }

    @Volatile
    private var virtualDisplayId: Int? = null

    fun getDisplayId(): Int? = virtualDisplayId

    @Volatile
    private var videoWidth: Int = 0

    @Volatile
    private var videoHeight: Int = 0

    fun getVideoSize(): Pair<Int, Int>? =
        if (videoWidth > 0 && videoHeight > 0) Pair(videoWidth, videoHeight) else null

    private val binaryLock = Any()
    private val earlyBinaryFrames = ArrayDeque<ByteArray>()

    @Volatile
    private var binaryHandler: ((ByteArray) -> Unit)? = null

    fun setBinaryHandler(handler: ((ByteArray) -> Unit)?) {
        val framesToReplay: List<ByteArray>
        synchronized(binaryLock) {
            binaryHandler = handler
            Log.d(TAG, "setBinaryHandler: id=${virtualDisplayId} handlerSet=${handler != null}, bufferedFrames=${earlyBinaryFrames.size}")
            framesToReplay = if (handler != null && earlyBinaryFrames.isNotEmpty()) {
                val list = earlyBinaryFrames.toList()
                earlyBinaryFrames.clear()
                list
            } else {
                emptyList()
            }
        }
        if (handler != null && framesToReplay.isNotEmpty()) {
            Log.d(TAG, "setBinaryHandler: replaying ${framesToReplay.size} buffered frames")
            framesToReplay.forEach { frame ->
                try {
                    handler(frame)
                } catch (_: Exception) {
                }
            }
        }
    }

    private val videoSink = object : IShowerVideoSink.Stub() {
        override fun onVideoFrame(data: ByteArray) {
            val handler: ((ByteArray) -> Unit)?
            synchronized(binaryLock) {
                handler = binaryHandler
                if (handler == null) {
                    if (earlyBinaryFrames.size >= 120) {
                        earlyBinaryFrames.removeFirst()
                    }
                    earlyBinaryFrames.addLast(data)
                }
            }
            handler?.invoke(data)
        }
    }

    suspend fun requestScreenshot(timeoutMs: Long = 3000L): ByteArray? =
        withContext(Dispatchers.IO) {
            val service = getBinder() ?: return@withContext null
            val id = virtualDisplayId ?: return@withContext null
            try {
                withTimeout(timeoutMs) {
                    service.requestScreenshot(id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "requestScreenshot failed for $id", e)
                null
            }
        }

    suspend fun ensureDisplay(
        context: Context,
        width: Int,
        height: Int,
        dpi: Int,
        bitrateKbps: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder(context) ?: return@withContext false
        try {
            // Align size
            val alignedWidth = width and -8
            val alignedHeight = height and -8
            val targetWidth = if (alignedWidth > 0) alignedWidth else width
            val targetHeight = if (alignedHeight > 0) alignedHeight else height

            // Changed: ensureDisplay now returns the ID and doesn't destroy existing ones.
            val id = service.ensureDisplay(targetWidth, targetHeight, dpi, bitrateKbps ?: 0)
            if (id < 0) {
                virtualDisplayId = null
                Log.e(TAG, "ensureDisplay: server reported invalid displayId=$id")
                return@withContext false
            }
            
            virtualDisplayId = id
            videoWidth = targetWidth
            videoHeight = targetHeight
            
            // Link local sink to this display on the server
            service.setVideoSink(id, videoSink.asBinder())
            
            Log.d(TAG, "ensureDisplay complete, new displayId=$virtualDisplayId, size=${videoWidth}x${videoHeight}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ensureDisplay failed", e)
            false
        }
    }

    suspend fun launchApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        if (packageName.isBlank()) return@withContext false
        try {
            service.launchApp(packageName, id)
            true
        } catch (e: Exception) {
            Log.e(TAG, "launchApp failed for $packageName on $id", e)
            false
        }
    }

    suspend fun tap(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.tap(id, x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "tap($x, $y) failed on $id", e)
            false
        }
    }

    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300L,
    ): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.swipe(id, startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), durationMs)
            true
        } catch (e: Exception) {
            Log.e(TAG, "swipe failed on $id", e)
            false
        }
    }

    suspend fun touchDown(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.touchDown(id, x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "touchDown($x, $y) failed on $id", e)
            false
        }
    }

    suspend fun touchMove(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.touchMove(id, x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "touchMove($x, $y) failed on $id", e)
            false
        }
    }

    suspend fun touchUp(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.touchUp(id, x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "touchUp($x, $y) failed on $id", e)
            false
        }
    }

    fun shutdown() {
        Log.d(TAG, "shutdown requested for display $virtualDisplayId")
        val service = binderService
        val id = virtualDisplayId
        virtualDisplayId = null
        videoWidth = 0
        videoHeight = 0
        synchronized(binaryLock) {
            binaryHandler = null
            earlyBinaryFrames.clear()
        }
        if (id != null && service?.asBinder()?.isBinderAlive == true) {
            try {
                service.setVideoSink(id, null)
                service.destroyDisplay(id)
            } catch (e: Exception) {
                Log.e(TAG, "shutdown: destroyDisplay failed for $id", e)
            }
        }
    }

    suspend fun key(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.injectKey(id, keyCode)
            true
        } catch (e: Exception) {
            Log.e(TAG, "key($keyCode) failed on $id", e)
            false
        }
    }
}
