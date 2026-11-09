// 探针根构建脚本。版本与主工程严格对齐（AGP 9.2.1 / Kotlin 2.3.0），
// 否则「在探针上测出的收益」不能说明主工程也会有同样收益。
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
}
