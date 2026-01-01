# API 文档: `results.d.ts`

本文档旨在解释 `results.d.ts` 文件在脚本类型系统中的作用。这个文件本身不定义任何可执行的 API 或函数，它的唯一职责是**定义所有工具成功执行后返回的数据对象的 TypeScript 接口（Interface）**。

## 核心职责

`results.d.ts` 是类型系统的基石之一。它为每个工具的输出提供了具体、严格的类型定义。这确保了当你在脚本中接收工具的返回结果时，可以获得准确的类型信息。

**主要内容:**

1.  **定义基础接口**:
    -   `BaseResult`: 这是一个所有结果类型都隐式包含的基础接口，通常定义了 `success: boolean` 和 `error?: string` 字段。

2.  **为每个工具的返回数据定义具体接口**:
    该文件为各种操作（文件、网络、UI、系统等）可能返回的数据结构定义了详细的接口。

    **示例 (`FileContentData` 和 `HttpResponseData`):**
    ```typescript
    // 文件读取结果的数据结构
    export interface FileContentData {
        path: string;
        content: string;
        size: number;
        toString(): string;
    }

    // HTTP 请求结果的数据结构
    export interface HttpResponseData {
        url: string;
        statusCode: number;
        statusMessage: string;
        headers: Record<string, string>;
        content: string;
        // ... more properties
        toString(): string;
    }
    ```

3.  **组合和封装结果类型**:
    有时，它还会将上述数据接口封装在更高一级的 `Result` 接口中，例如将 `FileContentData` 封装在 `FileContentResult` 中，后者会明确包含 `success` 和 `data` 字段。

## 开发者如何受益

作为脚本开发者，你不会直接导入或使用 `results.d.ts`。它的价值体现在与 `tool-types.d.ts` 的协同工作中，为你的开发过程带来类型安全和智能提示。

**工作流程:**

1.  你在脚本中调用一个工具，例如 `await Tools.Files.read('/path/to/file')`。
2.  TypeScript 查看 `Tools.Files.read` 的函数签名，发现它返回 `Promise<FileContentData>`。
3.  因为 `FileContentData` 接口在 `results.d.ts` 中有明确的定义，所以当你接收返回值时，IDE 就能准确地知道这个对象有哪些属性。

**实际效果:**

```typescript
/// <reference path="./types/index.d.ts" />

async function example() {
    const fileResult = await Tools.Files.read('/path/to/file');

    // IDE 知道 fileResult 是 FileContentData 类型
    // 因此，当你输入 "fileResult." 时，它会智能地提示 content, path, size 等属性。
    console.log(fileResult.content);

    // 如果你尝试访问一个不存在的属性，TypeScript 会报错。
    // Error: Property 'nonExistent' does not exist on type 'FileContentData'.
    // console.log(fileResult.nonExistent);
}
```

简而言之，`results.d.ts` 是所有工具返回值的“**数据蓝图**”。它通过为输出数据提供清晰的结构定义，确保了整个脚本开发环境的类型安全和可靠性，并极大地改善了代码自动补全的体验。 