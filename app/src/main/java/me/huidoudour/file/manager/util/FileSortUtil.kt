package me.huidoudour.file.manager.util

import me.huidoudour.file.manager.model.FileItem

/** 排序方式 */
enum class SortMode(val label: String) {
    NAME("名称"),
    SIZE("大小"),
    DATE("日期"),
    TYPE("类型")
}

object FileSortUtil {
    /**
     * 对文件列表排序：目录在前、文件在后，然后按指定方式排序
     */
    fun sort(files: List<FileItem>, mode: SortMode, ascending: Boolean = true): List<FileItem> {
        val directories = files.filter { it.isDirectory }.let { dirs ->
            when (mode) {
                SortMode.NAME -> if (ascending) dirs.sortedBy { it.name.lowercase() }
                    else dirs.sortedByDescending { it.name.lowercase() }
                SortMode.SIZE -> if (ascending) dirs.sortedBy { it.size }
                    else dirs.sortedByDescending { it.size }
                SortMode.DATE -> if (ascending) dirs.sortedBy { it.lastModified }
                    else dirs.sortedByDescending { it.lastModified }
                SortMode.TYPE -> if (ascending) dirs.sortedBy { it.extension }
                    else dirs.sortedByDescending { it.extension }
            }
        }
        val regularFiles = files.filter { !it.isDirectory }.let { files ->
            when (mode) {
                SortMode.NAME -> if (ascending) files.sortedBy { it.name.lowercase() }
                    else files.sortedByDescending { it.name.lowercase() }
                SortMode.SIZE -> if (ascending) files.sortedBy { it.size }
                    else files.sortedByDescending { it.size }
                SortMode.DATE -> if (ascending) files.sortedBy { it.lastModified }
                    else files.sortedByDescending { it.lastModified }
                SortMode.TYPE -> if (ascending) files.sortedBy { it.extension }
                    else files.sortedByDescending { it.extension }
            }
        }
        return directories + regularFiles
    }
}
