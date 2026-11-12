# 微信登录：「直接跳转微信」与「移动应用扫码登录（Login_via_Scan）」一手事实核查

> **日期**：2026-11-11。
> **起因**：维护者想在登录页做「微信扫码」登录，并提「先看看能不能直接跳转微信吧」。需要查清：从 Android 原生 App 用一个 URL / scheme 直接跳微信是否就等于微信登录；官方有没有免 SDK 的跳转登录；`SendAuth.Req` 到底强制什么；文档目录里那篇「移动应用扫码登录」到底是什么场景、能不能当作免 SDK / 免装微信的替代；有没有「App 展示二维码 + 轮询后端」的官方形态。
> **与既有笔记的关系**：本文只补 `docs/research/mobile-oauth-landing.md`（2026-11-09）§2.2 微信一节里两处标了「待核实」的点——「直接跳转微信是否构成微信登录」与「`Login_via_Scan` 是否构成未安装微信时的官方替代」——用一手官方文档取证；五家 OAuth 的落点、对比矩阵、后端分工不在本文重复。
> **工程约束（本轮严格遵守）**：不引入任何依赖、不改 `AndroidManifest.xml` / 构建文件 / 既有笔记 / 任何代码；**只新建这一篇笔记**。当前仓仍无微信 SDK、无 OAuth 依赖。文中「App 该怎么做」一律不写实现代码，只写事实与边界。
> **取证口径**：一手来源限定为微信开放平台官方文档 `developers.weixin.qq.com`（含官方「查看 Markdown」版 `*.md` 与官方 SDK 制品 Javadoc）；二手只作线索并在 §8 显式标注、不作为结论依据。本机无真机、无开放平台账号、无 AppID/AppSecret，凡需真机或控制台才能定的一律归入「待实测」。

**确定程度三分类（全文标注）**
- **【确证】**：官方文档原文可直接引用、或官方制品（AAR Javadoc）可直接证明的事实。
- **【官方未明说】**：官方文档没写，只有相关措辞可推，不能当结论用。
- **【待实测】**：需真机 / 开放平台控制台 / 已审核 AppID 才能定的点。

> 取证方法备注：官方文档正文通过官方「复制给 LLM / 查看 Markdown」的 `*.md` 版（如 `Development_Guide.md`、`Login_via_Scan.md`）与原 HTML 页双向核对；`read_page` 走的是 Firecrawl 云端 JS 渲染后的 Markdown 抽取（非服务器原始字节），故关键结论均由 `web_fetch` 直取官方 `*.md` 复核。`javadoc.io` 是官方 Maven 制品 `wechat-sdk-android` 的 Javadoc **第三方托管镜像**，其内容是官方 AAR 的类清单，本文按「一手制品 + 第三方托管」标注，不把它当官方公告。

---

## 0 一句话回答

「直接跳微信」**不等于**微信登录；官方**没有**给第三方 App 一个可手工拼装的、免 SDK 的登录 URL / scheme。移动应用微信登录（`SendAuth.Req`）**强制集成微信 Open SDK、强制用户安装微信客户端**，App 侧只拿到 **code**。目录里那篇「移动应用扫码登录」确实是**官方真实存在的「App 内贴二维码、让用户用微信扫」能力**（支持 Android），但它**仍然是 Open SDK 的一部分、且多一道后端签名依赖**，因此**不能**当作免 SDK / 免装微信的登录路径。「App 展示二维码 + 轮询后端」这一形态在开放平台官方文档里**不存在**。

---

## 1 「从 App 内用 URL / scheme 直接跳转微信」是否等于微信登录？官方有没有免 SDK 的跳转登录？

### 1.1 官方 `weixin://` 是什么 —— 【确证：是「打开小程序」，不是登录】

- 官方文档确实存在 `weixin://dl/business/` 这个 scheme，但它位于**小程序**开发文档的「开放能力 → 获取小程序链接」，标题即「获取 URL Scheme」；原文用途是「适用于从短信、邮件、微信外网页等场景**打开小程序**」「通过 URL Scheme **打开小程序**的场景值为 1065」「**只能生成已发布的小程序的 URL Scheme**」「针对**非个人主体小程序**开放」。明文形式拼 `appid` / `path` / `query` / `env_version`，加密形式拼 `t=TICKET`。来源：[获取 URL Scheme](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/url-scheme.html)（[官方 Markdown 版](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/url-scheme.md)）。
- 同一页原文还写：「**Android 系统不支持直接识别 URL Scheme，用户无法通过 Scheme 正常打开小程序，开发者需要使用 H5 页面中转**」；以及「通过 URL Scheme 跳转到微信时，可能会触发系统弹框询问，若用户选择不跳转，则无法打开小程序」。即：这个 scheme 本身在 Android 上都不保证能直接跳、且目标是小程序页面，不是授权页。来源同上。
- **结论【确证】**：`weixin://dl/business/` 是「拉起小程序」的链接能力，**不发起 OAuth 授权、不产生登录凭证**；把它当「跳微信登录」是类别错误。它与「移动应用微信登录」分属两个文档体系（小程序 vs 开放平台移动应用）。
- 补充【确证】：开放平台「移动应用」目录下另有「APP 拉起小程序功能」「APP 拉起微信客服功能」「微信内网页跳转 APP 功能」等跳转能力（见 [接入指南目录](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html) 侧栏），均非登录；其中「APP 拉起小程序」同样走 Open SDK 的 `sendReq` 请求对象，不是公开 URL scheme。

### 1.2 官方移动应用登录是怎么「跳」微信的 —— 【确证：靠 SDK 的 `sendReq`，不是公开 URI】

- [移动应用微信登录开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html)（[官方 Markdown 版](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.md)）原文：「**开发者需要配合使用微信开放平台提供的 SDK 进行授权登录请求接入**。正确接入 SDK 后并拥有相关授权域（scope）权限后，开发者移动应用会**在终端本地拉起微信应用进行授权登录**，微信用户确认后微信将拉起开发者移动应用，并带上授权临时票据（code）。」
- [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html)（[官方 Markdown 版](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.md)）给出的是 `IWXAPI.sendReq(BaseReq)` 与固定回调 Activity `WXEntryActivity.handleIntent(...)`，原文：「`boolean sendReq(BaseReq req)`：第三方 app 主动发送消息给微信，发送完成之后会切回到第三方 app 界面」。
- **结论【确证】**：官方描述的「拉起微信授权页」是 **Open SDK 内部行为**（`SendAuth.Req` → `sendReq` → 微信 → 回跳 `WXEntryActivity`）。**官方文档从未向第三方公开一个可自行拼装的「登录 URI / scheme」**，也没写「可不经 SDK 直接发 Intent 拉起授权页」。所以「直接跳微信」如果只是打开微信 App，不构成登录；如果是指「像 SDK 那样拉起授权页并拿回凭证」，官方唯一给的路径就是集成 SDK。

### 1.3 关于「能不能抓 SDK 的 scheme 自己拼」—— 【官方未明说】+【确证：无官方支持】

- **【官方未明说】** SDK 内部拉起微信时具体用的 Intent / URI / 参数：官方文档没有披露，属实现细节。本文不推测、不引用任何抓包结果。
- **【确证】** 即便能拼出拉起动作，官方设了几道与「免 SDK」冲突的硬约束，均来自 [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html) 与 [安卓常见问题](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/FAQ/Android.html)：
  - 应用须「通过审核」，并在开放平台「填写**包名和签名**两个字段」；签名校验失败回调 `errcode=-6`；
  - 回跳必须落在「应用包名 + `.wxapi.WXEntryActivity`」这个固定 Activity，由它 `handleIntent` 交给 SDK；
  - `targetSdkVersion ≥ 30` 时 Manifest 必须加 `<queries><package android:name="com.tencent.mm" /></queries>`，否则「OpenSDK 的接口可能无法正常拉起微信」；
  - 官方 FAQ 排查「`wxapi.sendReq` 返回 true 但微信客户端并未启动」第一条是「1）微信是否安装」。
- **结论【确证】**：**官方不提供免 SDK 的跳转登录形态**；自行逆向拼 scheme 属非官方支持路径，且仍绕不开包名/签名绑定、审核、固定回调 Activity 与包可见性，不能作为工程方案。

---

## 2 移动应用微信登录（`SendAuth.Req`）是否强制 SDK、强制装微信？App 最终拿到什么？

### 2.1 强制集成微信 Open SDK —— 【确证】

- [移动应用微信登录开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html) 原文（见 §1.2）：「开发者需要**配合使用微信开放平台提供的 SDK**进行授权登录请求接入」。
- [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html) 原文：「**微信 Open SDK for Android 是微信开放平台面向 Android 端第三方应用提供的开发工具包，支持分享与收藏、微信登录、微信支付等能力**」；接入第 2 步即要求在 `build.gradle` 加 `api 'com.tencent.mm.opensdk:wechat-sdk-android:+'`（6.8.0 起用 `wechat-sdk-android`），并同步把 import 从 `com.tencent.mm.sdk` 改为 `com.tencent.mm.opensdk`。
- 同一页给出接入必做项：新建 `<包名>.wxapi.WXEntryActivity`（`exported=true`、`taskAffinity=包名`、`launchMode=singleTask`、透明主题）、实现 `IWXAPIEventHandler`、`api.handleIntent(getIntent(), this)`；Android 13 上**该 Activity 不得带 intent-filter**（否则微信无法回跳）；R8 混淆需 keep `com.tencent.mm.opensdk.**` / `com.tencent.wxop.**` / `com.tencent.mm.sdk.**`。
- **结论【确证】**：SDK 是硬前置，不是可选封装；且必然引入一个真实 Activity、一条依赖与 Manifest 改动——这正是本轮「不改 Manifest / 不引入依赖」约束下不能落地的部分。

### 2.2 强制用户安装微信客户端 —— 【确证】

- [移动应用微信登录开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html)「准备工作」原文三条，**逐字**：
  1. 「**目前移动应用上微信登录只提供原生的登录方式，需要用户安装微信客户端才能配合使用**」；
  2. 「对于 Android 应用，**建议总是显示微信登录按钮，当用户手机没有安装微信客户端时，请引导用户下载安装微信客户端**」；
  3. 「对于 iOS 应用……建议开发者接入微信登录时，先检测用户手机是否已安装微信客户端（使用 SDK 中 `isWXAppInstalled` 函数），对未安装的用户隐藏微信登录按钮，只提供其他登录方式」。
- [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html) 的 Android 11 适配段亦以「拉起微信」为前提，要求 `<queries><package android:name="com.tencent.mm" /></queries>`；[安卓常见问题](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/FAQ/Android.html) 把「微信是否安装」列为 `sendReq` 无效的首要排查项。
- **结论【确证】**：未安装微信时，官方给的不是「换一条免安装路径」，而是「引导下载 / 隐藏按钮换别的登录方式」。**不存在面向移动应用的官方「通过浏览器授权」或「免装微信」替代**（这一点与既有笔记 §2.2 的判断一致，本轮用原文复核确证）。

### 2.3 App 侧最终拿到什么凭证 —— 【确证：code，不是 token】

- [开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html)「返回说明」原文：「用户点击授权后，微信客户端会被拉起，跳转至授权界面……**SDK 通过 SendAuth 的 Resp 返回数据给调用方**」，返回字段为 `ErrCode` / **`code`（「用户换取 access_token 的 code，仅在 ErrCode 为 0 时有效」）** / `state` / `lang` / `country`。
- 后端换 token：`https://api.weixin.qq.com/sns/oauth2/access_token?appid=APPID&secret=SECRET&code=CODE&grant_type=authorization_code` → `access_token`(2h) / `refresh_token`(180 天) / `openid` / `scope` / `unionid`。官方安全原文：「**建议将 AppSecret、用户数据（如 access_token）放在 App 云端服务器，由云端中转接口调用请求**」「建议开发者自行管理业务登录态」。code「超时时间为 10 分钟，一个 code 只能成功换取一次 access_token 即失效」。
- **结论【确证】**：App 拿到的是**一次性 code**（外加原样带回的 `state`），**不是 access_token / ID token**；只有 `state` 防 csrf，**没有 PKCE**；token 交换与 AppSecret 在后端。这是「形态像 authorization_code 流、但回调靠微信客户端拉起固定 Activity 而非 HTTP redirect」的厂商流程。

### 2.4 成立前提清单 —— 【确证】

来自 [开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html) 与 [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html)：开放平台注册开发者账号 → 拥有**已审核通过的移动应用** → 取得 AppID / AppSecret → **申请「微信登录」且通过审核** → 登记**包名 + 应用签名** → 集成 Open SDK（建议 ≥ 6.8.0）→ Manifest 加 `WXEntryActivity`、`<queries>`（targetSdk ≥ 30）→ 用户设备已装微信。另有限制：**已认证主体的未上架应用微信登录 100 次/天**，超出报「系统错误，错误码：10060」，需把各端上架状态设为已上架并重新审核（开发指南 FAQ 第 3 条）。`scope` 只能填 `snsapi_userinfo`。

---

## 3 「移动应用扫码登录（Login_via_Scan）」到底是什么？能否作为 App 内免 SDK / 免装微信路径？

### 3.1 场景 —— 【确证：是「App 内显示二维码、用户用微信扫」，不是网站应用扫码登录】

- 官方页面确在「开放平台 → **移动应用** → **微信登录功能**」目录下，与「移动应用微信登录开发指南」并列；页面标题「**移动应用扫码登录**」，最后编辑于 **2026-06-25**，原文首句：「**本文介绍如何在移动应用中使用微信扫码登录能力，支持 iOS、Android、鸿蒙三端**」。来源：[移动应用扫码登录](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.html)（[官方 Markdown 版](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.md)）。
- 「功能概述」原文逐字：「**扫码登录能力，指的是开发者可在移动应用内使用此能力，拉取二维码，用户使用微信客户端扫描二维码后可以登录此移动应用。此能力可被应用在多设备登录、智能硬件、电视盒子等场景**」。
- 「Android 扫码授权流程」原文：「首先 App 通过 `IDiffDevOAuth.auth()` 接口发起授权，然后在 `OAuthListener.onAuthGotQrcode()` 回调接口中**获取二维码，在 App 中展示二维码**，最后**用户通过微信扫码授权**」。
- **结论【确证】**：它**正是**「App 里贴一张二维码、让（通常是另一台装了微信的设备）去扫」的官方移动应用能力，且**明确支持 Android**；**不是**「网站应用扫码登录」（后者见 §3.7，是 PC 网页内嵌二维码）。既有笔记 §2.2 把它标成「是否构成未安装微信时的官方替代」待核实，本轮已取到正文。

### 3.2 它属于 Open SDK，不是免 SDK —— 【确证】

- 官方页自身的小节标题就是「**SDK 扫码登录签名算法**」，且 Android 流程要求调用 `IDiffDevOAuth.auth(appId, scope, noncestr, timestamp, signature, OAuthListener)`，回调接口 `OAuthListener` 含 `onAuthGotQrcode` / `onQrcodeScanned` / `onAuthFinish`；iOS 为 `Auth:...` + `onAuthGotQrcode` + `onAuthFinish:...AuthCode:`；鸿蒙为 `IDiffDevOAuth.startOAuth()` + `OAuthCallback`（`onGotQRCode` / `onQRCodeScanned` / `onAuthFinish` / `onAuthError`）。来源同上。
- `IDiffDevOAuth` 与 `OAuthListener` 确在官方 SDK 制品内：`wechat-sdk-android-without-mta 6.8.0` 的 Javadoc 显示接口 `com.tencent.mm.opensdk.diffdev.IDiffDevOAuth`（含 `auth(...)` 方法，「需要保证在主线程调用」，已知实现类 `DiffDevOAuthImplV1`），相邻类为 `DiffDevOAuthFactory` / `OAuthErrCode`。来源：[Javadoc: IDiffDevOAuth](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/diffdev/IDiffDevOAuth.html)（**一手制品 + 第三方托管镜像**，见文首口径备注）。该 `com.tencent.mm.opensdk` 包名与 [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html) 注 3 「包名从 `com.tencent.mm.sdk` 修改为 `com.tencent.mm.opensdk`」一致。
- **结论【确证】**：扫码登录是 **Open SDK 内的一个接口族**（`diffdev` 包），发起授权、取二维码、收 `authCode` 全走 SDK 回调；**它不提供「不装 SDK 也能调」的入口**。因此「用 `Login_via_Scan` 绕开 SDK」不成立。

### 3.3 它比 `SendAuth.Req` 还多一道强制后端依赖 —— 【确证】

- 官方页「SDK 扫码登录签名算法 → 获取 Ticket」原文：「生成签名之前必须先获取对应的 **sdk_ticket**……有效期为 7200 秒，**通过 access_token 来获取**」，步骤为：①「使用 App 的 **AppID 和 AppSecret** 获取 `access_token`」；②「用第一步拿到的 `access_token`，采用 HTTP GET 方式请求 `https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=ACCESS_TOKEN&type=2`」；并明确「由于获取 sdk_ticket 的 API 调用次数非常有限……**开发者需在自己的服务存储与更新 sdk_ticket**」。
- 签名规则原文：参与字段 `appid` / `noncestr` / `sdk_ticket` / `timestamp`，按字段名 ASCII 字典序拼 `string1` 后 `signature = sha1(string1)`（参数名全小写、值不 URL 转义）。
- **结论【确证】**：`signature` 依赖 `sdk_ticket`，`sdk_ticket` 依赖 `access_token`，`access_token` 依赖 **AppSecret**——与开发指南「AppSecret 不能落客户端」一致，故**必须有后端签发/缓存 ticket 并算签名**。`SendAuth.Req` 路径的 App 侧不需要这一步。换言之扫码登录**不是「更轻」的替代，而是「更重」**（多一个后端签名链路 + 仍要 SDK）。
- **【官方未明说 / 待实测】** 该页**没有**写「扫码登录能力需要单独申请开通 / 单独审核 / 单独付费」的前置章节，也没写它是否受「未上架 100 次/天」限制。能力开通条件需以开放平台控制台为准 → **待实测**。

### 3.4 免装微信？—— 【官方未明说】+【确证：扫码方必须装微信】

- **【确证】** 原文「**用户使用微信客户端扫描二维码**」→ 执行扫码动作的那一方必须有微信客户端。
- **【官方未明说】** 文档**没有**写「展示二维码的这台设备可以不装微信」。但「多设备登录、智能硬件、电视盒子等场景」这一举例强烈暗示：二维码显示在一台（可能没有微信的）设备上，由**另一台**手机上的微信扫——电视盒子本身装不了微信扫码端。**这只是措辞推断，不能写成结论**。
- 另有一层未明说：展示方仍要跑 Open SDK 的 `IDiffDevOAuth`；**SDK 在未安装微信的本机上能否拉取二维码 / 完成回调**，官方该页与 Android 接入指南均未说明。
- **【待实测】** 「同一台手机上 App 显示二维码、用户再用这台手机自己的微信去扫」是否在官方支持范围内，以及「展示方完全不装微信」能否跑通，都需真机 + 已审核 AppID 验证。本轮无此环境。
- **对维护者问题的直接回答【确证】**：即便「展示方免装微信」将来被实测成立，`Login_via_Scan` **也仍然是免不了 SDK**（§3.2）且**免不了后端**（§3.3），所以它**不能**作为「App 内免 SDK / 免装微信」的登录路径。这一条不依赖 §3.4 的待实测项。

### 3.5 App 侧最终拿到什么 —— 【确证：authCode】+【官方未明说：换 token 端点】

- **【确证】** 三端回调都给 **`authCode` / `code`**：iOS `onAuthFinish:(int)errCode AuthCode:(NSString *)authCode`「成功登录」；Android `onAuthFinish(OAuthErrCode errCode, String authCode)`「用户点击授权后，回调该接口」；鸿蒙 `onAuthFinish: (authCode: string)`。**不是 token**；二维码本身以图片字节回到 App（`onAuthGotQrcode(String qrcodeImgPath, byte[] imgBuf)`，其中 `qrcodeImgPath` 标注「废弃」；鸿蒙为 `base64JpegImageBuffer`）。
- **【官方未明说】** `Login_via_Scan` 这一页**没有**写「拿到 `authCode` 后调哪个接口换 `access_token`」。它与开发指南同属「微信登录功能」目录、字段名是 code/authCode，而 [开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html) 与 [授权后接口调用（UnionID）](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Authorized_API_call_UnionID.html) 给出的唯一 code→token 端点是 `sns/oauth2/access_token`；**按目录关系推断应走该端点，但该页未明说 → 待实测**，不写成确证。
- 其余【确证】：Android 侧额外有 `onQrcodeScanned()`「用户扫描二维码之后」回调（可用于改 UI 状态，但不产生凭证）；iOS 参数 `schemeData`「会在扫码后拼在 scheme 后」，说明 iOS 侧有扫码后经 scheme 回跳的设计，Android 流程段未提 scheme。

### 3.6 能否作为 App 内免 SDK / 免装微信的登录路径 —— 【确证：不能】

把 §3.2 / §3.3 / §3.4 合并：

| 维度 | 官方事实 | 确定程度 |
| --- | --- | --- |
| 是否真实存在、且是「App 内贴二维码」 | 是，移动应用目录、支持 Android、2026-06-25 在编 | 【确证】 |
| 是否免 SDK | **否**，入口 `IDiffDevOAuth` 在 `com.tencent.mm.opensdk.diffdev`，属官方 AAR | 【确证】 |
| 是否免后端 | **否**，`signature` 依赖后端用 AppSecret 换的 `sdk_ticket` | 【确证】 |
| 是否免用户装微信 | 扫码方**必须**装微信；展示方是否可免装**官方未明说** | 【确证 + 官方未明说】 |
| App 拿到什么 | `authCode`（code），换 token 端点该页未明说 | 【确证 + 官方未明说】 |
| 能否替代 `SendAuth.Req` 做「免 SDK 登录」 | **不能** | 【确证】 |

### 3.7 与「网站应用扫码登录」的区别 —— 【确证】

| 维度 | 移动应用扫码登录（`Login_via_Scan`） | 网站应用微信登录（`Wechat_Login`） |
| --- | --- | --- |
| 文档归属 | 开放平台 →「移动应用」→ 微信登录功能 | 开放平台 →「网站应用」→ 微信登录功能 |
| 应用类型 | 已审核通过的**移动应用** AppID | 已审核通过的**网站应用** AppID + 授权回调域名 |
| 二维码从哪来 | SDK 回调 `onAuthGotQrcode` 把图片字节交给 App | PC 打开 `https://open.weixin.qq.com/connect/qrconnect?...`（`scope=snsapi_login`），或页面内嵌 `WxLogin` JS 对象（`wxLogin.js`），支持 `fast_login` 快速登录（Windows/Mac 已登录微信时免扫码） |
| 凭证怎么回 | SDK 回调 `onAuthFinish` → `authCode` | HTTP **重定向** `redirect_uri?code=CODE&state=STATE`；内嵌二维码时「用户使用微信扫码授权后通过 JS 将 code 返回给网站」 |
| 是否要 SDK | **要**（Open SDK `IDiffDevOAuth`） | 不要（浏览器 / JS） |
| scope | 页内写「App 所拥有的 scope」（开发指南为 `snsapi_userinfo`） | 「网页应用目前仅填写 `snsapi_login`」 |
| 轮询 | 无，SDK 回调 | 无，重定向 / JS 回传 |

来源：[移动应用扫码登录](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.html) 与 [网站应用微信登录开发指南](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.html)（[官方 Markdown 版](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.md)）。**【确证】** 两者是**不同应用类型、不同 AppID、不同凭证回传机制**的两套能力，不能混为一谈。
- **【官方未明说 / 待实测】** 把网站应用的 `qrconnect` / 内嵌二维码塞进 Android WebView 当 App 登录页，官方文档既未允许也未禁止；且它需要「网站应用」AppID 与授权域名，与本仓「移动应用」身份不匹配 → 不构成本仓的官方移动应用登录路径，标「官方未明说」。

---

## 4 有没有「App 展示二维码 + 轮询后端」这种由后端中转的扫码登录官方形态？—— 【确证：没有】

### 4.1 官方「移动应用扫码登录」不是轮询 —— 【确证】

- [Login_via_Scan](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.html) 的 Android 流程只有三步：SDK `auth()` 发起 → `onAuthGotQrcode` 拿到二维码并在 App 展示 → `onQrcodeScanned` / `onAuthFinish` 收 `authCode`。**二维码由 SDK 回调给 App**（不是后端生成后下发给 App），**授权结果由 SDK 回调推送**（不是 App 定时查后端）。全文没有「轮询」「查询状态」「长连接」字样，`OAuthListener` 也无轮询接口（仅 `onAuthGotQrcode` / `onQrcodeScanned` / `onAuthFinish`）。
- **结论【确证】**：官方这形态是「SDK 回调推送」，**不是**「App 展示二维码 + 轮询后端」。

### 4.2 官方确实有「二维码 + 状态回访」，但那是公众号带参二维码，不是登录 —— 【确证】

- 官方 [生成带参数的二维码](https://developers.weixin.qq.com/doc/service/api/qrcode/qrcodes/api_createqrcode.html) 原文：「如果用户还未关注公众号，则用户可以关注公众号，关注后**微信会将带场景值关注事件推送给开发者**」「如果用户已经关注公众号，在用户扫描后会自动进入会话，**微信也会将带场景值扫描事件推送给开发者**」；流程是 `POST /cgi-bin/qrcode/create` 拿 `ticket` → `GET mp.weixin.qq.com/cgi-bin/showqrcode?ticket=TICKET` 换二维码 → 微信把事件**推送**给开发者服务器。
- **结论【确证】**：这是**服务号 / 公众号**能力（所属权限集 id 3），回传的是「关注 / 扫描事件 + 场景值」，**不是 OAuth `code`、不产出发往 App 的登录凭证**；即便配合后端轮询/事件推送自建登录，底层也仍要另接网页授权或别的身份源，**不是开放平台为移动应用登录提供的官方形态**。

### 4.3 网站应用扫码登录也不是轮询，且不是「移动应用」—— 【确证 + 官方未明说】

- 如 §3.7：网站应用是 `redirect_uri?code=CODE` 的 **HTTP 重定向**或 JS 回传 `code`，**没有轮询**；它要求「网站应用」AppID 与 `redirect_uri` 域名与审核时填写的授权域名一致。
- 官方「关于微信快速登录功能的说明」（[Wechat_Login.html](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.html)）还写：微信 3.9.11 for Windows / 4.0.0 for Mac 及以上，PC 已登录微信时「**快速登录无需扫码，可直接在 Windows / Mac 设备上进行确认**」——这进一步说明它是 PC 侧能力。
- **【官方未明说】** 官方未提供「后端生成二维码 → App 轮询后端等扫码结果 → 后端签发会话」这样的移动应用登录规范文档；也没有把网站应用扫码登录描述为可供原生 App 内嵌的登录方式。

### 4.4 结论 —— 【确证】

**开放平台官方文档里不存在「App 展示二维码 + 轮询后端」这种由后端中转的扫码登录官方形态。** 与「二维码」相关的官方能力共三类，均不等于该形态：
1. **移动应用扫码登录**（`Login_via_Scan`）：SDK 回调给 `authCode`，要 SDK + 后端签名，无轮询；
2. **网站应用微信登录**（`qrconnect` / `wxLogin.js`）：HTTP 重定向或 JS 回传 `code`，是网站应用，无轮询；
3. **公众号带参二维码**：事件推送、场景值，属服务号能力，不产生登录 code。

若将来要做「后端中转」的扫码登录，那属于**自建方案**（二维码指向自建 H5 → 网页授权 / 小程序登录 → 后端签发自有会话），需后端另行调研与开放平台控制台确认，**不是**本轮能援引的官方移动应用登录路径。

---

## 5 四问汇总（每条标确定程度）

| # | 问题 | 结论 | 确定程度 |
| --- | --- | --- | --- |
| 1 | URL / scheme「直接跳微信」= 微信登录？有免 SDK 跳转登录吗？ | 官方 `weixin://dl/business/` 是**打开小程序**、不是登录；官方移动应用登录靠 SDK `sendReq` 拉起微信，**未公开可拼装的登录 scheme**；**官方无免 SDK 跳转登录** | 【确证】（SDK 内部 URI 细节属【官方未明说】） |
| 2 | `SendAuth.Req` 强制 SDK / 强制装微信？App 拿到什么？ | **两者都强制**；App 拿 **code**（`SendAuth.Resp`，非 token、无 PKCE、仅 `state`），后端用 AppID+AppSecret 换 token | 【确证】 |
| 3 | `Login_via_Scan` 是什么？能当免 SDK / 免装微信路径吗？ | 是**官方「App 内贴二维码、用户用微信扫」的移动应用能力**（支持 Android/iOS/鸿蒙），**不是**网站应用扫码登录；但它属 Open SDK `diffdev` 包、且需后端用 AppSecret 换 `sdk_ticket` 算签名、扫码方必须装微信 → **不能**当免 SDK / 免装微信替代。App 拿 `authCode` | 【确证】；展示方是否可免装微信【官方未明说】、`authCode` 换 token 端点【官方未明说】 |
| 4 | 有「App 展示二维码 + 轮询后端」官方形态吗？ | **没有**。官方移动应用扫码登录是 SDK 回调（无轮询），网站应用是重定向 / JS 回传（无轮询、且是另一类应用），公众号带参二维码是事件推送（不产生登录 code） | 【确证】 |

**对「先看看能不能直接跳转微信吧」的回答【确证】**：能跳微信（打开小程序 / 拉起客服 / SDK 拉起授权页）与「完成微信登录」是两件事；官方唯一受支持的移动应用微信登录入口是**集成 Open SDK 的 `SendAuth.Req`**，而 `Login_via_Scan` 是同一 SDK 内的另一种（带二维码的）授权形态，二者都**不免除 SDK**，且都要求「已审核移动应用 + AppID/AppSecret + 后端」。

---

## 6 「官方未明说 / 待实测」清单（不要把推测写成结论）

1. **【官方未明说】** SDK 拉起微信授权页具体使用的 Intent / URI / 参数——官方未披露，不得据抓包当官方接口。
2. **【官方未明说】** `Login_via_Scan` 的 `authCode` 用哪个端点换 `access_token`——该页未写；目录关系指向 `sns/oauth2/access_token`，**待实测**。
3. **【官方未明说 / 待实测】** 展示二维码的设备是否可完全不安装微信；「同机自扫」是否被支持。
4. **【官方未明说 / 待实测】** 扫码登录能力是否需单独申请开通 / 单独审核 / 是否受「未上架 100 次/天（10060）」限制——以开放平台控制台为准。
5. **【官方未明说 / 待实测】** 把网站应用 `qrconnect` / 内嵌二维码放进 Android WebView 当 App 登录页，官方未置可否，且需「网站应用」AppID + 授权域名。
6. **【待实测】** 上述任一形态在 AGP 9.2.1 / compileSdk 36 / targetSdk 36 / R8 下的构建与真机行为；`wechat-sdk-android` 6.8.0 的 `minCompileSdk`；`WXEntryActivity` 在单 Activity Compose 架构下的回跳。
7. **【待实测】** 开放平台登记的「应用签名」确为签名 MD5（既有笔记 §2.2 已标待核实，本轮未取到签名生成工具页正文，未推进）。
8. 本轮**未验证**任何「后端中转扫码登录自建方案」的可行性——它不在官方文档范围内。

---

## 7 一手来源清单（全部 `developers.weixin.qq.com` 官方文档 / 官方 SDK 制品）

| 来源 | URL | 本文用它确证什么 |
| --- | --- | --- |
| 移动应用微信登录开发指南（含官方 Markdown 版） | [HTML](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html) · [.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.md) | 强制 SDK、强制装微信、`SendAuth.Resp` 给 code、code→token 端点、AppSecret/后端安全要求、100 次/天限制、前提清单 |
| Android 接入指南（含官方 Markdown 版） | [HTML](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html) · [.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.md) | Open SDK 定义与 Maven 坐标、`sendReq`、`WXEntryActivity`、`<queries>`、Android 13 陷阱、包名改 `opensdk`、混淆 keep |
| 移动应用扫码登录（含官方 Markdown 版） | [HTML](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.html) · [.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.md) | 功能概述原文、三端接口 `IDiffDevOAuth`/`OAuthListener`、`onAuthGotQrcode`/`onAuthFinish(authCode)`、`sdk_ticket` + sha1 签名、后端强制依赖 |
| 网站应用微信登录开发指南（含官方 Markdown 版） | [HTML](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.html) · [.md](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.md) | `qrconnect` / `wxLogin.js` / `snsapi_login` / `redirect_uri?code=` / 快速登录，用于与移动应用扫码登录区分 |
| 获取 URL Scheme（小程序，含官方 Markdown 版） | [HTML](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/url-scheme.html) · [.md](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/url-scheme.md) | `weixin://dl/business/` 是打开小程序、Android 需 H5 中转、非个人主体小程序、系统弹框 |
| 授权后接口调用（UnionID，移动应用） | [HTML](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Authorized_API_call_UnionID.html) | 唯一的 code→`access_token` 端点（用于说明 `authCode` 推断去向，标注为未明说） |
| 安卓常见问题 / 开发者须知 | [Android FAQ](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/FAQ/Android.html) · [开发者须知](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/FAQ/Required_Developer_Knowledge.html) | 包名 + 签名绑定、审核通过才能调起、「微信是否安装」排查项 |
| 生成带参数的二维码（服务号） | [HTML](https://developers.weixin.qq.com/doc/service/api/qrcode/qrcodes/api_createqrcode.html) | 公众号带参二维码是「事件推送 + 场景值」，不产生登录 code |
| 官方 SDK 制品 Javadoc（**第三方托管镜像**） | [IDiffDevOAuth](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/diffdev/IDiffDevOAuth.html) · [索引](https://javadoc.io/doc/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/latest/index-all.html) | `IDiffDevOAuth` / `OAuthListener` 确在 `com.tencent.mm.opensdk.diffdev`（官方 `wechat-sdk-android` AAR，6.8.0），证明扫码登录属 SDK |

---

## 8 二手线索（仅作线索，未采信、未作为任何结论依据）

- 检索命中 [CSDN「Android集成微信SDK扫码登录功能」](https://blog.csdn.net/lmnabc2006/article/details/84871801) 一类文章，标题与正文片段提到「登录页面加入微信二维码扫码登录入口（类似于 PC 端扫一扫登录）」、且明确写「集成微信 SDK」——**只作为「该能力在社区被讨论」的线索**，本文所有事实均回到 §7 的官方页面取证。
- 检索命中第三方文档镜像 [wxopen-docs 移动应用微信登录开发指南](https://wdk-docs.github.io/wxopen-docs/mobile/login/guide.html) 与 Authing 的「PC 网站使用微信扫码登录」「移动 APP 使用小程序登录」页面——均为**非官方转述 / 第三方方案**，仅作线索，未采信。
- 未在官方开放平台文档中检索到任何「免 SDK URL scheme 登录」或「App 轮询后端扫码登录」的页面；`site:developers.weixin.qq.com` 针对这些关键词的命中全部落在本文 §7 的页面内，且内容与上述确证一致。
