# 手机 App 接入五家 OAuth 登录：Android 原生落点调研（nongyu-app-plus）

> 2026-11-09。起因：维护者要求调研「手机 App 接入 QQ / 微信 / Google / X(Twitter) / Apple 五家 OAuth 登录」在 Android 原生客户端的落点。
> 只做资料核查与落点建议，**未引入任何依赖、未改动构建配置 / AndroidManifest / 任何现有文件**。
> 链接以一手来源优先（官方开发者文档、官方 SDK 仓库 / 博客、AOSP / Android Developers）；二手资料仅作线索并显式标注；本机无真机、无各平台账号，凡无法实测的一律写「待核实」。
> 工程约束（读 `README.md` / `app/build.gradle.kts` / `app/src/main/AndroidManifest.xml` 确认，不重复验证）：AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.3.0 / Compose BOM 2025.12.00 / compileSdk=36 / targetSdk=36 / minSdk=26 / JDK 17(21)；`applicationId = com.nongyu.appplus`；单 `:app` 模块；唯一入口 `MainActivity`（Compose 根节点）；当前无网络层、无状态管理、无路由、无依赖注入；`dependencies` 仅 core-ktx / lifecycle-runtime-ktx / activity-compose / Compose BOM 相关 / material3 / junit，**无任何 OAuth SDK**；`AndroidManifest.xml` 目前**甚至没有声明 `android.permission.INTERNET`**。

## 1 结论速览

- **五家不是同一套流程**，必须分成三类，不能合并成一个「OAuth 客户端」抽象：
  1. **厂商 SDK 流程（国产两家，强制或半强制）**：QQ 互联 Android SDK 与微信 Open SDK 都靠**拉起自家 App / 自带 Activity** 完成授权，回调是 **SDK 的 Activity/回调对象**，不是 HTTP redirect。QQ 的 SDK **直接把 `access_token` + `openid` 返回给 App**；微信的 SDK 返回 **code**，再由后端用 AppSecret 换 token。**两家都没有 PKCE**。
  2. **标准 OAuth 2.0 + PKCE 浏览器流程（Google / X）**：App 生成 `code_verifier` / `code_challenge(S256)`、持有 `state`，用系统浏览器打授权页，回调把 **authorization code** 交给 App，再交给后端换 token；App 不持有 `client_secret`。Google 另有一条 **Credential Manager「一键登录」**路径，直接返回 **Google ID token** 给 App，是第三种形态。
  3. **Web/REST 流程（Apple）**：**Apple 没有官方 Android SDK**。官方 REST API 文档原文把 Android 归为「other platform, like Android」，指定走 `Sign in with Apple JS` / REST；Apple 自己的「其他平台」文档明确要求 **Android 用自定义 URL scheme 把控制权交还 App，数据先落在 App 服务端的 `redirect_uri` 端点** —— 即后端中转几乎是官方规定的形态。`ASWebAuthenticationSession` 属于 Apple 平台的 AuthenticationServices 框架，**Android 上不存在**。
- **App 不该持有 `client_secret`、不该长期存第三方 `access_token`**：Google 把 installed app 定义为「无法保守秘密的 public client」并要求 Always use PKCE；微信原文警告 AppSecret 存客户端会被反编译窃取、access_token/refresh_token 存客户端等同登录态泄漏，并「建议将 AppSecret、用户数据（如 access_token）放在 App 云端服务器」；QQ 准备工作文档要求 appid/appkey 保密；Apple 的 `client_secret` 是用 `.p8` 私钥签的 ES256 JWT，天然只能在服务端；X 明确 Native App 为 public client、只用 PKCE。
- **已废弃 / 不再推荐**：`com.google.android.gms:play-services-auth`（legacy Google Sign-In for Android）已被官方标注 deprecated 并将在未来版本移除 Google Sign-In 相关 API，改用 **Android Credential Manager**；**Twitter Kit（含 Android）已于 2018-10-31 停止维护与发行，官方无替代 Android SDK**；Google OAuth 的 **loopback IP redirect 对 mobile apps 已 DEPRECATED**、manual copy/paste 也标 deprecated。
- **本仓当前是骨架期**：接 OAuth 的**阻塞项**是「网络层 + 状态管理/依赖注入 + 路由 + 后端接口契约 + 各平台控制台账号/资质」，且任何 SDK 引入都会打破既有「零新依赖 + BOM 2025.12.00 锁定」约定（该约定在 `docs/research/ai-orb-reference-projects.md` 中由维护者拍板过），**需维护者拍板**。
- 本文凡涉及「后端应该做什么」只写**接口边界假设**，统一标注「**需与后端调研对齐**」，不替后端下结论。

## 2 五家逐节

### 2.1 QQ 互联（Android SDK / 自主开发两套并存）

**流程（官方 SDK 流程）**
- 入口类 `Tencent`：`Tencent.createInstance(APP_ID, context, Authorities)` → `mTencent.login(activity, scope, IUiListener)`；`Authorities` 是 Manifest 里注册 FileProvider 的 authorities 值。SDK 以**弱引用**持有 `IUiListener`，故 listener 必须是成员变量，否则授权页停留过久被回收后回调不执行（[Android_SDK环境搭建](https://wiki.connect.qq.com/qq%E7%99%BB%E5%BD%95/)）。
- 登录成功 `IUiListener.onComplete(JSONObject)` 直接回传 `ret / pay_token / pf / expires_in / openid / pfkey / access_token` —— **App 直接拿到 access_token 与 openid，没有 authorization code、没有 PKCE**（同上）。`isSessionValid()` / `logout()` 由 SDK 管会话。
- 官方 [登录/校验登录态](https://wiki.connect.qq.com/%E7%99%BB%E5%BD%95-%E6%A0%A1%E9%AA%8C%E7%99%BB%E5%BD%95%E6%80%81) 给出 `Tencent.login(...)` 重载（含 `qrcode` 布尔），并写到 `AuthAgent.KEY_FORCE_QR_LOGIN` 强制扫码、返回码 `1=Activity 登录 / 2=H5 登录或显示下载页面` —— **AuthAgent 是 SDK 内部用于扫码/登录态的类，不是给 App 直接调的公开入口**（待核实其公开 API 边界）。3.5.7 起调用其它接口前须先 `Tencent.setIsPermissionGranted(true)`（设备信息授权），否则直接返回失败。
- **是否支持无 SDK 的浏览器 OAuth：支持，且是官方明写的第二条路**。[OAuth2.0简介](https://wiki.connect.qq.com/oauth2-0%E7%AE%80%E4%BB%8B/) 原文：移动应用可「使用 SDK」或「根据 QQ 登录 OAuth2.0 协议自主开发」，并「建议移动应用使用 webview 的方式打开登录页面」；同页写 iOS/Android SDK「均支持 web view 与浏览器两种方式……但浏览器方式用户跳跃较大，不建议使用」。SDK 下载页亦说明「本页面提供的 SDK 全部基于 OAuth2.0 协议，不再支持 OAuth1.0」（[SDK下载](https://wiki.connect.qq.com/sdk%E4%B8%8B%E8%BD%BD/)）。
  - 自主开发的 **Server-side** 路线是标准 `authorization_code`（[开发攻略_Server-side](https://wiki.connect.qq.com/%E5%BC%80%E5%8F%91%E6%94%BB%E7%95%A5_server-side/) / [获取Access_Token](https://wiki.connect.qq.com/%E4%BD%BF%E7%94%A8authorization_code%E8%8E%B7%E5%8F%96access_token/)）；**Client-side** 路线是 **Implicit Grant**：`response_type=token`，`access_token` 走 URL fragment(`#`)，有效期 60 天，再用 `graph.qq.com/oauth2.0/me` 换 openid（[开发攻略_Client-side](https://wiki.connect.qq.com/%E5%BC%80%E5%8F%91%E6%94%BB%E7%95%A5_client-side/)）。**Implicit Grant 会让长效 token 落在 App，与本笔记 §4 的安全边界冲突，不建议作为本 App 的登录链路**（待核实：QQ 自主开发是否支持 PKCE —— 本次检索的官方文档未见 `code_challenge` 参数）。
- **appkey 即 client_secret**：[准备工作_OAuth2.0](https://wiki.connect.qq.com/%E5%87%86%E5%A4%87%E5%B7%A5%E4%BD%9C_oauth2-0) 原文「appkey 的值即为 oauth_consumer_secret/client_secret」「对 appid 和 appkey 信息进行保密，不要随意泄漏」 → 走 Server-side 时 appkey 只能在后端（**需与后端调研对齐**）。

**回调**
- SDK 路径：必须在 `AndroidManifest.xml` 注册 `com.tencent.tauth.AuthActivity`（`noHistory="true"`、`launchMode="singleTask"`，`intent-filter` 含 VIEW / DEFAULT / BROWSABLE 与 `<data android:scheme="tencent你的AppId" />`）以及 `com.tencent.connect.common.AssistActivity`；权限 `android.permission.INTERNET`、`android.permission.ACCESS_NETWORK_STATE`；并在调用方 Activity 的 `onActivityResult` 里转发 `mTencent.onActivityResult(requestCode, resultCode, data)`（[Android_SDK环境搭建](https://wiki.connect.qq.com/qq%E7%99%BB%E5%BD%95/)）。
- 自主开发路径：`redirect_uri` 必须与注册时填写的回调地址**完全一致**（[开发攻略_Client-side](https://wiki.connect.qq.com/%E5%BC%80%E5%8F%91%E6%94%BB%E7%95%A5_client-side/)）。
- 本仓 `MainActivity` 目前只有 MAIN/LAUNCHER，且无 INTERNET 权限 → 接入 SDK 至少要新增两个 SDK Activity、一个 INTERNET 权限与可能的 FileProvider 声明（**阻塞项**，见 §6）。

**SDK / 依赖**
- 官方发行形态是**手动下载 jar**：`open_sdk_xxxx_lite.jar` 拷进 `libs/`，`compile fileTree(dir: 'libs', include: '*.jar')`（原文为旧 Gradle 写法）。文档括注「**sdk3.5.12 前支持 maven**」→ 当前版本是否有 Maven/AAR 坐标**待核实**（[Android_SDK环境搭建](https://wiki.connect.qq.com/qq%E7%99%BB%E5%BD%95/)）。
- 版本：SDK 下载页列 **Android_SDK 3.5.19（2026-04-14，1.5M）**；WIKI 首页更新日志列到 3.5.17（2024-10-16）—— 两处均为官方，但**以 SDK 下载页为准**（[SDK下载](https://wiki.connect.qq.com/sdk%E4%B8%8B%E8%BD%BD/) / [QQ互联WIKI](https://wiki.connect.qq.com/)）。
- 不需要 `google-services` 插件、不需要 `androidx.credentials`。最低 SDK / AAR 的 `minCompileSdk` **待核实**。

**官方引用**
- [Android_SDK环境搭建](https://wiki.connect.qq.com/qq%E7%99%BB%E5%BD%95/) · [登录/校验登录态](https://wiki.connect.qq.com/%E7%99%BB%E5%BD%95-%E6%A0%A1%E9%AA%8C%E7%99%BB%E5%BD%95%E6%80%81) · [SDK接入使用说明文档](https://wiki.connect.qq.com/sdk%E6%8E%A5%E5%85%A5%E4%BD%BF%E7%94%A8%E8%AF%B4%E6%98%8E%E6%96%87%E6%A1%A3/) · [SDK下载](https://wiki.connect.qq.com/sdk%E4%B8%8B%E8%BD%BD/) · [OAuth2.0简介](https://wiki.connect.qq.com/oauth2-0%E7%AE%80%E4%BB%8B/) · [开发攻略_Client-side](https://wiki.connect.qq.com/%E5%BC%80%E5%8F%91%E6%94%BB%E7%95%A5_client-side/) · [准备工作_OAuth2.0](https://wiki.connect.qq.com/%E5%87%86%E5%A4%87%E5%B7%A5%E4%BD%9C_oauth2-0)

**待核实**：QQ SDK 当前 Maven 坐标与 3.5.19 的 `minCompileSdk`；自主开发是否支持 PKCE；`AuthAgent` 的公开 API 边界；SDK 在 AGP 9.2.1 / compileSdk 36 下的构建兼容性；`tencent<AppId>` scheme 回调在单 Activity Compose 架构下的实测。

---

### 2.2 微信开放平台（移动应用微信登录，强制 SDK）

**流程（官方 SDK 流程）**
- [移动应用微信登录开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html) 原文：「移动应用微信登录是基于 **OAuth2.0 协议标准**构建的微信 OAuth2.0 授权登录系统」「支持 authorization_code 模式，适用于拥有 server 端的应用授权」，并明确 **「目前移动应用上微信登录只提供原生的登录方式，需要用户安装微信客户端才能配合使用」**；Android 建议总是显示按钮、未安装时引导下载。**即：强制 SDK，没有面向移动应用的「通过浏览器授权」官方替代**（[Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html) 同证）。网站应用是另一套（网页授权/扫码），不适用本 App。
- 调用：`WXAPIFactory.createWXAPI(context, APP_ID, true)` → `IWXAPI.registerApp(APP_ID)`；构造 `SendAuth.Req`，`scope = "snsapi_userinfo"`（**只能填这一个**）、`state`（建议带，防 csrf，长度 ≤ 1K，需 urlencode），`api.sendReq(req)`（[开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html) / [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html)）。
- SDK 通过 `SendAuth.Resp` 回 `ErrCode` / `code`（仅 ErrCode=0 有效）/ `state` / `lang` / `country` —— **App 拿到的是 code，不是 token；没有 PKCE**（用 `state` 防 csrf）。code 超时 10 分钟、一次性。
- 后端换 token：`https://api.weixin.qq.com/sns/oauth2/access_token?appid=APPID&secret=SECRET&code=CODE&grant_type=authorization_code` → `access_token`(2h) / `refresh_token`(180 天) / `openid` / `unionid`；刷新用 `/sns/oauth2/refresh_token`；取用户信息用 `/sns/userinfo`（**需与后端调研对齐**）。
- **官方安全原文（正是 §4 的依据）**：「AppSecret 是应用接口使用密钥……存储在客户端，极有可能被恶意窃取（如反编译获取 AppSecret）」「access_token……相当于用户登录态」「refresh_token……泄漏后相当于 access_token 泄漏」「**建议将 AppSecret、用户数据（如 access_token）放在 App 云端服务器，由云端中转接口调用请求**」「建议开发者自行管理业务登录态」（[开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html)）。

**回调**
- **固定路径硬约束**：必须在「应用包名 + `.wxapi`」下新建 `WXEntryActivity`（本仓即 `com.nongyu.appplus.wxapi.WXEntryActivity`），继承 Activity、实现 `IWXAPIEventHandler`，并在其中 `api.handleIntent(getIntent(), this)`；Manifest 需 `android:exported="true"`、`android:taskAffinity="填写你的包名"`、`android:launchMode="singleTask"`、透明主题（[Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html)）。**这是 Compose 单 Activity 架构下必须额外存在的一个真实 Activity，不是 Compose 目的地**。
- **Android 13 陷阱**：官方「Android 13 可能存在的问题适配」写明——若给 `WXEntryActivity` 加了 `intent-filter`，系统 Intent 过滤器会屏蔽不匹配的 intent，导致微信**无法回跳**；解决办法是**去掉该 Activity 的 intent-filter**（同上；[Android 13 变更说明](https://developer.android.com/about/versions/13/behavior-changes-all?hl=zh-cn#intents)）。
- **Android 11+ 适配**：OpenSDK 需 ≥ 6.8.0；`targetSdkVersion ≥ 30` 时须在 Manifest 加 `<queries><package android:name="com.tencent.mm" /></queries>`（软件包可见性）；签名校验失败回调 `errcode=-6`（同上）。
- 混淆需保留 `com.tencent.mm.opensdk.**` / `com.tencent.wxop.**` / `com.tencent.mm.sdk.**`；本仓 release 已开 R8，接 SDK 时属必须核对项。

**SDK / 依赖**
- Maven Central 坐标 `com.tencent.mm.opensdk:wechat-sdk-android:+`（6.8.0 起改用 `wechat-sdk-android`；官方列举 Maven Central 可用版本 6.6.4 / 6.6.5 / 6.6.23 / 6.7.0 / 6.7.9 / 6.8.0，建议 6.8.0）（[Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html)）。jCenter 已关停。
- 包名从旧 `com.tencent.mm.sdk` 改为 **`com.tencent.mm.opensdk`**，import 需同步（同上，注 3）。
- 不需要 `google-services` 插件、不需要 `androidx.credentials`。最低 SDK / 该 AAR 的 `minCompileSdk` **待核实**。

**官方引用**
- [移动应用微信登录开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html) · [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html) · [移动应用开发信息修改说明](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/guideline/dev_info.html) · [安卓常见问题](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/FAQ/Android.html) · [微信Open SDK 开发者合规使用指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/agreement/sdk)

**待核实**：微信开放平台登记的「应用签名」确为签名 MD5（官方 [操作指南-配置 Android 应用备用签名](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/guideline/backup_signature.html) 与签名生成工具页本次未取到正文；「包名 + 签名 MD5」的绑定关系目前仅由 [CSDN 二手资料](https://blog.csdn.net/weixin_31856057/article/details/166428411) 与 [博客园二手资料](https://cnblogs.com/sp1086/p/9635769.html) 佐证，**须以开放平台控制台实测为准**）；`开发指南` 目录里另有「[移动应用扫码登录](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.html)」，是否构成「未安装微信时的官方替代」未取证 → **待核实**；6.8.0 AAR 与 AGP 9.2.1 / compileSdk 36 的兼容性；未上架应用微信登录 100 次/天限制（错误码 10060）对本仓调试期的影响。

---

### 2.3 Google（Credential Manager 为现行路径，legacy GIS 已废弃）

**流程**
- **现状（一手）**：[About the migration from legacy Google Sign-In](https://developer.android.com/identity/sign-in/legacy-gsi-migration) 原文：「Google Sign-In for Android (as part of `com.google.android.gms:play-services-auth`) **is deprecated and will be removed from the Google Play services Auth SDK in a future release**」，要求迁移到 **Android Credential Manager**；[Google Play services release notes](https://developers.google.com/android/guides/releases) 亦写「Removed the APIs related to Google Sign-in. Use Android Credential Manager instead.」。同页还指出 Credential Manager **把 authentication 与 authorization 拆开**：登录用 Credential Manager，访问 Google Drive 等授权另用 [AuthorizationClient API](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient)（[Authorize access to Google user data](https://developer.android.com/identity/authorization)）。
- **「一键登录」形态**：Credential Manager 的 `GetSignInWithGoogleOption(serverClientId = WEB_CLIENT_ID)` → `GoogleIdTokenCredential.createFrom(...)` → 拿到 **Google ID token**（`idToken`），App 把它交给后端校验（**需与后端调研对齐**）。社区/官方 issue 佐证该 API 形状（[Google Issue Tracker 525786067](https://issuetracker.google.com/issues/525786067)、[StackOverflow 案例](https://stackoverflow.com/questions/79714865/google-credential-manager-issue-with-getting-the-user-email)，**二手**）。**这与标准 authorization code + PKCE 是两种不同形态**：前者 App 直接拿到 ID token，后者 App 只拿到 code。
- **标准 OAuth 2.0 + PKCE 形态**：[OAuth 2.0 for Desktop Apps](https://developers.google.com/identity/protocols/oauth2/native-app) 原文把 installed app 定义为「**cannot keep secrets (they are public clients)**」并写「**Always use PKCE**」；Step 1 生成 `code_verifier`（43–128 字符高熵随机串，字符集 `[A-Z]/[a-z]/[0-9]/-/./_/~`）、`code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))`（**S256 recommended**，另有 `plain`），Step 2 打 `https://accounts.google.com/o/oauth2/v2/auth`，`response_type=code`，带 `state`（recommended），Step 5 用 code + `code_verifier` 换 token。该页同时注明「**For mobile apps, see the Android and iOS authorization guides**」——即纯浏览器 OAuth 不是 Android 上的首选官方路径。
- redirect 方式（同页 [App redirect methods](https://developers.google.com/identity/protocols/oauth2/native-app#installed_app_redirect_methods)）：**Custom URI scheme**（反域名记法、必须含句点，如 `com.example.app:redirect_uri_path` 或 `com.googleusercontent.apps.123:/oauth2redirect`）、**Loopback IP**（`http://127.0.0.1:port`，但页面明确标注「support for the loopback IP address redirect option on **mobile apps** is **DEPRECATED**」）、**Manual copy/paste（Deprecated）**。`redirect_uri` 必须与 Cloud Console 登记的完全一致，否则 `redirect_uri_mismatch`。

**回调**
- Credential Manager 路径由系统 bottom sheet 回传 credential，**不依赖 App 自定义 redirect**（`serverClientId` 是 Web client ID）。
- 若走浏览器 OAuth：自定义 scheme 需在 Manifest 的 `intent-filter` 声明（BROWSABLE + DEFAULT + VIEW）；HTTPS 形态则应走 **Android App Links**（见 §3）。
- **待核实**：Google 该页出现「Custom URI scheme is not supported on Android or Chrome apps」的报错提示文案（[页面](https://developers.google.com/identity/protocols/oauth2/native-app)），以及 2022 年 [Making OAuth flows safer](https://developers.googleblog.com/2022/02/making-oauth-flows-safer.html#disallowed-loopback) 对 mobile loopback 的限制 → **新 Android OAuth client 是否允许自定义 scheme、或必须改 HTTPS App Links，待核实**。

**SDK / 依赖**
- 现行：`androidx.credentials:credentials` + `androidx.credentials:credentials-play-services-auth`（[credentials release notes](https://developer.android.com/jetpack/androidx/releases/credentials) 当前页声明 `1.7.0-alpha03`；`1.5.0` 被 [Google Issue Tracker](https://issuetracker.google.com/issues/525786067) 与多方引用为稳定线 → **确切稳定版本号待核实**）；`com.google.android.libraries.identity.googleid:googleid`（社区见 `1.1.1` / `1.2.0` → **待核实**）。
- 废弃：`com.google.android.gms:play-services-auth`（legacy Google Sign-In for Android，见上）。
- **google-services 插件 / `google-services.json`**：[Setting up OAuth 2.0](https://support.google.com/googleapi/answer/6158849)（官方标注「no longer up-to-date」，指向 [Manage OAuth Clients](https://support.google.com/cloud/answer/15549257)）说明创建 **Android** 类型 OAuth client 需填 **包名 + SHA-1 签名指纹**；[Authorize access to Google user data](https://developer.android.com/identity/authorization) 亦要求「create an Android client ID... specify your app's package name and SHA-1 signature」。`google-services.json` 是 Google Services / Firebase Gradle 插件产物；Credential Manager 的 `GetSignInWithGoogleOption` 用的是 **Web client ID**（`serverClientId`），[第三方文档](https://react-native-nitro-google-sign-in.github.io/docs/setup/google-cloud/#quick-checklist) 明写「Optional path — no google-services.json on Android」（**二手**）。→ **本仓是否需要 `google-services` 插件 / `google-services.json` 待核实**，取决于最终选 Credential Manager 还是 Android OAuth client。
- OAuth 同意屏幕：Google Cloud 项目 → [OAuth consent screen](https://console.cloud.google.com/apis/credentials/consent)（External/Internal、scopes、敏感 scope 需 verification；见 [Submitting your app for verification](https://support.google.com/cloud/answer/13461325)）。

**官方引用**
- [About the migration from legacy Google Sign-In](https://developer.android.com/identity/sign-in/legacy-gsi-migration) · [Google Play services release notes](https://developers.google.com/android/guides/releases) · [Credential Manager / Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg) · [credentials release notes](https://developer.android.com/jetpack/androidx/releases/credentials) · [OAuth 2.0 for Desktop Apps](https://developers.google.com/identity/protocols/oauth2/native-app) · [Authorize access to Google user data](https://developer.android.com/identity/authorization) · [Setting up OAuth 2.0](https://support.google.com/googleapi/answer/6158849)

**待核实**：`androidx.credentials` / `googleid` 的稳定版本与 `minSdk`；是否必须引入 `google-services` 插件；Android 自定义 scheme 的现状；这些库与 Compose BOM 2025.12.00（映射 Compose 1.10.0）的传递依赖是否冲突（列入 §8 第 4 条）。

---

### 2.4 X / Twitter（OAuth 2.0 + PKCE，无官方 Android SDK）

**流程（标准 OAuth 2.0 + PKCE 浏览器流程）**
- **官方 Android SDK 现状**：[Discontinuing support for Twitter Kit SDK](https://blog.x.com/developer/en_us/topics/tools/2018/discontinuing-support-for-twitter-kit-sdk.html) 原文：「Twitter Kit is our open source iOS, **Android**, and Unity SDK for displaying Tweets, **authorizing Twitter users**, and working with the Twitter API」「**On October 31, 2018**, we will no longer actively contribute to... After this date we will also stop releasing the SDKs through Cocoapods, Carthage, and **Bintray JCenter**... documentation and source code... remain available in an **archived state**」（[X Developers 公告镜像](https://devcommunity.x.com/t/discontinuing-support-for-twitter-kit-sdk/105094/1) 同证）。→ **没有现行官方 Android 登录 SDK**，登录需在系统浏览器里跑 OAuth 2.0。
- [OAuth 2.0 Authorization Code Flow with PKCE](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code) / [user-access-token](https://docs.x.com/fundamentals/authentication/oauth-2-0/user-access-token)：Step 1 构造 `https://x.com/i/oauth2/authorize?response_type=code&client_id=...&redirect_uri=...&scope=...&state=...&code_challenge=...&code_challenge_method=...`；`code_challenge_method` 支持 **`S256` 或 `plain`**（[authorization-code.md](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code.md) 参数表），生产应用 `code_challenge` 应用随机串。Step 3 `POST https://api.x.com/2/oauth2/token`（`application/x-www-form-urlencoded`）带 `code` / `grant_type=authorization_code` / `client_id` / `redirect_uri` / `code_verifier`；要 refresh token 需 `offline.access` scope。
- **客户端类型决定 client_secret 归属**：[developer-apps](https://docs.x.com/resources/fundamentals/developer-apps.md) / [authorization-code.md](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code.md) 表格：**Web App / Automated App/Bot = Confidential（有 Client Secret）**；**Native App / Single Page App = Public（「Public clients use PKCE only」，移动端无法安全保存 secret）**。token 端点上 confidential client 用 HTTP Basic（`Authorization: Basic base64(client_id:client_secret)`），public client 用 body 里的 `client_id` 且不带 secret。→ 若 App 注册为 **Native App(public)**，token 交换理论可由 App 用 `code_verifier` 自己完成；若由**后端**代换，则后端需是另一个 confidential client、且 `redirect_uri` 要在该 App 的 allowlist 里 —— **两种架构的取舍需与后端调研对齐**，本笔记不替后端定。
- **OAuth 1.0a 是否仍需**：[OAuth 2.0 可只用于 X API v2](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code)（「OAuth 2.0 can be used with the X API v2 only」），App 的 authentication settings 可选 OAuth 1.0a / OAuth 2.0 / 两者都开。官方社区 2025 答复：「**OAuth 1.0a support will be continued**... we **highly recommend OAuth2.0**」「We recommend using OAuth2.0 for any new integrations」（[Will OAuth 1.0a User Context Continue to Be Supported for API v2?](https://devcommunity.x.com/t/will-oauth-1-0a-user-context-continue-to-be-supported-for-api-v2/245571/3)；[Livestream API auth](https://docs.x.com/livestream-api/authentication) 亦写「OAuth 2.0 is **strongly recommended** for all new integrations」「OAuth 1.0a 3-legged user context is still accepted for existing integrations」）。→ **纯登录/身份场景用 OAuth 2.0 + PKCE 即可，不必引入 OAuth 1.0a 签名**；只有将来调用仍需 1.0a 的旧端点才用得上（**需与后端调研对齐**：拿 token 后由谁调 X API）。

**回调**
- [Callback URLs](https://docs.x.com/fundamentals/developer-apps)：必须在 Developer Console 的 allowlist 登记、**精确匹配（含尾部斜杠）**、每个 App 最多 10 个、生产用 `https://`、本地开发用 `http://127.0.0.1`（不用 `localhost`）；**「Disallowed protocols」列出 `javascript/data/file/ftp/mailto/telnet` 及「other non-standard schemes」**。
- → **自定义 scheme（如 `nongyu://oauth`）能否登记为 X 的 Callback URL，是最需要实测的一点**（官方措辞「other non-standard schemes」有歧义）；X 开发者社区还有「Android 装了 X App 时 Web OAuth 2.0 被中断」的报告（[devcommunity 帖](https://devcommunity.x.com/t/web-oauth-2-0-is-broken-on-android-if-twitter-app-is-installed/169698)，**二手**）。**待核实**。
- 因此 X 更稳的形态可能是 **HTTPS App Links 回调 + 后端托管的 redirect 端点**（见 §3 / §4）。

**SDK / 依赖**
- **无官方 Android SDK、无 Maven 坐标**（Twitter Kit Android 已归档 / 停止发行）。浏览器 OAuth 只需系统浏览器（Custom Tabs）+ 一个 HTTP 客户端（后端侧）；不需要 `google-services` 插件、不需要 `androidx.credentials`。
- 可选第三方封装：Firebase 等（**二手**，本笔记不推荐、不计入依赖清单）。
- scopes 示例：`tweet.read / users.read / follows.read / follows.write / offline.access / users.email`（[authorization-code](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code)）；`users.email` 是否需额外 App 权限 / 付费层级 **待核实**。

**官方引用**
- [Discontinuing support for Twitter Kit SDK](https://blog.x.com/developer/en_us/topics/tools/2018/discontinuing-support-for-twitter-kit-sdk.html) · [OAuth 2.0 Authorization Code Flow with PKCE](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code) · [User access token / Steps](https://docs.x.com/fundamentals/authentication/oauth-2-0/user-access-token) · [OAuth 2.0 app types & Callback URLs](https://docs.x.com/fundamentals/developer-apps) · [OAuth 1.0a overview](https://docs.x.com/fundamentals/authentication/oauth-1-0a/overview) · [Migrating OAuth 1.0a tokens to OAuth 2.0](https://docs.x.com/fundamentals/authentication/oauth-2-0/oauth-1-0a-token-exchange)

**待核实**：自定义 scheme 是否被 X 接受；`users.email` 权限层级；Free/Basic/Enterprise 访问层级对登录的影响；Native App(public) 由 App 还是后端换 token 的架构选型（需与后端调研对齐）；X App 安装时对浏览器回调的干扰。

---

### 2.5 Apple（无官方 Android SDK，走 Web/REST + 后端中转）

**流程（Web / REST 流程，不是 SDK 流程）**
- **官方对「非 Apple 平台」的说法（一手关键证据）**：[Sign in with Apple REST API](https://developer.apple.com/documentation/signinwithapplerestapi) 的 Overview 原文：「**To sign in from a web app or other platform, like Android, use `Sign in with Apple JS`**. Alternatively, to let users set up accounts and sign in to your **native iOS, macOS, tvOS, and watchOS** apps, use the AuthenticationServices framework.」→ **Apple 没有官方 Android SDK**；`AuthenticationServices` / `ASWebAuthenticationSession` 是 Apple 平台专属（[ASWebAuthenticationSession](https://developer.apple.com/documentation/authenticationservices/aswebauthenticationsession)），**Android 上不存在**，任务描述里「通常走 ASWebAuthenticationSession 的 Web 流程」应更正为「Android 走系统浏览器 + Apple 的 Web/REST 端点」。
- [Incorporating Sign in with Apple into other platforms](https://developer.apple.com/documentation/signinwithapplejs/incorporating_sign_in_with_apple_into_other_platforms) 给出手工发起的端点与参数：`GET https://appleid.apple.com/auth/authorize`，参数 `client_id`（Service ID）/ `redirect_uri` / `response_type`（`code`、`id_token` 或两者）/ `scope`（`name`、`email`）/ `response_mode`（`query` / `fragment` / `form_post`；**请求了任何 scope 时必须 `form_post`**）/ `state`。成功响应含 `code`（单次、5 分钟有效）、`id_token`（JWT）、`state`、`user`（name/email JSON）。
- **同一官方文档的「Return Control to the App」原文**：「**Platforms that support custom URL schemes—iOS 12.0 and earlier and Android—must handle the data resulting from the authorization flow by storing it on their app server in the logic of their `redirect_uri` endpoint. You must then redirect the custom URL scheme to give control back to the app.**」→ **Apple 自己规定 Android 侧由服务端 `redirect_uri` 端点接收 form_post、落库、再用自定义 scheme 把控制权交还 App**；即「后端中转」不是可选优化，而是官方描述的形态。
- 后端侧：[Sign in with Apple REST API](https://developer.apple.com/documentation/signinwithapplerestapi.md) 列 `Token validation`（用授权码换 token / 校验 refresh token）、`Token revocation`、`Fetch Apple's public key for verifying token signature`（JWKSet 验 `id_token` 签名）、`Request an authorization to the Sign in with Apple server`；`client_secret` 是 **ES256 JWT**（`iss`=Team ID、`sub`=Service ID/client_id、`aud`=`https://appleid.apple.com`、`kid`=Key ID），用 Apple Developer 下载的 `.p8` 私钥签名（[Creating a client secret](https://developer.apple.com/documentation/accountorganizationaldatasharing/creating-a-client-secret)）→ **私钥与 client_secret 只能在服务端**（**需与后端调研对齐**：token 端点、id_token 验签、自有会话签发、账号绑定）。
- 可选的第三方整包：Firebase Android SDK 的 `OAuthProvider.newBuilder("apple.com")` 可「carry out the end-to-end OAuth 2.0 sign-in flow」（[Firebase: Authenticate Using Apple on Android](https://firebase.google.com/docs/auth/android/apple)，**二手/第三方**，会引入 Firebase 依赖，本笔记不推荐、仅作线索）。

**回调**
- 浏览器里 Apple 对 `redirect_uri` 做 `form_post`（因为要 name/email scope）→ **回调首先落在后端 Web 端点**，后端再 302 到一个自定义 URL scheme 让 Android App 收回控制权（Apple 文档原文，见上）。
- Android 侧需在 Manifest 声明该自定义 scheme 的 `intent-filter`（BROWSABLE + DEFAULT + VIEW），并在 `onNewIntent` / `onCreate` 里接收；或用 HTTPS App Links 承接（见 §3）。**具体 scheme 命名、`assetlinks.json` 是否同时需要，待核实**。

**SDK / 依赖**
- **无官方 Android SDK、无 Maven 坐标**。需要的是：系统浏览器（Custom Tabs）、后端上的 `Sign in with Apple JS` 或 REST 端点、Apple Developer 的 Service ID / Key 配置。不需要 `google-services` 插件、不需要 `androidx.credentials`（除非选 Firebase 路线）。
- 配置物：App ID（启用 Sign in with Apple，可作 Primary App ID）→ **Service ID**（绑定域名 + Return URL）→ 私钥 `.p8` + **Key ID** + **Team ID** → 后端生成 `client_secret`（[Apple Developer: Create a private key](https://developer.apple.com/help/account/keys/create-a-private-key/) / [Sign in with Apple JS](https://developer.apple.com/documentation/signinwithapplejs)）。

**官方引用**
- [Sign in with Apple REST API](https://developer.apple.com/documentation/signinwithapplerestapi)（[Markdown 版](https://developer.apple.com/documentation/signinwithapplerestapi.md)） · [Incorporating Sign in with Apple into other platforms](https://developer.apple.com/documentation/signinwithapplejs/incorporating_sign_in_with_apple_into_other_platforms) · [Sign in with Apple JS](https://developer.apple.com/documentation/signinwithapplejs) · [Creating a client secret](https://developer.apple.com/documentation/accountorganizationaldatasharing/creating-a-client-secret) · [ASWebAuthenticationSession](https://developer.apple.com/documentation/authenticationservices/aswebauthenticationsession) · [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/) · [Firebase: Authenticate Using Apple on Android](https://firebase.google.com/docs/auth/android/apple)（二手）

**待核实**：Service ID Return URL 是否只接受 `https://`、自定义 scheme 回跳的实测；Apple 私有中继邮箱（hide my email）对账号绑定的影响；`id_token` 验签与 client_secret 生成的后端实现边界（需与后端调研对齐）；是否选择 Firebase 整包（会新增依赖，需维护者拍板）。

---

## 3 redirect / 回调接收：自定义 scheme vs Android App Links，以及 Compose 单 Activity 的接法

**两种机制（Android Developers 一手）**
| 机制 | 声明要点 | 验证 / 归属 | 适用 |
| --- | --- | --- | --- |
| 自定义 scheme（deep link） | `intent-filter` 内 `VIEW` + `DEFAULT` + `BROWSABLE` + `<data android:scheme="..." />` | 无需域名验证，但**任意 App 可声明同一 scheme**，存在劫持风险 | QQ SDK 的 `tencent<AppId>`；Apple 明写 Android 用它回跳控制权；Google custom URI scheme |
| Android App Links（verified） | 同上，但 `android:autoVerify="true"`、`http`/`https` + `host`，且**不要在同一 filter 里塞其它 scheme**（官方原文「Do not include other schemes, as this will prevent verification」） | 系统对每个 host 请求 `https://hostname/.well-known/assetlinks.json`；文件须 `application/json`、HTTPS、**无 301/302 重定向**；Android 11(API 30) 及以下要求**所有 host** 都能取到匹配文件；每个子域单独发布，通配 host 需在根域发布；文件内含 `sha256_cert_fingerprints` | X / Google / Apple 的 `https://` 回调更稳的形态 |

一手：[Add Intent filters for App Links](https://developer.android.com/training/app-links/add-applinks) · [Verify App Links](https://developer.android.com/training/app-links/verify-applinks) · [Configure website associations and dynamic rules](https://developer.android.com/training/app-links/configure-assetlinks)。Android 15+ 还引入 `uri-relative-filter-group` 与 `assetlinks.json` 动态规则的两层过滤（[add-applinks](https://developer.android.com/training/app-links/add-applinks)，**本次只作线索，具体规则待核实**）。

**各家要求的 callback 形态**
| 平台 | 回调形态 | 备注 |
| --- | --- | --- |
| QQ（SDK） | `tencent<AppId>` 自定义 scheme → `com.tencent.tauth.AuthActivity` | 由 SDK 的 Activity 承接，App 侧 `onActivityResult` 转发 |
| QQ（自主开发） | 注册时填的 `redirect_uri`，必须完全一致 | Server-side code / Client-side fragment |
| 微信 | 无 HTTP redirect；`com.nongyu.appplus.wxapi.WXEntryActivity` 固定 Activity + `IWXAPIEventHandler.onResp` | **Android 13 上该 Activity 不得带 intent-filter** |
| Google（Credential Manager） | 无 redirect；系统 bottom sheet 回传 `GoogleIdTokenCredential` | 用 Web client ID |
| Google（浏览器 OAuth） | custom URI scheme（反域名、含句点）/ loopback（mobile 已弃用）/ manual copy-paste（弃用） | 自定义 scheme 在 Android 的现状待核实 |
| X | Developer Console allowlist 的 Callback URL，精确匹配、生产 `https://`、最多 10 个 | 自定义 scheme 是否被「other non-standard schemes」排除，待核实 |
| Apple | Apple 对后端 `redirect_uri` 做 `form_post` → 后端 302 到自定义 scheme 把控制权交还 Android App | 后端中转是官方明写的形态 |

**Compose 单 Activity 架构下的约束（本仓现状：`MainActivity` 是唯一 Activity）**
- **SDK 会强制引入额外 Activity**：QQ 的 `AuthActivity` / `AssistActivity`、微信的 `WXEntryActivity` 都是 Manifest 里的真实 Activity，无法用 Compose 目的地替代；微信还要求固定包路径。这与「单 Activity + Compose Navigation」不冲突（它们不是导航页），但**必须改 Manifest**（本轮不改）。
- **`rememberLauncherForActivityResult` 不是 OAuth 回调的接法**：Activity Result API 用于 `StartActivityForResult` / 权限请求等「发出去再拿 result」的场景；OAuth 的 deep link / App Link 是**外部把 Intent 投递给本 App**，走的是 Activity 的 `onCreate(intent)`（冷启动）或 `onNewIntent(intent)`（`singleTask`/`singleTop` 复用时）。官方 [Create a deep link for a destination](https://developer.android.com/guide/navigation/design/deep-link) 给出的范式正是 `onNewIntent` → `navController.handleDeepLink(intent)`。
- 因此落点应是：Activity 层把收到的 Intent（含 `state`、自定义 scheme 参数、或 SDK 回调结果）转成一条一次性事件，喂给 Compose 侧的状态容器（如 ViewModel / SavedStateHandle / 热流），Compose 只负责渲染与触发；**QQ SDK 另有硬性要求** `mTencent.onActivityResult(...)` 在调用方 Activity 的 `onActivityResult` 里转发；**微信**要求 `WXEntryActivity.handleIntent(...)`。
- 具体在 Compose 里如何把 Intent 事件安全送达（避免重组重复消费、进程死亡后恢复、登录中途被回收导致 `code_verifier`/`state` 丢失）**待核实**，且依赖「状态管理 + 路由」先落地（§6）。

## 4 App 与后端的分工边界（PKCE 与 code 流）

**标准的「App 只拿 code、后端换 token」链路（Google / X 的浏览器 OAuth，及 QQ 自主开发 Server-side）**
1. App 生成 `code_verifier`（高熵 43–128 字符）与 `code_challenge = BASE64URL(SHA256(verifier))`（S256），生成并保存 `state`；
2. App 用**系统浏览器 / Custom Tabs** 打开授权 URL，带 `code_challenge` / `code_challenge_method=S256` / `state` / `redirect_uri`；
3. 用户同意后，授权服务器把 `code`（+ `state`）通过回调交还 App；App 先核对 `state`；
4. App 把 **`code` + `code_verifier`（+ `redirect_uri`）交给后端**（**接口边界假设，需与后端调研对齐**）；
5. 后端在 token 端点用 **`client_secret`**（confidential client）或仅 `client_id`（public client）换 access/refresh/id token，校验 `id_token` 签名与 audience，签发**本 App 自己的会话 token** 返回 App；
6. App 只持久化**自家会话 token**，不持久化第三方 access/refresh token。

一手依据：Google [OAuth 2.0 for Desktop Apps](https://developers.google.com/identity/protocols/oauth2/native-app)（Step 1/2/4/5、`state` recommended、「Always use PKCE」）；X [authorization-code](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code) / [user-access-token](https://docs.x.com/fundamentals/authentication/oauth-2-0/user-access-token)（token 端点带 `code_verifier`）；Apple [Incorporating Sign in with Apple into other platforms](https://developer.apple.com/documentation/signinwithapplejs/incorporating_sign_in_with_apple_into_other_platforms)（`form_post` 到服务端 `redirect_uri`）；微信 [开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html)（code → 后端用 AppSecret 换）。

**为什么 App 不该持有 `client_secret`**
- Google：installed app「**cannot keep secrets (they are public clients)**」、`Desktop apps are public clients... Always use PKCE`（[native-app](https://developers.google.com/identity/protocols/oauth2/native-app)）。
- 微信：「AppSecret……存储在客户端，极有可能被恶意窃取（**如反编译获取 AppSecret**）」，并「建议将 AppSecret……放在 App 云端服务器」（[开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html)）。
- QQ：appkey 即 `client_secret`，官方要求「保密，不要随意泄漏」（[准备工作_OAuth2.0](https://wiki.connect.qq.com/%E5%87%86%E5%A4%87%E5%B7%A5%E4%BD%9C_oauth2-0)）。
- X：Native App = Public，「**Public clients use PKCE only**」「cannot use registered client secrets, such as applications running in a browser or **mobile device**」（[developer-apps](https://docs.x.com/resources/fundamentals/developer-apps.md) / [authorization-code.md](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code.md)）。
- Apple：`client_secret` 是用 `.p8` 私钥签的 ES256 JWT（[Creating a client secret](https://developer.apple.com/documentation/accountorganizationaldatasharing/creating-a-client-secret)）→ 私钥只能留服务端。

**为什么不该把第三方 `access_token` 长期存在 App**
- 微信原文把 access_token 定义为「相当于用户登录态」、refresh_token 为「长效凭证」，泄漏后可被恶意发起接口调用；并明确「建议将……用户数据（如 access_token）放在 App 云端服务器」「建议开发者自行管理业务登录态并合理设置过期时间」（[开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html)）。
- QQ Client-side implicit grant 的 `access_token` 有效期 **60 天**（[开发攻略_Client-side](https://wiki.connect.qq.com/%E5%BC%80%E5%8F%91%E6%94%BB%E7%95%A5_client-side/)），更不该落 App。

**QQ / 微信 / Apple 与「标准 OAuth 2.0 + PKCE」的差别（最容易被误写成同一套的地方）**
| 平台 | App 侧拿到什么 | 谁换 token | PKCE | 回调载体 | 与标准流程的偏差 |
| --- | --- | --- | --- | --- | --- |
| QQ **SDK** | **`access_token` + `openid` + `pay_token`**（`onComplete(JSONObject)`） | 无人换，SDK 已发好 | **无** | `AuthActivity` scheme + `onActivityResult` + `IUiListener` | **App 直接拿到长效 token**，与「App 只拿 code」相反；必须立刻送后端换自家会话，不得留存 |
| QQ **自主开发** | code（Server-side）或 `access_token`（Client-side implicit，fragment） | 后端（Server-side） | 官方文档未见 `code_challenge`（**待核实**） | 注册 `redirect_uri` | 这是另一条链路，与 SDK 不是一回事；implicit 不推荐 |
| 微信 **SDK** | **code**（`SendAuth.Resp`，`state` 原样带回） | **后端**用 `AppID + AppSecret + code` 换 | **无**（只有 `state`） | `WXEntryActivity.onResp` | 形态像 code flow，但**没有 PKCE**、回调靠微信客户端拉起固定 Activity 而非 HTTP redirect、强制安装微信 |
| Google **Credential Manager** | **Google ID token**（`GoogleIdTokenCredential.idToken`） | 后端**校验 ID token**（非「换 token」） | 不适用 | 系统 bottom sheet | 第三种形态：App 直接拿 ID token |
| Google / **X 浏览器 OAuth** | **authorization code** | 后端 | **有（S256）** | 浏览器 redirect（custom scheme / App Link） | 本笔记所指的「标准 OAuth 2.0 + PKCE」 |
| **Apple** | code + `id_token`（先到**后端** `redirect_uri` 的 form_post） | **后端**用 ES256 `client_secret` 换并验签 | 无（Apple 用 `state` + `id_token` 验签） | 后端 Web 端点 → 自定义 scheme 回跳 App | **App 连 code 都不直接接**，官方要求后端中转 |

> 本节所有「后端应该做什么」均为**接口边界假设，需与后端调研对齐**：包括 code / id_token 的上行接口形状、token 端点调用方、自有会话 token 的签发与校验、`openid`/`unionid`/`sub` 的账号绑定与去重、解绑与注销（Apple 有 `Revoke tokens` 要求）、刷新与失效策略。本笔记不替后端选型。

## 5 横向对比矩阵

| 维度 | QQ 互联 | 微信 | Google | X / Twitter | Apple |
| --- | --- | --- | --- | --- | --- |
| 现行官方 Android SDK | 有（手动 jar，3.5.19） | 有（Maven Central `wechat-sdk-android`） | 有（Credential Manager + `googleid`） | **无**（Twitter Kit 2018-10-31 停维/归档） | **无**（官方指定 Web/REST） |
| 主流程形态 | 厂商 SDK（另有自主开发 OAuth2.0） | 厂商 SDK（官方称基于 OAuth2.0 标准，但只提供原生登录） | Credential Manager 一键登录 / 浏览器 OAuth2.0+PKCE | 标准 OAuth 2.0 + PKCE | Sign in with Apple JS / REST（Web 流程） |
| App 最终拿到 | `access_token`+`openid`（SDK） | **code** | ID token（CM）或 code（PKCE） | **code**（+ 自行/后端换 token） | code + `id_token`（**先到后端**） |
| 是否强制装自家 App | 未装 QQ 时 SDK 可走 H5/扫码/浏览器（返回码 `2`） | **强制**（官方：需用户安装微信客户端） | 否（需 Google Play services / 系统浏览器） | 否（系统浏览器） | 否（系统浏览器） |
| 回调 | `tencent<AppId>` scheme → `AuthActivity` | `<包名>.wxapi.WXEntryActivity` | bottom sheet / custom scheme / App Link | Callback URL allowlist（生产 `https://`） | 后端 `redirect_uri` form_post → 自定义 scheme 回跳 |
| PKCE | 无（自主开发是否支持待核实） | **无**（仅 `state`） | 有（S256，浏览器路径） | **有**（S256 / plain） | 无（`state` + id_token 验签） |
| `client_secret` 归属 | `appkey` 保密，后端（Server-side） | `AppSecret` 后端 | Web client ID 配 CM；Android client 绑包名+SHA-1 | Native App=Public 用 PKCE；confidential client 的 secret 后端 | ES256 JWT `client_secret`，**仅后端** |
| 关键控制台绑定 | appid / appkey、回调地址、开发者资质审核 | **包名 + 应用签名** + AppID/AppSecret + 登录审核 + 上架状态 | Google Cloud 项目 + 同意屏幕 + Android client（包名+SHA-1）/ Web client ID | Developer Console App + OAuth 2.0 开关 + Callback URL + scopes | App ID(Primary) + Service ID + 域名/Return URL + `.p8`/Key ID/Team ID |
| 已废弃/不再推荐 | OAuth 1.0 不再支持；3.5.12 前才支持 maven | 旧包名 `com.tencent.mm.sdk`、jCenter、< 6.8.0 | `play-services-auth` legacy GSI；mobile loopback redirect；manual copy/paste | Twitter Kit (Android)；新集成不建议 OAuth 1.0a | 无 Android SDK；`ASWebAuthenticationSession` 不适用 Android |
| 是否需 `google-services` 插件 | 否 | 否 | 待核实（CM 用 Web client ID，或可不引） | 否 | 否（Firebase 路线另计） |
| 是否引入 `androidx.credentials` | 否 | 否 | **是**（若走 Credential Manager） | 否 | 否 |

## 6 工程落点与阻塞项（参照 `docs/adr/` 落点风格，不写代码）

**落点建议（建议先立一条 ADR，例如「ADR-0008 登录/认证的落点与先后依赖」）**
- 本仓是 single-context、单 `:app` 模块（`docs/agents/domain.md`：整仓一份 GLOSSARY、无 per-module glossary；`docs/adr/` 在真正决策时才惰性创建）。因此**建议先落在 `:app` 内的 `com.nongyu.appplus.feature.login`（或 `.../auth/`）分包**，把「五家差异」收敛成一层薄薄的 provider 适配边界；待模块边界与后端契约稳定后，再评估是否抽独立 `:auth` Gradle 模块 —— 不要在骨架期为了 OAuth 直接加模块。
- 与现有 `docs/adr/` 风格一致：ADR 应含 `Status`（proposed）、决策内容表、`Considered Options`（例如「每家 SDK 直连 vs 统一走浏览器 OAuth+后端中转」）、`Consequences`、`References`（回指本笔记与 `README.md` 骨架边界）。**注意**：`docs/agents/domain.md` 要求「若输出与既有 ADR 冲突要显式提出」；现有 ADR-0006「冷启动只读本地皮肤包不碰网络」约束的是**启动路径**，登录是用户主动触发的交互路径，二者不冲突，但 ADR 里应写明「登录不得把网络 IO 塞回冷启动路径」。
- **依赖顺序（阻塞项必须先落地）**，对应 `README.md` 的「后续计划：设计准则 → 主题 Token → 路由与骨架页面 → 业务模块接入」：
  1. **网络层**（把 code / id_token 上行给后端、拿回自家会话 token）—— **阻塞**。没有它，任何一家都只能停在「拿到回调」。
  2. **状态管理 + 依赖注入**（保存 `code_verifier` / `state` / 会话态、把 Activity 收到的 Intent 事件安全送到 Compose、跨进程死亡恢复）—— **阻塞**。`code_verifier`/`state` 丢失会导致 PKCE 流程无法完成。
  3. **路由 / 骨架页**（未登录 → 登录页 → 首页，以及 deep link 落到哪个目的地）—— **阻塞**（尤其 App Links 回调需要一个确定的接收目的地）。
  4. **后端接口契约** —— **阻塞**，且**需与后端调研对齐**：上行参数、token 端点归属、自有会话签发/校验、账号绑定、注销/解绑。
  5. **各平台控制台账号 / 资质 / 签名 / 域名 / Return URL** —— **阻塞**，且全部是**人工步骤**（§7）。
  6. **构建配置与 Manifest 变更**（本轮一律不做）：新增依赖坐标、INTERNET 权限、QQ 的两个 SDK Activity、微信的 `WXEntryActivity`(+`<queries>` on Android 11+)、FileProvider authorities、intent-filter（含 `autoVerify`）、R8 keep 规则、以及可能的 `assetlinks.json` 托管。
  7. **维护者拍板**：引入任何 OAuth SDK 都会打破 `README.md` 骨架期边界与 `ai-orb-reference-projects.md` 里被维护者拍板过的「零新依赖 + BOM 2025.12.00 锁定」约定；且新库可能抬高 Compose/lifecycle 版本（§9）。**这是治理层面的阻塞项，不是技术细节**。
- QQ/微信 SDK 要求**真实 Activity + 固定包路径**，与「Compose 单 Activity 外壳」并存但需在 Manifest 显式声明；`rememberLauncherForActivityResult` 不用于接 OAuth deep link（见 §3）。

**建议的 ADR 决策表（草案，供拍板，不写代码）**
| 项 | 建议 | 理由 |
| --- | --- | --- |
| 代码位置 | `:app` 内 `feature/login`（或 `auth`）分包，暂不拆模块 | 单模块 single-context，骨架期避免过早模块化 |
| 五家统一方式 | 不强行统一为一套 OAuth；用 provider 适配层隔离差异 | §2/§4 已证五家分三类，QQ 直接返 token、Apple 先到后端 |
| 会话归属 | App 只存自家会话 token | 微信官方建议 + public client 定义 |
| 回调接收 | Activity 层 `onNewIntent`/`onCreate` 接 Intent → 一次性事件 → 状态容器 | 单 Activity Compose 架构的官方 deep link 范式 |
| 先后顺序 | 网络层 / 状态管理 / 路由 / 后端契约 → 才接第一家 | 见上 1–5 |

## 7 人工步骤与合规

**只能由维护者在各平台控制台完成的事项（全部为人工步骤）**
| 平台 | 人工步骤 | 一手依据 |
| --- | --- | --- |
| QQ 互联 | 注册腾讯开放平台开发者（**开发者资质审核**，常见驳回原因见官方 FAQ：营业执照原件扫描件 + 手写用途 + 公章）；在 connect.qq.com 创建应用拿 **appid/appkey** 并填**回调地址**；**应用审核约 5 个工作日**，未通过时「仅开发者账号能登录」；appid/appkey 保密 | [申请注册相关问题](https://wiki.connect.qq.com/%E5%B8%B8%E8%A7%84%E9%97%AE%E9%A2%98-20170322) · [准备工作_OAuth2.0](https://wiki.connect.qq.com/%E5%87%86%E5%A4%87%E5%B7%A5%E4%BD%9C_oauth2-0) |
| 微信 | 微信开放平台注册开发者账号 → 创建**移动应用**并审核通过 → **申请「微信登录」并审核通过** → 拿 AppID/AppSecret；登记 **Android 包名 + 应用签名**；按端（Android/iOS/鸿蒙）设置**上架状态**（未上架有 100 次/天、错误码 10060 限制）；开发信息修改按「已上架需审核 / 未上架即刻生效」规则 | [开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html) · [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html) · [开发信息修改说明](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/guideline/dev_info.html) · [配置 Android 应用备用签名](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/guideline/backup_signature.html) |
| Google | 建 Google Cloud 项目 → 配 **OAuth 同意屏幕**（External/Internal、scopes、敏感 scope 需提交 verification）→ 创建 **Android OAuth client（包名 + SHA-1 签名指纹）** 和/或 **Web client ID**（Credential Manager 的 `serverClientId`）→ 视选型下载 `google-services.json` | [Setting up OAuth 2.0](https://support.google.com/googleapi/answer/6158849) · [Authorize access to Google user data](https://developer.android.com/identity/authorization) · [Submitting your app for verification](https://support.google.com/cloud/answer/13461325) |
| X | X Developer Portal 建 Project/App → **User authentication settings 开启 OAuth 2.0**、选 App 类型（Native App=Public）→ 登记 **Callback URI / Redirect URL**（精确匹配、最多 10 个）→ 配 App permissions 与 scopes → 确认访问层级（Free/Basic/Enterprise，**待核实**） | [developer-apps](https://docs.x.com/fundamentals/developer-apps) · [authorization-code](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code) |
| Apple | Apple Developer Program → App ID 启用 **Sign in with Apple**（Primary App ID）→ 建 **Service ID** 并绑定域名 + **Return URL** → 生成私钥 `.p8` + **Key ID** + **Team ID** → 后端据此生成 ES256 `client_secret` → 配 `redirect_uri` 端点与 `form_post` 处理 | [Sign in with Apple JS](https://developer.apple.com/documentation/signinwithapplejs) · [Create a private key](https://developer.apple.com/help/account/keys/create-a-private-key/) · [Incorporating... into other platforms](https://developer.apple.com/documentation/signinwithapplejs/incorporating_sign_in_with_apple_into_other_platforms) |
| 通用 | 托管 `https://<domain>/.well-known/assetlinks.json`（App Links 验证需 `application/json`、HTTPS、无重定向、含 `sha256_cert_fingerprints`）；准备**正式签名证书**（微信签名/Google SHA-1/App Links 指纹都绑正式签名，本仓 release 目前沿用 debug 签名，见 `app/build.gradle.kts:40`） | [Verify App Links](https://developer.android.com/training/app-links/verify-applinks) |

**合规与上线前置**
- **国内 App 备案 / 隐私政策**：工信部 App 备案是上架前置（**具体适用与流程待核实，需法务确认**）。隐私政策须**逐一列出第三方 SDK 收集个人信息的目的、方式、范围**；2026 年中央网信办/工信部/公安部个人信息保护专项行动通报中，「隐私政策未逐一列出第三方 SDK」是高发违规项（[通报解读，二手](https://ayoukeji.com/newsshow/20261105004.html)）；厂商自检指南亦要求「用户同意隐私政策前不得初始化 SDK / 不得提前收集信息」「SDK 声明与实际收集一致」（[荣耀开发者隐私自检指南，二手/厂商文档](https://developer.honor.com/cn/doc/guides/101581)）。QQ 与微信各自有 [QQ互联SDK隐私保护声明](https://wiki.connect.qq.com/qq%E4%BA%92%E8%81%94sdk%E9%9A%90%E7%A7%81%E4%BF%9D%E6%8A%A4%E5%A3%B0%E6%98%8E/) 与 [微信 Open SDK 个人信息处理规则](https://support.weixin.qq.com/cgi-bin/mmsupportacctnodeweb-bin/pages/RYiYJkLOrQwu0nb8) / [合规使用指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/agreement/sdk) —— 接入时须纳入隐私清单。
- **「Apple 登录强制规则」的适用范围**：[App Store Review Guidelines 4.8](https://developer.apple.com/app-store/review/guidelines/#sign-in-with-apple) 原文：「Apps that use a third-party or social login service (such as Facebook Login, Google Sign-In, Sign in with Twitter, Sign In with LinkedIn, Login with Amazon, **or WeChat Login**) to set up or authenticate the user's primary account... **must also offer Sign in with Apple as an equivalent option**」；豁免情形为「仅用自家账号体系 / 教育企业商务 App 用既有账号 / 政府或行业电子身份 / 特定第三方服务的客户端」。该规则是 **App Store 审核规则 → 约束上架 App Store 的 iOS/iPadOS/macOS/tvOS/watchOS 应用，不约束纯 Android 应用**；Apple 自己的「Sign in with Apple Guidelines」还专门写了「iOS 12 及更早……以及 web applications 无法直接用 JS 框架时，须手动控制登录请求」（[Apple 论坛引用的官方条文](https://developer.apple.com/forums/thread/122755)，**二手转录**）。→ 结论：**本仓（纯 Android）不因此被强制接 Apple 登录**；但若产品同时发 iOS 版且提供这四家登录，iOS 版必须等价提供「通过 Apple 登录」。**待核实**：同一账号体系跨端、或 Web 端也提供第三方登录时是否触发该规则（需上架前与法务/Apple 确认）。
- 五家的按钮/品牌规范（QQ 登录按钮、Google 品牌、Apple 按钮）属上线前 UI 合规项，**待核实**（本次未逐条取证）。

## 8 待实测 / 待核实清单

本机无真机/模拟器（`README.md`：「尚未在真机/模拟器上运行」）、无各平台开发者账号，以下无法确认：

1. **微信签名校验**：开放平台登记的「应用签名」是否确为签名 MD5、正式签名证书与包名的匹配、以及 `errcode=-6` 的实测排查（官方文档正文未取到，仅有二手线索）。
2. **Android App Links 验证**：`assetlinks.json` 托管、`android:autoVerify="true"` 实际验证结果、Android 15+ 动态规则、以及多 host / 通配 host 的发布要求。
3. **各 SDK 在 AGP 9.2.1 / compileSdk 36 / minSdk 26 下的兼容性**：QQ `open_sdk` 3.5.19 jar 是否含 `minCompileSdk` 元数据、微信 `wechat-sdk-android:6.8.0` AAR 元数据、国产 SDK 对 AGP 9 manifest 合并 / `<queries>` / FileProvider 的兼容。
4. **Compose BOM 2025.12.00 与 `androidx.credentials` / `googleid` 是否冲突**：BOM 映射 Compose 1.10.0（见 `docs/research/compose-navigation-shell.md`），新库是否抬高 Compose/lifecycle 版本、是否触发 `minCompileSdk=37` 类硬阻断。
5. **`androidx.credentials` 的确切稳定版本号与 `minSdk`**：release notes 当前页只列 `1.7.0-alpha03`，`1.5.0` 稳定线来自 issue tracker / 社区（二手）。`googleid` 稳定版本（见 `1.1.1` / `1.2.0`，二手）待核实。
6. **Google**：本仓是否必须引入 `google-services` 插件 / `google-services.json`；Android 新 OAuth client 是否仍允许自定义 scheme（页面出现「Custom URI scheme is not supported on Android or Chrome apps」提示）；Credential Manager 是否要求 Google Play services 版本门槛。
7. **X**：Callback URL 是否接受自定义 scheme（「other non-standard schemes」措辞歧义）；装了 X App 时 Web OAuth 回调是否被中断（devcommunity 报告，二手）；`users.email` 是否需额外权限；Free/Basic 层级对登录的影响；Native App(public) 由 App 还是后端换 token 的架构（**需与后端调研对齐**）。
8. **Apple**：Service ID 的 Return URL 是否只接受 `https://`；`form_post` → 自定义 scheme 回跳的实测；`id_token` 验签 / `client_secret` 生成 / Revoke tokens 的后端边界（**需与后端调研对齐**）；是否走 Firebase 整包（会新增依赖）。
9. **QQ**：当前 SDK 是否提供 Maven 坐标（「3.5.12 前支持 maven」）；自主开发是否支持 PKCE；`AuthAgent` 公开 API 边界；SDK 版与自主开发版最终选哪条（需维护者拍板）。
10. **微信**：目录里的「移动应用扫码登录」是否构成「未安装微信时的官方替代」；未上架 100 次/天限制对调试期的影响；Android 13 上去掉 `WXEntryActivity` intent-filter 后的回跳实测。
11. **后端契约（全部需与后端调研对齐）**：code / id_token / QQ `access_token` 的上行接口形状、token 端点由谁调、自有会话 token 的签发与校验、`openid`/`unionid`/`sub` 的绑定去重、解绑与注销、刷新与失效策略、以及「Apple 后端中转端点」的部署。
12. **合规**：工信部 App 备案流程、隐私政策逐条列 SDK 的最终清单、`App Store Review Guidelines 4.8` 跨端适用范围 —— 均需法务/上架前确认。
13. **本机现状核对（已确认，非待办）**：`AndroidManifest.xml` 无 `INTERNET` 权限、`dependencies` 无 OAuth SDK、无网络层/路由/DI/状态管理 —— 见 §1 与本笔记开头。

## 9 引用链接列表

**QQ 互联（官方）**
- https://wiki.connect.qq.com/qq%E7%99%BB%E5%BD%95/ （Android_SDK环境搭建）
- https://wiki.connect.qq.com/%E7%99%BB%E5%BD%95-%E6%A0%A1%E9%AA%8C%E7%99%BB%E5%BD%95%E6%80%81 （登录/校验登录态、AuthAgent.KEY_FORCE_QR_LOGIN）
- https://wiki.connect.qq.com/sdk%E6%8E%A5%E5%85%A5%E4%BD%BF%E7%94%A8%E8%AF%B4%E6%98%8E%E6%96%87%E6%A1%A3/ （SDK 接入使用说明）
- https://wiki.connect.qq.com/sdk%E4%B8%8B%E8%BD%BD/ （SDK下载、3.5.19、不再支持 OAuth1.0）
- https://wiki.connect.qq.com/oauth2-0%E7%AE%80%E4%BB%8B/ （移动应用两种接入方式 / webview / 浏览器）
- https://wiki.connect.qq.com/%E5%BC%80%E5%8F%91%E6%94%BB%E7%95%A5_server-side/ · https://wiki.connect.qq.com/%E5%BC%80%E5%8F%91%E6%94%BB%E7%95%A5_client-side/ （Implicit Grant / 60 天 token）
- https://wiki.connect.qq.com/%E4%BD%BF%E7%94%A8authorization_code%E8%8E%B7%E5%8F%96access_token/ · https://wiki.connect.qq.com/%E5%87%86%E5%A4%87%E5%B7%A5%E4%BD%9C_oauth2-0 （appkey=client_secret、保密）
- https://wiki.connect.qq.com/%E5%B8%B8%E8%A7%84%E9%97%AE%E9%A2%98-20170322 （开发者资质 / 应用审核约 5 工作日 / 未审核仅开发者可登录）
- https://wiki.connect.qq.com/qq%E4%BA%92%E8%81%94sdk%E9%9A%90%E7%A7%81%E4%BF%9D%E6%8A%A4%E5%A3%B0%E6%98%8E/ （SDK 隐私保护声明）
- https://wiki.connect.qq.com/ （WIKI 首页更新日志）

**微信开放平台（官方）**
- https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html （开发指南：强制 SDK / code / AppSecret 存服务端 / 100 次每天）
- https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html （WXEntryActivity 固定路径 / Android 11 queries / Android 13 intent-filter / Maven 坐标）
- https://developers.weixin.qq.com/doc/oplatform/Mobile_App/guideline/dev_info.html · https://developers.weixin.qq.com/doc/oplatform/Mobile_App/guideline/backup_signature.html （开发信息 / 备用签名）
- https://developers.weixin.qq.com/doc/oplatform/Mobile_App/FAQ/Android.html · https://developers.weixin.qq.com/doc/oplatform/Mobile_App/agreement/sdk （FAQ / 合规使用指南）
- https://support.weixin.qq.com/cgi-bin/mmsupportacctnodeweb-bin/pages/RYiYJkLOrQwu0nb8 （Open SDK 个人信息处理规则）
- https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.html （移动应用扫码登录，待核实用途）

**Google / Android（官方）**
- https://developer.android.com/identity/sign-in/legacy-gsi-migration （play-services-auth deprecated → Credential Manager）
- https://developers.google.com/android/guides/releases （「Removed the APIs related to Google Sign-in」）
- https://developer.android.com/identity/sign-in/credential-manager-siwg · https://developer.android.com/jetpack/androidx/releases/credentials （Credential Manager / 依赖坐标）
- https://developers.google.com/identity/protocols/oauth2/native-app （PKCE / code_verifier / redirect methods / loopback 对 mobile deprecated）
- https://developer.android.com/identity/authorization （Android client ID 需包名 + SHA-1；AuthorizationClient）
- https://support.google.com/googleapi/answer/6158849 · https://support.google.com/cloud/answer/15549257 · https://support.google.com/cloud/answer/13461325 （OAuth client / consent screen / verification）
- https://developer.android.com/training/app-links/add-applinks · https://developer.android.com/training/app-links/verify-applinks · https://developer.android.com/training/app-links/configure-assetlinks （App Links / autoVerify / assetlinks.json）
- https://developer.android.com/guide/navigation/design/deep-link （onNewIntent / handleDeepLink）
- https://developer.android.com/about/versions/13/behavior-changes-all?hl=zh-cn#intents （Android 13 Intent 过滤器变更）

**X / Twitter（官方）**
- https://blog.x.com/developer/en_us/topics/tools/2018/discontinuing-support-for-twitter-kit-sdk.html · https://devcommunity.x.com/t/discontinuing-support-for-twitter-kit-sdk/105094/1 （Twitter Kit 2018-10-31 停维）
- https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code · https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code.md （OAuth 2.0 + PKCE、confidential/public、scopes）
- https://docs.x.com/fundamentals/authentication/oauth-2-0/user-access-token · https://docs.x.com/fundamentals/developer-apps （token 端点 / app 类型 / Callback URL 规则）
- https://docs.x.com/fundamentals/authentication/oauth-1-0a/overview · https://docs.x.com/fundamentals/authentication/oauth-2-0/oauth-1-0a-token-exchange （OAuth 1.0a 现状与迁移）
- https://devcommunity.x.com/t/will-oauth-1-0a-user-context-continue-to-be-supported-for-api-v2/245571/3 · https://docs.x.com/livestream-api/authentication （官方社区 1.0a 继续支持 / 推荐 2.0）
- https://devcommunity.x.com/t/web-oauth-2-0-is-broken-on-android-if-twitter-app-is-installed/169698 （二手线索：Android 装 X App 时 Web OAuth 中断）

**Apple（官方）**
- https://developer.apple.com/documentation/signinwithapplerestapi · https://developer.apple.com/documentation/signinwithapplerestapi.md （REST API；Android 属 other platform → 用 JS）
- https://developer.apple.com/documentation/signinwithapplejs · https://developer.apple.com/documentation/signinwithapplejs/incorporating_sign_in_with_apple_into_other_platforms （authorize 端点 / form_post / Android 自定义 scheme 回跳 / 服务端 redirect_uri）
- https://developer.apple.com/documentation/accountorganizationaldatasharing/creating-a-client-secret · https://developer.apple.com/help/account/keys/create-a-private-key/ （ES256 client_secret / .p8 / Key ID / Team ID）
- https://developer.apple.com/documentation/authenticationservices/aswebauthenticationsession · https://developer.apple.com/documentation/signinwithapple/authenticating-users-with-sign-in-with-apple （Apple 平台专属框架）
- https://developer.apple.com/app-store/review/guidelines/ · https://developer.apple.com/app-store/review/guidelines/#sign-in-with-apple （4.8 与豁免）
- https://developer.apple.com/forums/thread/122755 （二手转录：4.8 原文与「other platforms」条款）
- https://firebase.google.com/docs/auth/android/apple （二手：Firebase Android 上的 Apple 登录整包）

**合规（二手 / 厂商文档，仅作线索）**
- https://ayoukeji.com/newsshow/20261105004.html （2026 个人信息保护专项行动通报解读）
- https://developer.honor.com/cn/doc/guides/101581 （应用市场隐私自检指南：SDK 声明、同意前不初始化）
- https://blog.csdn.net/weixin_31856057/article/details/166428411 · https://cnblogs.com/sp1086/p/9635769.html （微信「包名 + 签名 MD5」绑定关系，二手）
- https://react-native-nitro-google-sign-in.github.io/docs/setup/google-cloud/#quick-checklist （Credential Manager 可不引 google-services.json，二手）

**工程内文件（仅阅读，未改动）**
- `README.md` · `AGENTS.md` · `docs/agents/domain.md` · `app/build.gradle.kts` · `app/src/main/AndroidManifest.xml`
- `docs/research/ai-orb-reference-projects.md` · `docs/research/compose-navigation-shell.md`（写作风格与 BOM 1.10.0 事实）
- `docs/adr/0006-冷启动只读本地皮肤包不碰网络.md`（落点风格）
