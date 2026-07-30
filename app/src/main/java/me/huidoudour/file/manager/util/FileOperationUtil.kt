package me.huidoudour.file.manager.util

import java.io.File

/**
 * 文件操作工具: 递归复制 / 移动 / 删除 / 统计
 */
object FileOperationUtil {

    /** destDir 是否位于 src 内部 (防止把目录复制/移动到自己内部) */
    fun isSubPath(src: File, destDir: File): Boolean {
        if (!src.isDirectory) return false
        var cur: File? = destDir
        while (cur != null) {
            if (cur.absolutePath == src.absolutePath) return true
            cur = cur.parentFile
        }
        return false
    }

    /** 递归删除文件/目录 */
    fun deleteRecursively(file: File, onProgress: ((String) -> Unit)? = null): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                if (!deleteRecursively(child, onProgress)) return false
            }
        }
        onProgress?.invoke(file.name)
        return file.delete()
    }

    /** 递归复制文件/目录 */
    fun copyRecursively(src: File, dest: File, onProgress: ((String) -> Unit)? = null): Boolean {
        return try {
            if (src.isDirectory) {
                if (!dest.exists() && !dest.mkdirs()) return false
                src.listFiles()?.forEach { child ->
                    if (!copyRecursively(child, File(dest, child.name), onProgress)) return false
                }
                true
            } else {
                onProgress?.invoke(src.name)
                src.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.setLastModified(src.lastModified())
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** 移动: 优先重命名, 跨卷失败时降级为 复制+删除 */
    fun move(src: File, dest: File, onProgress: ((String) -> Unit)? = null): Boolean {
        if (src.renameTo(dest)) return true
        return copyRecursively(src, dest, onProgress) && deleteRecursively(src)
    }

    /**
     * 递归统计目录: Triple(总大小, 文件数, 目录数)。
     * isActive 用于协程取消检查。目录数不含 root 自身。
     */
    fun computeStats(root: File, isActive: () -> Boolean = { true }): Triple<Long, Int, Int> {
        var size = 0L
        var fileCount = 0
        var dirCount = 0

        fun walk(f: File) {
            if (!isActive()) return
            if (f.isDirectory) {
                dirCount++
                f.listFiles()?.forEach { walk(it) }
            } else {
                fileCount++
                size += f.length()
            }
        }
        walk(root)
        if (root.isDirectory) dirCount--
        return Triple(size, fileCount, dirCount)
    }
}
