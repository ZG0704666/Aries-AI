package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion.CompletionItem
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion.CompletionPopup
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.getThemeForLanguage

/**
 * 代码编辑器组件，支持语法高亮、行号显示和代码补全
 * @param code 初始代码内容
 * @param language 代码语言，用于语法高亮
 * @param onCodeChange 代码变更回调
 * @param modifier 修饰符
 * @param readOnly 是否只读模式
 * @param showLineNumbers 是否显示行号
 * @param enableCompletion 是否启用代码补全
 * @param editorRef 获取编辑器引用的回调
 */
@Composable
fun CodeEditor(
        code: String,
        language: String,
        onCodeChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        readOnly: Boolean = false,
        showLineNumbers: Boolean = true,
        enableCompletion: Boolean = true,
        editorRef: ((NativeCodeEditor) -> Unit)? = null
) {
    val theme = getThemeForLanguage(language)
    
    // 补全状态
    var showCompletions by remember { mutableStateOf(false) }
    var completionItems by remember { mutableStateOf<List<CompletionItem>>(emptyList()) }
    var completionPrefix by remember { mutableStateOf("") }
    var completionPosition by remember { mutableStateOf(Offset.Zero) }
    var cursorOffset by remember { mutableStateOf(IntOffset.Zero) }
    
    // 编辑器引用
    val editorRefState = remember { mutableStateOf<NativeCodeEditor?>(null) }
    
    // 当前密度，用于dp转px
    val density = LocalDensity.current
    
    CustomScaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // 编辑器区域
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                // 保存编辑器位置，用于定位补全弹窗
                                val position = coordinates.positionInRoot()
                                completionPosition = position
                            },
                        factory = { context ->
                            NativeCodeEditor(context).apply {
                                this.layoutParams =
                                        ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                
                                // 设置补全回调
                                if (enableCompletion) {
                                    setCompletionCallback(object : CodeText.CompletionCallback {
                                        override fun showCompletions(items: List<CompletionItem>, prefix: String) {
                                            showCompletions = true
                                            completionItems = items
                                            completionPrefix = prefix
                                            
                                            // 获取光标位置
                                            val cursorPos = getCursorScreenPosition()
                                            if (cursorPos != null) {
                                                completionPosition = Offset(
                                                    completionPosition.x + cursorPos.x,
                                                    completionPosition.y + cursorPos.y
                                                )
                                            }
                                        }
                                        
                                        override fun hideCompletions() {
                                            showCompletions = false
                                        }
                                        
                                       override fun isCompletionVisible(): Boolean {
                                            return showCompletions
                                        }
                                    })
                                }
                                
                                // 传递编辑器引用
                                editorRefState.value = this
                                editorRef?.invoke(this)
                            }
                        },
                        update = { view ->
                            view.setLanguage(language)
                            view.isEnabled = !readOnly

                            // 始终更新监听器以捕获最新的`code`和`onCodeChange`
                            view.setOnTextChangedListener { newText ->
                                if (newText != code) {
                                    onCodeChange(newText)
                                }
                            }

                            // 仅当文本不同时才设置文本，以避免循环和光标重置
                            if (view.getText() != code) {
                                view.setText(code, true)
                            }
                        }
                )
                
                // 显示补全弹窗
                if (showCompletions && completionItems.isNotEmpty()) {
                    val density = LocalDensity.current
                    val popupOffset = with(density) {
                        // 直接使用光标底部坐标，并稍微向下偏移一点点以增加间距
                        IntOffset(cursorOffset.x, cursorOffset.y + 4.dp.toPx().toInt())
                    }
                    
                    CompletionPopup(
                        completionItems = completionItems,
                        onItemSelected = { item ->
                            // 应用选中的补全项
                            editorRefState.value?.applyCompletion(item)
                            showCompletions = false
                        },
                        onDismissRequest = {
                            showCompletions = false
                        },
                        offset = popupOffset
                    )
                }
            }
            
            // 快捷输入栏
            Surface(
                modifier = Modifier.fillMaxWidth().height(40.dp), // 减小高度
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), // 使用间隔布局
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 常用编程标点符号，重新排序并添加
                    val symbols = listOf(
                        "{", "}", "(", ")", "[", "]", "=", ".", ",", ";", ":",
                        "\"", "'", "+", "-", "*", "/", "_", "<", ">", "&", "|",
                        "!", "?"
                    )
                    
                    items(symbols) { symbol ->
                        SymbolButton(symbol = symbol) {
                            // 插入符号到光标位置
                            editorRefState.value?.insertSymbol(symbol)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 符号按钮组件
 */
@Composable
private fun SymbolButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp) // 减小按钮大小
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 原生代码编辑器视图 */
class NativeCodeEditor : ViewGroup {
    private val codePane: CodePane

    private var onTextChangedListener: ((String) -> Unit)? = null

    constructor(context: Context) : super(context) {
        codePane = CodePane(context)
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        codePane = CodePane(context, attrs)
        init()
    }

    private fun init() {
        addView(codePane)

        // 添加文本变化监听
        codePane.getCodeText()
                .addTextChangedListener(
                        object : android.text.TextWatcher {
                            override fun beforeTextChanged(
                                    s: CharSequence?,
                                    start: Int,
                                    count: Int,
                                    after: Int
                            ) {}

                            override fun onTextChanged(
                                    s: CharSequence?,
                                    start: Int,
                                    before: Int,
                                    count: Int
                            ) {}

                            override fun afterTextChanged(s: android.text.Editable?) {
                                onTextChangedListener?.invoke(s?.toString() ?: "")
                            }
                        }
                )
    }

    /** 设置文本变化监听器 */
    fun setOnTextChangedListener(listener: (String) -> Unit) {
        onTextChangedListener = listener
    }

    /** 设置语言 */
    fun setLanguage(language: String) {
        codePane.setLanguage(language)
    }

    /** 设置文本内容，并可选择是否添加到撤销历史 */
    fun setText(text: String, fromUpdate: Boolean = false) {
        val codeText = codePane.getCodeText()
        if (fromUpdate) {
            codeText.ignoreNextChange()
        }
        codeText.setText(text)
        
        // 如果是从外部更新（如加载文件），手动触发代码解析以显示高亮
        if (fromUpdate) {
            codeText.triggerParse()
        }
    }

    /** 获取文本内容 */
    fun getText(): String {
        return codePane.getCodeText().text.toString()
    }
    
    /** 设置补全回调 */
    fun setCompletionCallback(callback: CodeText.CompletionCallback) {
        codePane.getCodeText().completionCallback = callback
    }
    
    /** 应用补全项 */
    fun applyCompletion(item: CompletionItem) {
        codePane.getCodeText().applyCompletion(item)
    }
    
    /** 撤销 */
    fun undo() {
        val codeText = codePane.getCodeText()
        codeText.undo()
    }
    
    /** 重做 */
    fun redo() {
        val codeText = codePane.getCodeText()
        codeText.redo()
    }
    
    /** 插入符号到光标位置 */
    fun insertSymbol(symbol: String) {
        val codeText = codePane.getCodeText()
        val start = codeText.selectionStart
        
        if (start != -1) {
            // 使用新的方法插入文本，以记录历史
            codeText.insertTextProgrammatically(start, symbol)
            // 移动光标
            codeText.setSelection(start + symbol.length)
        }
    }

    /**
     * 以编程方式替换所有文本，并记录为单一的可撤销操作
     * @param newText 新的文本内容
     */
    fun replaceAllText(newText: String) {
        codePane.getCodeText().replaceAllText(newText)
    }
    
    /** 获取光标在屏幕上的位置 */
    fun getCursorScreenPosition(): Point? {
        val codeText = codePane.getCodeText()
        val layout = codeText.layout ?: return null
        
        val line = layout.getLineForOffset(codeText.selectionStart)
        val baseline = layout.getLineBaseline(line)
        val ascent = layout.getLineAscent(line)
        
        val x = layout.getPrimaryHorizontal(codeText.selectionStart)
        val y = baseline + ascent
        
        // 考虑滚动位置
        val scrollX = codePane.scrollX
        val scrollY = codePane.scrollY
        
        return Point((x - scrollX).toInt(), (y - scrollY).toInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        codePane.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        codePane.layout(0, 0, r - l, b - t)
    }
}
