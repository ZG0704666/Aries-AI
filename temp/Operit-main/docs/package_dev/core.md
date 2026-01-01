# API 文档: `core.d.ts`

本文档详细介绍了 `core.d.ts` 文件中定义的 API，它们构成了脚本执行环境的基础。这些是所有脚本都可以直接使用的核心全局函数和工具对象。

## 概述

此文件定义了脚本与原生 Android 环境通信、控制执行流程以及进行基本数据操作所需的最基本元素。

-   **全局函数**: 像 `complete()` 和 `toolCall()` 这样的关键函数。
-   **`NativeInterface`**: 用于与底层原生代码进行低级别通信的接口。
-   **工具库**: 提供了类似 Lodash 的 `_` 对象和 `dataUtils` 用于常见的数据处理任务。

---

## 核心 API 详解

### 全局函数

这些函数在全局作用域内直接可用，无需导入。

-   **`complete(result: T): void`**
    这是**最重要**的函数之一。当你的工具函数完成任务后，**必须**调用此函数来结束脚本的执行并返回最终结果。否则，执行环境将一直等待，直到超时。

    -   **`result`**: 任何你想返回给调用方的数据。通常是一个包含 `success: boolean` 和 `message: string` 字段的对象。

-   **`toolCall(toolName: string, params?: object): Promise<any>`**
    一个用于在脚本内部调用其他工具的异步函数。这是一个强大的功能，允许你将复杂的任务分解为多个工具的组合。

    -   **`toolName`**: 要调用的工具的名称。
    -   **`params`**: 传递给被调用工具的参数对象。

### `NativeInterface` 命名空间

这个命名空间提供了与原生 Android 代码直接交互的底层接口。通常，你应该优先使用 `Tools` 对象中封装好的高级 API。

-   `callTool(toolType: string, toolName: string, paramsJson: string): string`: **（旧版）** 同步调用一个原生工具。
-   `callToolAsync(callbackId: string, ...)`: 异步调用一个原生工具。
-   `setResult(result: string): void`: 为脚本执行设置最终结果。`complete()` 函数是这个方法的更高级封装。
-   `setError(error: string): void`: 报告一个错误。
-   `registerImageFromBase64(base64: string, mimeType: string): string`: 将一段 base64 编码的图片数据注册到全局图片池中，并返回一个形如 `<link type="image" id="..."></link>` 的标签字符串，可直接嵌入到工具结果或对话内容中，供支持图片的模型读取。
-   `registerImageFromPath(path: string): string`: 从设备上的图片文件路径注册图片（会自动推断并在必要时转换格式），并返回一个 `<link type="image" id="..."></link>` 标签字符串，适合在工具结果中引用本地截图或文件。
-   `logInfo(message: string): void`: 记录一条信息日志。
-   `logError(message: string): void`: 记录一条错误日志。
-   `reportError(...)`: 报告一个详细的 JavaScript 运行时错误，包括错误类型、消息、行号和堆栈跟踪。

### `_` 工具库 (Lodash-like)

一个全局可用的对象，提供了一组常用的、类似 [Lodash](https://lodash.com/) 的实用函数，用于简化数据操作。

-   `isEmpty(value: any): boolean`: 检查值是否为空（例如，`null`、`undefined`、空字符串、空数组或空对象）。
-   `isString(value: any): boolean`: 检查值是否为字符串。
-   `isNumber(value: any): boolean`: 检查值是否为数字。
-   `isBoolean(value: any): boolean`: 检查值是否为布尔值。
-   `isObject(value: any): boolean`: 检查值是否为对象。
-   `isArray(value: any): boolean`: 检查值是否为数组。
-   `forEach(collection, iteratee)`: 遍历集合（数组或对象）的每个元素。
-   `map(collection, iteratee)`: 通过对集合中的每个元素执行转换函数来创建一个新数组。

### `dataUtils` 工具库

提供了用于数据格式转换的辅助函数。

-   `parseJson(jsonString: string): any`: 将 JSON 格式的字符串解析为 JavaScript 对象。
-   `stringifyJson(obj: any): string`: 将 JavaScript 对象转换为 JSON 格式的字符串。
-   `formatDate(date?: Date | string): string`: 将日期对象或字符串格式化为标准日期字符串。

### `exports` 对象

如果你正在编写一个遵循 CommonJS 模块规范的脚本，可以使用 `exports` 对象来导出你的工具函数，使其可以被脚本执行引擎加载和调用。

**示例:**
```typescript
// in my_script.ts
function my_tool(params) {
    // ...
    complete({ success: true, message: "done" });
}

exports.my_tool = my_tool;
``` 