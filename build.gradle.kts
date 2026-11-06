// 根构建脚本：仅声明插件版本，不在此处应用，具体应用见各模块
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
