# API 文档: `tool-types.d.ts`

本文档旨在解释 `tool-types.d.ts` 文件在脚本类型系统中的作用。这个文件本身不直接提供可执行的 API，而是为 TypeScript 编译器和开发工具提供**元数据（Metadata）**，核心是定义了**工具名称**与**其返回结果类型**之间的映射关系。

## 核心职责

`tool-types.d.ts` 的主要职责有两个：

1.  **定义工具名称类型 (Tool Name Types)**:
    它将所有可用的原生工具按照类别（File, Net, System, UI 等）进行分组，并为每个类别定义一个字符串字面量联合类型（Union Type）。例如 `FileToolName` 可能包含 `'list_files' | 'read_file' | ...`。最后，将所有这些类型合并成一个总的 `ToolName` 类型。

2.  **建立工具与返回值的映射 (`ToolResultMap`)**:
    这是该文件最关键的部分。它定义了一个名为 `ToolResultMap` 的接口，这个接口就像一张**“查询表”**，将每个具体的工具名称（字符串）映射到该工具成功执行后 `Promise` 将解析出的具体数据结构类型。

    **示例 (`ToolResultMap` 接口的一部分):**
    ```typescript
    import { DirectoryListingData, FileContentData, ... } from './results';

    export interface ToolResultMap {
        // 文件操作
        'list_files': DirectoryListingData;
        'read_file': FileContentData;

        // 网络操作
        'http_request': HttpResponseData;

        // UI 操作
        'get_page_info': UIPageResultData;

        // ... 更多映射
    }
    ```

## 开发者如何受益

虽然你作为脚本开发者不需要直接导入或使用此文件，但它的存在极大地提升了开发体验，主要体现在对 `toolCall` 函数的支持上。

`core.d.ts` 中的 `toolCall` 函数利用了 `ToolResultMap` 提供了强大的类型推断能力。

```typescript
// in core.d.ts
export type ToolReturnType<T extends string> = T extends keyof import('./tool-types').ToolResultMap
    ? import('./tool-types').ToolResultMap[T]
    : any;

export declare function toolCall<T extends string>(toolName: T, ...): Promise<ToolReturnType<T>>;
```

当你调用 `toolCall` 时，TypeScript 会：
1.  检查你传入的 `toolName` 字符串。
2.  在 `ToolResultMap` 中查找这个字符串键。
3.  如果找到，它就知道 `toolCall` 返回的 `Promise` 将解析为什么类型。

**实际效果:**

```typescript
/// <reference path="./types/index.d.ts" />

async function example() {
    // 因为 ToolResultMap['get_page_info'] 是 UIPageResultData，
    // 所以 TypeScript 知道 pageInfo 变量的类型是 UIPageResultData。
    const pageInfo = await toolCall('get_page_info');

    // 因此，你可以获得精确的代码补全和类型检查。
    // a. Correct:
    console.log(pageInfo.activityName);

    // b. Error: Property 'nonExistentProp' does not exist on type 'UIPageResultData'.
    // console.log(pageInfo.nonExistentProp);
}
```

简而言之，`tool-types.d.ts` 是实现脚本开发环境中**类型安全**和**智能提示**的关键底层文件之一，它通过映射关系连接了“调用”和“结果”。 