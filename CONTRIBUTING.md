# 贡献者指南

欢迎为 Aries AI 项目做出贡献！本文档将帮助你快速上手贡献流程。

---

## 📋 目录

- [开发环境设置](#开发环境设置)
- [贡献流程](#贡献流程)
- [代码规范](#代码规范)
- [测试要求](#测试要求)
- [CI/CD 工作流](# cicd-工作流)
- [常见问题](#常见问题)

---

## 🛠️ 开发环境设置

### 系统要求

| 组件 | 最低版本 | 推荐版本 |
|------|---------|---------|
| JDK | 11 | 17 |
| Android SDK | 30 | 34+ |
| Gradle | 8.0 | 8.4+ |
| Android Studio | Arctic Fox | Hedgehog+ |
| Git | 2.30 | 2.40+ |

### 设置步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/ZG0704666/Aries-AI.git
   cd Aries-AI
   ```

2. **配置 local.properties**（可选，仅调试需要）
   ```properties
   sdk.dir=/path/to/Android/sdk
   github.token=your_github_token  # 仅调试需要
   ```

3. **验证构建**
   ```bash
   ./gradlew assembleDebug
   ```

4. **运行测试**
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 🔄 贡献流程

### 1. Fork 仓库

在 GitHub 上点击 "Fork" 按钮，创建你自己的仓库副本。

### 2. 创建分支

从 `develop` 分支创建你的功能分支：

```bash
git checkout develop
git checkout -b feature/your-feature-name
```

**分支命名规范**：

| 类型 | 前缀 | 示例 |
|------|------|------|
| 新功能 | `feature/` | `feature/virtual-screen-optimization` |
| Bug 修复 | `fix/` | `fix/screenshot-black-frame` |
| 文档 | `docs/` | `docs/update-api-guide` |
| 重构 | `refactor/` | `refactor/action-parser` |
| 性能优化 | `perf/` | `perf/reduce-screenshot-size` |
| 测试 | `test/` | `test/add-agent-configuration-tests` |

### 3. 本地开发

**提交前必做检查**：

```bash
# 1. 编译验证
./gradlew assembleDebug

# 2. 运行单元测试
./gradlew testDebugUnitTest

# 3. Lint 检查
./gradlew lint
```

### 4. 提交代码

使用规范的提交信息格式：

```bash
git add .
git commit -m "type(scope): description"
```

**提交信息格式**：

```
<type>(<scope>): <subject>

<body> (optional)

<footer> (optional)
```

**Type 类型**：

| Type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档更新 |
| `style` | 代码格式（不影响功能） |
| `refactor` | 重构 |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建/工具链 |

**示例**：

```bash
# 新功能
git commit -m "feat(vdiso): add OpenGL frame dispatcher for dual-surface support"

# Bug 修复
git commit -m "fix(input): resolve focus deadlock in virtual screen IME handling"

# 测试
git commit -m "test(core): add unit tests for AgentConfiguration"
```

### 5. 推送分支

```bash
git push origin feature/your-feature-name
```

### 6. 创建 Pull Request

1. 在 GitHub 上打开你的 Fork 仓库
2. 点击 "Compare & pull request"
3. 填写 PR 模板：
   - **标题**：清晰描述改动
   - **描述**：说明改动内容、测试方法、相关 Issue
4. 提交 PR

---

## 📝 代码规范

### Kotlin 代码规范

1. **命名规范**
   ```kotlin
   // 类名：大驼峰
   class VirtualDisplayController
   
   // 函数/变量：小驼峰
   fun takeScreenshot()
   val displayId: Int
   
   // 常量：全大写 + 下划线
   const val DEFAULT_SCREEN_WIDTH = 1080
   
   // 私有属性：小驼峰 + 下划线前缀（可选）
   private val _screenWidth: Int
   ```

2. **代码风格**
   - 缩进：4 个空格（不用 Tab）
   - 行宽：120 字符
   - 文件编码：UTF-8

3. **注释要求**
   ```kotlin
   /**
    * 虚拟屏控制器
    * 
    * 负责创建和管理虚拟显示设备，实现焦点隔离。
    * 
    * @param displayId 虚拟显示 ID
    * @param callback 截图回调
    */
   class VirtualDisplayController(
       private val displayId: Int,
       private val callback: ScreenshotCallback
   ) {
       // ...
   }
   ```

### 资源文件规范

1. **命名规范**
   ```xml
   <!-- 布局文件：snake_case -->
   res/layout/activity_main.xml
   
   <!-- 字符串：snake_case 前缀 -->
   <string name="settings_virtual_screen">虚拟屏幕</string>
   
   <!-- 颜色：用途_描述 -->
   <color name="primary_background">#FFFFFF</color>
   ```

2. **避免硬编码**
   ```kotlin
   // ❌ 错误
   textView.textSize = 16f
   textView.setTextColor(Color.parseColor("#FF0000"))
   
   // ✅ 正确
   textView.textSize = resources.getDimension(R.dimen.text_size_medium)
   textView.setTextColor(ContextCompat.getColor(context, R.color.error))
   ```

---

## 🧪 测试要求

### 单元测试

**新增功能必须包含单元测试**：

```kotlin
// 测试文件位置
app/src/test/java/com/ai/phoneagent/<module>/<ClassName>Test.kt

// 示例
class AgentConfigurationTest {
    
    @Test
    fun `默认配置值正确`() {
        val config = AgentConfiguration.DEFAULT
        assertEquals(100, config.maxSteps)
    }
    
    @Test
    fun `不同动作的延迟时间正确`() {
        val config = AgentConfiguration.DEFAULT
        assertEquals(1050L, config.getActionDelayMs("launch"))
        assertEquals(320L, config.getActionDelayMs("tap"))
    }
}
```

### 测试覆盖目标

| 模块 | 最低覆盖率 | 说明 |
|------|-----------|------|
| `core/` | 70% | 核心逻辑 |
| `vdiso/` | 60% | 虚拟屏模块 |
| `input/` | 60% | 输入注入 |
| `net/` | 50% | 网络模块 |
| `ui/` | 30% | UI 模块（手动测试为主） |

### 运行测试

```bash
# 运行所有单元测试
./gradlew testDebugUnitTest

# 运行特定测试类
./gradlew testDebugUnitTest --tests "com.ai.phoneagent.core.CoreModuleTest"

# 生成覆盖率报告
./gradlew jacocoTestReport
```

---

## 🚀 CI/CD 工作流

### 自动触发

当你提交 PR 后，以下流程会自动触发：

| 工作流 | 触发时机 | 检查内容 |
|--------|---------|---------|
| **GitHub Actions CI** | PR 创建/更新 | 编译、单元测试、Lint |
| **Qodo Merge** | PR 创建/更新 | 代码审阅、安全分析、性能建议 |
| **Qodo Cover** | PR 创建 | 检测测试缺口、生成单元测试 |

### 查看检查结果

1. **CI 状态**：PR 页面 → "Checks" 标签
2. **Qodo 审阅**：PR 页面 → "Conversation" 标签
3. **构建产物**：CI 成功后下载 Debug APK

### 常见问题处理

#### CI 编译失败

```bash
# 1. 查看错误日志
点击 Checks → Build Debug APK → 查看错误

# 2. 本地复现
./gradlew assembleDebug --stacktrace

# 3. 修复后重新推送
git commit -m "fix: resolve compilation error"
git push
```

#### 单元测试失败

```bash
# 1. 查看失败测试
点击 Checks → Run Unit Tests → 查看失败详情

# 2. 本地运行测试
./gradlew testDebugUnitTest --tests "FailedTestClassName"

# 3. 修复后重新推送
```

#### Qodo 提出建议

1. 查看 Qodo 评论（Conversation 标签）
2. 评估建议优先级：
   - 🔴 高：Bug、安全问题 → 必须修复
   - 🟡 中：性能、代码质量 → 建议修复
   - 🟢 低：命名、风格 → 可选修复
3. 手动修复或评论 `/oc` 命令让 AI 协助

---

## ❓ 常见问题

### Q: 我的 PR 多久会被审查？

A: CI 和 Qodo 会在 5 分钟内自动完成。维护者会在 1-3 个工作日内审查。

### Q: 如何加速 PR 合并？

A: 
1. 确保 CI 全绿（编译/测试/Lint）
2. 回应所有 Qodo 提出的高优先级问题
3. 提供清晰的测试说明
4. 小步提交（每个 PR 聚焦一个功能）

### Q: 我可以跳过测试吗？

A: 不建议。新增功能必须包含测试。特殊情况请在 PR 中说明原因。

### Q: 如何本地测试虚拟屏功能？

A: 虚拟屏功能需要真实设备 + Shizuku。模拟器无法完整测试。

### Q: 遇到构建问题怎么办？

A:
1. 清理构建缓存：`./gradlew clean`
2. 重新构建：`./gradlew assembleDebug`
3. 查看 `docs/BUILDING.md` 获取详细构建指南
4. 在 Issue 中提问

---

## 📚 相关文档

- [构建指南](./docs/BUILDING.md)
- [代码规范](./docs/CODING_STANDARDS.md)
- [Git 工作流](./docs/GIT_WORKFLOW.md)
- [技术文档](./docs/TECHNICAL_OVERVIEW.md)
- [AI PR 审阅指南](./docs/AI_PR_REVIEW.md)
- [FAQ](./docs/FAQ.md)

---

## 🤝 需要帮助？

- 💬 **QQ 群**：[746439473](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=&authKey=&noverify=0&group_code=746439473)
- 🐛 **Issue**：[GitHub Issues](https://github.com/ZG0704666/Aries-AI/issues)
- 📧 **邮件**：zhangyongqi@njit.edu.cn

---

感谢你的贡献！🎉
