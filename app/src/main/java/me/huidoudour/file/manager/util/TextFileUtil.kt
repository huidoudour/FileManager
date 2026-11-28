package me.huidoudour.file.manager.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 换行风格 (保存时按读取到的风格写回, 避免整份文件的换行方式被改写) */
enum class LineEnding(val value: String) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r");

    /** 统一为 LF 供编辑框使用; 保存时再按本风格还原 */
    fun normalize(text: String): String = when (this) {
        LF -> text
        // 单次遍历完成转换: 大文件时避免 replace 链产生多份全文副本
        CRLF, CR -> buildString(text.length) {
            var index = 0
            while (index < text.length) {
                if (text[index] == '\r') {
                    append('\n')
                    index += if (text.getOrNull(index + 1) == '\n') 2 else 1
                } else {
                    append(text[index])
                    index++
                }
            }
        }
    }

    companion object {
        /** 取出现次数最多的换行风格; 文本中没有换行时默认 LF */
        fun detect(text: String): LineEnding {
            var lf = 0
            var crlf = 0
            var cr = 0
            var index = 0
            while (index < text.length) {
                when (text[index]) {
                    '\r' -> if (text.getOrNull(index + 1) == '\n') {
                        crlf++
                        index++
                    } else {
                        cr++
                    }

                    '\n' -> lf++
                }
                index++
            }
            return when {
                crlf > 0 && crlf >= lf && crlf >= cr -> CRLF
                cr > 0 && cr > lf -> CR
                else -> LF
            }
        }
    }
}

/** 文本编码; [bom] 非空表示文件带 BOM, 保存时原样写回 */
enum class TextEncoding(val label: String, val charset: Charset, val bom: ByteArray) {
    UTF8("UTF-8", Charsets.UTF_8, ByteArray(0)),
    UTF8_BOM("UTF-8 BOM", Charsets.UTF_8, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())),
    UTF16_LE("UTF-16 LE", Charsets.UTF_16LE, byteArrayOf(0xFF.toByte(), 0xFE.toByte())),
    UTF16_BE("UTF-16 BE", Charsets.UTF_16BE, byteArrayOf(0xFE.toByte(), 0xFF.toByte())),
    GBK("GBK", charset("GBK"), ByteArray(0));

    companion object {
        /** 按 BOM 头识别编码, 无 BOM 返回 null */
        fun fromBom(bytes: ByteArray): TextEncoding? = when {
            bytes.hasPrefix(UTF8_BOM.bom) -> UTF8_BOM
            bytes.hasPrefix(UTF16_LE.bom) -> UTF16_LE
            bytes.hasPrefix(UTF16_BE.bom) -> UTF16_BE
            else -> null
        }

        private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}

/** 读取到的文本内容及其原始编码 / 换行风格 */
data class TextDocument(
    val content: String,
    val encoding: TextEncoding,
    val lineEnding: LineEnding
)

/** 文本读取结果 */
sealed interface TextLoadResult {
    data class Success(val document: TextDocument) : TextLoadResult
    data object TooLarge : TextLoadResult
    data object Binary : TextLoadResult
    data class Failure(val message: String?) : TextLoadResult
}

/**
 * 纯文本读写 — 内置文本编辑器使用的编码与换行探测
 *
 * - 读取: 先探测文件头判断 BOM 与二进制, 再按编码流式解码 (不把整份文件读成字节数组),
 *   无 BOM 时严格 UTF-8 解码, 失败回退 GBK; 换行统一为 LF, 原始风格另行记录
 * - 保存: 沿用读取时的编码与换行风格, BOM 原样写回, 其他应用打开时的显示效果不变
 */
object TextFileUtil {
    /**
     * 超过该大小的文件不进入内置编辑器。
     *
     * 编辑器渲染是逐行虚拟化的, 但载入时仍需把全文解码进内存 (编辑与撤销都要用),
     * 峰值约为文件大小的 3~4 倍, 故上限取 10MB, 常见的日志/源码/配置都能直接编辑。
     */
    const val MAX_EDITABLE_BYTES = 10L * 1024 * 1024

    /** 二进制检测只看文件开头, 避免大文件全量扫描 */
    private const val BINARY_PROBE_BYTES = 8 * 1024

    /** 流式解码的分块字符数 */
    private const val DECODE_BUFFER_CHARS = 64 * 1024

    /** 解码结果: 文本内容 + 实际采用的编码 */
    private class Decoded(val content: String, val encoding: TextEncoding)

    /** 读取文本; 不抛异常, 结果由 [TextLoadResult] 表达 */
    suspend fun load(file: File): TextLoadResult = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext TextLoadResult.Failure(null)
        if (file.length() > MAX_EDITABLE_BYTES) return@withContext TextLoadResult.TooLarge
        try {
            val head = readHead(file, BINARY_PROBE_BYTES)
            val bomEncoding = TextEncoding.fromBom(head)
            if (bomEncoding == null && looksBinary(head)) {
                return@withContext TextLoadResult.Binary
            }
            val decoded = if (bomEncoding != null) {
                Decoded(
                    content = decode(file, bomEncoding.charset, bomEncoding.bom.size),
                    encoding = bomEncoding
                )
            } else {
                decodeWithoutBom(file)
            }
            val lineEnding = LineEnding.detect(decoded.content)
            TextLoadResult.Success(
                TextDocument(
                    content = lineEnding.normalize(decoded.content),
                    encoding = decoded.encoding,
                    lineEnding = lineEnding
                )
            )
        } catch (e: Exception) {
            TextLoadResult.Failure(e.message)
        }
    }

    /** 写入文本; 失败时抛出 IOException, 由调用方提示 */
    suspend fun save(
        file: File,
        content: String,
        encoding: TextEncoding,
        lineEnding: LineEnding
    ): Unit = withContext(Dispatchers.IO) {
        val normalized = lineEnding.normalize(content)
        val text = if (lineEnding == LineEnding.LF) {
            normalized
        } else {
            normalized.replace("\n", lineEnding.value)
        }
        val body = text.toByteArray(encoding.charset)
        val bytes = if (encoding.bom.isEmpty()) body else encoding.bom + body
        file.writeBytes(bytes)
    }

    /** 无 BOM: 严格 UTF-8 解码失败时回退 GBK (兼容常见中文文本) */
    private fun decodeWithoutBom(file: File): Decoded {
        val utf8 = try {
            decode(file, Charsets.UTF_8, strict = true)
        } catch (e: CharacterCodingException) {
            null
        }
        return if (utf8 != null) {
            Decoded(utf8, TextEncoding.UTF8)
        } else {
            Decoded(decode(file, TextEncoding.GBK.charset), TextEncoding.GBK)
        }
    }

    /**
     * 按编码流式解码整个文件, 分块读取, 不额外持有整份文件的字节数组。
     *
     * [strict] 为 true 时遇到非法字节直接抛 [CharacterCodingException], 由调用方换编码重试。
     */
    private fun decode(
        file: File,
        charset: Charset,
        skipBytes: Int = 0,
        strict: Boolean = false
    ): String {
        file.inputStream().buffered().use { input ->
            var skipped = 0
            while (skipped < skipBytes) {
                val count = input.skip((skipBytes - skipped).toLong())
                if (count <= 0) break
                skipped += count.toInt()
            }
            val decoder = charset.newDecoder().apply {
                if (strict) {
                    onMalformedInput(CodingErrorAction.REPORT)
                    onUnmappableCharacter(CodingErrorAction.REPORT)
                }
            }
            InputStreamReader(input, decoder).use { reader ->
                // 预分配取较小值: UTF-16/GBK 的字符数远少于字节数, 避免一次性预留过大
                val content = StringBuilder(minOf(file.length(), 1L shl 20).toInt())
                val buffer = CharArray(DECODE_BUFFER_CHARS)
                while (true) {
                    val count = reader.read(buffer)
                    if (count <= 0) break
                    content.append(buffer, 0, count)
                }
                return content.toString()
            }
        }
    }

    /** 读取文件开头若干字节, 用于 BOM 与二进制探测 */
    private fun readHead(file: File, limit: Int): ByteArray =
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(limit)
            var read = 0
            while (read < limit) {
                val count = input.read(buffer, read, limit - read)
                if (count <= 0) break
                read += count
            }
            if (read == limit) buffer else buffer.copyOf(read)
        }

    /** 开头出现 NUL 字节即视为二进制 (纯文本文件不会包含) */
    private fun looksBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, BINARY_PROBE_BYTES)
        for (index in 0 until limit) {
            if (bytes[index] == 0.toByte()) return true
        }
        return false
    }
}
