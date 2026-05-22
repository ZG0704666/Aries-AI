plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

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

fun localProperty(name: String, defaultValue: String): String =
    localProperty(name).ifBlank { defaultValue }

fun escapedBuildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.ai.phoneagent"
    compileSdk = 36

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    defaultConfig {
        applicationId = "com.ai.phoneagent"
        minSdk = 30
        targetSdk = 36
        versionCode = 17
        versionName = "v1.4.2-xyla.alpha"

        buildConfigField("String", "GITHUB_TOKEN", "\"\"")
        buildConfigField("String", "ARIES_LOGTO_ENDPOINT", "\"https://sso.aries.org.cn/\"")
        buildConfigField("String", "ARIES_LOGTO_APP_ID", "\"${escapedBuildConfigString(localProperty("aries.logto.appId", "ynaappkxpdyahwo8m81ja"))}\"")
        buildConfigField("String", "ARIES_LOGTO_REDIRECT_URI", "\"io.logto.android://com.ai.phoneagent/callback\"")
        buildConfigField("String", "ARIES_LOGTO_API_RESOURCE", "\"${escapedBuildConfigString(localProperty("aries.logto.apiResource", "https://api.aries.org.cn/"))}\"")
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
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    buildTypes {
        debug {
            val escapedToken = escapedBuildConfigString(githubToken)
            buildConfigField("String", "GITHUB_TOKEN", "\"$escapedToken\"")
            val endpoint = localProperty("aries.logto.endpoint", "https://sso.aries.org.cn/")
            buildConfigField("String", "ARIES_LOGTO_ENDPOINT", "\"${escapedBuildConfigString(endpoint)}\"")
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
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
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
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // HiddenApiBypass - 放宽隐藏 API 限制（虚拟屏创建必需）
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    
    // Immutable Collections
    implementation(libs.kotlinx.collections.immutable)

    // 网络与序列化
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.logto.sdk:android:1.1.3")

    // 后台任务（便于自动化/定时流程）
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    
    // Koin - Dependency Injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    
    // Coil - Image Loading (Coil 2 for existing code, Coil 3 for new Markdown module)
    implementation(libs.coil.compose)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    
    // Lucide Icons
    implementation(libs.compose.icons.lucide)
    
    // Markdown Renderer (mikepenz - kept during transition)
    implementation(libs.multiplatform.markdown.renderer)
    
    // Self-hosted Markdown rendering module deps
    implementation(libs.jetbrains.markdown)          // JetBrains AST parser
    implementation(libs.quickjs.kt)                  // QuickJS + Prism.js highlighting
    implementation(libs.jlatexmath.android)          // JLatexMath for Android
    implementation(libs.jsoup)                       // HTML parsing
    
    // Test Dependencies
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    
    // ViewModel 和 LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)
    
    // PDF 处理
    implementation("com.itextpdf:itext7-core:7.2.5")

    // Office 文档解析（doc/docx/ppt/pptx/xls/xlsx）
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")
}
