plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // 票 #48 实测：AGP 9 内置 Kotlin 下按目录 alias 声明即可，不需要 buildscript classpath。
    alias(libs.plugins.kotlin.serialization)
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
            // 调试包保持不收缩，保证断点/栈可读
            isMinifyEnabled = false
            // 调试期兜底 token（票 #43 Q5=A / 规格 §2.3）。
            // 只在 debug 变体声明 → release 里连这个符号都不存在，是结构性隔离，
            // 不依赖任何运行时 if (BuildConfig.DEBUG) 判断。
            // 默认空串：设了才有值，避免把真实 token 写进仓库。
            // 用法：./gradlew :app:assembleDebug -PdevToken=<你的 token>
            //      或写入 ~/.gradle/gradle.properties 的 devToken=（不进仓库）
            buildConfigField(
                "String",
                "DEV_TOKEN",
                "\"${providers.gradleProperty("devToken").orNull ?: ""}\"",
            )
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
        // 皮肤包要判定 minAppVersion（ADR-0003：判定权在客户端），
        // 启动路径上需要读到当前 versionCode，故开启 BuildConfig。
        buildConfig = true
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

    // --- 网络层（票 #48 spike 实测通过）---
    // Retrofit 3.0.0 的 POM 自带 okhttp 4.12.0，这里的 4.12.1 显式覆盖它。
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    // coroutines 显式声明：实测本工程本就解析到 1.9.0，钉死后零抬升、零体积增量。
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // debug 请求日志。与 okhttp 严格同版本；release 侧 level=NONE（隔源集装配见 net/http）。
    implementation(libs.okhttp.logging.interceptor)
    // 最小会话存储（Keystore + AES）。引入由维护者 2026-11-09 拍板，
    // 见 docs/specs/网络层.md §10.4；会拖 tink-android:1.8.0。
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    // MockWebServer：假服务端，纯 JVM 可跑（不编译 Android 运行时）。
    // testImplementation 不进 runtime classpath，R8 看不到，不会污染 release。
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
