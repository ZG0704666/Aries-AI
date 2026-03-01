# Phone Agent 编译指南

> 本文档提供完整的环境搭建、依赖安装、项目配置和编译步骤，帮助新成员快速开始开发。

---

## 📋 目录

- [一、环境要求](#一环境要求)
- [二、依赖库清单](#二依赖库清单)
- [三、项目克隆和导入](#三项目克隆和导入)
- [四、VSCode配置](#四vscode配置)
- [五、Android Studio配置](#五android-studio配置)
- [六、编译和运行](#六编译和运行)
- [七、常见问题排查](#七常见问题排查)

---

## 一、环境要求

### 1.1 必需软件

| 软件 | 最低版本 | 推荐版本 | 下载地址 |
|------|----------|----------|----------|
| JDK | 17 | 21（Android Studio 自带 JBR） | https://developer.android.com/studio |
| Android Studio | Hedgehog (2023.1.1) | Ladybug (2024.2.1) 或更高 | https://developer.android.com/studio |
| Gradle | 8.13（Wrapper） | 8.13（Wrapper） | 使用项目自带 gradlew |
| Kotlin | 2.0.21 | 2.0.21 | 由项目配置 |
| AGP | 8.13.2 | 8.13.2 | 由项目配置 |
| Git | 2.30+ | 2.40+ | https://git-scm.com/downloads |

### 1.2 Android SDK 要求

请确保已安装 Android SDK Platform：

- **Android 36 (API 36)**

### 1.3 版本检查

```bash
# 检查JDK版本
java -version
# 应输出：openjdk version "17.x.x" 或 "21.x.x"
# 推荐：使用 Android Studio 自带 JBR 21

# 检查Git版本
git --version
# 应输出：git version 2.x.x

# 检查Gradle版本（在项目根目录）
./gradlew --version
# 应输出：Gradle 8.13

# 检查 Kotlin 版本
./gradlew --version | grep Kotlin
# 应输出：Kotlin version 2.0.21

# 检查 AGP 版本
cat gradle/libs.versions.toml | grep agp
# 应输出：agp = "8.13.2"
```

### 1.4 版本权威来源（请以仓库配置为准）

- **Gradle 版本**：`gradle/wrapper/gradle-wrapper.properties` → `distributionUrl`
- **AGP 版本**：`gradle/libs.versions.toml` → `agp = "8.13.2"`
- **Kotlin 版本**：`gradle/libs.versions.toml` → `kotlin = "2.0.21"`
- **应用版本**：`app/build.gradle.kts` → `defaultConfig` 中的 `versionCode` 和 `versionName`
- **仓库配置**：`settings.gradle.kts` → `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`

> ⚠️ **重要**：项目启用了 `FAIL_ON_PROJECT_REPOS` 模式，只能在 `settings.gradle.kts` 中配置仓库，不能在模块的 `build.gradle.kts` 中添加 `repositories {}`。

### 1.5 环境变量配置

```bash
# Windows (PowerShell)
# 推荐：不需要手动配置 JAVA_HOME，优先让 Android Studio 使用自带 JBR (Java 21)
# 兼容：如果你想使用外部 JDK，可设置为 JDK 17 或 21 的安装目录
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.x-hotspot")
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Users\YourName\AppData\Local\Android\Sdk")

# 添加到PATH
$env:Path += ";$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\tools"

# 验证配置
java -version
adb --version
```

---

## 二、依赖库清单

### 2.1 核心依赖（已在 libs.versions.toml 中定义）

| 依赖库 | 版本 | 用途 |
|---------|------|------|
| AndroidX Core KTX | 1.17.0 | Android 核心库 |
| AndroidX AppCompat | 1.7.1 | 向后兼容 |
| Material Design | 1.13.0 | UI 组件 |

### 2.2 其他依赖（在 app/build.gradle.kts 中直接声明）

以下依赖未在 Version Catalog 中定义，而是在 `app/build.gradle.kts` 中直接声明：

- Kotlin Coroutines（异步编程）
- OkHttp / Retrofit（网络请求）
- Gson（JSON 序列化）
- Markwon（Markdown 渲染）
- Shizuku（系统级权限）
- Sherpa-ncnn（语音识别）

**查看完整依赖列表**：
```bash
./gradlew :app:dependencies
```

**说明**：项目采用混合依赖管理方式，核心依赖使用 Version Catalog，其他依赖直接声明。

### 2.3 测试依赖

| 依赖库 | 版本 | 用途 |
|---------|------|------|
| JUnit | 4.13.2 | 单元测试 |
| AndroidX JUnit | 1.3.0 | Android 测试 |
| Espresso Core | 3.7.0 | UI 测试 |

### 2.4 自动安装说明

项目使用 Gradle 版本目录（Version Catalog），所有依赖会自动下载和配置，无需手动安装。

---

## 三、项目克隆和导入

### 3.1 克隆项目

```bash
# HTTPS方式（推荐）
git clone https://github.com/ZG0704666/Aries-AI.git
cd Aries-AI

# SSH方式（需要配置SSH密钥）
git clone git@github.com:ZG0704666/Aries-AI.git
cd Aries-AI
```

### 3.2 配置本地仓库

```bash
# 配置用户信息
git config user.name "你的名字"
git config user.email "your.email@example.com"

# 配置分支追踪
git config branch.main.name main
```

### 3.3 创建功能分支

```bash
# 切换到main分支
git checkout main

# 拉取最新代码
git pull origin main

# 创建功能分支（带你的名字）
git checkout -b feature/ui-tree-张三
```

---

## 四、VSCode配置

### 4.1 安装推荐插件

打开VSCode，按`Ctrl+Shift+X`打开扩展市场，搜索并安装以下插件：

| 插件名称 | 用途 | 是否必需 |
|----------|------|---------|
| Kotlin Language | Kotlin语法高亮 | |
| Android Code Snippet | Android代码片段 | |
| Gradle Language Support | Gradle语法支持 | |
| GitLens | Git增强 | |
| Error Lens | 错误信息增强 | |
| Rainbow Brackets | 彩虹括号 | |

### 4.2 VSCode设置

创建或编辑`.vscode/settings.json`：

```json
{
  "kotlin.languageServer.enabled": true,
  "editor.formatOnSave": true,
  "editor.codeActionsOnSave": {
    "source.fixAll": true
  },
  "files.exclude": {
    "**/.gradle": true,
    "**/build": true,
    "**/local.properties": true
  },
  "git.enableSmartCommit": true,
  "git.postCommitCommand": "no verify"
}
```

### 4.3 VSCode调试配置

创建`.vscode/launch.json`：

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "android",
      "request": "launch",
      "name": "Launch App",
      "appLaunchActivity": ".MainActivity"
    }
  ]
}
```

---

## 五、Android Studio配置

### 5.1 必需插件

打开Android Studio，进入`File > Settings > Plugins`，搜索并安装：

| 插件名称 | 用途 | 是否必需 |
|----------|------|---------|
| Kotlin | Kotlin语言支持 | |
| .gitignore | Git忽略文件生成 | |
| Markdown Navigator | Markdown文件预览 | |
| Rainbow Brackets | 彩虹括号 | |

### 5.2 Gradle配置

#### 5.2.1 配置Gradle JVM

打开`File > Settings > Build, Execution, Deployment > Build Tools > Gradle`：

```
Gradle JDK:
  ☑ 选择 "Android Studio 自带 JBR (JetBrains Runtime) 21"
  或
  ☑ 选择外部 JDK 17/21

Gradle VM options:
  -Xmx4096m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
```

> 💡 **提示**：项目较大，建议分配至少 4GB 内存给 Gradle。

#### 5.2.2 配置编译选项

打开`File > Settings > Build, Execution, Deployment > Compiler > Kotlin Compiler`：

```
Language version: 以项目 Gradle 配置/插件版本为准
Target JVM version: 以项目 Gradle 配置为准
```

### 5.3 同步Gradle

1. 打开项目后，Android Studio会自动同步Gradle
2. 等待同步完成（右下角进度条）
3. 如果同步失败，尝试：
   - 点击`File > Invalidate Caches / Restart`
   - 删除`.gradle`和`build`文件夹，重新同步

---

## 六、编译和运行

### 6.0 当前版本信息

**版本号位置**：`app/build.gradle.kts` → `defaultConfig` 部分

```kotlin
// 示例结构（实际值以代码为准）
android {
    defaultConfig {
        versionCode = ?  // 内部版本号，每次发布递增
        versionName = "?.?.?"  // 用户可见版本号，遵循语义化版本
        minSdk = 30  // Android 11
        targetSdk = 36  // Android 16
    }
}
```

**查看当前版本**：
```bash
# 方法 1：运行验证脚本
.\verify-docs.ps1

# 方法 2：直接查看文件
cat app/build.gradle.kts | Select-String "versionCode|versionName"
```

### 6.1 编译Debug版本

```bash
# Windows (PowerShell)
.\gradlew assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

### 6.2 编译Release版本

```bash
# Windows (PowerShell)
.\gradlew assembleRelease

# Linux/Mac
./gradlew assembleRelease
```

### 6.3 运行测试

```bash
# 运行所有测试
.\gradlew test

# 运行单元测试
.\gradlew testDebugUnitTest

# 运行Android测试
.\gradlew connectedAndroidTest
```

### 6.4 安装到设备

```bash
# 安装Debug版本
.\gradlew installDebug

# 安装Release版本
.\gradlew installRelease

# 或者直接安装APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 6.5 运行应用

#### 方式1：通过Android Studio

1. 连接Android设备或启动模拟器
2. 点击`Run > Run 'app'`
3. 选择设备，点击运行按钮

#### 方式2：通过命令行

```bash
# 启动应用
adb shell am start -n com.ai.phoneagent/.MainActivity

# 查看日志
adb logcat | grep "PhoneAgent"
```

---

## 七、常见问题排查

### 7.1 Gradle同步失败

**问题**：Gradle sync失败，提示网络错误

**解决方案**：

1. 配置国内镜像源，编辑`gradle/wrapper/gradle-wrapper.properties`：

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.13-bin.zip
```

2. 或者配置阿里云镜像：

```properties
distributionUrl=https\://mirrors.aliyun.com/gradle/gradle-8.13-bin.zip
```

### 7.2 依赖下载失败

**问题**：依赖下载缓慢或失败

**解决方案**：

> ⚠️ **重要提示**：
> 
> 本项目启用了 `FAIL_ON_PROJECT_REPOS` 模式，这意味着：
> - ✅ 只能在 `settings.gradle.kts` 中配置仓库
> - ❌ 不能在 `app/build.gradle.kts` 或其他模块中添加 `repositories {}`
> - ❌ 否则会导致构建失败
> 
> 如果需要添加新的 Maven 仓库，请在 `settings.gradle.kts` 的 `dependencyResolutionManagement` 中添加。

1. 配置Maven镜像，编辑`settings.gradle.kts`：

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 如需使用国内镜像，可以添加：
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
    }
}
```

2. 使用代理（如果需要）：

```properties
# gradle.properties
systemProp.http.proxyHost=proxy.example.com
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=proxy.example.com
systemProp.https.proxyPort=8080
```

### 7.3 编译错误

**问题**：编译失败，提示找不到符号

**解决方案**：

1. 清理项目：

```bash
.\gradlew clean
```

2. 删除`.idea`文件夹：

```bash
# Windows
Remove-Item -Recurse -Force .idea

# Linux/Mac
rm -rf .idea
```

3. 重新导入项目到Android Studio

### 7.4 设备连接问题

**问题**：adb无法识别设备

**解决方案**：

1. 重启adb服务：

```bash
adb kill-server
adb start-server
```

2. 检查USB调试是否开启：
   - 进入`设置 > 开发者选项`
   - 开启`USB调试`

3. 检查授权：
   - 设备上弹出授权对话框，点击`允许`

### 7.5 内存不足错误

**问题**：编译时提示内存不足

**解决方案**：

1. 增加Gradle内存：

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m
```

2. 增加Android Studio内存：

```
File > Settings > Appearance & Behavior > System Settings > Memory
Heap Size: 4096 MB
```

## 📚 八、相关文档

- [FEISHU_COLLABORATION.md](./FEISHU_COLLABORATION.md) - 飞书协作文档模板
- [CODING_STANDARDS.md](./CODING_STANDARDS.md) - 代码规范
- [GIT_WORKFLOW.md](./GIT_WORKFLOW.md) - Git工作流
- [README.md](../../README.md) - 项目概述

---

## 🆘 九、快速检查清单

在开始开发前，请确认：

- [ ] 推荐使用 Android Studio 自带 JBR（Java 21）
- [ ] Android Studio 已安装（建议使用官方最新稳定版）
- [ ] 已通过 `gradlew` 验证Gradle Wrapper可用
- [ ] 已安装 Android SDK Platform 36 (API 36)
- [ ] 项目已克隆到本地
- [ ] VSCode推荐插件已安装
- [ ] Android Studio推荐插件已安装
- [ ] Gradle同步成功
- [ ] 可以编译Debug版本
- [ ] 可以运行测试

---

**文档版本**：v1.3
**最后更新**：2026-02-28
**维护人**：ZG0704666
