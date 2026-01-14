# 密钥旋转与安全通知清单

## 立即行动项

### 1. 硬编码密钥旋转（优先级：最高）

**受影响的密钥：**
- `APP_TOKEN_SECRET: '18comicAPP'` (jmcomic 示例)
- `APP_TOKEN_SECRET_2: '18comicAPPContent'` (jmcomic 示例)
- `APP_DATA_SECRET: '185Hcomic3PAPP7R'` (jmcomic 示例)

**旋转步骤：**
1. 在目标服务后台撤销当前密钥
2. 生成新密钥
3. 通知依赖此密钥的所有服务更新配置
4. 部署代码修复（fix_hardcoded_secrets.py）
5. 执行 Git 历史清理（cleanup_git_history.py）

**验证清除：**
```bash
git log -p --all -S "18comicAPP" -- "*.ts" "*.js" | head -20
git log -p --all -S "185Hcomic3PAPP7R" | head -20
```

---

### 2. GitHub OAuth Secret 旋转（优先级：高）

**受影响的位置：**
- `temp/Operit-main/app/build.gradle.kts` (GITHUB_CLIENT_SECRET)
- BuildConfig 注入的值

**旋转步骤：**
1. 登录 GitHub Settings > Developer settings > OAuth Apps
2. 撤销当前的 Client Secret
3. 生成新的 Secret
4. 更新 local.properties 中的值
5. 重新编译与部署
6. 验证 OAuth 流程正常

**验证方法：**
```bash
# 检查是否有旧密钥残留
git log --all -S "GITHUB_CLIENT_SECRET" | grep -c commit
```

---

### 3. Android 签名密钥口令检查（优先级：中）

**受影响的文件：**
- `RELEASE_STORE_PASSWORD` (local.properties)
- `RELEASE_KEY_PASSWORD` (local.properties)

**检查清单：**
- [ ] local.properties 未被提交到 Git（验证：`git ls-files | grep local.properties`）
- [ ] .gitignore 已包含 local.properties 与 *.jks
- [ ] keystore 文件未在 release/ 目录中
- [ ] 如果曾暴露过，联系 Google Play 更新签名密钥

---

## 团队通知模板

```
【安全通知】敏感凭据泄露与旋转通知

尊敬的团队成员，

在例行安全审计中，我们发现以下敏感信息被误提交到版本控制：

【需要立即旋转的密钥】
1. jmcomic 示例中的 API Token 与加密密钥（3 个）
2. GitHub OAuth Client Secret
3. Android 签名密钥口令（如适用）

【已执行的修复】
- 代码已修复：硬编码密钥替换为环境变量
- .env.example 模板已创建
- .gitignore 规则已更新
- Git 历史将进行清理（强制推送）

【需要您采取的行动】
1. 重新克隆仓库：git clone <repo-url>
2. 创建 local.properties 与 .env 文件（使用 .example 模板）
3. 更新环境变量（获取新旋转的密钥）
4. 重新编译与测试

【截止日期】
- 密钥旋转：YYYY-MM-DD
- 代码部署：YYYY-MM-DD
- Git 历史清理：YYYY-MM-DD（此后需强制同步）

如有疑问，请联系安全团队。
```

---

## 验证清单

### 代码修复验证
- [ ] jmcomic.ts 已替换为环境变量
- [ ] jmcomic.js 已替换为环境变量
- [ ] 代码提交并已推送

### Git 历史清理验证
- [ ] 备份分支已创建 (`git branch | grep backup`)
- [ ] git-filter-repo 已运行
- [ ] 敏感值不再出现在历史中
- [ ] 强制推送已完成

### 环境与配置验证
- [ ] .env.example 已在根目录
- [ ] local.properties 已添加到 .gitignore
- [ ] CI/CD pipeline 已配置环境变量注入
- [ ] 所有开发者已知晓新流程

### 运行时验证
- [ ] 应用启动无密钥硬编码警告
- [ ] API 调用使用新旋转的密钥成功
- [ ] OAuth 流程正常工作

---

## 参考资源

- [Git 敏感信息清理 - git-filter-repo](https://github.com/newren/git-filter-repo)
- [GitHub - 撤销已泄露的密钥](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure)
- [OWASP - 密钥管理最佳实践](https://owasp.org/www-community/Credential_Management_Cheat_Sheet)
- [Google Play - 应用签名](https://support.google.com/googleplay/android-developer/answer/9842756)
