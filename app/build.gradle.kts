plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    buildTypes {
        debug {
            val escapedToken = githubToken.replace("\\", "\\\\").replace("\"", "\\\"")
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
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    
    // Immutable Collections
    implementation(libs.kotlinx.collections.immutable)

    // 网络与序列化
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

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
    
    // Coil - Image Loading
    implementation(libs.coil.compose)
    
    // Lucide Icons
    implementation(libs.compose.icons.lucide)
    
    // Markdown Renderer
    implementation(libs.multiplatform.markdown.renderer)
    implementation(libs.multiplatform.markdown.renderer.code)

    // LaTeX Renderer
    implementation(libs.latex.renderer)

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
