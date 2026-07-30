package me.huidoudour.file.manager.model

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
    /** 格式化文件大小 */
    val formattedSize: String
        get() = if (isDirectory) "" else formatSize(size)

    /** 是否是存储根目录 (如 /storage/emulated/0) */
    val isStorageRoot: Boolean
        get() = path == "/storage/emulated/0" || path == "/"

    companion object {
        fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
                .coerceAtMost(units.size - 1)
            return "%.1f %s".format(
                bytes / Math.pow(1024.0, digitGroups.toDouble()),
                units[digitGroups]
            )
        }
    }
}
