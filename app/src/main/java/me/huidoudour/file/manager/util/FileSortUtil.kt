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
     * 对文件列表排序
     *
     * @param directoriesFirst 为 true 时目录排在文件前 (照搬 MaterialFiles 的 sortDirectoriesFirst)
     */
    fun sort(
        files: List<FileItem>,
        mode: SortMode,
        ascending: Boolean = true,
        directoriesFirst: Boolean = true
    ): List<FileItem> {
        fun sortList(list: List<FileItem>): List<FileItem> = when (mode) {
            SortMode.NAME -> if (ascending) list.sortedWith(compareBy(NaturalOrderComparator) { it.name })
                else list.sortedWith(compareBy(NaturalOrderComparator.reversed()) { it.name })
            SortMode.SIZE -> if (ascending) list.sortedBy { it.size }
                else list.sortedByDescending { it.size }
            SortMode.DATE -> if (ascending) list.sortedBy { it.lastModified }
                else list.sortedByDescending { it.lastModified }
            SortMode.TYPE -> if (ascending) list.sortedBy { it.extension }
                else list.sortedByDescending { it.extension }
        }

        return if (directoriesFirst) {
            sortList(files.filter { it.isDirectory }) + sortList(files.filter { !it.isDirectory })
        } else {
            sortList(files)
        }
    }
}
