package com.ai.phoneagent.telemetry

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.ai.phoneagent.AriesAgentApp
import com.ai.phoneagent.BuildConfig
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.data.preferences.TelemetryPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class TelemetryHeartbeatManager(
    private val application: Application,
    private val okHttpClient: OkHttpClient,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val telemetryPreferencesRepository: TelemetryPreferencesRepository,
) {
    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L
        private const val MIN_HEARTBEAT_GAP_MS = 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sendMutex = Mutex()

    @Volatile
    private var startedActivityCount: Int = 0

    @Volatile
    private var heartbeatJob: Job? = null

    @Volatile
    private var sessionId: String = newSessionId()

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) {
            val previousCount = startedActivityCount
            startedActivityCount += 1
            if (previousCount == 0) {
                onAppForegrounded()
            }
        }

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) {
            startedActivityCount = max(0, startedActivityCount - 1)
            if (startedActivityCount == 0) {
                onAppBackgrounded()
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    private fun onAppForegrounded() {
        sessionId = newSessionId()
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            sendHeartbeatIfNeeded(force = false)
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendHeartbeatIfNeeded(force = true)
            }
        }
    }

    private fun onAppBackgrounded() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun sendHeartbeatIfNeeded(force: Boolean) {
        val endpoint = BuildConfig.TELEMETRY_HEARTBEAT_ENDPOINT.trim()
        if (endpoint.isBlank()) return
        if (!appPreferencesRepository.userAgreementAcceptedFlow.first()) return

        sendMutex.withLock {
            val now = System.currentTimeMillis()
            val lastHeartbeatAtMs = telemetryPreferencesRepository.getLastHeartbeatAtMs()
            if (!force && now - lastHeartbeatAtMs < MIN_HEARTBEAT_GAP_MS) return

            val installId = telemetryPreferencesRepository.getOrCreateInstallId()
            val payload = JSONObject()
                .put("installIdHash", sha256Hex(installId))
                .put("sessionId", sessionId)
                .put("platform", "android")
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("locale", Locale.getDefault().toLanguageTag())
                .put("eventTimeMs", now)
                .put("source", "foreground")
                .toString()

            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url(endpoint)
                        .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        response.isSuccessful
                    }
                }.getOrElse { error ->
                    AriesAgentApp.logw("Telemetry heartbeat failed", error)
                    false
                }
            }

            if (success) {
                telemetryPreferencesRepository.setLastHeartbeatAtMs(now)
            }
        }
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                append("%02x".format(byte))
            }
        }
    }
}