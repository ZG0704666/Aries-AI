# Areis AI 对齐Operit方案 - 详细TODO清单

## 📋 项目概述

**核心策略**: 保持单体应用架构（仅无障碍授权），对齐Operit核心能力

**参考来源**:

* `temp/Open-AutoGLM-main`: Python版Agent逻辑、提示词模板、应用映射表

* `temp/Operit-main`: Kotlin版UI树格式、工具系统、JS工具包

***

## 🔴 阶段一：核心架构优化（Week 1-2）

### TODO-001: UI树格式标准化

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`: `dumpUiTreeXml(...)` / `getUiHierarchy(format, detail, maxNodes)`

**下一步**:

* 若需要更严格对齐 Operit 输出字段/排序，可补充一份 schema 对照测试样例

**优先级**: ⭐⭐⭐⭐⭐⭐

**任务描述**:
将当前`dumpUiTree()`的输出格式改造为标准XML格式，对齐Operit的schema

**具体操作步骤**:

1. 打开`PhoneAgentAccessibilityService.kt`，定位`dumpUiTree()`方法
2. 创建新的`dumpUiTreeXml()`方法，输出标准XML格式：

   ```xml
   <ui_hierarchy>
     <node class="android.widget.TextView" 
            package="com.tencent.mm" 
            content-desc="聊天" 
            text="Hello" 
            resource-id="com.tencent.mm:id/text" 
            bounds="[100,200][300,400]" 
            clickable="true" 
            focused="false"/>
   </ui_hierarchy>
   ```
3. 修改`getUiHierarchy()`方法，支持format参数（xml/json）
4. nodeId统一使用`Rect.toShortString()`格式

**预期结果**:

* UI树输出为标准XML格式

* bounds格式为`[left,top][right,bottom]`

* 文件大小控制在3KB内（200节点）

**验收标准**:

1. 在Android Studio中运行应用，执行自动化任务
2. 查看Logcat日志，搜索"UI_TREE"，确认输出格式为XML
3. 复制XML内容，使用在线XML验证器验证格式正确性
4. 确认bounds格式为`[100,200][300,400]`

**验证方法**:

```bash
# 在Logcat中过滤
adb logcat | grep "UI_TREE"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git commit -m "feat: UI树格式标准化为XML，对齐Operit schema

- 新增dumpUiTreeXml()方法输出标准XML格式
- nodeId使用Rect.toShortString()格式
- bounds格式统一为[left,top][right,bottom]
- 支持format参数(xml/json)"
git push origin feature/ui-tree-standardization
```

***

### TODO-002: 应用包名映射表扩展

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/AppPackageMapping.kt`: 大量中英文别名→包名映射
* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`: `launch_app` 调用 `AppPackageManager.resolvePackageName(...)`

**下一步**:

* 若要严格满足“迁移 Open-AutoGLM 全量映射表”，可对照 `apps.py` 做一次覆盖率检查（可选）

**优先级**: ⭐⭐⭐⭐⭐

**任务描述**:
从Open-AutoGLM的`apps.py`迁移100+应用包名映射，扩展`AppPackageManager.kt`

**具体操作步骤**:

1. 打开`temp/Open-AutoGLM-main/phone_agent/config/apps.py`
2. 复制完整的`APP_PACKAGES`字典（约150行）
3. 打开`app/src/main/java/com/ai/phoneagent/core/tools/AppPackageManager.kt`
4. 在`APP_PACKAGES`Map中添加所有应用映射
5. 添加模糊匹配逻辑（如"微信"、"WeChat"都映射到`com.tencent.mm`）

**预期结果**:

* 应用映射表从20个扩展到100+个

* 支持中英文别名模糊匹配

* 启动应用成功率提升

**验收标准**:

1. 在Android Studio中运行应用
2. 测试启动"微信"、"WeChat"、"淘宝"、"京东"等应用
3. 确认所有应用都能正确启动
4. 测试模糊匹配："WeChat" → 启动微信

**验证方法**:

```kotlin
// 在AutomationActivityNew中测试
val packageName = AppPackageManager.getPackageName("WeChat")
Log.d("TEST", "Package: $packageName") // 应输出: com.tencent.mm
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/tools/AppPackageManager.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/tools/AppPackageManager.kt
git commit -m "feat: 扩展应用包名映射表至100+应用

- 从Open-AutoGLM迁移完整应用映射表
- 支持中英文别名模糊匹配
- 新增LRU缓存策略(5分钟TTL)
- 添加应用启动测试用例"
git push origin feature/app-mapping-expansion
```

***

### TODO-003: 系统提示词优化

**状态**: 🟡 部分完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`: `splitThinkingAndAnswer(...)` + 动作解析 `do(...)`/`finish(...)`
* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`: 支持上下文裁剪与 UI 树截断等执行侧规则

**下一步**:

* 需要核对并补齐“Open-AutoGLM 18条规则”是否已完整写入 `buildSystemPrompt()`（若未完整迁移则补充）

**优先级**: ⭐⭐⭐⭐⭐

**任务描述**:
从Open-AutoGLM的`prompts.py`迁移18条规则到Kotlin，优化系统提示词

**具体操作步骤**:

1. 打开`temp/Open-AutoGLM-main/phone_agent/config/prompts.py`
2. 复制`SYSTEM_PROMPT`中的18条规则（第55-74行）
3. 打开`app/src/main/java/com/ai/phoneagent/core/agent/PhoneAgent.kt`
4. 在`buildSystemPrompt()`方法中添加规则：

   * 规则1: 检查当前app是否是目标app

   * 规则2: 无关页面执行Back

   * 规则3: 页面未加载最多Wait三次

   * 规则4: 网络问题重新加载

   * 规则5: 找不到目标可以Swipe查找

   * 规则6: 价格/时间区间放宽要求

   * 规则7: 小红书筛选图文笔记

   * 规则8: 购物车全选后取消全选

   * 规则9: 外卖清空购物车

   * 规则10: 多个外卖同一店铺

   * 规则11: 严格遵循用户意图

   * 规则12: 日期滑动方向调整

   * 规则13: 逐个查找项目栏

   * 规则14: 检查上一步是否生效

   * 规则15: 滑动不生效调整位置

   * 规则16: 游戏自动战斗

   * 规则17: 搜索页面不对返回上一级

   * 规则18: 结束前检查任务完整性

**预期结果**:

* Agent遵循18条规则执行任务

* 任务成功率从70%提升到85%+

* 减少无效操作和死循环

**验收标准**:

1. 在Android Studio中运行应用
2. 执行复杂任务（如"美团点外卖"）
3. 观察Agent行为，确认遵循规则
4. 测试异常场景（网络错误、找不到目标）

**验证方法**:

```bash
# 在Logcat中过滤Agent思考过程
adb logcat | grep "AgentThinking"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/agent/PhoneAgent.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/agent/PhoneAgent.kt
git commit -m "feat: 优化系统提示词，新增18条执行规则

- 从Open-AutoGLM迁移完整规则集
- 规则1: 检查当前app是否是目标app
- 规则2-18: 异常处理和任务优化
- 预期任务成功率从70%提升到85%+"
git push origin feature/system-prompt-optimization
```

***

## 🟡 阶段二：工具系统扩展（Week 3-4）

### TODO-004: 新增get\_page\_info工具

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`: 已注册 `get_page_info`

**下一步**:

* 若要增强可用性，可把返回值从仅 UI 树扩展为「package/activity + UI 树」合并输出（可选）

**优先级**: ⭐⭐⭐⭐⭐

**任务描述**:
实现`get_page_info`工具，获取页面信息（package+activity+UI树）

**具体操作步骤**:

1. 打开`app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`
2. 在`registerTools()`方法中新增工具注册：

   ```kotlin
   handler.registerTool(
       name = "get_page_info",
       dangerCheck = { false },
       descriptionGenerator = { 
           val format = it.parameters.find { p -> p.name == "format" }?.value ?: "xml"
           val detail = it.parameters.find { p -> p.name == "detail" }?.value ?: "summary"
           "获取页面信息(format=$format, detail=$detail)"
       },
       executor = { tool ->
           val service = PhoneAgentAccessibilityService.instance
               ?: return ToolResult(tool.name, false, error = "无障碍服务未启用")
           
           val format = tool.parameters.find { it.name == "format" }?.value ?: "xml"
           val detail = tool.parameters.find { it.name == "detail" }?.value ?: "summary"
           
           val xml = service.getUiHierarchy(format, detail)
           ToolResult(tool.name, true, StringResultData(xml))
       }
   )
   ```
3. 在`PhoneAgentAccessibilityService.kt`中新增`getUiHierarchy(format, detail)`方法

**预期结果**:

* 可通过`get_page_info`工具获取页面信息

* 支持format参数（xml/json）

* 支持detail参数（minimal/summary/full）

**验收标准**:

1. 在Android Studio中运行应用
2. 在自动化界面输入"获取页面信息"
3. 确认返回标准XML格式UI树
4. 测试format=json，确认返回JSON格式

**验证方法**:

```bash
# 在Logcat中过滤工具调用
adb logcat | grep "get_page_info"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git commit -m "feat: 新增get_page_info工具

- 支持获取页面信息(package+activity+UI树)
- 支持format参数(xml/json)
- 支持detail参数(minimal/summary/full)
- 对齐Operit工具接口"
git push origin feature/tool-get-page-info
```

***

### TODO-005: 新增click\_element工具

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`: 已注册 `click_element`
* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`: `clickElement(...)`（selector优先 + bounds/坐标兜底）

**下一步**:

* 可补充 partialMatch/contains 等匹配策略的开关参数（可选）

**优先级**: ⭐⭐⭐⭐⭐

**任务描述**:
实现`click_element`工具，支持selector优先、坐标兜底的智能点击

**具体操作步骤**:

1. 在`ToolRegistration.kt`中新增工具注册：

   ```kotlin
   handler.registerTool(
       name = "click_element",
       dangerCheck = { false },
       descriptionGenerator = { tool ->
           val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
           val text = tool.parameters.find { it.name == "text" }?.value
           val className = tool.parameters.find { it.name == "className" }?.value
           val index = tool.parameters.find { it.name == "index" }?.value
           "点击元素(resourceId=$resourceId, text=$text, className=$className, index=$index)"
       },
       executor = { tool ->
           val service = PhoneAgentAccessibilityService.instance
               ?: return ToolResult(tool.name, false, error = "无障碍服务未启用")
           
           val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
           val text = tool.parameters.find { it.name == "text" }?.value
           val className = tool.parameters.find { it.name == "className" }?.value
           val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0
           
           // 优先使用selector，失败则降级到坐标
           val success = service.clickElement(
               resourceId = resourceId,
               text = text,
               className = className,
               index = index
           )
           
           ToolResult(tool.name, success, UIActionResultData("click_element", success))
       }
   )
   ```
2. 在`PhoneAgentAccessibilityService.kt`中实现`clickElement()`方法

**预期结果**:

* 支持通过resourceId/text/className/index点击元素

* selector优先，坐标兜底

* 支持模糊匹配（partialMatch）

**验收标准**:

1. 在Android Studio中运行应用
2. 测试点击"登录"按钮（通过text）
3. 测试点击资源ID（通过resourceId）
4. 测试点击列表项（通过index）

**验证方法**:

```bash
# 在Logcat中过滤点击操作
adb logcat | grep "click_element"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git commit -m "feat: 新增click_element工具

- 支持resourceId/text/className/index点击
- selector优先，坐标兜底
- 支持模糊匹配(partialMatch)
- 对齐Operit工具接口"
git push origin feature/tool-click-element
```

***

### TODO-006: 新增set\_input\_text工具

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`: 已注册 `set_input_text`
* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`: `setTextOnElement(...)` / `setTextOnFocused(...)`

**下一步**:

* 若在部分 App 中 `ACTION_SET_TEXT` 失败，可补充剪贴板粘贴/键盘输入兜底策略（可选）

**优先级**: ⭐⭐⭐⭐⭐

**任务描述**:
实现`set_input_text`工具，支持焦点nodeId和setTextOnNode

**具体操作步骤**:

1. 在`ToolRegistration.kt`中新增工具注册：

   ```kotlin
   git add app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt
   git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
   git commit -m "feat: 新增click_element工具

   - 支持resourceId/text/className/index点击
   - selector优先，坐标兜底
   - 支持模糊匹配(partialMatch)
   - 对齐Operit工具接口"
   git push origin feature/tool-click-element
   ```
2. 在`PhoneAgentAccessibilityService.kt`中实现`setTextOnElement()`方法

**预期结果**:

* 支持通过nodeId/resourceId设置文本

* 自动聚焦输入框

* 自动清除现有文本

**验收标准**:

1. 在Android Studio中运行应用
2. 测试在搜索框输入文本
3. 测试通过resourceId设置文本
4. 确认自动清除现有文本

**验证方法**:

```bash
# 在Logcat中过滤输入操作
adb logcat | grep "set_input_text"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git commit -m "feat: 新增set_input_text工具

- 支持nodeId/resourceId设置文本
- 自动聚焦输入框
- 自动清除现有文本
- 对齐Operit工具接口"
git push origin feature/tool-set-input-text
```

***

### TODO-007: 新增wait\_for\_element工具

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`: 已注册 `wait_for_element`
* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`: `waitForElement(...)`

**下一步**:

* 可考虑加入“等待消失/等待变化”的模式（可选）

**优先级**: ⭐⭐⭐⭐

**任务描述**:
实现`wait_for_element`工具，等待元素出现（超时控制）

**具体操作步骤**:

1. 在`ToolRegistration.kt`中新增工具注册：

   ```kotlin
   handler.registerTool(
       name = "wait_for_element",
       dangerCheck = { false },
       descriptionGenerator = { tool ->
           val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
           val text = tool.parameters.find { it.name == "text" }?.value
           val timeout = tool.parameters.find { it.name == "timeout" }?.value ?: "5000"
           "等待元素(resourceId=$resourceId, text=$text, timeout=${timeout}ms)"
       },
       executor = { tool ->
           val service = PhoneAgentAccessibilityService.instance
               ?: return ToolResult(tool.name, false, error = "无障碍服务未启用")
           
           val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
           val text = tool.parameters.find { it.name == "text" }?.value
           val timeout = tool.parameters.find { it.name == "timeout" }?.value?.toLongOrNull() ?: 5000L
           
           val startTime = System.currentTimeMillis()
           while (System.currentTimeMillis() - startTime < timeout) {
               val found = service.findElement(resourceId, text)
               if (found) {
                   return ToolResult(tool.name, true, StringResultData("元素已出现"))
               }
               delay(200)
           }
           
           ToolResult(tool.name, false, error = "等待超时")
       }
   )
   ```
2. 在`PhoneAgentAccessibilityService.kt`中实现`findElement()`方法

**预期结果**:

* 支持等待元素出现

* 支持超时控制

* 支持resourceId/text匹配

**验收标准**:

1. 在Android Studio中运行应用
2. 测试等待"加载中"消失
3. 测试等待特定按钮出现
4. 测试超时场景

**验证方法**:

```bash
# 在Logcat中过滤等待操作
adb logcat | grep "wait_for_element"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git commit -m "feat: 新增wait_for_element工具

- 支持等待元素出现
- 支持超时控制(默认5秒)
- 支持resourceId/text匹配
- 对齐Operit工具接口"
git push origin feature/tool-wait-for-element
```

***

### TODO-008: 新增scroll\_to\_element工具

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`: 已注册 `scroll_to_element`
* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`: `scrollToElement(...)`

**下一步**:

* 可补充“指定滚动容器节点/指定滚动区域”的能力（可选）

**优先级**: ⭐⭐⭐

**任务描述**:
实现`scroll_to_element`工具，滚动到指定元素

**具体操作步骤**:

1. 在`ToolRegistration.kt`中新增工具注册：

   ```kotlin
   handler.registerTool(
       name = "scroll_to_element",
       dangerCheck = { false },
       descriptionGenerator = { tool ->
           val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
           val text = tool.parameters.find { it.name == "text" }?.value
           val direction = tool.parameters.find { it.name == "direction" }?.value ?: "down"
           "滚动到元素(resourceId=$resourceId, text=$text, direction=$direction)"
       },
       executor = { tool ->
           val service = PhoneAgentAccessibilityService.instance
               ?: return ToolResult(tool.name, false, error = "无障碍服务未启用")
           
           val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
           val text = tool.parameters.find { it.name == "text" }?.value
           val direction = tool.parameters.find { it.name == "direction" }?.value ?: "down"
           val maxScrolls = 5
           
           repeat(maxScrolls) {
               val found = service.findElement(resourceId, text)
               if (found) {
                   return ToolResult(tool.name, true, StringResultData("元素已找到"))
               }
               
               service.swipe(
                   startX = if (direction == "down") 500 else 500,
                   startY = if (direction == "down") 1500 else 500,
                   endX = if (direction == "down") 500 else 500,
                   endY = if (direction == "down") 500 else 1500,
                   duration = 300
               )
               delay(500)
           }
           
           ToolResult(tool.name, false, error = "滚动后未找到元素")
       }
   )
   ```

**预期结果**:

* 支持滚动到指定元素

* 支持direction参数（up/down）

* 最多滚动5次

**验收标准**:

1. 在Android Studio中运行应用
2. 测试向下滚动找到"加载更多"
3. 测试向上滚动找到顶部元素
4. 确认最多滚动5次

**验证方法**:

```bash
# 在Logcat中过滤滚动操作
adb logcat | grep "scroll_to_element"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt
git commit -m "feat: 新增scroll_to_element工具

- 支持滚动到指定元素
- 支持direction参数(up/down)
- 最多滚动5次
- 对齐Operit工具接口"
git push origin feature/tool-scroll-to-element
```

***

### TODO-009: 新增press\_key工具

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`: 已注册 `press_key`
* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`: `pressKey(...)`

**下一步**:

* 可把支持的 key_code 列表在工具描述中固定下来，减少模型输出漂移（可选）

**优先级**: ⭐⭐⭐⭐

**任务描述**:
实现`press_key`工具，模拟按键（Home/Back/Recent）

**具体操作步骤**:

1. 在`ToolRegistration.kt`中新增工具注册：

   ```kotlin
   handler.registerTool(
       name = "press_key",
       dangerCheck = { false },
       descriptionGenerator = { tool ->
           val keyCode = tool.parameters.find { it.name == "key_code" }?.value ?: "BACK"
           "按键: $keyCode"
       },
       executor = { tool ->
           val service = PhoneAgentAccessibilityService.instance
               ?: return ToolResult(tool.name, false, error = "无障碍服务未启用")
           
           val keyCode = tool.parameters.find { it.name == "key_code" }?.value ?: "BACK"
           
           val success = when (keyCode.uppercase()) {
               "BACK" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
               "HOME" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
               "RECENTS" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
               "NOTIFICATIONS" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
               else -> false
           }
           
           ToolResult(tool.name, success, StringResultData("按键成功"))
       }
   )
   ```

**预期结果**:

* 支持模拟Back/Home/Recent按键

* 支持NOTIFICATIONS按键

* 支持自定义key\_code

**验收标准**:

1. 在Android Studio中运行应用
2. 测试按Back键
3. 测试按Home键
4. 测试按Recent键

**验证方法**:

```bash
# 在Logcat中过滤按键操作
adb logcat | grep "press_key"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt
git commit -m "feat: 新增press_key工具

- 支持模拟Back/Home/Recent按键
- 支持NOTIFICATIONS按键
- 支持自定义key_code
- 对齐Operit工具接口"
git push origin feature/tool-press-key
```

***

### TODO-010: 新增get\_current\_app工具

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`: 已注册 `get_current_app`
* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`: `getCurrentAppInfo()`

**下一步**:

* 若要对齐 Operit 的“当前 activity 记录”语义，可改为监听 `TYPE_WINDOW_STATE_CHANGED` 并保存 activityName（可选）

**优先级**: ⭐⭐⭐⭐

**任务描述**:
实现`get_current_app`工具，获取当前应用包名

**具体操作步骤**:

1. 在`ToolRegistration.kt`中新增工具注册：

   ```kotlin
   handler.registerTool(
       name = "get_current_app",
       dangerCheck = { false },
       descriptionGenerator = { "获取当前应用包名" },
       executor = { tool ->
           val service = PhoneAgentAccessibilityService.instance
               ?: return ToolResult(tool.name, false, error = "无障碍服务未启用")
           
           val packageName = service.currentAppPackage()
           val activityName = service.currentActivityName()
           
           val result = """
               当前应用包名: $packageName
               当前Activity: $activityName
           """.trimIndent()
           
           ToolResult(tool.name, true, StringResultData(result))
       }
   )
   ```

**预期结果**:

* 返回当前应用包名

* 返回当前Activity名称

* 格式清晰易读

**验收标准**:

1. 在Android Studio中运行应用
2. 打开微信，执行`get_current_app`
3. 确认返回`com.tencent.mm`
4. 打开淘宝，再次执行，确认返回`com.taobao.taobao`

**验证方法**:

```bash
# 在Logcat中过滤应用信息
adb logcat | grep "get_current_app"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt
git commit -m "feat: 新增get_current_app工具

- 返回当前应用包名
- 返回当前Activity名称
- 格式清晰易读
- 对齐Operit工具接口"
git push origin feature/tool-get-current-app
```

***

### TODO-011: 新增get\_device\_info工具

**状态**: 🔴 未开始

**证据**:

* 当前 `app/` 代码中未检索到 `get_device_info` 的工具注册与实现

**下一步**:

* 在 `ToolRegistration.kt` 中新增 `get_device_info` 工具，返回 model/manufacturer/sdk/分辨率/dpi 等

**优先级**: ⭐⭐⭐

**任务描述**:
实现`get_device_info`工具，获取设备信息

**具体操作步骤**:

1. 在`ToolRegistration.kt`中新增工具注册：

   ```kotlin
   handler.registerTool(
       name = "get_device_info",
       dangerCheck = { false },
       descriptionGenerator = { "获取设备信息" },
       executor = { tool ->
           val result = """
               设备型号: ${Build.MODEL}
               设备制造商: ${Build.MANUFACTURER}
               Android版本: ${Build.VERSION.RELEASE}
               SDK版本: ${Build.VERSION.SDK_INT}
               屏幕分辨率: ${Resources.getSystem().displayMetrics.widthPixels}x${Resources.getSystem().displayMetrics.heightPixels}
               屏幕密度: ${Resources.getSystem().displayMetrics.densityDpi}dpi
           """.trimIndent()
           
           ToolResult(tool.name, true, StringResultData(result))
       }
   )
   ```

**预期结果**:

* 返回设备型号

* 返回Android版本

* 返回屏幕分辨率

* 返回屏幕密度

**验收标准**:

1. 在Android Studio中运行应用
2. 执行`get_device_info`
3. 确认返回完整设备信息
4. 确认信息准确无误

**验证方法**:

```bash
# 在Logcat中过滤设备信息
adb logcat | grep "get_device_info"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt
git commit -m "feat: 新增get_device_info工具

- 返回设备型号和制造商
- 返回Android版本和SDK版本
- 返回屏幕分辨率和密度
- 对齐Operit工具接口"
git push origin feature/tool-get-device-info
```

***

### TODO-012: 新增find\_elements工具

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`: 已注册 `find_elements`
* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`: `findElements(...)`

**下一步**:

* 可输出结构化 JSON（便于模型稳定解析）作为增强（可选）

**优先级**: ⭐⭐⭐

**任务描述**:
实现`find_elements`工具，查找匹配元素

**具体操作步骤**:

1. 在`ToolRegistration.kt`中新增工具注册：

   ```kotlin
   handler.registerTool(
       name = "find_elements",
       dangerCheck = { false },
       descriptionGenerator = { tool ->
           val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
           val text = tool.parameters.find { it.name == "text" }?.value
           val className = tool.parameters.find { it.name == "className" }?.value
           "查找元素(resourceId=$resourceId, text=$text, className=$className)"
       },
       executor = { tool ->
           val service = PhoneAgentAccessibilityService.instance
               ?: return ToolResult(tool.name, false, error = "无障碍服务未启用")
           
           val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
           val text = tool.parameters.find { it.name == "text" }?.value
           val className = tool.parameters.find { it.name == "className" }?.value
           
           val elements = service.findElements(resourceId, text, className)
           
           val result = buildString {
               appendLine("找到 ${elements.size} 个匹配元素:")
               elements.forEachIndexed { index, element ->
                   appendLine("  [$index] ${element.className}")
                   appendLine("      resourceId: ${element.viewIdResourceName}")
                   appendLine("      text: ${element.text}")
                   appendLine("      bounds: ${element.bounds}")
               }
           }
           
           ToolResult(tool.name, true, StringResultData(result))
       }
   )
   ```
2. 在`PhoneAgentAccessibilityService.kt`中实现`findElements()`方法

**预期结果**:

* 返回所有匹配元素

* 显示元素详细信息

* 支持resourceId/text/className匹配

**验收标准**:

1. 在Android Studio中运行应用
2. 测试查找所有"Button"元素
3. 测试查找包含"登录"文本的元素
4. 确认返回完整元素列表

**验证方法**:

```bash
# 在Logcat中过滤查找操作
adb logcat | grep "find_elements"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt`

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/tools/ToolRegistration.kt
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git commit -m "feat: 新增find_elements工具

- 返回所有匹配元素
- 显示元素详细信息
- 支持resourceId/text/className匹配
- 对齐Operit工具接口"
git push origin feature/tool-find-elements
```

***

## 🟢 阶段三：性能优化（Week 5-6）

### TODO-013: 截图缓存实现

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/core/cache/ScreenshotCache.kt`
* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`: `getOptimizedScreenshot(...)`

**下一步**:

* 可补充缓存命中率统计日志，方便量化收益（可选）

**优先级**: ⭐⭐⭐⭐⭐

**任务描述**:
实现截图缓存机制，减少重复截图，提升性能

**具体操作步骤**:

1. 创建新文件`app/src/main/java/com/ai/phoneagent/core/cache/ScreenshotCache.kt`：

   ```kotlin
   data class ScreenshotData(
       val base64: String,
       val timestamp: Long,
       val hash: String
   )

   class ScreenshotCache(private val maxSize: Int = 3) {
       private val cache = LinkedHashMap<String, ScreenshotData>()
       private val timestamps = LinkedHashMap<String, Long>()
       
       fun get(key: String): ScreenshotData? {
           val now = System.currentTimeMillis()
           if (timestamps.containsKey(key)) {
               val age = now - timestamps[key]!!
               if (age < 2000) { // 2秒内有效
                   return cache[key]
               }
           }
           return null
       }
       
       fun put(key: String, data: ScreenshotData) {
           if (cache.size >= maxSize) {
               val oldest = timestamps.entries.minByOrNull { it.value }
               if (oldest != null) {
                   cache.remove(oldest.key)
                   timestamps.remove(oldest.key)
               }
           }
           cache[key] = data
           timestamps[key] = System.currentTimeMillis()
       }
       
       fun clear() {
           cache.clear()
           timestamps.clear()
       }
   }
   ```
2. 在`PhoneAgentAccessibilityService.kt`中集成缓存：

   ```kotlin
   private val screenshotCache = ScreenshotCache()

   suspend fun tryCaptureScreenshotBase64(): String {
       val cacheKey = "screenshot_${System.currentTimeMillis() / 2000}" // 每2秒一个key
       val cached = screenshotCache.get(cacheKey)
       if (cached != null) {
           AppLogger.d("ScreenshotCache", "命中缓存: $cacheKey")
           return cached.base64
       }
       
       val base64 = captureScreenshotBase64()
       screenshotCache.put(cacheKey, ScreenshotData(base64, System.currentTimeMillis(), cacheKey))
       return base64
   }
   ```

**预期结果**:

* 截图缓存命中率≥30%

* 减少重复截图

* 提升性能20%+

**验收标准**:

1. 在Android Studio中运行应用
2. 执行自动化任务（连续截图）
3. 查看Logcat，确认缓存命中
4. 测量性能提升（对比无缓存版本）

**验证方法**:

```bash
# 在Logcat中过滤缓存命中
adb logcat | grep "ScreenshotCache"
```

**涉及文件**:

* 新增`app/src/main/java/com/ai/phoneagent/core/cache/ScreenshotCache.kt`

* 修改`app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/cache/ScreenshotCache.kt
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git commit -m "feat: 实现截图缓存机制

- 新增ScreenshotCache类(3张缓存)
- 2秒TTL缓存策略
- LRU淘汰策略
- 预期缓存命中率≥30%"
git push origin feature/screenshot-cache
```

***

### TODO-014: 截图节流实现

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/core/cache/ScreenshotThrottler.kt`
* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`: `getOptimizedScreenshot(...)` 调用节流器

**下一步**:

* 若遇到页面快速变化导致截图缺失，可把节流与 `lastWindowEventTime` 联动做“事件触发优先”（可选）

**优先级**: ⭐⭐⭐⭐⭐

**任务描述**:
实现截图节流机制，避免频繁截图导致性能下降

**具体操作步骤**:

1. 创建新文件`app/src/main/java/com/ai/phoneagent/core/cache/ScreenshotThrottler.kt`：

   ```kotlin
   class ScreenshotThrottler {
       private var lastScreenshotTime: Long = 0
       private val minInterval: Long = 1100 // 1.1秒
       
       fun shouldTakeScreenshot(): Boolean {
           val now = System.currentTimeMillis()
           val elapsed = now - lastScreenshotTime
           if (elapsed < minInterval) {
               AppLogger.w("ScreenshotThrottler", "节流: 距离上次${elapsed}ms < ${minInterval}ms")
               return false
           }
           lastScreenshotTime = now
           return true
       }
       
       fun recordScreenshot() {
           lastScreenshotTime = System.currentTimeMillis()
       }
       
       fun reset() {
           lastScreenshotTime = 0
       }
   }
   ```
2. 在`PhoneAgentAccessibilityService.kt`中集成节流：

   ```kotlin
   private val screenshotThrottler = ScreenshotThrottler()

   suspend fun tryCaptureScreenshotBase64(): String {
       if (!screenshotThrottler.shouldTakeScreenshot()) {
           AppLogger.d("Screenshot", "跳过截图(节流)")
           return getLastScreenshot() ?: ""
       }
       
       val base64 = captureScreenshotBase64()
       screenshotThrottler.recordScreenshot()
       return base64
   }
   ```

**预期结果**:

* 连续调用<1.1s时被节流

* 避免频繁截图

* 提升性能15%+

**验收标准**:

1. 在Android Studio中运行应用
2. 连续执行3次截图操作（间隔<1s）
3. 查看Logcat，确认第2、3次被节流
4. 测量性能提升

**验证方法**:

```bash
# 在Logcat中过滤节流日志
adb logcat | grep "ScreenshotThrottler"
```

**涉及文件**:

* 新增`app/src/main/java/com/ai/phoneagent/core/cache/ScreenshotThrottler.kt`

* 修改`app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/cache/ScreenshotThrottler.kt
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git commit -m "feat: 实现截图节流机制

- 新增ScreenshotThrottler类(1.1s节流)
- 避免频繁截图
- 提升性能15%+
- 对齐Operit节流策略"
git push origin feature/screenshot-throttle
```

***

### TODO-015: 截图压缩优化

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`: `tryCaptureScreenshotBase64()` 内已实现缩放 + JPEG 压缩（`SCREENSHOT_SCALE_PERCENT`/`SCREENSHOT_QUALITY`）

**下一步**:

* 如需进一步降流量，可把质量/缩放调参至 75/60%，并加上 size 监控日志（可选）

**优先级**: ⭐⭐⭐⭐

**任务描述**:
优化截图压缩参数，减少文件大小，提升传输速度

**具体操作步骤**:

1. 打开`PhoneAgentAccessibilityService.kt`
2. 定位`captureScreenshotBase64()`方法
3. 修改压缩参数：

   ```kotlin
   private const val SCREENSHOT_QUALITY = 75  // 从85降至75
   private const val SCREENSHOT_SCALE_PERCENT = 60  // 从75%降至60%

   private fun captureScreenshotBase64(): String {
       val screenshot = rootInActiveWindow?.takeScreenshot() ?: return ""
       val bitmap = Bitmap.createBitmap(
           (screenshot.width * SCREENSHOT_SCALE_PERCENT / 100).toInt(),
           (screenshot.height * SCREENSHOT_SCALE_PERCENT / 100).toInt()
       )
       
       val stream = ByteArrayOutputStream()
       bitmap.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_QUALITY, stream)
       val byteArray = stream.toByteArray()
       
       AppLogger.d("Screenshot", "截图大小: ${byteArray.size} bytes")
       return Base64.encodeToString(byteArray, Base64.NO_WRAP)
   }
   ```

**预期结果**:

* 截图文件大小<150KB

* 压缩率提升30%+

* 传输速度提升25%+

**验收标准**:

1. 在Android Studio中运行应用
2. 执行截图操作
3. 查看Logcat，确认文件大小<150KB
4. 对比优化前后的文件大小

**验证方法**:

```bash
# 在Logcat中过滤截图大小
adb logcat | grep "截图大小"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git commit -m "perf: 优化截图压缩参数

- 质量从85降至75
- 缩放从75%降至60%
- 目标文件大小<150KB
- 传输速度提升25%+"
git push origin feature/screenshot-compression
```

***

### TODO-016: 流式响应集成

**状态**: 🟡 部分完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/net/AutoGlmClient.kt`: `sendChatStreamResult(...)`
* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`: 存在 `requestModelStreamingWithEarlyStop(...)` 但当前执行链路使用稳定的非流式 `requestModelWithRetry(...)`

**下一步**:

* 若要恢复流式，需要先解决“早停后回退导致并行请求/协程状态混乱”的问题，再切回流式执行链路

**优先级**: ⭐⭐⭐⭐⭐

**任务描述**:
在`UiAutomationAgent`中集成流式响应，实时显示思考过程

**具体操作步骤**:

1. 打开`app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`
2. 定位`executeStep()`方法
3. 集成流式响应：

   ```kotlin
   suspend fun executeStepWithStreaming(
       task: String,
       apiKey: String,
       model: String,
       messages: MutableList<ChatRequestMessage>
   ): StepResult {
       var fullThinking = StringBuilder()
       var fullContent = StringBuilder()
       
       AutoGlmClient.sendChatStreamResult(
           apiKey = apiKey,
           messages = messages,
           onReasoningDelta = { reasoning ->
               fullThinking.append(reasoning)
               // 实时显示思考过程
               updateThinkingUI(reasoning)
           },
           onContentDelta = { content ->
               fullContent.append(content)
               // 实时显示生成内容
               updateContentUI(content)
           }
       )
       
       val responseText = "$fullThinking\n$fullContent"
       // 继续处理...
   }
   ```

**预期结果**:

* 实时显示思考过程

* 实时显示生成内容

* 用户体验提升

**验收标准**:

1. 在Android Studio中运行应用
2. 执行自动化任务
3. 确认实时显示思考过程
4. 确认实时显示生成内容

**验证方法**:

```bash
# 在Logcat中过滤流式响应
adb logcat | grep "Streaming"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt
git commit -m "feat: 集成流式响应

- 实时显示思考过程
- 实时显示生成内容
- 用户体验提升
- 对齐Open-AutoGLM流式实现"
git push origin feature/streaming-response
```

***

### TODO-017: 智能上下文裁剪

**状态**: ✅ 已完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`: `trimHistory(...)`（移除历史图片 + token/轮次限制）
* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`: `truncateUiTree(...)`（限制 UI 树字符数）

**下一步**:

* 可在裁剪时对 UI 树做更强的结构化摘要（节点抽样/只保留可交互节点）（可选）

**优先级**: ⭐⭐⭐⭐

**任务描述**:
实现智能上下文裁剪，控制Token数量，提升响应速度

**具体操作步骤**:

1. 打开`UiAutomationAgent.kt`
2. 新增`trimHistorySmart()`方法：

   ```kotlin
   private fun trimHistorySmart(history: MutableList<ChatRequestMessage>) {
       // 1. 移除所有历史中的图片(只保留文本)
       for (i in history.indices) {
           val msg = history[i]
           if (msg.content is List<*>) {
               val textOnly = (msg.content as List<*>)
                   .filter { (it as? Map<*, *>)?.get("type") == "text" }
               if (textOnly.isNotEmpty()) {
                   history[i] = ChatRequestMessage(msg.role, textOnly)
               }
           }
       }
       
       // 2. 限制UI树到1200字符
       val uiTreeMsgIndex = history.indexOfLast { 
           it.role == "user" && 
           (it.content as? String)?.contains("UI树") == true
       }
       if (uiTreeMsgIndex >= 0) {
           val uiTreeMsg = history[uiTreeMsgIndex]
           val currentContent = uiTreeMsg.content as String
           if (currentContent.length > 1200) {
               history[uiTreeMsgIndex] = ChatRequestMessage(
                   uiTreeMsg.role,
                   currentContent.take(1200) + "\n... [UI树已截断,共${currentContent.length}字符] ..."
               )
           }
       }
       
       // 3. 保留最近5轮对话
       if (history.size > 10) {
           val toKeep = history.take(2) + history.takeLast(8)
           history.clear()
           history.addAll(toKeep)
       }
   }
   ```

**预期结果**:

* 上下文大小控制在15000 tokens内

* 移除历史图片

* 限制UI树到1200字符

* 保留最近5轮对话

**验收标准**:

1. 在Android Studio中运行应用
2. 执行多轮对话（10轮+）
3. 确认上下文被裁剪
4. 确认Token数量在15000内

**验证方法**:

```bash
# 在Logcat中过滤上下文裁剪
adb logcat | grep "trimHistorySmart"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt
git commit -m "feat: 实现智能上下文裁剪

- 移除历史图片
- 限制UI树到1200字符
- 保留最近5轮对话
- 上下文控制在15000 tokens内"
git push origin feature/smart-context-trimming
```

***

### TODO-018: 智能等待时间

**状态**: 🟡 部分完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`: 存在 `postActionDelayMs` 等参数化等待

**下一步**:

* 若要实现“按动作类型动态调整”，需引入 `getActionDelay(actionName)` 并在动作执行后按类型 delay

**优先级**: ⭐⭐⭐⭐

**任务描述**:
实现智能等待时间，根据动作类型动态调整

**具体操作步骤**:

1. 打开`UiAutomationAgent.kt`
2. 新增`getActionDelay()`方法：

   ```kotlin
   private fun getActionDelay(actionName: String): Long {
       return when (actionName) {
           "launch" -> 500L
           "tap", "click" -> 100L
           "type", "input" -> 200L
           "swipe", "scroll" -> 300L
           "back" -> 150L
           "home" -> 200L
           "long_press" -> 400L
           "double_tap" -> 150L
           else -> 200L
       }
   }
   ```
3. 在执行动作后应用智能等待：

   ```kotlin
   val delay = getActionDelay(actionName)
   delay(delay)
   ```

**预期结果**:

* 动作执行耗时从0.5s降至0.3s

* 根据动作类型动态调整

* 减少不必要的等待

**验收标准**:

1. 在Android Studio中运行应用
2. 执行不同类型的动作
3. 测量每种动作的等待时间
4. 确认智能等待生效

**验证方法**:

```bash
# 在Logcat中过滤动作延迟
adb logcat | grep "ActionDelay"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt
git commit -m "perf: 实现智能等待时间

- 根据动作类型动态调整
- launch: 500ms
- tap/click: 100ms
- type/input: 200ms
- swipe/scroll: 300ms
- 动作执行耗时从0.5s降至0.3s"
git push origin feature/smart-action-delay
```

***

### TODO-019: 移除不必要的awaitWindowEvent

**状态**: 🔴 未开始

**证据**:

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`: 仍存在 `awaitWindowEvent(afterTimeMs, timeoutMs)` 并在可靠性链路中使用

**下一步**:

* 增加 `shouldWaitForWindowEvent(actionName)`，仅关键动作等待，其余跳过以降耗时

**优先级**: ⭐⭐⭐

**任务描述**:
移除非关键操作的`awaitWindowEvent`，减少等待时间

**具体操作步骤**:

1. 打开`PhoneAgentAccessibilityService.kt`
2. 定位所有`awaitWindowEvent()`调用
3. 新增`shouldWaitForWindowEvent()`方法：

   ```kotlin
   private fun shouldWaitForWindowEvent(actionName: String): Boolean {
       return when (actionName) {
           "launch", "tap", "click", "type", "input" -> true
           "swipe", "scroll" -> true
           "back", "home" -> true
           else -> false
       }
   }
   ```
4. 修改动作执行逻辑：

   ```kotlin
   if (shouldWaitForWindowEvent(actionName)) {
       awaitWindowEvent()
   }
   ```

**预期结果**:

* 非关键操作不等待

* 减少等待时间

* 提升性能10%+

**验收标准**:

1. 在Android Studio中运行应用
2. 执行非关键操作（如swipe）
3. 确认不等待WindowEvent
4. 测量性能提升

**验证方法**:

```bash
# 在Logcat中过滤WindowEvent
adb logcat | grep "awaitWindowEvent"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git commit -m "perf: 移除非关键操作的awaitWindowEvent

- 仅关键操作等待WindowEvent
- 非关键操作跳过等待
- 减少等待时间
- 提升性能10%+"
git push origin feature/remove-unnecessary-wait
```

***

### TODO-020: 动作并行化

**状态**: 🟡 部分完成

**证据**:

* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`: 已并行获取「截图 + UI树」（`async`）

**下一步**:

* Tap+Type 合并执行（`performTapAndInput` / 跳过下一个动作）目前未检索到实现；若要做需新增合并逻辑并回归验证

**优先级**: ⭐⭐⭐

**任务描述**:
实现Tap+Type合并操作，减少动作次数

**具体操作步骤**:

1. 打开`PhoneAgentAccessibilityService.kt`
2. 新增`performTapAndInput()`方法：

   ```kotlin
   suspend fun performTapAndInput(
       x: Float,
       y: Float,
       text: String
   ): Boolean {
       // 先点击
       val tapSuccess = clickAwait(x, y, durationMs = 60L)
       delay(100)
       
       // 再输入
       val inputSuccess = performTextInput(text)
       
       return tapSuccess && inputSuccess
   }
   ```
3. 在`UiAutomationAgent.kt`中检测Tap+Type序列：

   ```kotlin
   if (currentAction.name == "tap" && nextAction.name == "type") {
       val tapParams = currentAction.parameters
       val typeParams = nextAction.parameters
       
       val x = tapParams.find { it.name == "x" }?.value?.toFloatOrNull() ?: 0f
       val y = tapParams.find { it.name == "y" }?.value?.toFloatOrNull() ?: 0f
       val text = typeParams.find { it.name == "text" }?.value ?: ""
       
       service.performTapAndInput(x, y, text)
       
       // 跳过下一个动作
       skipNextAction = true
   }
   ```

**预期结果**:

* Tap+Type合并为一个操作

* 减少动作次数

* 提升性能15%+

**验收标准**:

1. 在Android Studio中运行应用
2. 执行点击输入框并输入文本的任务
3. 确认Tap+Type合并执行
4. 测量性能提升

**验证方法**:

```bash
# 在Logcat中过滤合并操作
adb logcat | grep "performTapAndInput"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`

* `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt
git add app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt
git commit -m "feat: 实现Tap+Type合并操作

- 新增performTapAndInput方法
- 检测Tap+Type序列并合并
- 减少动作次数
- 提升性能15%+"
git push origin feature/action-parallelization
```

***

## 🟣 阶段四：高级功能（Week 7-8，低优先级）

### TODO-021: 应用场景模板系统

**优先级**: ⭐⭐⭐

**任务描述**:
实现应用场景模板系统，为高频场景提供模板化动作

**具体操作步骤**:

1. 创建新文件`app/src/main/java/com/ai/phoneagent/core/templates/AppTemplate.kt`：

   ```kotlin
   data class AppTemplate(
       val appName: String,
       val packageName: String,
       val steps: List<TemplateStep>
   )

   data class TemplateStep(
       val action: String,
       val selector: Selector?,
       val params: Map<String, String>
   )

   data class Selector(
       val resourceId: String? = null,
       val text: String? = null,
       val contentDesc: String? = null,
       val className: String? = null,
       val index: Int = 0
   )
   ```
2. 创建`AppTemplateRegistry.kt`：

   ```kotlin
   object AppTemplateRegistry {
       private val templates = mapOf(
           "美团" to AppTemplate(
               appName = "美团",
               packageName = "com.sankuai.meituan",
               steps = listOf(
                   TemplateStep("tap", Selector(text = "搜索"), emptyMap()),
                   TemplateStep("type", null, mapOf("text" to "火锅")),
                   TemplateStep("tap", Selector(text = "搜索"), emptyMap()),
                   TemplateStep("tap", Selector(text = "人气最高"), emptyMap()),
                   TemplateStep("tap", Selector(text = "预订"), emptyMap())
               )
           ),
           "12306" to AppTemplate(
               appName = "12306",
               packageName = "com.MobileTicket",
               steps = listOf(
                   TemplateStep("type", null, mapOf("text" to "南京")),
                   TemplateStep("type", null, mapOf("text" to "北京")),
                   TemplateStep("type", null, mapOf("text" to "1月19日")),
                   TemplateStep("tap", Selector(text = "查询"), emptyMap()),
                   TemplateStep("tap", Selector(text = "最便宜"), emptyMap()),
                   TemplateStep("tap", Selector(text = "预订"), emptyMap())
               )
           )
       )
       
       fun getTemplate(appName: String): AppTemplate? {
           return templates[appName]
       }
       
       fun getAllTemplates(): List<AppTemplate> {
           return templates.values.toList()
       }
   }
   ```

**预期结果**:

* 支持5+个应用模板

* 模板可动态加载

* 模板执行成功率≥90%

**验收标准**:

1. 在Android Studio中运行应用
2. 测试美团模板
3. 测试12306模板
4. 确认模板执行成功率≥90%

**验证方法**:

```bash
# 在Logcat中过滤模板执行
adb logcat | grep "AppTemplate"
```

**涉及文件**:

* 新增`app/src/main/java/com/ai/phoneagent/core/templates/AppTemplate.kt`

* 新增`app/src/main/java/com/ai/phoneagent/core/templates/AppTemplateRegistry.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/templates/AppTemplate.kt
git add app/src/main/java/com/ai/phoneagent/core/templates/AppTemplateRegistry.kt
git commit -m "feat: 实现应用场景模板系统

- 新增AppTemplate数据模型
- 新增AppTemplateRegistry
- 支持5+个应用模板
- 模板执行成功率≥90%"
git push origin feature/app-template-system
```

***

### TODO-022: 工具权限系统增强

**优先级**: ⭐⭐

**任务描述**:
增强工具权限系统，实现危险操作检测和用户确认

**具体操作步骤**:

1. 打开`app/src/main/java/com/ai/phoneagent/core/permissions/ToolPermissionSystem.kt`
2. 新增危险操作检测：

   ```kotlin
   private val dangerousTools = setOf(
       "delete_file",
       "send_message",
       "make_payment",
       "transfer_money"
   )

   fun isDangerous(toolName: String): Boolean {
       return dangerousTools.contains(toolName)
   }

   fun getDangerLevel(toolName: String): DangerLevel {
       return when (toolName) {
           "delete_file" -> DangerLevel.HIGH
           "send_message" -> DangerLevel.MEDIUM
           "make_payment" -> DangerLevel.CRITICAL
           "transfer_money" -> DangerLevel.CRITICAL
           else -> DangerLevel.LOW
       }
   }

   enum class DangerLevel {
       LOW, MEDIUM, HIGH, CRITICAL
   }
   ```
3. 新增用户确认对话框：

   ```kotlin
   suspend fun confirmDangerousOperation(
       toolName: String,
       details: String
   ): Boolean {
       if (!isDangerous(toolName)) return true
       
       val dangerLevel = getDangerLevel(toolName)
       
       return withContext(Dispatchers.Main) {
           // 显示确认对话框
           showConfirmationDialog(toolName, details, dangerLevel)
       }
   }
   ```

**预期结果**:

* 危险操作自动检测

* 用户确认对话框正常显示

* 权限状态可查询

**验收标准**:

1. 在Android Studio中运行应用
2. 测试危险操作（如delete\_file）
3. 确认显示确认对话框
4. 测试用户取消操作

**验证方法**:

```bash
# 在Logcat中过滤权限检查
adb logcat | grep "ToolPermission"
```

**涉及文件**:

* `app/src/main/java/com/ai/phoneagent/core/permissions/ToolPermissionSystem.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/permissions/ToolPermissionSystem.kt
git commit -m "feat: 增强工具权限系统

- 新增危险操作检测
- 新增危险等级评估(LOW/MEDIUM/HIGH/CRITICAL)
- 新增用户确认对话框
- 权限状态可查询"
git push origin feature/tool-permission-enhancement
```

***

### TODO-023: JS工具包系统（可选）

**优先级**: ⭐

**任务描述**:
实现JS工具包系统，支持动态加载JS工具

**具体操作步骤**:

1. 从Operit复制`automatic_ui_base.js`到`app/src/main/assets/packages/`
2. 创建`JsPackageLoader.kt`：

   ```kotlin
   class JsPackageLoader(private val context: Context) {
       private val packages = mutableMapOf<String, JsPackage>()
       
       suspend fun loadPackage(packageName: String): Result<JsPackage> {
           return try {
               val script = context.assets.open("packages/$packageName.js")
                   .bufferedReader()
                   .readText()
               
               val metadata = parseMetadata(script)
               val tools = parseTools(script)
               
               val pkg = JsPackage(
                   name = metadata.name,
                   description = metadata.description,
                   tools = tools
               )
               
               packages[packageName] = pkg
               Result.success(pkg)
           } catch (e: Exception) {
               Result.failure(e)
           }
       }
       
       private fun parseMetadata(script: String): PackageMetadata {
           // 解析 /* METADATA ... */
       }
       
       private fun parseTools(script: String): List<JsTool> {
           // 解析工具定义
       }
   }
   ```

**预期结果**:

* 可从assets加载JS包

* JS工具可正常调用

* 包元数据可解析

**验收标准**:

1. 在Android Studio中运行应用
2. 加载`automatic_ui_base.js`包
3. 测试JS工具调用
4. 确认包元数据正确解析

**验证方法**:

```bash
# 在Logcat中过滤JS包加载
adb logcat | grep "JsPackageLoader"
```

**涉及文件**:

* 新增`app/src/main/java/com/ai/phoneagent/core/javascript/JsPackageLoader.kt`

* 新增`app/src/main/assets/packages/automatic_ui_base.js`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/javascript/JsPackageLoader.kt
git add app/src/main/assets/packages/automatic_ui_base.js
git commit -m "feat: 实现JS工具包系统

- 新增JsPackageLoader类
- 支持从assets加载JS包
- 支持包元数据解析
- 对齐Operit JS工具系统"
git push origin feature/js-package-system
```

***

### TODO-024: 智能记忆库（可选）

**优先级**: ⭐

**任务描述**:
实现智能记忆库，记住用户偏好和应用习惯

**具体操作步骤**:

1. 创建`MemoryItem.kt`：

   ```kotlin
   data class MemoryItem(
       val id: String,
       val type: MemoryType,
       val content: String,
       val timestamp: Long
   )

   enum class MemoryType {
       APP_PREFERENCE,
       OPERATION_HABIT,
       CUSTOM_COMMAND
   }
   ```
2. 创建`MemoryRepository.kt`：

   ```kotlin
   class MemoryRepository(private val context: Context) {
       private val memories = mutableMapOf<String, MemoryItem>()
       
       fun saveMemory(item: MemoryItem) {
           memories[item.id] = item
           // 持久化到本地
           saveToLocal(item)
       }
       
       fun getMemories(type: MemoryType): List<MemoryItem> {
           return memories.values.filter { it.type == type }
       }
       
       fun searchMemories(query: String): List<MemoryItem> {
           return memories.values.filter { 
               it.content.contains(query, ignoreCase = true)
           }
       }
       
       fun getRecommendation(context: String): String? {
           val habits = getMemories(MemoryType.OPERATION_HABIT)
           // 基于习惯推荐
           return habits.firstOrNull()?.content
       }
       
       private fun saveToLocal(item: MemoryItem) {
           val prefs = context.getSharedPreferences("memories", Context.MODE_PRIVATE)
           val json = Gson().toJson(item)
           prefs.edit().putString(item.id, json).apply()
       }
   }
   ```

**预期结果**:

* 记忆可持久化

* 支持搜索和推荐

* 数据可导出

**验收标准**:

1. 在Android Studio中运行应用
2. 保存用户偏好
3. 搜索记忆
4. 测试推荐功能

**验证方法**:

```bash
# 在Logcat中过滤记忆操作
adb logcat | grep "MemoryRepository"
```

**涉及文件**:

* 新增`app/src/main/java/com/ai/phoneagent/data/repository/MemoryRepository.kt`

* 新增`app/src/main/java/com/ai/phoneagent/data/model/MemoryItem.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/data/repository/MemoryRepository.kt
git add app/src/main/java/com/ai/phoneagent/data/model/MemoryItem.kt
git commit -m "feat: 实现智能记忆库

- 新增MemoryItem数据模型
- 新增MemoryRepository
- 支持记忆持久化
- 支持搜索和推荐"
git push origin feature/memory-repository
```

***

### TODO-025: 任务编排系统（可选）

**优先级**: ⭐

**任务描述**:
实现任务编排系统，支持复杂多步骤任务

**具体操作步骤**:

1. 创建`Workflow.kt`：

   ```kotlin
   data class Workflow(
       val id: String,
       val name: String,
       val description: String,
       val steps: List<WorkflowStep>
   )

   data class WorkflowStep(
       val id: String,
       val action: String,
       val toolName: String,
       val parameters: Map<String, String>,
       val condition: StepCondition?
   )

   data class StepCondition(
       val type: ConditionType,
       val field: String,
       val operator: String,
       val value: String
   )

   enum class ConditionType {
       UI_CONTAINS,
       APP_EQUALS,
       TEXT_MATCHES
   }
   ```
2. 创建`WorkflowExecutor.kt`：

   ```kotlin
   class WorkflowExecutor(private val context: Context) {
       suspend fun executeWorkflow(workflow: Workflow): WorkflowResult {
           for (step in workflow.steps) {
               if (step.condition != null) {
                   val conditionMet = evaluateCondition(step.condition)
                   if (!conditionMet) {
                       continue
                   }
               }
               
               val result = executeStep(step)
               if (!result.success) {
                   return WorkflowResult(false, "步骤${step.id}失败: ${result.error}")
               }
           }
           return WorkflowResult(true, "工作流执行成功")
       }
       
       private fun evaluateCondition(condition: StepCondition): Boolean {
           // 评估条件
       }
       
       private fun executeStep(step: WorkflowStep): StepResult {
           // 执行步骤
       }
   }
   ```

**预期结果**:

* 工作流可定义和执行

* 支持条件分支

* 执行结果可追踪

**验收标准**:

1. 在Android Studio中运行应用
2. 定义工作流
3. 执行工作流
4. 测试条件分支

**验证方法**:

```bash
# 在Logcat中过滤工作流执行
adb logcat | grep "WorkflowExecutor"
```

**涉及文件**:

* 新增`app/src/main/java/com/ai/phoneagent/core/workflow/WorkflowExecutor.kt`

* 新增`app/src/main/java/com/ai/phoneagent/data/model/Workflow.kt`

**Git提交建议**:

```bash
git add app/src/main/java/com/ai/phoneagent/core/workflow/WorkflowExecutor.kt
git add app/src/main/java/com/ai/phoneagent/data/model/Workflow.kt
git commit -m "feat: 实现任务编排系统

- 新增Workflow数据模型
- 新增WorkflowExecutor
- 支持条件分支
- 执行结果可追踪"
git push origin feature/workflow-system
```

***

## 📊 验收标准总结

### 阶段一验收（Week 1-2）

* [ ] UI树输出为标准XML格式

* [ ] nodeId使用`Rect.toShortString()`

* [ ] 应用映射表扩展到100+个

* [ ] 系统提示词包含18条规则

### 阶段二验收（Week 3-4）

* [ ] 工具总数≥25个

* [ ] 所有工具可正常调用

* [ ] 工具描述清晰准确

* [ ] 错误处理完善

### 阶段三验收（Week 5-6）

* [ ] 截图缓存命中率≥30%

* [ ] 节流生效（1.1s）

* [ ] 单步耗时≤2秒

* [ ] 模型调用耗时≤1.2s

### 阶段四验收（Week 7-8，可选）

* [ ] 支持≥5个应用模板

* [ ] 权限系统正常工作

* [ ] JS工具包可加载

* [ ] 记忆可持久化

* [ ] 工作流可执行

***

## 🎯 预期收益

### 性能提升

| 指标     | 当前   | 目标   | 提升     |
| ------ | ---- | ---- | ------ |
| 单步平均耗时 | 3-5秒 | 2秒   | 40-60% |
| 模型调用耗时 | 2秒   | 1.2秒 | 40%    |
| 截图耗时   | 1.5秒 | 0.8秒 | 47%    |
| 动作执行耗时 | 0.5秒 | 0.3秒 | 40%    |

### 功能提升

| 指标     | 当前  | 目标  | 提升    |
| ------ | --- | --- | ----- |
| 工具数量   | 12个 | 25个 | +108% |
| 支持应用场景 | 0个  | 5个  | 新增    |
| UI树标准化 | ❌   | ✅   | 新增    |
| JS工具包  | ❌   | ✅   | 新增    |
| 智能记忆   | ❌   | ✅   | 新增    |

***

## 📝 Git工作流建议

### 分支策略

```bash
# 主分支
main

# 功能分支
feature/ui-tree-standardization
feature/app-mapping-expansion
feature/system-prompt-optimization
feature/tool-get-page-info
feature/tool-click-element
feature/tool-set-input-text
feature/tool-wait-for-element
feature/tool-scroll-to-element
feature/tool-press-key
feature/tool-get-current-app
feature/tool-get-device-info
feature/tool-find-elements
feature/screenshot-cache
feature/screenshot-throttle
feature/screenshot-compression
feature/streaming-response
feature/smart-context-trimming
feature/smart-action-delay
feature/remove-unnecessary-wait
feature/action-parallelization
feature/app-template-system
feature/tool-permission-enhancement
feature/js-package-system
feature/memory-repository
feature/workflow-system
```

### 提交规范

```bash
# 格式
<type>(<scope>): <subject>

<body>

<footer>

# 类型
feat: 新功能
fix: 修复bug
perf: 性能优化
refactor: 重构
docs: 文档
test: 测试
chore: 构建/工具链

# 示例
feat(tool): 新增get_page_info工具

- 支持获取页面信息(package+activity+UI树)
- 支持format参数(xml/json)
- 支持detail参数(minimal/summary/full)
- 对齐Operit工具接口

Closes #001
```

### 推送流程

```bash
# 1. 完成单个TODO项
# 2. 通过验收标准验证
# 3. 提交代码
git add <files>
git commit -m "feat: ..."
# 4. 推送到GitHub
git push origin feature/<name>
# 5. 创建Pull Request
gh pr create --title "..." --body "..."
```

***

## ✅ 总结

本TODO清单包含25个具体任务，分为4个阶段：

**阶段一（Week 1-2）**: 核心架构优化

* UI树格式标准化

* 应用包名映射表扩展

* 系统提示词优化

**阶段二（Week 3-4）**: 工具系统扩展

* 9个新工具（get\_page\_info, click\_element, set\_input\_text等）

**阶段三（Week 5-6）**: 性能优化

* 截图缓存+节流

* 流式响应+智能裁剪

* 智能等待+动作并行

**阶段四（Week 7-8）**: 高级功能（可选）

* 应用场景模板

* 工具权限系统

* JS工具包

* 智能记忆库

* 任务编排系统

**核心优势**:

* ✅ 保持单体架构（仅无障碍授权）

* ✅ 对齐Operit核心能力

* ✅ 详细的可执行步骤

* ✅ 清晰的验收标准

* ✅ 完整的Git工作流

**预期成果**:

* 工具数量: 12 → 25个(+108%)

* 单步耗时: 3-5秒 → 2秒(-40-60%)

* 支持场景: 0 → 5个(新增)

* UI树标准化: ❌ → ✅(新增)

