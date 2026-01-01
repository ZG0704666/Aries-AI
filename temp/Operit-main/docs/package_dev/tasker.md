# API 文档: `tasker.d.ts`

本文档详细介绍了 `tasker.d.ts` 文件中定义的 API，该 API 提供了与 [Tasker](https://tasker.joaoapps.com/) 自动化应用集成的功能。

## 概述

所有 Tasker 集成相关的功能都封装在全局的 `Tools.Tasker` 命名空间下。这个模块允许你从脚本中向 Tasker 发送事件，实现跨应用的自动化流程。

Tasker 是 Android 平台上功能强大的自动化工具，通过本 API，你可以：
- 从 AI 助手触发 Tasker 任务
- 传递参数给 Tasker 配置文件（Profile）
- 实现复杂的跨应用自动化场景

---

## `Tools.Tasker` 命名空间详解

### `triggerEvent(params: TriggerTaskerEventParams): Promise<string>`

向 Tasker 发送一个事件，触发对应的任务或配置文件。

-   **`params`**: 一个包含事件类型和可选参数的对象。
    -   `task_type: string`: **(必须)** 事件类型标识符。这个值应该与你在 Tasker 中配置的事件名称完全匹配。
    -   `arg1?: string`: **可选**。第一个字符串参数，可以在 Tasker 中通过 `%arg1` 变量访问。
    -   `arg2?: string`: **可选**。第二个字符串参数，可以在 Tasker 中通过 `%arg2` 变量访问。
    -   `arg3?: string`: **可选**。第三个字符串参数，可以在 Tasker 中通过 `%arg3` 变量访问。
    -   `arg4?: string`: **可选**。第四个字符串参数，可以在 Tasker 中通过 `%arg4` 变量访问。
    -   `arg5?: string`: **可选**。第五个字符串参数，可以在 Tasker 中通过 `%arg5` 变量访问。
    -   `args_json?: string`: **可选**。JSON 格式的字符串，用于传递复杂的数据结构。当5个简单参数不够用，或需要传递对象、数组等复杂数据时使用。

-   **返回值**: 一个 `Promise`，成功时解析为一个来自原生层的状态消息字符串。

---

## 使用场景

### 场景 1: 简单事件触发

触发 Tasker 中预定义的任务，不需要传递额外参数。

**Tasker 配置:**
- 创建一个 Profile，Event 类型选择 "Intent Received"
- Action 设置为监听自定义广播
- 或者使用 Tasker 的插件接口

**示例:**
```typescript
async function triggerSimpleTask() {
    try {
        const result = await Tools.Tasker.triggerEvent({
            task_type: "MorningRoutine"
        });
        
        console.log(`Tasker 响应: ${result}`);
        complete({ success: true, message: "成功触发晨间例行任务。" });
    } catch (error) {
        complete({ success: false, message: `触发失败: ${error.message}` });
    }
}
```

### 场景 2: 带参数的事件

向 Tasker 传递一些简单的字符串参数，让 Tasker 根据参数执行不同的逻辑。

**Tasker 配置:**
- 在 Task 中使用 `%arg1`, `%arg2` 等变量
- 例如: 显示通知时使用 `%arg1` 作为标题，`%arg2` 作为内容

**示例:**
```typescript
async function sendNotificationViaTasker() {
    const title = "会议提醒";
    const message = "您的会议将在15分钟后开始";
    const priority = "high";

    try {
        await Tools.Tasker.triggerEvent({
            task_type: "ShowNotification",
            arg1: title,
            arg2: message,
            arg3: priority
        });

        complete({ success: true, message: "通知事件已发送给 Tasker。" });
    } catch (error) {
        complete({ success: false, message: `发送失败: ${error.message}` });
    }
}
```

### 场景 3: 传递复杂数据结构

当需要传递数组、对象或超过5个参数时，使用 `args_json` 字段。

**Tasker 配置:**
- 在 Task 中使用 JavaScript 或 JavaScriptlet 操作解析 JSON
- 访问 `args_json` 变量并用 `JSON.parse()` 解析

**示例:**
```typescript
async function triggerComplexTask() {
    const taskData = {
        action: "process_data",
        timestamp: Date.now(),
        items: [
            { id: 1, name: "任务A", priority: "high" },
            { id: 2, name: "任务B", priority: "medium" }
        ],
        config: {
            retry: true,
            timeout: 30000,
            notification: true
        }
    };

    try {
        await Tools.Tasker.triggerEvent({
            task_type: "DataProcessor",
            args_json: JSON.stringify(taskData)
        });

        complete({ 
            success: true, 
            message: "复杂数据已发送给 Tasker 处理。" 
        });
    } catch (error) {
        complete({ 
            success: false, 
            message: `处理失败: ${error.message}` 
        });
    }
}
```

### 场景 4: 混合使用简单参数和 JSON

你可以同时使用 `arg1-arg5` 和 `args_json`，例如用简单参数标识任务类型，用 JSON 传递详细数据。

**示例:**
```typescript
async function hybridParameters() {
    try {
        await Tools.Tasker.triggerEvent({
            task_type: "SmartHomeControl",
            arg1: "bedroom",        // 房间
            arg2: "light",          // 设备类型
            arg3: "on",             // 基本操作
            args_json: JSON.stringify({
                brightness: 80,
                color: "#FF5733",
                transition_time: 2000,
                schedule: {
                    start: "18:00",
                    end: "22:00"
                }
            })
        });

        complete({ success: true, message: "智能家居指令已发送。" });
    } catch (error) {
        complete({ success: false, message: `发送失败: ${error.message}` });
    }
}
```

---

## 集成指南

### 在 Tasker 中接收事件

有多种方式在 Tasker 中接收从本 API 发送的事件：

#### 方法 1: 使用 Intent Received Event (推荐)

1. 在 Tasker 中创建新的 Profile
2. 选择 Event → System → Intent Received
3. 设置 Action 为你的应用发送的自定义 Intent action
4. 在 Task 中使用 `%arg1` 到 `%arg5` 访问参数

#### 方法 2: 使用 Tasker 插件接口

如果你的应用实现了 Tasker 插件接口，可以直接接收事件。

#### 方法 3: 通过广播接收器

使用系统广播机制，Tasker 监听特定的广播 action。

### 在 Tasker Task 中访问参数

**简单参数:**
```
// 在 Tasker Task 中
Variable Set: %notification_title to %arg1
Variable Set: %notification_body to %arg2
Flash: %notification_title - %notification_body
```

**JSON 参数:**
```
// 在 Tasker Task 中使用 JavaScriptlet
Variable Set: %json_data to %args_json

// JavaScriptlet Action:
var data = JSON.parse(args_json);
var items = data.items;
var config = data.config;

// 处理数据...
setLocal("result", "处理完成");
```

---

## 最佳实践

1.  **事件命名规范**: 使用清晰、描述性的 `task_type` 名称，建议使用 PascalCase 或 snake_case 风格。

2.  **参数验证**: 在 Tasker 端添加参数验证逻辑，确保接收到的数据符合预期。

3.  **错误处理**: 始终使用 try-catch 包裹 `triggerEvent` 调用，处理可能的失败情况。

4.  **JSON 格式**: 使用 `JSON.stringify()` 确保传递的是有效的 JSON 字符串。

5.  **测试**: 先在 Tasker 中手动测试 Task，确认逻辑正确后再通过 API 触发。

6.  **文档**: 为每个 `task_type` 编写文档，说明需要哪些参数及其格式。

---

## 故障排除

**问题: Tasker 没有响应事件**
- 检查 Tasker Profile 是否已启用
- 确认 `task_type` 与 Tasker 中配置的完全匹配（区分大小写）
- 检查 Tasker 的日志 (Run Log) 查看是否接收到事件

**问题: 参数在 Tasker 中为空**
- 确认在发送时参数值不为 `undefined` 或 `null`
- 检查 Tasker 中变量名是否正确 (`%arg1` 而非 `arg1`)

**问题: JSON 解析失败**
- 使用 `JSON.stringify()` 而非字符串拼接
- 确保传递的对象不包含循环引用
- 在 Tasker 中添加 try-catch 处理 JSON 解析错误

