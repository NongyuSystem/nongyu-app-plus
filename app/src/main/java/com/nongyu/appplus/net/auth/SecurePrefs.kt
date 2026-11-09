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
