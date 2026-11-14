package me.huidoudour.file.manager.util

import androidx.annotation.StringRes
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import java.io.File
import java.util.zip.ZipFile

/** 文件类别 */
enum class FileCategory(@StringRes val labelRes: Int) {
    FOLDER(R.string.category_folder),
    IMAGE(R.string.category_image),
    VIDEO(R.string.category_video),
    AUDIO(R.string.category_audio),
    DOCUMENT(R.string.category_document),
    PDF(R.string.category_pdf),
    ARCHIVE(R.string.category_archive),
    CODE(R.string.category_code),
    APK(R.string.category_apk),
    OTHER(R.string.category_other)
}

object FileTypeUtil {
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "tif", "heic", "heif"
    )
    private val videoExtensions = setOf(
        "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "rmvb"
    )
    private val audioExtensions = setOf(
        "mp3", "wav", "aac", "flac", "ogg", "wma", "m4a", "opus", "mid", "midi"
    )
    private val documentExtensions = setOf(
        "txt", "md", "log", "csv", "xml", "json", "yaml", "yml", "ini", "cfg", "conf"
    )
    private val pdfExtensions = setOf("pdf")
    private val archiveExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "lz4", "zst"
    )
    private val codeExtensions = setOf(
        "kt", "java", "py", "js", "ts", "html", "css", "c", "cpp", "h", "hpp",
        "go", "rs", "swift", "rb", "php", "sh", "bat", "ps1", "sql", "gradle"
    )
    private val apkExtensions = setOf("apk", "xapk", "apks", "apkm")

    /** zip 内 APK 检测的最大层级: 根目录 1 层 + 进入 1 次文件夹 */
    private const val ZIP_APK_MAX_DEPTH = 2

    /** 安装器可直接处理的扩展名 (单 APK 与 xapk/apks/apkm 拆分包) */
    fun isInstallerPackage(extension: String): Boolean =
        extension.lowercase() in apkExtensions

    /** 纯文本文件 (文档与代码类): 可在应用内预览/编辑 */
    fun isTextExtension(extension: String): Boolean {
        val ext = extension.lowercase()
        return ext in documentExtensions || ext in codeExtensions
    }

    /**
     * 安装器使用的 MIME 类型，需与 io.github.huidoudour.Installer 的入口校验保持一致。
     * zip 由安装器自行判断是否内含 APK，因此统一按二进制流传递。
     */
    fun getInstallerMimeType(extension: String): String = when (extension.lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "xapk" -> "application/x-xapk"
        "apkm" -> "application/vnd.apkm"
        else -> "application/octet-stream"
    }

    /**
     * 快速检查压缩包内是否至少包含一个 APK。
     * 仅读取 zip 中央目录，不做解压，避免大文件产生额外 I/O。
     *
     * 只检查靠前的层级：压缩包根目录为第 1 层，每进入一次文件夹加 1 层，
     * 默认最多 2 层 (根 + 1 级文件夹)，更深的条目不再扫描。
     */
    fun zipContainsApk(file: File, maxDepth: Int = ZIP_APK_MAX_DEPTH): Boolean = try {
        ZipFile(file).use { zip ->
            zip.entries().asSequence().any { entry ->
                !entry.isDirectory &&
                    entry.name.endsWith(".apk", ignoreCase = true) &&
                    entryDepth(entry.name) <= maxDepth
            }
        }
    } catch (_: Exception) {
        false
    }

    /** 条目层级: 根目录下的条目为 1, 每多一级文件夹 +1 */
    private fun entryDepth(name: String): Int =
        name.count { it == '/' || it == '\\' } + 1

    fun getCategory(file: FileItem): FileCategory {
        if (file.isDirectory) return FileCategory.FOLDER
        val ext = file.extension.lowercase()
        return when (ext) {
            in imageExtensions -> FileCategory.IMAGE
            in videoExtensions -> FileCategory.VIDEO
            in audioExtensions -> FileCategory.AUDIO
            in documentExtensions -> FileCategory.DOCUMENT
            in pdfExtensions -> FileCategory.PDF
            in archiveExtensions -> FileCategory.ARCHIVE
            in codeExtensions -> FileCategory.CODE
            in apkExtensions -> FileCategory.APK
            else -> FileCategory.OTHER
        }
    }

    fun getMimeType(file: FileItem): String = when (getCategory(file)) {
        FileCategory.FOLDER -> "inode/directory"
        FileCategory.IMAGE -> "image/*"
        FileCategory.VIDEO -> "video/*"
        FileCategory.AUDIO -> "audio/*"
        FileCategory.DOCUMENT -> "text/plain"
        FileCategory.PDF -> "application/pdf"
        FileCategory.ARCHIVE -> "application/zip"
        FileCategory.CODE -> "text/plain"
        FileCategory.APK -> "application/vnd.android.package-archive"
        FileCategory.OTHER -> "application/octet-stream"
    }
}
