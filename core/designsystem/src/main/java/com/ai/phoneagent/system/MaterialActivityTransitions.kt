package com.ai.phoneagent.system

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.AnimRes
import androidx.core.content.ContextCompat

/** 一组进入/退出动画资源。 */
private data class MaterialTransitionSpec(
    @AnimRes val enter: Int,
    @AnimRes val exit: Int,
)

/** 前进方向（向右打开）的过渡动画。 */
private val forwardTransitionSpec =
    MaterialTransitionSpec(
        enter = com.google.android.material.R.anim.m3_side_sheet_enter_from_right,
        exit = com.google.android.material.R.anim.m3_side_sheet_exit_to_left,
    )

/** 返回方向（向左关闭）的过渡动画。 */
private val backwardTransitionSpec =
    MaterialTransitionSpec(
        enter = com.google.android.material.R.anim.m3_side_sheet_enter_from_left,
        exit = com.google.android.material.R.anim.m3_side_sheet_exit_to_right,
    )

/** 启动 Activity 并应用前进过渡动画。 */
fun Activity.startActivityWithMaterialForwardTransition(intent: Intent) {
    startActivity(intent)
    applyMaterialOpenTransition()
}

/** 为当前 Activity 应用前进（打开）过渡动画。 */
fun Activity.applyMaterialOpenTransition() {
    applyMaterialTransition(isOpen = true, spec = forwardTransitionSpec)
}

/** 为当前 Activity 应用返回（关闭）过渡动画。 */
fun Activity.applyMaterialCloseTransition() {
    applyMaterialTransition(isOpen = false, spec = backwardTransitionSpec)
}

/**
 * 从任意 Context 启动 Activity 并应用前进过渡动画。
 *
 * 非 Activity 的 Context 会补上 `NEW_TASK` 标志，并通过 [ActivityOptions] 携带动画。
 */
fun Context.startActivityWithMaterialForwardTransition(intent: Intent) {
    if (this is Activity) {
        startActivityWithMaterialForwardTransition(intent)
        return
    }

    val launchIntent =
        Intent(intent).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    ContextCompat.startActivity(this, launchIntent, createMaterialForwardOptions())
}

/** 构建携带前进过渡动画的 ActivityOptions Bundle，失败时返回 null。 */
fun Context.createMaterialForwardOptions(): Bundle? {
    return runCatching {
        ActivityOptions
            .makeCustomAnimation(this, forwardTransitionSpec.enter, forwardTransitionSpec.exit)
            .toBundle()
    }.getOrNull()
}

/**
 * 应用过渡动画。
 *
 * Android 14 (UPSIDE_DOWN_CAKE) 及以上使用 [Activity.overrideActivityTransition]，低版本回退到已废弃的 `overridePendingTransition`。
 */
private fun Activity.applyMaterialTransition(
    isOpen: Boolean,
    spec: MaterialTransitionSpec,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val transitionType =
            if (isOpen) {
                Activity.OVERRIDE_TRANSITION_OPEN
            } else {
                Activity.OVERRIDE_TRANSITION_CLOSE
            }
        overrideActivityTransition(transitionType, spec.enter, spec.exit)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(spec.enter, spec.exit)
    }
}
