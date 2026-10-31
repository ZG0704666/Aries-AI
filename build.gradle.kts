// 顶层构建文件：声明所有子项目/模块共用的 Gradle 插件，但不在根项目中应用。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// 统一 lint 门禁策略：存量告警固化到各模块 lint-baseline.xml，
// 之后新增的任何 warning 按 error 处理，避免 676 条历史告警稀释新问题。
subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            lint {
                baseline = file("lint-baseline.xml")
                warningsAsErrors = true
                abortOnError = true
                // 依赖版本升级提示不进 lint 门禁：由 Renovate/Dependabot 等专门流程管理，
                // 避免每次依赖升级都要重生成 baseline（可移植性差、噪声大）。
                disable += "GradleDependency"
                disable += "NewerVersionAvailable"
                disable += "AndroidGradlePluginVersion"
            }
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            lint {
                baseline = file("lint-baseline.xml")
                warningsAsErrors = true
                abortOnError = true
                // 与 application 模块保持同一策略，理由见上。
                disable += "GradleDependency"
                disable += "NewerVersionAvailable"
                disable += "AndroidGradlePluginVersion"
            }
        }
    }
}
