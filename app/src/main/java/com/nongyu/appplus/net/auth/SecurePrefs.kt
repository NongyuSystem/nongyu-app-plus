package com.nongyu.appplus.net.auth

/**
 * 安全存储的**单一真源**：DataStore 文件名、Keystore 别名、令牌键。
 *
 * ## 存储位置（从 EncryptedSharedPreferences 迁走后的关键变化）
 *
 * | | 旧实现 | 现实现 |
 * | --- | --- | --- |
 * | 落盘 | `shared_prefs/nongyu_secure_session.xml` | `files/datastore/nongyu_secure_session.preferences_pb` |
 * | 备份规则 domain | `sharedpref` | **`file`** |
 * | 加密 | 库内 `EncryptedSharedPreferences` | 自管 Keystore AES/GCM |
 *
 * ⚠️ **备份规则的 `domain` 必须一起改**：DataStore 落在 `files/` 下，
 * 仍写 `domain="sharedpref"` 的话排除规则**形同不存在**，密文会被备份带走。
 *
 * ## 为什么 XML 里写字面量而不是 `@string/`
 * 备份规则 XML 的 `path` 属性**是否解析字符串资源引用未取证**
 * （`XmlResourceParser` 对 resource ref 的取值行为在不同 API 上不一致），
 * 且当时 `strings.xml` 里根本没有这个字符串。
 * 所以 XML 侧写字面量，并有一条纯 JVM 测试读这两个 XML 断言与 [DATASTORE_NAME] 一致
 * （见 `SecurePrefsNameConsistencyTest`）。
 *
 * **改这里时必须同步改两个 XML 文件。**
 *
 * ## 旧数据不迁移：为什么这是安全的（重要，别当成遗漏）
 *
 * 从 `EncryptedSharedPreferences` 迁到 DataStore 后，新代码**不读取**旧的
 * `shared_prefs/nongyu_secure_session.xml`。看起来像「老用户升级后被静默登出」，
 * 但实际不是问题，原因有二：
 *
 * 1. **从来没有生产代码往这里写过 token**：令牌来源是 `defaultTokenProvider()`
 *    （debug 取 `BuildConfig.DEV_TOKEN`，release 恒为 `null`），
 *    即 [SessionStore] 目前只在「会话失效时 clear()」这一条路径被用到，
 *    `readToken()` / `writeToken()` 尚无生产调用方。没有存量数据，就谈不上丢失。
 * 2. 即便要迁移，也**不能**在移除 security-crypto 依赖后读旧数据：
 *    旧文件是用 `MasterKey`（旧库自己的 Keystore 别名）加密的，
 *    不解回该依赖就读不出来。
 *
 * **将来接真实登录时必须做的决定**：一旦 `writeToken()` 有了生产调用方（真正落地的登录），
 * 需要在此处明确二选一——要么确认「当前没有存量用户」直接不管，
 * 要么加一次性迁移（临时把 security-crypto 作为 `compileOnly`/迁移期依赖读旧文件，
 * 把明文搬进 DataStore 后删除旧文件与依赖）。
 */
object SecurePrefs {
    /**
     * DataStore 的**名字**（不是完整文件名）。
     *
     * 实际落盘为 `files/datastore/nongyu_secure_session.preferences_pb`。
     * 备份规则的 `path` 要写 `datastore/<这个名字>.preferences_pb`。
     */
    const val DATASTORE_NAME: String = "nongyu_secure_session"

    /** 令牌在 DataStore 里的键。 */
    const val KEY_TOKEN: String = "session_token"

    /** Keystore 里保存 AES 密钥的别名（自管加密用）。 */
    const val KEY_ALIAS: String = "nongyu_session_aead_key"

    /**
     * 备份规则里要排除的相对路径（相对 `files/` 域）。
     *
     * DataStore 的文件名固定是 `<name>.preferences_pb`，
     * 所以这里由 [DATASTORE_NAME] 派生，避免两处写死不同步。
     */
    val BACKUP_EXCLUDE_PATH: String get() = "datastore/$DATASTORE_NAME.preferences_pb"
}
