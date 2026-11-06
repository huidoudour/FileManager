package me.huidoudour.file.manager.signing

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/** 签名所需的私钥与证书链 */
data class SignerIdentity(
    val alias: String,
    val privateKey: PrivateKey,
    val certificates: List<X509Certificate>
)

/**
 * 内置公开测试密钥 (AOSP testkey)。
 *
 * assets 中存放公开的 PKCS#8 私钥与 X.509 证书 (来自 AOSP build/target/product/security)，
 * 首次使用时合成 PKCS12 密钥库存入私有目录，与其他密钥库走同一套加载流程。
 * 该密钥材料完全公开，仅供测试签名，不适用于正式发布。
 */
object BuiltinTestKey {

    /** 内置配置的固定 ID (用于识别与防误删保护) */
    const val CONFIG_ID = "builtin-testkey"

    /** 私有目录内的密钥库文件名 */
    const val FILE_NAME = "builtin_testkey.p12"

    /** 密钥别名与密码 (沿用 Android 公开测试密钥惯例) */
    const val ALIAS = "testkey"
    const val PASSWORD = "android"

    private const val ASSET_PK8 = "testkey.pk8"
    private const val ASSET_PEM = "testkey.x509.pem"

    /** 确保私有目录内的内置密钥库文件存在，缺失时用 assets 材料重新合成 */
    @Throws(Exception::class)
    fun ensureFile(context: Context, dest: File) {
        if (dest.exists() && dest.length() > 0) return
        val privateKey: PrivateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(readAsset(context, ASSET_PK8)))
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(readAsset(context, ASSET_PEM)))
                as X509Certificate
        val password = PASSWORD.toCharArray()
        val keyStore = KeyStore.getInstance(KeystoreHelper.TYPE_PKCS12)
        keyStore.load(null, null)
        keyStore.setKeyEntry(ALIAS, privateKey, password, arrayOf(certificate))
        dest.parentFile?.mkdirs()
        FileOutputStream(dest).use { keyStore.store(it, password) }
    }

    private fun readAsset(context: Context, name: String): ByteArray =
        context.assets.open(name).use { it.readBytes() }
}
