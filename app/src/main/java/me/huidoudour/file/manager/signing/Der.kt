package me.huidoudour.file.manager.signing

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 极简 DER 编解码工具。
 *
 * 签名功能需要两类 ASN.1 能力：
 * - 读取：解析 JKS 内私钥条目的 EncryptedPrivateKeyInfo / PrivateKeyInfo 外层结构；
 * - 写入：拼出自签名 X.509 证书（生成密钥时使用）。
 *
 * 只覆盖用到的类型，不引入 BouncyCastle，避免为单一功能增加数 MB 依赖。
 */

/** 一个 DER TLV 的位置信息：tag、内容区间 [start, end) 与下一个元素起点 */
internal class DerTlv(val tag: Int, val start: Int, val end: Int, val next: Int)

/** 读取 offset 处的 DER TLV 头；数据损坏时抛出 IllegalArgumentException */
internal fun readDerTlv(data: ByteArray, offset: Int): DerTlv {
    require(offset + 2 <= data.size) { "DER 数据不完整" }
    var pos = offset
    val tag = data[pos++].toInt() and 0xFF
    var length = data[pos++].toInt() and 0xFF
    if (length and 0x80 != 0) {
        val byteCount = length and 0x7F
        require(byteCount in 1..4 && pos + byteCount <= data.size) { "DER 长度非法" }
        length = 0
        repeat(byteCount) { length = (length shl 8) or (data[pos++].toInt() and 0xFF) }
    }
    require(pos + length <= data.size) { "DER 数据被截断" }
    return DerTlv(tag, pos, pos + length, pos + length)
}

/** 解出 DER OID 的点分字符串（如 1.2.840.113549.1.1.11） */
internal fun decodeDerOid(data: ByteArray, start: Int, end: Int): String {
    require(start < end) { "OID 内容为空" }
    val arcs = ArrayList<Long>()
    var value = 0L
    for (i in start until end) {
        val byte = data[i].toInt() and 0xFF
        value = (value shl 7) or (byte and 0x7F).toLong()
        if (byte and 0x80 == 0) {
            arcs.add(value)
            value = 0
        }
    }
    require(arcs.isNotEmpty()) { "OID 内容非法" }
    val firstArc = when {
        arcs[0] < 40 -> 0
        arcs[0] < 80 -> 1
        else -> 2
    }
    return buildString {
        append(firstArc).append('.').append(arcs[0] - firstArc * 40)
        for (i in 1 until arcs.size) append('.').append(arcs[i])
    }
}

/** 生成证书用的 DER 编码器 */
internal object DerWriter {

    fun sequence(vararg children: ByteArray): ByteArray = tlv(0x30, concat(children))

    fun set(vararg children: ByteArray): ByteArray = tlv(0x31, concat(children))

    fun integer(value: ByteArray): ByteArray = tlv(0x02, value)

    fun integer(value: Int): ByteArray =
        integer(BigInteger.valueOf(value.toLong()).toByteArray())

    fun oid(dotted: String): ByteArray = tlv(0x06, encodeOid(dotted))

    fun utf8(value: String): ByteArray = tlv(0x0C, value.toByteArray(Charsets.UTF_8))

    fun nullValue(): ByteArray = byteArrayOf(0x05, 0x00)

    fun bitString(value: ByteArray): ByteArray = tlv(0x03, byteArrayOf(0) + value)

    /** 上下文相关标签（如 [0] EXPLICIT version） */
    fun explicit(tagNumber: Int, content: ByteArray): ByteArray =
        tlv(0xA0 + tagNumber, content)

    /** 原样嵌入已经是 DER 编码的数据（如 SubjectPublicKeyInfo） */
    fun raw(encoded: ByteArray): ByteArray = encoded

    /**
     * 编码 X.509 时间：2050 年以前用 UTCTime，之后必须用 GeneralizedTime，
     * 否则 25 年有效期的证书会被解成 19xx 年。
     */
    fun time(timeMillis: Long): ByteArray {
        val utc = TimeZone.getTimeZone("UTC")
        val year = Calendar.getInstance(utc).apply { timeInMillis = timeMillis }.get(Calendar.YEAR)
        return if (year >= 2050) {
            val format = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply { timeZone = utc }
            tlv(0x18, format.format(Date(timeMillis)).toByteArray(Charsets.US_ASCII))
        } else {
            val format = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = utc }
            tlv(0x17, format.format(Date(timeMillis)).toByteArray(Charsets.US_ASCII))
        }
    }

    private fun tlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(content.size + 8)
        out.write(tag)
        writeLength(out, content.size)
        out.write(content)
        return out.toByteArray()
    }

    private fun writeLength(out: ByteArrayOutputStream, length: Int) {
        if (length < 0x80) {
            out.write(length)
            return
        }
        var count = 1
        var temp = length ushr 8
        while (temp > 0) {
            count++
            temp = temp ushr 8
        }
        out.write(0x80 or count)
        for (i in count - 1 downTo 0) out.write((length ushr (8 * i)) and 0xFF)
    }

    private fun concat(parts: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream(parts.sumOf { it.size })
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** OID 首字节为 40*arc1+arc2，其余按 7 位分组、最高位标记续字节 */
    private fun encodeOid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toLong() }
        require(arcs.size >= 2) { "OID 至少需要两段: $dotted" }
        val out = ByteArrayOutputStream()
        writeBase128(out, arcs[0] * 40 + arcs[1])
        for (i in 2 until arcs.size) writeBase128(out, arcs[i])
        return out.toByteArray()
    }

    private fun writeBase128(out: ByteArrayOutputStream, value: Long) {
        val groups = ArrayList<Int>()
        var rest = value
        do {
            groups.add((rest and 0x7F).toInt())
            rest = rest ushr 7
        } while (rest > 0)
        for (i in groups.lastIndex downTo 0) {
            out.write(if (i == 0) groups[i] else groups[i] or 0x80)
        }
    }
}
