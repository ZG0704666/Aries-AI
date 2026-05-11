package com.ai.phoneagent.ui.debug

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.ai.phoneagent.BuildConfig

private const val COMPOSE_PERF_TAG = "ComposePerf"

private class RecomposeCounter(
    var count: Int = 0,
)

@Composable
fun DebugRecomposeLogger(
    scope: String,
    logEvery: Int = 25,
) {
    if (!BuildConfig.DEBUG) return

    val counter = remember(scope) { RecomposeCounter() }
    SideEffect {
        counter.count += 1
        if (counter.count == 1 || counter.count % logEvery == 0) {
            Log.d(COMPOSE_PERF_TAG, "$scope recomposed ${counter.count} times")
        }
    }
}