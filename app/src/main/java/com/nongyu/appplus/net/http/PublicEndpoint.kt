package com.nongyu.appplus.net.http

/**
 * 标记「公开端点」—— 无 token 时也照发，且不挂 `Authorization: Bearer` 头。
 *
 * ## 为什么用注解而不是路径前缀白名单
 * 路径前缀白名单会在**后端改路径时静默失效**（改完就变成「本来公开的端点突然要票了」，
 * 而且是运行期才发现）。注解挂在方法上，路径改了注解还在。
 *
 * ## ⚠️ 「公开」≠「免签名」（现行契约 `docs/后端接口与NY1签名对接契约.md` §1.4）
 * 这是两套完全不同的概念，别混：
 *
 * | 概念 | 由谁决定 | 效果 |
 * | --- | --- | --- |
 * | **公开**（本注解） | 客户端 | 不挂 `Authorization: Bearer`，无 token 也照发 |
 * | **免签名** | 后端 appSign 中间件 | 只有 `/healthz`、`/status` 两条探针路径 |
 *
 * **登录、发验证码这些公开端点仍然要带 NY1 签名头。** 后端签名中间件在用户鉴权之前，
 * 只对 `/healthz` 与 `/status` 豁免；「公开」只是说不需要用户令牌。
 * `AppSignInterceptor` 已按豁免表实现，本注解不参与签名判定。
 *
 * ## 一期标了本注解的端点（现行契约 §2.1）
 * - `GET /healthz`（同时也是免签名路径）
 * - `POST /identity/login`
 * - `POST /identity/login/admin`
 * - `POST /identity/mailcode/send`
 *
 * ## 历史说明
 * 11-09 的 `#46` 历史快照曾说「公开路径表只在可吊销装配下存在、降级装配只放行
 * `/healthz`」。现行后端已补齐路由与签名中间件，该结论**已过时**，
 * 不要照它写代码；唯一权威是上面那份对接契约。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PublicEndpoint
