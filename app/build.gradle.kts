import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dokka)
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
    ndkVersion = "27.0.12077973"
    buildToolsVersion = "36.1.0"

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

    // Immutable Collections
    implementation(libs.kotlinx.collections.immutable)

    // 网络与序列化
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)
    implementation(libs.logto.android)

    // 后台任务（便于自动化/定时流程）
    implementation(libs.androidx.work.runtime.ktx)

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
    testImplementation(libs.robolectric)
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

dokka {
    moduleName.set("Aries AI Phone Agent")
    dokkaSourceSets.configureEach {
        skipDeprecated.set(true)
        reportUndocumented.set(false)
        perPackageOption {
            matchingRegex.set(".*\\.(internal|core\\.security|core\\.cache|net)\\..*")
            suppress.set(true)
        }
        sourceLink {
            localDirectory.set(file("src/main/java"))
            remoteUrl.set(uri("https://github.com/AriesAI/aries-phone-agent/tree/main/app/src/main/java"))
            remoteLineSuffix.set("#L")
        }
    }
}
