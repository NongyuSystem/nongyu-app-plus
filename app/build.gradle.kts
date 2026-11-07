plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nongyu.appplus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nongyu.appplus"
        minSdk = 26
        targetSdk = 36
        // 每次打包都必须递增 versionCode / versionName，并 commit + push 到 GitHub。
        // v18：修输入框比导航栏窄 —— AnimatedVisibility 未 fillMaxWidth 导致按内容测量。
        versionCode = 18
        versionName = "0.0.17test"
    }

    buildTypes {
        release {
            // 骨架阶段不开启混淆，避免为占位代码维护 keep 规则
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
