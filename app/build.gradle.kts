plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.ai.phoneagent"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GITHUB_TOKEN", "\"\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 网络与序列化
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 后台任务（便于自动化/定时流程）
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}