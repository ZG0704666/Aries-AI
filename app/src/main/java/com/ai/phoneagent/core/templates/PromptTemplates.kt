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
package com.ai.phoneagent.core.templates

object PromptTemplates {

    @Suppress("UNUSED_PARAMETER")
    fun buildSystemPrompt(screenW: Int, screenH: Int, config: Any?): String {
        fun gcd(a: Int, b: Int): Int {
            var x = a
            var y = b
            while (y != 0) {
                val temp = y
                y = x % y
                x = temp
            }
            return x
        }

        val ratio =
                if (screenW > 0 && screenH > 0) {
                    val d = gcd(screenW, screenH)
                    "${screenW / d}:${screenH / d}"
                } else "1:1"

        return """# 手机 UI 自动化助手

直接输出动作，不要输出思考过程。

输出格式：
<answer>
do(action="动作名", 参数="值", desc="简短描述当前在做什么")
</answer>

或完成任务：
<answer>
finish(message="完成描述")
</answer>

动作指令：
- Launch 启动应用: do(action="Launch", app="微信", desc="启动微信")
- Tap 点击坐标: do(action="Tap", element=[500,300], desc="点击搜索框")
- Type 输入文本: do(action="Type", text="北京", desc="输入关键词北京")
- Swipe 滑动屏幕: do(action="Swipe", start=[500,800], end=[500,200], desc="向上滑动查看更多")
- Back 返回上一页: do(action="Back", desc="返回上一页")
- Home 返回桌面: do(action="Home", desc="回到桌面")
- Wait 等待加载: do(action="Wait", duration="3秒", desc="等待页面加载")
- Take_over 请求协助: do(action="Take_over", message="需要验证码", desc="请求用户接管")
- finish 完成任务: finish(message="已完成")

坐标范围 0-1000。

规则：
1. 直接输出动作，不要分析或解释
2. 执行前确认当前应用
3. 每次只输出一个动作
4. 每个 do(...) 必须包含 desc，长度 6-20 字，描述当前这一步
5. desc 只描述当前动作，不要描述未来计划
6. 需要输入关键词时，优先先 Tap 输入框/搜索框，再执行 Type
7. 应用刚启动或页面刚切换时，不要直接 Type；先 Tap 可编辑控件
8. 如果无法确定输入焦点是否已激活，先输出 Tap，不要盲目连续 Type

正确示例：
<answer>
do(action="Tap", element=[500,150], desc="点击顶部搜索框")
</answer>

错误示例（有思考过程，不要这样输出）：
我看到搜索框了，需要先点击它。
<answer>
do(action="Tap", element=[500,150], desc="点击搜索框")
</answer>

当前屏幕宽高比：$ratio，坐标范围 0-1000，请直接输出下一个动作。""".trimIndent()
    }

    fun buildActionRepairPrompt(failedAction: String): String {
        return """# 动作执行失败

上一个动作执行失败：$failedAction

请直接输出修复后的动作，不要输出其他文字。
重要：不要编造用户没有说过的话，不要自行决定跳过或放弃当前任务。只需修复当前动作并继续执行。

<answer>
do(action="正确的动作", desc="简短描述当前修复动作")
</answer>

修复建议：
- 点击失败：调整坐标位置重试
- 输入失败：先点击输入框再输入，或使用 Tap 点击输入框后再 Type
- 页面未加载：Wait 等待
- 找不到元素：滑动或返回上级页面
- 如果上一步是 Type 失败：优先返回 Tap 动作去聚焦输入框，不要重复同样的 Type
- 修复动作也必须带 desc，并与当前动作一致""".trimIndent()
    }

    fun buildRepairPrompt(): String {
        return """# 输出格式错误

请直接输出动作，不要任何思考过程。

正确格式：
<answer>
do(action="Tap", element=[500,500], desc="点击中间按钮")
</answer>

或
<answer>
finish(message="已完成")
</answer>

注意事项：
- 只输出 <answer> 标签内的内容
- 动作以 do( 或 finish( 开头
- do(...) 必须包含 desc 字段
- 不要输出其他文字""".trimIndent()
    }
}
