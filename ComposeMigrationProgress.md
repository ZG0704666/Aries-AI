# Compose 迁移进度

最后更新：2026-03-10（本轮已同步）  
当前分支：`codex/refactor-md3-modular`

## 进度口径

- 可见界面迁移进度：`约 95%`
- 底层架构彻底迁移进度：`约 82%`

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
- 本轮继续新增：
  - 用户消息已不再写入 hidden legacy `messagesContainer`。
  - 会话切换时的历史用户消息也不再重建 legacy 用户气泡。
  - 旧的用户消息复杂气泡渲染方法已从 `MainActivity` 主路径移除。
- 本轮大幅推进：
  - 主界面流式 AI 消息已不再依赖 hidden `item_ai_message_complex` 和 `StreamRenderHelper` 作为临时渲染宿主。
  - 主界面发送链的思考增量、回答增量、最终持久化已改成直接由 Compose transcript 状态驱动。
  - 主界面 legacy AI 气泡渲染、旧打字机消息函数、旧“正在思考”占位函数已从 `MainActivity` 清出主工程代码。
- 本轮继续大幅推进：
  - 自动化控制面板的任务输入、日志文本、虚拟屏状态文案、推荐任务文案已切到 Compose state 直驱。
  - 自动化控制面板的复制日志动作不再依赖 hidden `TextView` 长按回调。
  - 自动化控制面板的开始/暂停/停止按钮状态已开始由 Compose 侧派生，不再完全反向读取 hidden legacy 按钮。
- 仍存在问题：
  - 自动化运行时状态仍有少量 legacy View 引用字段保留在 `MainActivity.kt` 中。
  - 自动化控制台页面本身仍保留一层 hidden legacy bridge 用于部分权限控件与动画兼容。
  - Compose 侧的复制/重试已经接入，但底层仍通过旧消息存储和旧发送链驱动。
  - `MainActivity.kt` 与 `AutomationActivityNew.kt` 仍是 View + Compose 混合状态管理。
- 下一步：把消息流、交互按钮、自动化消息卡全部改成纯 Compose。

## 未完成

- 继续清除 `MainActivity` 中剩余的自动化 legacy 运行时引用。
- 移除 `AutomationActivityNew` 中剩余的 hidden legacy 控件依赖。
- 将更多页面状态迁移到 Compose-first 状态模型，而不是继续由 View 驱动。
- 继续拆分超长页面逻辑，减少 `MainActivity.kt` 和 `AutomationActivityNew.kt` 的职责。

## 当前重点

### 主界面

- 目标：让聊天主链路完全脱离旧 XML 消息渲染。
- 判断标准：
  - 新消息生成时不再依赖 legacy 消息视图宿主
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

- 继续清理主界面剩余自动化 runtime 的 legacy 引用。
- 继续收 `AutomationActivityNew` 剩余的 hidden legacy bridge。
- 完成自动化控制台去 legacy bridge。

## 本轮完成

- 主界面消息 transcript 已支持 Compose 侧复制。
- 主界面消息 transcript 已支持 Compose 侧重试。
- 主界面生成中的 AI 消息已迁到 Compose 可见层实时展示。
- 主界面自动化消息卡已迁到 Compose 可见层，并接入确认/终止按钮。
- 自动化倒计时与终止中的状态已开始从 legacy 运行时同步到 Compose transcript。
- 主界面用户消息已经完全改由 Compose transcript 可见层承接，不再依赖 hidden legacy 用户气泡。
- 主界面流式 AI 消息已经完全改由 Compose transcript 状态承接，不再依赖 hidden legacy AI 渲染宿主。
- `MainActivity.kt` 中旧 AI 气泡渲染、旧打字机消息、旧“正在思考”占位代码已大规模删除。
- 自动化控制面板的任务输入、日志、虚拟屏状态、推荐任务文案已改为 Compose state 主导。
- 自动化控制面板的日志复制和按钮状态开始脱离 hidden legacy 控件反向取值。
- 中文进度文件已切换为强制实时更新模式。

完成以上几项后，主界面和自动化页的可见主链都已接近 Compose 化完成，接下来重点转向彻底删除剩余 hidden bridge。
