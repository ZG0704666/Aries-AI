package com.ai.phoneagent.core.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import com.ai.phoneagent.AppState

/** 公共显示工具类 整合了项目中重复的 dp 转换、窗口类型选择、flags 创建等逻辑 */
object DisplayUtils {

    /** dp 转 px (Int) */
    fun dp(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        dp.toFloat(),
                        context.resources.displayMetrics
                )
                .toInt()
    }

    /** dp 转 px (Float) */
    fun dpF(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.resources.displayMetrics
        )
    }

    /** 根据屏幕密度计算 dp 转 px (Int) 无需 Context，用于已有 density 的场景 */
    fun dp(density: Float, dp: Int): Int = (dp * density).toInt()

    /** 获取悬浮窗 WindowManager.LayoutParams 的 type 按优先级返回可用的类型列表 */
    fun getOverlayTypeCandidates(): List<Int> {
        val appCtx = AppState.getAppContext() ?: return getDefaultOverlayTypes()
        val accessibilityService = AccessibilityServiceHelper.getAccessibilityService()

        return buildList {
            // 1. 检查悬浮窗权限，优先使用 APPLICATION_OVERLAY（更稳定）
            val overlayPermOk =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(appCtx)
                    } else {
                        true
                    }

            if (overlayPermOk) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    add(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                } else {
                    @Suppress("DEPRECATION") add(WindowManager.LayoutParams.TYPE_PHONE)
                }
            }

            // 2. 无障碍服务作为兜底（用于无悬浮窗权限或机型兼容）
            if (accessibilityService != null) {
                add(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            }

            // 3. 防御性兜底，确保不返回空列表
            if (isEmpty()) {
                addAll(getDefaultOverlayTypes())
            }
        }
    }

    private fun getDefaultOverlayTypes(): List<Int> {
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                @Suppress("DEPRECATION") add(WindowManager.LayoutParams.TYPE_PHONE)
            }
        }
    }

    /**
     * 创建悬浮窗标准 flags
     * - 不获取焦点
     * - 不阻塞触摸事件传递到下层
     * - 布局在屏幕内
     */
    fun createOverlayFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    /** 创建简单的悬浮窗 flags (仅不获取焦点) */
    fun createSimpleOverlayFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    /** 创建底部居中的 gravity */
    fun createBottomCenterGravity(): Int {
        return Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    }

    /** 创建右上角的 gravity */
    fun createTopEndGravity(): Int {
        return Gravity.TOP or Gravity.END
    }

    /** 辅助获取无障碍服务实例 */
    private object AccessibilityServiceHelper {
        fun getAccessibilityService(): Any? {
            return try {
                val clazz = Class.forName("com.ai.phoneagent.PhoneAgentAccessibilityService")
                val field = clazz.getDeclaredField("instance")
                field.isAccessible = true
                field.get(null)
            } catch (e: Exception) {
                null
            }
        }
    }
}
