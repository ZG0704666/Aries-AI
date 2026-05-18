# Aries AI 开发文档

> 最后更新：2026-05-18
>
> 当前版本：1.4.3 (versionCode 18)

## 📖 阅读指南

**文档目的**：让开发者（包括 AI）快速理解项目当前状态、关键决策和技术实现。

**推荐阅读顺序**：
1. 快速导航 → 了解文档结构
2. 最新状态 → 掌握当前进度
3. 技术架构 → 理解核心实现
4. 开发指南 → 开始开发
5. 问题解决 → 遇到问题时查阅

---

## 📑 快速导航

### 核心章节
- [最新状态](#最新状态) - 当前版本功能与进度
- [技术架构](#技术架构) - 核心模块与数据流
- [开发指南](#开发指南) - 环境配置与开发规范
- [问题解决](#问题解决) - 常见问题与解决方案
- [版本历史](#版本历史) - 版本更新记录

### 快速查询
- [快速查问题索引](#快速查问题索引) - 按现象快速定位
- [代码入口索引](#代码入口索引) - 关键文件位置
- [配置参数说明](#配置参数说明) - AgentConfiguration 详解

---

## 🎯 最新状态

### 版本 1.4.3 (2026-04-09)

**当前版本信息**：
- versionCode: 18
- versionName: v1.4.3
- targetSdk: 36 (Android 16)
- minSdk: 30 (Android 11)
- Kotlin: 2.2.21
- Gradle: 8.13
- AGP: 8.13.2

**更新**：
- **用户体验改进计划（UX Program）**：新增可选的用户体验改进计划配置，收集基础报错数据以诊断产品问题，默认关闭。同步更新隐私政策。
- **流式思考内容深度重构 (Stream WebView Render)正在处理！！**：大幅优化流处理链路（`StreamRenderHelper`），稳定解析和渲染 `<think>` 标签及 Markdown 格式文本，修复思考框收缩与文本乱码故障。

**核心功能状态**：

| 功能模块 | 状态 | 说明 |
|---------|------|------|
| 主对话界面 | ✅ 优化 | 深度重构流式处理（StreamRenderHelper），优化 `<think>` 标签解析，修复渲染异常 |
| 自动化执行 | ✅ 稳定 | Agent 循环、动作解析、错误重试 |
| 虚拟屏模式 | ✅ 可用 | 后台执行、实时预览、Shizuku 集成 |
| 语音识别 | ✅ 稳定 | Sherpa-ncnn 本地识别 |
| 小窗模式 | ✅ 稳定 | 悬浮窗对话、消息同步 |
| 权限与协议 | ✅ 完善 | 统一权限引导，新增「用户体验改进计划」及隐私说明更新 |
| 多模型支持 | ✅ 可用 | OpenAI 兼容 API、自定义端点 |

**已知限制**：
- 预发布版尚在测试阶段，可能存在部分不稳定情况
- 虚拟屏输入源管理需要优化（部分场景可能使用剪贴板内容）
- 复杂 UI 场景下的元素识别准确率有待提升
- 长时间自动化任务的稳定性需要更多测试

### 近期开发状态（2026-05-17）

| 项目 | 状态 |
|------|------|
| 当前分支 | `pr-10` |
| 对齐目标 | `beiyuge/alpha` |
| 最近审查范围 | 2026-05-13 至 2026-05-17 的主页、设置、权限、自动化和 Markdown 渲染提交 |
| 主要风险 | 流式 Markdown 渲染与最终 Markdown 渲染路径需要保持一致 |
| 验证阻塞 | 当前本机环境未配置 `JAVA_HOME`，Gradle 测试无法执行 |

#### 近期提交审查

| 提交 | 主题 | 文档影响 |
|------|------|----------|
| `1591e6a` | 修复流式输出渲染 Markdown 问题 | 已补充流式 Markdown 缓冲、临时补全、代码高亮禁用策略 |
| `59e18dd` | 自动化控制界面状态摘要和交互模式文本 | 已补充自动化控制 UI 与后台执行依赖提示说明 |
| `fd369b2` | 429 与无障碍按钮 | 已补充错误恢复和权限入口相关状态 |
| `4626ccf` | 权限行布局与 API 模式持久化 | 已补充设置页 API 模式持久化和权限布局说明 |
| `9cb53e9` | 输入框、设置界面、附件提示文本 | 已补充主页输入栏和附件提示的开发注意点 |
| `d14b51a` | 反馈日志打包与分享 | 已补充反馈日志作为问题排查路径 |

#### 审查结论

- 主页对话已经迁移到 Compose transcript 路径，`ConversationTranscript.kt` 是消息正文、思考折叠、自动化卡片和 Markdown 渲染的主要维护点。
- 流式回复不能再走“裸 Markdown 文本 -> 最终 Markdown 渲染”的两阶段视觉路径；应使用渲染缓冲区和 `safeMarkdown` 快照，从一开始保持 Markdown 形态。
- 代码块渲染应统一走 `ui.components.markdown.CodeBlock`，避免流式和最终消息使用两套代码块 UI。
- 流式阶段应禁用代码高亮的异步重算，最终消息再启用完整高亮和工具栏，减少文本和容器跳动。
- 设置页、权限引导、自动化控制页的用户可见文案持续增加，新增文案必须进入 `strings.xml`。

#### 当前开发重点

| 方向 | 当前约定 |
|------|----------|
| 主页流式 Markdown | 原始输出保留在 raw buffer，UI 使用节流后的 render preview |
| 代码块 | 流式阶段保留代码块形态但禁用高亮，结束后使用完整 Markdown/CodeBlock 渲染 |
| 自动化控制 | UI 显示状态摘要、交互模式和后台执行依赖，文案统一走字符串资源 |
| 权限与设置 | 权限入口保持可见且可恢复；API 模式选择需要持久化到 SettingsViewModel 对应状态 |
| 问题反馈 | 日志打包和分享是 429、权限、模型请求失败的首选排查材料 |

#### 验证记录

建议执行：

```bash
./gradlew :app:testDebugUnitTest --tests "com.ai.phoneagent.ui.messages.StreamingTranscriptPreviewTest"
./gradlew :app:compileDebugKotlin
```

当前阻塞：

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

处理方式：安装 JDK 17 或使用 Android Studio 自带 JBR，设置 `JAVA_HOME` 后重新执行上述 Gradle 任务。

---

## 🏗️ 技术架构

### 核心模块

```
Aries AI
├── 对话系统
│   ├── MainActivity.kt - 主对话界面
│   ├── ui/messages/ConversationTranscript.kt - Compose 消息列表与流式 Markdown
│   ├── ui/components/markdown/ - Markdown 与代码块渲染
│   ├── FloatingChatService.kt - 小窗模式
│   └── helper/ - 流式解析与兼容辅助
├── 自动化系统
│   ├── UiAutomationAgent.kt - Agent 主循环
│   ├── core/executor/ - 动作执行器
│   ├── core/parser/ - 动作解析器
│   └── core/config/ - 配置管理
├── 虚拟屏系统
│   ├── VirtualDisplayController.kt - 虚拟屏管理
│   ├── ShizukuBridge.kt - Shizuku 集成
│   └── vdiso/ - 虚拟屏引擎
├── 语音系统
│   └── speech/SherpaSpeechRecognizer.kt - 语音识别
└── 网络层
    ├── net/AutoGlmClient.kt - API 客户端
    └── net/ChatModels.kt - 数据模型
```

### 数据流

**对话流程**：
```
用户输入 → MainActivity → AutoGlmClient → 流式响应 → AriesStreamParser → UI 渲染
```

**自动化流程**：
```
任务输入 → UiAutomationAgent → 截图/UI树采集 → 模型推理 → 动作解析 → ActionExecutor → 执行反馈 → 循环
```

**虚拟屏流程**：
```
启动虚拟屏 → ShizukuBridge → VirtualDisplayController → 后台执行 → 实时预览 → 结果返回
```

---

## 🔧 开发指南

### 环境要求

**必需**：
- JDK 17
- Android Studio Hedgehog (2023.1.1) 或更高
- Android SDK 36
- Gradle 8.13+

**推荐**：
- 8GB+ RAM
- 真机测试（Android 11-16）

### 快速开始

1. **克隆项目**
```bash
git clone <repository-url>
cd "Aries AI project"
```

2. **配置环境**
```bash
# 设置 JDK 17
export JAVA_HOME=/path/to/jdk-17

# 同步依赖
./gradlew --refresh-dependencies
```

3. **构建运行**
```bash
# Debug 构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

### 代码规范

详见 [CODING_STANDARDS.md](./docs/CODING_STANDARDS.md)

**核心原则**：
- Kotlin 优先，遵循官方风格指南
- 使用 ViewBinding/Compose
- 异步操作使用 Coroutines
- 错误处理使用 Result 类型
- 日志使用统一 TAG

### 配置参数说明

核心配置在 `AgentConfiguration.kt` 中：

**执行参数**：
- `maxSteps: Int = 100` - 最大执行步数
- `stepDelayMs: Long = 160L` - 步骤间延迟
- `useBackgroundVirtualDisplay: Boolean = false` - 是否使用虚拟屏
- `useShizukuInteraction: Boolean = false` - 是否使用 Shizuku

**模型参数**：
- `maxModelRetries: Int = 3` - 模型调用重试次数
- `temperature: Float = 0.0f` - 温度参数
- `maxTokens: Int = 4096` - 最大 token 数

**截图优化**：
- `screenshotCompressionQuality: Int = 85` - 压缩质量
- `screenshotMaxSizeKB: Int = 150` - 最大体积
- `enableScreenshotCache: Boolean = true` - 启用缓存

完整参数说明见代码注释。

---

## 🔍 问题解决

### 快速查问题索引

| 现象 | 可能原因 | 解决方案 | 章节 |
|------|---------|---------|------|
| 编译失败 | JDK 版本不对 | 使用 JDK 17 | [环境配置](#环境要求) |
| Gradle 同步失败 | 网络问题 | 配置代理 | [网络配置](#网络配置) |
| 虚拟屏无法启动 | Shizuku 未授权 | 检查 Shizuku 权限 | [Shizuku 配置](#shizuku-配置) |
| 语音识别无响应 | 模型文件缺失 | 下载 Sherpa 模型 | [语音配置](#语音配置) |
| 自动化执行失败 | 无障碍服务未开启 | 开启无障碍服务 | [权限配置](#权限配置) |
| API 调用失败 | API Key 无效 | 检查 API Key 配置 | [API 配置](#api-配置) |

### 常见问题详解

#### 环境配置

**Q: Gradle 同步失败，提示无法解析依赖**

A: 配置代理或使用国内镜像：
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}
```

**Q: 编译时提示 JDK 版本错误**

A: 确保使用 JDK 17：
```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-17

# macOS/Linux
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
```

#### 网络配置

**Q: API 调用超时或失败**

A: 检查网络连接和 API 配置：
1. 确认 API Key 有效
2. 检查网络连接
3. 如需使用代理，在 `AutoGlmClient.kt` 中配置

当前 API 端点：`https://open.bigmodel.cn/api/paas/v4/`

**自定义端点**：支持通过 `baseUrl` 参数配置自定义 API 端点（如 DeepSeek、Moonshot 等 OpenAI 兼容 API）。

#### Shizuku 配置

**Q: 虚拟屏无法启动**

A: 检查 Shizuku 配置：
1. 安装 Shizuku App
2. 启动 Shizuku 服务（ADB 或 Root 模式）
3. 在 Aries AI 中授权 Shizuku 权限
4. 在自动化设置中启用 Shizuku 交互

**Q: Shizuku 权限被拒绝**

A: 重新授权：
1. 打开 Shizuku App
2. 找到 Aries AI
3. 重新授权

#### 语音配置

**Q: 语音识别无响应**

A: 检查模型文件：
1. 确认 `app/src/main/assets/sherpa-models/` 目录存在
2. 下载模型文件（见 `sherpa-models/README.md`）
3. 确认 JNI 库存在于 `jniLibs/arm64-v8a/`

**Q: 语音识别准确率低**

A: Sherpa-ncnn 支持中英双语，但在嘈杂环境下准确率会下降。建议：
- 在安静环境使用
- 说话清晰、语速适中
- 避免方言或口音过重

#### 权限配置

**Q: 自动化执行失败，提示权限不足**

A: 检查必需权限：
1. 无障碍服务 - 用于 UI 操作
2. 悬浮窗权限 - 用于进度显示
3. 录音权限 - 用于语音输入（可选）

首次启动会弹出权限引导面板。

#### API 配置

**Q: 如何配置自定义 API 端点？**

A: 在调用 `AutoGlmClient` 时传入 `baseUrl` 参数：
```kotlin
AutoGlmClient.sendChatResult(
    apiKey = "your-api-key",
    messages = messages,
    baseUrl = "https://your-custom-endpoint.com/v1/",
    model = "your-model-name"
)
```

支持任何 OpenAI 兼容的 API 端点。

---

## 📚 版本历史

### 版本快照

| 版本 | versionCode | 发布日期 | 主要功能 | 状态 |
|------|-------------|---------|---------|------|
| **1.4.3** | 18 | 2026-04-09 | Stream WebView深度重构、UX Program | 当前 |
| 1.4.0 | 16 | 2026-02-28 | 架构优化、配置统一、文档重构 | 稳定 |
| 1.3.2 | 15 | 2026-02-25 | Shizuku 优化、OpenAI 兼容 API | 稳定 |

完整版本历史见 [更新记录](#更新记录)。

### 更新记录

#### 2026-05-17 - 开发状态整合与流式 Markdown 优化

**文档整合**：
- 将近期提交审查、当前开发重点和验证状态合并到本开发文档，避免 `docs/` 下出现临时状态文档。
- 后续 Wiki 同步以本文件作为主开发文档入口，专题文档继续保留在 `docs/` 目录。

**主页 Markdown 渲染**：
- 主页流式回复使用渲染缓冲区和 `safeMarkdown` 快照，减少裸 Markdown 到最终渲染的跳变。
- 流式阶段保留代码块形态但禁用高亮，最终消息再启用完整 Markdown/CodeBlock 渲染。

**验证状态**：
- 已记录建议验证命令。
- 当前本机缺少 `JAVA_HOME`/`java`，Gradle 验证需配置 JDK 后执行。

#### 2026-04-09 - v1.4.3 beta (预发布版)

**核心重构**：
- **流式思考内容深度重构 (Stream WebView Render)**：优化 `StreamRenderHelper` 的流处理链路，支持复杂 Markdown 和 `<think>` 标签拦截展示，修复异常收缩和乱码渲染导致的崩溃现象。

**新增功能**：
- 新增「用户体验改进计划（UX Program）」入口，默认关闭，并同步补充隐私说明。
- 在关于页链路中补充更新检查与版本信息展示能力。

**修复与优化**：
- 修复若干导致崩溃的潜在问题，提升预发布版稳定性。

#### 2026-02-28 - v1.4.0

**架构优化**：
- 重构配置系统，统一到 `AgentConfiguration`
- 优化代码结构，提升可维护性
- 完善注释和文档

**文档更新**：
- 重构开发文档，采用混合结构
- 新增快速导航和问题索引
- 简化历史内容，突出当前状态

**配置统一**：
- 所有配置参数集中管理
- 提供合理的默认值
- 支持灵活的参数覆盖

#### 2026-02-25 - v1.3.2

**Shizuku 交互优化**：
- 优化权限请求流程
- 改进服务连接稳定性
- 增强虚拟屏协同机制

**第三方 API 支持**：
- 新增 OpenAI 兼容 API 支持
- 支持自定义 API 端点配置
- 统一的多模型接入接口

**网站优化**：
- 移动端响应式布局优化
- 文档中心适配
- 提升用户体验

#### 2026-02-16 - v1.3.0

**虚拟屏功能**：
- 后台虚拟屏执行
- 实时预览悬浮窗
- 分辨率动态对齐
- 状态实时刷新

**输入源管理**：
- 优化文本输入流程
- 改进焦点管理
- 增强输入验证

**自动化优化**：
- 应用直启加速（~1s）
- 敏感检测优化
- 进度百分比显示

---

## 🗂️ 历史归档

### 已解决问题归档

以下问题已在历史版本中解决，归档以供参考：

#### 小窗返回主界面闪烁（v1.3.0 已修复）

**现象**：从小窗返回主界面时出现明显闪烁。

**根因**：动画时序和窗口移除顺序不当。

**解决方案**：
- 优化动画时序，增加 120ms 延迟
- 使用 ACK 机制确认返回
- 动画完成后再移除窗口

#### 输入框失焦（v1.3.0 已修复）

**现象**：点击输入框后光标闪动，无法输入。

**根因**：`ScrollView.fullScroll(FOCUS_DOWN)` 抢走焦点。

**解决方案**：改用 `smoothScrollTo()` 避免焦点抢夺。

#### Drawer 右滑无响应（v1.3.0 已修复）

**现象**：主界面右滑无法打开侧边栏。

**根因**：缺少边缘检测逻辑。

**解决方案**：在 `dispatchTouchEvent` 中实现右滑检测。

#### 消息不同步（v1.3.0 已修复）

**现象**：小窗和主界面消息不同步。

**根因**：前缀解析不一致，监听器清理过早。

**解决方案**：
- 统一前缀解析（兼容 `"我:"` 和 `"我: "`）
- 增加待同步队列
- 优化监听器生命周期

#### Gradle 同步失败（v1.3.0 已修复）

**现象**：无法解析 ktlint 插件。

**根因**：插件仓库不可用。

**解决方案**：移除 ktlint 插件依赖。

更多历史问题见 Git 提交记录。

### 历史技术方案

#### Vosk 语音识别（已弃用）

**使用时期**：v1.0.x - v1.2.x

**弃用原因**：
- 模型体积大（~50MB）
- 识别速度慢
- 准确率不如 Sherpa-ncnn

**替代方案**：Sherpa-ncnn（v1.3.0+）

**参考资料**：
- Vosk 官网：https://alphacephei.com/vosk/
- 中文模型：vosk-model-cn

如需了解 Vosk 集成细节，可查看 v1.2.x 分支代码。

---

## 📖 知识库

### 代码入口索引

**主要文件**：

| 文件 | 功能 | 行数 |
|------|------|------|
| `MainActivity.kt` | 主对话界面 | ~1200 |
| `UiAutomationAgent.kt` | 自动化 Agent | ~800 |
| `AutoGlmClient.kt` | API 客户端 | ~600 |
| `ActionExecutor.kt` | 动作执行器 | ~500 |
| `AgentConfiguration.kt` | 配置管理 | ~400 |
| `ShizukuBridge.kt` | Shizuku 集成 | ~300 |
| `VirtualDisplayController.kt` | 虚拟屏管理 | ~400 |

**核心目录**：

```
app/src/main/java/com/ai/phoneagent/
├── core/                    # 核心模块
│   ├── agent/              # Agent 相关
│   ├── executor/           # 动作执行
│   ├── parser/             # 动作解析
│   ├── config/             # 配置管理
│   ├── tools/              # 工具集
│   └── utils/              # 工具类
├── helper/                 # 辅助类
├── net/                    # 网络层
├── speech/                 # 语音识别
├── ui/                     # UI 组件
└── vdiso/                  # 虚拟屏引擎
```

### 依赖说明

**核心依赖**：
- Kotlin Coroutines - 异步编程
- OkHttp/Retrofit - 网络请求
- JetBrains Markdown / Compose Markdown Renderer - Markdown 解析与渲染
- Sherpa-ncnn - 语音识别
- Shizuku - 系统级权限

**版本要求**：
- Kotlin: 2.2.21
- Compose BOM: 2024.12.01
- kotlinx.serialization-json: 1.7.3
- JetBrains Markdown: 0.7.3
- Compose Markdown Renderer: 0.27.0

### 常用应用包名

```kotlin
val commonApps = mapOf(
    "微信" to "com.tencent.mm",
    "支付宝" to "com.eg.android.AlipayGphone",
    "抖音" to "com.ss.android.ugc.aweme",
    "美团" to "com.sankuai.meituan",
    "12306" to "com.MobileTicket",
    "QQ" to "com.tencent.mobileqq",
    "淘宝" to "com.taobao.taobao",
    "京东" to "com.jingdong.app.mall"
)
```

完整映射见 `AppPackageMapping.kt`。

### 参考资源

**官方文档**：
- Android 开发者文档：https://developer.android.com
- Kotlin 官方文档：https://kotlinlang.org/docs
- Shizuku 文档：https://shizuku.rikka.app

**开源项目**：
- Open-AutoGLM：https://github.com/THUDM/AutoGLM
- Sherpa-ncnn：https://github.com/k2-fsa/sherpa-ncnn

**社区资源**：
- 项目 GitHub：[待补充]
- 问题反馈：[待补充]

---

## 🚀 路线图

### 近期计划（Q1 2026）

**功能优化**：
- [ ] 虚拟屏输入源管理优化
- [ ] 复杂 UI 场景识别准确率提升
- [ ] 长时间任务稳定性增强

**性能优化**：
- [ ] 截图压缩算法优化
- [ ] 模型推理速度提升
- [ ] 内存占用优化

**用户体验**：
- [ ] 多模型配置界面
- [ ] 任务模板系统
- [ ] 历史任务复用

### 中期计划（Q2 2026）

**新功能**：
- [ ] 本地小模型支持
- [ ] 多设备协同
- [ ] 云端任务同步

**生态建设**：
- [ ] 插件系统
- [ ] 社区任务市场
- [ ] 开发者文档完善

### 长期愿景

打造开放、智能、易用的 Android 自动化平台，让每个人都能轻松实现手机自动化。

---

## 📝 贡献指南

### 如何贡献

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

### 代码审查

所有 PR 需要通过：
- 代码风格检查
- 单元测试
- 功能测试
- 文档更新

详见 [CONTRIBUTING.md](./CONTRIBUTING.md)

---

## 📄 许可证

本项目采用 AGPL-3.0 许可证。详见 [LICENSE](./LICENSE)。

---

## 📞 联系方式

- 开发者：ZG0704666
- 邮箱：zhangyongqi@aries-agent.com

---

**文档版本**：v2.1
**最后更新**：2026-05-17
**维护者**：ZG0704666
