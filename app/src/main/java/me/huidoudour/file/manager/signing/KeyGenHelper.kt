package me.huidoudour.file.manager.signing

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec

/**
 * 应用内生成自签名密钥：密钥对 + 自签名 X.509 证书，保存为 PKCS12 格式。
 *
 * 证书由 [DerWriter] 手工拼出 DER，再用 [Signature] 签署，不依赖 BouncyCastle；
 * 生成后会做一次自校验（验证签名），避免写出无法使用的密钥库。
 */
object KeyGenHelper {

    const val ALG_RSA_2048 = "RSA 2048"
    const val ALG_RSA_4096 = "RSA 4096"
    const val ALG_EC_P256 = "EC P-256"

    val algorithms: List<String> = listOf(ALG_RSA_2048, ALG_RSA_4096, ALG_EC_P256)

    private const val OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11"
    private const val OID_SHA256_WITH_ECDSA = "1.2.840.10045.4.3.2"
    private const val OID_COMMON_NAME = "2.5.4.3"
    private const val OID_ORGANIZATION = "2.5.4.10"
    private const val OID_ORGANIZATIONAL_UNIT = "2.5.4.11"

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * 生成密钥并落盘到私有目录，返回私有目录内的文件名。
     *
     * @param algorithm     [algorithms] 之一
     * @param cn            证书主体 CN（必填）
     * @param o             组织 O（可空）
     * @param ou            组织单元 OU（可空）
     * @param validityYears 证书有效期（年）
     */
    @Throws(Exception::class)
    fun generate(
        keystoreDir: File,
        alias: String,
        password: CharArray,
        algorithm: String,
        cn: String,
        o: String,
        ou: String,
        validityYears: Int
    ): String {
        require(cn.isNotBlank()) { "证书通用名称不能为空" }
        require(alias.isNotBlank()) { "别名不能为空" }
        // 上限 100 年，避免溢出到非法年份
        require(validityYears in 1..100) { "有效期需在 1-100 年之间" }

        val (keyPair, signatureAlgorithm) = createKeyPair(algorithm)
        val certificate = createSelfSignedCertificate(keyPair, signatureAlgorithm, cn, o, ou, validityYears)

        val keyStore = KeyStore.getInstance(KeystoreHelper.TYPE_PKCS12)
        keyStore.load(null, null)
        keyStore.setKeyEntry(alias, keyPair.private, password, arrayOf(certificate))

        if (!keystoreDir.exists() && !keystoreDir.mkdirs()) {
            throw IllegalStateException("无法创建密钥库目录: ${keystoreDir.absolutePath}")
        }
        val fileName = "${System.currentTimeMillis()}_${alias.replace(Regex("[^A-Za-z0-9._-]"), "_")}.p12"
        FileOutputStream(File(keystoreDir, fileName)).use { keyStore.store(it, password) }
        return fileName
    }

    private fun createKeyPair(algorithm: String): Pair<KeyPair, String> {
        val generator: KeyPairGenerator
        val signatureAlgorithm: String
        when (algorithm) {
            ALG_EC_P256 -> {
                generator = KeyPairGenerator.getInstance("EC")
                generator.initialize(ECGenParameterSpec("secp256r1"))
                signatureAlgorithm = "SHA256withECDSA"
            }

            ALG_RSA_4096 -> {
                generator = KeyPairGenerator.getInstance("RSA")
                generator.initialize(4096)
                signatureAlgorithm = "SHA256withRSA"
            }

            else -> {
                generator = KeyPairGenerator.getInstance("RSA")
                generator.initialize(2048)
                signatureAlgorithm = "SHA256withRSA"
            }
        }
        return generator.generateKeyPair() to signatureAlgorithm
    }

    private fun createSelfSignedCertificate(
        keyPair: KeyPair,
        signatureAlgorithm: String,
        cn: String,
        o: String,
        ou: String,
        validityYears: Int
    ): X509Certificate {
        val subject = DerWriter.sequence(
            *buildList {
                add(attribute(OID_COMMON_NAME, cn))
                if (o.isNotBlank()) add(attribute(OID_ORGANIZATION, o))
                if (ou.isNotBlank()) add(attribute(OID_ORGANIZATIONAL_UNIT, ou))
            }.toTypedArray()
        )
        // RFC 5758: ECDSA 的 AlgorithmIdentifier 不带参数，RSA 则写 NULL
        val algorithmIdentifier = if (signatureAlgorithm.endsWith("ECDSA")) {
            DerWriter.sequence(DerWriter.oid(OID_SHA256_WITH_ECDSA))
        } else {
            DerWriter.sequence(DerWriter.oid(OID_SHA256_WITH_RSA), DerWriter.nullValue())
        }

        val serial = BigInteger(64, SecureRandom()).setBit(0)
        val now = System.currentTimeMillis()
        val tbsCertificate = DerWriter.sequence(
            DerWriter.explicit(0, DerWriter.integer(2)), // v3
            DerWriter.integer(serial.toByteArray()),
            algorithmIdentifier,
            subject,
            DerWriter.sequence(
                DerWriter.time(now - DAY_MILLIS),
                DerWriter.time(now + validityYears * 365L * DAY_MILLIS)
            ),
            subject,
            DerWriter.raw(keyPair.public.encoded)
        )

        val signatureValue = Signature.getInstance(signatureAlgorithm).run {
            initSign(keyPair.private)
            update(tbsCertificate)
            sign()
        }
        val encoded = DerWriter.sequence(
            tbsCertificate,
            algorithmIdentifier,
            DerWriter.bitString(signatureValue)
        )
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
        // 自校验: 证书结构可解析且签名有效, 否则直接抛异常而不是写出坏文件
        certificate.verify(keyPair.public)
        return certificate
    }

    private fun attribute(oid: String, value: String): ByteArray =
        DerWriter.set(DerWriter.sequence(DerWriter.oid(oid), DerWriter.utf8(value)))
}
