import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 从 local.properties 读取 GitHub Token；文件缺失或未配置时返回空串。
val githubToken: String by lazy {
    val f = rootProject.file("local.properties")
    if (!f.exists()) return@lazy ""

    val line =
        f.readLines()
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("#") && it.startsWith("github.token") }
            ?: return@lazy ""

    val eqIdx = line.indexOf('=')
    if (eqIdx < 0) return@lazy ""
    line.substring(eqIdx + 1).trim()
}

// 通用本地属性读取：按 key 在 local.properties 中查找，未找到时返回空串。
fun localProperty(name: String): String {
    val f = rootProject.file("local.properties")
    if (!f.exists()) return ""

    val line =
        f.readLines()
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("#") && it.startsWith(name) }
            ?: return ""

    val eqIdx = line.indexOf('=')
    if (eqIdx < 0) return ""
    return line.substring(eqIdx + 1).trim()
}

// 读取本地属性，未配置时回落到默认值。
fun localProperty(name: String, defaultValue: String): String =
    localProperty(name).ifBlank { defaultValue }

// 转义反斜杠与双引号，确保写入 BuildConfig 字符串字面量时的安全性。
fun escapedBuildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.ai.phoneagent"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = "com.ai.phoneagent"
        minSdk = 30
        targetSdk = 36
        versionCode = 21
        versionName = "2.0.0 Alpha"

        // 默认 BuildConfig 占位：实际 Token 与遥测地址在 buildTypes 中按变体覆盖。
        buildConfigField("String", "GITHUB_TOKEN", "\"\"")
        buildConfigField(
            "String",
            "TELEMETRY_HEARTBEAT_ENDPOINT",
            "\"${escapedBuildConfigString(localProperty("aries.telemetry.heartbeatEndpoint", "https://oiariesapi.xuanyu.online/v1/telemetry/heartbeat"))}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON", "-DARIES_ENABLE_SHERPA_NCNN=ON")
                targets += listOf("sherpa-ncnn-jni")
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    buildTypes {
        debug {
            val escapedToken = escapedBuildConfigString(githubToken)
            buildConfigField("String", "GITHUB_TOKEN", "\"$escapedToken\"")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/io.netty.versions.properties"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

configurations.all {
    // 保留 org.jetbrains:annotations（显式声明版本），仅排除 org.intellij:annotations 避免重复
    exclude(group = "org.intellij", module = "annotations")
    // 旧库可能引入的 java5 版本注解，统一移除
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:prompt"))
    implementation(project(":core:shizuku"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:updates"))

    // Shizuku - 虚拟屏核心依赖
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // HiddenApiBypass - 放宽隐藏 API 限制（虚拟屏创建必需）
    implementation(libs.hiddenapibypass)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)

    // 协程
    implementation(libs.kotlinx.coroutines.android)

    // 不可变集合
    implementation(libs.kotlinx.collections.immutable)

    // 网络与序列化
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    
    // Koin - 依赖注入
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    
    // Coil - 图片加载（既有代码用 Coil 2，新 Markdown 模块用 Coil 3）
    implementation(libs.coil.compose)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    
    // Lucide 图标
    implementation(libs.compose.icons.lucide)
    
    // Markdown 渲染器（mikepenz - 过渡期保留）
    implementation(libs.multiplatform.markdown.renderer)
    
    // 自托管 Markdown 渲染模块依赖
    implementation(libs.jetbrains.markdown)          // JetBrains AST parser
    implementation(libs.quickjs.kt)                  // QuickJS + Prism.js highlighting
    implementation(libs.jlatexmath.android)          // JLatexMath for Android
    implementation(libs.jsoup)                       // HTML parsing
    
    // 测试依赖
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    
    // ViewModel 和 LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)

    // PDF 处理
    implementation(libs.itext7.core)

    // Office 文档解析（doc/docx/ppt/pptx/xls/xlsx）
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)
    implementation(libs.apache.poi.scratchpad)
}
