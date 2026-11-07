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
        // v37：回退课表 —— 恢复到引入课表之前的骨架（仅导航壳 + 星云球）。
        // versionCode 必须大于已发布的 36，否则手机无法覆盖安装，
        // 所以版本号继续递增，不用回旧号。
        versionCode = 37
        versionName = "0.0.29test"
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
