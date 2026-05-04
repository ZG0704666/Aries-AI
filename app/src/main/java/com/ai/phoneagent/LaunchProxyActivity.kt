package com.ai.phoneagent

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/** 透明跳板 Activity，用于在前台安全地拉起目标应用，避免"后台启动应用"弹窗。 支持将应用启动到指定 displayId（虚拟屏模式）。 */
class LaunchProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target: Intent? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(EXTRA_TARGET_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_TARGET_INTENT)
                }

        val displayId = intent?.getIntExtra(EXTRA_DISPLAY_ID, -1) ?: -1

        if (target != null) {
            target.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

            if (displayId > 0) {
                // 虚拟屏模式：通过 ActivityOptions.setLaunchDisplayId 启动到指定 display
                val launched = launchOnTargetDisplay(target, displayId)
                if (!launched) {
                    // fallback: 通过 Shizuku shell 命令启动
                    launchViaShellFallback(target, displayId)
                }
            } else {
                runCatching { startActivity(target) }
            }
        }

        finish()
        overridePendingTransition(0, 0)
    }

    /** 通过 ActivityOptions.setLaunchDisplayId 启动应用到指定 display */
    private fun launchOnTargetDisplay(target: Intent, displayId: Int): Boolean {
        return try {
            val options = ActivityOptions.makeBasic()
            options.setLaunchDisplayId(displayId)
            startActivity(target, options.toBundle())
            Log.i(TAG, "Launched on display $displayId via ActivityOptions")
            true
        } catch (e: Exception) {
            Log.w(TAG, "ActivityOptions.setLaunchDisplayId failed: ${e.message}")
            false
        }
    }

    /** Fallback：通过 Shizuku 的 am start --display 命令启动 */
    private fun launchViaShellFallback(target: Intent, displayId: Int) {
        try {
            val component = target.component
            if (component != null) {
                val componentStr = "${component.packageName}/${component.className}"
                val flags = target.flags
                val candidates =
                        listOf(
                                "cmd activity start-activity --user 0 --display $displayId --windowingMode 1 -n $componentStr -f $flags",
                                "am start --user 0 --display $displayId -n $componentStr -f $flags",
                                "am start --display $displayId -n $componentStr -f $flags",
                        )
                for (cmd in candidates) {
                    val result = runCatching { ShizukuBridge.execResult(cmd) }.getOrNull()
                    if (result != null && result.exitCode == 0) {
                        Log.i(TAG, "Launched on display $displayId via shell: $cmd")
                        return
                    }
                }
                Log.w(TAG, "All shell fallback attempts failed for display $displayId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shell fallback failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LaunchProxy"
        private const val EXTRA_TARGET_INTENT = "target_intent"
        private const val EXTRA_DISPLAY_ID = "display_id"

        /** 启动应用到默认显示器（前台模式） */
        fun launch(context: Context, targetIntent: Intent) {
            val proxy = Intent(context, LaunchProxyActivity::class.java)
            proxy.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            proxy.putExtra(EXTRA_TARGET_INTENT, targetIntent)
            context.startActivity(proxy)
        }

        /** 启动应用到指定 displayId（虚拟屏模式） */
        fun launchOnDisplay(context: Context, targetIntent: Intent, displayId: Int) {
            if (displayId <= 0) {
                launch(context, targetIntent)
                return
            }
            val proxy = Intent(context, LaunchProxyActivity::class.java)
            proxy.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            proxy.putExtra(EXTRA_TARGET_INTENT, targetIntent)
            proxy.putExtra(EXTRA_DISPLAY_ID, displayId)
            context.startActivity(proxy)
        }
    }
}
