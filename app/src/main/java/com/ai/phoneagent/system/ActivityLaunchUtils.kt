/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Licensed under AGPL-3.0. See LICENSE for details.
 */
package com.ai.phoneagent.system

import android.util.Log
import com.ai.phoneagent.ShizukuBridge
import java.util.regex.Pattern

/**
 * Activity启动工具类 - 支持在指定display上启动应用
 * 
 * 对齐参考项目 autoglm_KY 的多候选命令降级策略
 */
object ActivityLaunchUtils {

    private const val TAG = "ActivityLaunchUtils"

    /**
     * 在指定display上启动应用
     * 
     * 策略优先级：
     * 1. cmd activity start-activity --display（Shell权限，最可靠）
     * 2. am start --display（传统方式）
     * 3. monkey --display（兜底）
     * 4. cmd activity task move-to-display（任务迁移）
     * 
     * @param packageName 要启动的应用包名
     * @param displayId 目标display ID
     * @return 是否成功
     */
    fun launchAppOnDisplay(packageName: String, displayId: Int): Boolean {
        if (packageName.isBlank() || displayId <= 0) {
            return false
        }

        // 解析目标应用的启动component
        val component = resolveLauncherComponent(packageName)
        
        // FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_REORDER_TO_FRONT
        val flags = 0x10200000
        
        val candidates = mutableListOf<String>()
        
        if (component.isNotBlank()) {
            // 优先使用明确的component启动
            candidates.addAll(listOf(
                "cmd activity start-activity --user 0 --display $displayId --windowingMode 1 --activity-reorder-to-front -n $component -f $flags",
                "cmd activity start-activity --user 0 --display $displayId --windowingMode 1 -n $component -f $flags",
                "cmd activity start-activity --user 0 --display $displayId --activity-reorder-to-front -n $component -f $flags",
                "cmd activity start-activity --user 0 --display $displayId -n $component -f $flags",
                "am start --user 0 -n $component --display $displayId --activity-reorder-to-front -f $flags",
                "am start --user 0 -n $component --display $displayId -f $flags",
            ))
        }

        // 回退到package方式启动
        candidates.addAll(listOf(
            "cmd activity start-activity --user 0 --display $displayId -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $packageName",
            "am start --user 0 --display $displayId -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $packageName",
            "am start --display $displayId -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $packageName",
        ))

        // 执行候选命令
        for (cmd in candidates) {
            try {
                val result = ShizukuBridge.execText(cmd)
                if (result != null && !result.contains("error") && !result.contains("Exception")) {
                    Log.i(TAG, "launchApp succeeded: $cmd")
                    // 尝试任务迁移，解决部分ROM忽略--display的问题
                    moveExistingTaskToDisplay(packageName, displayId)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "launchApp failed: $cmd", e)
            }
        }

        // 兜底：使用monkey
        return tryMonkeyStart(packageName, displayId)
    }

    /**
     * 尝试使用monkey启动应用
     */
    private fun tryMonkeyStart(packageName: String, displayId: Int): Boolean {
        return try {
            val cmd = "monkey --display $displayId -p $packageName -c android.intent.category.LAUNCHER 1"
            val result = ShizukuBridge.execText(cmd)
            result != null
        } catch (e: Exception) {
            Log.w(TAG, "tryMonkeyStart failed", e)
            false
        }
    }

    /**
     * 解析应用的LAUNCHER Activity component
     */
    fun resolveLauncherComponent(packageName: String): String {
        if (packageName.isBlank()) return ""

        val candidates = listOf(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $packageName",
            "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $packageName",
        )

        for (cmd in candidates) {
            try {
                val result = ShizukuBridge.execText(cmd)
                if (result.isNullOrBlank()) continue

                val lines = result.trim().lines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.contains("/") && trimmed.contains(packageName)) {
                        // 匹配 pkg/cls 格式
                        val match = Pattern.compile("([\\w.]+/[\\w.\\$]+)").matcher(trimmed)
                        if (match.find()) {
                            val component = match.group(1)
                            if (component?.startsWith("$packageName/") == true) {
                                return component
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "resolveLauncherComponent failed for $packageName", e)
            }
        }
        return ""
    }

    /**
     * 将已存在的任务迁移到指定display
     * 
     * 部分ROM会忽略--display参数，需要启动后再迁移
     */
    fun moveExistingTaskToDisplay(packageName: String, displayId: Int): Boolean {
        if (packageName.isBlank() || displayId <= 0) return false

        val taskId = findTaskIdForPackage(packageName)
        if (taskId == null) {
            Log.d(TAG, "No task found for $packageName")
            return false
        }

        val candidates = listOf(
            "cmd activity task move-to-display $taskId $displayId",
            "cmd activity task move-task-to-display $taskId $displayId",
            "cmd activity move-task $taskId $displayId",
            "am task move-to-display $taskId $displayId",
        )

        for (cmd in candidates) {
            try {
                val result = ShizukuBridge.execText(cmd)
                if (result != null) {
                    Log.i(TAG, "moveTaskToDisplay succeeded: $cmd")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "moveTaskToDisplay failed: $cmd", e)
            }
        }
        return false
    }

    /**
     * 从dumpsys中查找包名对应的task ID
     */
    private fun findTaskIdForPackage(packageName: String): Int? {
        if (packageName.isBlank()) return null

        return try {
            val result = ShizukuBridge.execText("dumpsys activity activities")
            if (result.isNullOrBlank()) return null

            val pkg = packageName.lowercase()
            val taskIdPattern = Pattern.compile("\\btaskId=(\\d+)\\b")
            val taskHashPattern = Pattern.compile("Task\\{[^}]*#(\\d+)")

            var currentTaskId: Int? = null
            var matchedInBlock = false
            var best: Int? = null

            val lines = result.split("\n")
            for (line in lines) {
                val trimmed = line.trim()

                // 查找taskId
                val taskIdMatcher = taskIdPattern.matcher(trimmed)
                if (taskIdMatcher.find()) {
                    currentTaskId = taskIdMatcher.group(1)?.toIntOrNull()
                    matchedInBlock = false
                }

                // 如果没找到taskId，尝试task hash
                if (currentTaskId == null) {
                    val hashMatcher = taskHashPattern.matcher(trimmed)
                    if (hashMatcher.find()) {
                        currentTaskId = hashMatcher.group(1)?.toIntOrNull()
                        matchedInBlock = false
                    }
                }

                // 检查是否在匹配包名的块中
                if (currentTaskId != null) {
                    if (trimmed.lowercase().contains("$pkg/")) {
                        matchedInBlock = true
                    }
                }

                // 如果当前task不匹配包名，保存之前的最佳匹配
                if (currentTaskId != null && !matchedInBlock && best == null) {
                    best = currentTaskId
                    currentTaskId = null
                }
            }

            // 返回最佳匹配
            best ?: currentTaskId
        } catch (e: Exception) {
            Log.w(TAG, "findTaskIdForPackage failed", e)
            null
        }
    }

    /**
     * 确保焦点在指定display上
     */
    fun ensureFocusedDisplay(displayId: Int): Boolean {
        if (displayId <= 0) return false

        val candidates = listOf(
            "cmd input set-focused-display $displayId",
            "wm set-focused-display $displayId",
        )

        for (cmd in candidates) {
            try {
                val result = ShizukuBridge.execText(cmd)
                if (result != null) {
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "ensureFocusedDisplay failed: $cmd", e)
            }
        }
        return false
    }
}
