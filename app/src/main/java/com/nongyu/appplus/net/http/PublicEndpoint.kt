package com.nongyu.appplus.net.http

/**
 * 标记「公开端点」—— 无 token 时也照发，且不挂 `Authorization` 头。
 *
 * ## 为什么用注解而不是路径前缀白名单
 * 路径前缀白名单会在**后端改路径时静默失效**（改完就变成「本来公开的端点突然要票了」，
 * 而且是运行期才发现）。注解挂在方法上，路径改了注解还在。
 *
 * ## ⚠️ 精度提醒：公开与否是「运行时事实」，不是契约常量
 * `#46` 实测：`backend-plus` 的「公开路径表」**只在可吊销装配（`DATABASE_URL` 非空）下存在**。
 * 降级装配用的是 `AuthMiddleware`，它**只放行 `/healthz`**，连 `/identity/login/admin`
 * 无票也是 401。
 *
 * 所以：标了本注解**不代表后端一定不要求票**，只代表「客户端不会主动拦它」。
 * 联调时 `/identity/mailcode/send` 在降级后端上仍然**要带票才能通**。
 *
 * ## 一期标了本注解的端点
 * - `GET /healthz`
 * - `POST /identity/login/admin`
 * - `POST /identity/mailcode/send`
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PublicEndpoint
