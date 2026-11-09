// 探针独立工程（wayfinder #30）。刻意与 :app 完全隔离：
// 本文件存在即代表这是一个独立 Gradle 构建，主工程的 settings.gradle.kts 只 include(":app")，不会把这里的模块拉进去。
// 工具链必须与主工程完全同版本，否则测出的数字不能外推到 nongyu-app-plus。
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 刻意不设 FAIL_ON_PROJECT_REPOS。
    // 原因：本机 ~/.gradle/init.d/aliyun-mirrors.gradle 会用 allprojects{repositories{}} 注入阿里云镜像，
    // 那种注入属于「project repositories」；一旦这里设 FAIL_ON_PROJECT_REPOS，就会直接构建失败：
    //   "Build was configured to prefer settings repositories over project repositories
    //    but repository 'maven' was added by initialization script '.../aliyun-mirrors.gradle'"
    // 主工程用「干净 GRADLE_USER_HOME（无 init.d）」来规避，但探针工程应当能在
    // 任意一台装了该 init.d 的机器上直接跑，因此这里改为与 init.d 共存。
    // 仓库声明的真源仍是下面 settings 里的 google()/mavenCentral()。
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "nongyu-perf-probe"

include(":app")
