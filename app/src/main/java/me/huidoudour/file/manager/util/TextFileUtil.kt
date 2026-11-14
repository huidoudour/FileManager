package me.huidoudour.file.manager.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
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
        CRLF, CR -> text.replace("\r\n", "\n").replace('\r', '\n')
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
 * - 读取: 优先按 BOM 识别 UTF-8 / UTF-16, 无 BOM 时严格 UTF-8 解码, 失败回退 GBK
 * - 保存: 沿用读取时的编码与换行风格, BOM 原样写回, 其他应用打开时的显示效果不变
 */
object TextFileUtil {
    /** 超过该大小的文件不进入内置编辑器 (大段文本在 BasicTextField 中无法流畅渲染) */
    const val MAX_EDITABLE_BYTES = 1L * 1024 * 1024

    /** 二进制检测只看文件开头, 避免大文件全量扫描 */
    private const val BINARY_PROBE_BYTES = 8 * 1024

    /** 读取文本; 不抛异常, 结果由 [TextLoadResult] 表达 */
    suspend fun load(file: File): TextLoadResult = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext TextLoadResult.Failure(null)
        if (file.length() > MAX_EDITABLE_BYTES) return@withContext TextLoadResult.TooLarge
        try {
            val bytes = file.readBytes()
            val bomEncoding = TextEncoding.fromBom(bytes)
            if (bomEncoding == null && looksBinary(bytes)) {
                return@withContext TextLoadResult.Binary
            }
            val body = if (bomEncoding == null) {
                bytes
            } else {
                bytes.copyOfRange(bomEncoding.bom.size, bytes.size)
            }
            val (encoding, content) = if (bomEncoding != null) {
                bomEncoding to String(body, bomEncoding.charset)
            } else {
                decodeWithoutBom(body)
            }
            val lineEnding = LineEnding.detect(content)
            TextLoadResult.Success(
                TextDocument(
                    content = lineEnding.normalize(content),
                    encoding = encoding,
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
    private fun decodeWithoutBom(bytes: ByteArray): Pair<TextEncoding, String> {
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        return if (utf8 != null) {
            TextEncoding.UTF8 to utf8
        } else {
            TextEncoding.GBK to String(bytes, TextEncoding.GBK.charset)
        }
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
