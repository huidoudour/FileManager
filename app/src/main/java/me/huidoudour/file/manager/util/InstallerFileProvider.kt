package me.huidoudour.file.manager.util

import android.net.Uri
import androidx.core.content.FileProvider

/**
 * 专用于向安装器传递安装包的 FileProvider。
 *
 * 系统 FileProvider 会按扩展名推断 MIME，.zip 会被识别为 application/zip，
 * 而安装器只接受 application/octet-stream 等安装包类型。这里按安装包扩展名
 * 返回安装器认可的 MIME，其余统一按二进制流返回，避免安装器在入口校验阶段
 * 直接拒绝内含 APK 的压缩包。
 */
class InstallerFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? {
        val name = uri.lastPathSegment?.lowercase().orEmpty()
        val extension = name.substringAfterLast('.', "")
        if (extension.isNotEmpty() && FileTypeUtil.isInstallerPackage(extension)) {
            return FileTypeUtil.getInstallerMimeType(extension)
        }
        return "application/octet-stream"
    }
}
