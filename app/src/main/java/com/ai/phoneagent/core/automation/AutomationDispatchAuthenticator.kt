package com.ai.phoneagent.core.automation

import android.content.Intent
import android.util.Log
import com.ai.phoneagent.viewmodel.AutomationViewModel
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Process-local authenticator guarding the automation dispatch entrypoint.
 *
 * MainActivity is an exported LAUNCHER activity, so any installed app can send it an
 * explicit Intent carrying automation control extras (task / auto-start / source /
 * force-top / keep-on-top). Without a guard a malicious app could trigger unattended
 * UI automation. The trusted in-app dispatcher therefore stamps every automation
 * Intent with a random per-process token, and the receiver only honours automation
 * extras when that token matches.
 *
 * The token is generated once per process from [SecureRandom] (256-bit), is never
 * persisted, never written to saved-state, and becomes invalid on process death.
 * Verification uses a constant-time comparison.
 */
class AutomationDispatchAuthenticator {

    private val token: String = generateToken()

    /** Returns the current process-local dispatch token. */
    fun token(): String = token

    /**
     * Returns true when [intent] carries no automation control extras (nothing to
     * authorize), or when it carries a valid dispatch token. Returns false when the
     * intent carries automation control extras but the token is missing or invalid.
     *
     * 鉴权范围仅覆盖真正会触发无人值守 UI 自动化的控制字段（task / autoStart / source）。
     * 纯展示字段（[AutomationViewModel.EXTRA_FORCE_TOP_ON_ENTRY] /
     * [AutomationViewModel.EXTRA_KEEP_MAIN_ON_TOP]）仅控制 Activity 进栈与置顶行为，
     * 不携带自动化指令，故移出鉴权范围——内部入口（AutomationOverlay、
     * AutomationLiveNotification、FloatingChatService.navigateToTaskDetail）可继续附带这些字段而无须 token。
     */
    fun isAuthorized(intent: Intent?): Boolean {
        intent ?: return true
        if (!hasAutomationControlExtras(intent)) return true
        val provided = intent.getStringExtra(AutomationViewModel.EXTRA_AUTOMATION_DISPATCH_TOKEN)
        return constantTimeEquals(provided, token)
    }

    /** True when the intent carries any automation control field that must be authorized. */
    fun hasAutomationControlExtras(intent: Intent): Boolean {
        return intent.hasExtra(AutomationViewModel.EXTRA_AUTOMATION_TASK) ||
            intent.hasExtra(AutomationViewModel.EXTRA_AUTOMATION_AUTO_START) ||
            intent.hasExtra(AutomationViewModel.EXTRA_AUTOMATION_SOURCE)
    }

    /** Logs a sanitized rejection; never logs the task content or any token. */
    fun logRejected(intent: Intent?) {
        val present = mutableListOf<String>()
        if (intent?.hasExtra(AutomationViewModel.EXTRA_AUTOMATION_TASK) == true) present.add("task")
        if (intent?.hasExtra(AutomationViewModel.EXTRA_AUTOMATION_AUTO_START) == true) present.add("autoStart")
        if (intent?.hasExtra(AutomationViewModel.EXTRA_AUTOMATION_SOURCE) == true) present.add("source")
        Log.w(TAG, "Rejected unauthorized automation intent fields=$present")
    }

    private fun constantTimeEquals(candidate: String?, expected: String): Boolean {
        candidate ?: return false
        val candidateBytes = candidate.toByteArray(Charsets.UTF_8)
        val expectedBytes = expected.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(candidateBytes, expectedBytes)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        // java.util.Base64（API 26+，minSdk 30 满足）：与 android.util.Base64 行为一致，
        // 且使本类可在纯 JVM 单元测试中实例化（android.util.Base64 在 local unit test 下为 stub）。
        return Base64.getEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        private const val TAG = "AutomationDispatchAuth"
        private const val TOKEN_BYTES = 32 // 256-bit
    }
}
