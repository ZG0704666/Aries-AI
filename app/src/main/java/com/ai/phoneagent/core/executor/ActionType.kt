/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.core.executor

/**
 * 动作类型分类
 * 用于后续将 ActionExecutor 拆分为独立的执行器
 */
enum class ActionType {
    TAP,
    LONG_PRESS,
    DOUBLE_TAP,
    SWIPE,
    TYPE,
    LAUNCH,
    BACK,
    HOME,
    WAIT,
    TAKE_OVER,
    FINISH,
    UNKNOWN;

    companion object {
        /**
         * 从动作名称解析动作类型
         */
        fun fromName(rawName: String?): ActionType {
            if (rawName == null) return UNKNOWN
            val name = rawName.trim().trim('"', '\'', ' ').lowercase()
            val nameKey = name.replace(" ", "")
            return when (nameKey) {
                "tap", "click", "press" -> TAP
                "longpress", "long_press" -> LONG_PRESS
                "doubletap", "double_tap" -> DOUBLE_TAP
                "swipe", "scroll" -> SWIPE
                "type", "input", "text", "type_name" -> TYPE
                "launch", "open_app", "start_app" -> LAUNCH
                "back" -> BACK
                "home" -> HOME
                "wait", "sleep" -> WAIT
                "take_over", "takeover" -> TAKE_OVER
                "finish" -> FINISH
                else -> UNKNOWN
            }
        }
    }
}
