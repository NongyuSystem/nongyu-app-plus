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
        // v35：课表交互与视觉大改 —— 底部避让、滑动切周、卡片化、底部抽屉、
        // 日历选周、今日高亮、切换动画、时间列收窄。
        versionCode = 35
        versionName = "0.1.5test"
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
