package me.huidoudour.file.manager.signing

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * 统一的密钥库加载入口，屏蔽 PKCS12 / BKS / JKS 三种格式差异。
 *
 * - PKCS12：Android 平台自带实现，直接使用；
 * - JKS：平台不支持，交给 [JksKeyStoreReader] 解析；
 * - BKS：尝试平台内置 BouncyCastle 提供的实现，失败时提示转换为 PKCS12。
 */
object KeystoreHelper {

    const val TYPE_PKCS12 = "PKCS12"
    const val TYPE_BKS = "BKS"
    const val TYPE_JKS = "JKS"

    private const val MAGIC_JKS = 0xFEEDFEED.toInt()
    private const val MAGIC_JCEKS = 0xCECECECE.toInt()

    /** 已加载的密钥库（keyStore 与 jks 二选一非空） */
    class Loaded(
        val type: String,
        private val keyStore: KeyStore?,
        private val jks: JksKeyStoreReader?
    ) {

        /** 所有私钥条目的别名 */
        fun keyAliases(): List<String> {
            if (jks != null) return jks.keyAliases()
            val store = keyStore ?: return emptyList()
            val aliases = store.aliases()
            val result = ArrayList<String>()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (store.isKeyEntry(alias)) result.add(alias)
            }
            return result
        }

        /** 取出指定别名的私钥与证书链 */
        fun getSigner(alias: String, keyPassword: CharArray): SignerIdentity {
            if (jks != null) {
                val key = jks.getPrivateKey(alias, keyPassword)
                val chain = jks.getCertificateChain(alias)
                if (chain.isNullOrEmpty()) throw IOException("条目缺少证书链: $alias")
                return SignerIdentity(alias, key, chain)
            }
            val store = keyStore ?: throw IOException("密钥库未加载")
            val key = store.getKey(alias, keyPassword) as? PrivateKey
                ?: throw IOException("别名不存在或不是私钥条目: $alias")
            val certificates = store.getCertificateChain(alias)
            if (certificates.isNullOrEmpty()) throw IOException("条目缺少证书链: $alias")
            val chain = certificates.mapNotNull { it as? X509Certificate }
            if (chain.isEmpty()) throw IOException("条目证书不是 X.509 格式: $alias")
            return SignerIdentity(alias, key, chain)
        }
    }

    /**
     * 按文件头特征与逐一尝试的方式加载密钥库。
     * 密码错误、格式不支持时抛出带可读信息的异常。
     */
    @Throws(IOException::class)
    fun load(file: File, storePassword: CharArray): Loaded {
        if (!file.exists()) throw IOException("密钥库文件不存在: ${file.name}")
        return load(file.readBytes(), storePassword)
    }

    @Throws(IOException::class)
    fun load(data: ByteArray, storePassword: CharArray): Loaded {
        if (data.size < 4) throw IOException("文件太小，不是有效的密钥库")

        val magic = ((data[0].toInt() and 0xFF) shl 24) or
            ((data[1].toInt() and 0xFF) shl 16) or
            ((data[2].toInt() and 0xFF) shl 8) or
            (data[3].toInt() and 0xFF)
        if (magic == MAGIC_JKS || magic == MAGIC_JCEKS) {
            return Loaded(TYPE_JKS, null, JksKeyStoreReader.load(data, storePassword))
        }

        // PKCS12：Android 内置支持，优先使用
        val pkcs12Error: Exception?
        try {
            val keyStore = KeyStore.getInstance(TYPE_PKCS12)
            ByteArrayInputStream(data).use { keyStore.load(it, storePassword) }
            return Loaded(TYPE_PKCS12, keyStore, null)
        } catch (e: Exception) {
            pkcs12Error = e
        }

        // BKS：依赖平台内置 BouncyCastle 的 KeyStore 实现
        val bksError: Exception?
        try {
            val keyStore = KeyStore.getInstance(TYPE_BKS)
            ByteArrayInputStream(data).use { keyStore.load(it, storePassword) }
            return Loaded(TYPE_BKS, keyStore, null)
        } catch (e: Exception) {
            bksError = e
        } catch (e: NoSuchMethodError) {
            throw IOException("当前设备不支持 BKS 密钥库，请转换为 PKCS12 格式")
        }

        val reason = pkcs12Error.message ?: bksError.message ?: "未知原因"
        throw IOException("无法加载密钥库（格式不支持或密码错误）: $reason", pkcs12Error)
    }
}
