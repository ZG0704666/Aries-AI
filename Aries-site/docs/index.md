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
      <strong>开始使用</strong>
      <span>项目简介、许可证、环境权限、API Key 与模型配置</span>
    </a>
    <a class="home-doc-card" href="/architecture/overview">
      <strong>架构与数据</strong>
      <span>整体架构、模块组织、数据流、DI、导航与持久化</span>
    </a>
    <a class="home-doc-card" href="/chat/history">
      <strong>对话与模型</strong>
      <span>会话历史、消息卡片、流式 Markdown、语音输入与多模型</span>
    </a>
    <a class="home-doc-card" href="/automation/agent-loop">
      <strong>自动化与工具</strong>
      <span>Agent 主循环、动作执行、虚拟屏、设备控制与工具框架</span>
    </a>
    <a class="home-doc-card" href="/ui/material3-theme">
      <strong>界面与设计</strong>
      <span>Material 3 主题、设置界面与图标系统</span>
    </a>
    <a class="home-doc-card" href="/release/github-release">
      <strong>更新与分发</strong>
      <span>GitHub Release、应用内更新、APK 安装与遥测</span>
    </a>
    <a class="home-doc-card" href="/dev/coding-standards">
      <strong>工程实践</strong>
      <span>代码规范、Git 工作流、AI PR 评审、构建与 CI</span>
    </a>
    <a class="home-doc-card" href="/support/faq">
      <strong>帮助与支持</strong>
      <span>FAQ、故障排查与虚拟屏调试</span>
    </a>
  </div>
</div>
