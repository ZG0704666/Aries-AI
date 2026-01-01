package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.content.Context
import android.graphics.Canvas
import android.text.Editable
import android.text.Layout
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.AttributeSet
import com.ai.assistance.operit.util.AppLogger
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion.CompletionItem
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion.CompletionProviderFactory
import android.text.TextUtils

/**
 * 编辑操作数据类
 * @param type 操作类型 (INSERT, DELETE)
 * @param text 操作的文本内容
 * @param start 操作的起始位置
 */
private data class Edit(val type: EditType, val text: String, val start: Int)

private enum class EditType {
    INSERT,
    DELETE
}

/**
 * 代码文本编辑器
 */
class CodeText : ColorsText {
    // 代码解析器
    private lateinit var codeParser: CodeParser
    
    // 当前语言
    private var currentLanguage = "text"
    
    // 撤销/重做历史 - 使用增量编辑
    private val undoHistory = mutableListOf<Edit>()
    private val redoHistory = mutableListOf<Edit>()
    private var isUndoingOrRedoing = false
    private var ignoreNextChange = false
    
    // 代码补全相关
    private var completionProvider = CompletionProviderFactory.getProvider(currentLanguage)
    private var showingCompletion = false
    private var completionItems = listOf<CompletionItem>()
    private var completionPrefix = ""
    
    // 光标位置
    var cursorOffset: IntOffset = IntOffset.Zero
    
    // 代码补全接口
    interface CompletionCallback {
        fun showCompletions(items: List<CompletionItem>, prefix: String)
        fun hideCompletions()
        fun isCompletionVisible(): Boolean
    }
    
    // 代码补全回调
    var completionCallback: CompletionCallback? = null
    
    // 父视图尺寸
    private var pWidth = 0
    private var pHeight = 0
    
    fun setPHeight(pHeight: Int) {
        this.pHeight = pHeight
    }
    
    fun getPHeight(): Int {
        return pHeight
    }
    
    fun setPWidth(pWidth: Int) {
        this.pWidth = pWidth
    }
    
    fun getPWidth(): Int {
        return pWidth
    }
    
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        
        // 处理代码补全
        updateCompletion()
    }
    
    /**
     * 检查是否可以撤销
     */
    fun canUndo(): Boolean {
        return undoHistory.isNotEmpty()
    }
    
    /**
     * 撤销上一次编辑
     */
    fun undo() {
        if (undoHistory.isEmpty() || isUndoingOrRedoing) return
        
        isUndoingOrRedoing = true
        val edit = undoHistory.removeAt(undoHistory.size - 1)
        
        when (edit.type) {
            EditType.INSERT -> {
                // 撤销插入 -> 删除
                text?.let { editable ->
                    val end = edit.start + edit.text.length
                    if (end <= editable.length) {
                        editable.delete(edit.start, end)
                    }
                }
                // 恢复光标位置
                Selection.setSelection(text, edit.start)
            }
            EditType.DELETE -> {
                // 撤销删除 -> 插入
                text?.insert(edit.start, edit.text)
                // 恢复光标位置
                Selection.setSelection(text, edit.start + edit.text.length)
            }
        }
        
        redoHistory.add(edit)
        isUndoingOrRedoing = false
    }
    
    /**
     * 检查是否可以重做
     */
    fun canRedo(): Boolean {
        return redoHistory.isNotEmpty()
    }
    
    /**
     * 重做上一次撤销的编辑
     */
    fun redo() {
        if (redoHistory.isEmpty() || isUndoingOrRedoing) return
        
        isUndoingOrRedoing = true
        val edit = redoHistory.removeAt(redoHistory.size - 1)
        
        when (edit.type) {
            EditType.INSERT -> {
                // 重做插入 -> 插入
                text?.insert(edit.start, edit.text)
                // 恢复光标位置
                Selection.setSelection(text, edit.start + edit.text.length)
            }
            EditType.DELETE -> {
                // 重做删除 -> 删除
                text?.let { editable ->
                    val end = edit.start + edit.text.length
                    if (end <= editable.length) {
                        editable.delete(edit.start, end)
                    }
                }
                // 恢复光标位置
                Selection.setSelection(text, edit.start)
            }
        }
        
        undoHistory.add(edit)
        isUndoingOrRedoing = false
    }
    
    /**
     * 忽略下一次文本变化（用于加载文件等场景）
     */
    fun ignoreNextChange() {
        ignoreNextChange = true
    }
    
    /**
     * 以编程方式插入文本，并记录到撤销历史中
     * @param start 插入位置
     * @param text 要插入的文本
     */
    fun insertTextProgrammatically(start: Int, text: String) {
        if (isUndoingOrRedoing) return
        
        // 忽略下一次文本变化，因为我们手动处理历史记录
        ignoreNextChange = true
        
        // 记录插入操作
        val edit = Edit(EditType.INSERT, text, start)
        undoHistory.add(edit)
        
        // 执行插入
        this.text?.insert(start, text)
        
        // 清空重做历史
        redoHistory.clear()
    }

    /**
     * 以编程方式替换所有文本，并作为一个整体记录到撤销历史中
     * @param newText 新的文本内容
     */
    fun replaceAllText(newText: String) {
        val oldText = text.toString()
        if (oldText == newText) return

        // 锁定，防止TextWatcher记录这次更改
        isUndoingOrRedoing = true

        // 将“删除旧文本”和“插入新文本”作为一组操作记录下来
        undoHistory.add(Edit(EditType.DELETE, oldText, 0))
        undoHistory.add(Edit(EditType.INSERT, newText, 0))

        // 实际执行文本替换
        text?.replace(0, oldText.length, newText)

        // 任何新的编辑都会使重做历史失效
        redoHistory.clear()

        // 解锁
        isUndoingOrRedoing = false
    }

    /**
     * 请求显示补全
     */
    private fun onCompletionRequest(items: List<CompletionItem>) {
        if (items.isNotEmpty()) {
            showingCompletion = true
            completionItems = items
            completionCallback?.showCompletions(items, completionPrefix)
        } else {
            hideCompletion()
        }
    }
    
    /**
     * 更新代码补全
     */
    private fun updateCompletion() {
        if (completionProvider == null) {
            onCompletionRequest(emptyList())
            return
        }
        
        val cursorPosition = selectionStart
        // 使用text作为Editable直接操作，避免创建新的String对象
        val editable = text ?: return
        
        // 获取前缀
        completionPrefix = completionProvider!!.getPrefix(editable, cursorPosition)
        
        if (completionProvider?.shouldShowCompletion(editable, cursorPosition) == true) {
            val items = completionProvider!!.getCompletionItems(editable, cursorPosition)
            // 更新光标位置
            updateCursorOffset()
            onCompletionRequest(items)
        } else {
            onCompletionRequest(emptyList())
        }
    }
    
    /**
     * 更新光标屏幕偏移
     */
    private fun updateCursorOffset() {
        val layout = this.layout ?: return
        val line = layout.getLineForOffset(selectionStart)
        val x = layout.getPrimaryHorizontal(selectionStart).toInt()
        val y = layout.getLineBottom(line) // 使用行的底部Y坐标
        
        // 考虑滚动位置
        val scrollX = this.scrollX
        val scrollY = this.scrollY
        
        cursorOffset = IntOffset(x - scrollX, y - scrollY)
    }
    
    /**
     * 隐藏补全
     */
    private fun hideCompletion() {
        if (showingCompletion) {
            showingCompletion = false
            completionCallback?.hideCompletions()
        }
    }
    
    /**
     * 应用选中的补全项
     */
    fun applyCompletion(item: CompletionItem) {
        val start = selectionStart - completionPrefix.length
        val end = selectionStart
        
        if (start >= 0 && end <= text?.length ?: 0) {
            // 使用新的方法插入文本，以记录历史
            text?.delete(start, end)
            insertTextProgrammatically(start, item.insertText)
        }
        
        hideCompletion()
    }
    
    fun setLanguage(language: String) {
        currentLanguage = language
        codeParser.setLanguage(language)
        completionProvider = CompletionProviderFactory.getProvider(language)
    }

    /**
     * 手动触发完整代码解析（用于文件加载后初始化高亮）
     */
    fun triggerParse() {
        val textLength = text?.length ?: 0
        if (textLength > 0) {
            codeParser.parse(0, 0, textLength)
        }
    }

    /**
     * 设置语法检查
     */
    fun setCheckGrammar(c: GrammarCheck.Check) {
        check = c
    }
    
    override fun getParentWidth(): Int {
        return pWidth
    }
    
    override fun getParentHeight(): Int {
        return pHeight
    }
    
    constructor(context: Context) : super(context) {
        init()
    }
    
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }
    
    private fun init() {
        // 初始化代码解析器
        codeParser = CodeParser(this)
        
        // 动态解析代码更新文字颜色
        addTextChangedListener(object : TextWatcher {
            var deletedText: String? = null
            var insertedText: String? = null
            var insertionStart: Int = 0
            
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                if (isUndoingOrRedoing || ignoreNextChange) return
                
                deletedText = s.subSequence(start, start + count).toString()
                insertionStart = start
            }
            
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (isUndoingOrRedoing || ignoreNextChange) return
                
                insertedText = s.subSequence(start, start + count).toString()
                
                // 解析代码
                codeParser.parse(start, before, count)
                
                // 更新补全
                updateCompletion()
            }
            
            override fun afterTextChanged(s: Editable) {
                if (isUndoingOrRedoing) return
                
                if (ignoreNextChange) {
                    ignoreNextChange = false
                    return
                }
                
                if (deletedText?.isNotEmpty() == true) {
                    undoHistory.add(Edit(EditType.DELETE, deletedText!!, insertionStart))
                }
                
                if (insertedText?.isNotEmpty() == true) {
                    undoHistory.add(Edit(EditType.INSERT, insertedText!!, insertionStart))
                }
                
                if (deletedText?.isNotEmpty() == true || insertedText?.isNotEmpty() == true) {
                    redoHistory.clear()
                }
                
                deletedText = null
                insertedText = null
            }
        })
    }
    
    // 记录绘制用时
    private var drawUseTimeCount: Long = 0
    // 记录最后一次回车时间
    private var lastEnterTime: Long = 0
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
    
    /**
     * 记录用户操作的键盘，避免一次按键，多次输入
     */
    private var defaultDeviceId = -1000
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // 如果补全可见，并且按下了上下键或回车键，则由补全处理
        if (completionCallback?.isCompletionVisible() == true) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_TAB -> {
                    // 这些按键由补全处理，不传递给编辑器
                    return true
                }
                KeyEvent.KEYCODE_ESCAPE -> {
                    hideCompletion()
                    return true
                }
            }
        }
        
        // 处理设备ID
        val deviceId = event.deviceId
        if (defaultDeviceId == -1000) {
            defaultDeviceId = deviceId
        } else {
            if (defaultDeviceId == -1) {
                if (deviceId != defaultDeviceId) {
                    defaultDeviceId = deviceId
                    return true
                }
            }
        }
        
        if (deviceId != defaultDeviceId) {
            return true
        }
        
        // 处理Tab键
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            val start = selectionStart
            val end = selectionEnd
            if (start == end) {
                text?.insert(start, "    ")
            }
            return true
        }
        
        // 处理回车键
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            lastEnterTime = System.currentTimeMillis()
            val layout = layout
            if (layout == null) {
                return true
            }
            val start = selectionStart
            val end = selectionEnd
            if (start == end) {
                val line = layout.getLineForOffset(start)
                val startPos = layout.getLineStart(line)
                var space = ""
                for (i in startPos until (text?.length ?: 0)) {
                    if (text?.get(i) == ' ') {
                        space += " "
                    } else {
                        break
                    }
                }
                text?.insert(start, "\n$space")
                if (start > 0) {
                    val c = text?.get(start - 1)
                    if (c == '{' || c == '[' || c == '(') {
                        val newStart = selectionStart
                        text?.insert(newStart, "    ")
                        val afterIndentStart = selectionStart
                        text?.insert(afterIndentStart, "\n$space")
                        setSelection(afterIndentStart)
                    }
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}