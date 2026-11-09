# 怎么跑测试

本工程只有**纯 JVM 单测**（`app/src/test/java/`），基于 junit 4；没有 instrumented 测试，
不需要模拟器或真机。

```bash
./gradlew :app:testDebugUnitTest
```

本机（Windows）**必须**按下面的方式跑，否则构建会在 Gradle 配置阶段就失败：

```powershell
Set-Location "D:\IT\program\web-program\nongyu-system\apps\nongyu-app-plus"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"          # JDK 21 会报 Module jdk.internal.vm.ci not found
$env:ANDROID_HOME = "C:\Users\LENOVO\AppData\Local\Android\Sdk"
$env:GRADLE_USER_HOME = Join-Path $env:TEMP "nongyu-gradle-home"   # 干净目录，无 init.d
$gradle = "C:\Users\LENOVO\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat"
& $gradle :app:testDebugUnitTest --no-daemon --console=plain
```

三个硬约束的原因：

1. **JDK 17** —— JDK 21 下构建报 `Module jdk.internal.vm.ci not found`。
2. **干净 `GRADLE_USER_HOME`** —— 本机 `~/.gradle/init.d/aliyun-mirrors.gradle` 会向 project 注入
   repositories，与 `settings.gradle.kts` 的 `FAIL_ON_PROJECT_REPOS` 冲突。`--init-script` 传空脚本
   **不能**屏蔽 init.d，只能换 home 目录。
3. **直接调 `gradle.bat`** —— 本机 `gradlew.bat` 会去校验 / 下载 wrapper zip 并超时；直接调用已解压的
   gradle 最稳。

报告位置：`app/build/reports/tests/testDebugUnitTest/index.html`。

测试约定（规格 #33 的 Testing Decisions）：只断言**外部行为**，不测实现细节；业务断言落在
「皮肤数据源接口」这个唯一主接缝上。`TestInfraSmokeTest` 只负责证明基建通了，不覆盖业务逻辑。
