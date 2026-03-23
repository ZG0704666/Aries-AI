# AI 自动化 PR 审阅指南

> Qodo AI + OpenCode 组合工作流，实现高效、智能的代码审查

---

## 概述

本项目中使用了两个 AI 工具协同工作：

| 工具 | 角色 | 触发方式 | 模型 |
|------|------|----------|------|
| **Qodo AI** | 自动审阅者 | PR 创建/更新时自动触发 | Qodo 内置模型 |
| **OpenCode** | 代码执行者 | 评论 `/oc` 命令触发 | 阿里编码计划 (Qwen3.5 Plus) |

### 工作流程图

```
┌─────────────────────────────────────────────────────────────┐
│                      PR 自动审阅流程                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ① 开发者提交 PR                                             │
│         ↓                                                    │
│  ② Qodo AI 自动审阅 → 输出审查报告                           │
│         ↓                                                    │
│  ③ 开发者评论 "/oc 命令"                                     │
│         ↓                                                    │
│  ④ OpenCode 执行任务 (用阿里模型)                            │
│         ↓                                                    │
│  ⑤ 自动提交修改到 PR                                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 一、Qodo AI（自动审阅）

### 1.1 已安装配置

Qodo AI 已作为 GitHub App 安装在仓库中，无需额外配置。

### 1.2 自动触发时机

- PR 创建时
- PR 有新提交时

### 1.3 审阅内容

Qodo AI 会自动分析并提供：

- **代码质量**：潜在 bug、代码异味、最佳实践
- **安全性**：敏感信息泄露、安全漏洞
- **性能**：性能瓶颈、优化建议
- **可维护性**：代码结构、命名规范

### 1.4 查看审阅结果

1. 打开 PR 页面
2. 在 "Conversation" 标签页查看 Qodo 的评论
3. 或在 "Files changed" 标签页查看行内评论

---

## 二、OpenCode（按需执行）

### 2.1 触发命令

在 PR 上评论以下命令触发 OpenCode：

| 命令 | 说明 |
|------|------|
| `/oc` | 触发 OpenCode |
| `/opencode` | 同上（完整命令） |

### 2.2 常用任务示例

#### 修复问题

```
/oc 修复 Qodo 指出的空指针问题
```

```
/oc 根据 Qodo 的建议优化这段代码的性能
```

#### 代码解释

```
/oc 解释一下这个 PR 的主要改动
```

```
/oc 这个函数是做什么的？有什么潜在问题？
```

#### 代码改进

```
/oc 为这个新增的类添加单元测试
```

```
/oc 重构这个方法，使其更易读
```

#### 文档更新

```
/oc 更新 README.md，添加新功能的说明
```

---

## 三、组合使用场景

### 场景 1：修复 Qodo 发现的问题

```
┌─ Qodo 评论 ─────────────────────────────────┐
│ ⚠️ 第 42 行：可能存在空指针异常               │
│ 建议：添加空值检查                            │
└──────────────────────────────────────────────┘

你的回复：
/oc 修复 Qodo 指出的第 42 行空指针问题
```

### 场景 2：批量处理多个问题

```
/oc 根据 Qodo 的审查意见，修复以下问题：
1. 第 15 行的命名不规范
2. 第 42 行缺少异常处理
3. 第 78 行的性能问题
```

### 场景 3：请求优化建议

```
/oc 分析 Qodo 提到的性能问题，给出具体的优化方案并实现
```

### 场景 4：代码审查辅助

```
/oc 检查这个 PR 是否有遗漏的边界情况处理
```

---

## 四、最佳实践

### 4.1 ✅ 推荐做法

1. **先等待 Qodo 审阅**：PR 创建后等待 1-2 分钟让 Qodo 完成
2. **针对性使用 OpenCode**：根据 Qodo 的建议精准触发任务
3. **验证 AI 修改**：AI 生成的代码也需要人工审核
4. **分段处理**：复杂任务拆分为多个小命令

### 4.2 ❌ 避免做法

1. **不要模糊命令**：
   - ❌ `/oc 修复所有问题`
   - ✅ `/oc 修复 ConversationTranscript.kt 第 15 行的命名问题`

2. **不要忽略 Qodo 警告**：特别是安全相关问题

3. **不要在 main 分支直接使用**：OpenCode 只在 PR 上工作

---

## 五、权限说明

### 5.1 谁可以触发 OpenCode？

以下角色可以触发：
- 仓库 Owner
- 组织 Member
- 协作者 Collaborator

### 5.2 OpenCode 的权限

OpenCode 具有：
- 读取仓库代码
- 提交修改到 PR 分支
- 评论 PR/Issue

---

## 六、配置文件说明

### 6.1 工作流配置

**文件**: `.github/workflows/opencode.yml`

```yaml
name: opencode

on:
  issue_comment:
    types: [created]
  pull_request_review_comment:
    types: [created]

jobs:
  opencode:
    if: |
      (
        contains(github.event.comment.body, ' /oc') ||
        startsWith(github.event.comment.body, '/oc') ||
        contains(github.event.comment.body, ' /opencode') ||
        startsWith(github.event.comment.body, '/opencode')
      ) &&
      (
        github.event.comment.author_association == 'OWNER' ||
        github.event.comment.author_association == 'MEMBER' ||
        github.event.comment.author_association == 'COLLABORATOR'
      ) &&
      (
        github.event_name != 'issue_comment' ||
        github.event.issue.pull_request != null
      )
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: write
      issues: write
      id-token: write
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 1
          persist-credentials: false

      - name: Run opencode
        uses: anomalyco/opencode/github@latest
        env:
          ALIBABA_CODING_PLAN_API_KEY: ${{ secrets.ALIBABA_CODING_PLAN_API_KEY }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          model: alibaba-coding-plan/qwen3.5-plus
          use_github_token: true
```

### 6.2 模型配置

**文件**: `.opencode/opencode.json`

```json
{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "alibaba-coding-plan": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "Alibaba Coding Plan",
      "options": {
        "baseURL": "https://coding.dashscope.aliyuncs.com/v1",
        "apiKey": "{env:ALIBABA_CODING_PLAN_API_KEY}"
      },
      "models": {
        "qwen3.5-plus": { "name": "Qwen3.5 Plus" },
        "glm-5": { "name": "GLM-5" },
        "kimi-k2.5": { "name": "Kimi K2.5" },
        "MiniMax-M2.5": { "name": "MiniMax M2.5" }
      }
    }
  }
}
```

### 6.3 可用模型

修改工作流中的 `model` 参数即可切换：

| 模型 ID | 说明 | 适用场景 |
|---------|------|----------|
| `qwen3.5-plus` | 通义千问 3.5 Plus | 通用编程任务（默认） |
| `glm-5` | 智谱 GLM-5 | 代码理解、生成 |
| `kimi-k2.5` | Moonshot Kimi | 长文本、多模态 |
| `MiniMax-M2.5` | MiniMax | 快速响应 |

---

## 七、故障排查

### 7.1 OpenCode 没有响应

**可能原因**：
1. Secret 未配置或失效
2. 评论者没有权限
3. 命令格式不对
4. 工作流正在运行中

**解决方法**：
1. 检查 Secrets 设置
2. 确认评论者是 Owner/Member/Collaborator
3. 使用正确的命令格式：`/oc` 或 `/opencode`

### 7.2 Qodo 没有审阅

**可能原因**：
1. Qodo GitHub App 未安装或权限不足
2. PR 是 draft 状态

**解决方法**：
1. 检查 GitHub App 安装状态
2. 将 PR 标记为 ready for review

### 7.3 API 错误

**可能原因**：
- 阿里编码计划 API Key 过期或额度用尽

**解决方法**：
1. 登录阿里云控制台检查 API Key 状态
2. 必要时更新 GitHub Secret

---

## 八、快速参考卡

### 常用命令

```bash
# 修复问题
/oc 修复 [具体问题]

# 解释代码
/oc 解释 [文件/函数]

# 添加测试
/oc 为 [类/方法] 添加单元测试

# 优化代码
/oc 优化 [文件] 的性能

# 更新文档
/oc 更新 README，添加 [功能] 的说明
```

### 流程速记

```
PR 提交 → Qodo 审阅 → 评论 /oc → OpenCode 执行 → 验证修改
```

---

**文档版本**：v1.0  
**最后更新**：2026-03-22  
**维护人**：ZG0704666