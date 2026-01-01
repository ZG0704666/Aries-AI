# API 文档: `workflow.d.ts`

本文档详细介绍了 `workflow.d.ts` 文件中定义的 API，该 API 提供了强大的工作流（Workflow）管理功能，允许你创建、管理和执行自动化工作流。

## 概述

所有工作流相关的功能都封装在全局的 `Tools.Workflow` 命名空间下。工作流是一种可视化的自动化编排工具，由节点（Nodes）和连接（Connections）组成，可以实现复杂的自动化任务序列。

**核心概念:**
-   **工作流 (Workflow)**: 一个完整的自动化流程，包含多个节点和它们之间的连接。
-   **节点 (Node)**: 工作流中的基本单元，分为触发器节点（Trigger）和执行节点（Execute）。
-   **触发器节点 (Trigger Node)**: 定义工作流何时启动，例如定时触发、事件触发等。
-   **执行节点 (Execute Node)**: 定义要执行的具体操作，例如发送通知、调用 API、操作文件等。
-   **连接 (Connection)**: 定义节点之间的执行顺序和数据流动。

---

## 工作流结构

### 节点类型

#### 触发器节点 (Trigger Node)
触发器节点是工作流的入口点，定义了工作流的启动条件。

**常见触发器类型:**
-   **定时触发**: 在特定时间或按固定间隔执行
-   **事件触发**: 当特定系统事件发生时执行
-   **手动触发**: 通过 API 或 UI 手动启动
-   **条件触发**: 当特定条件满足时执行

#### 执行节点 (Execute Node)
执行节点定义了工作流中要执行的具体操作。

**常见执行类型:**
-   **工具调用**: 调用系统工具（Files, Net, System 等）
-   **条件判断**: 根据条件决定执行分支
-   **循环操作**: 重复执行某些操作
-   **数据转换**: 处理和转换数据

### 连接 (Connection)
连接定义了节点之间的关系和执行流程，包括：
-   从哪个节点开始 (`from`)
-   到哪个节点结束 (`to`)
-   可选的条件判断
-   数据传递规则

---

## `Tools.Workflow` 命名空间详解

### `create(params: CreateParams): Promise<Info>`

创建一个新的工作流。

-   **`params`**: 工作流创建参数对象
    -   `name: string`: **(必须)** 工作流的名称，用于标识和显示。
    -   `description?: string`: **可选**。工作流的详细描述，说明其用途和功能。
    -   `nodes?: Node[] | string`: **可选**。节点数组或 JSON 字符串。如果不提供，将创建一个空工作流。
    -   `connections?: Connection[] | string`: **可选**。连接数组或 JSON 字符串。定义节点之间的执行流程。
    -   `enabled?: boolean`: **可选** (默认为 `true`)。工作流是否启用。禁用的工作流不会被触发器启动。

-   **返回值**: 一个 `Promise`，成功时解析为包含工作流基本信息的 `Info` 对象，包括工作流 ID、名称、创建时间等。

### `get(params: GetParams): Promise<Detail>`

获取指定工作流的完整详细信息。

-   **`params`**: 查询参数对象
    -   `workflow_id: string`: **(必须)** 要查询的工作流的唯一标识符。

-   **返回值**: 一个 `Promise`，成功时解析为包含工作流完整信息的 `Detail` 对象，包括所有节点、连接和配置。

### `update(params: UpdateParams): Promise<Info>`

更新现有工作流的配置。

-   **`params`**: 更新参数对象
    -   `workflow_id: string`: **(必须)** 要更新的工作流的 ID。
    -   `name?: string`: **可选**。新的工作流名称。
    -   `description?: string`: **可选**。新的工作流描述。
    -   `nodes?: Node[] | string`: **可选**。更新后的节点配置。会完全替换现有节点。
    -   `connections?: Connection[] | string`: **可选**。更新后的连接配置。会完全替换现有连接。
    -   `enabled?: boolean`: **可选**。启用或禁用工作流。

-   **返回值**: 一个 `Promise`，成功时解析为更新后的工作流信息。

### `deleteWorkflow(params: DeleteParams): Promise<string>`

永久删除一个工作流。

-   **`params`**: 删除参数对象
    -   `workflow_id: string`: **(必须)** 要删除的工作流的 ID。

-   **返回值**: 一个 `Promise`，成功时解析为确认删除的消息字符串。

**注意**: 此操作不可撤销，删除后工作流的所有数据将永久丢失。

### `list(): Promise<List>`

列出所有已创建的工作流。

-   **返回值**: 一个 `Promise`，成功时解析为包含所有工作流基本信息的数组。

### `trigger(params: TriggerParams): Promise<string>`

手动触发执行一个工作流。

-   **`params`**: 触发参数对象
    -   `workflow_id: string`: **(必须)** 要触发的工作流的 ID。

-   **返回值**: 一个 `Promise`，成功时解析为执行结果消息。

---

## 使用示例

### 示例 1: 创建简单的定时工作流

创建一个每天早上8点发送通知的工作流。

```typescript
async function createMorningNotificationWorkflow() {
    try {
        const workflow = await Tools.Workflow.create({
            name: "晨间通知",
            description: "每天早上8点发送一条激励通知",
            nodes: [
                {
                    id: "trigger_1",
                    type: "trigger",
                    name: "定时触发器",
                    config: {
                        trigger_type: "time",
                        time: "08:00",
                        repeat: "daily"
                    },
                    position: { x: 100, y: 100 }
                },
                {
                    id: "action_1",
                    type: "execute",
                    name: "发送通知",
                    config: {
                        tool: "notification",
                        title: "早安！",
                        message: "新的一天开始了，加油！"
                    },
                    position: { x: 300, y: 100 }
                }
            ],
            connections: [
                {
                    from: "trigger_1",
                    to: "action_1"
                }
            ],
            enabled: true
        });

        console.log(`工作流创建成功，ID: ${workflow.id}`);
        complete({ 
            success: true, 
            message: "晨间通知工作流已创建",
            workflow_id: workflow.id 
        });
    } catch (error) {
        complete({ 
            success: false, 
            message: `创建失败: ${error.message}` 
        });
    }
}
```

### 示例 2: 创建条件分支工作流

创建一个根据天气情况发送不同提醒的工作流。

```typescript
async function createWeatherBasedWorkflow() {
    try {
        const workflow = await Tools.Workflow.create({
            name: "天气提醒",
            description: "根据天气情况发送合适的提醒",
            nodes: [
                {
                    id: "trigger_1",
                    type: "trigger",
                    name: "每日触发",
                    config: {
                        trigger_type: "time",
                        time: "07:00"
                    },
                    position: { x: 100, y: 200 }
                },
                {
                    id: "fetch_weather",
                    type: "execute",
                    name: "获取天气",
                    config: {
                        tool: "http_get",
                        url: "https://api.weather.com/v1/current"
                    },
                    position: { x: 300, y: 200 }
                },
                {
                    id: "condition_check",
                    type: "execute",
                    name: "判断天气",
                    config: {
                        tool: "condition",
                        expression: "weather.rain > 0"
                    },
                    position: { x: 500, y: 200 }
                },
                {
                    id: "rainy_notify",
                    type: "execute",
                    name: "下雨提醒",
                    config: {
                        tool: "notification",
                        title: "记得带伞",
                        message: "今天有雨，出门记得带伞哦！"
                    },
                    position: { x: 700, y: 100 }
                },
                {
                    id: "sunny_notify",
                    type: "execute",
                    name: "晴天提醒",
                    config: {
                        tool: "notification",
                        title: "天气不错",
                        message: "今天天气很好，适合出门！"
                    },
                    position: { x: 700, y: 300 }
                }
            ],
            connections: [
                { from: "trigger_1", to: "fetch_weather" },
                { from: "fetch_weather", to: "condition_check" },
                { 
                    from: "condition_check", 
                    to: "rainy_notify",
                    condition: "true"
                },
                { 
                    from: "condition_check", 
                    to: "sunny_notify",
                    condition: "false"
                }
            ],
            enabled: true
        });

        complete({ 
            success: true, 
            message: "天气提醒工作流已创建",
            workflow_id: workflow.id 
        });
    } catch (error) {
        complete({ 
            success: false, 
            message: `创建失败: ${error.message}` 
        });
    }
}
```

### 示例 3: 查询和更新工作流

获取工作流详情并更新其配置。

```typescript
async function updateWorkflowExample() {
    const workflowId = "workflow_12345";

    try {
        // 获取当前工作流详情
        const details = await Tools.Workflow.get({
            workflow_id: workflowId
        });

        console.log(`当前工作流名称: ${details.name}`);
        console.log(`节点数量: ${details.nodes.length}`);
        console.log(`连接数量: ${details.connections.length}`);

        // 更新工作流（例如暂时禁用）
        await Tools.Workflow.update({
            workflow_id: workflowId,
            enabled: false,
            description: "暂时禁用 - 维护中"
        });

        console.log("工作流已更新并禁用");
        complete({ success: true, message: "工作流更新成功" });
    } catch (error) {
        complete({ 
            success: false, 
            message: `操作失败: ${error.message}` 
        });
    }
}
```

### 示例 4: 列出所有工作流

列出系统中所有工作流并显示其状态。

```typescript
async function listAllWorkflows() {
    try {
        const workflows = await Tools.Workflow.list();

        console.log(`共有 ${workflows.length} 个工作流:`);

        workflows.forEach((wf, index) => {
            console.log(`${index + 1}. ${wf.name}`);
            console.log(`   ID: ${wf.id}`);
            console.log(`   状态: ${wf.enabled ? '启用' : '禁用'}`);
            console.log(`   描述: ${wf.description || '无'}`);
            console.log(`   创建时间: ${wf.createdAt}`);
            console.log('---');
        });

        complete({ 
            success: true, 
            data: workflows,
            message: `找到 ${workflows.length} 个工作流` 
        });
    } catch (error) {
        complete({ 
            success: false, 
            message: `查询失败: ${error.message}` 
        });
    }
}
```

### 示例 5: 手动触发工作流

手动执行一个工作流（绕过触发器条件）。

```typescript
async function manualTriggerWorkflow() {
    const workflowId = "workflow_12345";

    try {
        console.log("正在手动触发工作流...");
        
        const result = await Tools.Workflow.trigger({
            workflow_id: workflowId
        });

        console.log(`执行结果: ${result}`);
        complete({ 
            success: true, 
            message: "工作流已成功执行",
            result: result 
        });
    } catch (error) {
        complete({ 
            success: false, 
            message: `触发失败: ${error.message}` 
        });
    }
}
```

### 示例 6: 删除工作流

删除不再需要的工作流。

```typescript
async function deleteWorkflowExample() {
    const workflowId = "workflow_12345";

    try {
        // 先确认工作流存在
        const details = await Tools.Workflow.get({
            workflow_id: workflowId
        });

        console.log(`准备删除工作流: ${details.name}`);

        // 执行删除
        const result = await Tools.Workflow.deleteWorkflow({
            workflow_id: workflowId
        });

        console.log(result);
        complete({ 
            success: true, 
            message: "工作流已删除" 
        });
    } catch (error) {
        complete({ 
            success: false, 
            message: `删除失败: ${error.message}` 
        });
    }
}
```

---

## 工作流设计最佳实践

### 1. 节点命名规范
-   使用描述性的节点名称，清楚说明节点的功能
-   触发器节点建议以 "trigger_" 开头
-   执行节点建议以功能命名，如 "fetch_data", "send_notification"

### 2. 合理组织节点
-   将相关节点在画布上放置在一起
-   使用清晰的 position 坐标，保持视觉上的整洁
-   复杂工作流考虑使用子工作流功能（如果支持）

### 3. 错误处理
-   为关键节点添加错误处理分支
-   使用条件节点检查上一步的执行结果
-   考虑添加重试逻辑和失败通知

### 4. 性能优化
-   避免在工作流中使用过长的 sleep 操作
-   合理使用并行执行（如果多个节点无依赖关系）
-   定期清理不再使用的工作流

### 5. 测试和调试
-   先创建简单的测试工作流验证逻辑
-   使用 `trigger()` 方法手动测试工作流
-   查看执行日志，了解每个节点的执行情况
-   在生产环境启用前，充分测试各种边界情况

### 6. 版本管理
-   在更新工作流前，记录当前配置（使用 `get()` 获取）
-   重大更新时考虑创建新工作流而非更新现有工作流
-   为工作流添加版本号在描述中，如 "v1.2 - 添加了错误重试"

### 7. 文档记录
-   在 `description` 字段中详细说明工作流的用途
-   记录每个节点的配置参数和作用
-   说明节点之间的数据流动和依赖关系

---

## 节点配置参考

### 触发器节点配置示例

**定时触发:**
```typescript
{
    id: "trigger_1",
    type: "trigger",
    name: "定时触发器",
    config: {
        trigger_type: "time",
        time: "09:00",
        repeat: "daily"  // daily, weekly, monthly
    }
}
```

**事件触发:**
```typescript
{
    id: "trigger_2",
    type: "trigger",
    name: "通知事件",
    config: {
        trigger_type: "event",
        event: "notification_received",
        filter: { app: "com.example.app" }
    }
}
```

### 执行节点配置示例

**HTTP 请求:**
```typescript
{
    id: "action_1",
    type: "execute",
    name: "API 调用",
    config: {
        tool: "http_request",
        method: "POST",
        url: "https://api.example.com/data",
        body: { key: "value" }
    }
}
```

**文件操作:**
```typescript
{
    id: "action_2",
    type: "execute",
    name: "写入日志",
    config: {
        tool: "file_write",
        path: "/sdcard/logs/workflow.log",
        content: "{{timestamp}} - 工作流执行"
    }
}
```

**条件判断:**
```typescript
{
    id: "action_3",
    type: "execute",
    name: "检查条件",
    config: {
        tool: "condition",
        expression: "{{previous_result.success}} === true"
    }
}
```

---

## 故障排除

**问题: 工作流创建失败**
- 检查 nodes 和 connections 的 JSON 格式是否正确
- 确保所有必需字段都已提供
- 验证节点 ID 的唯一性
- 检查连接中引用的节点 ID 是否存在

**问题: 工作流不执行**
- 确认工作流的 `enabled` 状态为 `true`
- 检查触发器配置是否正确
- 验证触发条件是否满足
- 查看系统日志了解详细错误信息

**问题: 节点执行失败**
- 检查节点的 config 配置是否完整
- 验证工具调用的参数是否正确
- 确认前置节点是否成功执行
- 查看节点执行日志

**问题: 连接没有生效**
- 确认连接的 from 和 to 节点 ID 正确
- 检查是否有条件字段，条件表达式是否正确
- 验证节点之间是否有循环依赖
- 确保连接的逻辑顺序合理

