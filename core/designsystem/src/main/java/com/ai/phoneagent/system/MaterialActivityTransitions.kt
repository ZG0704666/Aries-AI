package com.ai.phoneagent.system

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.AnimRes
import androidx.core.content.ContextCompat

private data class MaterialTransitionSpec(
    @AnimRes val enter: Int,
    @AnimRes val exit: Int,
)

private val forwardTransitionSpec =
    MaterialTransitionSpec(
        enter = com.google.android.material.R.anim.m3_side_sheet_enter_from_right,
        exit = com.google.android.material.R.anim.m3_side_sheet_exit_to_left,
    )

private val backwardTransitionSpec =
    MaterialTransitionSpec(
        enter = com.google.android.material.R.anim.m3_side_sheet_enter_from_left,
        exit = com.google.android.material.R.anim.m3_side_sheet_exit_to_right,
    )

fun Activity.startActivityWithMaterialForwardTransition(intent: Intent) {
    startActivity(intent)
    applyMaterialOpenTransition()
}

fun Activity.applyMaterialOpenTransition() {
    applyMaterialTransition(isOpen = true, spec = forwardTransitionSpec)
}

fun Activity.applyMaterialCloseTransition() {
    applyMaterialTransition(isOpen = false, spec = backwardTransitionSpec)
}

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

fun Context.createMaterialForwardOptions(): Bundle? {
    return runCatching {
        ActivityOptions
            .makeCustomAnimation(this, forwardTransitionSpec.enter, forwardTransitionSpec.exit)
            .toBundle()
    }.getOrNull()
}

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
