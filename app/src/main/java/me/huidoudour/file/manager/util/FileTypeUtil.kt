package me.huidoudour.file.manager.util

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import java.io.File

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

    fun getCategory(file: FileItem): FileCategory {
        if (file.isDirectory) return FileCategory.FOLDER
        val ext = file.extension.lowercase()
        return when {
            ext in imageExtensions -> FileCategory.IMAGE
            ext in videoExtensions -> FileCategory.VIDEO
            ext in audioExtensions -> FileCategory.AUDIO
            ext in documentExtensions -> FileCategory.DOCUMENT
            ext in pdfExtensions -> FileCategory.PDF
            ext in archiveExtensions -> FileCategory.ARCHIVE
            ext in codeExtensions -> FileCategory.CODE
            ext in apkExtensions -> FileCategory.APK
            else -> FileCategory.OTHER
        }
    }

    fun getCategory(file: File): FileCategory {
        if (file.isDirectory) return FileCategory.FOLDER
        val ext = file.extension.lowercase()
        return when {
            ext in imageExtensions -> FileCategory.IMAGE
            ext in videoExtensions -> FileCategory.VIDEO
            ext in audioExtensions -> FileCategory.AUDIO
            ext in documentExtensions -> FileCategory.DOCUMENT
            ext in pdfExtensions -> FileCategory.PDF
            ext in archiveExtensions -> FileCategory.ARCHIVE
            ext in codeExtensions -> FileCategory.CODE
            ext in apkExtensions -> FileCategory.APK
            else -> FileCategory.OTHER
        }
    }

    fun getIcon(category: FileCategory): ImageVector = when (category) {
        FileCategory.FOLDER -> Icons.Filled.Folder
        FileCategory.IMAGE -> Icons.Filled.Image
        FileCategory.VIDEO -> Icons.Filled.Movie
        FileCategory.AUDIO -> Icons.Filled.MusicNote
        FileCategory.DOCUMENT -> Icons.Filled.Description
        FileCategory.PDF -> Icons.Filled.PictureAsPdf
        FileCategory.ARCHIVE -> Icons.Filled.Archive
        FileCategory.CODE -> Icons.Filled.Code
        FileCategory.APK -> Icons.Filled.Android
        FileCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
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
