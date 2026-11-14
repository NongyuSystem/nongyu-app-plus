# 川农统一身份认证微信扫码登录：实测闭环记录（取代 #82 的待验证项）

> **日期**：2026-11-14
> **起因**：地图 #76 的 research 子票 #82 与实施票 #97（S8 微信扫码完成感知与回调闭环）。
> #82 当时明确声明「未实测，只产出方案与间接证据」。本文是**实测结论**，把 #97 列出的四个未决问题逐条答完。
>
> **脱敏口径**（照 `BE/docs/tech/实测-门户appid免密换票-已证实.md`）：只回报状态码、cookie 名、属性、Location 形状、字段名；
> **任何凭据值一律 REDACTED**。本文不记录真实 `state`、tgt、st、token 的值。
>
> **不访问**带一次性 `state` 的真实授权 URL 做测试（每次取码只取一次，不重放）。

## 0. 一句话结论

**App 端可以独立完成微信扫码登录的前半段**（取码 + 轮询），后半段（扫码授权完成）**必须由真人在微信里扫一次**。
完成感知靠**轮询**，不是 WebSocket；扫码成功后**会**拿到 `go-admin-tgt`，且它同时出现在**解密后的 body**和 **`Set-Cookie`** 两处。

## 1. 四个未决问题的实测答案

### ① 二维码 / 一次性 `state` 由哪个接口生成、URL 是否每次变化

**接口**：`POST https://auth.sicau.edu.cn/api/v1/cas/getLoginQr`，body `{"appid":"ywtb"}`

- **必须是 POST**：同路径 GET 返回 **404**（实测）。
- `appid` 必须是 **`ywtb`**（门户），不是教务的 `b54a2f27-...`。用错 appid 得不到微信授权 URL。
- 响应信封 `{requestId, code, msg, data}`，`code=200, msg="获取成功"`。
- `data` 需解密（`MD5(requestId + ":sicau-cas")` 作 AES-128 密钥，前 16 字节是 IV），得到：

```json
{
  "url": "https://open.weixin.qq.com/connect/oauth2/authorize?...&state=REDACTED#wechat_redirect",
  "state": "loginByQr:REDACTED"
}
```

- **URL 每次变化**：每次调用都产出新的 `state`（`loginByQr:` 前缀 + 43 字符 base64url）。旧码对应的 `state` 在扫码完成后即失效（实测：同一 `state` 连续轮询，命中一次后继续轮询会退回「未扫码」空结果）。

### ② App 如何拿到并展示这张码

App 把 `url` 字段**本地编码成二维码**绘制，不需要任何服务端图片接口。

仓内已有自研零依赖编码器 `app/src/main/java/com/nongyu/appplus/login/model/QrEncoder.kt`（Byte 模式 + EC 级 M + 版本 1..20 + 固定 mask 0），
已用 OpenCV 对 v1/v8/v10/v11/v12/v13/v14 共 6 个用例做**真实解码验证**全部还原原文。

### ③ 扫码授权后 App 如何感知完成

**轮询**，不是 WebSocket，也没有 `wxlogined` 回调打给 App。

- **接口**：`POST https://auth.sicau.edu.cn/api/v1/cas/getCasLoginQrRes`，body `{"state":"<①里的 state>"}`
- **必须是 POST**（同路径 GET 返回 404）。
- **参数是 `state`，不是 `requestId`**（传错会得到「微信回调链接有误」）。
- 轮询间隔：学校页面本身每 1~2 秒一次；App 取 1.5 秒。
- 响应基线（实测，`bodyLen` 是解密前信封字节数）：

| 状态 | HTTP | code | msg | bodyLen | 解密后 |
| --- | --- | --- | --- | --- | --- |
| 未扫码 | 200 | 200 | `ok` | **208** | `{callbackUrl:"",tgt:"",st:"",user:null,users:null,uuid:""}` |
| 已扫码授权 | 200 | 200 | `ok` | **1896** | `tgt` 有值、`user.name` 有值（如「姚一飞」） |

**判据**：不要只看 HTTP 状态码（两次都是 200），要看 **bodyLen 或解密后的 `tgt` 字段是否非空**。

### ④ `loginByQr` 回调后是否直接下发 `go-admin-tgt`

**是（判 A）**。实测扫码完成后：

- 解密后的 body 里 `tgt` 字段有值；
- 同时 `Set-Cookie` 里也有 `go-admin-tgt`（两者值一致）。

因此**不需要**新开规格补「微信凭证 → tgt」，`wechatCode` 那条路可以彻底放弃。

> ⚠️ 但要区分「拿到 tgt」和「拿到可用凭据」：tgt 必须经过形态校验（长度 8..512、含 `:`、纯可见 ASCII、不含 `<>"'\\` 与空白）。
> 权威依据 `BE/docs/tech/权威-川农统一身份认证唯一方案.md` §1.2：客户端若把响应 body 当 cookie 值交上去，
> 后端会存进 Redis，之后每次换票 500「未登录」，表现为「能登录、有姓名、画像其他字段全空」，且日志零线索。

## 2. 完整链路（App 视角）

```
① POST /api/v1/cas/getLoginQr {"appid":"ywtb"}
   ← {requestId, code:200, msg:"获取成功", data:<密文>}
   ← 解密得 {url, state:"loginByQr:..."}

② App 用 QrEncoder 把 url 编码成二维码，Canvas 绘制

③ 用户用手机微信扫 → 微信内完成 OAuth → 回调学校 CAS

④ App 每 1.5 秒轮询 POST /api/v1/cas/getCasLoginQrRes {"state":"..."}
   ← 未扫码：解密全空（bodyLen≈208）
   ← 已扫码：tgt 非空（bodyLen≈1896）

⑤ 拿 tgt → POST /identity/login {method:"school", goAdminTgt, deviceId}
   ← 200 {token, user{...}}
   ← 落凭证 → 切主界面
```

## 3. 实现要点与踩坑

### 3.1 必须从 `Set-Cookie` 取 tgt，且必须校验形态

`go-admin-tgt` 是 **HttpOnly** cookie，`document.cookie` 读不到，**响应 body 的明文里也没有它**（body 是 CAS 信封，tgt 在解密后的 data 里）。

仓内落点：
- `TgtCaptureInterceptor`：从响应的 `Set-Cookie` 头捕获；
- `TgtValidator.looksLikeTgt`：形态校验，判据与后端 `looksLikeTGT` 一致。

### 3.2 取新码时必须清掉上一轮残留的 tgt

否则新码的**第一次**轮询就会因拦截器里还有旧 tgt 被误判成「已扫码」→ 提交 → 失败 → 刷新 → 再误判，
表现为**「二维码刚出来 1 秒就失效」**。落点：`CasLoginUseCase.fetchLoginQr()` 开头 `tgtInterceptor.reset()`。

### 3.3 组合取消不是失败

扫码命中后提交 `method=school` 会触发 `LoginSessionHolder.onSignedIn()` → 根节点切主界面 →
`LoginCenterScreen` 离开组合 → `LaunchedEffect` 协程被取消，抛 `LeftCompositionCancellationException`。

**这是正常行为**。若把它当异常写回 `FAILED` 状态，用户会被拉回登录页，表现为「扫完就失效」。

修法：提交放 `withContext(NonCancellable)`，并单独 `catch (CancellationException)` 只记日志、不报错。

### 3.4 不能跳转微信直达扫一扫

真机实测（微信已登录）：
- `https://open.weixin.qq.com/...` 的 VIEW intent 只有 Chrome 认领，微信**不注册 https scheme**；
- `weixin://dl/scan` 会落到 `com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI`（内置浏览器），**不是扫一扫**；
- `weixin://dl/business/?url=<任意参数>` 只停在 `WXCustomSchemeEntryActivity`。

所以两个按钮只能**拉起微信主界面**，把「打开那个 URL」交给用户自己完成。
另外 targetSdk ≥ 30 必须在 AndroidManifest 声明 `<queries>`，否则 `getLaunchIntentForPackage` **静默返回 null**。

## 4. 复现方法（不含凭据）

```bash
# 仅取码，不解码授权 URL、不扫码
curl -s -X POST https://auth.sicau.edu.cn/api/v1/cas/getLoginQr \
  -H 'Content-Type: application/json' \
  -d '{"appid":"ywtb"}'
# 期望：code=200, msg="获取成功", data 非空
# 解密后只观察字段名（url/state），值一律不记录

# 轮询（需上一步的 state；只观察 bodyLen 与 tgt 是否为空）
curl -s -X POST https://auth.sicau.edu.cn/api/v1/cas/getCasLoginQrRes \
  -H 'Content-Type: application/json' \
  -d '{"state":"<REDACTED>"}'
# 未扫码：解密后 tgt 为空
# 扫码后：解密后 tgt 非空
```

## 5. 与 #82 的关系

| #82 当时的状态 | 本文的结论 |
| --- | --- |
| 未实测，只给方案与间接证据 | **已实测**，四个问题全部答完 |
| 「需确认是否会下发 tgt」列为待验证 | **判 A**：会下发，body 与 Set-Cookie 都有 |
| 建议「若判 B 则新开规格补微信凭证→tgt」 | **不需要**，判 A |

## 6. 边界（不夸大）

- **扫码这一步无法自动化**：必须有真人在手机微信里扫一次并确认授权。任何「脚本自动完成扫码登录」的做法都不在本文支持范围内。
- **本文不覆盖**：微信开放平台 SDK、公众号网页授权（snsapi_userinfo 的 App 侧直连）、「川农微教务」公众号教务首页扫码入口——那是另一条与本文无关的通道。
- 学校端行为可能变化。若 `getLoginQr` 返回的 `code` 不再是 200，或 `getCasLoginQrRes` 的 bodyLen 基线变化（208 / 1896），先怀疑学校侧改动，不要先改客户端。
