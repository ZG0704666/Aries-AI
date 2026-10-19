// 插件解析仓库：仅使用 Google、Maven Central 与 Gradle 官方插件门户。
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// 依赖解析策略：禁止子模块自行声明仓库，统一在此处集中管理。
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// 工程模块图：app 主模块 + core 共享层 + feature 功能层。
rootProject.name = "Phone Agent"
include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:prompt")
include(":core:shizuku")
include(":feature:settings")
include(":feature:updates")
