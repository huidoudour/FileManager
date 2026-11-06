package me.huidoudour.file.manager.signing

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.LinkedHashMap

/**
 * 纯 Java 实现的 JKS 密钥库读取器（只读）。
 *
 * Android 平台没有内置 JKS KeyStore 实现，BouncyCastle 的 JKS 支持又只能读证书条目
 * （私钥条目会直接抛 "BC JKS store is read-only..."），因此这里按公开的 JKS 文件格式
 * 与 Sun KeyProtector 算法自行解析。
 */
class JksKeyStoreReader private constructor(
    private val entries: Map<String, Entry>
) {

    private class Entry(
        val protectedKey: ByteArray?,          // 私钥条目: EncryptedPrivateKeyInfo DER
        val chain: List<X509Certificate>?,     // 证书链（私钥条目）或单证书（信任条目）
        val isKeyEntry: Boolean
    )

    /** 所有私钥条目的别名 */
    fun keyAliases(): List<String> =
        entries.filterValues { it.isKeyEntry }.keys.toList()

    fun getCertificateChain(alias: String): List<X509Certificate>? = entries[alias]?.chain

    /**
     * 用 Sun KeyProtector 算法解密私钥（SHA-1 XOR 密钥流）。
     * 密码错误时抛 UnrecoverableKeyException。
     */
    fun getPrivateKey(alias: String, keyPassword: CharArray): PrivateKey {
        val entry = entries[alias]
        if (entry == null || !entry.isKeyEntry || entry.protectedKey == null) {
            throw UnrecoverableKeyException("别名不存在或不是私钥条目: $alias")
        }
        val (algorithmOid, encrypted) = parseEncryptedPrivateKey(entry.protectedKey)
        if (algorithmOid != SUN_KEY_PROTECTOR_OID) {
            throw UnrecoverableKeyException("不支持的私钥保护算法: $algorithmOid")
        }
        if (encrypted.size < 40) throw UnrecoverableKeyException("私钥数据损坏")

        val password = passwordBytes(keyPassword)
        val salt = encrypted.copyOfRange(0, 20)
        val encLength = encrypted.size - 40
        val plain = ByteArray(encLength)

        val digest = MessageDigest.getInstance("SHA-1")
        var block = salt
        var pos = 0
        while (pos < encLength) {
            digest.update(password)
            digest.update(block)
            block = digest.digest()
            var i = 0
            while (i < 20 && pos + i < encLength) {
                plain[pos + i] = (encrypted[20 + pos + i].toInt() xor block[i].toInt()).toByte()
                i++
            }
            pos += 20
        }

        // 校验 SHA1(password + plainKey) == 尾部 20 字节
        digest.reset()
        digest.update(password)
        digest.update(plain)
        val check = digest.digest()
        for (i in 0 until 20) {
            if (check[i] != encrypted[encrypted.size - 20 + i]) {
                throw UnrecoverableKeyException("别名密码错误")
            }
        }

        val keyAlgorithm = keyAlgorithmName(privateKeyAlgorithmOid(plain))
        return KeyFactory.getInstance(keyAlgorithm)
            .generatePrivate(PKCS8EncodedKeySpec(plain))
    }

    companion object {

        private const val MAGIC_JKS = 0xFEEDFEED.toInt()
        private const val MAGIC_JCEKS = 0xCECECECE.toInt()

        /** Sun 专有 KeyProtector 算法 OID */
        private const val SUN_KEY_PROTECTOR_OID = "1.3.6.1.4.1.42.2.17.1.1"

        private val MIGHTY_APHRODITE = "Mighty Aphrodite".toByteArray(Charsets.US_ASCII)

        /** 解析 JKS 文件并校验尾部摘要（同时验证库密码） */
        @JvmStatic
        @Throws(IOException::class)
        fun load(data: ByteArray, storePassword: CharArray?): JksKeyStoreReader {
            val input = DataInputStream(ByteArrayInputStream(data))
            val magic = input.readInt()
            if (magic == MAGIC_JCEKS) {
                throw IOException("暂不支持 JCEKS 格式，请先用 keytool 转换为 PKCS12")
            }
            if (magic != MAGIC_JKS) throw IOException("不是有效的 JKS 密钥库文件")
            val version = input.readInt()
            if (version != 1 && version != 2) throw IOException("不支持的 JKS 版本: $version")

            val certificateFactory = try {
                CertificateFactory.getInstance("X.509")
            } catch (e: Exception) {
                throw IOException(e)
            }

            val entries = LinkedHashMap<String, Entry>()
            try {
                val count = input.readInt()
                for (i in 0 until count) {
                    when (val tag = input.readInt()) {
                        1 -> { // 私钥条目
                            val alias = input.readUTF()
                            input.readLong() // timestamp
                            val protectedKey = ByteArray(input.readInt())
                            input.readFully(protectedKey)
                            val chainLength = input.readInt()
                            val chain = ArrayList<X509Certificate>(chainLength)
                            for (j in 0 until chainLength) chain.add(readCertificate(input, certificateFactory, version))
                            entries[alias] = Entry(protectedKey, chain, isKeyEntry = true)
                        }

                        2 -> { // 信任证书条目
                            val alias = input.readUTF()
                            input.readLong()
                            val chain = listOf(readCertificate(input, certificateFactory, version))
                            entries[alias] = Entry(null, chain, isKeyEntry = false)
                        }

                        else -> throw IOException("JKS 条目类型无法识别: $tag")
                    }
                }
            } catch (e: java.security.cert.CertificateException) {
                throw IOException("解析证书失败: ${e.message}", e)
            }

            // 尾部 20 字节为 SHA1(password_utf16be + "Mighty Aphrodite" + 前面全部内容)
            if (storePassword != null) {
                val actual = ByteArray(20)
                input.readFully(actual)
                try {
                    val digest = MessageDigest.getInstance("SHA-1")
                    digest.update(passwordBytes(storePassword))
                    digest.update(MIGHTY_APHRODITE)
                    digest.update(data, 0, data.size - 20)
                    if (!MessageDigest.isEqual(digest.digest(), actual)) {
                        throw IOException("密钥库密码错误或文件已损坏")
                    }
                } catch (e: java.security.NoSuchAlgorithmException) {
                    throw IOException(e)
                }
            }
            return JksKeyStoreReader(entries)
        }

        private fun readCertificate(
            input: DataInputStream,
            factory: CertificateFactory,
            version: Int
        ): X509Certificate {
            if (version == 2) input.readUTF() // 证书类型，恒为 "X.509"
            val certData = ByteArray(input.readInt())
            input.readFully(certData)
            return factory.generateCertificate(ByteArrayInputStream(certData)) as X509Certificate
        }

        /** 读取 EncryptedPrivateKeyInfo 的算法 OID 与密文 */
        private fun parseEncryptedPrivateKey(der: ByteArray): Pair<String, ByteArray> {
            val outer = readDerTlv(der, 0)
            require(outer.tag == 0x30) { "私钥数据格式错误" }
            val algorithm = readDerTlv(der, outer.start)
            require(algorithm.tag == 0x30) { "私钥保护算法格式错误" }
            val oid = readDerTlv(der, algorithm.start)
            require(oid.tag == 0x06) { "私钥保护算法缺少 OID" }
            val data = readDerTlv(der, algorithm.next)
            require(data.tag == 0x04) { "私钥密文格式错误" }
            return decodeDerOid(der, oid.start, oid.end) to der.copyOfRange(data.start, data.end)
        }

        /** 读取 PrivateKeyInfo 的算法 OID */
        private fun privateKeyAlgorithmOid(der: ByteArray): String {
            val outer = readDerTlv(der, 0)
            require(outer.tag == 0x30) { "私钥数据格式错误" }
            val version = readDerTlv(der, outer.start)
            val algorithm = readDerTlv(der, version.next)
            require(algorithm.tag == 0x30) { "私钥算法格式错误" }
            val oid = readDerTlv(der, algorithm.start)
            require(oid.tag == 0x06) { "私钥算法缺少 OID" }
            return decodeDerOid(der, oid.start, oid.end)
        }

        private fun keyAlgorithmName(oid: String): String = when (oid) {
            "1.2.840.113549.1.1.1" -> "RSA"
            "1.2.840.10045.2.1" -> "EC"
            "1.2.840.10040.4.1" -> "DSA"
            else -> "RSA"
        }

        /** JKS 口令按 UTF-16BE 编码参与摘要与密钥流 */
        private fun passwordBytes(password: CharArray): ByteArray {
            val bytes = ByteArray(password.size * 2)
            for (i in password.indices) {
                bytes[i * 2] = (password[i].code shr 8).toByte()
                bytes[i * 2 + 1] = password[i].code.toByte()
            }
            return bytes
        }
    }
}
