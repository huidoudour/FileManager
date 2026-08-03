package me.huidoudour.file.manager.provider

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * SAF (Storage Access Framework) DocumentsProvider。
 *
 * 实现后在系统文件选择器（包括"打开"和"保存"对话框）中，
 * 本 App 将作为一个存储位置出现，用户可以浏览目录并创建/保存文件。
 */
class FileDocumentsProvider : DocumentsProvider() {

    companion object {
        /** 默认根 ID */
        private const val ROOT_ID = "primary"

        /** 根目录 */
        private val ROOT_DIR = Environment.getExternalStorageDirectory()

        /** 默认文档投影 */
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
            Document.COLUMN_FLAGS
        )

        /** 默认根投影 */
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
        )

        /** 判断文件是否在根目录范围内 */
        private fun isChildOfRoot(file: File): Boolean {
            val path = file.absolutePath
            val rootPath = ROOT_DIR.absolutePath
            return path == rootPath || path.startsWith("$rootPath/")
        }

        /** 根据文件获取 MIME 类型 */
        private fun getMimeType(file: File): String =
            if (file.isDirectory) {
                Document.MIME_TYPE_DIR
            } else {
                val ext = MimeTypeMap.getFileExtensionFromUrl(file.name).lowercase()
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    ?: "application/octet-stream"
            }
    }

    // =========================================================================
    //  生命周期
    // =========================================================================

    override fun onCreate(): Boolean = true

    // =========================================================================
    //  根目录
    // =========================================================================

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()

        row.add(Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(Root.COLUMN_FLAGS,
            Root.FLAG_SUPPORTS_IS_CHILD or
            Root.FLAG_SUPPORTS_CREATE or
            Root.FLAG_SUPPORTS_SEARCH
        )
        row.add(Root.COLUMN_TITLE, "文件管理")
        row.add(Root.COLUMN_DOCUMENT_ID, getDocumentId(ROOT_DIR))
        row.add(Root.COLUMN_AVAILABLE_BYTES, ROOT_DIR.freeSpace)

        return result
    }

    // =========================================================================
    //  查询文档
    // =========================================================================

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parentDir = getFileForId(parentDocumentId)
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        val children = parentDir.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()

        for (file in children) {
            if (!file.canRead()) continue
            includeFile(result, file)
        }

        return result
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val file = getFileForId(documentId)
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, file)
        return result
    }

    // =========================================================================
    //  打开文档 (读取)
    // =========================================================================

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getFileForId(documentId)
        val accessMode = when {
            "r" in (mode ?: "r") -> ParcelFileDescriptor.MODE_READ_ONLY
            "w" in (mode ?: "w") -> ParcelFileDescriptor.MODE_READ_WRITE or
                ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun openDocumentThumbnail(
        documentId: String?,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        // 不实现缩略图，返回 null 让系统自行处理
        return null
    }

    // =========================================================================
    //  创建文档 (保存/导出文件)
    // =========================================================================

    override fun createDocument(
        parentDocumentId: String?,
        mimeType: String?,
        displayName: String?
    ): String {
        val parentDir = getFileForId(parentDocumentId)
        val newFile = File(parentDir, displayName ?: "untitled")

        if (Document.MIME_TYPE_DIR == mimeType) {
            if (!newFile.mkdir()) {
                throw FileNotFoundException("无法创建目录: ${newFile.absolutePath}")
            }
        } else {
            if (!newFile.createNewFile()) {
                throw FileNotFoundException("无法创建文件: ${newFile.absolutePath}")
            }
        }

        return getDocumentId(newFile)
    }

    // =========================================================================
    //  删除 / 重命名
    // =========================================================================

    override fun deleteDocument(documentId: String?) {
        val file = getFileForId(documentId)
        if (!deleteRecursively(file)) {
            throw FileNotFoundException("无法删除: ${file.absolutePath}")
        }
    }

    override fun renameDocument(documentId: String?, displayName: String?): String? {
        val file = getFileForId(documentId)
        val newFile = File(file.parentFile, displayName ?: return null)
        if (!file.renameTo(newFile)) {
            throw FileNotFoundException("无法重命名: ${file.absolutePath}")
        }
        return getDocumentId(newFile)
    }

    // =========================================================================
    //  搜索
    // =========================================================================

    override fun querySearchDocuments(
        rootId: String?,
        query: String?,
        projection: Array<out String>?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (query.isNullOrBlank()) return result

        val queue = ArrayDeque<File>().apply { add(ROOT_DIR) }
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) queue.add(child)
                if (child.name.contains(query, ignoreCase = true)) {
                    includeFile(result, child)
                }
            }
        }
        return result
    }

    // =========================================================================
    //  isChildDocument
    // =========================================================================

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        val parent = getFileForId(parentDocumentId)
        val child = getFileForId(documentId)
        return child.absolutePath.startsWith(parent.absolutePath + "/")
    }

    // =========================================================================
    //  ID ↔ 路径 转换
    // =========================================================================

    /** 文件路径 → document ID (就是绝对路径) */
    private fun getDocumentId(file: File): String = file.absolutePath

    /** document ID → 文件对象，附带安全检查 */
    private fun getFileForId(documentId: String?): File {
        if (documentId.isNullOrBlank()) {
            throw FileNotFoundException("无效的 document ID")
        }
        val file = File(documentId)
        if (!isChildOfRoot(file)) {
            throw FileNotFoundException("文件不在允许范围内: $documentId")
        }
        return file
    }

    // =========================================================================
    //  辅助
    // =========================================================================

    /** 递归删除 */
    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        return file.delete()
    }

    /** 向 cursor 添加一行文件信息 */
    private fun includeFile(cursor: MatrixCursor, file: File) {
        val flags = when {
            file.isDirectory -> {
                var f = Document.FLAG_DIR_SUPPORTS_CREATE
                if (file.canWrite()) f = f or Document.FLAG_SUPPORTS_DELETE or
                    Document.FLAG_SUPPORTS_RENAME
                f
            }
            else -> {
                var f = 0
                if (file.canWrite()) f = f or Document.FLAG_SUPPORTS_DELETE or
                    Document.FLAG_SUPPORTS_RENAME or
                    Document.FLAG_SUPPORTS_WRITE
                f
            }
        }

        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, getDocumentId(file))
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_SIZE, if (file.isFile) file.length() else null)
            .add(Document.COLUMN_MIME_TYPE, getMimeType(file))
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_FLAGS, flags)
    }
}

