<div align="center">

<img src="Aries-site/assets/favicon_rounded.png" width="140" alt="Aries AI">

# Aries AI

**开源 Android AI 自动化引擎**

让普通 Android 手机也能实现 AI 自动化功能

[![License](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2011+-green.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)]()
[![Stars](https://img.shields.io/github/stars/ZG0704666/Aries-AI?style=social)](https://github.com/ZG0704666/Aries-AI)

[快速开始](#-快速开始) • [功能特性](#-功能特性) • [使用文档](#-使用文档) • [开发指南](#-开发指南) • [加入社区](#-社区)

</div>

---

## 🎯 项目简介

Aries AI 是一个开源的 Android UI 自动化引擎，让普通 Android 手机也能实现类似豆包手机的 AI 自动化功能。通过接入大语言模型，AI 可以理解屏幕内容并自动执行复杂任务。

**为什么选择 Aries AI**：
- 📱 **广泛兼容**：支持 Android 11+ 设备，无需特定硬件
- 🔓 **完全开源**：代码透明，可自由定制和扩展
- 🚀 **性能优化**：多项加速技术，响应时间 1.8s，成功率 94%
- 🖥️ **后台运行**：虚拟屏幕技术，不干扰主屏幕使用
- 🔌 **模型灵活**：兼容 OpenAI 接口，支持 20+ 种大模型

**典型应用场景**：
- 自动化订票、预订餐厅等日常任务
- 批量处理重复性操作
- 应用自动化测试
- 数据采集与爬虫

---

## ✨ 功能特性

### 核心功能

| 功能 | 说明 |
|------|------|
| **虚拟屏幕执行** | 在独立虚拟屏幕中运行任务，主屏幕完全不受影响，支持实时预览 |
| **多模型支持** | 兼容 OpenAI 接口标准，支持 20+ 种大模型（GLM、DeepSeek、Qwen 等） |
| **语音交互** | 支持语音输入任务指令，基于 Sherpa-ncnn 离线识别 |
| **预设任务** | 内置餐厅预订、火车票、机票等常用场景模板 |
| **完全开源** | 代码透明可审计，支持自定义扩展和二次开发 |

### 性能优化

<table>
<tr>
<td width="50%">

**输入优化**
- 智能截图压缩（85% 质量，最大 150KB）
- UI 树智能截断
- 截图缓存与复用（TTL 机制）

</td>
<td width="50%">

**推理加速**
- 流式早停机制
- 并行状态采集
- 上下文智能管理

</td>
</tr>
<tr>
<td width="50%">

**执行优化**
- Tap+Type 操作合并
- 智能应用预启动
- 动作延迟动态调整

</td>
<td width="50%">

**性能表现**
- 响应时间：3.2s → 1.8s
- 截图大小：250KB → 85KB
- 成功率：82% → 94%

</td>
</tr>
</table>

---

## 🚀 快速开始

### 用户安装

**系统要求**：Android 11+ 设备（无需特定品牌或型号）

1. **下载安装包**
   - 从 [Releases](https://github.com/ZG0704666/Aries-AI/releases) 下载最新 APK
   - 或加入 [QQ 群 746439473](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=&authKey=&noverify=0&group_code=746439473) 获取

2. **配置环境**
   - 安装到设备并开启无障碍服务权限
   - 配置 API Key（兼容 OpenAI 接口的任意服务商）
   - 推荐：[智谱 GLM](https://open.bigmodel.cn/)、[DeepSeek](https://platform.deepseek.com/)、[硅基流动](https://siliconflow.cn/) 等

3. **开始使用**
   - 语音或文本输入任务指令
   - 选择预设任务快速开始
   - 开启虚拟屏幕后台运行

### 开发者构建

```bash
# 克隆仓库
git clone https://github.com/ZG0704666/Aries-AI.git
cd Aries-AI

# 构建项目
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

**环境要求**：
- JDK 17+
- Android SDK 34+
- Gradle 8.0+

**可选配置**：
- 语音模型：从 [Google Drive](https://drive.google.com/drive/folders/1LnebW8G1wmpMeGIEQunAYMnmT1BpXmCD?usp=sharing) 下载，放置到 `app/src/main/assets/sherpa-models`

---

## 📖 使用文档

### 基础使用

**方式一：主界面对话**

直接与 AI 对话，描述你的需求：
```
"帮我在大众点评预订明天中午12点的火锅店，2个人"
"打开微信给张三发消息说明天见"
```

**方式二：预设任务**

选择内置任务模板，快速执行常见操作：
- 餐厅预订
- 火车票预订
- 机票预订
- 自定义任务

**方式三：虚拟屏幕模式**

后台独立运行，不影响主屏幕：
- 点击"虚拟屏幕"按钮启动
- 实时预览窗口显示执行过程
- 主屏幕可继续使用其他应用

### API 使用

```kotlin
// 1. 创建 Agent 实例
val agent = UiAutomationAgent(
    config = AgentConfiguration(
        maxSteps = 100,
        maxTokens = 4096,
        screenshotCompressionQuality = 85,
        enableScreenshotCache = true,
        useStreamingWithEarlyStop = true
    )
)

// 2. 执行任务
val result = agent.run(
    apiKey = "your-api-key",
    model = "glm-4.6v-flash",  // 主页默认模型，自动化界面默认使用 autoglm-phone
    task = "打开微信发消息给张三",
    service = accessibilityService,
    onLog = { log -> println(log) }
)

// 3. 处理结果
when {
    result.success -> println("✅ 任务完成：${result.message}")
    else -> println("❌ 任务失败：${result.message}")
}
```

**配置参数说明**：

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `maxSteps` | 最大执行步数 | 100 |
| `maxTokens` | 最大输出 token | 4096 |
| `maxContextTokens` | 上下文 token 上限 | 30000 |
| `screenshotCompressionQuality` | 截图压缩质量 | 85 |
| `enableScreenshotCache` | 启用截图缓存 | true |
| `useStreamingWithEarlyStop` | 启用流式早停 | true |

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                      Aries AI                            │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │  UI 层 (AutomationActivity)                    │    │
│  │  • 主界面交互  • 虚拟屏幕管理  • 实时预览      │    │
│  └────────────────┬───────────────────────────────┘    │
│                   │                                      │
│  ┌────────────────▼───────────────────────────────┐    │
│  │  控制层 (UiAutomationAgent)                    │    │
│  │  • 任务调度  • 状态管理  • 错误处理            │    │
│  └────────────────┬───────────────────────────────┘    │
│                   │                                      │
│  ┌────────────────▼───────────────────────────────┐    │
│  │  核心引擎层                                     │    │
│  │  ┌──────────────┐  ┌──────────────┐           │    │
│  │  │ 配置中心      │  │ 动作解析器    │           │    │
│  │  │ AgentConfig  │  │ ActionParser │           │    │
│  │  └──────────────┘  └──────────────┘           │    │
│  │  ┌──────────────┐  ┌──────────────┐           │    │
│  │  │ 动作执行器    │  │ 提示模板      │           │    │
│  │  │ActionExecutor│  │PromptTemplate│           │    │
│  │  └──────────────┘  └──────────────┘           │    │
│  │  ┌──────────────┐  ┌──────────────┐           │    │
│  │  │ 截图管理器    │  │ 缓存管理      │           │    │
│  │  │ScreenshotMgr │  │ CacheManager │           │    │
│  │  └──────────────┘  └──────────────┘           │    │
│  └────────────────┬───────────────────────────────┘    │
│                   │                                      │
│  ┌────────────────▼───────────────────────────────┐    │
│  │  模型接入层 (AutoGlmClient)                    │    │
│  │  • 兼容 OpenAI 接口标准                        │    │
│  │  • 支持 20+ 种大模型服务商                     │    │
│  │  • 流式响应支持                                │    │
│  └────────────────┬───────────────────────────────┘    │
│                   │                                      │
│  ┌────────────────▼───────────────────────────────┐    │
│  │  系统服务层                                     │    │
│  │  • 无障碍服务 (AccessibilityService)           │    │
│  │  • 浮动窗口 (Overlay)                          │    │
│  │  • 虚拟屏幕 (VirtualDisplay)                   │    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

**关键技术点**：
- **无障碍服务**：获取 UI 树结构和执行操作
- **虚拟屏幕**：创建独立显示环境，隔离主屏幕
- **协程并发**：并行采集截图和 UI 树，提升效率
- **流式处理**：边接收边解析，识别到动作立即执行

---

## 🛠️ 开发指南

### 项目结构

```
Aries-AI/
├── app/
│   ├── src/main/
│   │   ├── java/com/aries/ai/
│   │   │   ├── agent/          # 核心 Agent 逻辑
│   │   │   ├── ui/             # UI 界面
│   │   │   ├── service/        # 系统服务
│   │   │   ├── model/          # 数据模型
│   │   │   └── utils/          # 工具类
│   │   ├── assets/             # 资源文件
│   │   └── res/                # Android 资源
│   └── build.gradle.kts
├── docs/                       # 文档目录
│   ├── BUILDING.md            # 构建指南
│   ├── CODING_STANDARDS.md    # 代码规范
│   └── GIT_WORKFLOW.md        # Git 工作流
└── README.md
```

### 开发文档

| 文档 | 说明 |
|------|------|
| [BUILDING.md](./BUILDING.md) | 环境配置、依赖安装、编译运行 |
| [CODING_STANDARDS.md](./CODING_STANDARDS.md) | 代码规范、命名规则、注释要求 |
| [GIT_WORKFLOW.md](./GIT_WORKFLOW.md) | Git 使用规范、分支管理、提交规范 |
| [docs/frontend-readme.md](./docs/frontend-readme.md) | 前端开发指南 |
| [docs/api-interface-spec.md](./docs/api-interface-spec.md) | API 接口规范 |
| [docs/backend-technical-solution.md](./docs/backend-technical-solution.md) | 后端技术方案 |

### 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 1.9+ |
| 构建 | Gradle 8.0+ |
| 网络 | OkHttp + Retrofit |
| 异步 | Kotlin Coroutines |
| JSON | Gson |
| UI | AndroidX + Material Design |
| 语音 | Sherpa-ncnn |

### 测试

```bash
# 运行单元测试
./gradlew test

# 运行 Lint 检查
./gradlew lint

# 生成测试报告
./gradlew testDebugUnitTest --tests "*"
```

---

## 🤝 贡献指南

我们欢迎所有形式的贡献！无论是新功能、Bug 修复、文档改进还是问题反馈。

### 如何贡献

1. **Fork 项目** 到你的 GitHub 账号
2. **创建分支** (`git checkout -b feature/AmazingFeature`)
3. **编写代码** 并遵循项目代码规范
4. **提交更改** (`git commit -m 'Add some AmazingFeature'`)
5. **推送分支** (`git push origin feature/AmazingFeature`)
6. **提交 PR** 并描述你的更改

### 贡献类型

- 🐛 **Bug 修复**：发现并修复问题
- ✨ **新功能**：添加新的功能特性
- 📝 **文档**：改进文档和示例
- 🎨 **UI/UX**：优化界面和用户体验
- ⚡ **性能**：提升性能和效率
- 🧪 **测试**：添加或改进测试用例

### 行为准则

- 尊重所有贡献者
- 提供建设性的反馈
- 专注于对项目最有利的事情
- 保持友好和专业的态度

---

## 📄 开源协议

本项目采用 [AGPL-3.0](LICENSE) 协议开源。

**简单来说**：
- ✅ 可以自由使用、修改和分发
- ✅ 可用于商业和非商业用途
- ⚠️ 修改后分发必须保持相同协议
- ⚠️ 作为网络服务提供时必须公开源代码

详细条款请查看 [LICENSE](LICENSE) 文件。

---

## 🌟 社区

### 获取帮助

- 💬 **QQ 群**：[746439473](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=&authKey=&noverify=0&group_code=746439473)
- 🐛 **问题反馈**：[GitHub Issues](https://github.com/ZG0704666/Aries-AI/issues)
- 💡 **功能建议**：[GitHub Discussions](https://github.com/ZG0704666/Aries-AI/discussions)
- 📧 **邮件联系**：zhangyongqi@njit.edu.cn

### 贡献者

感谢所有为 Aries AI 做出贡献的开发者！

<a href="https://github.com/ZG0704666/Aries-AI/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=ZG0704666/Aries-AI" />
</a>

---

## 📊 项目状态

![GitHub stars](https://img.shields.io/github/stars/ZG0704666/Aries-AI?style=social)
![GitHub forks](https://img.shields.io/github/forks/ZG0704666/Aries-AI?style=social)
![GitHub issues](https://img.shields.io/github/issues/ZG0704666/Aries-AI)
![GitHub pull requests](https://img.shields.io/github/issues-pr/ZG0704666/Aries-AI)
![GitHub last commit](https://img.shields.io/github/last-commit/ZG0704666/Aries-AI)

---

<div align="center">

**如果这个项目对你有帮助，请给我们一个 ⭐ Star！**

Made with ❤️ by [ZG0704666](https://github.com/ZG0704666)

[返回顶部](#aries-ai)

</div>
