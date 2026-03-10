# Compose 迁移进度

最后更新：2026-03-10（本轮已同步）
当前分支：`codex/refactor-md3-modular`

## 进度口径

- 可见界面迁移进度：`100%`
- 底层架构彻底迁移进度：`约 91%`

说明：
- “可见界面迁移进度”指用户实际看到和操作到的页面，是否已经切到 Compose Material 3。
- “底层架构彻底迁移进度”指业务状态、运行时控制流、数据同步是否已经不再依赖旧 XML / View 兼容桥接。

## 已完成

- 已建立共享 Compose Material 3 主题与 design system 基础设施。
- 设置页已迁移到 Compose。
- 关于页已迁移到 Compose。
- 自动化控制面板的可见 UI 已迁移到 Compose。
- 主界面顶栏已迁移到 Compose。
- 主界面侧边栏已迁移到 Compose。
- 主界面输入区已迁移到 Compose。
- 主界面消息列表的可见层已迁移到 Compose。
- 主界面消息复制、重试、流式输出、自动化卡片交互已接入 Compose transcript。
- 自动化控制面板的执行模式切换、Shizuku 开关、自动批准开关已改为 Compose-first 状态入口。
- 自动化控制面板的语音输入按钮动画已改为 Compose 可见层驱动，不再依赖 hidden legacy View 动画。
- 自动化控制面板的推荐任务轮播文本已改为 Compose 状态驱动。
- 自动化控制面板的日志复制反馈已脱离 hidden `TextView` 动画。
- 自动化控制面板的宿主布局已收敛为单一 `ComposeView`，hidden legacy 布局已不再参与页面运行。

## 本轮完成

- 把自动化控制面板里最后一段“Compose 交互先写 hidden 控件，再由 listener 改状态”的链路切掉了。
- `onExecutionModeChange`、`onShizukuModeChange`、`onAutoApproveChange` 已改成直接写业务状态，再按需镜像到 hidden 控件。
- 删除了自动化页遗留的 hidden 推荐任务点击入口和 hidden 语音按钮点击入口。
- 删除了自动化页的 hidden 麦克风脉冲动画实现，改成 Compose 可见按钮动画。
- 删除了自动化页已经失效的 `syncComposeStateFromLegacyViews()` / `setupLogCopy()` / `playLogCopyAnim()` 路径。
- 删除了自动化页整块 hidden `legacyAutomationContent` 宿主，`activity_automation.xml` 现在只保留 Compose 宿主。
- 自动化页的日志、任务输入、模式说明、虚拟屏状态、按钮状态已不再镜像回 hidden View。
- 将进度口径正式推进到“可见界面迁移 100%”。

## 当前状态

### 主界面

- 用户可见消息链路已由 Compose transcript 承接。
- 主页面可见结构已不再依赖旧 XML 消息气泡作为显示主路径。
- 仍保留少量 hidden legacy runtime 引用，用于兼容部分历史自动化和消息数据流。

### 自动化页

- 用户可见界面与可见交互已全部由 Compose Material 3 承接。
- 页面宿主已经是纯 Compose，不再保留 hidden legacy 布局作为运行时桥接。

### 当前主要剩余债

- `MainActivity.kt` 仍保留一条较长的 legacy 运行时链：
  - hidden `topAppBar` / `NavigationView`
  - hidden `messagesContainer`
  - 自动化面板 runtime 引用与部分旧消息同步逻辑
- 这些部分已经不在可见主路径上，但仍影响“底层彻底迁移”口径。

## 未完成

- 继续清理 `MainActivity.kt` 中剩余的 hidden legacy runtime 引用。
- 继续清理 `AutomationActivityNew.kt` 中剩余的 hidden legacy 字段镜像。
- 把更多状态收口到 Compose-first 状态模型，进一步降低 View/Compose 混合管理。
- 继续拆分超长页面逻辑，减少 `MainActivity.kt` 与 `AutomationActivityNew.kt` 的职责。

## 下一阶段目标

- 优先收掉主界面中只用于兼容旧运行时的遗留宿主。
- 把“可见界面已完成迁移”推进到“底层架构也基本完成迁移”。

## 实时更新规则

- 每次推进 Compose 迁移后，必须同步更新本文件。
- 进度更新至少包括：
  - 本轮完成了什么
  - 当前整体进度变化
  - 下一步要做什么
- 如果某一轮只是修复回归问题，也要在这里体现，不允许只改代码不更新进度。
