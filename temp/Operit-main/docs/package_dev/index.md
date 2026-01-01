# API 文档: `index.d.ts`

本文档旨在解释 `index.d.ts` 文件在脚本开发环境中的核心作用。这个文件是整个类型系统的入口点，它本身不定义新的功能，而是负责**组织、聚合和暴露**所有其他 `.d.ts` 文件中定义的类型和接口。

## 核心职责

`index.d.ts` 的主要职责有三个：

1.  **导入与重导出 (Import & Re-export)**:
    它导入了来自 `core.d.ts`, `ui.d.ts`, `android.d.ts` 等所有模块的类型定义，然后再将它们统一导出。这使得开发者在理论上只需要引用这一个文件就能访问到所有 API 的类型信息（尽管 `/// <reference />` 指令通常会自动处理）。

2.  **构建全局 `Tools` 对象**:
    脚本环境中最常用的全局对象 `Tools` 是在这里组装的。`index.d.ts` 将来自不同文件的命名空间（如 `Files`, `Net`, `System`, `UI`, `FFmpeg`）组合成一个统一的 `Tools` 对象，方便开发者通过 `Tools.Files.read()` 这样的形式调用。

    ```typescript
    // index.d.ts (Conceptual)
    import { Files as FilesType } from './files';
    import { Net as NetType } from './network';
    // ... other imports

    declare global {
        const Tools: {
            Files: typeof FilesType;
            Net: typeof NetType;
            // ... other namespaces
        };
    }
    ```

3.  **定义全局作用域 (Global Scope)**:
    这是 `index.d.ts` 最重要的功能之一。它使用 TypeScript 的 `declare global` 块来将最常用、最重要的类、函数和类型注入到全局作用域中。这意味着你在 `.ts` 脚本中可以直接使用它们，而**无需任何 `import` 语句**。

## 全局可用的核心 API

通过 `index.d.ts` 的 `declare global` 设置，以下 API 成为全局可用：

### 核心函数

-   `complete(result: any): void`: 结束脚本执行并返回结果。
-   `toolCall(name: string, params?: object): Promise<any>`: 在脚本内部调用其他工具。
-   `getEnv(key: string): string | undefined`: 读取环境变量值。优先返回应用内“环境配置”界面中设置的值，其次尝试从系统环境变量中读取。常用于访问 API Key 等敏感配置，通常配合脚本 `METADATA` 中的 `env` 字段一起使用。

### 核心类

-   `UINode`: UI 元素操作的核心类 (来自 `ui.d.ts`)。
-   `Intent`: 用于应用间通信的 Intent 类 (来自 `android.d.ts`)。
-   `Android`: Android 底层功能的总入口 (来自 `android.d.ts`)。
-   `OkHttp`: 强大的 HTTP 客户端 (来自 `okhttp.d.ts`)。

### 核心对象

-   `Tools`: 包含所有主要工具集的命名空间对象 (如 `Tools.Files`, `Tools.UI` 等)。
-   `_`: Lodash-like 实用工具库 (来自 `core.d.ts`)。
-   `dataUtils`: 数据处理工具库 (来自 `core.d.ts`)。
-   `CryptoJS`: 加密工具库 (来自 `cryptojs.d.ts`)。
-   `Jimp`: 图像处理库 (来自 `jimp.d.ts`)。
-   `exports`: CommonJS 风格的模块导出对象。

### 核心类型

-   所有在 `results.d.ts` 中定义的返回结果类型，例如 `UIPageResultData`, `FileContentData`, `HttpResponseData`, `GrepResultData` 等，都在全局可用。

## 开发者如何使用

作为脚本开发者，你不需要直接与 `index.d.ts` 交互。你只需要知道，由于这个文件的存在，你可以直接在你的 `.ts` 脚本中使用上述所有全局 API。

为了确保你的 IDE（如 VS Code）能够正确识别这些全局类型并提供智能提示，请务必在你的脚本文件顶部包含三斜杠指令：

```typescript
/// <reference path="./types/index.d.ts" />

// 现在你可以直接使用全局 API，并获得代码补全
const page = await UINode.getCurrentPage();
await Tools.System.sleep(1000);
complete({ success: true });
``` 