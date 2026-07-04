package com.ai.phoneagent.ui.helper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * 剪贴板操作 helper。
 *
 * 抽取自 MainActivity，仅做职责拆分，不改变原有逻辑。
 * 通过 [Context] 获取系统 ClipboardManager 服务并展示 Toast 提示。
 */
class ClipboardHelper(private val context: Context) {

    fun copyTranscriptMessage(text: String) {
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Reply", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已复制内容", Toast.LENGTH_SHORT).show()
    }
}
