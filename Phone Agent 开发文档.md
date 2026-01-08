# Phone Agent 开发文档

> 最后更新：2026-01-08

## 0. 阅读指南（给 AI / 新同学）

- **文档目的**：让后来者（包括其他 AI）能快速理解 Phone Agent 当前实现到哪里、有哪些关键决策未定、以及遇到的问题是怎么定位/修复的。
- **阅读顺序建议**：先看「最新状态」→「代码入口」→「关键决策」→「路线图」→「开发构想」→「问题记录」→「更新记录」→「知识库」。
- **自动化对齐路线（Operit 方法论）**：见 [自动化升级计划.md](./自动化升级计划.md)

## 目录

- [1. 最新状态（2026-01-08）](#1-最新状态2026-01-08)
- [自动化升级计划（按 Operit 方法论全面对齐｜仅无障碍）](./自动化升级计划.md)
- [2. 关键代码入口与数据流索引](#2-关键代码入口与数据流索引)
- [3. 关键决策（待确认）](#3-关键决策待确认)
- [4. 近期路线图（对齐 TODO）](#4-近期路线图对齐-todo)
- [5. 开发构想（DeepSeek 风格手机助手）](#5-开发构想deepseek-风格手机助手)
- [6. 问题记录（定位 → 修复）](#6-问题记录定位--修复)
- [7. 更新记录（Changelog）](#7-更新记录changelog)
- [8. 知识库（Reference）](#8-知识库reference)
- [9. 历史任务进度（旧）](#9-历史任务进度旧)
- [10. 大文件/LFS 与协作提醒](#10-大文件lfs-与协作提醒)

## 1. 最新状态（2026-01-08）

### 小窗关闭防闪优化 + 进度百分比精准 + 主页对话&操作区修复（2026-01-08）

- **小窗关闭防闪（已修复）**：
  - 优化 `FloatingChatService.animateDismissAndMaybeOpenApp()` 逻辑
  - 点击返回按钮后立即降低透明度（0.3f）并设为不可触摸
  - 使用更快的淡出动画（100ms，AccelerateInterpolator）
  - 动画完成后再延迟 30ms 拉起主界面，避免同时操作导致闪烁
  
- **进度百分比精准显示（已实现）**：
  - `AutomationOverlay` 新增 `estimatedTotalSteps` 和 `hasEstimatedSteps` 状态
  - `updateEstimatedSteps()` 只在第一次解析出有效预估值时设置，后续不再覆盖
  - `calculateProgressPercent()` 智能计算百分比：
    - 有预估步骤数时：使用 `step/estimated` 计算真实百分比
    - 无预估时：使用平滑的假进度曲线（快速上升到 60%，然后缓慢增长到 90%）
  - `updateStep()` 不再用外部 `maxSteps` 参数覆盖预估值
  
- **主页模型对话优化（已实现）**：
  - **修复 AI 无法看到上下文**：新增 `buildChatHistory()` 方法，构建完整对话历史（系统提示 + 最近 10 轮对话）传递给模型
  - **批量更新模型输出**：新增 `appendMessageBatch()` 方法，使用 StringBuilder 每 5 个字符批量刷新一次 UI，根据标点符号调整延迟，显示更自然流畅
  - **气泡操作区可见性修复**：重新进入 App 时，遍历消息气泡强制显示底部“复制/重试”操作区，防止动画/复用导致按钮消失。
  - **输入栏首次点击即可输入**：为输入框添加触摸聚焦并主动唤起软键盘的逻辑，解决需点两次才能输入的问题。

### 自动化执行体验优化（2026-01-07）

- **悬浮窗可视性**：尺寸由 100dp 调整为 115dp；标题/副标题支持 2 行显示，长文自动省略，显示更完整。
- **进度百分比更精准**：从模型思考中的计划列表（“我需要：1. 2. 3. …”/“需要 N 步”）解析总步数，按实际步数计算百分比。
- **避免误触悬浮窗**：执行点击/长按/双击/滑动前临时隐藏悬浮窗，操作完成后恢复，避免点击到悬浮窗导致返回自动化界面。
- **输入更稳**：Type 动作若无焦点会先点击输入框；若仍失败会自动查找可编辑输入框再输入。
- **模型输出容错**：解析器增强对截断/乱码/缺少括号的容忍，并用更直接的修复提示强制模型只输出动作格式。

### 应用启动与敏感检测优化（2026-01-05）

- **应用直启提速**：新增 `AppPackageManager`（缓存 5 分钟、模糊匹配），`launch_app` 直接查缓存并启动，无需再让模型“思考”；新增 `get_installed_apps` 供模型/用户列出已装应用，启动耗时从 5-10s 降到 ~1s。
- **系统提示词同步**：`AutomationActivityNew` 更新系统提示，明确允许直接使用应用名/包名启动，并提示购物/支付/金融/日常操作均为“允许”类别，避免模型过度谨慎。
- **进度与步数**：自动化默认 `maxSteps=100`（不再 12 步封顶），`maxModelRetries=3`，进度 Overlay 显示百分比（去除 “1/12” 样式），任务页/Overlay 文案同步；涉及 `UiAutomationAgent`、`AutomationActivity`/`AutomationActivityNew`、`AutomationOverlay`、`ui/UIAutomationProgressOverlay.kt`。
- **敏感检测放宽但保留底线**：新增 `ContentFilter` 对模型回复做“去警告”处理，仅保留真实错误；`UiAutomationAgent` 的敏感检测收窄为支付密码/CVV/验证码/确认支付等高风险词，购物/支付流程不再被误拦。危险操作（删除系统文件、未授权访问等）仍需拦截并提示。

### 兼容性与测试矩阵（必须遵守）

- **目标 Android 版本范围**：Android 11 - Android 16（即 API 30 - API 36）。
  - 说明：本项目 `minSdk=30`，要求从 Android 11 起可用；同时需要在新系统（目前真机/环境为 Android 16）上保持行为一致。
- **小窗/悬浮窗相关回归必测**：Android 11/13/14/16 至少各覆盖 1 台/1 个模拟器（重点关注 Android 14+ 对后台启动 Activity 的限制）。

### 小窗返回主界面（Android 11-16 最终方案）

- **目标体验**：
  - 小窗 **Home/放大**：关闭小窗 + 可靠回到 `MainActivity`。
  - 小窗 **X**：仅关闭小窗，不回到 App。
- **最终技术方案（优先级从高到低）**：
  - **A. 任务栈前置（最稳，优先使用）**：`ActivityManager.appTasks` 中找到包含 `MainActivity` 的任务，`moveToFront()` 后 `startActivity(..., MainActivityIntent)`，保证回到同一任务栈。
  - **B. 前台跳板 + PendingIntent（对齐 Android 14+）**：通过 `PendingIntent -> LaunchProxyActivity -> MainActivity` 拉起；Android 14+ 使用 `ActivityOptions.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)` 显式 opt-in。
  - **C. 兜底**：若上述路径异常，再直接 `startActivity(...)`（仍可能被系统限制，但作为最后兜底）。
- **原因说明**：
  - Android 14+（targetSdk 34+）对“后台启动 Activity”限制更严格，Overlay 场景在不同 ROM/系统版本上可能出现“点击后只关闭小窗但 Activity 未被拉起”的不确定性；因此需要任务栈前置 + PendingIntent opt-in 的双保险，行为更接近 Operit 等成熟悬浮窗应用。

- **稳定性与防闪关键点（已落地）**：
  - **ACK 机制**：主界面在检测到从小窗返回（`intent.getBooleanExtra("from_floating", false)`）后，发送广播 `FloatingChatService.ACTION_FLOATING_RETURNED` 作为“已回前台”确认。
    - `MainActivity` 同时在 `onResume()` 与 `onNewIntent()` 触发该处理，避免任务栈复用（`onNewIntent`）时 ACK 发送过晚导致服务误判失败。
  - **服务停机时机**：`FloatingChatService` 在 Home/放大后不会立刻 `stopSelf()`，而是等待 ACK；收到 ACK 才停止服务，确保前台服务通知不被过早移除。
  - **失败重试与超时**（当前实现参数）：
    - 等待 ACK 超时：`6000ms`
    - 期间重试拉起主界面：`900ms` / `1700ms` / `2600ms`
    - 拉起节流：`700ms`
  - **视觉策略（减少“闪一下”）**：点击 Home/放大后，小窗先做收起淡出动画，并设置短 `startDelay`（`120ms`）让系统有时间切到主界面；动画结束后将 View 设为 `GONE`（不立即移除 Window），并在返回期间设置 `FLAG_NOT_TOUCHABLE`，避免透明 overlay 挡触摸；最终由 ACK 驱动服务结束并在 `onDestroy()` 统一移除 overlay。

### 已完成

- **主对话页 Drawer 手势与小窗聊天同步（2026-01-02）**
  - **侧边栏右滑打开（主页）**：在主界面任意位置右滑，超过阈值后打开 Drawer（使用 `DrawerLayout.openDrawer(..., true)` 的系统动画）。
    - 说明：当前实现为“达到阈值后触发打开”，非全程跟手拖拽；后续若要“跟手”，需要改造为将手势交给 `DrawerLayout` 的 dragger 或引入自定义容器（风险更高）。
  - **小窗模式入口收敛**：暂时**禁止右滑进入小窗**，进入小窗的唯一入口为点击顶部菜单按钮（`action_floating_window` → `enterMiniWindowMode()`）。
  - **主界面 ↔ 小窗消息双向同步**：
    - 悬浮窗服务侧（`FloatingChatService`）新增待同步队列；当主界面监听器未挂载时暂存消息，主界面恢复时一次性补发。
    - 主界面侧（`MainActivity`）在 `onResume()` 重新挂载监听器，在 `onDestroy()` 清理监听器避免泄漏。
  - **修复消息偶发“消失”**：统一/兼容消息前缀解析，支持 `"我:" / "我: "` 与 `"AI:" / "AI: "` 两种格式，避免因前缀空格差异导致 UI/历史恢复漏渲染。
  - 涉及文件：
    - `app/src/main/java/com/ai/phoneagent/MainActivity.kt`
    - `app/src/main/java/com/ai/phoneagent/FloatingChatService.kt`

- **小窗返回主界面稳定性与防闪（2026-01-03）**
  - `MainActivity`：在 `onResume()` / `onNewIntent()` 第一时间处理 `from_floating`，并发送 ACK 广播。
  - `FloatingChatService`：等待 ACK 后再 `stopSelf()`；超时恢复小窗；期间有限次重试拉起主界面。
  - 小窗回收动画：`startDelay=120ms`，动画结束后 View 设为 `GONE`（不立即移除 Window），并用 `FLAG_NOT_TOUCHABLE` 避免挡触摸，减少返回主界面时的视觉闪烁。

- **语音识别体验优化（2026-01-02）**
  - **完全移除 Vosk**：删除旧的 Vosk 模型文件目录（`assets/model/`）和相关依赖，清理代码。
  - **按钮震动反馈**：点击语音按钮和发送按钮时触发轻微震动（30ms），提升交互体验。
  - **语音输入动画**：按下语音按钮后，输入框显示"正在语音输入."并循环显示 1~3 个点的动画，有识别结果后切换为实际文字。
  - 涉及文件：
    - `app/src/main/java/com/ai/phoneagent/MainActivity.kt` - 添加震动反馈和动画逻辑
    - `app/build.gradle.kts` - 清理 Vosk 注释
    - 删除 `app/src/main/assets/model/` 目录

- **语音识别引擎更换：Vosk → Sherpa-ncnn（2026-01-01）**
  - 移除 Vosk 依赖，改用 Sherpa-ncnn（k2-fsa 开源项目）作为本地语音识别引擎。
  - Sherpa-ncnn 相比 Vosk 更轻量、性能更好，更适合移动端实时流式识别。
  - 支持中英文双语混合识别。
  - 涉及文件：
    - `app/src/main/java/com/k2fsa/sherpa/ncnn/SherpaNcnn.kt` - Sherpa JNI 封装
    - `app/src/main/java/com/ai/phoneagent/speech/SherpaSpeechRecognizer.kt` - 语音识别器
    - `app/src/main/java/com/ai/phoneagent/MainActivity.kt` - 集成更新
    - `app/build.gradle.kts` - 依赖更新
  - 资源部署说明：
    - JNI 原生库：`libsherpa-ncnn-jni.so` 放入 `app/src/main/jniLibs/arm64-v8a/` 和 `armeabi-v7a/`
    - 模型文件：`sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13` 放入 `app/src/main/assets/sherpa-models/`
    - 详见 `app/src/main/jniLibs/README.md` 和 `app/src/main/assets/sherpa-models/README.md`

- **统一权限引导（首次进入弹底部面板）**
  - 首次进入 App 弹出底部权限面板：无障碍 / 悬浮窗 / 录音。
  - 自动化开始前仅做快速检查；执行中不再弹权限引导/不强制跳转系统设置。
  - 涉及文件：
    - `app/src/main/java/com/ai/phoneagent/PermissionBottomSheet.kt`
    - `app/src/main/java/com/ai/phoneagent/MainActivity.kt`

- **新版自动化系统对齐 Operit（2026-01-05）**
  - **核心架构对齐**：
    - ✅ 完整的 Agent 执行循环（截图 → AI 思考 → 动作解析 → 执行）
    - ✅ 工具注册系统（12+ 个基础工具）
    - ✅ 动作解析与自修复
    - ✅ 错误重试机制（maxModelRetries 提高到 3）
    - ✅ 上下文管理（智能裁剪）
    - ✅ 暂停/继续/取消支持
    - ✅ 进度 Overlay 显示（支持百分比与假进度）
  - **配置优化**：
    - `maxSteps` 提高至 100（实际由任务决定，不再硬限制）
    - `maxModelRetries` 提高至 3（应对网络不稳定）
    - 进度条百分比计算：已知总步数时精确，未知时按指数曲线显示 0-90% 假进度
  - **API 加速与可靠性**：
    - 🚀 **当前 API 端点**：`https://open.bigmodel.cn/api/paas/v4/`（官方入口）
    - 🌐 **备用 CDN 加速方案（可配置）**：
      - **方案A**：使用代理加速（如 Cloudflare Workers、Vercel 等）
      - **方案B**：自建反向代理（可将官方 API 映射到自有域名/IP）
      - **方案C**：检查网络运营商（某些运营商可能对国际 API 有限制，可考虑 VPN/专线）
    - 📝 **修改方法**（`AutoGlmClient.kt` 第 22 行）：
      ```kotlin
      private const val BASE_URL = "https://your-accelerated-endpoint.com/api/paas/v4/"
      ```
    - ⚠️ **重试机制**：内置指数退避重试（基础延迟 700ms，最多 3 次），自动应对临时网络波动
  - **涉及文件**：
    - `core/agent/PhoneAgent.kt` - Agent 核心（470 行）
    - `core/agent/AgentModels.kt` - 配置与数据模型
    - `core/agent/ActionHandler.kt` - 动作执行（110 行）
    - `core/tools/ToolRegistration.kt` - 工具注册（12 个工具）
    - `core/tools/AIToolHandler.kt` - 工具管理器
    - `ui/UIAutomationProgressOverlay.kt` - 进度 UI（支持百分比）
    - `AutomationActivityNew.kt` - 集成示例（380 行）
    - `app/src/main/java/com/ai/phoneagent/AutomationActivity.kt`

- **自动化页 UI 可用性修复**
  - 任务输入框改为更易读的白底黑字。
  - 修复 IME（软键盘）遮挡问题：`adjustResize + WindowInsets` 底部 padding。
  - 自动化执行按钮在键盘弹出时可见。

- **执行中悬浮窗（AutomationOverlay）第一版优化**
  - 悬浮窗更小：`168dp -> 148dp`。
  - 白底圆角卡片风格 + 阴影。
  - 支持拖动（类似微信语音通话悬浮窗）。
  - 点击可返回 `AutomationActivity`。
  - 文案优化：副标题自动去掉 `[Step x]` 前缀，避免重复显示 step。
  - 资源释放：Overlay 移除时取消环形动画，避免后台空转。
  - 涉及文件：`app/src/main/java/com/ai/phoneagent/AutomationOverlay.kt`

- **构建修复：移除 ktlint 插件**
  - 现象：Gradle 无法解析 `org.jlleitschuh.gradle.ktlint` 插件导致同步/构建失败。
  - 处理：移除顶层与 app 模块对 ktlint 插件的引用与配置，恢复可构建。
  - 涉及文件：
    - `build.gradle.kts`
    - `app/build.gradle.kts`
    - `gradle/libs.versions.toml`

- **自动化闭环可靠性增强（对齐 Open-AutoGLM/Operit）**
  - 模型调用统一使用 `AutoGlmClient.sendChatResult(...)`，并增加有限次重试 + 指数退避（429/5xx/IO）。
  - 解析失败修复循环：要求模型严格输出 `do(...) / finish(...)`，最多 N 次修复。
  - 动作执行失败修复：把失败信息回传模型生成新动作，有限次重试。
  - 上下文裁剪：记录 `observationUserIndex`，确保每步截图剥离对准当前观测消息。
  - 自检：`:app:compileDebugKotlin` 通过。
  - 涉及文件：
    - `app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`
    - `app/src/main/java/com/ai/phoneagent/net/AutoGlmClient.kt`
    - `app/src/main/java/com/ai/phoneagent/net/ChatModels.kt`

### 待确认（需要 1 句话）

- **点击悬浮窗后应用以“小窗模式”打开：选择 A / B**
  - **方案 A**：点击后正常全屏打开（最稳、所有机型一致）。
  - **方案 B**：尽量用 `ActivityOptions.setLaunchBounds` 让 Activity 以小矩形打开（依赖系统/ROM 是否支持 freeform）。

### 待完成（近期）

- **丝滑非线性动画**：自动化开始时把主应用“缩小成悬浮窗”的过渡动画。
- **自动化提速**：对“打开/启动/进入 应用”本地优先直开 + 减少每步不必要等待/重复采集。
- **本地验证**：Gradle Sync/Build 通过；重进 App 不再显示“API未检查”（若之前已检查）。

## 2. 关键代码入口与数据流索引

- **主入口/对话页**：`app/src/main/java/com/ai/phoneagent/MainActivity.kt`
- **自动化入口页**：`app/src/main/java/com/ai/phoneagent/AutomationActivity.kt`
  - **启动自动化（模型驱动闭环）**：`startModelDrivenAutomation()`
  - **无障碍启用检测**：`isAccessibilityEnabled()`
- **无障碍能力（执行通道）**：`app/src/main/java/com/ai/phoneagent/PhoneAgentAccessibilityService.kt`
  - 负责 `dumpUiTree()`、点击/滑动/返回等动作（以及 Android 11+ 的 `takeScreenshot`）。
- **Agent 主循环（模型→动作→执行→再采集）**：`app/src/main/java/com/ai/phoneagent/UiAutomationAgent.kt`
  - 负责 prompt/messages 组织、动作解析（`do(...) / finish(...)`）、延迟策略与日志。
- **执行中悬浮窗（进度展示）**：`app/src/main/java/com/ai/phoneagent/AutomationOverlay.kt`
  - 负责 `WindowManager` overlay 的显示/更新/拖动/点击返回。
- **统一权限引导底部面板**：
  - `app/src/main/java/com/ai/phoneagent/PermissionBottomSheet.kt`
  - `app/src/main/res/layout/sheet_permissions.xml`
- **参考实现（可拆解复用）**：`temp/Phone-Agent_app-main`（包含成品浮窗/窗口模式切换等）

## 3. 关键决策（待确认）

- **点击悬浮窗后应用以“小窗模式”打开：选择 A / B**
  - **方案 A**：点击后正常全屏打开（最稳、所有机型一致）。
  - **方案 B**：尽量用 `ActivityOptions.setLaunchBounds` 让 Activity 以小矩形打开（依赖系统/ROM 是否支持 freeform，小米/三星等可能行为不同）。

## 4. 近期路线图（对齐 TODO）

- **TODO#10（in_progress）**：悬浮窗交互与视觉继续打磨
  - 更像微信语音通话：拖动吸边/停靠、点击回到应用、完成态光效/进度文案优化。
- **TODO#13（pending）**：自动化开始时“主应用缩小成悬浮窗”的丝滑非线性动画
  - 需要先确认「小窗模式 A/B」，再确定点击后恢复时的窗口策略。
- **TODO#8（pending）**：自动化提速（本地优先 Launch + 减少不必要等待/重复采集）
- **TODO#9（pending）**：抖音“点赞置顶评论”本地模板动作（降低模型不确定性）
- **TODO#5（in_progress）**：本地编译/运行验证（编译已通过；待真机回归 overlay/进度与重试日志）

## 5. 开发构想（DeepSeek 风格手机助手）

- **模型接入**：集成智谱 AutoGLM-Phone-9B，封装推理接口（HTTP/WebSocket），添加 Token/密钥配置页。
- **多模态/自动化能力**：
  - ADB/Accessibility 自动化：模拟点击、输入、滚动，截图 OCR，结合策略自动完成 12306 订座、抖音评论等。
  - 流程编排：任务脚本（YAML/JSON）描述场景，执行器按步骤调用自动化与模型。
- **UI 设计**：
  - DeepSeek 式对话主界面：消息流 + 卡片式工具结果（截图、表单、进度）。
  - “任务”面板：展示正在运行的自动化流程、进度、日志。
  - “插件/工具”面板：可视化开关 ADB、辅助功能权限、网络代理、模型端点配置。
- **安全与合规**：
  - 权限最小化，显式提示账号/支付操作风险；
  - 可选沙盒/模拟器模式；
  - 日志脱敏（移除手机号/订单号）。

### 5.1 路线图补充说明（可选）

> 这里保留更“工程化”的拆解口径，用于对齐阶段目标；近期以「4. 近期路线图」为准。

1. **复用参考实现**：从 `temp/Phone-Agent_app-main` 拆解可复用的浮窗模式切换/动画与交互细节。
2. **自动化体验**：把“模型闭环”过程中用户等待感降到最低（本地直开、少采集、少等待、少重复）。
3. **稳定性模板化**：对高频复杂任务（例如抖音评论/点赞/置顶相关）用本地模板动作兜底。
4. **可观测性**：统一日志、任务进度、错误收集与可复现材料（UI 树/截图/动作序列）。

## 6. 问题记录（定位 → 修复）

> 统一记录模板：**现象** → **影响** → **根因** → **修复/规避** → **涉及文件/备注**。

### 6.1 Gradle 同步/构建失败：ktlint 插件解析失败（已修复）

- **现象**：Gradle Sync 报错，无法解析 `org.jlleitschuh.gradle.ktlint` 插件。
- **影响**：IDE 无法 Sync / 无法构建。
- **根因**：插件版本/仓库解析不可用（CI/网络环境导致）。
- **修复/规避**：移除工程和 app 模块对 ktlint 插件的引用与配置。
- **涉及文件**：`build.gradle.kts`、`app/build.gradle.kts`、`gradle/libs.versions.toml`

### 6.2 Drawer 沉浸式适配：状态栏文字/图标变白 & 灰遮罩残留（已规避）

- **现象**：`WindowCompat.setDecorFitsSystemWindows(window, false)` + 手动 insets 时，部分设备状态栏文字/图标变白；Drawer 打开仍出现灰色背景；内容顶部下移。
- **影响**：沉浸式体验不一致，影响观感/可用性。
- **根因**：不同 ROM 对 edge-to-edge + status bar appearance + Drawer scrim 的处理差异。
- **修复/规避**：
  - 回滚为 `setDecorFitsSystemWindows(window, true)` + 透明状态栏/导航栏（保持 Light Status Bar），让系统托管 inset。
  - Drawer `scrimColor` / `statusBarBackground` 保持透明，避免额外覆盖。
- **备注**：若后续要继续深度沉浸式，需要按目标机型逐步验证。

### 6.3 VSCode 状态栏显示 “Gradle: Build Error”（无需处理）

- **现象**：VSCode 左下角显示 “Gradle: Build Error”。
- **影响**：不影响实际 Android Studio 编译/运行，但会造成误判。
- **根因**：VSCode/插件侧的 Gradle 集成能力有限或环境不一致。
- **修复/规避**：以 Android Studio 的 Sync/Build 结果为准；VSCode 用于编辑即可。

### 6.4 VSCode Kotlin Language Server 403：GitHub API rate limit exceeded（已解决）

- **现象**：`Could not fetch from GitHub releases API: StatusCodeError: 403 - {"message":"API rate limit exceeded..."}`。
- **影响**：Kotlin 语言服务无法下载/更新，影响代码提示。
- **根因**：GitHub 匿名访问速率限制。
- **修复/规避**：
  1. 生成 GitHub Personal Access Token（无需特殊权限）。
  2. Windows 环境变量添加 `GITHUB_TOKEN=<token>`。
  3. 重启 VSCode。

### 6.5 Vosk 语音模型解压失败/缓存损坏（已处理，详见知识库）

- **现象**：Toast “模型解压失败”；按钮无响应。
- **影响**：离线语音输入不可用。
- **根因**：assets 打包不完整、目录名不一致、缓存目录残留半拷贝、存储空间不足等。
- **修复/规避**：启动时做缓存有效性检测；失败时清理后重新解压；必要时卸载重装清理 `files/model`。

### 6.6 API Key 掩码状态不即时刷新（待优化）

- **现象**：保存 API Key 后 UI 的 `*` 掩码状态不会立刻刷新。
- **影响**：不影响实际请求，但 UI 反馈滞后。
- **计划**：保存成功后主动刷新状态 TextView / 或监听存储变更。

### 6.7 Launch 别名不命中（待增强）

- **现象**：`do(action=Launch, app="系统设置")` 可能找不到 Intent。
- **影响**：模型动作执行失败，自动化中断。
- **临时建议**：优先输出包名（如 `com.android.settings`），或使用更常见别名（`设置`/`Settings`）。
- **计划**：补充别名映射并增强 label/包名匹配策略。

### 6.8 小窗/主界面消息不同步或消息“丢失显示”（已修复）

- **现象**：在小窗中发送“你好”等消息，主界面与小窗有时都未显示或恢复后缺失。
- **影响**：对话连续性被破坏，用户误以为发送失败。
- **根因**（组合问题）：
  1. 消息前缀存在 `"我:"` 与 `"我: "` 的格式差异，部分解析/渲染逻辑仅匹配其中一种，导致漏渲染/漏恢复。
  2. 主界面在 `onPause()` 清理 `messageSyncListener`，导致主界面后台时小窗产生的新消息无法实时推送；恢复时缺少可靠的补偿通道。
- **修复/规避**：
  - 统一/兼容前缀解析：`updateMessagesUI()` 与 `restoreChatHistory()` 对 `"我:"/"我: "`、`"AI:"/"AI: "` 均兼容。
  - 悬浮窗服务侧增加待同步队列：监听器为空时暂存，监听器重新挂载时补发。
  - 主界面侧不在 `onPause()` 清监听器，改为 `onDestroy()` 清理，减少后台期间的同步空窗。
- **涉及文件**：`FloatingChatService.kt`、`MainActivity.kt`

### 6.9 主界面右滑打开 Drawer 无响应（已修复）

- **现象**：用户期望在主页任意位置右滑打开侧边栏，但右滑无响应或仅边缘可触发。
- **影响**：交互不符合预期。
- **根因**：
  - 早期实现用自定义 `dispatchTouchEvent` 做边缘检测/拦截，后续迭代中移除了相关逻辑；
  - 直接依赖 `DrawerLayout` 默认边缘触发在部分机型/布局下触发区域较窄。
- **修复/规避**：在 `MainActivity.dispatchTouchEvent` 中实现“主页任意位置右滑超过阈值触发打开 Drawer”的判定，然后调用 `openDrawer(GravityCompat.START, true)` 打开。
- **涉及文件**：`MainActivity.kt`

## 7. 更新记录（Changelog）

### 更新记录 · 2026-01-08

- **小窗关闭防闪优化**
  - 优化 `FloatingChatService.animateDismissAndMaybeOpenApp()` 动画逻辑
  - 点击返回后立即降低透明度（0.3f）并禁用触摸
  - 使用更快的淡出动画（100ms，AccelerateInterpolator）
  - 动画完成后延迟 30ms 再拉起主界面，避免同时操作导致的视觉闪烁
  - 涉及文件：`FloatingChatService.kt`

- **主界面消息气泡操作区可见性修复**
  - 重新进入 App 时遍历已渲染气泡，强制显示底部“复制/重试”操作区，避免因动画/复用导致按钮消失。
  - 涉及文件：`MainActivity.kt`

- **输入栏首次点击即弹键盘**
  - 为输入框添加触摸聚焦并主动唤起软键盘的逻辑，解决需要点击两次才能输入的问题。
  - 涉及文件：`MainActivity.kt`

- **进度百分比精准显示**
  - `AutomationOverlay` 新增 `estimatedTotalSteps`、`hasEstimatedSteps` 状态变量
  - `updateEstimatedSteps()` 只在首次解析出有效预估值时设置
  - 新增 `calculateProgressPercent()` 智能计算百分比：
    - 有预估步骤数时：使用真实 `step/estimated` 计算
    - 无预估时：使用平滑假进度曲线（快速升到 60%，缓慢增长到 90%）
  - 涉及文件：`AutomationOverlay.kt`

- **主页模型对话优化**
  - 修复 AI 无法看到用户上下文问题：新增 `buildChatHistory()` 构建完整对话历史（系统提示 + 最近 10 轮对话）
  - 新增 `appendMessageBatch()` 方法：使用 StringBuilder 每 5 字符批量刷新 UI，根据标点调整延迟，显示更自然流畅
  - 涉及文件：`MainActivity.kt`

### 更新记录 · 2026-01-05

- **应用启动提速与缓存**：新增 `core/tools/AppPackageManager.kt`，后台缓存应用名↔包名（5 分钟），`launch_app` 直启绕过模型导航，新增 `get_installed_apps` 供选择；启动耗时约 ~1s。
- **系统提示词与工具注册**：`AutomationActivityNew` 更新系统提示，明确允许购物/支付/金融等正常操作；`ToolRegistration` 注册新工具并使用缓存直启。
- **敏感检测放宽**：`ContentFilter` 去除模型“过度警告”，`UiAutomationAgent` 仅对支付密码/CVV/验证码等高风险词触发敏感提示，购物/支付流不再被误拦。
- **进度与步数**：`maxSteps` 默认 100，`maxModelRetries` 3；`AutomationOverlay`/`UIAutomationProgressOverlay` 进度显示改为百分比，消除 “1/12” 误导。

### 更新记录 · 2026-01-03

- 小窗模式（FloatingChatService / MainActivity）
  - 修复“从小窗回到 App 偶发明显闪一下 / 重试导致二次拉起”的体验问题。
  - `MainActivity` 在 `onResume()` 与 `onNewIntent()` 统一处理 `from_floating` 并尽早发送 ACK。
  - `FloatingChatService` 在 Home/放大后等待 ACK 再停服务，避免前台通知过早消失；增加有限次重试与超时恢复逻辑。
  - 视觉策略：收起淡出动画增加短 `startDelay`，并在动画后将 View 设为 `GONE`（不立即移除 Window），返回期间设置 `FLAG_NOT_TOUCHABLE` 避免挡触摸。

### 更新记录 · 2026-01-02

- 主对话页 Drawer/手势
  - 支持主页任意位置右滑，超过阈值后打开 Drawer（系统动画）。
  - 说明：当前为“阈值触发打开”，非全程跟手拖拽。

- 小窗模式（FloatingChatService）
  - 进入小窗的唯一入口：点击顶部菜单按钮（禁用右滑进入小窗）。
  - 主界面 ↔ 小窗消息双向同步增强：新增待同步队列，主界面恢复后补发未同步消息。
  - 修复消息前缀解析差异导致的漏渲染/漏恢复（兼容 `"我:"/"我: "`、`"AI:"/"AI: "`）。

### 更新记录 · 2026-01-01

- 权限体系
  - 新增首次启动底部权限面板（无障碍/悬浮窗/录音）。
  - 自动化执行中不再弹权限请求或强制跳转设置；缺权限时仅提示/跳过悬浮窗。

- 自动化悬浮窗
  - `AutomationOverlay` 调整为更小的白底圆角卡片，并加入拖动能力。
  - 文案显示优化：副标题去掉 `[Step x]` 前缀。
  - Overlay 移除时取消动画，减少后台资源占用。

- 工程构建
  - 移除 `ktlint` Gradle 插件引用，解决插件仓库解析失败导致的构建错误。

- 自动化可靠性
  - 模型调用统一使用 `AutoGlmClient.sendChatResult(...)`，对 429/5xx/IO 做有限次重试 + 指数退避。
  - 输出解析加入修复循环：解析失败时要求模型严格输出 `do(...) / finish(...)`。
  - 动作执行失败时回传失败信息请求模型修复动作，并做有限次重试。
  - 修复“剥离截图”索引：记录 `observationUserIndex`，避免修复对话插入导致剥离错位。
  - 自检：`:app:compileDebugKotlin` 通过。

### 更新记录 · 2025-12-31

- **主对话页 UI 与 IME 适配（完成 t15）**
  - Edge-to-edge：`WindowCompat.setDecorFitsSystemWindows(window, false)`。
  - Insets 监听挂到 `drawerLayout` 根；将 `systemBars.top` 与 `max(systemBars.bottom, ime.bottom)` 应用到 `contentRoot` padding，IME 打开时消息区与输入栏一起上移；`navigationView` 仅应用 `systemBars`。
  - 顶部位置优化：`contentRoot` 去掉额外 `paddingTop`（0dp），`ai_bar_margin_top` 改为 `6dp`，顶栏更贴近状态栏但不遮挡。
  - 输入栏悬浮感：`inputBar` 背景用主题色；关闭裁剪以呈现阴影/光效；`inputContainer` 提升圆角与阴影；关闭 `cardUseCompatPadding` 让光效贴边。

- **灵动光效改造（完成 tGlow）**
  - 统一为整圈连续渐变：`亮蓝 → 青蓝 → 亮蓝 → 粉色 → 亮蓝 → 青蓝 → 亮蓝` 循环，取消高亮头/拖尾段，不再出现“伸缩/呼吸”观感。
  - 旋转更稳：周期 `~5200ms`；发光描边与核心描边双层叠加，但用 `maxHalf` 内缩避免被裁剪导致厚度变化。
  - 坐标稳定：边框光效的 bounds 计算改为 `ViewGroup.offsetDescendantRectToMyCoords` 并挂载到父视图 overlay，避免因平移/滚动导致错位。
  - 崩溃修复：修正 `SweepGradient` 颜色与位置数组不等长导致的启动崩溃。
  - 涉及文件：`MainActivity.kt`（`attachAnimatedRing`/`attachAnimatedBorderRing`）。

### 更新记录 · 2025-12-30

- Drawer/手势
  - Drawer 打开时主内容区域平移+缩放（DeepSeek 风格），并关闭 scrim（透明遮罩）。
  - 支持全屏右滑打开 Drawer、左滑关闭。

- 顶部栏
  - 顶部栏新增“新对话”按钮：创建新会话并清空 UI。

- 历史对话（多会话）
  - 历史弹窗升级为 `RecyclerView` 列表：点击加载会话；左滑删除会话。
  - 数据结构从简单 Q/A 列表升级为 `Conversation + UiMessage`（内存态）。

- 自动化入口
  - 侧边栏“自动化”可进入 `AutomationActivity`。
  - 自动化页面使用 `WindowInsets` 为根布局添加 `systemBars` padding，避免被状态栏/手势条遮挡。
  - 新增 `AccessibilityService` MVP：可在系统开启无障碍后执行示例动作（主页/滑动/返回）。
  - 新增模型驱动闭环：输入任务与模型 ID 后，`UiAutomationAgent` 多步调用 API 输出动作并执行。
  - 协议升级：对齐 Open-AutoGLM 官方输出格式（`do(...) / finish(...)`）与多模态 messages。
  - 截图能力：Android 11+ 通过 `AccessibilityService.takeScreenshot` 每步采集截图并以 base64 PNG 发送给模型（失败时自动退化为纯文本/UI 树）。
  - 构建约束：`minSdk` 提升至 30（Android 11+），确保截图 API 可用。
  - Launch 稳定性：已移植 `Open-AutoGLM-main/phone_agent/config/apps.py` 常用应用名→包名映射，执行 Launch 时优先走映射，再回退已安装应用 label 匹配。

### 更新记录 · 2025-12-29

- 核心环境与构建
  - 在 Windows 配置 JDK 17：`JAVA_HOME=C:\Users\30989\.jdks\ms-17.0.17`，并将 `%JAVA_HOME%\bin` 加入 `Path`。
  - Android Studio 的 Gradle JDK 设为本地 JDK 17 或 Embedded JDK 17。
  - 命令行构建验证：`.\gradlew.bat :app:assembleDebug` 成功。

- Vosk 集成修复（与 `com.alphacephei:vosk-android:0.3.32` 对齐）
  - 旧接口 `UnpackListener/ExceptionListener` 已被替换为 `StorageService.Callback<T>`。
  - 正确的初始化片段如下：

```kotlin
StorageService.unpack(
    this,
    "model",
    "model",
    object : StorageService.Callback<Model> {
        override fun onComplete(result: Model?) {
            if (result == null) {
                Toast.makeText(this@MainActivity, "模型解压失败", Toast.LENGTH_LONG).show()
                return
            }
            this@MainActivity.model = result
            binding.btnVoice.isEnabled = true
            Toast.makeText(this@MainActivity, "本地语音模型已就绪", Toast.LENGTH_SHORT).show()
        }
    },
    object : StorageService.Callback<IOException> {
        override fun onComplete(result: IOException?) {
            Toast.makeText(this@MainActivity, "模型解压失败: ${result?.message}", Toast.LENGTH_LONG).show()
        }
    }
)
```

- 资源与权限
  - 中文模型目录已放置：`app/src/main/assets/model/`。
  - Manifest 已声明录音权限：`android.permission.RECORD_AUDIO`。

- 语音按钮交互与动画
  - 启动时按钮保持可点击；模型未就绪时点击会提示“模型加载中…”。
  - 首次点击会触发系统录音权限弹窗，授权后自动开始识别。
  - 识别开始时麦克风按钮出现“呼吸动画”，停止后恢复。
  - 识别的部分/最终文本会实时回填到输入框（保留原始前缀）。

- UI 调整
  - 输入框文字颜色设为黑色：在 `activity_main.xml` 的 `EditText` 上设置 `android:textColor="@android:color/black"`。

- 快速验证步骤
  1. 首次点击麦克风按钮 → 授权录音（如已授权则直接进入识别）。
  2. 说出一段中文 → 输入框实时出现黑色文字（部分/最终结果）。
  3. 再点一次麦克风按钮 → 识别停止，动画消失。


## 8. 知识库（Reference）

### 参考代码（temp 目录 - 只读参考）

- `temp/Open-AutoGLM-main`：智谱官方发布的参考实现。该项目需要通过 ADB 连接手机以实现自动化/模型联动功能，仅作参考和脚本借鉴，禁止修改 `temp` 目录中的内容到仓库中。
- `temp/Operit-main`：成品安卓 App 示例（独立可运行）。该项目为参考实现，可用于对比 UI 与自动化流程实现，同样请勿在 `temp` 目录中直接修改文件。

说明：若需要对参考代码做实验或修改，请在工作目录外另行复制一份（例如 `work/Open-AutoGLM/`），将改动保存在该复制目录，不要将更改提交到当前仓库 `temp/` 下。

### 常用应用包名映射（Kotlin Map 示例）

以下是常用应用（如美团、12306、抖音等）包名的 Kotlin Map 映射示例，可用于快速查找应用包名：

```kotlin
val appMapping = mapOf(
  "美团" to "com.sankuai.meituan",
  "12306" to "com.MobileTicket",
  "抖音" to "com.ss.android.ugc.aweme",
  "微信" to "com.tencent.mm",
  "支付宝" to "com.eg.android.AlipayGphone",
  "QQ" to "com.tencent.mobileqq"
  // ...可继续补充
)
```

如需扩展，请在 map 中继续添加应用名称与包名的对应关系。

### Launch 融合与映射迁移说明

目前 Launch 功能采用“按已安装应用 label/包名模糊匹配”，但稳定性有限。

官方 `apps.py` 已维护常用应用包名映射（如美团、12306、抖音等），建议将 `Open-AutoGLM-main/phone_agent/config/apps.py` 的映射移植为 Kotlin Map。

这样，未来可直接通过 `Launch app="美团"` 等指令稳定启动目标应用，无需依赖模糊匹配。

迁移后，映射将更丰富、更稳定，推荐逐步完善和同步官方映射。

---

### 语音模型交互问题记录（解压失败/缓存损坏/路径不匹配）

- 现象
  - 首次启动或点击麦克风时弹出 Toast：“模型解压失败”。
  - 语音按钮看似无反应（实为模型未就绪或解压失败）。

- 常见根因
  - APK 中 `assets/model/**` 未完整打包或目录名/大小写不一致（必须为 `model`）。
  - 设备私有目录 `files/model` 残留半拷贝/损坏，导致后续加载 `Model` 失败。
  - 设备存储空间不足（中文模型解压后可达上百 MB）。
  - 模型目录缺关键文件（如 `conf/model.conf`）。

- 排查步骤（推荐顺序）
  1. 设备端清理缓存：卸载 App 重新安装，或用 Device File Explorer 删除 `data/data/com.ai.phoneagent/files/model`。
  2. 验证 APK 资源：Build APK → Analyze APK… → 展开 `assets/model/`，确认包含 `conf/model.conf` 等关键文件。
  3. 看 Logcat：过滤 `StorageService|Vosk|IOException|unpack`，关注 `Caused by` 根因（如 ENOSPC 空间不足、ENOENT 路径不存在）。
  4. 确认设备可用存储空间充足，再次启动。

- 代码层处理（已实现）
  - 启动时优先检测 `files/model/conf/model.conf`：若存在则直接从缓存目录创建 `Model`，跳过解压；
  - 若缓存存在但加载失败，自动 `deleteRecursively()` 清理后再解压；
  - 解压成功后更新状态文案“离线模型已就绪”，并启用麦克风按钮；
  - 模型未就绪时点击麦克风会提示“模型加载中…”。

- 建议
  - 首次发布建议使用体积较小的中文模型以缩短首次解压时间；
  - 为模型下载/更新加入进度提示与失败重试逻辑（后续可扩展为在线下载）。

---

### Android Studio 代理设置（解决依赖无法同步/下载慢问题）

1. 打开 Android Studio，点击右上角搜索图标左边的“犀牛”图标（或依次进入 File > Settings > Appearance & Behavior > System Settings > HTTP Proxy）。
2. 选择 “Manual proxy configuration”，填写：
  - Host name: `127.0.0.1`
  - Port: `7897`（如使用 Clash Verge，端口为 7897，不同代理软件端口可能不同，请以实际为准）
3. 点击 “Check connection”，可输入 `github.com` 测试代理是否可用。
4. 设置完成后，点击右下角“应用”，重启 IDE 并重试同步。

> ⚠️ 如依赖同步失败、无法访问外网资源，优先检查代理设置。

---

### Sherpa-ncnn 本地语音识别集成说明（当前方案）

> 已替换 Vosk，改用 Sherpa-ncnn 作为本地语音识别引擎。

- **项目地址**：https://github.com/k2-fsa/sherpa-ncnn
- **优势**：比 Vosk 更轻量、实时性更好、支持流式识别、中英文混合识别效果佳

#### 依赖文件

1. **JNI 原生库**（必需）
   - 下载地址：https://github.com/k2-fsa/sherpa-ncnn/releases
   - 放置位置：`app/src/main/jniLibs/arm64-v8a/libsherpa-ncnn-jni.so` 和 `armeabi-v7a/`

2. **模型文件**（必需）
   - 推荐模型：`sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13`（中英双语）
   - 下载地址：https://huggingface.co/csukuangfj/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13
   - 放置位置：`app/src/main/assets/sherpa-models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/`

#### 核心文件

- `app/src/main/java/com/k2fsa/sherpa/ncnn/SherpaNcnn.kt` - JNI 封装类
- `app/src/main/java/com/ai/phoneagent/speech/SherpaSpeechRecognizer.kt` - 语音识别器封装

#### 使用方式

```kotlin
// 初始化
val recognizer = SherpaSpeechRecognizer(context)
recognizer.initialize()

// 开始识别
recognizer.startListening(object : SherpaSpeechRecognizer.RecognitionListener {
    override fun onPartialResult(text: String) { /* 中间结果 */ }
    override fun onResult(text: String) { /* 完整结果 */ }
    override fun onFinalResult(text: String) { /* 最终结果 */ }
    override fun onError(exception: Exception) { /* 错误处理 */ }
    override fun onTimeout() { /* 超时处理 */ }
})

// 停止识别
recognizer.stopListening()

// 释放资源
recognizer.shutdown()
```

#### 排查步骤

1. 确认 `libsherpa-ncnn-jni.so` 已放入 jniLibs 对应架构目录
2. 确认模型文件完整（7 个文件：encoder/decoder/joiner 的 param+bin，以及 tokens.txt）
3. Logcat 过滤 `SherpaSpeechRecognizer` 查看初始化日志
4. 首次启动会将模型从 assets 拷贝到 filesDir，需要一定时间

---

### Vosk 本地语音识别集成说明（已弃用）

> ⚠️ 已弃用，改用 Sherpa-ncnn。以下内容仅供参考。

- 依赖：`com.alphacephei:vosk-android:0.3.32`
- 模型：需下载中文模型（如 vosk-model-cn），放置于 `app/src/main/assets/model/` 目录。
- 初始化：在 `MainActivity` 中通过 `Model` 和 `Recognizer` 初始化，建议异步加载模型，避免主线程阻塞。
- 权限：需动态申请录音权限（Manifest.permission.RECORD_AUDIO）。
- 监听：实现 `RecognitionListener`，处理 onResult/onPartialResult/onError 等回调。
- 注意：如遇 PointerType 依赖冲突，仅保留 Android 版 vosk 依赖，避免多平台混用。

---

### UI/交互优化与依赖说明
- 顶部栏悬浮：通过 `ViewCompat.setOnApplyWindowInsetsListener` 适配状态栏，提升沉浸感。
- 输入框自适应高度：`EditText` 配合 `addTextChangedListener` 实现多行自动扩展。
- 打字机动画：`ObjectAnimator` 或自定义协程逐字显示，提升消息流动感。
- API 配置栏：支持动态切换模型端点、Token，便于开发调试。
- 依赖建议：
  - Kotlin 协程、ViewBinding、Retrofit/OkHttp、Vosk SDK
  - 推荐 minSdk 24 及以上，便于兼容新特性

---

### 常见问题与环境配置经验
- Gradle 下载失败/SSL 问题：优先使用国内镜像或本地分发包，必要时配置代理。
- JDK 版本：强制使用 JDK 17，避免 8/11/21 版本兼容性问题。
- SDK/NDK 缺失：用 sdkmanager 安装指定版本（如 ndk;25.1.8937393），并接受所有 license。
- 语音识别无响应：检查模型路径、录音权限、设备兼容性。
- Clean/Rebuild：推荐用 `./gradlew clean build` 或 IDE 菜单操作，排查依赖冲突。

---

### 问题记录与持续补充
- 按模板记录开发中遇到的 bug、现象、定位与解决方案，便于团队协作与知识沉淀。

> 文档持续更新，建议每次大改动/新特性开发后补充说明。

## 9. 历史任务进度（旧）

- **t15 主对话页输入栏/键盘适配 + 光效贴合 + 悬浮感**：completed
- **tGlow 灵动光效统一风格**（整圈连续渐变：亮蓝/青蓝/粉色，慢速旋转，无伸缩感）：completed
- **t14 API 配置区密钥掩码即时刷新 + 动态光效错位修复**：completed
- **t16 历史对话持久化**（退出后恢复会话与消息）：pending
- **t17 自动化闭环稳定性复核**（Launch/截图/解析/等待等）：pending
- **t12 敏感操作确认/接管机制**（敏感 Tap 确认、暂停/继续/接管）：pending

## 10. 大文件/LFS 与协作提醒

- **所有日志文件（*.log、logs/ 目录等）已被 .gitignore 排除，不会提交到 GitHub。**
    - 如需保留日志，请手动备份。
    - 日志文件如需协作分析，请通过其他方式（如网盘、邮件）单独分享。

> 重要：仓库已忽略体积超 100MB 的 Sherpa 模型二进制，请勿再次提交模型文件；推送前务必检查 `git status`。

---

## 2026-01-06 更新日志

### UI/交互优化
- **自动化界面推荐语句滚动展示**
  - 在任务输入框下方添加半透明推荐语句区域，每4秒自动切换展示。
  - 推荐语句包括：美团预订火锅店、12306订票、航旅纵横订机票等实用示例。
  - 用户可点击推荐语句直接填入输入框，提升输入效率。
  - 支持淡入淡出动画过渡，视觉体验流畅。
- **语音输入图标优化**
  - 语音输入按钮移至任务输入框右侧居中位置，尺寸加大至56dp。
  - 添加圆形高亮背景和4dp阴影，更显眼易点击。
  - 保持与主界面语音输入的一致交互体验。
- **界面简化**
  - 移除"执行硬编码测试动作"按钮，界面更简洁专注。
  - 输入框与语音按钮横向布局，充分利用屏幕宽度。

### 关于界面优化
- **更新日志弹窗精简**
  - 顶部说明精简为一句"下方可以选择历史版本"。
  - 移除多行技术警告（GitHub token、镜像、API限流等），界面更清爽。
  - 彻底移除语音输入相关控件和逻辑。
  - 修复布局和代码引用不一致导致的编译错误。

### 代码结构优化
- **推荐语句系统**
  - 推荐语句列表集中管理，易于扩展和维护。
  - 滚动逻辑与UI解耦，使用协程实现定时切换。
  - 支持自定义动画效果和切换间隔。
  - 在 Activity 销毁时自动清理协程，避免内存泄漏。
- **适配性增强**
  - AutomationActivity 和 AutomationActivityNew 同步实现推荐语句功能。
  - 共用布局文件，确保双版本体验一致。
  - 资源文件自动生成（bg_rounded_light.xml），减少手动配置。

### Bug修复
- 修复资源缺失导致的编译失败（ic_mic.xml、bg_circle_accent.xml、bg_rounded_light.xml）。
- 修复布局和代码引用不一致导致的未定义错误（btnRunDemo、btnViewAll、语音输入相关）。
- 修复关于界面弹窗旧内容显示问题，确保运行时和XML同步更新。
- 修复 AutomationActivityNew.kt 缺失 `kotlinx.coroutines.delay` 导入导致的编译错误。

### 涉及文件
- `app/src/main/res/layout/activity_automation.xml`
- `app/src/main/res/layout/dialog_release_history.xml`
- `app/src/main/java/com/ai/phoneagent/AutomationActivity.kt`
- `app/src/main/java/com/ai/phoneagent/AutomationActivityNew.kt`
- `app/src/main/java/com/ai/phoneagent/AboutActivity.kt`
- `app/src/main/res/drawable/bg_rounded_light.xml`
- `app/src/main/res/drawable/ic_mic.xml`
- `app/src/main/res/drawable/bg_circle_accent.xml`

---

## 今日进度总结（2026-01-06）

✅ **完成项**
1. 自动化界面推荐语句滚动展示功能（4秒切换，点击填入）
2. 语音输入图标优化（右侧居中、显眼、易用）
3. 移除硬编码测试按钮，界面更简洁
4. 关于界面弹窗精简优化
5. 修复所有资源和引用相关编译错误
6. 代码结构优化，推荐语句系统易扩展
7. 文档同步更新，记录完整进度

🎯 **核心收益**
- 用户输入任务更便捷（推荐语句一键填入）
- 界面更简洁专注（移除冗余按钮和警告）
- 代码更规范易维护（解耦设计、协程管理）
- 交互体验更流畅（动画过渡、视觉优化）

📝 **技术亮点**
- 使用协程实现定时滚动，资源管理规范
- 淡入淡出动画提升视觉体验
- 双Activity同步实现，确保版本一致性
- 自动生成缺失资源文件，降低配置成本

---

> 重要：仓库已忽略体积超 100MB 的 Sherpa 模型二进制，请勿再次提交模型文件；推送前务必检查 `git status`。

- 模型/大文件不入库：
  - `.gitignore` 已忽略 `app/src/main/assets/sherpa-models/`，仅保留 `README.md` 作为下载指引。
  - JNI so 仍需放在 `app/src/main/jniLibs/arm64-v8a/`、`armeabi-v7a/`（体积较小，可入库）。
- 模型获取与摆放：
  1) 按 `app/src/main/assets/sherpa-models/README.md` 下载模型压缩包。
  2) 解压后放入 `app/src/main/assets/sherpa-models/`，保持原有文件结构（`encoder_jit_trace-pnnx.ncnn.bin` 等）。
  3) 本地运行前确认路径存在；提交前确认未被 `git add`。
- 若历史已包含大文件需清理再推送：
  - 使用 `git filter-repo`：
    ```bash
    git filter-repo --invert-paths \
      --path app/src/main/assets/sherpa-models/model_qint8_arm64.onnx \
      --path app/src/main/assets/sherpa-models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/encoder_jit_trace-pnnx.ncnn.bin
    ```
  - 或用 BFG：
    ```bash
    bfg --delete-files model_qint8_arm64.onnx --delete-files encoder_jit_trace-pnnx.ncnn.bin
    ```
  - 清理后强推：`git push -f origin main`。
  - 协作者：历史重写后请重新 clone 或重建本地分支，避免旧提交带入大文件。
