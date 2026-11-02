# Agent 状态与配置

本页说明 UiAutomationAgent 在每一步使用的数据模型、运行状态，以及 AgentConfiguration 中的执行、模型、上下文和安全参数。

Aries-AI Agent 数据模型体系定义了自动化 Agent 运行时的核心数据结构、状态管理和通信协议，涵盖从模型对话到动作执行、从工具调用到结果存储的完整数据流。

## 概述

Aries-AI 的 Agent 数据模型遵循分层设计原则，按职责划分为以下几个核心领域：

| 领域 | 核心类型 | 职责 |
|------|----------|------|
| **Agent 动作模型** | `ParsedAgentAction` | 解析后的结构化动作指令，连接模型输出与执行层 |
| **对话通信模型** | `ChatContent`, `ChatRequestMessage` | OpenAI 兼容的聊天消息格式，支持文本与多模态 |
| **工具系统模型** | `AITool`, `ToolResult` | 工具注册、参数传递与执行结果的数据抽象 |
| **自动化指令模型** | `AutomationInstructionRequest` | 跨组件任务分发与调度指令 |
| **配置与状态模型** | `AgentConfiguration`, `RuntimeConnectionState` | 运行参数配置与运行时连接状态 |
| **存储与结果模型** | `ConversationEntity`, `AgentResult`, `AutomationTimelineEntry` | 对话持久化、执行结果与时间线记录 |

这些模型共同构成了从"接收任务 → 请求模型 → 解析动作 → 执行操作 → 记录结果"的完整数据闭环。

## 架构

### 数据模型全景关系图

```mermaid
flowchart TD
    subgraph Input["输入层"]
        Task[用户任务文本]
        InstrReq[AutomationInstructionRequest]
    end

    subgraph Chat["对话通信层"]
        ChatReq[ChatRequest]
        ChatReqMsg[ChatRequestMessage]
        ChatContent["ChatContent<br/>Text / Multimodal"]
        ContentPart["ContentPart<br/>TextPart / ImageUrlPart"]
        ChatResp[ChatResponse]
    end

    subgraph Agent["Agent 核心层"]
        ParsedAction[ParsedAgentAction]
        ActionParser[ActionParser]
        ActionExecutor[ActionExecutor]
        AgentResult[AgentResult]
    end

    subgraph Tool["工具系统层"]
        AITool[AITool]
        ToolParam[ToolParameter]
        ToolResult[ToolResult]
        ToolResultData["ToolResultData<br/>String/Image/UI/Action"]
    end

    subgraph Config["配置与状态层"]
        AgentConfig[AgentConfiguration]
        ConnState[RuntimeConnectionState]
        Timeline[AutomationTimelineEntry]
    end

    subgraph Storage["存储层"]
        ConvEntity[ConversationEntity]
        StoredMsg[StoredMessageRecord]
        AutoResultRepo[AutomationResultsRepository]
    end

    Task --> InstrReq
    InstrReq --> ChatReq
    ChatReq --> ChatReqMsg
    ChatReqMsg --> ChatContent
    ChatContent --> ContentPart
    ChatResp --> ActionParser
    ActionParser --> ParsedAction
    ParsedAction --> ActionExecutor
    ActionExecutor --> AgentResult
    AITool --> ToolParam
    ToolResult --> ToolResultData
    AgentResult --> AutoResultRepo
    AgentResult --> ConvEntity
    ConvEntity --> StoredMsg
    AgentConfig -.-> ActionExecutor
    ConnState -.-> AgentConfig
    Timeline -.-> AgentResult
```

> 图中实线箭头表示数据流转方向，虚线箭头表示配置/状态依赖关系。

### 分层设计思想

数据模型的分层遵循"关注点分离"原则：

1. **输入层**：负责接收和规范化外部任务请求，`AutomationInstructionRequest` 封装了任务来源、自动启动策略等元信息。
2. **对话通信层**：实现与 OpenAI 兼容 API 的数据协议。`ChatContent` 采用 sealed class 设计，通过自定义序列化器 `ChatContentSerializer` 统一处理文本字符串与多模态数组两种格式。
3. **Agent 核心层**：`ParsedAgentAction` 是连接模型输出与执行引擎的桥梁，其 `metadata` 字段（`"do"` / `"finish"`）驱动整个状态流转。
4. **工具系统层**：`ToolResult` 采用 sealed class 的 `ToolResultData` 子类型，支持字符串、图片、UI 页面信息、UI 操作结果等多种返回形式。
5. **配置与状态层**：`AgentConfiguration` 采用 data class + companion object 预置方案提供开箱即用的默认配置，所有参数均有明确的设计意图注释。
6. **存储层**：通过 Room（`ConversationEntity`）和 DataStore（`AutomationResultsRepository`）实现对话与结果的持久化。

## 核心数据模型详解

### 1. ParsedAgentAction — Agent 动作模型

`ParsedAgentAction` 是整个 Agent 系统中最核心的数据结构，被 `UiAutomationAgent`、`ActionParser`、`ActionExecutor` 三大模块共同使用。它表示一条从模型输出中解析出的结构化动作指令。

```kotlin
data class ParsedAgentAction(
    val metadata: String,           // "do" 或 "finish"
    val actionName: String?,        // 动作名称（如 tap, swipe）
    val fields: Map<String, String>, // 动作参数
    val raw: String = ""            // 原始响应
)
```
> Source: [AgentModels.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/agent/AgentModels.kt#L7-L12)

**字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `metadata` | `String` | 动作元类型：`"do"` 表示执行操作，`"finish"` 表示任务完成 |
| `actionName` | `String?` | 具体动作名称，如 `tap`、`swipe`、`type`、`launch`、`back` 等 |
| `fields` | `Map<String, String>` | 动作参数字典，如 `{"x": "500", "y": "300"}` |
| `raw` | `String` | 模型原始输出文本，用于日志和错误排查 |

**设计意图**：使用 `Map<String, String>` 而非强类型字段，使得模型输出的参数格式具有最大灵活性——不同动作需要不同的参数集（如 `tap` 需要 `x/y`，`launch` 需要 `package`，`type` 需要 `text`），这种设计避免了为每种动作定义单独类型的复杂性。

### 2. ChatContent / ContentPart — 对话内容模型

对话内容模型实现了 OpenAI 兼容 API 的双格式消息内容表示：纯文本字符串和多模态内容数组。

```kotlin
@Serializable(with = ChatContentSerializer::class)
sealed class ChatContent {
    @Serializable
    data class Text(val text: String) : ChatContent()

    @Serializable
    data class Multimodal(val parts: List<ContentPart>) : ChatContent()
}
```
> Source: [ChatContent.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/data/model/ChatContent.kt#L24-L34)

```kotlin
@Serializable(with = ContentPartSerializer::class)
sealed class ContentPart {
    @Serializable
    data class TextPart(
        @SerialName("text") val text: String,
    ) : ContentPart()

    @Serializable
    data class ImageUrlPart(
        @SerialName("image_url") val imageUrl: ImageUrl,
    ) : ContentPart()
}

@Serializable
data class ImageUrl(
    @SerialName("url") val url: String,
)
```
> Source: [ContentPart.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/data/model/ContentPart.kt#L25-L42)

**设计意图**：

- 使用 `sealed class` 保证类型安全，所有子类在编译期已知，支持 `when` 表达式的穷举检查。
- 自定义序列化器（`ChatContentSerializer`、`ContentPartSerializer`）处理序列化/反序列化的双格式兼容：序列化时 `Text` 输出为 JSON 原始字符串，`Multimodal` 输出为 JSON 数组；反序列化时根据 JSON 元素类型自动判断。

**序列化规则：**

| Kotlin 类型 | JSON 输出 | JSON 输入 → 解析结果 |
|------------|-----------|---------------------|
| `ChatContent.Text` | `"hello world"`（JSON 字符串） | JSON 原始字符串 → `Text` |
| `ChatContent.Multimodal` | `[{type:"text",text:"..."},{type:"image_url",...}]` | JSON 数组 → `Multimodal` |

### 3. ChatRequest / ChatRequestMessage — 模型请求模型

```kotlin
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatRequestMessage>,
    val stream: Boolean = false,
    val temperature: Float? = null,
    @SerialName("max_tokens") val max_tokens: Int? = null,
    @SerialName("top_p") val top_p: Float? = null,
    @SerialName("frequency_penalty") val frequency_penalty: Float? = null,
)

@Serializable
data class ChatRequestMessage(
    val role: String,
    val content: ChatContent,
)
```
> Source: [ChatModels.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/net/ChatModels.kt#L10-L24)

`ChatRequestMessage` 设计了一个兼容构造器，支持从 `String`、`List`、`Map` 等旧格式自动转换为类型安全的 `ChatContent`，确保向后兼容：

```kotlin
constructor(role: String, content: Any) : this(role, content.toChatContent())
```
> Source: [ChatModels.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/net/ChatModels.kt#L30)

### 4. AITool / ToolResult — 工具系统模型

工具系统模型定义了 Agent 可调用的工具抽象及其执行结果。

```kotlin
@Serializable
data class AITool(
    val name: String,
    val parameters: List<ToolParameter> = emptyList(),
)

@Serializable
data class ToolParameter(
    val name: String,
    val value: String,
)
```
> Source: [AITool.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/data/model/AITool.kt#L5-L16)

```kotlin
@Serializable
data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val result: ToolResultData? = null,
    val error: String = ""
)

@Serializable
sealed class ToolResultData

@Serializable
data class StringResultData(val data: String) : ToolResultData()

@Serializable
data class ImageResultData(
    val width: Int, val height: Int, val base64Data: String
) : ToolResultData()

@Serializable
data class UIPageResultData(
    val packageName: String,
    val activityName: String,
    val uiElements: List<SimplifiedUINode>
) : ToolResultData()

@Serializable
data class UIActionResultData(
    val action: String, val success: Boolean, val message: String
) : ToolResultData()
```
> Source: [ToolResult.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/data/model/ToolResult.kt#L12-L77)

**设计意图**：`ToolResultData` 采用 sealed class 而非单一类型，使不同工具返回的结果可以采用最适合的数据结构：

| 结果类型 | 适用场景 |
|----------|----------|
| `StringResultData` | 文本类工具（`get_ui_tree`、`get_current_app`） |
| `ImageResultData` | 截图工具（`screenshot`） |
| `UIPageResultData` | 页面信息采集工具 |
| `UIActionResultData` | UI 操作工具（`tap`、`swipe`、`input_text`） |

### 5. AgentConfiguration — 运行时配置模型

`AgentConfiguration` 是 Agent 的"参数控制中心"，采用 **"默认即用"** 设计原则——不传入任何参数即可完成端到端自动化任务。

```kotlin
data class AgentConfiguration(
    // ========== 执行参数 ==========
    val useBackgroundVirtualDisplay: Boolean = false,
    val useShizukuInteraction: Boolean = false,
    val maxSteps: Int = 100,

    // ========== 模型调用参数 ==========
    val maxModelRetries: Int = 3,
    val maxParseRepairs: Int = 2,
    val maxActionRepairs: Int = 1,
    val temperature: Float? = 0.0f,
    val maxTokens: Int? = 4096,

    // ========== 上下文管理参数 ==========
    val maxContextTokens: Int = 20000,
    val maxUiTreeChars: Int = 3000,
    val maxHistoryTurns: Int = 6,

    // ========== 截图优化参数 ==========
    val enableScreenshotCache: Boolean = true,
    val enableScreenshotThrottle: Boolean = true,
    val screenshotCompressionQuality: Int = 85,
    // ... 更多参数
) {
    companion object {
        val DEFAULT = AgentConfiguration()
        val TEST = AgentConfiguration(
            maxSteps = 10,
            stepDelayMs = 50L,
            maxModelRetries = 1,
        )
    }

    fun getActionDelayMs(actionName: String): Long { /* ... */ }
    fun isDangerousKeyword(text: String): Boolean { /* ... */ }
}
```
> Source: [AgentConfiguration.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/config/AgentConfiguration.kt#L38-L392)

**配置分层调参策略：**

```mermaid
flowchart LR
    subgraph Layer1["第一层：稳定性"]
        A1[maxSteps] --> A2[maxModelRetries]
        A2 --> A3[maxActionRepairs]
    end
    subgraph Layer2["第二层：性能"]
        B1[maxUiTreeChars] --> B2[maxContextTokens]
        B2 --> B3[screenshotCompressionQuality]
    end
    subgraph Layer3["第三层：体验"]
        C1[stepDelayMs] --> C2[clickDurationMs]
        C2 --> C3[postActionDelayMs]
    end
    Layer1 --> Layer2 --> Layer3
```

## Agent 运行状态模型

### 自动化指令请求与分发

```kotlin
data class AutomationInstructionRequest(
    val instruction: String,
    val source: Source = Source.MANUAL_AGENT_MODE,
    val autoStart: Boolean = true,
    val forceTopOnEntry: Boolean = false,
    val keepMainOnTop: Boolean = false,
) {
    enum class Source(val wireValue: String) {
        MANUAL_AGENT_MODE("manual_agent_mode"),
        ADVANCED_AI("advanced_ai"),
    }
}

data class AutomationDispatchResult(
    val success: Boolean,
    val message: String,
)
```
> Source: [AutomationInstructionGateway.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/automation/AutomationInstructionGateway.kt#L11-L27)

### AgentResult — 执行结果

```kotlin
data class AgentResult(
    val success: Boolean,
    val message: String,
    val steps: Int,
)
```
> Source: [UiAutomationAgent.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt#L103-L107)

`AgentResult` 的三个字段精确反映了自动化执行的关键指标：成功与否（`success`）、结果描述（`message`，成功时为 `"已完成"`，失败时为具体错误信息）、执行步数（`steps`，反映任务复杂度）。

### RuntimeConnectionState — 运行时连接状态

```kotlin
data class RuntimeConnectionState(
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val shizukuBinderConnected: Boolean,
    val shizukuPermissionGranted: Boolean,
) {
    val shizukuReady: Boolean
        get() = shizukuBinderConnected && shizukuPermissionGranted
}
```
> Source: [AutomationViewModel.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/viewmodel/AutomationViewModel.kt#L94-L102)

这个状态对象综合评估了两种执行通道（无障碍服务 + Shizuku）的就绪情况，`resolveRuntimeInteractionPreference` 方法据此决定实际使用的交互模式。

### AutomationTimelineEntry — 时间线条目

```kotlin
data class AutomationTimelineEntry(
    var displayText: String,
    var action: String? = null,
)
```
> Source: [AutomationTimelineFormatter.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/helper/AutomationTimelineFormatter.kt#L3-L6)

每个条目包含一次操作的显示文本（如"点击搜索按钮"）和动作标签（如"tap"），用于构建可视化的执行时间线。

## Agent 执行流程与状态转换

```mermaid
stateDiagram-v2
    [*] --> Idle: 初始化

    Idle --> Starting: startAgent()
    Starting --> Active: Agent 主循环启动
    Starting --> Idle: Shizuku/无障碍不可用

    Active --> Screenshot: 每步开始
    Screenshot --> ModelRequest: 截图+UI树就绪
    ModelRequest --> Parsing: 模型回复到达
    ModelRequest --> Failed: 模型调用失败

    Parsing --> Executing: 解析为 do 动作
    Parsing --> Repairing: 解析失败
    Parsing --> Finished: 解析为 finish

    Executing --> Active: 动作执行成功，下一步
    Executing --> Repairing: 动作执行失败

    Repairing --> Active: 修复成功
    Repairing --> Failed: 修复次数耗尽

    Active --> Paused: 用户暂停
    Paused --> Active: 用户恢复

    Active --> MaxSteps: 达到最大步数
    MaxSteps --> Finished
    Finished --> Idle: 清理资源
    Failed --> Idle: 清理资源
```

**关键状态说明：**

| 状态 | 说明 | 触发条件 |
|------|------|----------|
| `Idle` | 空闲状态 | 初始化/任务结束 |
| `Active` | Agent 主循环运行中 | 进入 `runAgentLoop` |
| `Screenshot` | 截图与 UI 树采集 | 每步开始时 |
| `ModelRequest` | 等待模型响应 | 构建消息后发起请求 |
| `Parsing` | 解析模型输出 | 模型返回文本后 |
| `Executing` | 执行 UI 动作 | 解析得到有效 `do` 动作 |
| `Repairing` | 动作修复中 | 执行失败或解析失败 |
| `Paused` | 暂停状态 | 用户手动暂停 |
| `Finished` | 任务完成 | `finish` 动作或达最大步数 |
| `Failed` | 任务失败 | 不可恢复的错误 |

## 对话历史存储模型

```kotlin
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val updatedAt: Long,
    val messagesJson: String,
)

data class StoredMessageRecord(
    val author: String,
    val content: String,
    val isUser: Boolean,
    val thinkingDurationMs: Long? = null,
    val attachments: List<StoredAttachmentRecord> = emptyList(),
)

data class ConversationRecord(
    val id: Long,
    val title: String,
    val messages: List<StoredMessageRecord>,
    val updatedAt: Long,
)
```
> Sources:
> - [ConversationEntity.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/data/local/ConversationEntity.kt#L7-L14)
> - [ConversationStorageModels.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/data/local/ConversationStorageModels.kt#L6-L28)

对话历史通过 Room 数据库持久化。`messagesJson` 字段将消息列表序列化为 JSON 字符串存储，这种设计避免了为每条消息创建单独数据库行的复杂性。

## 内容安全过滤模型

`ContentFilter` 采用白名单+黑名单双机制：

```kotlin
object ContentFilter {
    // 真正危险的操作（需要拦截）
    private val dangerousKeywords = setOf(
        "delete all", "format device", "factory reset",
        "uninstall system", "root exploit", "malware", "ransomware"
    )

    // 允许的正常操作（不拦截）
    private val allowedKeywords = setOf(
        "purchase", "buy", "payment", "transfer",
        "open app", "settings", "click", "search", // ...
    )
}
```
> Source: [ContentFilter.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/agent/ContentFilter.kt#L20-L62)

**设计意图**：白名单机制允许正常的购物、支付、金融查询等商业操作通过，而黑名单仅拦截真正危险的系统级操作（格式化设备、恶意软件等）。这种"信任用户"的设计避免了过度限制。

## 动作模型 API 参考

### `ParsedAgentAction` 动作名称映射

`ActionExecutor.execute()` 方法根据 `actionName` 路由到具体的执行函数：

| actionName 支持的别名 | 执行方法 | 说明 |
|----------------------|----------|------|
| `launch`, `open_app`, `start_app` | `executeLaunch()` | 启动应用 |
| `back` | `executeBack()` | 返回上一页 |
| `home` | `executeHome()` | 回到桌面 |
| `wait`, `sleep` | `executeWait()` | 等待指定时长 |
| `type`, `input`, `text`, `type_name` | `executeType()` | 输入文本 |
| `tap`, `click`, `press` | `executeTap()` | 点击坐标 |
| `longpress`, `long_press` | `executeLongPress()` | 长按 |
| `doubletap`, `double_tap` | `executeDoubleTap()` | 双击 |
| `swipe`, `scroll` | `executeSwipe()` | 滑动 |
| `take_over`, `takeover` | `executeTakeOver()` | 人工接管 |
| `finish` | 直接返回 `true` | 任务完成 |

> Source: [ActionExecutor.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/executor/ActionExecutor.kt#L171-L211)

### `ActionParser.parse(raw: String): ParsedAgentAction`

解析模型输出的原始文本，提取结构化动作。

**解析策略**（按优先级）：
1. 从 `<answer>` XML 标签中提取动作
2. 从 `【回答开始】【回答结束】` 中文标签中提取
3. 查找 `do(` 或 `finish(` 位置作为兜底策略
4. 支持 Open-AutoGLM 格式的 `<think>` + `<answer>` 标签

> Source: [ActionParser.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/parser/ActionParser.kt#L33-L77)

### `ActionParser.parseWithThinking(content: String): Pair<String?, String>`

同时提取思考过程和回答内容，兼容多种格式：

- Open-AutoGLM 格式：`<think>` + `<answer>`
- Aries 自定义格式：`【思考开始】...【思考结束】` + `【回答开始】...【回答结束】`
- XML 标签格式：`<think>` + `<answer>` 通用标签
- 简单格式：以 `finish(message=` 或 `do(action=` 为分界

> Source: [ActionParser.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/parser/ActionParser.kt#L184-L224)

## 配置选项速览

以下是 `AgentConfiguration` 中最关键的配置项：

| 选项 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `useBackgroundVirtualDisplay` | `Boolean` | `false` | 是否使用后台虚拟屏执行 |
| `useShizukuInteraction` | `Boolean` | `false` | 是否启用 Shizuku 交互模式 |
| `maxSteps` | `Int` | `100` | 最大执行步数 |
| `stepDelayMs` | `Long` | `160` | 每步间基础延迟(ms) |
| `maxModelRetries` | `Int` | `3` | 模型调用最大重试次数 |
| `maxParseRepairs` | `Int` | `2` | 解析修复最大次数 |
| `maxActionRepairs` | `Int` | `1` | 动作执行修复最大次数 |
| `temperature` | `Float?` | `0.0` | 模型温度参数 |
| `maxTokens` | `Int?` | `4096` | 单次回复最大 token 数 |
| `maxContextTokens` | `Int` | `20000` | 上下文 token 上限 |
| `maxUiTreeChars` | `Int` | `3000` | UI 树最大字符数 |
| `maxHistoryTurns` | `Int` | `6` | 最多保留对话轮数 |
| `screenshotCompressionQuality` | `Int` | `85` | 截图压缩质量(0-100) |
| `screenshotMaxSizeKB` | `Int` | `150` | 截图目标最大体积(KB) |

> Source: [AgentConfiguration.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/config/AgentConfiguration.kt#L38-L357)

---

## 配置管理

Aries AI 框架的统一配置管理中心，将原本分散的配置参数整合为一个 Kotlin `data class`，提供完整、可解释、分层可调的自动化行为控制。

### 配置参数分类

`AgentConfiguration` 包含 **100+** 个配置参数，按功能划分为以下类别：

```mermaid
flowchart LR
    subgraph Config["AgentConfiguration 参数分类"]
        direction TB
        E["⚙️ 执行参数<br/>6 项"]
        M["🤖 模型调用参数<br/>4 项"]
        MP["🧠 模型参数<br/>4 项"]
        C["📋 上下文管理<br/>4 项"]
        P["⚡ 性能优化<br/>2 项"]
        S["📸 截图优化<br/>6 项"]
        AD["⏱️ 动作延迟<br/>8 项"]
        WW["🪟 窗口等待<br/>7 项"]
        UT["🌲 UI 树参数<br/>6 项"]
        SF["🛡️ 安全检测<br/>2 项"]
        O["🔧 其他参数<br/>~50 项"]
    end
```

#### 执行参数

控制 Agent 主循环的执行模式与步数限制。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `useBackgroundVirtualDisplay` | `Boolean` | `false` | 是否启用后台虚拟屏模式（任务在后台隔离执行，不影响前台操作） |
| `useShizukuInteraction` | `Boolean` | `false` | 是否启用 Shizuku 交互能力（视图树读取、坐标点击与输入更高效） |
| `maxSteps` | `Int` | `100` | 单次任务最大执行步数，防止模型在错误 UI 上无限循环 |
| `stepDelayMs` | `Long` | `160` | 每步之间的基础延迟（毫秒），给系统和无障碍事件队列「喘息时间」 |
| `postActionDelayMs` | `Long` | `120` | 动作执行后的统一 settle delay，吸收动画/网络加载的尾巴 |

#### 模型调用参数

控制 API 调用的容错与修复策略。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `maxModelRetries` | `Int` | `3` | 模型调用最大重试次数（覆盖网络波动、5xx、超时等场景） |
| `modelRetryBaseDelayMs` | `Long` | `700` | 重试基础延迟（毫秒），配合指数退避算法使用 |
| `maxParseRepairs` | `Int` | `2` | 解析修复最大次数（模型输出格式不符合预期时的修复重试） |
| `maxActionRepairs` | `Int` | `1` | 动作执行修复最大次数（元素找不到、点击无效等执行层失败） |

#### 模型参数

控制模型生成行为的超参数。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `temperature` | `Float?` | `0.0` | 温度参数，自动化任务追求确定性，因此默认接近 0 |
| `topP` | `Float?` | `0.85` | nucleus sampling 参数，控制输出多样性 |
| `frequencyPenalty` | `Float?` | `0.2` | 频率惩罚，减少重复 token 输出 |
| `maxTokens` | `Int?` | `4096` | 单次回复最大 token 数（不等价于上下文窗口） |

#### 上下文管理参数

控制对话上下文窗口，防止上下文爆炸。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `maxContextTokens` | `Int` | `20000` | 最大上下文 token 数，用于本地「上下文裁剪/摘要」策略 |
| `maxUiTreeChars` | `Int` | `3000` | UI 树最大字符数，超过阈值时截断以控制 token 消耗 |
| `maxHistoryTurns` | `Int` | `6` | 最多保留对话轮数（自动化更关注最近几轮状态） |

#### 动作延迟参数

每种动作类型有独立的执行后延迟，精细控制不同操作的等待时间。

| 参数 | 类型 | 默认值 | 适用动作 |
|------|------|--------|----------|
| `launchActionDelayMs` | `Long` | `1050` | `launch`、`open_app`、`start_app` |
| `typeActionDelayMs` | `Long` | `260` | `type`、`input`、`text`、`type_name` |
| `tapActionDelayMs` | `Long` | `320` | `tap`、`click`、`press`、`longpress`、`doubletap` |
| `swipeActionDelayMs` | `Long` | `420` | `swipe`、`scroll` |
| `backActionDelayMs` | `Long` | `220` | `back` |
| `homeActionDelayMs` | `Long` | `420` | `home` |
| `waitActionDelayMs` | `Long` | `650` | `wait` |
| `defaultActionDelayMs` | `Long` | `240` | 其他未匹配动作 |

#### 窗口事件等待参数

动作发出后等待 Accessibility 窗口事件/UI 变化的超时时间。

| 参数 | 类型 | 默认值 | 适用动作 |
|------|------|--------|----------|
| `launchAwaitWindowTimeoutMs` | `Long` | `2200` | 应用启动 |
| `backAwaitWindowTimeoutMs` | `Long` | `1400` | 返回操作 |
| `homeAwaitWindowTimeoutMs` | `Long` | `1800` | Home 键 |
| `tapAwaitWindowTimeoutMs` | `Long` | `1400` | 点击操作 |
| `swipeAwaitWindowTimeoutMs` | `Long` | `1600` | 滑动操作 |
| `typeAwaitWindowTimeoutMs` | `Long` | `1200` | 输入操作 |
| `defaultAwaitWindowTimeoutMs` | `Long` | `1500` | 其他动作 |

### 核心方法

`AgentConfiguration` 不仅存储配置数据，还提供三个关键计算方法：

#### `getActionDelayMs(actionName: String): Long`

根据动作名称映射对应的动作延迟。内部对动作名称做归一化处理（去空格、转小写），确保对于模型的多样化输出格式具有鲁棒性。

```kotlin
fun getActionDelayMs(actionName: String): Long {
    val normalized = actionName.replace(" ", "").lowercase()
    return when (normalized) {
        "launch", "open_app", "start_app" -> launchActionDelayMs
        "type", "input", "text", "type_name" -> typeActionDelayMs
        "tap", "click", "press", "doubletap", "double_tap", "longpress", "long_press" -> tapActionDelayMs
        "swipe", "scroll" -> swipeActionDelayMs
        "back" -> backActionDelayMs
        "home" -> homeActionDelayMs
        "wait" -> waitActionDelayMs
        else -> defaultActionDelayMs
    }
}
```
> Source: [AgentConfiguration.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/config/AgentConfiguration.kt#L380-L392)

**设计意图**：模型输出的动作名称可能因模型而异（例如 `tap` vs `click` vs `press`），通过归一化映射确保无论模型输出哪种命名变体，都能匹配到正确的延迟配置。

#### `getAwaitWindowTimeoutMs(actionName: String): Long`

根据动作名称映射窗口事件等待超时。用于动作执行后等待 UI 变化的判定窗口。

```kotlin
fun getAwaitWindowTimeoutMs(actionName: String): Long {
    val normalized = actionName.replace(" ", "").lowercase()
    return when (normalized) {
        "launch", "open_app", "start_app" -> launchAwaitWindowTimeoutMs
        "back" -> backAwaitWindowTimeoutMs
        "home" -> homeAwaitWindowTimeoutMs
        "tap", "click", "press", "longpress", "long_press", "doubletap", "double_tap" -> tapAwaitWindowTimeoutMs
        "swipe", "scroll" -> swipeAwaitWindowTimeoutMs
        "type", "input", "text" -> typeAwaitWindowTimeoutMs
        else -> defaultAwaitWindowTimeoutMs
    }
}
```
> Source: [AgentConfiguration.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/config/AgentConfiguration.kt#L400-L411)

#### `isDangerousKeyword(text: String): Boolean`

判断文本是否包含危险操作关键词，用于 UI 文本/用户输入/模型输出的风险分级。

```kotlin
fun isDangerousKeyword(text: String): Boolean {
    return dangerousOperationKeywords.any { text.contains(it, ignoreCase = true) }
}
```
> Source: [AgentConfiguration.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/config/AgentConfiguration.kt#L418-L420)

**危险操作关键词**（默认值）：

```kotlin
val dangerousOperationKeywords: List<String> = listOf(
    "支付", "密码", "银行卡", "信用卡", "cvv", "安全码",
    "验证码", "确认支付", "确认付款"
)
```
> Source: [AgentConfiguration.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/config/AgentConfiguration.kt#L353-L356)

### 预设配置

`AgentConfiguration` 在 `companion object` 中提供了两种开箱即用的预设：

#### DEFAULT — 生产环境基线

```kotlin
val DEFAULT = AgentConfiguration()
```
> Source: [AgentConfiguration.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/config/AgentConfiguration.kt#L360)

所有参数使用默认值。适用于日常线上自动化任务，在稳定性、性能和观感之间取得平衡。

#### TEST — 快速测试模式

```kotlin
val TEST = AgentConfiguration(
    maxSteps = 10,
    stepDelayMs = 50L,
    maxModelRetries = 1,
    screenshotThrottleMinIntervalMs = 500L,
    screenshotCacheTtlMs = 1000L,
)
```
> Source: [AgentConfiguration.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/config/AgentConfiguration.kt#L366-L372)

特点：更少步骤、更短延迟、更少重试，便于快速失败并定位问题。适用于单元测试和快速回归。

### 配置流转

下图展示了一个自动化任务中 `AgentConfiguration` 如何被创建并流转到各个组件：

```mermaid
sequenceDiagram
    participant VM as AutomationViewModel
    participant AC as AgentConfiguration
    participant UA as UiAutomationAgent
    participant AE as ActionExecutor
    participant SM as ScreenshotManager

    VM->>AC: 创建配置实例<br/>(useBackgroundVirtualDisplay, useShizukuInteraction...)
    AC-->>VM: config 实例
    VM->>UA: UiAutomationAgent(appContext, config)
    UA->>AE: ActionExecutor(appContext, config)
    UA->>SM: ScreenshotManager(config)

    Note over UA: Agent 主循环中...
    UA->>AC: config.maxSteps
    UA->>AC: config.stepDelayMs
    UA->>AC: getActionDelayMs(actionName)
    UA->>SM: getOptimizedScreenshot(service)

    Note over SM: 截图优化中...
    SM->>AC: enableScreenshotCache
    SM->>AC: screenshotCacheMaxSize
    SM->>AC: screenshotThrottleMinIntervalMs

    Note over AE: 动作执行中...
    AE->>AC: getAwaitWindowTimeoutMs(actionName)
    AE->>AC: useShizukuInteraction
    AE->>AC: useBackgroundVirtualDisplay
```

### 使用示例

#### 基础用法 — 使用默认配置

最简单的使用方式，直接使用 `DEFAULT` 预设：

```kotlin
val agent = UiAutomationAgent(appContext, AgentConfiguration.DEFAULT)

val result = agent.run(
    apiKey = "your-api-key",
    baseUrl = "https://api.example.com",
    model = "autoglm-phone",
    task = "打开微信，给张三发一条消息",
    service = accessibilityService,
    onLog = { log -> println(log) }
)
```
> Source: [UiAutomationAgent.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt#L61-L64)

#### 自定义配置

根据具体场景覆盖默认参数：

```kotlin
val customConfig = AgentConfiguration(
    maxSteps = 50,
    stepDelayMs = 200L,
    screenshotCompressionQuality = 90,
    enableScreenshotCache = true,
    enableScreenshotThrottle = true,
    useStreamingWithEarlyStop = true,
    temperature = 0.1f,
    maxTokens = 8192,
    maxContextTokens = 15000,
)

val agent = UiAutomationAgent(appContext, customConfig)
```
> Source: [README.md](https://github.com/ZG0704666/Aries-AI/blob/main/README.md#L141-L148)

#### ViewModel 中的动态配置

实际项目中，配置通常由 `AutomationViewModel` 根据用户界面选项动态创建：

```kotlin
val config = AgentConfiguration(
    useBackgroundVirtualDisplay = isBackgroundMode,
    useShizukuInteraction = effectiveUseShizuku,
)

val agent = UiAutomationAgent(appContext, config)
val result = agent.run(
    apiKey = apiKey,
    baseUrl = baseUrl,
    model = model,
    useThirdPartyApi = useThirdPartyApi,
    task = task,
    service = svc,
    control = object : UiAutomationAgent.Control {
        override fun isPaused(): Boolean = paused
        override suspend fun confirm(message: String): Boolean { /* ... */ }
    },
    onLog = { msg -> appendLog(msg) },
)
```
> Source: [AutomationViewModel.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/viewmodel/AutomationViewModel.kt#L891-L903)

#### 测试场景 — 使用 TEST 预设

```kotlin
@Test
fun `AgentConfiguration TEST 配置适用于测试`() {
    val testConfig = AgentConfiguration.TEST

    assertEquals(10, testConfig.maxSteps)
    assertEquals(50L, testConfig.stepDelayMs)
    assertEquals(1, testConfig.maxModelRetries)
}
```
> Source: [CoreModuleTest.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/test/java/com/ai/phoneagent/core/CoreModuleTest.kt#L201-L207)

### 配置调优指南

#### 稳定性优先（推荐入门顺序）

```mermaid
flowchart TD
    S[开始调优] --> A{任务经常<br/>中途失败?}
    A -->|是| B1[增加 maxModelRetries → 5]
    A -->|否| C{动作经常<br/>执行失败?}
    B1 --> C
    C -->|是| B2[增加 maxActionRepairs → 3]
    C -->|否| D{页面还没加载完<br/>就开始下一步?}
    B2 --> D
    D -->|是| B3[增大 stepDelayMs<br/>和 postActionDelayMs]
    D -->|否| E[基础稳定性OK ✅]
    B3 --> E
```

#### 性能优化（Token 与延迟控制）

```mermaid
flowchart TD
    E[稳定性 OK] --> F{API 费用过高<br/>或响应太慢?}
    F -->|是| G1[降低 maxContextTokens<br/>减少 maxHistoryTurns]
    F -->|否| H{截图占用<br/>太多带宽?}
    G1 --> H
    H -->|是| G2[降低 screenshotCompressionQuality<br/>减小 screenshotMaxSizeKB]
    H -->|否| I[性能 OK ✅]
    G2 --> I
```

#### 体验优化（动画与观感）

```mermaid
flowchart TD
    I[性能 OK] --> J{操作间停顿<br/>不自然?}
    J -->|是| K1[微调各动作延迟参数<br/>如 tapActionDelayMs]
    J -->|否| L{截图质量<br/>不够清晰?}
    K1 --> L
    L -->|是| K2[提高 screenshotQuality<br/>增大 screenshotScalePercent]
    L -->|否| M[体验 OK ✅]
    K2 --> M
```

### 安全检测机制

`AgentConfiguration` 内置两类关键词列表用于风险检测：

| 关键词列表 | 用途 | 默认条目示例 |
|-----------|------|-------------|
| `sensitiveKeywords` | UI 文本/模型输出/用户输入的风险提示/拦截（偏账户/隐私/验证码） | 支付密码、银行卡、验证码、CVV、OTP |
| `dangerousOperationKeywords` | 判断动作意图是否可能触发资金/账号风险 | 支付、密码、银行卡、确认支付 |

```kotlin
val sensitiveKeywords: List<String> = listOf(
    "支付密码", "银行卡", "信用卡", "卡号", "cvv", "安全码",
    "验证码", "短信验证码", "otp", "一次性密码", "动态口令",
    "输入密码", "请输入密码", "确认支付", "确认付款"
)
```
> Source: [AgentConfiguration.kt](https://github.com/ZG0704666/Aries-AI/blob/main/app/src/main/java/com/ai/phoneagent/core/config/AgentConfiguration.kt#L267-L271)

### 配置 API 参考

#### `data class AgentConfiguration`

统一的 Agent 配置数据类，所有参数均为 `val`（不可变），确保线程安全。

**构造函数参数：** 约 100+ 个参数，详见上方「配置参数分类」各表格。

**预定义实例：**

| 实例 | 说明 |
|------|------|
| `AgentConfiguration.DEFAULT` | 生产环境基线配置 |
| `AgentConfiguration.TEST` | 快速测试配置（maxSteps=10, stepDelayMs=50ms, maxModelRetries=1） |

**实例方法：**

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `getActionDelayMs(actionName: String)` | `Long` | 根据归一化后的动作名返回对应延迟（ms） |
| `getAwaitWindowTimeoutMs(actionName: String)` | `Long` | 根据归一化后的动作名返回窗口等待超时（ms） |
| `isDangerousKeyword(text: String)` | `Boolean` | 判断文本是否包含危险操作关键词 |
