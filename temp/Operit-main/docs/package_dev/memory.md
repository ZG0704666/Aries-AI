# API 文档: `memory.d.ts`

本文档详细介绍了 `memory.d.ts` 文件中定义的 API，该 API 提供了强大的记忆管理功能，允许你创建、查询、更新、删除记忆，以及在记忆之间建立关联关系。

## 概述

所有记忆管理相关的功能都封装在全局的 `Tools.Memory` 命名空间下。记忆系统是一个语义化的知识库，支持：
- 向量化语义搜索
- 记忆的增删改查
- 记忆之间的关联链接
- 文件夹组织
- 标签和元数据管理

**核心概念:**
-   **记忆 (Memory)**: 一个知识单元，包含标题、内容、元数据等信息。
-   **语义搜索**: 基于内容含义而非关键词匹配的智能搜索。
-   **记忆链接**: 记忆之间的关联关系，可以表示"相关"、"因果"、"包含"等语义关系。
-   **文件夹**: 用于组织和分类记忆的层级结构。

---

## `Tools.Memory` 命名空间详解

### `query(query: string, folderPath?: string, threshold?: number, limit?: number): Promise<string>`

向记忆库发送一个自然语言查询，使用语义搜索找到最相关的记忆。

-   **`query`**: **(必须)** 你希望查询的问题或关键词，以字符串形式提供。
-   **`folderPath`**: **可选**。限制搜索范围到特定文件夹路径。如果不提供，则在整个记忆库中搜索。
-   **`threshold`**: **可选** (默认 0.35)。语义相似度阈值，范围 0.0-1.0。只返回相似度高于此阈值的结果。值越高，结果越精确但可能更少。
-   **`limit`**: **可选** (默认 5)。返回的最大结果数量，范围 1-20。

-   **返回值**: 一个 `Promise`，成功时解析为一个字符串，包含从记忆库中检索到的相关信息。

**示例:**

```typescript
async function searchMemories() {
    try {
        // 基本查询
        const result1 = await Tools.Memory.query("如何设置开发环境？");
        console.log("查询结果:\n" + result1);

        // 在特定文件夹中查询
        const result2 = await Tools.Memory.query(
            "Python 配置",
            "/项目文档/后端",
            0.5,
            10
        );
        console.log("文件夹查询结果:\n" + result2);

        complete({ success: true, message: "记忆查询成功" });
    } catch (error) {
        complete({ success: false, message: `查询失败: ${error.message}` });
    }
}
```

### `getByTitle(title: string, chunkIndex?: number, chunkRange?: string, query?: string): Promise<string>`

通过精确的标题获取一个记忆的内容。

-   **`title`**: **(必须)** 记忆的精确标题。
-   **`chunkIndex`**: **可选**。对于文档类型的记忆，指定要获取的块索引。
-   **`chunkRange`**: **可选**。对于文档类型的记忆，指定要获取的块范围（如 "3-7"）。
-   **`query`**: **可选**。在文档中进行语义搜索的查询字符串。

-   **返回值**: 一个 `Promise`，成功时解析为记忆的内容字符串。

**示例:**

```typescript
async function getMemoryByTitle() {
    try {
        // 获取完整记忆
        const content = await Tools.Memory.getByTitle("API 使用指南");
        console.log(content);

        // 获取文档的特定块
        const chunk = await Tools.Memory.getByTitle("长文档", 5);
        console.log("第5块内容:\n" + chunk);

        // 获取文档的块范围
        const chunks = await Tools.Memory.getByTitle("长文档", undefined, "3-7");
        console.log("第3-7块内容:\n" + chunks);

        complete({ success: true, message: "记忆获取成功" });
    } catch (error) {
        complete({ success: false, message: `获取失败: ${error.message}` });
    }
}
```

### `create(title: string, content: string, contentType?: string, source?: string, folderPath?: string): Promise<string>`

创建一个新的记忆。

-   **`title`**: **(必须)** 记忆的标题，应该是唯一的。
-   **`content`**: **(必须)** 记忆的内容。
-   **`contentType`**: **可选** (默认 "text/plain")。内容类型，如 "text/plain", "text/markdown", "application/json" 等。
-   **`source`**: **可选** (默认 "ai_created")。记忆的来源标识。
-   **`folderPath`**: **可选** (默认 "")。记忆所在的文件夹路径。

-   **返回值**: 一个 `Promise`，成功时解析为创建成功的消息字符串。

**示例:**

```typescript
async function createNewMemory() {
    try {
        // 创建简单文本记忆
        await Tools.Memory.create(
            "项目启动步骤",
            "1. 克隆仓库\n2. 安装依赖\n3. 配置环境变量\n4. 运行开发服务器"
        );

        // 创建 Markdown 格式的记忆
        await Tools.Memory.create(
            "API 文档",
            "# REST API\n\n## 端点\n\n- GET /api/users\n- POST /api/users",
            "text/markdown",
            "manual_input",
            "/文档/API"
        );

        // 创建 JSON 数据记忆
        const configData = {
            database: { host: "localhost", port: 5432 },
            cache: { enabled: true, ttl: 3600 }
        };
        await Tools.Memory.create(
            "开发环境配置",
            JSON.stringify(configData, null, 2),
            "application/json",
            "config_export",
            "/配置"
        );

        complete({ success: true, message: "记忆创建成功" });
    } catch (error) {
        complete({ success: false, message: `创建失败: ${error.message}` });
    }
}
```

### `update(oldTitle: string, updates?: UpdateOptions): Promise<string>`

更新一个现有记忆的属性。

-   **`oldTitle`**: **(必须)** 要更新的记忆的当前标题。
-   **`updates`**: **可选**。包含要更新的字段的对象：
    -   `newTitle?: string`: 新的标题。
    -   `content?: string`: 新的内容。
    -   `contentType?: string`: 新的内容类型。
    -   `source?: string`: 新的来源标识。
    -   `credibility?: number`: 可信度 (0.0-1.0)。
    -   `importance?: number`: 重要性 (0.0-1.0)。
    -   `folderPath?: string`: 新的文件夹路径。
    -   `tags?: string`: 标签（逗号分隔的字符串）。

-   **返回值**: 一个 `Promise`，成功时解析为更新成功的消息字符串。

**示例:**

```typescript
async function updateMemory() {
    try {
        // 更新内容
        await Tools.Memory.update("项目启动步骤", {
            content: "1. 克隆仓库\n2. 安装依赖\n3. 配置环境变量\n4. 运行数据库迁移\n5. 运行开发服务器"
        });

        // 重命名并移动到新文件夹
        await Tools.Memory.update("临时笔记", {
            newTitle: "重要会议记录",
            folderPath: "/会议/2024",
            importance: 0.9,
            tags: "会议,重要,2024"
        });

        // 更新元数据
        await Tools.Memory.update("外部数据", {
            credibility: 0.6,
            source: "third_party_api"
        });

        complete({ success: true, message: "记忆更新成功" });
    } catch (error) {
        complete({ success: false, message: `更新失败: ${error.message}` });
    }
}
```

### `deleteMemory(title: string): Promise<string>`

永久删除一个记忆。

-   **`title`**: **(必须)** 要删除的记忆的标题。

-   **返回值**: 一个 `Promise`，成功时解析为删除成功的消息字符串。

**注意**: 此操作不可撤销。删除记忆后，与其相关的所有链接也会被移除。

**示例:**

```typescript
async function deleteOldMemory() {
    try {
        // 删除不再需要的记忆
        await Tools.Memory.deleteMemory("过期的临时笔记");
        
        console.log("记忆已删除");
        complete({ success: true, message: "记忆删除成功" });
    } catch (error) {
        complete({ success: false, message: `删除失败: ${error.message}` });
    }
}
```

### `link(sourceTitle: string, targetTitle: string, linkType?: string, weight?: number, description?: string): Promise<MemoryLinkResultData>`

在两个记忆之间创建一个关联链接。

-   **`sourceTitle`**: **(必须)** 源记忆的标题。
-   **`targetTitle`**: **(必须)** 目标记忆的标题。
-   **`linkType`**: **可选** (默认 "related")。链接类型，描述两个记忆之间的关系。常见类型：
    -   `"related"`: 相关
    -   `"causes"`: 因果关系（源导致目标）
    -   `"explains"`: 解释关系（源解释目标）
    -   `"part_of"`: 包含关系（源是目标的一部分）
    -   `"prerequisite"`: 前置关系（源是目标的前提）
    -   `"contradicts"`: 矛盾关系
-   **`weight`**: **可选** (默认 0.7)。链接强度，范围 0.0-1.0。值越高表示关系越强。
-   **`description`**: **可选** (默认 "")。对这个链接关系的详细描述。

-   **返回值**: 一个 `Promise`，成功时解析为包含链接详情的 `MemoryLinkResultData` 对象。

**示例:**

```typescript
async function linkMemories() {
    try {
        // 创建简单的相关链接
        await Tools.Memory.link(
            "Python 基础教程",
            "Python 进阶技巧"
        );

        // 创建因果关系链接
        await Tools.Memory.link(
            "数据库连接失败",
            "应用启动错误",
            "causes",
            0.9,
            "数据库连接失败导致应用无法启动"
        );

        // 创建解释关系链接
        await Tools.Memory.link(
            "REST API 设计原则",
            "项目 API 架构",
            "explains",
            0.8,
            "REST 原则解释了我们的 API 设计决策"
        );

        // 创建前置关系链接
        await Tools.Memory.link(
            "环境配置指南",
            "项目部署流程",
            "prerequisite",
            1.0,
            "必须先完成环境配置才能部署"
        );

        complete({ success: true, message: "记忆链接创建成功" });
    } catch (error) {
        complete({ success: false, message: `链接失败: ${error.message}` });
    }
}
```

---

## 使用场景

### 场景 1: 构建知识库

创建一个项目相关的知识库，包含文档、配置和最佳实践。

```typescript
async function buildKnowledgeBase() {
    try {
        // 创建项目概述
        await Tools.Memory.create(
            "项目概述",
            "这是一个基于 Android 的 AI 助手应用，提供自动化脚本执行能力...",
            "text/markdown",
            "manual_input",
            "/项目"
        );

        // 创建技术栈文档
        await Tools.Memory.create(
            "技术栈",
            "- Kotlin\n- Android SDK\n- TypeScript\n- Vector Database",
            "text/markdown",
            "manual_input",
            "/项目/技术"
        );

        // 创建架构说明
        await Tools.Memory.create(
            "系统架构",
            "采用分层架构：UI层 -> 业务逻辑层 -> 数据层...",
            "text/markdown",
            "manual_input",
            "/项目/架构"
        );

        // 建立链接关系
        await Tools.Memory.link("技术栈", "项目概述", "part_of");
        await Tools.Memory.link("系统架构", "项目概述", "explains");

        complete({ success: true, message: "知识库构建完成" });
    } catch (error) {
        complete({ success: false, message: `构建失败: ${error.message}` });
    }
}
```

### 场景 2: 智能问答系统

使用语义搜索回答用户问题。

```typescript
async function answerQuestion(question: string) {
    try {
        // 在记忆库中搜索相关信息
        const searchResults = await Tools.Memory.query(
            question,
            undefined,  // 搜索所有文件夹
            0.4,        // 中等相似度阈值
            5           // 返回前5个结果
        );

        if (searchResults && searchResults.trim().length > 0) {
            console.log("找到相关信息:");
            console.log(searchResults);
            
            complete({
                success: true,
                message: "问题回答成功",
                answer: searchResults
            });
        } else {
            complete({
                success: false,
                message: "未找到相关信息"
            });
        }
    } catch (error) {
        complete({ success: false, message: `查询失败: ${error.message}` });
    }
}

// 使用示例
answerQuestion("如何配置数据库连接？");
```

### 场景 3: 记忆整理和维护

定期整理和更新记忆库。

```typescript
async function organizeMemories() {
    try {
        // 更新过时的记忆
        await Tools.Memory.update("旧版 API 文档", {
            newTitle: "API 文档 v1.0 (已废弃)",
            importance: 0.3,
            tags: "废弃,历史版本"
        });

        // 创建新版本
        await Tools.Memory.create(
            "API 文档 v2.0",
            "最新的 API 文档内容...",
            "text/markdown",
            "manual_input",
            "/文档/API"
        );

        // 建立版本关系
        await Tools.Memory.link(
            "API 文档 v1.0 (已废弃)",
            "API 文档 v2.0",
            "related",
            0.5,
            "v2.0 是 v1.0 的升级版本"
        );

        // 删除不再需要的临时记忆
        await Tools.Memory.deleteMemory("临时测试笔记");

        complete({ success: true, message: "记忆整理完成" });
    } catch (error) {
        complete({ success: false, message: `整理失败: ${error.message}` });
    }
}
```

### 场景 4: 学习笔记系统

创建和组织学习笔记，建立知识关联。

```typescript
async function createLearningNotes() {
    try {
        // 创建基础概念笔记
        await Tools.Memory.create(
            "JavaScript 闭包",
            "闭包是指函数可以访问其外部作用域的变量...",
            "text/markdown",
            "learning",
            "/学习/JavaScript"
        );

        await Tools.Memory.create(
            "JavaScript 作用域",
            "作用域决定了变量的可访问性...",
            "text/markdown",
            "learning",
            "/学习/JavaScript"
        );

        await Tools.Memory.create(
            "JavaScript 高阶函数",
            "高阶函数是接受函数作为参数或返回函数的函数...",
            "text/markdown",
            "learning",
            "/学习/JavaScript"
        );

        // 建立知识关联
        await Tools.Memory.link(
            "JavaScript 作用域",
            "JavaScript 闭包",
            "prerequisite",
            0.9,
            "理解作用域是理解闭包的前提"
        );

        await Tools.Memory.link(
            "JavaScript 闭包",
            "JavaScript 高阶函数",
            "related",
            0.7,
            "闭包常用于实现高阶函数"
        );

        complete({ success: true, message: "学习笔记创建完成" });
    } catch (error) {
        complete({ success: false, message: `创建失败: ${error.message}` });
    }
}
```

---

## 最佳实践

### 1. 标题命名规范

-   使用清晰、描述性的标题
-   避免使用特殊字符
-   保持标题的唯一性
-   使用一致的命名风格

### 2. 内容组织

-   使用文件夹进行分类管理
-   为重要记忆设置适当的 `importance` 值
-   使用 `tags` 进行多维度分类
-   定期清理过时的记忆

### 3. 语义搜索优化

-   使用自然语言进行查询
-   根据需求调整 `threshold` 值：
    -   0.2-0.3: 宽松搜索，返回更多结果
    -   0.4-0.5: 平衡搜索，适合大多数场景
    -   0.6-0.8: 严格搜索，只返回高度相关的结果
-   合理设置 `limit` 避免结果过多

### 4. 链接管理

-   选择合适的 `linkType` 描述关系
-   使用 `weight` 表示关系强度
-   添加 `description` 说明链接原因
-   避免创建循环依赖

### 5. 元数据管理

-   设置 `credibility` 标识信息可信度
-   使用 `source` 追踪信息来源
-   通过 `contentType` 明确内容格式
-   利用 `tags` 实现灵活分类

### 6. 错误处理

-   始终使用 try-catch 包裹记忆操作
-   检查记忆是否存在再进行更新或删除
-   验证标题的唯一性
-   处理搜索无结果的情况

---

## 故障排除

**问题: 查询没有返回结果**
-   降低 `threshold` 值尝试宽松搜索
-   检查查询语句是否清晰
-   确认目标记忆确实存在
-   尝试使用不同的关键词

**问题: 创建记忆失败**
-   检查标题是否已存在（标题必须唯一）
-   验证 `folderPath` 格式是否正确
-   确保 `content` 不为空
-   检查 `contentType` 是否有效

**问题: 更新记忆失败**
-   确认 `oldTitle` 精确匹配现有记忆
-   如果要重命名，确保 `newTitle` 不与其他记忆冲突
-   检查更新的字段值是否有效

**问题: 链接创建失败**
-   确认源记忆和目标记忆都存在
-   检查 `weight` 值是否在 0.0-1.0 范围内
-   避免创建重复的链接
-   确保 `linkType` 值合理

**问题: 搜索结果不准确**
-   调整 `threshold` 值
-   使用更具体的查询语句
-   检查记忆内容的质量
-   考虑使用 `folderPath` 限制搜索范围

