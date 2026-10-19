package com.ai.phoneagent.core.automation

import android.content.Intent
import android.util.Log
import com.ai.phoneagent.viewmodel.AutomationViewModel
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 进程级鉴权器，守护自动化分发入口。
 *
 * MainActivity 是导出的 LAUNCHER activity，任何已安装应用都可以向它发送携带自动化
 * 控制字段（task / auto-start / source / force-top / keep-on-top）的显式 Intent。
 * 若无防护，恶意应用可触发无人值守的 UI 自动化。因此可信的应用内分发方会给每个
 * 自动化 Intent 附上一个随机进程级 token，接收方仅在该 token 匹配时才理会自动化字段。
 *
 * token 由 [SecureRandom]（256 位）在进程内生成一次，不持久化、不写入 saved-state，
 * 进程死亡即失效。校验使用常量时间比较。
 */
class AutomationDispatchAuthenticator {

    private val token: String = generateToken()

    /** 返回当前进程级的分发 token。 */
    fun token(): String = token

    /**
     * 当 [intent] 不含自动化控制字段（无需鉴权），或携带有效分发 token 时返回 true；
     * 当 Intent 含自动化控制字段但 token 缺失或无效时返回 false。
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

    /** 判断 Intent 是否携带任何需要鉴权的自动化控制字段。 */
    fun hasAutomationControlExtras(intent: Intent): Boolean {
        return intent.hasExtra(AutomationViewModel.EXTRA_AUTOMATION_TASK) ||
            intent.hasExtra(AutomationViewModel.EXTRA_AUTOMATION_AUTO_START) ||
            intent.hasExtra(AutomationViewModel.EXTRA_AUTOMATION_SOURCE)
    }

    /** 记录脱敏后的拒绝日志；不记录任务内容，也不记录任何 token。 */
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
