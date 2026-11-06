package me.huidoudour.file.manager.signing

import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/**
 * 一条已保存的签名密钥配置。
 * 密钥库文件本体存放在应用私有目录 (filesDir/keystores/) 内，这里只记录文件名。
 */
data class KeystoreConfig(
    val id: String,
    val name: String,
    val fileName: String,
    val type: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val createdAt: Long
) {

    /** 内置公开测试密钥不允许删除（删除后也会自动恢复） */
    val isBuiltin: Boolean get() = id == BuiltinTestKey.CONFIG_ID

    @Throws(JSONException::class)
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("fileName", fileName)
        .put("type", type)
        .put("storePassword", storePassword)
        .put("keyAlias", keyAlias)
        .put("keyPassword", keyPassword)
        .put("createdAt", createdAt)

    companion object {

        fun create(
            name: String,
            fileName: String,
            type: String,
            storePassword: String,
            keyAlias: String,
            keyPassword: String
        ): KeystoreConfig = KeystoreConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            fileName = fileName,
            type = type,
            storePassword = storePassword,
            keyAlias = keyAlias,
            keyPassword = keyPassword,
            createdAt = System.currentTimeMillis()
        )

        @Throws(JSONException::class)
        fun fromJson(json: JSONObject): KeystoreConfig = KeystoreConfig(
            id = json.getString("id"),
            name = json.getString("name"),
            fileName = json.getString("fileName"),
            type = json.getString("type"),
            storePassword = json.getString("storePassword"),
            keyAlias = json.getString("keyAlias"),
            keyPassword = json.getString("keyPassword"),
            createdAt = json.optLong("createdAt")
        )
    }
}
