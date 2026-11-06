package me.huidoudour.file.manager.signing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.huidoudour.file.manager.R
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * 密钥配置持久化仓库：
 * - 配置列表保存为 filesDir/keystore_configs.json
 * - 密钥库文件本体保存在 filesDir/keystores/ 下
 * - 内置公开 testkey 在列表缺失或文件丢失时自动恢复
 */
class KeystoreRepository(context: Context) {

    private val appContext = context.applicationContext
    private val configFile = File(appContext.filesDir, CONFIG_FILE)
    val keystoreDir = File(appContext.filesDir, KEYSTORE_DIR)
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** 上次签名使用的密钥 id，用于下次打开签名对话框时默认选中 */
    var lastUsedId: String?
        get() = preferences.getString(KEY_LAST_USED, null)
        set(value) {
            preferences.edit().putString(KEY_LAST_USED, value).apply()
        }

    fun getKeystoreFile(config: KeystoreConfig): File = File(keystoreDir, config.fileName)

    @Synchronized
    fun loadAll(): List<KeystoreConfig> {
        val list = ArrayList<KeystoreConfig>()
        if (configFile.exists()) {
            try {
                val array = JSONArray(configFile.readText(Charsets.UTF_8))
                for (i in 0 until array.length()) {
                    list.add(KeystoreConfig.fromJson(array.getJSONObject(i)))
                }
            } catch (_: Exception) {
                // 配置损坏时忽略，至少保证内置密钥可用
            }
        }
        if (ensureBuiltinTestKey(list)) saveAll(list)
        return list
    }

    fun findById(id: String): KeystoreConfig? = loadAll().firstOrNull { it.id == id }

    @Synchronized
    fun add(config: KeystoreConfig) {
        val list = ArrayList(loadAll())
        list.add(config)
        saveAll(list)
    }

    /** 删除配置及其私有目录内的密钥库文件（多个配置共用同一文件时保留文件） */
    @Synchronized
    fun delete(config: KeystoreConfig) {
        val list = loadAll()
        val kept = ArrayList<KeystoreConfig>(list.size)
        var fileStillUsed = false
        for (item in list) {
            if (item.id == config.id) continue
            kept.add(item)
            if (item.fileName == config.fileName) fileStillUsed = true
        }
        saveAll(kept)
        if (!fileStillUsed && !config.isBuiltin) {
            getKeystoreFile(config).delete()
        }
        if (lastUsedId == config.id) lastUsedId = null
    }

    /** 将外部密钥库流导入私有目录，返回私有目录内的文件名 */
    @Throws(IOException::class)
    fun importKeystoreFile(source: InputStream, displayName: String?): String {
        val safeName = System.currentTimeMillis().toString() + "_" +
            (displayName ?: "keystore").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dest = File(keystoreDir, safeName)
        keystoreDir.mkdirs()
        source.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return safeName
    }

    /** 加载指定配置的签名身份；内置密钥文件缺失时会先用 assets 重新合成 */
    suspend fun resolveIdentity(config: KeystoreConfig): SignerIdentity = withContext(Dispatchers.IO) {
        if (config.isBuiltin) {
            BuiltinTestKey.ensureFile(appContext, getKeystoreFile(config))
        }
        val file = getKeystoreFile(config)
        if (!file.exists()) throw IOException(appContext.getString(R.string.keystore_file_missing))
        KeystoreHelper.load(file, config.storePassword.toCharArray())
            .getSigner(config.keyAlias, config.keyPassword.toCharArray())
    }

    /**
     * 保障内置公开 testkey 始终可用：配置或密钥库文件缺失时自动恢复。
     * 返回列表是否发生变化（需要写回）。
     */
    private fun ensureBuiltinTestKey(list: MutableList<KeystoreConfig>): Boolean {
        if (list.any { it.id == BuiltinTestKey.CONFIG_ID && getKeystoreFile(it).exists() }) {
            return false
        }
        list.removeAll { it.id == BuiltinTestKey.CONFIG_ID }
        try {
            BuiltinTestKey.ensureFile(appContext, File(keystoreDir, BuiltinTestKey.FILE_NAME))
        } catch (_: Exception) {
            // assets 缺失等异常：跳过内置密钥，不影响其他配置
            return false
        }
        val builtin = KeystoreConfig.create(
            appContext.getString(R.string.keystore_builtin_name),
            BuiltinTestKey.FILE_NAME,
            KeystoreHelper.TYPE_PKCS12,
            BuiltinTestKey.PASSWORD,
            BuiltinTestKey.ALIAS,
            BuiltinTestKey.PASSWORD
        ).copy(id = BuiltinTestKey.CONFIG_ID)
        list.add(0, builtin)
        return true
    }

    private fun saveAll(list: List<KeystoreConfig>) {
        try {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            configFile.writeText(array.toString(2), Charsets.UTF_8)
        } catch (_: Exception) {
            // 写失败不影响本次内存中的配置使用
        }
    }

    companion object {
        private const val CONFIG_FILE = "keystore_configs.json"
        private const val KEYSTORE_DIR = "keystores"
        private const val PREFERENCES = "signing_prefs"
        private const val KEY_LAST_USED = "last_keystore_id"
    }
}
