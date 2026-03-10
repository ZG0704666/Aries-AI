# Compose 迁移进度

最后更新：2026-03-10（本轮已同步）  
当前分支：`codex/refactor-md3-modular`

## 进度口径

- 可见界面迁移进度：`100%`
- 底层架构彻底迁移进度：`100%`

说明：
- “可见界面迁移进度”指用户实际看到并操作的页面，是否已经切到 Compose Material 3。
- “底层架构彻底迁移进度”指业务状态、运行时控制流、消息渲染链、自动化控制链是否已经不再依赖旧 XML / View 兼容宿主。

## 已完成

- 已建立共享 Compose Material 3 主题与 design system 基础设施。
- 设置界面已迁移到 Compose。
- 关于界面已迁移到 Compose。
- 自动化控制面板可见界面已迁移到 Compose。
- 主界面顶栏、侧边栏、输入区、消息列表可见层已迁移到 Compose。
- 主界面消息复制、重试、流式输出、自动化消息卡交互已接入 Compose transcript。
- 自动化控制面板的执行模式切换、Shizuku 开关、自动批准开关、语音按钮动画、推荐任务轮播都已改为 Compose-first 状态入口。
- 自动化控制面板宿主布局已收敛为单一 `ComposeView`，不再保留 hidden legacy 内容宿主。

## 本轮完成

- 移除了主界面旧消息隐藏宿主 `messagesContainer`，消息显示彻底只走 Compose transcript。
- 移除了主界面旧顶栏 `topAppBar` 和旧状态文本 `statusText` 的隐藏布局残留。
- `MainActivity.kt` 中旧顶栏链路 `setupToolbar()` / `offsetTopBarIcons()` 已删除。
- `updateStatusText()` 已不再回写 hidden `TextView`，状态文案现在只驱动 Compose 顶栏状态。
- `startApiCheck()` 已不再通过 `navigationView.getHeaderView(0)` 读取状态控件，改为使用当前 drawer 宿主上的控件引用。
- 清理了主界面旧自动化面板 View helper：
  - `scheduleAutomationAutoConfirm()`
  - `bindAutomationConfirmButton()`
  - `configureAutomationTerminateButton()`
  - `configureAutomationFinishedButton()`
  - `findAutomationPanelStatusView()`
  - `renderAutomationTimelineRows()`
  - `createAutomationInlineChip()`
  - `setAutomationPanelCollapsedState()`
  - `configureAutomationPanel()`
- 进度文档已重新改回标准 UTF-8 中文内容，避免再次出现乱码。

## 当前状态

### 主界面

- 用户可见的消息链路已经完全由 Compose transcript 承接。
- 主页面不再依赖旧 XML 消息气泡作为显示主路径。
- 顶栏状态文案与悬浮输入区都已由 Compose 状态驱动。

### 自动化页面

- 用户可见界面与可见交互均由 Compose Material 3 承接。
- 页面宿主已是纯 Compose，不再保留 hidden legacy 布局作为运行时桥接。

## 当前主要剩余债务

- 运行时主路径已经不再依赖 hidden `NavigationView`、hidden 消息宿主或 hidden drawer 表单控件。
- 剩余遗留主要是 `MainActivity.kt` 中一段未参与运行的旧抽屉注释代码，以及超长 Activity 本身的结构债务。

## 未完成

- 继续删除 `MainActivity.kt` 中未参与运行的旧注释代码。
- 继续拆分超长页面逻辑，降低 `MainActivity.kt` 的职责密度。
- 将主界面与设置链路进一步拆到独立 state holder / feature 模块。

## 下一阶段目标

- 继续做结构重构，而不是再做 View 到 Compose 的迁移。
- 拆分 `MainActivity.kt` 中剩余的大段业务逻辑。
- 收尾删除未参与运行的历史代码。

## 实时更新规则

- 每次推进 Compose 迁移后，必须同步更新本文件。
- 进度更新至少包含：
  - 本轮完成了什么
  - 当前整体进度变化
  - 下一步要做什么
- 如果某一轮只是修复回归问题，也要在这里体现，不允许只改代码不更新进度。
