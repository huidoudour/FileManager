package me.huidoudour.file.manager.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** 单行歌词; [timeMs] 仅在 [Lyrics.isSynced] 为 true 时有效 (null 表示无时间戳的纯文本行) */
data class LyricLine(val timeMs: Long?, val text: String)

/** 歌词内容; [isSynced] = true 表示歌词带时间戳, 可随播放进度高亮并自动滚动 */
data class Lyrics(val lines: List<LyricLine>, val isSynced: Boolean)

/**
 * 歌词工具 — 智能识别与解析
 *
 * - [find] 查找与音频文件同名的歌词文件 (.lrc 优先, 其次 .txt; 扩展名与文件名均大小写不敏感)
 * - LRC 歌词解析时间戳 (支持一行多时间戳), 按时间排序; [ti:] / [ar:] 等元数据行自动跳过
 * - 无时间戳的文本歌词按纯文本逐行展示
 */
object LyricsUtil {

    /** LRC 时间戳标签: [mm:ss] / [mm:ss.xx] / [mm:ss:xx] (小数 1-3 位) */
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** 按音频路径查找同目录同名歌词文件并解析, 找不到返回 null */
    suspend fun find(audioPath: String): Lyrics? = withContext(Dispatchers.IO) {
        val audio = File(audioPath)
        val parent = audio.parentFile ?: return@withContext null
        val baseName = audio.nameWithoutExtension
        val files = parent.listFiles() ?: return@withContext null
        for (ext in listOf("lrc", "txt")) {
            val lyricsFile = files.firstOrNull {
                it.isFile && it.extension.equals(ext, ignoreCase = true) &&
                    it.nameWithoutExtension.equals(baseName, ignoreCase = true)
            } ?: continue
            val lyrics = runCatching { parse(readTextSmart(lyricsFile)) }.getOrNull()
            if (lyrics != null && lyrics.lines.isNotEmpty()) return@withContext lyrics
        }
        null
    }

    /** 解析歌词文本: 含时间戳 → 同步歌词 (按时间排序), 否则按纯文本逐行 */
    fun parse(raw: String): Lyrics {
        val syncedLines = parseTimedLines(raw)
        return if (syncedLines.isNotEmpty()) {
            Lyrics(syncedLines, isSynced = true)
        } else {
            Lyrics(
                raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    .map { LyricLine(timeMs = null, text = it) },
                isSynced = false
            )
        }
    }

    /** 解析 LRC 时间戳行; 一行多时间戳展开为多条 (重复副歌), 无时间戳行跳过 */
    private fun parseTimedLines(raw: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            val tags = TIME_TAG.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val text = line.substring(tags.last().range.last + 1).trim()
            tags.forEach { tag ->
                val minutes = tag.groupValues[1].toLong()
                val seconds = tag.groupValues[2].toLong()
                val fraction = tag.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                lines += LyricLine(minutes * 60_000 + seconds * 1_000 + millis, text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** 读取文本: UTF-8 严格解码失败时回退 GBK (兼容常见中文歌词编码), 并去除 BOM */
    private fun readTextSmart(file: File): String {
        val bytes = file.readBytes()
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            runCatching { String(bytes, charset("GBK")) }.getOrElse { String(bytes) }
        }.removePrefix("\uFEFF")
    }
}
