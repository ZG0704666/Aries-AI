# API 文档: `ui.d.ts`

本文档详细介绍了 `ui.d.ts` 文件中定义的 API，这是进行 UI 自动化脚本编写的核心。它提供了检查屏幕内容和模拟用户交互的强大功能。

## 概述

UI 自动化 API 主要由两部分组成：

1.  **`Tools.UI` 命名空间**: 提供了一系列用于执行基本 UI 操作（如点击、滑动、输入文本）的全局函数。
2.  **`UINode` 类**: 一个功能极其强大的类，它将屏幕上的 UI 元素封装成一个类似 Web DOM 节点的对​​象，允许你查询元素属性、遍历 UI 树以及对特定元素执行操作。

---

## `Tools.UI` 命名空间详解

这些函数提供了直接与屏幕交互的基础能力。

-   `getPageInfo(): Promise<UIPageResultData>`:
    获取当前屏幕的完整 UI 结构快照。这是绝大多数 UI 自动化任务的起点。返回的数据可以被 `UINode.fromPageInfo()` 用来构造一个 `UINode` 树。

-   `tap(x: number, y: number): Promise<UIActionResultData>`:
    在屏幕的指定 `(x, y)` 坐标处模拟一次点击。

-   `swipe(startX: number, startY: number, endX: number, endY: number): Promise<UIActionResultData>`:
    模拟一次从 `(startX, startY)` 到 `(endX, endY)` 的滑动操作。

-   `clickElement(...)`:
    一个重载的、非常灵活的函数，用于查找并点击一个元素。你可以通过多种方式指定目标元素：
    -   `clickElement({ resourceId?: '...', text?: '...', ... })`: 通过包含多个属性的对象来精确查找。
    -   `clickElement('com.app:id/button')`: 通过 `resourceId` 点击。
    -   `clickElement('com.app:id/button', 2)`: 点击 `resourceId` 匹配的第3个元素 (索引从0开始)。

-   `setText(text: string): Promise<UIActionResultData>`:
    在当前聚焦的输入框中输入指定的文本。

-   `pressKey(keyCode: string): Promise<UIActionResultData>`:
    模拟按下物理或虚拟按键，例如 `'BACK'`, `'HOME'`, `'ENTER'`。

---

## `UINode` 类详解

`UINode` 是 UI 自动化中最核心、最强大的工具。它将从 `getPageInfo()` 获取的原始 UI 数据转换成一个易于操作的树状对象模型。

**获取 `UINode` 实例的主要方式:**

```typescript
// 获取代表当前整个页面的根 UINode 节点
const page = await UINode.getCurrentPage();
```

### 核心属性 (只读)

-   `className: string`: 元素的类名 (e.g., `'android.widget.TextView'`)。
-   `text: string`: 元素的文本内容。
-   `contentDesc: string`: 元素的“内容描述”，常用于无文本的按钮。
-   `resourceId: string`: 元素的唯一资源 ID。
-   `bounds: string`: 元素在屏幕上的矩形坐标 (e.g., `'[0,100][200,300]'`)。
-   `isClickable: boolean`: 元素是否可点击。
-   `children: UINode[]`: 包含所有子节点的 `UINode` 数组。
-   `parent: UINode`: 父 `UINode` 节点。
-   `centerPoint: { x: number, y: number }`: 根据 `bounds` 计算出的中心点坐标。

### 核心方法

#### 查找和遍历

-   `find(criteria: object | function): UINode | undefined`:
    查找**第一个**满足条件的后代节点。
-   `findAll(criteria: object | function): UINode[]`:
    查找**所有**满足条件的后代节点。

-   **便捷查找方法 (常用):**
    -   `findByText(text: string, options?: ...)`: 通过文本内容查找。
    -   `findById(id: string, options?: ...)`: 通过 `resourceId` 查找 (最推荐，最稳定)。
    -   `findByClass(className: string, options?: ...)`: 通过类名查找。
    -   `findByContentDesc(description: string, options?: ...)`: 通过内容描述查找。
    -   `findAllBy...`: 与上述方法对应，但返回所有匹配项的数组。

#### 交互操作

-   `click(): Promise<UIActionResultData>`:
    点击该节点。它会自动计算元素的中心点并执行 `tap` 操作。

-   `setText(text: string): Promise<UIActionResultData>`:
    在此节点上输入文本（仅当该节点是输入框时有效）。

-   `wait(ms?: number): Promise<UINode>`:
    等待指定毫秒数，然后**重新获取**整个页面的 UI 结构并返回一个新的根 `UINode`。这对于等待动画或网络加载完成非常有用。

-   `clickAndWait(ms?: number): Promise<UINode>`:
    一个组合操作：先 `click()` 当前节点，然后执行 `wait()`。这是处理点击后页面跳转或内容更新的**黄金标准**。

#### 静态方法

-   `UINode.getCurrentPage(): Promise<UINode>`:
    静态方法，直接获取当前页面的根 `UINode`。相当于 `Tools.UI.getPageInfo()` 和 `UINode.fromPageInfo()` 的结合。

-   `UINode.fromPageInfo(pageInfo: UIPageResultData): UINode`:
    将 `getPageInfo` 返回的原始数据转换为 `UINode` 对象。

**示例: 查找并点击一个按钮**
```typescript
async function findAndClick() {
    try {
        const page = await UINode.getCurrentPage();
        
        // 优先使用 ID 查找
        let loginButton = page.findById("com.example.app:id/login_button");

        // 如果 ID 找不到，尝试通过文本查找
        if (!loginButton) {
            loginButton = page.findByText("登录");
        }

        if (loginButton) {
            // 点击按钮，并等待 2 秒让新页面加载
            const newPage = await loginButton.clickAndWait(2000);
            
            // 现在 newPage 是点击操作之后的新页面 UINode
            console.log("成功点击并跳转到新页面。");
            complete({ success: true, message: "操作成功" });
        } else {
            complete({ success: false, message: "未找到登录按钮。" });
        }

    } catch (error) {
        complete({ success: false, message: `UI 操作失败: ${error.message}` });
    }
}
```
