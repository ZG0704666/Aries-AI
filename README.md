<div align="center">

<img src="Aries-site/assets/favicon_rounded.png" width="140" alt="Aries AI">

# Aries AI

**开源 Android AI 自动化引擎**

当前开发版本：**2.0.0 Alpha**（versionCode 21）。安装包版本以 [GitHub Releases](https://github.com/ZG0704666/Aries-AI/releases) 为准。

用自然语言描述目标，让 AI 理解手机界面、规划步骤并执行操作。

[![License](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2011+-green.svg)](#-快速开始)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-purple.svg)](./gradle/libs.versions.toml)
[![Stars](https://img.shields.io/github/stars/ZG0704666/Aries-AI?style=social)](https://github.com/ZG0704666/Aries-AI)

[快速开始](#-快速开始) • [核心特性](#-核心特性) • [技术文档](./Aries-site/docs/TECHNICAL_OVERVIEW.md) • [FAQ](./Aries-site/docs/FAQ.md)

</div>

---

## 🎯 项目简介

Aries AI 是一个开源的 Android UI 自动化引擎，通过接入大语言模型，让 AI 理解屏幕内容并自动执行复杂任务。

**核心亮点**：
- 🖥️ **虚拟屏自动化**：通过独立显示、焦点管理与定向输入，减少后台任务对主屏操作的干扰
- ⚡ **执行链路可配置**：支持截图缓存、流式响应与异步输入等自动化能力；实际耗时受设备、模型和网络影响
- 🔓 **完全开源**：代码透明，可自由定制和扩展
- 🔌 **模型灵活**：提供第三方 OpenAI 兼容 API、Aries API 和本地 MNN 三种接入模式
- 🎙️ **对话与语音**：支持流式回复、会话历史、附件输入与 Sherpa-ncnn 离线语音识别

**适用场景**：跨应用信息查询、重复界面操作、预订流程辅助和应用测试。任务完成情况取决于模型能力、目标应用和设备环境。

---

## ✨ 核心特性

### 虚拟屏与输入控制

Aries AI 将任务运行、画面采集和输入注入组织到虚拟屏执行链路中，并提供实时预览：

| 特性 | 说明 |
|------|------|
| **虚拟屏焦点管理** | 使用焦点恢复与定向输入机制；实际隔离效果取决于设备和系统能力 |
| **OpenGL 分发** | 同时支持截图和预览，无需切换 Surface |
| **截图检测** | 检测并等待非黑帧，降低空白画面对模型判断的影响 |
| **输入法协调** | 配合输入法策略与焦点恢复机制，处理部分设备上的输入冲突 |

> 虚拟屏隔离和输入定向能力依赖 Android 版本、ROM、Shizuku 授权及设备实现；请以目标设备上的实际验证为准。

### 执行与对话体验

| 能力 | 实现方式 |
|------|----------|
| 截图处理 | 支持压缩、缓存与节流，减少重复采集与传输 |
| 流式响应 | 增量展示回复，并支持自动化动作的流式解析 |
| 状态采集 | 结合截图和 UI 状态，为模型提供当前页面上下文 |
| 输入执行 | 按执行模式选择无障碍、Shizuku 或虚拟屏输入路径 |
| 对话管理 | 保存会话历史，支持 Markdown、代码块和附件展示 |
| 离线语音 | 通过 Sherpa-ncnn 在设备端识别语音，需要准备语音模型 |

实际响应时间受设备性能、画面内容、模型服务和网络影响；此处列出实现能力，不作为速度或成功率基准。

### 三种模型接入模式

| 模式 | 配置方式 | 适用场景 |
|------|----------|----------|
| 第三方 API | 配置 Base URL、模型名称和 API Key | 接入提供 OpenAI 兼容聊天接口的服务或自部署模型 |
| Aries API | 登录 Aries 账号并使用平台提供的模型 | 使用集成的账号与模型配置流程 |
| 本地 MNN | 下载模型文件并检查就绪状态 | 在设备端运行模型，当前预置为 Qwen3.5-9B MNN |

接口兼容不等于能力一致：屏幕理解需要模型支持图像输入，工具调用也取决于服务端实现。原生 Claude、Gemini 等接口不能仅凭修改模型名直接接入，需使用兼容端点或适配服务。本地模型首次下载需要网络，运行效果取决于设备内存和 native 运行环境。

详见 [多模型接入总览](./Aries-site/docs/多模型接入/多模型接入总览.md)、[本地推理](./Aries-site/docs/多模型接入/本地推理%20(MNN).md) 和 [离线语音识别](./Aries-site/docs/对话系统/离线语音识别%20(Sherpa-ncnn).md)。

---

## 🚀 快速开始

### 用户安装

**系统要求**：Android 11（API 30）及以上；当前构建仅打包 `arm64-v8a` native 库，需使用 ARM64 设备。

1. **下载安装**
   - [Releases](https://github.com/ZG0704666/Aries-AI/releases) 下载最新 APK
   - 或加入 [QQ 群 746439473](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=&authKey=&noverify=0&group_code=746439473) 获取

2. **配置环境**
   - 开启无障碍服务权限
   - 安装 [Shizuku](https://shizuku.rikka.app/)（虚拟屏功能必需）
   - 在设置中选择模型模式；第三方模式填写 Base URL、模型名称和 API Key，并执行连通性检查
   - 按实际使用功能授予悬浮窗、麦克风或通知权限

3. **开始使用**
   - 语音或文本输入任务指令
   - 先用打开应用、查找信息等简短任务验证执行链路
   - 开启虚拟屏幕后台运行

### 开发者构建

```bash
# 克隆仓库
git clone --recurse-submodules https://github.com/ZG0704666/Aries-AI.git
cd Aries-AI

# 构建项目
./gradlew assembleDebug

# 安装到已连接设备
./gradlew installDebug
```

已克隆的仓库可执行 `git submodule update --init --recursive` 补齐 native 子模块。Windows PowerShell 使用 `./gradlew.bat` 替代 `./gradlew`。

| 项目 | 当前配置 |
|------|----------|
| JDK | CI 使用 JDK 17 |
| Android SDK | compileSdk / targetSdk 36，minSdk 30 |
| Build Tools | CI 安装 36.0.0 |
| Gradle / AGP | Wrapper 9.4.1 / AGP 9.2.1 |
| Kotlin | 2.2.21；Java / Kotlin 字节码目标为 11 |
| Native 工具链 | NDK 28.2.13676358、CMake 3.22.1 |
| ABI / 应用 ID | `arm64-v8a` / `com.ai.phoneagent` |

配置 Android SDK 路径后构建，Debug APK 输出为 `app/build/outputs/apk/debug/app-debug.apk`。上述版本来自 [应用构建配置](./app/build.gradle.kts)、[依赖版本目录](./gradle/libs.versions.toml) 和 [CI 工作流](./.github/workflows/ci.yml)。

详细构建指南请查看 [BUILDING.md](./Aries-site/docs/BUILDING.md)

---

## 📖 使用说明

### 三种使用方式

**1. 主界面对话**
```
"帮我在携程订一张明天北京到上海的机票，经济舱"
"在美团上找一家评分4.5以上的川菜馆，预订今晚7点，3个人"
```

**2. 预设任务**
- 餐厅预订
- 火车票预订
- 机票预订
- 自定义任务

**3. 虚拟屏幕模式**（推荐）
- 点击"虚拟屏幕"按钮启动
- 实时预览窗口显示执行过程
- 主屏幕可继续使用其他应用
- 焦点和输入隔离效果需在目标设备上验证

### 开发集成

自动化入口为 `UiAutomationAgent`，由应用提供上下文、偏好仓储、悬浮层、提示词和本地推理等依赖。它是应用内部组件，集成时应参考当前构造函数与依赖注入配置。

- [Agent 主循环](./Aries-site/docs/自动化引擎/自动化%20Agent%20主循环.md)：任务调度、模型请求与动作执行。
- [配置管理](./Aries-site/docs/自动化引擎/配置管理%20(AgentConfiguration).md)：执行步数、截图、重试和流式响应配置。
- [依赖注入](./Aries-site/docs/技术架构/DI%20依赖注入%20(Koin).md)：组件创建与依赖关系。

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                      Aries AI                            │
├─────────────────────────────────────────────────────────┤
│  UI 层 → 控制层 → 核心引擎层 → 模型接入层               │
│                                                          │
│  虚拟屏隔离层（核心技术）：                              │
│  ├─ VirtualDisplayController（统一入口）                │
│  ├─ ShizukuVirtualDisplayEngine（核心引擎）             │
│  ├─ VdGlFrameDispatcher（OpenGL 分发）                  │
│  └─ InputHelper（输入注入）                              │
│                                                          │
│  系统服务层：无障碍服务 + Shizuku 权限管理              │
└─────────────────────────────────────────────────────────┘
```

**核心技术**：
- 虚拟屏焦点管理与定向输入（行为依赖设备和系统兼容性）
- OpenGL 分发架构（同时支持截图和预览）
- 智能截图策略（自动等待非黑帧）
- 输入法策略与焦点恢复（缓解输入冲突）
- 按系统能力适配虚拟屏创建和输入路径（兼容性需实机验证）

> 📚 **详细技术文档**：[TECHNICAL_OVERVIEW.md](./Aries-site/docs/TECHNICAL_OVERVIEW.md)

---

## 🛠️ 开发指南

### 项目结构

```text
Aries-AI/
├── app/                       # Android 应用、Agent、模型接入与 native 组件
├── core/
│   ├── common/                # 通用能力
│   ├── designsystem/          # 设计系统
│   ├── prompt/                # 提示词模块
│   └── shizuku/               # Shizuku 集成
├── feature/
│   ├── settings/              # 设置模块
│   └── updates/               # 更新模块
├── Aries-site/                # 官网与文档中心
│   └── docs/                  # 中文分类文档与英文兼容入口
├── .github/workflows/         # CI 工作流
└── settings.gradle.kts        # 模块与依赖仓库配置
```

### 开发文档

| 文档 | 说明 |
|------|------|
| [文档中心](./Aries-site/docs-center.html) | 中文分类导航、专题说明和兼容入口 |
| [BUILDING.md](./Aries-site/docs/BUILDING.md) | 环境配置、依赖安装、编译运行 |
| [CODING_STANDARDS.md](./Aries-site/docs/CODING_STANDARDS.md) | 代码规范、命名规则、注释要求 |
| [GIT_WORKFLOW.md](./Aries-site/docs/GIT_WORKFLOW.md) | Git 使用规范、分支管理、提交规范 |
| [TECHNICAL_OVERVIEW.md](./Aries-site/docs/TECHNICAL_OVERVIEW.md) | 核心技术实现与优势 ⭐ |

### 技术栈

- **语言**：Kotlin 2.2.21
- **构建**：Gradle Wrapper 9.4.1 + AGP 9.2.1
- **异步**：Kotlin Coroutines
- **权限管理**：Shizuku（系统级权限）
- **界面**：Jetpack Compose + Material 3
- **图形处理**：OpenGL ES 3.0+
- **虚拟屏**：VirtualDisplay + SurfaceTexture
- **存储与依赖注入**：Room + DataStore + Koin
- **设备端模型**：MNN 推理 + Sherpa-ncnn 语音识别

### 检查与测试

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
# 需要已连接设备或模拟器
./gradlew connectedAndroidTest
```

[Android CI](./.github/workflows/ci.yml) 构建 Debug APK、检查 Sherpa JNI 库、执行单元测试和 Lint，并上传构建产物与报告。设备测试需单独运行；CI 构建成功不代表所有 ROM 上的虚拟屏、输入法和录音行为均已验证。

### 文档维护

项目文档统一维护在 `Aries-site/docs/`，保留现有中文分类和英文兼容入口。新增专题时同步更新文档中心导航，核对源码、相对链接和 Mermaid 图表。

- [CI 流水线与测试](./Aries-site/docs/开发规范与工具/CI%20流水线与测试.md)

---

## 🤝 贡献指南

我们欢迎所有形式的贡献！

### 如何贡献

1. Fork 项目到你的 GitHub 账号
2. 创建分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

### 贡献类型

- 🐛 Bug 修复
- ✨ 新功能
- 📝 文档改进
- ⚡ 性能优化
- 🧪 测试用例

详细规范请查看 [贡献指南](./CONTRIBUTING.md) 和 [Git 工作流](./Aries-site/docs/GIT_WORKFLOW.md)。

---

## 📄 开源协议

本项目采用 [AGPL-3.0](LICENSE) 协议开源。

你可以依照协议使用、修改和分发本项目。分发修改版本、或让用户通过网络与修改版本交互时，应遵守 AGPL 对许可证保留和对应源代码提供的要求；具体以 [LICENSE](./LICENSE) 正文为准。第三方组件遵循各自许可证。

---

## 🌟 社区

### 获取帮助

- 💬 **QQ 群**：[746439473](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=&authKey=&noverify=0&group_code=746439473)
- 🐛 **问题反馈**：[GitHub Issues](https://github.com/ZG0704666/Aries-AI/issues)
- 💡 **功能建议**：[GitHub Discussions](https://github.com/ZG0704666/Aries-AI/discussions)
- 📧 **邮件联系**：zhangyongqi@njit.edu.cn

### 常见问题

> 💡 更多问题请查看 [完整 FAQ 文档](./Aries-site/docs/FAQ.md)

---

## 📊 项目状态

![GitHub stars](https://img.shields.io/github/stars/ZG0704666/Aries-AI?style=social)
![GitHub forks](https://img.shields.io/github/forks/ZG0704666/Aries-AI?style=social)
![GitHub issues](https://img.shields.io/github/issues/ZG0704666/Aries-AI)
![GitHub last commit](https://img.shields.io/github/last-commit/ZG0704666/Aries-AI)

---

<div align="center">

**如果这个项目对你有帮助，请给我们一个 ⭐ Star！**

Made with ❤️ by [ZG0704666](https://github.com/ZG0704666)

[返回顶部](#aries-ai)

</div>
