# targetSdk=36 下的明文 HTTP：官方默认、debug 例外与 127.0.0.1

> 对应 GitHub issue #44。本文件**纯事实核查**，不改任何构建文件。
> 核查对象：本工程 `app/build.gradle.kts`（`compileSdk = 36` / `minSdk = 26` / `targetSdk = 36`）。
> 核查时间：2026-11-09。

---

## 1. 结论速览

| # | 结论 | 置信度 |
|---|---|---|
| 1 | `targetSdk >= 28` 起本项目**默认禁止明文 HTTP**，且这是平台层强制（不是库层），`targetSdk = 36` 适用。 | 一手确证 |
| 2 | **`127.0.0.1` / `localhost` 在 API 28–36 上「不被默认放行」**。 | 一手确证 |
| 3 | 隐式 localhost 放行**确实存在，但门槛是「运行平台 >= Android 17 (API 37)」，不是 targetSdk**，且要求「应用没有为 localhost 定义过配置」。本项目在 API 26–36 设备上**拿不到这个豁免**。 | 一手确证 |
| 4 | `debug-overrides` **不能**开明文，它只搬信任锚（源码级证据）。要开明文必须写 `cleartextTrafficPermitted="true"`。 | 一手确证 |
| 5 | **推荐形态**：`src/debug/res/xml/network_security_config.xml` + `src/debug/AndroidManifest.xml` 用 `tools:replace="android:networkSecurityConfig"`。本工程 release 未设该属性，故 `tools:replace` 实际可省，但写上更稳。 | 一手确证（机制）+ 工程现状 |
| 6 | OkHttp 与 `HttpURLConnection` **行为一致**：都读同一个平台策略对象；OkHttp 只是自己再加一道同源判断。 | 一手确证 |
| 7 | `adb reverse` **绕不开**这条限制 —— 它把流量变成 127.0.0.1，而 127.0.0.1 正是被禁的。真机联调必须显式放行。 | 推导（附一手依据） |
| 8 | TLS 1.2 在 API 26 上**默认启用**，无需任何配置。 | 一手确证 |
| 9 | 证书固定（pinning）**本期不建议做**，属可选加固且有运维代价。 | 官方选项 + 代价 |

**一句话给规格**：debug 包必须带一份显式 `cleartextTrafficPermitted="true"` 的网络安全配置，且**必须把 `127.0.0.1`、`localhost`、`10.0.2.2` 逐个列进 `<domain>`**（或直接用 `<base-config cleartextTrafficPermitted="true">` 只在 debug 源集生效）。`usesCleartextTraffic="true"` 单独用**会被网络安全配置覆盖**；`debug-overrides` **帮不上忙**。

---

## 2. 逐条事实 + 一手引用

### 2.1 默认禁明文的确切条文

**官方文档原文**（[应用元素 `android:usesCleartextTraffic`](https://developer.android.google.cn/guide/topics/manifest/application-element?hl=zh-cn)）：

> 对于目标 API 级别为 27 或更低级别的应用，默认值为 `"true"`。对于目标 API 级别为 28 或更高级别的应用，默认值为 `"false"`。

**AOSP 源码主分支**（`frameworks/base/core/java/com/android/internal/pm/pkg/parsing/ParsingPackageUtils.java:2446`）：

```java
.setCleartextTrafficAllowed(bool(targetSdk < Build.VERSION_CODES.P, R.styleable.AndroidManifestApplication_usesCleartextTraffic, sa))
```

`Build.VERSION_CODES.P = 28`（`frameworks/base/core/java/android/os/Build.java:1212`）。即：**manifest 没写该属性时，默认值 = `targetSdk < 28`**。本项目 `targetSdk = 36` → 默认 `false`。

**同时的策略落地点**（`NetworkSecurityConfig.java:193-202`，`frameworks/base/core/java/android/security/net/config/`）：

```java
public static Builder getDefaultBuilder(ApplicationInfo info) {
    // System certificate store, does not bypass static pins, does not disable CT.
    CertificatesEntryRef systemRef = new CertificatesEntryRef(
            SystemCertificateSource.getInstance(), false, false);
    Builder builder = new Builder()
            .setHstsEnforced(DEFAULT_HSTS_ENFORCED)
            .addCertificatesEntryRef(systemRef);
    final boolean cleartextTrafficPermitted = info.targetSdkVersion < Build.VERSION_CODES.P
            && !info.isInstantApp();
    builder.setCleartextTrafficPermitted(cleartextTrafficPermitted);
    ...
}
```

其上游注释（同文件 `173-189`）写得很直白：

> `<li>If the application targets API level 27 (Android O MR1) or lower then cleartext traffic is allowed by default.</li>`
> `<li>Cleartext traffic is not permitted for ephemeral apps.</li>`

**注意一个易错点**：`NetworkSecurityConfig.DEFAULT_CLEARTEXT_TRAFFIC_PERMITTED = true`（同文件 `:38`），但它**不是**应用默认值 —— 它是 `Builder` 未显式设值时的兜底，而 `getDefaultBuilder()` 一定会显式调用 `setCleartextTrafficPermitted(...)`。所以**不要**拿这个常量当「默认放行」的证据。

### 2.2 `usesCleartextTraffic` 与 `networkSecurityConfig` 的优先级

**官方文档明确写了覆盖关系**（[应用元素](https://developer.android.google.cn/guide/topics/manifest/application-element?hl=zh-cn)）：

> 如果存在 Android 网络安全配置，则会在 Android 7.0（API 级别 24）及更高版本上忽略此标志。

**AOSP 源码印证这个分支**（`ManifestConfigSource.java:58-80`）：

```java
int configResource = mApplicationInfo.networkSecurityConfigRes;
ConfigSource source;
if (configResource != 0) {
    boolean debugBuild =
            (mApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    ...
    source = new XmlConfigSource(mContext, configResource, mApplicationInfo);
} else {
    ...
    // the legacy FLAG_USES_CLEARTEXT_TRAFFIC is not supported for Ephemeral apps
    boolean usesCleartextTraffic =
            (mApplicationInfo.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0
            && !mApplicationInfo.isInstantApp();
    source = new DefaultConfigSource(usesCleartextTraffic, mApplicationInfo);
}
```

**结论**：
- 设了 `android:networkSecurityConfig` → **走 XML，manifest 标志被忽略**（XML 里的 `cleartextTrafficPermitted` 说了算）。
- 没设 → 用 `usesCleartextTraffic`（其默认值见 2.1）。

⚠️ 这是本票最容易写错的一条：很多人以为「`usesCleartextTraffic="true"` 写了就都通了」。一旦你同时引入网络安全配置文件（本票推荐的做法），**`usesCleartextTraffic` 就失效了**，必须在 XML 里放行。

### 2.3 ★ `127.0.0.1` / `localhost` / `10.0.2.2` 是否被默认放行

**确定答案：在 `targetSdk = 36` 且平台版本 API 28–36 上 —— 都不默认放行。**

#### 证据一：配置包内没有任何 localhost 特例

对 `frameworks/base/core/java/android/security/net/config/` 下全部实现文件（`NetworkSecurityConfig`、`ApplicationConfig`、`ManifestConfigSource`、`XmlConfigSource`、`NetworkSecurityConfigProvider`、`Domain`）做全文检索，**`localhost` / `127.0.0.1` / `loopback` / `10.0.2.2` 命中 0 次**。

`ApplicationConfig.getConfigForHostname()`（`ApplicationConfig.java:75-119`）是唯一的主机名分派点，逻辑只有三种出口：

```java
public NetworkSecurityConfig getConfigForHostname(String hostname) {
    ensureInitialized();
    if (hostname == null || hostname.isEmpty() || mConfigs == null) {
        return mDefaultConfig;
    }
    ...
    // Check for an exact match.
    if (domain.hostname.equals(hostname)) {
        return config;
    }
    ...
    // If no match was found use the default configuration.
    return mDefaultConfig;
}
```

没有任何「若为回环地址则放行」的分支。`127.0.0.1` 走的是 `mDefaultConfig`，而 `mDefaultConfig` 在 `targetSdk>=28` 时由 `getDefaultBuilder()` 设成 `false`（见 2.1）。

**附带一个重要工程含义**：匹配是**纯字符串比较**（`domain.hostname.equals(hostname)`，`Domain` 只做 `toLowerCase`）。所以把 `127.0.0.1` 写进 `<domain>` **是有效的** —— 它不需要能解析成 IP，字面量对上就行。

#### 证据二：隐式 localhost 豁免的门槛是「平台版本 ≥ API 37」，不是 targetSdk

官方 [网络安全配置](https://developer.android.google.cn/privacy-and-security/security-config?hl=en) 有一个独立小节 "Localhost configuration"，原文：

> Enforcing the network security features for localhost connections is generally unnecessary. For example, certificate transparency is rarely needed for localhost connections.
> **From Android 17 (API level 37) and higher**, if no configuration has been defined for localhost, an implicit configuration is included. By default, this configuration does the following:
> - Allows cleartext traffic.
> - Doesn't enforce certificate transparency (CT).
> - Doesn't enforce certificate pinning.
> - Delegates to `<base-config>` for trust anchors.
>
> A configuration is considered to be targeting localhost if the domain is:
> - `localhost`
> - `ip6-localhost` or
> - a numerical IP address and `InetAddress.isLoopback()` is `true` (for example, `127.0.0.1` or `[::1]`)

API 参考里对应的是 `NetworkSecurityConfig.createLocalhostConfig()`，标注 **"Added in version 37.2"**，其说明是：

> Returns a `NetworkSecurityConfig` for localhost. The localhost configuration has the following properties: 1. Cleartext traffic is permitted. 2. Certificate Transparency is disabled.

**这就是坊间说法互相矛盾的根源**，两个说法各自都对一半：

- 说「localhost 默认放行」的人 → 引的是 Android 17+ 的新行为；
- 说「localhost 也被拦」的人 → 描述的是 API 28–36 的历史行为（也就是本工程目标区间）。

三条判据都指向「本项目吃不到」：
1. 门槛写的是 **Android 17 (API level 37) 及更高**（平台版本），不是 targetSdk；
2. 还要满足 **"if no configuration has been defined for localhost"** —— 一旦我们为 debug 写了配置，就落进"已定义"的边；
3. AOSP `main` 分支里根本没有这个 API（该镜像最新提交为 2024-11-29，早于 37.2 的引入），所以本地源码读不到 —— 这解释了「源码里找不到」与「文档说有」的表面冲突。

**`10.0.2.2` 更明确**：它既不在 `localhost` / `ip6-localhost` 名单里，`InetAddress.isLoopback()` 对它是 `false`（它是 QEMU 网关别名，不是回环地址），所以**即便在 Android 17+ 也不在豁免范围内**，必须显式放行。

#### 证据三：官方文档全文检索不到「IP 自动豁免」的说法

对 [网络安全配置](https://developer.android.google.cn/privacy-and-security/security-config?hl=zh-cn) 中文页做全文关键词统计：`127.0.0.1` 命中 **0** 次、`localhost` 命中 **0** 次（该页的 localhost 小节只出现在英文版/新版页面）。历史行为下没有任何「回环地址自动放行」的表述。

#### 二手旁证（明确标注为二手）

- StackOverflow 上有开发者实测报告：即使写了 `<domain includeSubdomains="true">127.0.0.1</domain>`，DownloadManager 仍报 `Cleartext HTTP traffic to 127.0.0.1 not permitted`，最后要靠 `<base-config cleartextTrafficPermitted="true"/>` 才通（[SO 51770323](https://stackoverflow.com/questions/51770323/how-to-solve-android-p-downloadmanager-stopping-with-cleartext-http-traffic-to)）。这侧面支持「127.0.0.1 不被默认放行」。
- OWASP MASTG 给出的「放行 localhost」示例一律是**显式**写 `domain-config`，从未声称默认放行（[MASTG-KNOW-0014](https://mas.owasp.org/MASTG/knowledge/android/MASVS-NETWORK/MASTG-KNOW-0014)）。

### 2.4 ★ `debug-overrides` 不能开明文（源码级证据）

这是一个高频误用点。`XmlConfigSource.java:308-321`：

```java
private void addDebugAnchorsIfNeeded(NetworkSecurityConfig.Builder debugConfigBuilder,
        NetworkSecurityConfig.Builder builder) {
    if (debugConfigBuilder == null || !debugConfigBuilder.hasCertificatesEntryRefs()) {
        return;
    }
    // Don't add trust anchors if not already present, the builder will inherit the anchors
    // from its parent, and that's where the trust anchors should be added.
    if (!builder.hasCertificatesEntryRefs()) {
        return;
    }

    builder.addCertificatesEntryRefs(debugConfigBuilder.getCertificatesEntryRefs());
}
```

**`debug-overrides` 被搬进其他配置的唯一内容是 `CertificatesEntryRef`（信任锚）**。它从头到尾没碰 `cleartextTrafficPermitted`。方法名 `addDebugAnchorsIfNeeded` 本身就是自述。

官方文档口径一致：

> 在 `debug-overrides` 中指定的信任锚会添加到所有其他配置，并且当服务器的证书链使用其中一个仅调试信任锚时不固定证书。

所以 **`<debug-overrides>` 适合「连自签 HTTPS 后端」，不适合「连明文 HTTP 后端」**。后者必须写 `cleartextTrafficPermitted="true"`。

### 2.5 OkHttp 4.12.1 与 `HttpURLConnection` 是否一致

**一致 —— 因为二者读的是同一个平台策略对象。**

OkHttp 4.12.1（`okhttp/src/main/kotlin/okhttp3/internal/platform/AndroidPlatform.kt:103-106`）：

```kotlin
override fun isCleartextTrafficPermitted(hostname: String): Boolean = when {
  Build.VERSION.SDK_INT >= 24 -> NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(hostname)
  Build.VERSION.SDK_INT >= 23 -> NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted
  else -> true
}
```

强制点在 `okhttp3/internal/connection/RealConnection.kt:183-191`：

```kotlin
if (route.address.sslSocketFactory == null) {
  if (ConnectionSpec.CLEARTEXT !in connectionSpecs) {
    throw RouteException(UnknownServiceException(
        "CLEARTEXT communication not enabled for client"))
  }
  val host = route.address.url.host
  if (!Platform.get().isCleartextTrafficPermitted(host)) {
    throw RouteException(UnknownServiceException(
        "CLEARTEXT communication to $host not permitted by network security policy"))
  }
}
```

而 `NetworkSecurityPolicy.isCleartextTrafficPermitted(hostname)` 最终走到 `ConfigNetworkSecurityPolicy` → `ApplicationConfig.isCleartextTrafficPermitted(hostname)`（`NetworkSecurityConfigProvider.install()` 里 `libcore.net.NetworkSecurityPolicy.setInstance(new ConfigNetworkSecurityPolicy(config))`）。

**所以**：
- 判定源**唯一**：都是 `android.security.net.config` 这套配置。
- 差别只在**报错形态**：OkHttp 抛 `UnknownServiceException("CLEARTEXT communication to X not permitted by network security policy")`；`HttpURLConnection` 抛 `java.io.IOException: Cleartext HTTP traffic to X not permitted`（平台栈）。
- 你可以用 `NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1")` 在运行时自检，两者结论必然相同。

> 附注：`NetworkSecurityPolicy.isCleartextTrafficPermitted()` 的文档特别说明「best effort」——`java.net.Socket` 裸 socket **不受**此限制约束（因为它无法判断是否明文）。若将来有人用裸 socket 自己实现 HTTP，这道闸门是绕得过的；但 OkHttp / HttpURLConnection 绕不过。

**本工程现状**：`app/build.gradle.kts` 与 `gradle/libs.versions.toml` 里**目前没有任何 HTTP 客户端依赖**（无 OkHttp、无 Retrofit、无 Ktor）。所以「OkHttp 版本行为」这条目前只是**预防性结论**，等真正引入网络层时直接适用（issue 里的 4.12.1 应属沿用 RN 版对照口径）。

### 2.6 `adb reverse tcp:3000 tcp:3000` 是否能绕开

**不能。**

`adb reverse` 的作用是把**设备上的某个端口**转发到**开发机的同名端口**。应用侧看到的地址仍然是 `127.0.0.1:3000`（或 `localhost:3000`）—— 它不改变「这是明文 + 目标是回环地址」这个事实。

于是判定链完全一样：
- 目标是 `127.0.0.1` → `ApplicationConfig.getConfigForHostname("127.0.0.1")` → 无匹配 → `mDefaultConfig` → `targetSdk>=28` → `false` → 拦截。

**结论**：`adb reverse` 解决的是**路由可达性**（真机没有 10.0.2.2、也连不到开发机的 127.0.0.1），**不解决**明文策略。它和网络安全配置是**正交**的两件事，必须同时处理：

1. `adb reverse tcp:3000 tcp:3000` → 让设备上的 127.0.0.1:3000 真的能到开发机；
2. debug 网络安全配置显式放行 `127.0.0.1` → 让平台允许这段明文。

顺带说明为什么这个组合在 RN 版「看起来能用」：RN 的 debug 构建历史上会带 `usesCleartextTraffic="true"`，且 Expo/RN 自己注入 debug 配置；Flutter 同理会注入 `network_security_config.xml`。本工程是纯原生、全新骨架，**没有任何脚手架替你注入**，所以必须自己写。

---

## 3. 可直接抄的 debug 放行配置形态

### 3.1 为什么用「debug 源集」而不是「单个资源 + debug-overrides」

| 方案 | 能否开明文 | 评价 |
|---|---|---|
| 只加 `android:usesCleartextTraffic="true"` | 能，但**一旦引入配置文件即失效**（2.2） | 不推荐：隐式、易被后来者覆盖 |
| 主源集一份配置 + `<debug-overrides>` | **不能**（2.4，只搬信任锚） | 不推荐：误用高发 |
| **`src/debug/` 源集独立配置 + debug manifest** | 能，且 release 完全不受影响 | ✅ 推荐 |

选第 3 种的决定性理由：`src/debug/` 的 manifest 与资源**只进 debug 包**。release 的 `AndroidManifest.xml` 一个字都不用改，也就不存在「忘记删 debug 放行」的风险 —— 这是比「同文件里靠 `debuggable` 分支」更强的保证。

### 3.2 `app/src/debug/res/xml/network_security_config.xml`（整份新建）

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  仅 debug 源集生效，不会进入 release 包。
  用途：允许联调期访问本机 / 模拟器的明文 HTTP 后端。

  为什么必须逐个列出：targetSdk>=28 时平台默认 cleartextTrafficPermitted=false，
  且 API 28-36 上 127.0.0.1 / localhost / 10.0.2.2 均不在隐式豁免范围内
  （隐式 localhost 豁免要以 Android 17 / API 37 为门槛）。
  匹配是纯字符串比较，所以直接把 IP 字面量写进 <domain> 是有效写法。
-->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <!-- 真机 + adb reverse，或模拟器直连宿主机 -->
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
        <!-- Android 模拟器访问宿主机的固定别名 -->
        <domain includeSubdomains="false">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

**要点**：
- `includeSubdomains="false"`：`127.0.0.1` 这类 IP 没有子域概念，设 `false` 语义更准确（设 `true` 也不会报错，但没意义）。
- 三个 `<domain>` 必须**互不重复**，否则 `parseDomain()` 抛 `"has already been specified"`（`XmlConfigSource.java:168-170`）。
- 没有写 `<base-config>` —— 平台默认（禁明文）继续生效，只有上面三个地址被放行，**范围最小**。
- 如果需要「真机同网段的局域网 IP」（如 `192.168.x.y:3000`），必须**再补一条** `<domain>`，写具体 IP。这正是 debug 配置要跟机器走、可能要手工改的地方；如果嫌烦，可退化为整个 `<base-config cleartextTrafficPermitted="true">`（范围更大，仅 debug 可接受）。

**退化写法**（联调 IP 频繁变化时用，安全性略降但仍在 debug 边界内）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### 3.3 `app/src/debug/AndroidManifest.xml`（整份新建）

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:networkSecurityConfig="@xml/network_security_config"
        tools:replace="android:networkSecurityConfig" />

</manifest>
```

**关于 `tools:replace` 的准确性说明**（重要，别照抄错的理由）：

`tools:replace` 的语义是「用高优先级清单的值替换低优先级清单中的同名属性」（[合并清单](https://developer.android.google.cn/studio/build/manifest-merge?hl=zh-cn)）。而合并优先级是：**build 变体清单（`src/debug/`）> 应用主清单（`src/main/`）**。

- **本工程当前 `src/main/AndroidManifest.xml` 并没有 `android:networkSecurityConfig`**，所以严格说**不存在冲突**，`tools:replace` 在当前状态下是**冗余但无害**的。
- 保留它的价值：防止将来主清单里有人补上 `android:networkSecurityConfig="@xml/..."` 时，两边取值不同触发合并冲突（`Value A / Value B → 冲突错误`），届时会直接构建失败。
- 如果你更喜欢「无冲突就不写」，可以去掉 `tools:replace`，只留 `android:networkSecurityConfig`。两种写法产出的 debug APK 一致。

> 注意：**不要**用 `tools:replace` 去覆盖 `android:usesCleartextTraffic` 来达到目的 —— 按 2.2，一旦存在网络安全配置，那个标志本来就被忽略，改它没有意义。

### 3.4 验证方式（落地后自检）

```bash
# 1. 确认 debug 包里属性合并正确
./gradlew :app:processDebugMainManifest
grep -n "networkSecurityConfig" app/build/intermediates/merged_manifest/debug/AndroidManifest.xml

# 2. 确认 release 包没有被污染（应为空）
./gradlew :app:processReleaseMainManifest
grep -c "networkSecurityConfig" app/build/intermediates/merged_manifest/release/AndroidManifest.xml
# 期望输出 0

# 3. 运行时自检（在 debug 包内打日志或断点）
#    NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1") == true
```

---

## 4. TLS 1.2 在 API 26 的默认状态

**结论：默认启用，无需任何配置。**

Conscrypt（Android 的 TLS 提供者）源码 `common/src/main/java/org/conscrypt/NativeCrypto.java:1534-1536`：

```java
public static String[] getDefaultProtocols() {
    return TLSV13_PROTOCOLS.clone();
}
```

而 `TLSV13_PROTOCOLS` 在两支分支下**都包含 TLS 1.2**（`NativeCrypto.java:1482-1497`）：

```java
if (deprecated) {
    TLSV12_PROTOCOLS = new String[] {
            SUPPORTED_PROTOCOL_TLSV1_2,
    };
    TLSV13_PROTOCOLS = new String[] {
            SUPPORTED_PROTOCOL_TLSV1_2,
            SUPPORTED_PROTOCOL_TLSV1_3,
    };
} else {
    TLSV12_PROTOCOLS = new String[] {
            DEPRECATED_PROTOCOL_TLSV1,
            DEPRECATED_PROTOCOL_TLSV1_1,
            SUPPORTED_PROTOCOL_TLSV1_2,
    };
    TLSV13_PROTOCOLS = new String[] {
            DEPRECATED_PROTOCOL_TLSV1,
            DEPRECATED_PROTOCOL_TLSV1_1,
            SUPPORTED_PROTOCOL_TLSV1_2,
            SUPPORTED_PROTOCOL_TLSV1_3,
    };
}
```

调用链：`SSLParametersImpl`（`common/src/main/java/org/conscrypt/SSLParametersImpl.java:178-190`）在调用方未指定协议时走 `NativeCrypto.getDefaultProtocols()`。

**含义**：
- TLS 1.2 **不需要**像 2016 年那样手工 `setEnabledProtocols(new String[]{"TLSv1.2"})`。那是针对 API ≤ 19 的老写法。
- Android 10+ 还会把 TLS 1.3 一并启用（官方：`在 Android 10 及更高版本中，系统默认会为所有 TLS 连接启用 TLS 1.3`）。API 26 设备上只有 TLS 1.2 可选，但同样默认开启。
- 唯一注意点：TLS 1.0/1.1 在较新 Conscrypt 里被标记为 deprecated/过滤（`Platform.java:82-84` 的 `DEPRECATED_TLS_V1` / `FILTERED_TLS_V1`）。**本项目 `minSdk = 26`，不需要为老协议做任何兼容**。

**顺带结论：本项目根本不需要为 TLS 写网络安全配置。** 只有当目标服务器证书有问题（自签、缺中间证书、过期）时才需要 `<trust-anchors src="user">` 或 `<certificates src="@raw/xxx">` —— 那是 2.4 里 `debug-overrides` 的正确用途。

### 关于证书固定（pinning）：本期不做

官方给的选项与代价：

| 选项 | 做法 | 代价 |
|---|---|---|
| 静态 pin | `<pin-set>` 里写公钥哈希（`SubjectPublicKeyInfo`） | 证书轮换 = 老版本 App 立刻全挂；必须预留 backup pin 并提前发版 |
| `<pin-set expiration="...">` | 设过期日后自动停止固定 | 只能缓解，仍需运维节奏配合 |
| CA 白名单 | `<trust-anchors>` 只留自己的 CA | 影响面小于 pin，但仍需证书生命周期管理 |

**本期建议不做**，理由：
1. 本工程**当前没有网络层**（2.5），没有可 pin 的端点；
2. RN 版用的是 Cloudflare 隧道随机子域（见 `apps/nongyu-rn-app/src/config/env.ts` 注释），域名不固定，pin 会立刻自伤；
3. pinning 的错误配置后果是「线上直接不可用」，属于必须有发布流程配合的加固，不适合在搭骨架阶段顺手做。

---

## 5. 待核实清单

| # | 事项 | 状态 | 说明 |
|---|---|---|---|
| 1 | `createLocalhostConfig()` / 隐式 localhost 配置在 AOSP 的实际实现 | **待核实** | GitHub 上的 `aosp-mirror/platform_frameworks_base` 最新提交停在 2024-11-29，早于 API 37.2 引入该 API。`android.googlesource.com` 本机不可达，读不到新源码。**但官方文档的门槛表述（Android 17 / API 37）已足以支撑「本项目不适用」的结论。** |
| 2 | API 37 上「已定义 localhost 配置」的判定粒度 | **待核实** | 文档说 "if no configuration has been defined for localhost"。若我们只在 debug 配置里写了 `<domain>10.0.2.2</domain>`（未涉及 localhost），在 API 37+ 上是否仍触发隐式豁免？文档未细化。**对本项目无实际影响（目标区间 26–36，且 debug 已显式放行 127.0.0.1）**。 |
| 3 | `10.0.2.2` 在 API 37+ 的归属 | **高置信推断** | 依据文档列举的三类（`localhost` / `ip6-localhost` / 数值 IP 且 `isLoopback()`），`10.0.2.2` 均不满足，故仍需显式放行。未找到直接点名 `10.0.2.2` 的官方条文。 |
| 4 | 真机走 `adb reverse` 时的主机名形态 | 待定 | 取决于将来代码里写 `http://127.0.0.1:3000` 还是 `http://localhost:3000`。两种都已在上面的配置里覆盖。 |
| 5 | OkHttp 4.12.1 在 API 26 上走哪条 `when` 分支 | 已明确 | `SDK_INT >= 24` → 分支 1，用带 hostname 的重载。无待核。 |
| 6 | 本工程将来引入的 HTTP 客户端 | 待定 | 当前无任何 HTTP 依赖。引入后本节 2.5 的结论直接适用（只要它走 `NetworkSecurityPolicy`）。若引入 Ktor 的 CIO 引擎（纯 socket），需单独复核是否遵守该策略。 |

---

## 6. 引用链接列表 + 核查通道说明

### 6.1 实际取证通道（★ 上一轮卡在这里）

`developer.android.com` **本机依旧不可达**，本轮复测结果：

```
curl https://developer.android.com/privacy-and-security/security-config
→ curl: (28) Failed to connect to developer.android.com:443 after 22229 ms
```

`android.googlesource.com` 同样不可达：

```
curl https://android.googlesource.com/platform/libcore/...
→ curl: (28) Failed to connect to android.googlesource.com:443 after 21221 ms
```

**本轮换来的一手通道（全部实测可用）**：

| 通道 | 用途 | 结果 |
|---|---|---|
| **`developer.android.google.cn`（官方中文/英文镜像）** | 官方文档正文（`?hl=zh-cn` / `?hl=en`） | ✅ 200，抓到 `security-config`(307KB)、`application-element`(28KB)、`NetworkSecurityConfig` API 参考(168KB)、`NetworkSecurityPolicy` API 参考(172KB)、`manifest-merge`(20KB)、`security-ssl`(13KB) |
| **`gh api repos/aosp-mirror/platform_frameworks_base/contents/...`（AOSP 源码镜像）** | 默认值判定、localhost 特例检索、debug-overrides 语义 | ✅ 取到 `NetworkSecurityConfig.java`(373行)、`ApplicationConfig.java`(215)、`XmlConfigSource.java`(448)、`ManifestConfigSource.java`(106)、`Domain.java`、`NetworkSecurityConfigProvider.java`、`ParsingPackageUtils.java`(3642)、`Build.java`、`attrs_manifest.xml`(4067) |
| **`gh api repos/AndroidSDKSources/android-sdk-sources-for-api-level-34`** | 执行层 `ConfigNetworkSecurityPolicy` / `FrameworkNetworkSecurityPolicy` | ✅ 拿到两份策略实现，确认按-hostname 委托 |
| **`gh api repos/square/okhttp`（tag `parent-4.12.1`）** | OkHttp 明文判定与抛错点 | ✅ `AndroidPlatform.kt:103`、`RealConnection.kt:183` |
| **`gh api repos/google/conscrypt`** | TLS 1.2 默认启用 | ✅ `NativeCrypto.java:1481-1536`、`SSLParametersImpl.java:178`、`Platform.java:82-102` |
| **`web_search`** | 定位「localhost 说法为何矛盾」的线索 | ⚠️ 二手，仅作线索；结论已用官方原文回证 |

**关键方法论**：这轮能破局靠的是**换镜像而不是换话题** —— `developer.android.google.cn` 是 Google 官方内容同源镜像（页面自己标注「Google 会使用 AI 技术将内容翻译成您偏好的语言」），属于一手来源的可达副本，不是第三方转述。

**教训（写给下一轮）**：`developer.android.com` 不通时，先试 `.google.cn` 镜像，不要直接降级到「按常识」。

### 6.2 引用列表

**官方文档（一手，经 `developer.android.google.cn` 镜像获取）**
1. [网络安全配置](https://developer.android.google.cn/privacy-and-security/security-config?hl=zh-cn) — `debug-overrides` 语义、各级默认 `base-config`、明文停用说明
2. [网络安全配置（英文版，含 Localhost configuration 小节）](https://developer.android.google.cn/privacy-and-security/security-config?hl=en) — ★「From Android 17 (API level 37) and higher」隐式 localhost 配置
3. [`<application>` 元素](https://developer.android.google.cn/guide/topics/manifest/application-element?hl=zh-cn) — `usesCleartextTraffic` 默认值与「有配置则被忽略」
4. [`NetworkSecurityConfig` API 参考](https://developer.android.google.cn/reference/android/security/net/config/NetworkSecurityConfig?hl=zh-cn) — `createLocalhostConfig()` "Added in version 37.2"
5. [`NetworkSecurityPolicy` API 参考](https://developer.android.google.cn/reference/android/security/NetworkSecurityPolicy?hl=zh-cn) — `isCleartextTrafficPermitted` best-effort 说明
6. [合并清单文件](https://developer.android.google.cn/studio/build/manifest-merge?hl=zh-cn) — 合并优先级与 `tools:replace`
7. [传输层安全](https://developer.android.google.cn/training/articles/security-ssl?hl=zh-cn) — TLS 1.3 在 Android 10+ 默认启用

**AOSP 源码（一手，经 GitHub 镜像，`ref=main`）**
8. `frameworks/base/core/java/android/security/net/config/NetworkSecurityConfig.java` — `:38` 常量、`:173-211` `getDefaultBuilder()`（★核心判定 `:200-202`）
9. `.../net/config/ApplicationConfig.java` — `:75-119` `getConfigForHostname()`（无 localhost 特例）、`:134-153` `isCleartextTrafficPermitted()`
10. `.../net/config/XmlConfigSource.java` — `:154-172` `parseDomain()`（字符串比较、禁重复）、`:249-253` `cleartextTrafficPermitted` 属性、`:308-321` `addDebugAnchorsIfNeeded()`（★只搬信任锚）
11. `.../net/config/ManifestConfigSource.java` — `:58-80` 配置资源 vs `usesCleartextTraffic` 分支
12. `.../net/config/Domain.java` — 仅 `toLowerCase`，无 IP 解析
13. `.../net/config/NetworkSecurityConfigProvider.java` — `:38-46` `install()` 装配 `ConfigNetworkSecurityPolicy`
14. `frameworks/base/core/java/com/android/internal/pm/pkg/parsing/ParsingPackageUtils.java` — `:2446` `setCleartextTrafficAllowed(bool(targetSdk < P, ...))`（★真正的默认值注入点）
15. `frameworks/base/core/java/android/os/Build.java` — `:1212` `VERSION_CODES.P = 28`
16. `frameworks/base/core/res/res/values/attrs_manifest.xml` — `:2068-2077` `usesCleartextTraffic` 属性文档

**OkHttp / Conscrypt 源码（一手）**
17. okhttp `parent-4.12.1` `okhttp/src/main/kotlin/okhttp3/internal/platform/AndroidPlatform.kt:103-108` — 委托平台策略
18. okhttp `parent-4.12.1` `okhttp/src/main/kotlin/okhttp3/internal/connection/RealConnection.kt:183-191` — 抛错点
19. conscrypt `common/src/main/java/org/conscrypt/NativeCrypto.java:1481-1536` — `getDefaultProtocols()` / `TLSV13_PROTOCOLS`
20. conscrypt `common/src/main/java/org/conscrypt/SSLParametersImpl.java:178-190` — 默认协议装配
21. conscrypt `android/src/main/java/org/conscrypt/Platform.java:82-102` — TLS 1.0 过滤开关
22. Android SDK sources (`android-sdk-sources-for-api-level-34`) `android/security/net/config/ConfigNetworkSecurityPolicy.java` — 按 hostname 委托 `ApplicationConfig`

**二手（仅作线索，结论均已回证）**
23. [SO 51770323 — DownloadManager 报 127.0.0.1 not permitted](https://stackoverflow.com/questions/51770323/how-to-solve-android-p-downloadmanager-stopping-with-cleartext-http-traffic-to)
24. [OWASP MASTG-KNOW-0014 — Android 网络安全配置](https://mas.owasp.org/MASTG/knowledge/android/MASVS-NETWORK/MASTG-KNOW-0014)

### 6.3 本工程现状快照（核查时点）

```
app/build.gradle.kts:  compileSdk = 36, minSdk = 26, targetSdk = 36
app/src/main/AndroidManifest.xml:  无 android:networkSecurityConfig，无 android:usesCleartextTraffic
app/src/:  仅 main / test（无 debug 源集）
app/src/main/res/xml/:  仅 backup_rules.xml、data_extraction_rules.xml
HTTP 依赖:  无（libs.versions.toml 中无 okhttp / retrofit / ktor）
RN 对照:   apps/nongyu-rn-app/src/config/env.ts 默认 "http://127.0.0.1:3000"、埋点 ":8082"
```

即：**当前工程一旦开始联调明文后端，必然被拦，且没有任何现成配置兜底。**
