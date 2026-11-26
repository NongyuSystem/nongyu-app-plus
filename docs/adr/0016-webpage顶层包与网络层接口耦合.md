# ADR-0016 通用 WebView 容器独立成 webpage 顶层包，与网络层用接口耦合

- 状态：已采纳（2026-11-26，票 #119 / 规格 #111）
- 相关：ADR-0008（顶层包按能力域切分）、ADR-0010（webvpn 顶层包先例）、`docs/specs/活动层-H5-节日页.md` D7

## 背景

规格 #111 v5 要求个人主页新增「学校相关页面」画像入口，点开用 App 内嵌浏览器
浏览校方站点。需要一套通用 WebView 容器。两条路：

1. 放进 `net` 层，复用其 HTTP 栈与 Cookie；
2. 独立成顶层能力域 `webpage`，与 net 用接口耦合。

`net/core` 的硬约束是**纯逻辑层禁止 import `android.*`**
（`docs/specs/网络层-N2-N5-实施记录.md` §3.1），而 WebView 是纯 Android 控件，
放进 net 会直接破坏这条约束与 800+ 纯 JVM 单测的可运行性。

## 决策

**新增 `webpage` 顶层包，按能力域 `model / logic / data / ui` 分层；不放入 net。**

- `webpage/logic`：`UrlNormalizer`、`WebPageAllowlist` —— 纯 JVM，零 Android/Compose import，
  单测覆盖协议/host/子域/非法 URL/尾斜杠；
- `webpage/model`：`WebPageConfig`（URL/白名单/标题/JS 开关）与 `WebPageCookieBridge`（Cookie 桥接口）；
- `webpage/ui`：`WebPageScreen`，用 `AndroidView(WebView)` 实现，只依赖上述 model；
- 与 net 的耦合收窄成**配置/Cookie 桥接口**：net 侧实现 `WebPageCookieBridge` 由装配点注入，
  `webpage` 包不 import net 内部实现（依赖方向单向）。

白名单判定方案两处共用：H5 节日页活动（原「离开白名单一律拦截」）与学校相关页面
（允许校方站点 `*.sicau.edu.cn`）——差别只在注入的 host 集合，判定机构统一。
H5 规格 D7 已同步修订并写明为何改（威胁模型不同：用户主动点进 vs 被诱导跳转）。
**WebVPN 登录沙箱不迁入**，登录链路零改动。

## 后果

- 符合 ADR-0008「顶层包是单数具名能力域」；`webpage/logic` 保持纯 JVM 可测。
- 学校站点强制 https（与 release 禁明文一致）；白名单外的导航一律拦截，不留 WebView。
- 返回键优先 WebView 回退历史、到首屏退出；`onPause/onResume` 随 Composable 生命周期；
  加载失败给明确态而非白屏。
- 将来若需要带 App 会话 Cookie 访问校方站点，由装配点注入 `WebPageCookieBridge` 实现即可，
  不改 `webpage` 包；本票暂注入 null（校方站点公开可读）。

## 验证

- `WebPageLogicTest` 锁定白名单/归一化行为；
- compileDebugKotlin + testDebugUnitTest + assembleRelease 全绿；
- 真机四步验收（打开/返回/加载失败/白名单拦截）留待人工。
