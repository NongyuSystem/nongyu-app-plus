# 微信网页授权 URL 能否从 Android App 直跳微信打开并完成登录（而不展示二维码）—— 一手事实核查

> **日期**：2026-11-12。
> **起因**：`/wayfinder` 地图 #76 的 research 票 **#81「探索：能否直接跳转微信打开网页登录（而不展示二维码）」**，按 `/research` 技能规则做一手事实核查。维护者在川农统一身份认证登录页看到「微信扫码」二维码，问：既然二维码里是一个微信网页授权 URL，能不能从 Android App 用 Intent / URL scheme **直接拉起微信客户端打开这个 URL**、让用户确认登录，**不展示二维码让用户扫**？
> **前置事实（本轮输入，不重复取证）**：该二维码已离线解码（OpenCV `QRCodeDetector` / `detectAndDecodeMulti` / `wechat_qrcode` 三路互证），完整 payload 为
> `https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx7c220ea873c845e0&redirect_uri=https%3A%2F%2Fauth.sicau.edu.cn%2Fapi%2Fv1%2Fcas%2FloginByQr%3Fappid%3Dywtb%26cascallback%3Dwxlogined&response_type=code&scope=snsapi_userinfo&state=loginByQr:4w4a1rzv1NOuVLZvGJ1Q8x93zDdtJeq_XE30al61Bew=#wechat_redirect`
> 结构：**服务号/公众号网页授权**（`connect/oauth2/authorize` + `snsapi_userinfo` + `#wechat_redirect`），授权后 302 回学校 CAS 的 `/api/v1/cas/loginByQr?appid=ywtb&cascallback=wxlogined`；`state` 为 `loginByQr:` + 一次性事务串，由服务端每次下发。appid `wx7c220ea873c845e0` 是学校平台自己的微信应用。
>
> **与 `docs/research/wechat-scan-direct-jump.md`（2026-11-11）的关系 —— 补充并划界，不推翻**：那篇查的是**微信开放平台**层面：农屿自己去开放平台注册移动应用，用 Open SDK 的 `SendAuth.Req` / `Login_via_Scan`（`IDiffDevOAuth`）/ 网站应用 `qrconnect` / 小程序 `weixin://dl/business/` 完成「微信登录」，结论是「官方无免 SDK 的跳转登录、跳微信 ≠ 登录」。**本篇查的不是同一套身份提供方**：本篇的身份提供方是川农统一身份认证平台（`auth.sicau.edu.cn`），授权入口是**服务号网页授权 URL**，凭证回落到学校服务器而非农屿 App。按票 #81 的要求，**不把那篇的「开放平台无免 SDK 直跳」搬来论证「学校 CAS 网页授权不能直跳」**；那篇只用来提供两份补证（§4 的 `SendAuth.Req` 前提、§3.2 的 `weixin://dl/business/` 定性），并在 §4 显式标注「开放平台移动应用登录 ≠ 学校 CAS 网页授权」这条边界。本篇新增的是一整套此前两篇都没查过的官方文档：**公众号/服务号微信网页授权**。
>
> **与 `docs/research/campus-sso-wechat-direct-auth.md`（2026-11-12）的关系 —— 续篇，升级结论，不推翻**：那篇因二维码 payload 未解出、全仓 grep `loginByQr` 零命中，把「学校 CAS 自带微信登录到底是什么形态」和「有没有直跳路径」都标为 **【仓内未见】**，结论是「仓内查不到，需对学校登录页实测/抓包才能定论」。本篇是它的续篇：payload 已解码，**形态已从【仓内未见】升级为【确证】**（= 服务号网页授权 + CAS 回调），并把「直跳」从「查不到」推进到「形态已查明 + 一手官方约束核查 → 无官方支持的可落地直跳形态，卡点分四层」。该篇自己写下的「【仓内未见】≠【官方不存在】」「微信 code → go-admin-tgt 链路不存在（`wechatCode` 被桥接层丢弃）」「后端只认 `go-admin-tgt`」三条继续有效，本篇不重复、不改写（§6、§7 引用）。
>
> **与 `docs/research/backend-plus-login-ui-facts.md`（2026-11-12）的关系**：该篇确证「后端只接收 `go-admin-tgt`、不提供 CAS 登录页 URL、`wechatCode` 在生产链路被丢弃、App 从哪里取得 tgt 待核实」。本篇只把这条作为「授权结果如何回到 App」那一层的仓内事实引用，不重复其全文。
>
> **取证口径**：一手优先——微信官方文档 `developers.weixin.qq.com`（原 HTML 页与官方「复制给 LLM / 查看 Markdown」的 `*.md` 双向核对）、官方 SDK 制品（Maven Central `wechat-sdk-android` 的 Javadoc）、Android 官方文档 `developer.android.com`、腾讯官方 `developer.work.weixin.qq.com`（**属企业微信体系，作旁证并标注体系差异**）。二手只作线索并在 §10 显式标注、不作为结论依据。本机无真机、无学校账号、无校园网、无开放平台资质/控制台，凡需真机 / 抓包 / 控制台才能定的一律归入【待实测】。
> **取证方法备注**：`javadoc.io` 是官方 Maven 制品 `wechat-sdk-android*` 的 Javadoc **第三方托管镜像**，其内容是官方 AAR 的类清单，本文按「一手制品 + 第三方托管」标注，不把它当官方公告或官方接入指南。`read_page` 走 Firecrawl 云端 JS 渲染后的 Markdown 抽取（非服务器原始字节），关键结论均由 `web_fetch` 直取官方 `*.md` / 官方页复核。
>
> **工程约束（本轮严格遵守，只读核查）**：**只新建这一篇笔记**；未修改任何既有文件、未改代码/Manifest/构建文件、未引入任何依赖、**未 git commit**、**未访问那个真实授权 URL**（`state` 一次性，访问会消耗并触发真实授权流程，且需校园网与学校账号）。文中「App 该怎么做」一律不写实现代码，只写事实与边界。
>
> **确定程度四档（全文标注）**
> - **【确证】**：官方原文可直接引用，或官方制品（AAR Javadoc）/ 仓内已解码事实可直接证明。
> - **【官方未明说】**：官方文档没写，只有相关措辞可推，不能当结论用；**「没有公开文档」不等于「官方禁止」**。
> - **【仓内未见】**：对指定范围做过 grep 后零命中；只说明**仓内**没有这份材料，不说明官方不存在。
> - **【待实测】**：需真机 / 微信客户端 / 开放平台控制台 / 抓包 / 校园网 / 学校账号才能定，本轮无此环境。

---

## 0 一句话回答

**【确证 + 官方未明说 + 待实测】综合：不存在官方支持的、可从 Android App 用 Intent / URL scheme「直接拉起微信客户端打开这个网页授权 URL 并完成登录、且不展示二维码」的完整形态。** 建议按**不可行**处理，卡点不在一层，而在四层叠加（§6）：

1. **URL 层**：`connect/oauth2/authorize` 这套网页授权官方文档的**设计前提就是「用户在微信客户端中访问第三方网页」**，且授权 `code` 是通过 **HTTP 302 回到 `redirect_uri`（学校 CAS 服务器）**，不回 App；`#wechat_redirect` 是「终端判断是否需要带上身份信息」的标记。【确证】（普通浏览器/WebView 打开的确切表现【官方未明说 + 待实测】）
2. **无官方 scheme / Intent 层**：官方公开的 `weixin://dl/business/` 是**打开小程序**、Android 还不支持直接识别；`IWXAPI.openWXApp()` 只「launch wechat app」不带 URL；官方 SDK 制品里唯一形状接近的 `OpenWebview.Req`（带 `url` 字段）**没有官方接入指南**、且仍要集成 SDK、且它的 `Resp` 不带 OAuth `code`。【确证 + 官方未明说 + 待实测】
3. **state 层**：`state=loginByQr:<一次性串>` 由**服务端每次下发**，微信对授权链接做**正则强匹配校验（参数顺序不对授权页无法正常访问）**，`redirect_uri` 域名还须与公众平台「网页授权域名」配置一致 → App **不能自己拼一个固定 URL 直跳**。【确证 + 仓内事实】
4. **回 App 接收层**：农屿 App 既没有 SDK 回调接收点（仓内无 `WXEntryActivity` / `SendAuth` / `OpenWebview` 任何调用），后端也只认 `go-admin-tgt`、`wechatCode` 被丢弃 → 即便微信侧授权成功，登录态**没有一条已存在的链路交回 App**。【确证 + 仓内未见】

**最简替代（§7）**：仍用二维码让用户用微信「扫一扫」打开该 URL（这**正是**官方文档给出的「让微信客户端打开网页」形态），或退一步用系统浏览器打开学校登录页；App 侧只走既有的 `go-admin-tgt` 交接（「App 如何取 tgt」本身仍是仓内待核实项）。

---

## 1 先把命题拆开：这条 URL 是什么，二维码在其中扮演什么角色

**【确证】** 解出的 payload 与微信官方「微信网页授权」文档给出的构造格式**逐参数对得上**：

| 参数 | 解出的值 | 官方定义 |
| --- | --- | --- |
| 路径 | `open.weixin.qq.com/connect/oauth2/authorize` | 官方「构造链接」原文即此路径 |
| `appid` | `wx7c220ea873c845e0` | 「服务号的唯一标识（仅支持填写已认证的服务号 appid）」 |
| `redirect_uri` | `https://auth.sicau.edu.cn/api/v1/cas/loginByQr?appid=ywtb&cascallback=wxlogined`（已 urlEncode） | 「授权后重定向的回调链接地址……必须要和在微信公众平台……网页授权域名的配置一致」 |
| `response_type=code` | 有 | 「返回类型，请填写 code」 |
| `scope=snsapi_userinfo` | 有 | 「弹出授权页面……需要用户手动同意」 |
| `state` | `loginByQr:<一次性串>` | 「重定向后会带上 state 参数……用于防止越权行为」 |
| `#wechat_redirect` | 有 | 「**无论直接打开还是做页面302重定向时候，必须带此参数**」 |

来源：[微信网页授权（服务号-指南）](https://developers.weixin.qq.com/doc/service/guide/h5/auth.html)（[官方 Markdown 版](https://developers.weixin.qq.com/doc/service/guide/h5/auth.md)，「最后一次编辑于 2026-07-04」）。

**【确证】** 因此它**不是**开放平台「网站应用扫码登录」（那是 `connect/qrconnect` + `snsapi_login`，见 [网站应用微信登录开发指南](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.md)：「网页应用目前仅填写 `snsapi_login`」），**也不是**开放平台移动应用登录（`SendAuth.Req`）。它是**公众号/服务号网页授权**——完全不同的文档体系、不同 appid 类型、不同凭证回传机制（HTTP 302 到 `redirect_uri`）。这一点决定了：本篇不能拿 §4 那两套开放平台能力来论证它能不能直跳。

**【确证】二维码的角色 = 「让微信客户端打开这个 URL」的官方载体。** 微信官方起步指南在介绍微信开发者工具「预览」按钮时原文：「**会将当前网址转换为二维码，方便你直接用微信客户端扫码打开**」；同页定义「微信网页，就是在普通网页运行在微信内置浏览器中时，可以有一些微信能力实现」。来源：[微信网页开发-起步指南](https://developers.weixin.qq.com/doc/service/guide/h5/start.md)（[HTML](https://developers.weixin.qq.com/doc/service/guide/h5/start.html)）。

→ 所以「绕开二维码」这个命题，**等价于要找到另一条官方通道，把同一个 URL 送进微信客户端的内置浏览器里打开，并把授权结果接回 App**。§2 查前一半，§3/§6 查后一半。

---

## 2 命题一（票 #81 必查 1）：网页授权 URL 是否强制在微信内置浏览器打开？`#wechat_redirect` 作用？普通浏览器/WebView 会怎样？`snsapi_userinfo` 是否要求微信客户端内会话？

### 2.1 【确证】官方文档把「微信客户端内」写进前提，且限定「已认证服务号」

- [微信网页授权](https://developers.weixin.qq.com/doc/service/guide/h5/auth.html)（[.md](https://developers.weixin.qq.com/doc/service/guide/h5/auth.md)）**开篇第一句**：「**如果用户在微信客户端中访问第三方网页**，服务号可以通过微信网页授权机制，来获取用户基本信息，进而实现业务逻辑。」
- 同页「一、开发前必读 → 1. 适用账号」原文：「**网页授权支持已认证的服务号使用，其他类型的账号（部分公众号/小程序/网站应用/移动应用等除了已认证服务号之外的账号）均不支持使用微信网页授权登录功能**」；「特殊补充：已认证的政府、事业单位和媒体类型的公众号也有权限」。错误码表另有 **10016「不支持微信开放平台的Appid，请使用服务号Appid」**。
- [微信网页开发（服务号）](https://developers.weixin.qq.com/doc/service/guide/h5/) 原文：「**微信内置浏览器可以打开 URL 网页，并且在打开时，可以借助特有的 API 接口，完成一些外部浏览器无法完成的动作**。比如设置分享内容、扫一扫、支付等。」；同页表格把「网页授权」列为微信网页开发能力之一（「仅服务号可用」）。
- [公众号第三方平台网页授权](https://developers.weixin.qq.com/doc/oplatform/Third-party_Platforms/2.0/Before_Develop/Official_Accounts/official_account_website_authorization.html)（[.md](https://developers.weixin.qq.com/doc/oplatform/Third-party_Platforms/2.0/Before_Develop/Official_Accounts/official_account_website_authorization.md)）「第一步：请求 CODE」原文：「……**使用微信客户端打开以下链接**（严格按照以下格式，包括顺序和大小写……）」，随后给出与本文 payload 同形的 `connect/oauth2/authorize?...#wechat_redirect` 链接。
- **结论【确证】**：这套授权流程的官方描述全部以「微信客户端 / 微信内置浏览器」为环境前提，且明确排除「网站应用 / 移动应用等」appid 类型；发起方式官方只给 `<a href>` / `location.href`（即在**网页内**跳转），没有给「第三方原生 App 用 Intent 发起」的入口。

### 2.2 【确证】`#wechat_redirect` 必须带，作用是「终端判断是否带身份信息」

- [微信网页授权](https://developers.weixin.qq.com/doc/service/guide/h5/auth.html)（[.md](https://developers.weixin.qq.com/doc/service/guide/h5/auth.md)）参数表逐字：「`#wechat_redirect` | **是** | **无论直接打开还是做页面302重定向时候，必须带此参数**」。
- 它是 **URL fragment（`#` 后）**，不会作为请求发到服务端，是给客户端/「终端」看的标记。腾讯官方 [企业微信·构造网页授权链接](https://developer.work.weixin.qq.com/document/path/91861) 把它说得更明白：「`#wechat_redirect` | 是 | **终端使用此参数判断是否需要带上身份信息**」；同一文档的 [91120 版](https://developer.work.weixin.qq.com/document/path/91120) 对第三方应用 oauth2 链接写作「是 | 固定内容」、对企业 oauth2 链接写作「是 | 终端使用此参数判断是否需要带上身份信息」。**注意体系差异**：这是企业微信开发者中心的措辞，但两处链接的主机、路径、`#wechat_redirect` 与「带微信身份」的语义与本文 payload 同源，作旁证；微信公众平台自己的文档只写了「必须带」，没写「为什么」→ 那句作用解释按「腾讯官方旁证 + 微信侧【官方未明说】」标注。
- **结论【确证】**：`#wechat_redirect` 是**必须**、且是「直接打开 / 302 重定向两种情形都要带」的客户端侧标记；它进一步印证「这个链接需要微信客户端（终端）参与处理身份」，而不是一个普通浏览器可独立完成的 OAuth 端点。

### 2.3 【官方未明说 + 待实测】普通浏览器 / Android WebView / 系统浏览器里打开会怎样

- **【确证，腾讯官方但属企业微信体系】** [企业微信常见问题·FAQ](https://developer.work.weixin.qq.com/document/path/90457) 有一节标题即「**企业微信打开页面提示『请在微信客户端打开链接』**」，正文逐字：「**企业微信不支持打开需要带微信身份的链接。** 包括几种情况：**访问链接为 oauth2 链接，appid 填写为公众号 appid**。这种情况，建议开发者更换链接，或者将原公众号服务迁移到企业微信服务。公众平台发布的文章，点击『阅读原文』。**这种只能在微信客户端才能访问**。」→ 这是腾讯官方明文：公众号 oauth2 链接属于「需要带微信身份的链接」，**同为腾讯客户端的企业微信都不支持打开**。但企业微信 ≠ 普通浏览器 / Android WebView，不能把这条**等价推广**成「Chrome 里一定报某句错」。
- **【官方未明说】** 在微信公众平台 / 开放平台官方文档中，本轮**未检到**逐字写明「在普通浏览器 / Android WebView / 系统浏览器 / 自定义 Tab 中打开 `connect/oauth2/authorize` 会返回什么」的段落——既没写「会显示『请在微信客户端打开链接』」，也没写「可以正常完成授权」。社区与二手资料广泛记录「请在微信客户端打开链接」这一提示（§10），但**官方站内无专述页**。按票 #81 要求，**不把「官方没有公开文档」写成「官方禁止」**，也不把二手提示写成官方明文。
- **【确证，相关但不专述】** [微信网页授权能力调整公告](https://developers.weixin.qq.com/community/minihome/doc/000c2c34068880629ced91a2f56001)（微信团队，2022-05-09，官方公告）原文：「微信网页授权能力是为了优化**用户在微信内登录网站应用的体验**而设计的」「当开发者在网页中在不规范使用发起 `snsapi_userinfo` 网页授权时，**微信将默认打开网页快照页模式进行基础浏览**」；快照页「默认对用户屏蔽网页授权弹窗……用户在快照页中仅可进行滑动浏览操作」，且「快照页内也无法使用微信其它 JS-SDK 的能力」。→ 这些「弹窗 / 快照页 / 固化」全是**微信客户端内**的行为，佐证该 URL 依赖微信客户端上下文；但公告同样没有写「外部浏览器打开会怎样」。
- **【待实测】** 确切表现需真机验证，且**不能用本文这条带一次性 `state` 的真实 URL 去试**（会触发/消耗真实授权）。可行做法：用自己已认证服务号构造一个同形但 `state` 为自己随机值、`redirect_uri` 为自有域名的链接，分别在系统浏览器、Android `WebView`、Chrome Custom Tab、微信内置浏览器里打开并抓包对照。本轮无此环境。

### 2.4 【确证部分 + 官方未明说】`snsapi_userinfo` 是否要求「微信客户端内的会话」

- **【确证】** [微信网页授权](https://developers.weixin.qq.com/doc/service/guide/h5/auth.html)（[.md](https://developers.weixin.qq.com/doc/service/guide/h5/auth.md)）：「`snsapi_userinfo`：用来获取用户的基本信息的。但这种授权**需要用户手动同意**」「由于用户同意过，所以**无须依赖用户关注服务号**」；「三、获取用户授权」把 `snsapi_userinfo` 分两种情况——**情况 1**：「对于已关注服务号的用户，**如果用户从服务号的会话或者自定义菜单进入本服务号的网页授权页，此时静默授权，无需用户在弹窗同意**」；**情况 2**（其他情况）：「当用户使用**用户交互（点击按钮）跳转授权链接时，微信会拉起弹出窗口确认**」「当你在用户未做任何交互动作，直接跳转授权链接时，网页将被固化，展示『点击访问完整网页』字样，**微信不推荐打开网页就直接授权的业务场景**」。
- **【确证】** 官方对「静默」的例外**只**给在「已关注 + 从服务号会话/自定义菜单进入」这个**微信客户端内**的上下文里；其余路径都要弹出确认。这佐证「微信客户端内的会话/页面上下文」是该流程的运行环境。
- **【官方未明说】** 官方文档**没有**逐字写「`snsapi_userinfo` 要求微信客户端内存在已登录的微信会话」这句。可援引的最接近的官方措辞是 §2.3 企业微信 FAQ 的「需要带微信身份的链接」（腾讯官方、企业微信体系）。「必须已登录微信客户端」属于**推断**，不写成结论 → 【待实测】（§8 第 7 项）。
- 顺带【官方未明说】：官方网页授权文档写 `state`「可以填写 a-zA-Z0-9 的参数值，最多 128 字节」，而本 payload 的 `state` 含 `:` 与 `_`；[公众号第三方平台网页授权](https://developers.weixin.qq.com/doc/oplatform/Third-party_Platforms/2.0/Before_Develop/Official_Accounts/official_account_website_authorization.md) 则写「可以填写任意参数值，最多 128 字节」。两处措辞不一致，「可以」不等于「只能」，本轮**不据此推断链接有效性或无效性**，只记录差异。

### 2.5 命题一小结

| 子问题 | 结论 | 档位 |
| --- | --- | --- |
| 是否强制在微信内置浏览器打开 | 官方全篇以「用户在微信客户端中访问第三方网页」「使用微信客户端打开以下链接」为前提，发起方式只给网页内 `<a href>` / `location.href`；授权 `code` 走 HTTP 302 回 `redirect_uri`（学校服务器） | 【确证】（措辞推定「强制」，无逐字「必须」句 → 措辞层面【官方未明说】） |
| `#wechat_redirect` 作用 / 是否必须 | **必须**，且「无论直接打开还是做页面302重定向时候」都要带；作用是「终端判断是否需要带上身份信息」（腾讯官方企业微信文档措辞） | 【确证】 |
| 普通浏览器 / Android WebView / 系统浏览器打开会怎样 | 官方站内**无明文**；腾讯官方企业微信 FAQ 明写「不支持打开需要带微信身份的链接」并把公众号 oauth2 链接列为其一（旁证，体系不同） | 【官方未明说】+【确证·旁证】+【待实测】 |
| `snsapi_userinfo` 是否要求微信客户端内会话 | 弹窗/静默/快照页规则全部是微信客户端内行为；「必须已登录微信会话」官方未逐字写 | 【确证·部分】+【官方未明说】 |

---

## 3 命题二（票 #81 必查 2）：Android 端有没有官方支持的、可从第三方 App「直接拉起微信客户端并打开指定网页 URL」的 scheme / Intent？

**先区分两件事**（票 #81 明确要求）：**① 打开微信**（拉起微信 App 主界面）与 **② 在微信内置浏览器里打开这个授权页并完成 OAuth、把结果接回 App**，是两件事。能找到的官方能力都只覆盖 ①，或覆盖「② 的前半」但不覆盖「② 的后半」。

### 3.1 【确证】Android 官方机制：能不能被微信接住，取决于微信自己声明的 intent-filter，而微信没公开「打开指定网页」的契约

- Android 官方 [Intents and intent filters](https://developer.android.com/guide/components/intents-filters) 原文：「**Implicit intents** do not name a specific component, but instead declare a general action to perform, which allows a component from another app to handle it」「When you use an implicit intent, **the Android system finds the appropriate component to start by comparing the contents of the intent to the intent filters declared in the manifest file of other apps** on the device. If multiple intent filters are compatible, **the system displays a dialog so the user can pick which app to use**」；`ACTION_VIEW`「Use this action in an intent with `startActivity()` when you have some information that an activity can show to the user」，`CATEGORY_BROWSABLE`「The target activity allows itself to be started by a web browser」。
- **结论【确证】**：从 App 发 `Intent(ACTION_VIEW, Uri.parse(url))` 在 Android 上是**通用机制**（`https://open.weixin.qq.com/...` 这种 http(s) URI 默认会被**浏览器**接走，不是微信内置浏览器）；要让微信接住，必须由**微信**在其 Manifest 里为该 URI / scheme 声明 intent-filter，**而微信官方从未向第三方公开「打开指定网页 URL」的 scheme / intent-filter 契约**（§3.2、§3.3、§3.4 逐项核查）。Android 官方 [Declare package visibility needs](https://developer.android.com/training/package-visibility/declaring) 另要求：targetSdk ≥ 30（Android 11）需 `<queries>` 声明才能与指定包交互——这是「能否看见微信」的前置，**不是**「让微信打开网页」的能力（微信侧同样要求，见 §3.3）。

### 3.2 【确证】`weixin://` 家族逐项：官方公开的是「打开小程序」，不是「打开网页授权」

- 官方 [获取 URL Scheme（小程序）](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/url-scheme.html)（[.md](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/url-scheme.md)）给出两种形态：明文 `weixin://dl/business/?appid=*APPID*&path=*PATH*&query=*QUERY*&env_version=*ENV_VERSION*`；加密 `weixin://dl/business/?t=*TICKET*`。原文用途逐字：「**适用于从短信、邮件、微信外网页等场景打开小程序**」「通过 URL Scheme 打开小程序的场景值为 1065」「**只能生成已发布的小程序的 URL Scheme**」「**针对非个人主体小程序开放**」。
- 同页 **Android 专项限制**逐字：「**Android 系统不支持直接识别 URL Scheme，用户无法通过 Scheme 正常打开小程序，开发者需要使用 H5 页面中转，再跳转到 Scheme 实现打开小程序**」，并给 `location.href = 'weixin://dl/business/?t=*TICKET*'` 的 H5 中转示例；注意事项还有「**通过 URL Scheme 跳转到微信时，可能会触发系统弹框询问，若用户选择不跳转，则无法打开小程序**」「部分浏览器会限制打开网页直接跳转」。
- **结论【确证】**：`weixin://dl/business/` 的参数是 `appid`/`path`/`query`（小程序页面）或 `t=TICKET`（服务端签发的小程序链接票据），**没有「任意 http(s) URL」参数**；它拉起的是**小程序页面**，既不发起 `connect/oauth2/authorize` 网页授权、也不产生回 App 的凭证。用它「直跳微信打开学校授权 URL」是类别错误。且 Android 上官方自己就写明不保证能直接跳。
- **【官方未明说】** 除 `weixin://dl/business/` 外，微信是否还有其它未公开的 `weixin://` 子 scheme 可用于打开网页：官方文档站未列，本轮不抓包、不逆向、不引用任何抓包结果。**没有公开文档 ≠ 官方禁止/官方不存在**，此项只记为【官方未明说】。

### 3.3 【确证】`IWXAPI` 接口面：`openWXApp()` 只「打开微信」，`sendReq` 是 SDK 内部通道

- 官方 SDK 制品 [IWXAPI Javadoc（wechat-sdk-android-with-mta 5.4.0，javadoc.io 托管镜像）](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-with-mta/5.4.0/com/tencent/mm/opensdk/openapi/IWXAPI.html) 方法清单逐项：`registerApp` / `unregisterApp` / `handleIntent` / `isWXAppInstalled` / `getWXAppSupportAPI` / **`openWXApp()`** / `sendReq(BaseReq)` / `sendResp(BaseResp)` / `detach` / `setLogImpl`。其中 `openWXApp()` 的说明逐字：**「launch wechat app」「返回 true if launched」**——**无参数、不带 URL**；`sendReq`「send request to wechat app」。
- 官方 [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html)（[.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.md)）：「`boolean sendReq(BaseReq req)`：**第三方 app 主动发送消息给微信，发送完成之后会切回到第三方 app 界面**」；接入第 1 步「只有审核通过的应用才能进行开发」，第 2 步加 `api 'com.tencent.mm.opensdk:wechat-sdk-android:+'`（「从 6.8.0 版本开始，请使用 `wechat-sdk-android`」，「包名从原来的 `com.tencent.mm.sdk` 修改为 `com.tencent.mm.opensdk`」）；第 4 步要求 `<包名>.wxapi.WXEntryActivity`（`exported=true`、`taskAffinity=包名`、`launchMode=singleTask`）+ `IWXAPIEventHandler` + `api.handleIntent(...)`；「Android 11 更新 OpenSDK 适配」段逐字：「由于 Android 11 软件包可见性特性的影响，**OpenSDK 的接口可能无法正常拉起微信**……需要在主工程的 `AndroidManifest.xml` 中增加 `<queries><package android:name="com.tencent.mm" /></queries>`」；「Android 13 可能存在的问题适配」段要求 `WXEntryActivity` 不带 intent-filter。
- **结论【确证】**：`openWXApp()` **只完成「① 打开微信」**；`sendReq` 是 SDK 私有的 `BaseReq` 序列化通道，**不是公开 URI scheme**，且要整套 SDK 前置（审核应用 + AppID + 包名签名 + `WXEntryActivity` + `<queries>` + 装微信）。官方接入指南**没有**给出「不经 SDK、直接发 Intent 让微信打开指定网页」的写法。

### 3.4 【确证 + 官方未明说 + 待实测】`OpenWebview.Req`：官方制品里唯一形状接近的 API，但不是可落地方案

这是本轮最值得记录的一手发现——**官方 SDK 制品里确实存在一个「第三方 App 让微信打开指定 URL」的请求类**：

- [OpenWebview 类 Javadoc（wechat-sdk-android-without-mta 6.8.0）](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/modelbiz/OpenWebview.html) 类注释逐字：「**Third App jump to wechat webview** / **第三方APP跳转微信webview**」。
- [OpenWebview.Req Javadoc](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/modelbiz/OpenWebview.Req.html) 字段逐字：只有一个 `public java.lang.String url`，注释「**The url you want jump to** / **要跳转的链接地址**」；其余为 `getType()` / `checkArgs()` / `toBundle` / `fromBundle`。
- [OpenWebview.Resp Javadoc](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/modelbiz/OpenWebview.Resp.html) 字段只有 `public java.lang.String result`（**无注释、无 code 字段**），外加继承的 `errCode` / `errStr` / `openId` / `transaction`。
- 相邻类 [WXOpenBusinessWebview.Req](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/modelbiz/WXOpenBusinessWebview.Req.html) 是**限定业务**的（「业务类型 1：香港钱包，2：获取发票抬头」+ `queryInfo` HashMap），**没有通用 `url`**，不能拿来打开任意网页。
- **【确证】** 该 API 存在、属官方 SDK 制品、语义是「第三方 App 跳转微信 webview」并接受一个 `url`。因此「官方完全没有『在微信内打开 URL』的能力」这句话**不成立**——本篇不这么写。
- **但【官方未明说】的约束非常多，且不把「没有公开文档」写成「官方禁止」**：以 `site:developers.weixin.qq.com OpenWebview` 等检索，官方文档站**未命中** `OpenWebview` 的接入指南/权限/URL 白名单/版本要求页面（命中的是 Javadoc 索引与开放社区里一条「鸿蒙SDK OpenWebview.Req 咨询」帖）。也就是说，官方**只**在 AAR 的类清单里留了这个类，没有为它写「可以用它打开 `connect/oauth2/authorize` 并完成授权」的文档。具体未明说的点：
  1. 是否允许**任意** `url`，还是受业务域名 / 白名单限制；
  2. 打开后是否运行在**微信内置浏览器上下文**（即是否识别 `#wechat_redirect`、能否「带上身份信息」）；
  3. `OpenWebview.Resp.result` 的语义与取值；
  4. 6.8.0 之后（`wechat-sdk-android` 正式制品）该类是否仍可用、是否需要单独申请权限；
  5. 是否仍受「已认证服务号 / 网页授权域名」约束。
- **【确证·结构性卡点】** 即便上述全部实测通过，它**仍解决不了命题的后半**：它走 `IWXAPI.sendReq`，因此 §3.3 的整套 SDK 前置全部生效（农屿当前**没有**微信 SDK、没有开放平台审核移动应用、没有 `WXEntryActivity`、没有 `<queries>`）；而网页授权的 `code` 是 **302 到 `redirect_uri`（学校 `auth.sicau.edu.cn`）**，在学校服务器/微信内置浏览器里闭环，**不会回到 App**；`OpenWebview.Resp` 也**没有 `code` 字段**（只有 `result`）。
- **【待实测】** 见 §8 第 2 项。**结论定性**：这是一条「有官方制品、无官方文档、且结构上仍不完整」的候选，**不能**据此判定票 #81 的「可行」。

### 3.5 【确证】把「打开微信」与「在微信内打开授权页并完成 OAuth」逐项分开

| 能力 | 做到哪一步 | 官方依据 | 档位 |
| --- | --- | --- | --- |
| `Intent(ACTION_VIEW, https://open.weixin.qq.com/connect/oauth2/authorize?...)` | 系统按 intent-filter 解析；http(s) URI 默认由**浏览器**接走，不是微信内置浏览器 | [Intents and intent filters](https://developer.android.com/guide/components/intents-filters) | 【确证】 |
| `weixin://dl/business/?t=TICKET` | 拉起**小程序页面**；Android 官方写明「不支持直接识别、需 H5 中转」、可能弹系统框、非个人主体、只能已发布小程序 | [获取 URL Scheme](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/url-scheme.md) | 【确证】 |
| `IWXAPI.openWXApp()` | **仅「launch wechat app」**，无参数、不带 URL | [IWXAPI Javadoc](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-with-mta/5.4.0/com/tencent/mm/opensdk/openapi/IWXAPI.html) | 【确证】 |
| `SendAuth.Req` + `sendReq` | 拉起微信**移动应用授权登录页**，回跳 `WXEntryActivity`，App 拿 `code`——但这是**开放平台移动应用 OAuth**，与本篇学校 CAS 网页授权不是一套（§4） | [移动应用微信登录开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.md) | 【确证·边界】 |
| `OpenWebview.Req` + `sendReq` | 「第三方APP跳转微信webview」并给 `url`；**无官方接入指南**，且 `Resp` 无 OAuth `code` | [OpenWebview.Req Javadoc](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/modelbiz/OpenWebview.Req.html) | 【确证·制品】+【官方未明说】+【待实测】 |
| 微信「扫一扫」扫二维码 | **在微信内置浏览器里打开该授权 URL**——官方设计的入口 | [微信网页开发-起步指南](https://developers.weixin.qq.com/doc/service/guide/h5/start.md) | 【确证】 |

### 3.6 【确证 + 仓内未见 + 待实测】反向通道：微信内网页跳转 App（授权完成后怎么回 App）

即使解决了「发起」这一跳，还有「回 App」这一跳。官方确实有**反向**能力：

- [微信内网页跳转 App](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_H5_Launch_APP.html)（[.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_H5_Launch_APP.md)，「最后一次编辑于 2026-06-25」）原文：「提供了**微信开放标签**以满足微信内网页跳转到 App 的需求」「**微信内网页无法跳转任意的 App**。开发者需要在微信开放平台**登记域名与移动应用（App）的绑定关系**，网页只可以跳转其域名绑定的移动应用（App）」；关联说明要求「将『JS 接口安全域名绑定的服务号』绑定在『移动应用的微信开放平台账号』下，并确保**服务号与此开放平台账号同主体且均已认证**」「只能绑定同一微信开放平台账号下**审核通过的移动应用**」「每月可修改绑定 3 次」。
- **【确证】** 这条通道的前提全部落在**学校侧服务号 + 农屿开放平台资质**：学校 `auth.sicau.edu.cn` 的服务号须为已认证、其 JS 接口安全域名须绑定农屿在开放平台**审核通过的移动应用**、且两者同主体。它不是「从 App 直跳微信」，而是「微信内网页回跳 App」，且**不解决发起那一跳**。
- **【仓内未见】** 仓内对 `*.kt` 全量 grep `weixin://|openWXApp|OpenWebview|SendAuth|snsapi_userinfo|connect/oauth2` **零命中**；对 `*.md` 的命中全部落在 [wechat-scan-direct-jump.md](wechat-scan-direct-jump.md)、[campus-sso-wechat-direct-auth.md](campus-sso-wechat-direct-auth.md)、[mobile-oauth-landing.md](mobile-oauth-landing.md) 三篇既有笔记的讨论文字里，**没有任何实现代码、Manifest 声明、SDK 依赖或 `WXEntryActivity`**。仓内也没有学校服务号与农屿移动应用的绑定关系记录。
- **【待实测】** 学校侧是否愿意/能够做该绑定、农屿是否有已审核移动应用、以及 `cascallback=wxlogined` 是否就是这类回跳钩子（§8 第 3、4 项）。

---

## 4 命题三（票 #81 必查 3）：Open SDK 免 SDK 路径？`SendAuth.Req` / `Login_via_Scan` 各自前提？—— 边界标注，**不越界论证**

> **边界声明（按票 #81 明确要求）**：本节两套能力属于**微信开放平台「移动应用」登录**——身份提供方是「农屿自己在开放平台注册的移动应用 + 微信账号」，appid 是移动应用 AppID，凭证回 `WXEntryActivity` / SDK 回调。**本次学校 CAS 的 `connect/oauth2/authorize` 是服务号网页授权，appid 是学校服务号，凭证 302 回学校服务器**。两者**不是同一套身份提供方、不是同一套授权体系**。本节**只作前提标注与边界划分**，**不**用「开放平台移动应用登录需要 SDK/需要装微信」来论证「学校 CAS 网页授权不能直跳」，反之也不论证「能直跳」。本篇 §0/§6 对「不可行」的论证全部来自 §2（网页授权 URL 自身约束）与 §3（Android/SDK 官方通道）与 §5（state 服务端下发）与 §6 第 4 层（仓内接收链路），不依赖本节。

- **【确证】开放平台没有「不集成 SDK 也能跳」的路径**：[移动应用微信登录开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html)（[.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.md)）「准备工作」逐字：「**目前移动应用上微信登录只提供原生的登录方式，需要用户安装微信客户端才能配合使用**」「对于 Android 应用，建议总是显示微信登录按钮，当用户手机没有安装微信客户端时，请引导用户下载安装微信客户端」；「第一步：请求 CODE」逐字：「**开发者需要配合使用微信开放平台提供的 SDK 进行授权登录请求接入**。正确接入 SDK 后并拥有相关授权域（scope）权限后，开发者移动应用会**在终端本地拉起微信应用进行授权登录**，微信用户确认后微信将拉起开发者移动应用，并带上授权临时票据（code）」。前提清单：开放平台注册 → **已审核通过的移动应用** → AppID/AppSecret → **申请「微信登录」且通过审核** → 登记**包名 + 应用签名** → 集成 Open SDK（建议 ≥ 6.8.0）→ `WXEntryActivity` + `<queries>`（targetSdk ≥ 30）→ 装微信；`scope` 「**只能填 `snsapi_userinfo`**」；App 拿到的是 **code**（`SendAuth.Resp`，非 token），由**后端**用 AppSecret 换 token（「建议将 AppSecret、用户数据……放在 App 云端服务器」）。
- **【确证】`SendAuth.Req` 前提**：SDK（硬前置）+ 已审核移动应用 + 已申请微信登录权限 + 包名/签名登记 + 装微信 + `WXEntryActivity` 回调接收；App 侧拿 `code`。来源同上 + [Android 接入指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.md)。
- **【确证】`Login_via_Scan`（`IDiffDevOAuth`）前提**：[移动应用扫码登录](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.html)（[.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.md)）原文：「**本文介绍如何在移动应用中使用微信扫码登录能力，支持 iOS、Android、鸿蒙三端**」「扫码登录能力，指的是开发者可在**移动应用内使用此能力，拉取二维码，用户使用微信客户端扫描二维码后可以登录此移动应用**」；Android 流程「首先 App 通过 **`IDiffDevOAuth.auth()`** 接口发起授权，然后在 `OAuthListener.onAuthGotQrcode()` 回调接口中获取二维码，在 App 中展示二维码，最后用户通过微信扫码授权」。它**仍属 Open SDK（`com.tencent.mm.opensdk.diffdev`）**，且「SDK 扫码登录签名算法」段逐字：「生成签名之前**必须先获取对应的 `sdk_ticket`**……通过 `access_token` 来获取」「使用 App 的 **AppID 和 AppSecret** 获取 `access_token`」「**开发者需在自己的服务存储与更新 `sdk_ticket`**」，`signature = sha1(string1)`。→ 前提：SDK + **后端用 AppSecret 换 ticket 并算签名** + 扫码方装微信；App 拿 `authCode`。
- **【确证·边界】** 两者都是「**在 App 内贴/发起二维码或直接拉起授权页给农屿自己的移动应用用**」，与「学校 CAS 的网页授权 URL 能否从 App 直跳」是**不同问题**。特别是 `Login_via_Scan` 生成的是**开放平台移动应用**的二维码，而学校那张二维码编码的是**服务号网页授权 URL**（§1），二者连二维码内容都不是一类。
- **【确证】`weixin://dl/business/` 不是登录**：见 §3.2 与 [wechat-scan-direct-jump.md §1.1](wechat-scan-direct-jump.md)。官方小程序 URL Scheme 的能力边界与本篇结论一致。

---

## 5 命题四（票 #81 必查 4）：`state` 一次性、服务端下发；App 能否自己拼一个固定 URL 直跳？这套机制与「直跳」兼容吗？

- **【确证·仓内事实 + 官方定义】** payload 的 `state=loginByQr:<43 字符一次性串>` 由**学校服务端每次生成并随二维码下发**（票 #81 给定事实；离线解码三路互证）。官方对 `state` 的定义：[微信网页授权](https://developers.weixin.qq.com/doc/service/guide/h5/auth.md)「**重定向后会带上 state 参数**……用于**防止越权行为**」；[移动应用微信登录开发指南](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.md)（作为 OAuth `state` 语义的旁证）「用于保持请求和回调的状态，授权请求后原样带回给第三方。该参数可用于**防止 csrf 攻击**」。授权成功后回 `redirect_uri/?code=CODE&state=STATE`，`code`「**只能使用一次，5 分钟未被使用自动过期**」（网页授权文档）。**注意区分**：一次性的是 `code`（官方明文）与**服务端给 `state` 设的事务语义**（票 #81 给定 / 仓内实证）；官方网页授权文档**没有**逐字写「`state` 一次性作废」→ 那部分属【仓内事实 + 官方未明说】，见 §8 第 6 项。
- **【确证】App 不能自拼固定 URL**，有三条官方/结构性理由：
  1. **state 每次由服务端下发**：二维码就是它的载体（§1）。「直跳」要绕开二维码，就必须**另有渠道拿到服务端每次新生成的 state**——[campus-sso-wechat-direct-auth.md §2、§4](campus-sso-wechat-direct-auth.md) 已把「学校侧是否有这样的 URL / 端点 / appid」标为【仓内未见】，本轮对 `*.kt`/`*.md` 的 grep 亦零命中（§3.6）。**没有 state 来源，就拼不出当次有效的授权 URL**。
  2. **微信对授权链接做正则强匹配**：[微信网页授权](https://developers.weixin.qq.com/doc/service/guide/h5/auth.md)「注意事项」逐字：「由于授权操作安全等级较高，所以在发起授权请求时，**微信会对授权链接做正则强匹配校验，如果链接的参数顺序不对，授权页面将无法正常访问**」；[公众号第三方平台网页授权](https://developers.weixin.qq.com/doc/oplatform/Third-party_Platforms/2.0/Before_Develop/Official_Accounts/official_account_website_authorization.md) 亦要求「**严格按照以下格式，包括顺序和大小写**」。→ 手工拼装/改参数风险高。
  3. **`redirect_uri` 域名绑定在服务端**：官方要求「必须要和在微信公众平台……**网页授权域名**的配置一致」，错误码 **10003「redirect_uri 域名与后台配置不一致」**、`10005`「此服务号并没有这些 scope 的权限」、`10016`「不支持微信开放平台的 Appid，请使用服务号 Appid」。本 payload 的 `redirect_uri` 是学校 `auth.sicau.edu.cn` 域名、`appid` 是学校服务号——**农屿 App 既不是该服务号主体、也无法改这两个值**，最多只能「转述」这条 URL，不能「生成」它。
- **【确证】与「直跳」不兼容**：「直跳」隐含「App 持有一个稳定、可自行构造的入口」。而这套机制是**「服务端每次下发一次性事务 URL → 微信客户端内打开 → 302 回学校服务器」**的服务端事务流，App 在其中**既不是发起方也不是回调方**。把一次性 URL 硬编码/复用，既拿不到当次 `state`，也过不了微信的强匹配与防重放。
- **【确证·补充】** `#wechat_redirect`（§2.2）与 `cascallback=wxlogined`（payload 内）都提示「微信客户端参与」「登录后回调」的设计；但 `cascallback=wxlogined` 的**具体含义、是否就是回 App 的钩子**，官方无文档、仓内无记录 → 【待实测】（§8 第 3 项）。

---

## 6 结论（票 #81 必查 5）：分档结论 + 卡在哪一层

### 6.1 对核心问题的直接回答

> 「能否从 Android App 用 Intent / URL scheme **直接拉起微信客户端打开这个网页授权 URL**、让用户确认登录，而**不展示二维码让用户扫**？」

**按「不可行（无官方支持的可落地形态）」处理**，但精确分档如下，**不把「没有公开文档」写成「官方禁止」**：

| 档位 | 结论 |
| --- | --- |
| **【确证】** | ① 该 URL 是服务号网页授权，官方文档以「微信客户端中访问」为前提，`code` 走 HTTP 302 回学校服务器；② 官方公开的 `weixin://dl/business/` 是打开小程序（Android 还不支持直接识别），`openWXApp()` 只打开微信不带 URL；③ 官方未提供「免 SDK / 免官方通道、第三方 App 让微信内置浏览器打开指定网页并完成 OAuth」的 scheme 或接入指南；④ 微信对授权链接做正则强匹配、`redirect_uri` 域名须与公众平台配置一致、`state` 由服务端下发 → App 不能自拼固定 URL；⑤ 仓内无 SDK / `WXEntryActivity` / 任何接收实现，后端只认 `go-admin-tgt`。 |
| **【官方未明说】** | ① 普通浏览器 / Android WebView / 系统浏览器打开该 URL 的确切表现（官方站内无专述，不写成「官方禁止」）；② `OpenWebview.Req` 是否允许任意 URL、是否在微信内置浏览器上下文、`Resp.result` 语义、是否需额外权限；③ 是否存在其它未公开的 `weixin://` 子 scheme；④ 「必须已登录微信客户端」/ 最低微信版本 / 是否需关注学校服务号；⑤ `snsapi_userinfo` 是否逐字要求「微信客户端内已登录会话」。 |
| **【仓内未见】** | ① 任何 `weixin://` 登录 scheme / `OpenWebview` / `openWXApp` / `SendAuth` 调用（`*.kt` 零命中）；② 学校侧让 App 直接换取一次性 `state` 的接口；③ 学校服务号 JS 安全域名 ↔ 农屿开放平台移动应用的绑定关系；④ 授权结果交回 App 的实现。 |
| **【待实测】** | §8 全部 8 项；尤其 `OpenWebview.Req` 真机行为、普通浏览器表现、学校 CAS 回调后如何交回 `go-admin-tgt`。 |

**若一定要给「确切形态」**：本轮**找不到**任何官方支持、可写进代码的 Intent / scheme 形态。唯一「官方制品里存在、形状接近」的是 `OpenWebview.Req`（`com.tencent.mm.opensdk.modelbiz.OpenWebview.Req`，字段 `url`）+ `IWXAPI.sendReq(req)`（§3.4），但它**没有官方接入指南**、**要整套 SDK**、**`Resp` 不带 `code`**、**授权结果不经 App**，因此**不构成**票 #81 要的「可行」。**不**推荐把它当方案落地。

### 6.2 卡在哪一层（四层，逐层点名）

```
[App] --(1) 想用 Intent/scheme 把 URL 送进微信内置浏览器--> [微信客户端]
                                                              |
                                                              v
[学校 auth.sicau.edu.cn /api/v1/cas/loginByQr] <--(3) HTTP 302 + code + state-- [微信内置浏览器 网页授权]
        |
        v  (4) 登录态怎么回 App？
     [农屿 App / 后端]
```

1. **第 1 层 · URL 必须在微信内置浏览器打开 + 无官方 scheme/Intent**：§2.1/§2.2（官方前提、`#wechat_redirect` 终端标记）+ §3.2/§3.3（`weixin://dl/business/` 是小程序、`openWXApp()` 不带 URL）。普通浏览器/WebView 的表现【官方未明说 + 待实测】，腾讯官方企业微信 FAQ 明写「不支持打开需要带微信身份的链接」（旁证）。**这是首要卡点。**
2. **第 2 层 · 无官方「在微信内打开任意 URL 并完成 OAuth」的公开通道**：`OpenWebview.Req` 只是 AAR 里的一个类，官方无接入指南，约束【官方未明说】，且仍要 SDK + 审核移动应用（§3.4）。
3. **第 3 层 · `state` 服务端下发 + 链接强匹配 + 域名绑定**：App 不能自拼固定 URL 直跳（§5）。「绕开二维码」首先要另找一条拿当次 `state` 的服务端渠道，仓内【仓内未见】。
4. **第 4 层 · 无 SDK 回调接收 / 无回 App 链路**：网页授权 `code` 302 到学校服务器，不是回 `WXEntryActivity`；农屿仓内无 SDK、`WXEntryActivity`、`OpenWebview` 任何实现；后端只认 `go-admin-tgt`、`wechatCode` 被丢弃（[backend-plus-login-ui-facts.md](backend-plus-login-ui-facts.md)、[campus-sso-wechat-direct-auth.md §2.3](campus-sso-wechat-direct-auth.md)）。即便前三层都被实测打通，**登录态仍没有一条已存在的链路交回 App**。反向的「微信内网页跳转 App」开放标签要求学校服务号与农屿开放平台移动应用同主体绑定（§3.6），仓内不具备。

> **层间关系**：第 1、2、3 层是「发起」这一跳的卡点，第 4 层是「回 App」这一跳的卡点；**四层任意一层不成立，票 #81 的「直跳替代扫码」就不成立**。当前 1/3/4 层已有【确证】或【仓内未见】的硬约束，第 2 层留给【待实测】但不改变总判断。

---

## 7 若不可行：最简替代

按「改动最小、不引入新资质、不触碰一次性 state」排序：

1. **仍用二维码（推荐，零新增依赖）**：App 展示学校登录页下发的那张二维码，用户用微信「扫一扫」打开授权 URL，在微信内置浏览器里确认授权。**这正是官方文档给出的「让微信客户端打开网页」形态**（[微信网页开发-起步指南](https://developers.weixin.qq.com/doc/service/guide/h5/start.md)「预览……转换为二维码，方便你直接用微信客户端扫码打开」）。App 侧继续走既有 `go-admin-tgt` 交接（仓内事实：后端只接收 `go-admin-tgt`）。**注意**：「App 如何取得 `go-admin-tgt`」本身仍是 [backend-plus-login-ui-facts.md §2.4](backend-plus-login-ui-facts.md) 与 [campus-sso-wechat-direct-auth.md §5.3 第 5 项](campus-sso-wechat-direct-auth.md) 的待核实项，本篇不臆测。
2. **系统浏览器兜底**：用 `Intent(ACTION_VIEW, 学校统一身份认证登录页)` 把用户送到外部浏览器自行完成（含扫码）。**这不是「直跳微信」**，只是把整段流程交给浏览器；且网页授权在普通浏览器里能否走通属【官方未明说 + 待实测】（§2.3），App 取 `tgt` 的链路问题依旧存在。
3. **不推荐**：逆向 `weixin://` 私有 scheme / 抓 SDK 内部 Intent 自己拼。属非官方支持路径，仍绕不开 §6.2 第 1、3、4 层；且官方对授权链接做正则强匹配，脆弱且易失效。
4. **若将来真要做「无二维码」**：需要先开规格解决三件事（都不是本轮范围）——(a) 学校侧提供「App 换取一次性 `state`/授权 URL」的服务端接口；(b) 一条官方通道把该 URL 送进微信内置浏览器（`OpenWebview.Req` 实测 + 官方约束确认，或学校侧改用开放标签/回跳 scheme）；(c) 学校 CAS 授权成功后把登录态（或 `go-admin-tgt`）交回农屿 App 的契约与后端实现。第 (c) 项会改动后端，与「仓内 `wechatCode` 被丢弃」直接相关。

---

## 8 待实测清单（不要把推测写成结论）

1. **【待实测·最关键】普通系统浏览器 / Android `WebView` / Chrome Custom Tab 直接打开同形 `connect/oauth2/authorize` 链接的确切表现**：是否提示「请在微信客户端打开链接」、是否能拿到 `code`。官方站内无明文（§2.3）。**必须用自建服务号的同形链接（自己的 `state`、自有 `redirect_uri`），不得访问本文带一次性 `state` 的真实 URL**。
2. **【待实测】`OpenWebview.Req` 真机行为**：是否允许任意 `url`、是否需额外权限/白名单、是否在微信内置浏览器上下文（能否识别 `#wechat_redirect` 并「带上身份信息」）、`Resp.result` 语义、6.8.0 之后 `wechat-sdk-android` 正式制品是否仍有该类（本轮 Javadoc 取的是 `wechat-sdk-android-without-mta 6.8.0`，制品不同）、`getWXAppSupportAPI()` 对应的最低微信版本。
3. **【待实测】学校 CAS 回调后的凭证形状**：`GET/POST /api/v1/cas/loginByQr?appid=ywtb&cascallback=wxlogined` 收到 `code`+`state` 后，是否下发 `go-admin-tgt`（cookie / 302 / JSON）、`cascallback=wxlogined` 是否就是回跳钩子、是否有 App scheme / 开放标签回跳。需抓包学校登录页 + 校园网 + 学校账号。
4. **【待实测】「微信内网页跳转 App」绑定是否可能**：学校 `auth.sicau.edu.cn` 服务号的 JS 接口安全域名是否绑定农屿开放平台审核通过的移动应用、主体是否一致（§3.6）。需开放平台控制台 + 学校侧配合。
5. **【待实测】是否存在「App 直接换取一次性 `state`」的学校侧接口**：仓内 grep 零命中（§3.6、§5），需抓包学校登录页二维码生成请求 / 查学校平台官方接口文档。
6. **【待实测】`state=loginByQr:<串>` 的生成与失效规则**：一次性、有效期、是否与二维码/会话绑定；官方网页授权文档只写 `state`「原样带回、防越权」，未写 state 本身的一次性（§5）。
7. **【待实测】微信客户端前提**：是否需已登录微信账号、最低微信版本、是否需关注学校服务号、`snsapi_userinfo` 是否逐字要求「客户端内已登录会话」（§2.4）。
8. **【待实测】学校侧 appid 资质**：`wx7c220ea873c845e0` 是否为「已认证服务号」、`auth.sicau.edu.cn` 是否在其「网页授权域名」配置内、scope 权限是否含 `snsapi_userinfo`（官方错误码 10003/10005/10016 对应，§2.1/§5）。URL 能被生成出来**强烈暗示**配置有效，但这是推断，不写成【确证】。
9. **【待实测·环境】** 校园网 / 真机 / 学校账号（沿用 [campus-sso-wechat-direct-auth.md §5.3 第 7 项](campus-sso-wechat-direct-auth.md)：「真机验收仍需校园网环境」）。本机不具备。

---

## 9 一手来源清单（全部官方文档 / 官方制品 / Android 官方 / 腾讯官方）

| 来源 | URL | 本文用它确证什么 |
| --- | --- | --- |
| 微信网页授权（服务号-指南，最后编辑 2026-07-04；HTML + 官方 Markdown 双向核对） | [HTML](https://developers.weixin.qq.com/doc/service/guide/h5/auth.html) · [.md](https://developers.weixin.qq.com/doc/service/guide/h5/auth.md) | 「用户在微信客户端中访问第三方网页」前提、构造链接格式与参数表、`#wechat_redirect` **必须**、「正则强匹配校验 / 参数顺序不对授权页无法正常访问」、网页授权域名配置、`snsapi_userinfo` 弹窗/静默/固化规则、`code` 一次性 5 分钟、错误码 10003/10005/10016、适用账号「仅已认证服务号」 |
| 微信网页开发-起步指南（服务号） | [.md](https://developers.weixin.qq.com/doc/service/guide/h5/start.md) · [HTML](https://developers.weixin.qq.com/doc/service/guide/h5/start.html) | 「微信网页 = 普通网页运行在微信内置浏览器中」、开发者工具「预览」**把网址转二维码方便微信客户端扫码打开**（§1/§7 二维码即官方入口） |
| 微信网页开发（服务号目录页） | [HTML](https://developers.weixin.qq.com/doc/service/guide/h5/) | 「微信内置浏览器可以打开 URL 网页……完成一些**外部浏览器无法完成**的动作」；网页授权「仅服务号可用」 |
| 公众号第三方平台网页授权 | [HTML](https://developers.weixin.qq.com/doc/oplatform/Third-party_Platforms/2.0/Before_Develop/Official_Accounts/official_account_website_authorization.html) · [.md](https://developers.weixin.qq.com/doc/oplatform/Third-party_Platforms/2.0/Before_Develop/Official_Accounts/official_account_website_authorization.md) | 「**使用微信客户端打开以下链接**（严格按照以下格式，包括顺序和大小写）」+ 同形 `connect/oauth2/authorize` 链接；`state`「任意参数值，最多 128 字节」 |
| 微信网页授权能力调整公告（微信团队，2022-05-09，官方公告） | [链接](https://developers.weixin.qq.com/community/minihome/doc/000c2c34068880629ced91a2f56001) | 「优化用户在**微信内**登录网站应用的体验」、不规范使用 `snsapi_userinfo` 进「网页快照页」、快照页屏蔽授权弹窗/虚拟账号/无法用 JS-SDK |
| 获取 URL Scheme（小程序） | [HTML](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/url-scheme.html) · [.md](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/url-scheme.md) | `weixin://dl/business/` 明文/加密形态、用途=**打开小程序**、**Android 不支持直接识别需 H5 中转**、系统弹框、非个人主体、只能已发布小程序 |
| Android 接入指南（开放平台移动应用） | [HTML](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html) · [.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.md) | `sendReq(BaseReq)` 语义、SDK 依赖与 Maven 坐标、`WXEntryActivity`、`<queries><package android:name="com.tencent.mm" /></queries>`、包名改 `opensdk`、Android 13 intent-filter 陷阱 |
| 移动应用微信登录开发指南 | [HTML](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html) · [.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.md) | §4 边界：强制 SDK、强制装微信、`scope` 只能 `snsapi_userinfo`、`SendAuth.Resp` 给 `code`、AppSecret 放后端、`state` 防 csrf 语义 |
| 移动应用扫码登录（`Login_via_Scan`） | [HTML](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.html) · [.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Login_via_Scan.md) | §4 边界：`IDiffDevOAuth.auth()`、`onAuthGotQrcode`、`onAuthFinish(authCode)`、`sdk_ticket`←`access_token`←AppSecret 的后端签名链路 |
| 微信内网页跳转 App | [HTML](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_H5_Launch_APP.html) · [.md](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_H5_Launch_APP.md) | §3.6 反向通道：开放标签、`wx-open-launch-app`、域名↔移动应用绑定、同主体且均已认证、只能绑定审核通过的移动应用 |
| 网站应用微信登录开发指南（`qrconnect`） | [.md](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.md) | §1 排除项：`qrconnect` + `snsapi_login` 是网站应用，与本 payload 不是一类 |
| 微信 Open SDK 制品 Javadoc（**官方 AAR 类清单 + javadoc.io 第三方托管镜像**） | [IWXAPI](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-with-mta/5.4.0/com/tencent/mm/opensdk/openapi/IWXAPI.html) · [OpenWebview](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/modelbiz/OpenWebview.html) · [OpenWebview.Req](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/modelbiz/OpenWebview.Req.html) · [OpenWebview.Resp](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/modelbiz/OpenWebview.Resp.html) · [WXOpenBusinessWebview.Req](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/com/tencent/mm/opensdk/modelbiz/WXOpenBusinessWebview.Req.html) · [索引](https://javadoc.io/doc/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.8.0/index-all.html) | `openWXApp()`「launch wechat app」无参数；`OpenWebview`「第三方APP跳转微信webview」+ `url` 字段；`Resp` 只有 `result` 无 `code`；`WXOpenBusinessWebview` 业务类型限定（1 香港钱包 / 2 发票抬头） |
| Android 官方 · Intents and intent filters | [链接](https://developer.android.com/guide/components/intents-filters) | 隐式 Intent 由系统按目标 app 的 intent-filter 解析、多个匹配弹选择器、`ACTION_VIEW` / `CATEGORY_BROWSABLE` 定义 |
| Android 官方 · Declare package visibility needs（最后更新 2026-10-28） | [链接](https://developer.android.com/training/package-visibility/declaring) | targetSdk ≥ 30 需 `<queries>` 声明包名/intent 签名才能与其它 app 交互（是「可见性」前置，不是「打开网页」能力） |
| 腾讯官方（**企业微信体系，作旁证**）· 构造网页授权链接 | [91861](https://developer.work.weixin.qq.com/document/path/91861) · [91120](https://developer.work.weixin.qq.com/document/path/91120) | `#wechat_redirect`「**终端使用此参数判断是否需要带上身份信息**」/「固定内容」/「是」 |
| 腾讯官方（**企业微信体系，作旁证**）· 常见问题 FAQ | [90457](https://developer.work.weixin.qq.com/document/path/90457) | 「**企业微信不支持打开需要带微信身份的链接**」「访问链接为 oauth2 链接，appid 填写为公众号 appid」「公众平台文章……只能在微信客户端才能访问」 |

---

## 10 二手线索（仅作线索，未采信、未作为任何结论依据）

- 检索命中大量 CSDN / 百度 / 博客文章讲「请在微信客户端打开链接」的成因与「改 UA 绕过」（例如 [一招解决"请在微信客户端中打开链接"](https://blog.csdn.net/yu502586512/article/details/134042151)、[为什么总提示"请在微信客户端打开链接"](https://kuku.baidu.com/landing/tscp_doc/c5b33eff7fd563a1d433e86b699e1fbc)）。它们**只作为「该提示在社区被广泛记录」的线索**；官方站内未检到专述页，故 §2.3 标【官方未明说】，不把这些文章当官方明文，更不把「改 UA 绕过」当方案。
- [微信开放社区](https://developers.weixin.qq.com/community/develop/doc/00048a7fa6c6f8614a7338ba56b400) 有一条「鸿蒙SDK OpenWebview.Req 咨询」帖标题，仅作「该 API 在社区被问过」的线索；**未**用它推断 `OpenWebview` 的官方约束。
- 第三方文档镜像 / 集成教程（CSDN、博客园、GitHub 反编译源码、阿里云社区文章等）在检索中出现，全部**未采信**；§9 的每条事实都回到官方页面或官方制品 Javadoc。
- 以 `site:developers.weixin.qq.com` 针对「免 SDK 打开网页授权 URL」「App 直跳微信打开 oauth2 链接」「OpenWebview 接入指南」的检索，命中均落在 §9 的页面或 Javadoc 索引内，**未检到**官方为「第三方 App 直跳微信打开 `connect/oauth2/authorize`」写的接入页。按票 #81 要求，**「未检到公开文档」只记为【官方未明说】，不写成「官方禁止」**。

---

## 11 本轮明确**没有**做的事（避免把边界写成结论）

- **未访问**那个真实授权 URL（带一次性 `state`，访问会触发/消耗真实授权），也未访问 `auth.sicau.edu.cn` / `open.weixin.qq.com` / 学校登录页、未抓包。
- **未**把「官方站内没有 `OpenWebview` 接入指南 / 没有普通浏览器表现的明文」写成「官方禁止」或「官方明确不支持」——按票 #81 要求，这些一律【官方未明说】。
- **未**把 [wechat-scan-direct-jump.md](wechat-scan-direct-jump.md) 的「开放平台无免 SDK 直跳」搬来否定学校 CAS 网页授权（§4 边界声明）；也**未**用开放平台移动应用登录 / `Login_via_Scan` 的二维码形态去替换本 payload 的服务号网页授权二维码（§1、§4）。
- **未**推翻 [campus-sso-wechat-direct-auth.md](campus-sso-wechat-direct-auth.md) 任何结论：只把「学校 CAS 微信登录形态」从【仓内未见】升级为【确证】（因 payload 已解码），其「仓内未见 ≠ 官方不存在」「`wechatCode` 被丢弃 / 只认 `go-admin-tgt`」继续有效。
- **未**改动 [backend-plus-login-ui-facts.md](backend-plus-login-ui-facts.md) 或其它任何既有文件；未改代码 / `AndroidManifest.xml` / 构建文件；未引入任何依赖；**未 git commit**（遵循票 #81「只读核查」约束，优先于全局 AGENTS.md 的默认提交约定）。
- **只新建这一篇笔记**：`docs/research/wechat-webauth-direct-open.md`。
