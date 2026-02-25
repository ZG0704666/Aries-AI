# Aries AI 🚀

<div align="center">

<img src="Aries-site/assets/favicon_rounded.png" width="120" height="120" alt="Aries AI Logo">

**让大模型在 Android 上丝滑执行 UI 自动化任务的推理加速引擎**

[![AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android%2011%20--%2036-brightgreen.svg)]()
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-orange.svg)]()
[![GitHub Stars](https://img.shields.io/github/stars/ZG0704666/Aries-AI?style=social)](https://github.com/ZG0704666/Aries-AI)

[![QQ群](https://img.shields.io/badge/QQ群-746439473-12B7F5?logo=tencent-qq&logoColor=white)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=&authKey=&noverify=0&group_code=746439473)
[![Email](https://img.shields.io/badge/Email-zhangyongqi@njit.edu.cn-EA4335?logo=gmail&logoColor=white)](mailto:zhangyongqi@njit.edu.cn)
[![Issues](https://img.shields.io/badge/Issues-欢迎反馈-green?logo=github)](https://github.com/ZG0704666/Aries-AI/issues)

</div>

---

## ✨ 核心特性

| 特性 | 说明 |
|------|------|
| **虚拟屏幕后台执行** | 创建独立虚拟屏幕运行自动化任务，不影响主屏幕，焦点输入完全隔离 |
| **实时预览窗口** | 浮动窗口实时显示虚拟屏幕操作画面，支持最小化/恢复，进度可视 |
| **智能截图压缩** | 质量 85%，最大 150KB，80% 缩放，平衡清晰度与传输体积 |
| **流式早停机制** | 识别可执行动作后提前结束，减少等待时间 |
| **并行状态采集** | 截图与 UI 树同时获取，避免串行等待 |
| **Tap+Type 合并** | 连续点击+输入操作合并执行，减少交互轮次 |
| **智能缓存策略** | 页面截图缓存复用，TTL 过期机制防止数据过期 |
| **上下文管理** | 基于 Token 估算的对话截断，保持上下文在合理范围 |

---

## 📊 性能数据

基于典型 UI 自动化任务的实测结果：

| 指标 | 优化前 | 优化后 |
|------|--------|--------|
| 平均响应时间 | ~3.2s | ~1.8s |
| 截图传输大小 | ~250KB | ~85KB |
| UI 树处理 | ~5000 chars | ~3000 chars |
| 状态采集方式 | 串行 | 并行 |
| 任务成功率 | ~82% | ~94% |

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Aries AI 架构                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐               │
│  │  UI 层      │   │  控制层      │   │  工具层      │               │
│  │Automation   │   │UiAutomation │   │AppPackage   │               │
│  │ActivityNew  │   │   Agent     │   │  Manager    │               │
│  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘               │
│         │                 │                 │                       │
│         └────────────┬────┴────────────────┘                       │
│                      ▼                                              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                     核心引擎层                                 │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐           │  │
│  │  │   配置中心   │  │   动作解析   │  │   动作执行   │           │  │
│  │  │AgentConfig  │  │ActionParser │  │ActionExecutor│           │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘           │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐           │  │
│  │  │   提示模板   │  │   状态缓存   │  │   截图管理   │           │  │
│  │  │PromptTemplate│  │ScreenshotMgr│  │ScreenshotCache│          │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘           │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                      ▼                                              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                     模型接入层                                 │  │
│  │  ┌─────────────────────────────────────────────────────────┐  │  │
│  │  │              AutoGlmClient 多模型网关                    │  │  │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │  │  │
│  │  │  │ 智谱 (Zhipu)│  │ OpenAI 兼容 │  │  流式支持    │     │  │  │
│  │  │  └─────────────┘  └─────────────┘  └─────────────┘     │  │  │
│  │  └─────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                      ▼                                              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                     系统服务层                                 │  │
│  │  ┌─────────────────┐  ┌─────────────────┐                   │  │
│  │  │AccessibilitySvc │  │  浮动窗 (Overlay)│                   │  │
│  │  │  无障碍服务      │  │  实时进度展示    │                   │  │
│  │  └─────────────────┘  └─────────────────┘                   │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 核心模块说明

```
┌─────────────────────────────────────────────────────────────┐
│                    加速策略实现                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1️⃣ 输入优化层                                               │
│     ├── ScreenshotManager (截图缓存 + 节流 + 压缩)           │
│     ├── ScreenshotCache (TTL + Key 过期策略)                 │
│     └── ActionUtils.truncateUiTree (UI 树截断)               │
│                                                              │
│  2️⃣ 推理加速层                                               │
│     ├── 流式早停 (shouldStop callback)                       │
│     ├── 并行采集 (coroutine async/await)                     │
│     └── 上下文截断 (trimHistory)                             │
│                                                              │
│  3️⃣ 执行优化层                                               │
│     ├── Tap+Type 合并执行                                    │
│     ├── 智能应用预启动                                        │
│     └── 动作延迟动态映射                                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 快速开始

### 📦 普通用户（直接安装）

可从官网或 QQ 群下载安装包，直接安装使用：

1. 下载 APK 安装包
2. 安装到 Android 设备
3. 开启无障碍服务
4. 配置 API Key 开始使用

### 🎙️ 语音模型下载（针对二次开发者，普通用户无需下载）

如需使用**离线语音识别**功能，请下载语音模型文件：

> **📥 下载地址**: [Google Drive - 语音模型](https://drive.google.com/drive/folders/1LnebW8G1wmpMeGIEQunAYMnmT1BpXmCD?usp=sharing)

**说明**：
- 由于模型文件较大，GitHub 无法直接上传
- 开发者需自行从 Google Drive 下载模型文件
- 下载后将模型文件放置到应用指定目录：Aries-AI\app\src\main\assets\sherpa-models
- 语音识别功能基于 Sherpa-ncnn 离线引擎，无需联网即可使用

**模型文件说明**：
- 包含中文语音识别模型
- 支持离线实时语音转文字
- 适用于语音输入任务指令

### 💻 二次开发（从源码构建）

如需修改代码或贡献开发，请参考详细文档：

| 文档 | 说明 |
|------|------|
| [BUILDING.md](./BUILDING.md) | 环境要求、依赖安装、项目配置、编译运行 |
| [CODING_STANDARDS.md](./CODING_STANDARDS.md) | 代码规范、命名规则、注释要求 |
| [GIT_WORKFLOW.md](./GIT_WORKFLOW.md) | Git 使用规范、分支管理、提交规范 |

### 📚 文档中心（/docs）

为提升前后端协作与 Agent 辅助编码效率，新增以下核心文档：

| 文档 | 说明 |
|------|------|
| [docs/frontend-readme.md](./docs/frontend-readme.md) | 前端启动、联调、环境变量与协作规范 |
| [docs/api-interface-spec.md](./docs/api-interface-spec.md) | 前后端接口契约、错误码、分页与版本规范 |
| [docs/backend-technical-solution.md](./docs/backend-technical-solution.md) | 后端技术架构、工程治理与发布方案 |

```bash
# 克隆项目
git clone https://github.com/ZG0704666/Aries-AI.git
cd Aries-AI

# 构建 Debug APK
.\gradlew assembleDebug

# 构建 Release APK
.\gradlew assembleRelease

# 安装到设备
adb install app/build/outputs/apk/release/app-release.apk
```

> **提示**：API Key 可在 [智谱开放平台](https://open.bigmodel.cn/) 注册后获取，在应用内配置。

---

## 📖 使用示例

配置完 API Key 后，即可在主界面使用 Aries AI：

### 三种使用方式

#### 🎙️ 主界面 AI 聊天

在主界面与 AI 进行自然对话，AI 会根据您的需求操控手机完成各项任务。

- **语音输入**：点击麦克风图标直接语音指令
- **文本输入**：在输入框中描述您的需求

#### 🤖 侧边栏自动化功能

点击侧边栏 **"自动化"** 进入自动化任务界面，可选择预设任务或输入自定义指令：

| 预设任务 | 示例 |
|---------|------|
| 餐厅预订 | "打开大众点评帮我预订一个明天中午11点的周围火锅店的位置，4个人" |
| 火车订票 | "打开12306订一张2月5日南京到北京的票，选最便宜的" |
| 机票预订 | "打开航旅纵横订一张2月5日从南京飞往成都的机票" |

#### 🖥️ 虚拟屏幕模式（新功能 v1.3.0+）

独立创建虚拟屏幕在后台运行自动化任务，同时不影响主屏幕的正常使用：

- **后台独立运行**：虚拟屏幕在后台执行任务，主屏幕完全独立，可继续使用其他应用
- **实时预览**：小窗口实时显示虚拟屏幕的操作画面，随时了解任务执行进度
- **灵活切换**：可随时最小化/恢复虚拟屏幕预览窗口，不打断主屏幕工作流
- **多任务支持**：支持同时启动虚拟屏幕执行后台任务，主屏幕进行其他操作
- **焦点隔离**：虚拟屏幕与主屏幕输入完全隔离，互不干扰

**使用场景**：
- 订票/订座时，AI 在虚拟屏幕后台操作，不影响你在主屏幕查看其他信息
- 后台爬虫/数据采集任务，主屏幕可继续日常使用
- 批量操作多个应用，虚拟屏幕处理，主屏幕不受打扰

---

## 🛠️ 核心 API

### UiAutomationAgent

```kotlin
class UiAutomationAgent(config: AgentConfiguration = AgentConfiguration.DEFAULT) {
    suspend fun run(
        apiKey: String,
        model: String,
        task: String,
        service: PhoneAgentAccessibilityService,
        control: Control = NoopControl,
        onLog: (String) -> Unit,
    ): AgentResult
}
```

### AgentConfiguration

```kotlin
data class AgentConfiguration(
    val maxSteps: Int = 100,              // 最大执行步数
    val maxTokens: Int? = 4096,           // 最大输出 token
    val maxContextTokens: Int = 30000,    // 上下文 token 上限
    val stepDelayMs: Long = 160L,         // 每步延迟
    val maxModelRetries: Int = 3,         // 模型重试次数
    val maxParseRepairs: Int = 2,         // 解析修复次数
    val maxActionRepairs: Int = 1,        // 动作修复次数
    val screenshotCompressionQuality: Int = 85,
    val screenshotMaxSizeKB: Int = 150,
    val enableScreenshotCache: Boolean = true,
    val parallelScreenshotAndUi: Boolean = true,
    val useStreamingWithEarlyStop: Boolean = true,
    // ... 更多配置
)
```

### AgentResult

```kotlin
data class AgentResult(
    val success: Boolean,
    val message: String,   // 完成消息或失败原因
    val steps: Int,        // 实际执行步数
)
```

---

## 🧪 测试

```bash
# 运行单元测试
.\gradlew test

# 运行 lint 检查
.\gradlew lint
```

---

## 📦 依赖技术

| 类别 | 技术 | 用途 |
|------|------|------|
| 网络 | OkHttp + Retrofit | HTTP 客户端 |
| JSON | Gson | 序列化/反序列化 |
| 异步 | Kotlin Coroutines | 协程并发 |
| 渲染 | Markwon | Markdown 显示 |
| 语音 | Sherpa-ncnn | 离线语音识别 |
| 架构 | AndroidX Lifecycle | 生命周期管理 |

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建分支 (`git checkout -b feature/AmazingFeature`)
3. 提交改动 (`git commit -m 'Add some AmazingFeature'`)
4. 推送分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📄 开源协议

本项目基于 **AGPL-3.0-only** 开源协议发布。

```
Copyright (c) 2025-2026 ZG0704666

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.
```

**注意**: 如果您将本项目作为网络服务提供，您必须向用户公开完整的源代码。

---

## 📧 联系方式

- **作者**: ZG0704666
- **邮箱**: zhangyongqi@njit.edu.cn
- **项目**: [https://github.com/ZG0704666/Aries-AI](https://github.com/ZG0704666/Aries-AI)

---

<div align="center">

**如果这个项目对你有帮助，欢迎 ⭐ Star 支持！**

</div>
