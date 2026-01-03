package com.ai.phoneagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * 透明跳板 Activity，用于在前台安全地拉起目标应用，避免“后台启动应用”弹窗。
 */
class LaunchProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target: Intent? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(EXTRA_TARGET_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_TARGET_INTENT)
                }
        if (target != null) {
            target.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            runCatching { startActivity(target) }
        }

        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_TARGET_INTENT = "target_intent"

        fun launch(context: Context, targetIntent: Intent) {
            val proxy = Intent(context, LaunchProxyActivity::class.java)
            proxy.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            proxy.putExtra(EXTRA_TARGET_INTENT, targetIntent)
            context.startActivity(proxy)
        }
    }
}