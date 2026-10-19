package com.ai.phoneagent.ui.debug

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.ai.phoneagent.BuildConfig

private const val COMPOSE_PERF_TAG = "ComposePerf"

/** 重组次数计数器，仅用于调试日志。 */
private class RecomposeCounter(
    var count: Int = 0,
)

/**
 * 调试用重组日志：记录指定作用域的重组次数，仅在 Debug 构建生效。
 * @param scope 日志中标识的作用域名称
 * @param logEvery 每隔多少次重组打印一次日志
 */
@Composable
fun DebugRecomposeLogger(
    scope: String,
    logEvery: Int = 25,
) {
    if (!BuildConfig.DEBUG) return

    val counter = remember(scope) { RecomposeCounter() }
    SideEffect {
        counter.count += 1
        // 首次及每隔 logEvery 次打印，避免日志刷屏
        if (counter.count == 1 || counter.count % logEvery == 0) {
            Log.d(COMPOSE_PERF_TAG, "$scope recomposed ${counter.count} times")
        }
    }
}