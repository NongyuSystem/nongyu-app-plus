# 应用级 R8 规则（release 开启 isMinifyEnabled + isShrinkResources 后生效）
#
# 现状：本工程没有反射 / 序列化框架 / JNI / XML 自定义 View 膨胀，
# 下面三类 keep 已由工具链自动覆盖，不需要手写：
#   1. AGP 依据 AndroidManifest 自动生成 keep 规则（四大组件、自定义 View）；
#   2. `proguard-android-optimize.txt` 保留组件、Parcelable、枚举等约定；
#   3. `androidx.compose.runtime` / `ui` 等 AAR 自带 consumer rules。
# 所以此处只放「主工程自己」的兜底规则，按需增长。
#
# 新增以下能力时必须回到这里补 keep，否则典型症状是 debug 正常、release 崩：
#   - 反射调用（Class.forName / getDeclaredMethod）、
#   - JSON / 协议序列化（字段名被混淆后对不上）、
#   - JNI（native 方法名与类名绑定）、
#   - 只在 XML / 字符串里出现过类名的入口。

# 保留行号信息：release 崩溃栈仍能定位到源码行。
# 不保留 SourceFile 的真实文件名（用 SourceFile 占位），避免泄漏本机路径。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# 网络层（N7 实测补入，2026-11-09）
#
# 背景：在「网络层没有任何从入口点可达的调用」时，R8 会把 okhttp3 / retrofit2 /
# kotlinx.serialization / tink 整条链删光（mapping.txt 保留 0 个类），
# 那时 assembleRelease 一律成功，但**这不是 keep 规则够用的证据**。
# 接上真实调用（NetStartupProbe → NetHolder → NongyuApi）之后，R8 立刻报出：
#
#   ERROR: R8: Missing class com.google.errorprone.annotations.CanIgnoreReturnValue
#   (referenced from: com.google.crypto.tink.KeysetManager ...)
#
# 根因：tink-android 的编译期用了 errorprone 注解，但注解类不在运行时 classpath 上。
# 这些注解**只在编译期起作用**，运行时不需要，所以 -dontwarn 是正确处置
# （不是 keep）。规则由 AGP 生成在
# app/build/outputs/mapping/release/missing_rules.txt。
# ---------------------------------------------------------------------------
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# kotlinx.serialization：自带 consumer rules 已覆盖常规 @Serializable 类。
# 但**具名 companion object** 的 serializer 不被自动规则覆盖，需手工补。
# 见 https://github.com/Kotlin/kotlinx.serialization/blob/master/README.md#android
# 当前网络层 DTO 都无具名 companion，故本节暂不添加具体规则；
# 一旦新增这类 DTO，必须回来补：
#   -keep class com.nongyu.appplus.net.model.** { public static ** Companion; }

