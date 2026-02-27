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

    private fun extractFailedTypeText(failedAction: String): String? {
        val isTypeAction =
                Regex("""action\s*=\s*["']?(type|input|text|type_name)["']?""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(failedAction)
        if (!isTypeAction) return null
        return Regex("""text\s*=\s*"([^"]*)"""")
                .find(failedAction)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildSystemPrompt(
            screenW: Int,
            screenH: Int,
            config: Any?,
            enforceDesc: Boolean = false,
    ): String {
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

        val descRuleText = buildSystemDescRule(enforceDesc)
        val intentTextRuleText = buildSystemIntentTextRule(enforceDesc)

        return """# 移动 UI 自动化核心提示词

请直接输出动作，不要输出其他说明。
输出格式：
<answer>
	do(action="操作名", 参数="值", desc="动作简述")
</answer>

可接受输出：
<answer>
	finish(message="完成任务")
</answer>

动作说明：
- 启动应用: do(action="Launch", app="应用名", desc="启动XXX")
- 点击: do(action="Tap", element=[500,300], desc="点击搜索框")
- 输入: do(action="Type", text="内容", desc="输入内容")
- 滑动: do(action="Swipe", start=[500,800], end=[500,200], desc="向上滑动")
- 返回: do(action="Back", desc="返回上一级")
- 主页: do(action="Home", desc="回到主页")
- 等待: do(action="Wait", duration="3", desc="等待页面加载")
- 人工接管: do(action="Take_over", message="需要用户确认", desc="请求用户接管")
- 完成: finish(message="任务完成")

坐标规则 0-1000。
建议流程：
1. 直接执行可执行动作
2. 执行点击/输入/滑动等
3. 每个动作先做截图再决策
4. $descRuleText
4.1 $intentTextRuleText
5. 若输入失败，优先“先 Tap 输入框再 Type”，避免直接 Type 失败
6. 如果页面停滞，先做一次 Scroll 或等待再重试
7. 避免连续无效 Tap；若连续两次 Tap 后界面无变化，必须改用 Swipe/Back/Wait/Launch 等其他动作

正常示例：
<answer>
	do(action="Tap", element=[500,150], desc="点击顶部搜索")
</answer>

错误示例：
<answer>
	do(action="Tap", element=[500,150], desc="点击顶部搜索")
</answer>

当前屏幕比例：$ratio
范围 0-1000，优先输出下一步动作。""".trimIndent()
    }

    fun buildActionRepairPrompt(failedAction: String, enforceDesc: Boolean = false): String {
        val descRuleText = buildRepairDescRule(enforceDesc)
        val intentTextRuleText = buildRepairIntentTextRule(enforceDesc)
        val failedTypeText = extractFailedTypeText(failedAction).orEmpty()
        val forceKeyboardTap = failedTypeText.any { it.code > 127 }
        val targetKey = failedTypeText.firstOrNull()?.toString().orEmpty()

        val inputRepairRule =
                if (forceKeyboardTap) {
                    """
- 当前失败动作是中文 Type，本次先执行“Tap 输入框重新聚焦”
- 下一步优先尝试 Type（不要长期只输出 Tap）
- 仅当 Type 再次失败时，才改为 Tap 软键盘按键输入
- 若改为键盘 Tap，优先点击“$targetKey”键；多字文本按顺序逐字 Tap
                    """.trimIndent()
                } else {
                    "- 若为输入场景，先 Tap 再 Type，或使用可点击输入框后再 Type"
                }

        val repairExample =
                if (forceKeyboardTap) {
                    """do(action="Tap", element=[500,500], desc="点击目标输入框重新聚焦")"""
                } else {
                    """do(action="Tap", element=[500,150], desc="点击顶部搜索框")"""
                }

        val optionalOutput =
                if (forceKeyboardTap) {
                    """do(action="Type", text="$failedTypeText", desc="重新尝试输入文本")"""
                } else {
                    """do(action="Type", text="需要输入的内容", desc="输入用户名")"""
                }

        return """# 动作执行失败修复

上一步骤失败动作：$failedAction

请给出可直接执行的新动作，要求如下：
- 如果上一步失败，先补齐关键参数
$inputRepairRule
- 页面切换失败可尝试 Wait 或 Swipe 后重试
- 可选输出 finish 提前结束
- $descRuleText
- $intentTextRuleText

<answer>
	$repairExample
</answer>

可选输出：
<answer>
	$optionalOutput
</answer>
""".trimIndent()
    }

    fun buildRepairPrompt(enforceDesc: Boolean = false): String {
        val descRuleText = buildRepairDescRule(enforceDesc)

        return """# 输出格式错误修复

如果上一步输出不规范，请重新输出。
示例：
<answer>
	do(action="Tap", element=[500,500], desc="点击中间按钮")
</answer>

<answer>
	finish(message="任务完成")
</answer>

注意：
- do() 与 finish() 均是合法输出
- $descRuleText
- 只返回一次完整动作，不要解释
""".trimIndent()
    }

    private fun buildSystemDescRule(enforceDesc: Boolean): String {
        return if (enforceDesc) {
            "每个 do(...) 必须包含 desc 字段"
        } else {
            "每个 do(...) 可选包含 desc 字段（建议包含，便于修复），不允许影响执行"
        }
    }

    private fun buildSystemIntentTextRule(enforceDesc: Boolean): String {
        return if (enforceDesc) {
            "第三方模型可选 text 字段；若提供，应简洁描述动作意图"
        } else {
            "默认模型请尽量在 do(...) 中额外携带 text=\"本次动作意图\"；Type 动作的 text 保留为实际输入内容"
        }
    }

    private fun buildRepairDescRule(enforceDesc: Boolean): String {
        return if (enforceDesc) {
            "do() 与 finish() 应包含 desc 字段，帮助模型保持稳定输出"
        } else {
            "do() 与 finish() 的 desc 字段建议包含（可选），不包含时仍应给出可执行动作"
        }
    }

    private fun buildRepairIntentTextRule(enforceDesc: Boolean): String {
        return if (enforceDesc) {
            "修复时 text 字段可选；若输出请简洁说明动作意图"
        } else {
            "修复时优先补充 text=\"动作意图\"（Type 动作除外），便于日志展示与排错"
        }
    }
}
