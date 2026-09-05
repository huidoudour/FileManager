package me.huidoudour.file.manager.model

import kotlin.math.log10
import kotlin.math.pow

/**
 * 文件/目录数据模型
 */
data class FileItem(
    val name: String,
    val path: String,
    val parentPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String = "",
    val canRead: Boolean = true,
    val canWrite: Boolean = true,
    val isHidden: Boolean = false
) {

    companion object {
        fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
                .coerceAtMost(units.size - 1)
            return "%.1f %s".format(
                bytes / 1024.0.pow(digitGroups.toDouble()),
                units[digitGroups]
            )
        }
    }
}
