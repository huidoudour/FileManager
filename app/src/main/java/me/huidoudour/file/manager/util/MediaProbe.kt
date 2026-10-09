package me.huidoudour.file.manager.util

import android.os.Build
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.arthenica.ffmpegkit.MediaInformationSession
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StreamInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 音视频解析工具 — 基于集成到应用内的 FFmpeg (ffprobe / ffmpeg)
 *
 * - [probe] 解析媒体信息 (容器/时长/码率/视频流/音频流)
 * - [extractThumbnail] 抽取视频首帧缩略图
 * - [extractWaveform] 生成音频波形图
 *
 * 注意: ffmpeg-kit 只发布 arm64-v8a / x86_64 二进制, 其他架构 (如 armeabi-v7a)
 * 无法加载 native 库, 调用方需先通过 [isSupported] 兜底 (回退到系统打开方式)。
 */
object MediaProbe {

    /** 当前设备 ABI 是否受 ffmpeg-kit 支持 */
    val isSupported: Boolean =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    /** 媒体信息 (从 ffprobe 结果提取的展示字段) */
    data class MediaInfo(
        val format: String?,
        val formatLong: String?,
        val durationMs: Long?,
        val bitrate: Long?,
        val video: StreamInfo?,
        val audio: StreamInfo?
    )

    /** 单条媒体流信息 */
    data class StreamInfo(
        val codec: String?,
        val codecLong: String?,
        val width: Int?,
        val height: Int?,
        val frameRate: String?,
        val sampleRate: String?,
        val channelLayout: String?
    )

    /** 解析媒体信息, 文件损坏/格式不支持时返回 null */
    suspend fun probe(path: String): MediaInfo? = withContext(Dispatchers.IO) {
        val info = suspendCancellableCoroutine { cont ->
            FFprobeKit.getMediaInformationAsync(path) { session: MediaInformationSession ->
                if (cont.isActive) cont.resume(session.mediaInformation)
            }
        }
        info?.let { toMediaInfo(it) }
    }

    /** 抽取视频缩略图 (第 1 秒处首帧, 失败回退第 0 秒), 失败返回 null */
    suspend fun extractThumbnail(cacheDir: File, path: String): File? = withContext(Dispatchers.IO) {
        val out = cacheFile(cacheDir, "thumb", path, "jpg")
        if (out.exists() && out.length() > 0) return@withContext out
        val success = runFfmpeg(
            "-y", "-ss", "1", "-i", path,
            "-frames:v", "1", "-vf", THUMB_FILTER, "-q:v", "4", out.absolutePath
        ) || runFfmpeg(
            "-y", "-i", path,
            "-frames:v", "1", "-vf", THUMB_FILTER, "-q:v", "4", out.absolutePath
        )
        if (success && out.length() > 0) out else null
    }

    /** 生成音频波形图 (最长取前 10 分钟), 失败返回 null */
    suspend fun extractWaveform(cacheDir: File, path: String): File? = withContext(Dispatchers.IO) {
        val out = cacheFile(cacheDir, "wave", path, "png")
        if (out.exists() && out.length() > 0) return@withContext out
        val success = runFfmpeg(
            "-y", "-i", path, "-t", "600",
            "-filter_complex", WAVE_FILTER, "-frames:v", "1", out.absolutePath
        )
        if (success && out.length() > 0) out else null
    }

    // =========================================================================
    //  内部实现
    // =========================================================================

    private const val THUMB_FILTER = "scale=w=640:h=-2:force_original_aspect_ratio=decrease"
    private const val WAVE_FILTER = "showwavespic=s=640x160:colors=0x64B5F6"

    /** 缓存文件: 路径与修改时间共同决定文件名, 源文件更新后自动重新生成 */
    private fun cacheFile(cacheDir: File, prefix: String, path: String, ext: String): File {
        val dir = File(cacheDir, "media_preview").apply { mkdirs() }
        val stamp = File(path).lastModified()
        return File(dir, "${prefix}_${path.hashCode()}_$stamp.$ext")
    }

    /** 同步执行 ffmpeg (调用方已在 IO 线程), 返回是否成功 */
    private fun runFfmpeg(vararg args: String): Boolean {
        val session = FFmpegKit.executeWithArguments(arrayOf(*args))
        return ReturnCode.isSuccess(session.returnCode)
    }

    private fun toMediaInfo(info: MediaInformation): MediaInfo {
        val streams = info.streams.orEmpty()
        return MediaInfo(
            format = info.format,
            formatLong = info.longFormat,
            durationMs = info.duration?.toDoubleOrNull()?.let { (it * 1000).toLong() },
            bitrate = info.bitrate?.toLongOrNull(),
            video = streams.firstOrNull { it.type == "video" }?.let { toStreamInfo(it) },
            audio = streams.firstOrNull { it.type == "audio" }?.let { toStreamInfo(it) }
        )
    }

    private fun toStreamInfo(stream: StreamInformation) = StreamInfo(
        codec = stream.codec?.uppercase(),
        codecLong = stream.codecLong,
        width = stream.width?.toInt(),
        height = stream.height?.toInt(),
        frameRate = formatFrameRate(stream.averageFrameRate),
        sampleRate = stream.sampleRate,
        channelLayout = stream.channelLayout
    )

    /** "30000/1001" -> "29.97" (整数帧率不带小数) */
    private fun formatFrameRate(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val fps = run {
            val parts = value.split("/")
            if (parts.size == 2) {
                val num = parts[0].toDoubleOrNull() ?: return@run null
                val den = parts[1].toDoubleOrNull() ?: return@run null
                if (den == 0.0) null else num / den
            } else {
                value.toDoubleOrNull()
            }
        } ?: return value
        return if (fps % 1.0 == 0.0) fps.toInt().toString()
        else String.format(Locale.US, "%.2f", fps)
    }
}
