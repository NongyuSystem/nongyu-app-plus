package com.nongyu.appplus.net.auth

/**
 * 安全存储的文件名 —— **单一真源**。
 *
 * ## 为什么要单独定义
 * 这个文件名同时出现在三处：
 * 1. [MinimalSessionStore] 创建 `EncryptedSharedPreferences` 时；
 * 2. `app/src/main/res/xml/data_extraction_rules.xml`（API 31+）；
 * 3. `app/src/main/res/xml/backup_rules.xml`（API ≤30）。
 *
 * ## 为什么 XML 里写字面量而不是 `@string/`
 * 备份规则 XML 的 `path` 属性**是否解析字符串资源引用未取证**
 * （`XmlResourceParser` 对 resource ref 的取值行为在不同 API 上不一致），
 * 且当时 `strings.xml` 里根本没有这个字符串。
 * 所以 XML 侧写字面量，并有一条纯 JVM 测试读这两个 XML 断言与 [FILE_NAME] 一致
 * （见 `SecurePrefsNameConsistencyTest`）。
 *
 * **改这里时必须同步改两个 XML 文件。**
 */
object SecurePrefs {
    /**
     * `EncryptedSharedPreferences` 的存储文件名。
     *
     * 实际落盘为 `shared_prefs/nongyu_secure_session.xml`，所以备份规则里
     * `domain="sharedpref" path` 要用**不带 `.xml` 的**名字（系统会自动补）。
     */
    const val FILE_NAME: String = "nongyu_secure_session"

    /** 令牌在 prefs 里的键。 */
    const val KEY_TOKEN: String = "session_token"
}
