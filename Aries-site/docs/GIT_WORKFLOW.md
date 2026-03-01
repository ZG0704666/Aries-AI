# Phone Agent Git工作流

> 本文档定义Phone Agent项目的Git工作流规范，确保团队协作顺畅和版本管理规范。

---

## 📋 目录

- [一、分支策略](#一分支策略)
- [二、提交规范](#二提交规范)
- [三、Pull Request流程](#三pull-request流程)
- [四、代码审查流程](#四代码审查流程)
- [五、合并流程](#五合并流程)
- [六、冲突解决](#六冲突解决)
- [七、版本管理](#七版本管理)

---

## 一、分支策略

### 1.1 分支类型

| 分支类型 | 命名格式 | 用途 | 生命周期 |
|----------|----------|------|----------|
| main | main | 稳定版本，生产代码 | 永久 |
| dev | dev | 开发集成分支 | 永久 |
| feature | feature/xxx-开发者名 | 功能开发 | 临时 |
| fix | fix/xxx-开发者名 | Bug修复 | 临时 |
| hotfix | hotfix/xxx-开发者名 | 紧急修复 | 临时 |

### 1.2 分支命名示例

```bash
# 功能分支（推荐）
feature/ui-tree-张三
feature/tool-click-element-李四
feature/perf-cache-王五

# 修复分支
fix/ui-parse-error-张三
fix/cache-bug-李四
fix/crash-fix-王五

# 热修复分支
hotfix/crash-fix-张三
hotfix/memory-leak-李四
hotfix/security-fix-王五
```

### 1.3 分支使用规则

✅ **必须**：
- 所有功能开发必须从`feature/xxx-开发者名`分支开始
- 所有Bug修复必须从`fix/xxx-开发者名`分支开始
- 紧急修复必须从`hotfix/xxx-开发者名`分支开始
- 分支名必须包含开发者标识，便于追踪

❌ **禁止**：
- 不要直接在`main`分支开发
- 不要在`dev`分支开发（除非是集成）
- 不要创建无意义的分支名（如`test`、`temp`）

---

## 二、提交规范

### 2.1 提交信息格式

```bash
<type>(<scope>): <subject>-<developer>

<body>

<footer>
```

### 2.2 类型（type）

| 类型 | 说明 | 示例 |
|------|------|------|
| feat | 新功能 | feat(tool): 新增get_page_info工具 |
| fix | 修复bug | fix(ui): 修复UI树解析失败 |
| perf | 性能优化 | perf(cache): 优化截图缓存策略 |
| refactor | 重构代码 | refactor(service): 重构无障碍服务 |
| docs | 文档更新 | docs(readme): 更新README |
| test | 测试相关 | test(unit): 添加单元测试 |
| chore | 构建/工具链 | chore(deps): 更新依赖版本 |

### 2.3 范围（scope）

| 范围 | 说明 | 示例 |
|------|------|------|
| tool | 工具相关 | feat(tool): 新增工具 |
| ui | UI相关 | fix(ui): 修复布局问题 |
| service | 服务相关 | perf(service): 优化服务性能 |
| cache | 缓存相关 | feat(cache): 新增缓存机制 |
| agent | Agent相关 | refactor(agent): 重构Agent逻辑 |
| config | 配置相关 | chore(config): 更新配置 |
| readme | README相关 | docs(readme): 更新README |

### 2.4 主题（subject）

✅ **要求**：
- 简洁明了，不超过50字符
- 使用中文，便于理解
- 描述做了什么，而不是怎么做

❌ **示例**：
- ❌ 新增get_page_info工具用于获取页面信息包括package和activity和UI树支持xml和json格式
- ✅ 新增get_page_info工具

### 2.5 正文（body）

✅ **要求**：
- 详细说明变更内容
- 列出主要变更点
- 说明影响范围

❌ **禁止**：
- 不要提交敏感信息（API密钥、密码等）
- 不要提交临时文件（.tmp、.bak等）
- 不要提交编译产物（build、.gradle等）

### 2.6 页脚（footer）

```bash
# 关联Issue
Closes #001
Fixes #002
Related to #003

# 关联PR
Refs #123
```

### 2.7 完整提交示例

```bash
# 功能开发
git add .
git commit -m "feat(tool): 新增get_page_info工具-张三

- 支持获取页面信息(package+activity+UI树)
- 支持format参数(xml/json)
- 支持detail参数(minimal/summary/full)
- 对齐Operit工具接口

Closes #005"

# Bug修复
git add .
git commit -m "fix(ui): 修复UI树解析失败-李四

- XML格式不兼容，调整解析器
- 添加异常处理
- 增加单元测试

Fixes #003"

# 性能优化
git add .
git commit -m "perf(cache): 优化截图缓存策略-王五

- 调整TTL从2秒到1.5秒
- 增加LRU淘汰策略
- 缓存命中率从20%提升到35%

Related to #013"
```

---

## 三、Pull Request流程

### 3.1 创建PR

```bash
# 使用GitHub CLI
gh pr create --title "feat(tool): 新增get_page_info工具-张三" \
             --body "## 变更内容\n\n- ...\n\n## 测试\n\n- ..." \
             --base main \
             --head feature/tool-get-page-info-张三

# 或在GitHub网页创建
1. 进入项目页面
2. 点击"Pull requests" > "New pull request"
3. 填写标题和描述
4. 选择base分支：main
5. 选择head分支：feature/tool-get-page-info-张三
6. 点击"Create pull request"
```

### 3.2 PR描述模板

```markdown
## 变更内容

### 主要变更
- 新增get_page_info工具
- 支持format参数(xml/json)
- 支持detail参数(minimal/summary/full)
- 对齐Operit工具接口

### 影响范围
- `ToolRegistration.kt` - 新增工具注册
- `PhoneAgentAccessibilityService.kt` - 新增getUiHierarchy方法

## 测试

### 单元测试
- [x] ScreenshotCacheTest通过
- [x] ToolRegistrationTest通过
- [x] 测试覆盖率≥70%

### 集成测试
- [x] 在真机上测试通过
- [x] UI树输出格式正确
- [x] 工具调用正常

### 手动测试
- [x] 测试format=xml
- [x] 测试format=json
- [x] 测试detail=summary
- [x] 测试detail=full

## 相关Issue

Closes #005
Related to #003, #004

## 审查清单

- [x] 代码符合规范
- [x] 公共API有注释
- [x] 异常处理完善
- [x] 无敏感信息
- [x] 文档已更新

## 截止日期

2026-01-20
```

### 3.3 PR状态标签

| 标签 | 说明 | 使用场景 |
|------|------|---------|
| draft | 草稿 | PR创建后未完成 |
| review | 审查中 | 等待代码审查 |
| approved | 已批准 | 审查通过 |
| changes requested | 需要修改 | 审查者提出修改意见 |
| merged | 已合并 | 已合并到main |
| closed | 已关闭 | 未合并但关闭 |

---

## 四、代码审查流程

### 4.1 审查者职责

| 审查项 | 检查内容 | 通过标准 |
|---------|---------|---------|
| 代码规范 | 命名、格式、注释 | 完全符合 |
| 功能完整性 | 所有功能已实现 | 功能完整 |
| 测试覆盖 | 单元测试覆盖率≥70% | 覆盖率达标 |
| 文档更新 | README、API文档已更新 | 文档已更新 |
| 性能影响 | 无性能问题 | 性能良好 |
| 安全检查 | 无敏感信息泄露 | 安全合规 |

### 4.2 审查意见格式

```markdown
### 优点

- XML schema设计合理
- 代码结构清晰
- 异常处理完善

### 需要改进

- 需要更新README.md
- 建议添加更多单元测试
- 建议优化缓存策略

### 具体问题

1. **第15行**：变量命名不符合规范
   - 当前：`ScreenshotCache`
   - 建议：`screenshotCache`
   - 位置：`ScreenshotCache.kt:15`

2. **第42行**：缺少异常处理
   - 建议：添加try-catch块
   - 位置：`ToolRegistration.kt:42`
```

### 4.3 审查结果

- [ ] 通过，可以合并
- [x] 需要修改后合并
- [x] 拒绝，需要重新开发

### 4.4 合并操作

```bash
# 项目负责人操作
git checkout main
git pull origin main
git merge --no-ff feature/tool-get-page-info-张三
git push origin main

# 删除已合并分支
git branch -d feature/tool-get-page-info-张三
git push origin --delete feature/tool-get-page-info-张三
```

---

## 五、合并流程

### 5.1 合并前检查

在合并前，项目负责人需要确认：

- [ ] 所有PR已通过审查
- [ ] 所有测试通过
- [ ] 文档已更新
- [ ] 无合并冲突
- [ ] 版本号已更新

### 5.2 合并策略

| 场景 | 策略 | 命令 |
|------|------|------|
| 无冲突 | 直接合并 | `git merge --no-ff feature/xxx` |
| 有冲突 | 手动解决 | `git merge feature/xxx`（手动解决冲突） |
| 多个PR | 按顺序合并 | 依次合并，避免冲突 |

### 5.3 合并后操作

```bash
# 1. 推送合并后的main
git push origin main

# 2. 删除已合并的功能分支
git branch -d feature/xxx-张三
git push origin --delete feature/xxx-张三

# 3. 创建版本标签（可选）
git tag -a v1.0.1 -m "Release v1.0.1"
git push origin v1.0.1
```

---

## 六、冲突解决

### 6.1 常见冲突场景

| 冲突类型 | 场景 | 解决策略 |
|----------|------|---------|
| 同一文件修改 | 多人修改同一文件 | 手动合并，保留双方修改 |
| 删除冲突 | 一方删除，一方修改 | 确认删除意图，手动合并 |
| 重命名冲突 | 文件被重命名 | 追踪文件历史，手动合并 |

### 6.2 冲突解决步骤

```bash
# 1. 拉取最新代码
git checkout main
git pull origin main

# 2. 合并功能分支
git merge feature/xxx-张三

# 3. 查看冲突文件
git status
# 会显示冲突文件列表

# 4. 打开冲突文件
# 使用IDE（VSCode或Android Studio）打开冲突文件
# 冲突标记：
# <<<<<<< HEAD
# =====
# >>>>>>> feature/xxx-张三

# 5. 解决冲突
# 保留需要的代码，删除冲突标记

# 6. 标记冲突已解决
git add <冲突文件>

# 7. 完成合并
git commit -m "merge: 合并feature/xxx-张三到main，解决冲突"
```

### 6.3 冲突解决示例

```kotlin
// 冲突前（main分支）
class ScreenshotCache {
    private val cache = HashMap<String, ScreenshotData>()
}

// 冲突后（feature分支）
class ScreenshotCache {
    private val cache = LinkedHashMap<String, ScreenshotData>()
}

// 解决冲突（合并后）
class ScreenshotCache {
    private val cache = LinkedHashMap<String, ScreenshotData>()
}
```

---

## 七、版本管理

### 7.1 版本号规范

采用语义化版本号：`MAJOR.MINOR.PATCH`

| 版本号 | 说明 | 示例 |
|----------|------|------|
| 1.0.0 | 初始版本 | 首次发布 |
| 1.0.1 | Bug修复 | 修复bug，向后兼容 |
| 1.1.0 | 新功能 | 添加新功能，向后兼容 |
| 2.0.0 | 重大变更 | 不兼容的API变更 |

### 7.2 版本更新规则

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        versionCode = 1        // 自增
        versionName = "1.0.0"  // 语义化版本
    }
}
```

### 7.3 版本发布流程

```bash
# 1. 更新版本号
# 修改 app/build.gradle.kts 中的 versionCode 和 versionName
# versionCode: 递增（如 15 -> 16）
# versionName: 遵循语义化版本（如 1.3.2 -> 1.4.0）

# 2. 创建版本标签（使用实际版本号）
git tag -a v1.4.0 -m "Release v1.4.0 - 架构优化"

# 3. 推送标签
git push origin v1.4.0

# 4. 创建GitHub Release
gh release create v1.4.0 \
    --title "v1.4.0 - 架构优化" \
    --notes "## 新增功能\n\n- ...\n\n## Bug修复\n\n- ..." \
    --target main
```

---

## 📋 快速参考

### 每日开发流程

```bash
# 1. 拉取最新代码
git checkout main
git pull origin main

# 2. 创建功能分支
git checkout -b feature/xxx-张三

# 3. 开发代码
# ... 编写代码 ...

# 4. 本地测试
./gradlew test
./gradlew installDebug

# 5. 提交代码
git add .
git commit -m "feat(scope): xxx-张三"

# 6. 推送到远程
git push origin feature/xxx-张三

# 7. 创建PR（在GitHub网页）
# 等待审查和合并
```

### 每周合并流程（项目负责人）

```bash
# 1. 检查所有PR
# 在GitHub查看所有Open的Pull Request

# 2. 审查代码
# 检查代码规范
# 检查功能完整性
# 检查测试覆盖

# 3. 合并到main
git checkout main
git pull origin main
git merge --no-ff feature/xxx-张三
git push origin main

# 4. 删除已合并分支
git branch -d feature/xxx-张三
git push origin --delete feature/xxx-张三
```

---

## 🎯 最佳实践

### ✅ 推荐做法

1. **频繁提交**：每完成一个小功能就提交，不要积累大量代码
2. **清晰的提交信息**：使用标准格式，便于追溯
3. **代码审查**：所有PR必须经过至少1人审查
4. **测试先行**：提交前确保测试通过
5. **文档同步**：代码变更必须同步更新文档

### ❌ 避免做法

1. **不要直接提交main**：必须通过PR合并
2. **不要提交敏感信息**：API密钥、密码等
3. **不要忽略测试**：测试覆盖率≥70%
4. **不要忽略文档**：代码变更必须更新文档
5. **不要创建无意义分支**：分支名必须清晰表达意图

---

## 📚 相关文档

- [FEISHU_COLLABORATION.md](./FEISHU_COLLABORATION.md) - 飞书协作文档模板
- [BUILDING.md](./BUILDING.md) - 环境搭建和编译指南
- [CODING_STANDARDS.md](./CODING_STANDARDS.md) - 代码规范
- [README.md](../README.md) - 项目概述

---

**文档版本**：v1.1
**最后更新**：2026-02-28
**维护人**：ZG0704666