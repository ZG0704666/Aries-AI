---
layout: home
title: Aries AI 技术文档
titleTemplate: false
description: Aries AI 技术文档：Android 视觉自动化引擎的架构、对话系统、自动化流程、虚拟屏隔离、多模型接入与开发规范。

hero:
  name: Aries AI
  text: 技术文档
  tagline: 用视觉理解屏幕，用一句话完成跨应用任务。从环境配置、架构设计到自动化引擎，这里是 Aries AI 的完整实现说明。
  image:
    src: /favicon.png
    alt: Aries AI
  actions:
    - theme: brand
      text: 快速开始
      link: /quickstart/setup
    - theme: alt
      text: 架构总览
      link: /architecture/overview
    - theme: alt
      text: 下载应用
      link: https://aries-agent.com/download

features:
  - title: 看得懂屏幕
    details: 基于多模态大模型理解界面结构与语义，不依赖固定脚本，陌生页面也能执行。
  - title: 虚拟屏隔离
    details: 任务在独立显示空间后台运行，截图、点击、输入不抢占前台，不打断你正在做的事。
  - title: 离线语音
    details: Sherpa-onnx + X-ASR-zh-en 本地流式识别，中英混说、自带标点，语音不出设备。
  - title: 多模型接入
    details: 第三方 API、Aries API 与本地 MNN 三种模式，按场景与网络条件自由切换。
  - title: 工具框架
    details: 网络、文件、应用包名等工具统一注册与权限管理，能力边界清晰可控。
  - title: 工程可维护
    details: Kotlin 多模块分层、Koin 依赖注入、Compose 设计系统与完整 CI 流程。
---

<div class="home-doc-index">
  <h2 class="home-doc-index__title">按主题浏览</h2>
  <p class="home-doc-index__desc">文档与代码同步维护，覆盖从安装配置到源码实现的完整链路。</p>
  <div class="home-doc-index__grid">
    <a class="home-doc-card" href="/overview/introduction">
      <strong>项目概览</strong>
      <span>核心特性、许可证与术语约定</span>
    </a>
    <a class="home-doc-card" href="/quickstart/setup">
      <strong>快速开始</strong>
      <span>环境与权限配置、API Key 与模型</span>
    </a>
    <a class="home-doc-card" href="/architecture/overview">
      <strong>技术架构</strong>
      <span>分层设计、模块组织、数据流与依赖注入</span>
    </a>
    <a class="home-doc-card" href="/chat/home-screen">
      <strong>对话系统</strong>
      <span>主界面、流式 Markdown、消息卡片与语音输入</span>
    </a>
    <a class="home-doc-card" href="/automation/agent-loop">
      <strong>自动化引擎</strong>
      <span>Agent 主循环、动作解析与执行、屏幕采集</span>
    </a>
    <a class="home-doc-card" href="/system/accessibility">
      <strong>系统服务与权限</strong>
      <span>设备控制、无障碍服务与悬浮层</span>
    </a>
    <a class="home-doc-card" href="/models/overview">
      <strong>多模型接入</strong>
      <span>模式切换、配置链路与本地 MNN 推理</span>
    </a>
    <a class="home-doc-card" href="/virtual-display/overview">
      <strong>虚拟屏隔离系统</strong>
      <span>完全隔离方案、焦点管理与输入注入</span>
    </a>
    <a class="home-doc-card" href="/tools/architecture">
      <strong>AI 工具框架</strong>
      <span>工具注册、权限管理与内置执行器</span>
    </a>
    <a class="home-doc-card" href="/ui/material3-theme">
      <strong>UI 组件与设计系统</strong>
      <span>Material 3 主题、设置界面与图标系统</span>
    </a>
    <a class="home-doc-card" href="/persistence/room">
      <strong>数据持久化</strong>
      <span>Room 数据库、会话仓储与截图缓存</span>
    </a>
    <a class="home-doc-card" href="/release/in-app-updates">
      <strong>更新与分发</strong>
      <span>应用内更新、GitHub Release 与遥测</span>
    </a>
    <a class="home-doc-card" href="/dev/coding-standards">
      <strong>开发规范与工具</strong>
      <span>代码规范、Git 工作流、构建与 CI</span>
    </a>
    <a class="home-doc-card" href="/support/faq">
      <strong>常见问题与支持</strong>
      <span>FAQ、故障排查与虚拟屏调试提示</span>
    </a>
  </div>
</div>
