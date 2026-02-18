/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Licensed under AGPL-3.0. See LICENSE for details.
 */
package com.ai.phoneagent

import android.content.Context

/**
 * 虚拟屏配置管理器 — 集中管理分辨率预设、DPI、16像素对齐等参数。
 *
 * 对齐参考：autoglm_KY-master ConfigManager 的虚拟屏配置部分。
 *
 * **关键特性**
 * - 分辨率预设：480P / 720P / 1080P
 * - 16像素对齐（`align16`）：避免视频编码器 / GPU 黑边问题
 * - DPI 范围校验：72–640
 * - 持久化存储到 SharedPreferences
 */
object VirtualDisplayConfig {

    private const val PREFS_NAME = "virtual_display_config"

    // ─── 分辨率预设 ───
    const val RES_480P = "480P"
    const val RES_720P = "720P"
    const val RES_1080P = "1080P"

    val RESOLUTION_PRESETS = setOf(RES_480P, RES_720P, RES_1080P)
    const val DEFAULT_RESOLUTION = RES_1080P // 用户选择：默认1080P

    // ─── DPI ───
    const val DEFAULT_DPI = 480
    private const val DPI_MIN = 72
    private const val DPI_MAX = 640

    // ─── SharedPreferences Keys ───
    private const val KEY_RESOLUTION_PRESET = "resolution_preset"
    private const val KEY_DPI = "virtual_display_dpi"
    private const val KEY_WIDTH = "virtual_display_width"
    private const val KEY_HEIGHT = "virtual_display_height"
    private const val KEY_USE_VIRTUAL_DISPLAY = "use_virtual_display"
    private const val KEY_USE_SHIZUKU_INTERACTION = "use_shizuku_interaction"

    // ════════════════════════════════════════════
    //  16 像素对齐
    // ════════════════════════════════════════════

    /** 将尺寸对齐到最近的 16 倍数（最小16）。 原因：GPU / MediaCodec 编码器通常要求 16 对齐，否则可能出现黑边、显存浪费或崩溃。 */
    fun align16(value: Int): Int {
        val v = value.coerceAtLeast(1)
        val down = (v / 16) * 16
        val up = ((v + 15) / 16) * 16
        return if (v - down <= up - v) down.coerceAtLeast(16) else up
    }

    // ════════════════════════════════════════════
    //  预设 → 尺寸
    // ════════════════════════════════════════════

    /**
     * 将预设字符串转换为 (宽, 高)，已 16 像素对齐。
     * - 480P → 480 × 848
     * - 720P → 720 × 1280
     * - 1080P → 1088 × 1920 （1080 对齐后 = 1088）
     */
    fun presetToSize(preset: String): Pair<Int, Int> {
        return when (preset) {
            RES_720P -> align16(720) to align16(1280)
            RES_1080P -> align16(1088) to align16(1920)
            else -> align16(480) to align16(848)
        }
    }

    // ════════════════════════════════════════════
    //  读取 / 写入
    // ════════════════════════════════════════════

    private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 获取当前分辨率预设 */
    fun getResolutionPreset(context: Context): String {
        return prefs(context)
                .getString(KEY_RESOLUTION_PRESET, DEFAULT_RESOLUTION)
                .orEmpty()
                .ifEmpty { DEFAULT_RESOLUTION }
                .takeIf { it in RESOLUTION_PRESETS }
                ?: DEFAULT_RESOLUTION
    }

    /** 设置分辨率预设（同时更新宽高缓存） */
    fun setResolutionPreset(context: Context, preset: String) {
        val safe = preset.takeIf { it in RESOLUTION_PRESETS } ?: DEFAULT_RESOLUTION
        val (w, h) = presetToSize(safe)
        prefs(context)
                .edit()
                .putString(KEY_RESOLUTION_PRESET, safe)
                .putInt(KEY_WIDTH, w)
                .putInt(KEY_HEIGHT, h)
                .apply()
    }

    /** 获取 DPI（带范围校验） */
    fun getDpi(context: Context): Int {
        return prefs(context).getInt(KEY_DPI, DEFAULT_DPI).takeIf { it in DPI_MIN..DPI_MAX }
                ?: DEFAULT_DPI
    }

    /** 设置 DPI（带范围校验） */
    fun setDpi(context: Context, dpi: Int) {
        val safe = dpi.takeIf { it in DPI_MIN..DPI_MAX } ?: DEFAULT_DPI
        prefs(context).edit().putInt(KEY_DPI, safe).apply()
    }

    /** 获取虚拟屏宽高（已 16 对齐）。 如果 SP 中有缓存值且合法，直接返回；否则从预设计算。 */
    fun getSize(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        val cachedW = p.getInt(KEY_WIDTH, 0)
        val cachedH = p.getInt(KEY_HEIGHT, 0)
        if (cachedW > 0 && cachedH > 0) return cachedW to cachedH
        // 从预设计算并缓存
        val preset = getResolutionPreset(context)
        val (w, h) = presetToSize(preset)
        p.edit().putInt(KEY_WIDTH, w).putInt(KEY_HEIGHT, h).apply()
        return w to h
    }

    /** 保存执行模式选择（虚拟屏 / 前台） */
    fun setUseVirtualDisplay(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_VIRTUAL_DISPLAY, value).apply()
    }

    /** 读取执行模式选择 */
    fun getUseVirtualDisplay(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_USE_VIRTUAL_DISPLAY, false)
    }

    fun getUseShizukuInteraction(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_USE_SHIZUKU_INTERACTION, false)
    }

    fun setUseShizukuInteraction(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_SHIZUKU_INTERACTION, value).apply()
    }

    /** 返回摘要字符串（调试 / 日志用） */
    fun summary(context: Context): String {
        val preset = getResolutionPreset(context)
        val (w, h) = getSize(context)
        val dpi = getDpi(context)
        return "${preset} (${w}×${h}) DPI=$dpi"
    }
}
