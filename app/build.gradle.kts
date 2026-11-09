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
        debug {
            // 调试包保持不收缩，保证断点/堆栈可读
            isMinifyEnabled = false
        }
        release {
            // 发布物形态是第一性能杠杆（ADR-0007）：探针实测 debug 冷启动 1164.0ms
            // vs release 384.5ms（3.03 倍），APK 10.4MB vs 804KB（13.3 倍）。
            // 因此 release 必须真的开启 R8 + 资源收缩，否则「发布包」等同于 debug 包。
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 尚未上架应用商店，沿用 debug 签名，让 release 包可以直接装到测试设备上验证；
            // 将来上架时改为读取 keystore.properties（勿把密钥写入仓库）。
            signingConfig = signingConfigs.getByName("debug")
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
