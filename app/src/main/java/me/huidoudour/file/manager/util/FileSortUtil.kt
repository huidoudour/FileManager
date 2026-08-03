package me.huidoudour.file.manager.util

import androidx.annotation.StringRes
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem

/** 排序方式 */
enum class SortMode(@StringRes val labelRes: Int) {
    NAME(R.string.sort_name),
    SIZE(R.string.sort_size),
    DATE(R.string.sort_date),
    TYPE(R.string.sort_type)
}

/**
 * 自然排序比较器：数字部分按数值大小排序，非数字部分按字典序（忽略大小写）。
 * 例如：1 < 2 < 3 < 10 < 11 < 12 < 111 < 124 < 125
 */
object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var ia = 0
        var ib = 0

        while (ia < a.length && ib < b.length) {
            val ca = a[ia]
            val cb = b[ib]

            if (ca.isDigit() && cb.isDigit()) {
                // 提取两边的连续数字段，按数值比较
                var numA = 0L
                while (ia < a.length && a[ia].isDigit()) {
                    numA = numA * 10 + (a[ia] - '0')
                    ia++
                }
                var numB = 0L
                while (ib < b.length && b[ib].isDigit()) {
                    numB = numB * 10 + (b[ib] - '0')
                    ib++
                }
                if (numA != numB) return numA.compareTo(numB)
            } else {
                // 非数字字符逐一比较（忽略大小写）
                val lowerA = ca.lowercaseChar()
                val lowerB = cb.lowercaseChar()
                if (lowerA != lowerB) return lowerA.compareTo(lowerB)
                ia++
                ib++
            }
        }
        // 更短的字符串排前面
        return (a.length - ia).compareTo(b.length - ib)
    }
}

object FileSortUtil {
    /**
     * 对文件列表排序：目录在前、文件在后，然后按指定方式排序
     */
    fun sort(files: List<FileItem>, mode: SortMode, ascending: Boolean = true): List<FileItem> {
        val directories = files.filter { it.isDirectory }.let { dirs ->
            when (mode) {
                SortMode.NAME -> if (ascending) dirs.sortedWith(compareBy(NaturalOrderComparator) { it.name })
                    else dirs.sortedWith(compareBy(NaturalOrderComparator.reversed()) { it.name })
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
                SortMode.NAME -> if (ascending) files.sortedWith(compareBy(NaturalOrderComparator) { it.name })
                    else files.sortedWith(compareBy(NaturalOrderComparator.reversed()) { it.name })
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
