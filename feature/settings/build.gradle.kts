plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ai.phoneagent.feature.settings"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    buildFeatures {
        buildConfig = false
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:prompt"))
    implementation(project(":core:shizuku"))
    implementation(project(":feature:updates"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    
    // Lucide Icons
    implementation(libs.compose.icons.lucide)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    
    // Koin - Dependency Injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
