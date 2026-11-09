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
