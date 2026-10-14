# 主对话界面 (HomeScreen)

`HomeScreen` 是主界面的 Compose 布局容器。当前使用三页 `HorizontalPager`，默认显示主页；业务状态与事件由 `MainActivity` 传入。

## 页面结构

| 页码 | 页面 | 内容 |
| --- | --- | --- |
| 0 | 历史 | `ConversationDrawer`：搜索、会话列表、选择与长按操作 |
| 1 | 主页 | 对话转录区、AI 提示和输入栏 |
| 2 | 设置 | `settingsContent` 插槽，由调用方嵌入 `SettingsRoute` |

顶部导航按钮与横向手势共同控制 Pager。`selectedPage` 接收外部选择，`onPageSelected` 上报当前页；导航动画启动前会取消尚未完成的导航任务。

`ConversationDrawer` 名称沿用旧实现，但它现在是历史页的内容，不是主布局中的模态抽屉。旧 `drawerState` 回调仍作为兼容入口：请求打开抽屉会切换到历史页，再关闭抽屉状态。

## 历史和设置导航

- 选择历史会话后，调用会话选择回调并回到主页。
- 历史页关闭自身的设置入口，统一使用顶部设置导航。
- 设置子页中隐藏顶部主导航，并禁止 Pager 横向滑动。
- `homeBackAction` 决定是否由主容器接管返回事件；返回历史页或设置根页时可回到主页，设置子页的返回交给设置导航处理。

`HorizontalPagerScrollConnection` 协调横向分页的嵌套滚动。手势开关同时取决于 `drawerGesturesEnabled` 与 `settingsAtRoot`。

## 对话与输入区域

主页使用纵向布局：上方是可伸缩的 `transcriptPaneContent`，下方是 `HomeBottomOverlay`。输入区使用导航栏和 IME inset，消息区域裁剪在可用空间内。

`HomeTranscriptPane` 负责将消息列表、流式消息、列表状态及交互回调交给对话转录组件。`HomeBottomOverlay` 放置 AI 提示与调用方提供的输入栏；名称中的 Overlay 不表示输入栏覆盖在消息列表上。

## 流式内容与滚动跟随

自动跟随以会话 `listKey` 为边界：

1. 切换会话时开启自动跟随，并滚动到底部。
2. 用户滚动时，根据是否处于底部更新跟随状态；上翻阅读历史内容会暂停自动跟随。
3. 收到显式 `scrollToBottomSignal` 时重新开启跟随并滚动到底部。
4. 流式正文或思考内容的布局版本变化后，仅在允许自动跟随且用户没有正在滚动时继续滚动。

这使长回复能够持续展示新增内容，同时保留用户阅读历史消息时的位置。

## 覆盖层和样式

`onboardingContent` 与 `historyDialogContent` 在主布局之后绘制，分别承载引导流程与历史操作对话框。它们由调用方提供，主容器不负责对应业务持久化。

页面尺寸、动画时长和颜色使用设计系统资源与 `MaterialTheme`。顶部导航使用 `ariesGlass`，标签来自字符串资源，并提供 Tab 角色与选中状态语义。

## 与任务页的关系

任务详情和结果由外层导航图管理，不是 Pager 的第四、第五页。对应路由为 `task/{runId}` 与 `task/{runId}/result`，参见[导航与路由系统](../技术架构/导航与路由系统.md)。

## 代码入口

- [HomeScreen.kt](../../../app/src/main/java/com/ai/phoneagent/ui/home/HomeScreen.kt)
- [MainActivity.kt](../../../app/src/main/java/com/ai/phoneagent/MainActivity.kt)
- [AriesNavGraph.kt](../../../app/src/main/java/com/ai/phoneagent/navigation/AriesNavGraph.kt)
- [SettingsRoute.kt](../../../app/src/main/java/com/ai/phoneagent/ui/settings/SettingsRoute.kt)

本页描述本地当前代码；远程 Wiki 索引及旧版本截图可能仍展示抽屉式布局。
