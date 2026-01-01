package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.util.*

/**
 * 编辑历史管理器，用于撤销和重做操作
 */
class PerformEdit(private val editText: EditText) {
    // 操作序号(一次编辑可能对应多个操作，如替换文字，就是删除+插入)
    private var index = 0
    // 撤销栈
    private val history = Stack<Action>()
    // 恢复栈
    private val historyBack = Stack<Action>()
    
    var alreadyWrite = false
    
    private var editable: Editable = editText.text
    // 自动操作标志，防止重复回调,导致无限撤销
    private var flag = false
    private var hasWriter = false
    
    init {
        check(editText, "EditText不能为空")
    }
    
    /**
     * 当Editable改变时调用
     */
    protected open fun onEditableChanged(s: Editable) {}
    
    /**
     * 当文本改变时调用
     */
    protected open fun onTextChanged(s: Editable) {
        alreadyWrite = true
    }
    
    /**
     * 清理历史记录
     */
    fun clearHistory() {
        history.clear()
        historyBack.clear()
    }
    
    /**
     * 是否可以撤销
     */
    fun canUndo(): Boolean {
        return !history.empty()
    }
    
    /**
     * 锁定操作
     */
    fun lock() {
        flag = true
    }
    
    /**
     * 解锁操作
     */
    fun unlock() {
        flag = false
    }
    
    /**
     * 撤销操作
     */
    fun undo() {
        if (history.empty()) return
        // 锁定操作
        flag = true
        val action = history.pop()
        historyBack.push(action)
        if (action.isAdd) {
            // 撤销添加
            editable.delete(action.startCursor, action.startCursor + action.actionTarget.length)
            editText.setSelection(action.startCursor, action.startCursor)
        } else {
            // 撤销删除
            editable.insert(action.startCursor, action.actionTarget)
            if (action.endCursor == action.startCursor) {
                editText.setSelection(action.startCursor + action.actionTarget.length)
            } else {
                editText.setSelection(action.startCursor, action.endCursor)
            }
        }
        // 释放操作
        flag = false
        // 判断是否是下一个动作是否和本动作是同一个操作，直到不同为止
        if (!history.empty() && history.peek().index == action.index) {
            undo()
        }
    }
    
    /**
     * 是否可以重做
     */
    fun canRedo(): Boolean {
        return !historyBack.empty()
    }
    
    /**
     * 重做操作
     */
    fun redo() {
        if (historyBack.empty()) return
        flag = true
        val action = historyBack.pop()
        history.push(action)
        if (action.isAdd) {
            // 恢复添加
            editable.insert(action.startCursor, action.actionTarget)
            if (action.endCursor == action.startCursor) {
                editText.setSelection(action.startCursor + action.actionTarget.length)
            } else {
                editText.setSelection(action.startCursor, action.endCursor)
            }
        } else {
            // 恢复删除
            editable.delete(action.startCursor, action.startCursor + action.actionTarget.length)
            editText.setSelection(action.startCursor, action.startCursor)
        }
        flag = false
        // 判断是否是下一个动作是否和本动作是同一个操作
        if (!historyBack.empty() && historyBack.peek().index == action.index) {
            redo()
        }
    }
    
    /**
     * 首次设置文本
     */
    fun setDefaultText(text: CharSequence) {
        clearHistory()
        flag = true
        editable.replace(0, editable.length, text)
        flag = false
        if (!hasWriter) {
            editText.addTextChangedListener(Watcher())
            hasWriter = true
        }
        alreadyWrite = false
    }
    
    /**
     * 开始多次拼接文本
     */
    fun setDefaultTextBuilderStart() {
        flag = true
    }
    
    /**
     * 多次拼接文本
     */
    fun setDefaultTextBuilder(text: CharSequence) {
        editable.append(text)
    }
    
    /**
     * 结束多次拼接文本
     */
    fun setDefaultTextBuilderEnd() {
        flag = false
        clearHistory()
        if (!hasWriter) {
            editText.addTextChangedListener(Watcher())
            hasWriter = true
        }
        alreadyWrite = false
    }
    
    /**
     * 文本监听器
     */
    private inner class Watcher : TextWatcher {
        /**
         * 文本改变前
         */
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (flag) return
            val end = start + count
            if (end > start && end <= s.length) {
                val charSequence = s.subSequence(start, end)
                // 删除了文字
                if (charSequence.isNotEmpty()) {
                    val action = Action(charSequence, start, false)
                    if (count > 1) {
                        // 如果一次超过一个字符，说明用户选择了，然后替换或者删除操作
                        action.setSelectCount(count)
                    } else if (count == 1 && count == after) {
                        // 一个字符替换
                        action.setSelectCount(count)
                    }
                    // 还有一种情况:选择一个字符,然后删除(暂时没有考虑这种情况)
                    history.push(action)
                    historyBack.clear()
                    action.updateIndex(++index)
                }
            }
        }
        
        /**
         * 文本改变中
         */
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (flag) return
            val end = start + count
            if (end > start) {
                val charSequence = s.subSequence(start, end)
                // 添加文字
                if (charSequence.isNotEmpty()) {
                    val action = Action(charSequence, start, true)
                    history.push(action)
                    historyBack.clear()
                    if (before > 0) {
                        // 文字替换（先删除再增加），删除和增加是同一个操作，所以不需要增加序号
                        action.updateIndex(index)
                    } else {
                        action.updateIndex(++index)
                    }
                }
            }
        }
        
        /**
         * 文本改变后
         */
        override fun afterTextChanged(s: Editable) {
            if (flag) return
            if (s !== editable) {
                editable = s
                onEditableChanged(s)
            }
            this@PerformEdit.onTextChanged(s)
        }
    }
    
    /**
     * 编辑动作
     */
    private inner class Action(
        // 改变的字符
        val actionTarget: CharSequence,
        // 光标位置
        val startCursor: Int,
        // 是否是添加操作
        val isAdd: Boolean
    ) {
        var endCursor = startCursor
        var index = 0
        
        /**
         * 设置选择数量
         */
        fun setSelectCount(count: Int) {
            endCursor += count
        }
        
        /**
         * 设置操作序号
         */
        fun updateIndex(newIndex: Int) {
            this.index = newIndex
        }
    }
    
    companion object {
        /**
         * 检查对象是否为空
         */
        private fun check(o: Any?, message: String) {
            if (o == null) throw IllegalStateException(message)
        }
    }
} 