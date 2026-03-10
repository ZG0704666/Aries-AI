# Compose 迁移进度

最后更新：2026-03-10（本轮已同步）  
当前分支：`codex/refactor-md3-modular`

## 进度口径

- 可见界面迁移进度：`约 69%`
- 底层架构彻底迁移进度：`约 48%`

说明：
- “可见界面迁移进度”指用户实际看到的页面是否已经切到 Compose Material 3。
- “底层架构彻底迁移进度”指是否已经不再依赖旧的 XML / View 兼容逻辑。

## 已完成

- 已建立共享 Compose Material 3 主题基础设施。
- 设置页已迁移到 Compose。
- 关于页已迁移到 Compose。
- 自动化控制面板的可见界面已迁移到 Compose。
- 主界面顶栏已迁移到 Compose。
- 主界面侧边栏已迁移到 Compose。
- 主界面输入区已迁移到 Compose。
- 主界面消息列表的可见层已迁移到 Compose。

## 进行中

### 1. 自动化控制面板

- 当前状态：可见 UI 已是 Compose。
- 仍存在问题：底层业务逻辑仍通过隐藏 legacy View 做桥接。
- 下一步：把运行时控制、状态同步、日志展示彻底改成纯 Compose。

### 2. 主界面消息区

- 当前状态：可见 transcript 已切到 Compose。
- 本轮新增：
  - Compose transcript 已接入复制操作。
  - Compose transcript 已接入重试操作。
  - 主界面消息可见层不再只是静态展示，已经开始承接消息交互。
- 本轮继续新增：
  - 生成中的 AI 消息已接入 Compose 可见层。
  - Compose transcript 现在可以实时显示思考内容和回答增量。
  - 流式消息状态已经按会话隔离，切换会话时不会把生成中的内容串到别的会话里。
- 本轮再次新增：
  - 自动化消息卡的可见形态已切到 Compose Material 3 卡片。
  - 自动化消息卡已在 Compose transcript 中接入状态展示、执行日志、确认/终止按钮。
  - 自动批准倒计时和终止请求中的状态已同步到 Compose transcript。
- 仍存在问题：
  - 底层流式生成过程仍依赖旧渲染链做兼容。
  - 自动化卡片交互仍有 legacy 兼容逻辑。
  - Compose 侧的复制/重试已经接入，但底层仍通过旧消息存储和旧发送链驱动。
  - `MainActivity.kt` 仍是 View + Compose 混合状态管理。
- 下一步：把消息流、交互按钮、自动化消息卡全部改成纯 Compose。

## 未完成

- 主界面流式消息渲染迁移为纯 Compose。
- 主界面 AI 消息操作区迁移为纯 Compose。
- 主界面自动化消息卡交互迁移为纯 Compose。
- 移除 `MainActivity` 中隐藏的 legacy 消息容器依赖。
- 移除 `AutomationActivityNew` 中隐藏的 legacy 容器依赖。
- 将更多页面状态迁移到 Compose-first 状态模型，而不是继续由 View 驱动。
- 继续拆分超长页面逻辑，减少 `MainActivity.kt` 和 `AutomationActivityNew.kt` 的职责。

## 当前重点

### 主界面

- 目标：让聊天主链路完全脱离旧 XML 消息渲染。
- 判断标准：
  - 新消息生成时不再依赖 `appendComplexAiMessage`
  - 重试、复制、自动化确认都走 Compose
  - 旧 `messagesContainer` 可删除

### 自动化页

- 目标：让自动化控制台完全脱离隐藏 legacy View。
- 判断标准：
  - 状态刷新不再读写隐藏控件
  - 控制按钮直接驱动 Compose 状态
  - 旧 `activity_automation.xml` 只保留 Compose 宿主或可继续收缩

## 实时更新规则

- 每次推进 Compose 迁移后，必须同步更新本文件。
- 进度更新至少包括：
  - 本轮完成了什么
  - 当前整体进度变化
  - 下一步要做什么
- 如果某一轮只是修复回归问题，也要在这里反映，不允许只改代码不更新进度。

## 下一阶段目标

- 继续清理主界面消息流底层 legacy 渲染依赖。
- 继续完成主界面消息操作区底层 Compose 化。
- 完成自动化控制台去 legacy bridge。

## 本轮完成

- 主界面消息 transcript 已支持 Compose 侧复制。
- 主界面消息 transcript 已支持 Compose 侧重试。
- 主界面生成中的 AI 消息已迁到 Compose 可见层实时展示。
- 主界面自动化消息卡已迁到 Compose 可见层，并接入确认/终止按钮。
- 自动化倒计时与终止中的状态已开始从 legacy 运行时同步到 Compose transcript。
- 中文进度文件已切换为强制实时更新模式。

完成以上几项后，主界面可见消息区已基本完成 Compose 化，接下来重点转向去除隐藏 legacy 容器和状态桥接。
