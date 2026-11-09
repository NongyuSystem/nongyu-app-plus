plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nongyu.perfprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nongyu.perfprobe"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "probe-1"
    }

    buildTypes {
        // 探针的核心用途之一就是量「debug 与 release 的差距」，
        // 所以两种都保留：debug 与主工程现状一致（未开 R8），release 打开收缩用于对照。
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 探针不上架，用 debug 签名即可安装对照。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 让探针能读到「本次构建是 debug 还是 release」，用于把结果写进日志。
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")

    // 与主工程同一份 BOM，确保 Compose 版本一致。
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
}
