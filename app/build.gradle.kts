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
        // v6：否决 Mirage 移植版（太丑），回退到自研 AGSL 星云球，继续在自研版上优化。
        versionCode = 6
        versionName = "0.0.5test"
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
