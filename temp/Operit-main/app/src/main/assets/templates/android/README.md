# Operit Android 项目

这是一个基于 **Jetpack Compose** 的现代化 Android 应用开发模板。

## 🚀 项目特性

✅ **Jetpack Compose** - 现代化声明式 UI 框架  
✅ **Material Design 3** - 最新设计规范  
✅ **Kotlin** - 100% Kotlin 编写  
✅ **Gradle Version Catalog** - 统一依赖管理  
✅ **开箱即用** - 包含完整项目结构  

## 📁 项目结构

```
android-project/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/java/myapplication/
│   │   │   │   ├── MainActivity.kt          # 主Activity
│   │   │   │   └── ui/theme/
│   │   │   │       ├── Color.kt             # 颜色定义
│   │   │   │       ├── Theme.kt             # 主题配置
│   │   │   │       └── Type.kt              # 字体配置
│   │   │   ├── res/                         # 资源文件
│   │   │   └── AndroidManifest.xml          # 应用清单
│   │   ├── androidTest/                     # Android测试
│   │   └── test/                            # 单元测试
│   ├── build.gradle.kts                     # App模块配置
│   └── proguard-rules.pro                   # 混淆规则
├── gradle/
│   ├── libs.versions.toml                   # 依赖版本管理
│   └── wrapper/                             # Gradle Wrapper
├── build.gradle.kts                         # 项目级配置
├── settings.gradle.kts                      # 项目设置
├── gradle.properties                        # Gradle属性
├── gradlew / gradlew.bat                    # Gradle命令
└── .gitignore                               # Git忽略
```

## 🛠️ 快速开始

### 1. 环境要求
- ✅ **JDK 8+** (推荐 JDK 17)
- ✅ **Gradle** (已包含 Wrapper)
- ✅ **Android SDK** (可选，用于完整编译)

### 2. 构建项目

#### 使用 Operit 内置命令按钮
- 🔧 **初始化 Gradle Wrapper** - 首次使用
- 🔨 **构建项目** - 编译整个项目
- 🧹 **清理构建** - 清理构建缓存
- 📋 **查看所有任务** - 列出可用任务

#### 命令行方式
```bash
# Linux/Mac
./gradlew build              # 构建项目
./gradlew assembleDebug      # 打包Debug APK
./gradlew installDebug       # 安装到设备
./gradlew clean              # 清理构建

# Windows
gradlew.bat build
gradlew.bat assembleDebug
```

### 3. 生成的APK位置
```
app/build/outputs/apk/debug/app-debug.apk
```

## 📦 依赖管理

项目使用 **Gradle Version Catalog** 统一管理依赖版本。

### 查看当前依赖
在 `gradle/libs.versions.toml` 中定义：

```toml
[versions]
agp = "8.6.0"
kotlin = "1.9.0"
compose-bom = "2024.04.01"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
```

### 添加新依赖
1. 在 `gradle/libs.versions.toml` 中添加版本和库定义
2. 在 `app/build.gradle.kts` 中引用：
   ```kotlin
   dependencies {
       implementation(libs.your.library.name)
   }
   ```

## 🎨 自定义应用

### 修改应用名称
编辑 `app/src/main/res/values/strings.xml`：
```xml
<string name="app_name">你的应用名</string>
```

### 修改包名
1. 更新 `app/build.gradle.kts` 中的 `namespace` 和 `applicationId`
2. 重命名 `java/com/java/myapplication` 目录结构
3. 更新 `AndroidManifest.xml` 中的包名引用

### 修改主题颜色
编辑 `app/src/main/java/.../ui/theme/Color.kt`：
```kotlin
val Purple80 = Color(0xFFD0BCFF)  // 修改为你的颜色
```

## 📱 Compose 示例

当前 `MainActivity.kt` 包含一个简单的 Greeting 示例：

```kotlin
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
```

你可以：
- 添加更多 Composable 函数
- 使用 Material3 组件
- 实现导航（推荐使用 Navigation Compose）
- 集成 ViewModel、Repository 等架构组件

## 🔧 常用 Gradle 任务

```bash
./gradlew tasks              # 查看所有可用任务
./gradlew clean              # 清理构建
./gradlew build              # 完整构建
./gradlew assembleDebug      # 构建Debug APK
./gradlew assembleRelease    # 构建Release APK
./gradlew installDebug       # 安装Debug到设备
./gradlew test               # 运行单元测试
./gradlew connectedAndroidTest # 运行Android测试
```

## 📝 注意事项

⚠️ **关于 Android SDK**  
- 此模板可以在 Operit 的 Ubuntu 环境中构建
- 完整编译需要安装 Android SDK
- 推荐使用 Android Studio 进行完整开发

⚠️ **关于包名**  
- 默认包名为 `com.java.myapplication`
- 发布前请修改为你的唯一包名

⚠️ **关于签名**  
- Debug 版本自动使用调试签名
- Release 版本需要配置签名密钥

## 🌐 相关资源

- [Jetpack Compose 官方文档](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Android 开发者指南](https://developer.android.com/)
- [Kotlin 官方文档](https://kotlinlang.org/)

## 💡 提示

- 使用 `./gradlew --scan` 可以查看详细的构建分析
- 使用 `./gradlew build --info` 查看详细构建日志
- 修改 `gradle.properties` 可以调整构建性能

Happy Coding! 🤖✨
