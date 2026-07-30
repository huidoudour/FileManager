package me.huidoudour.file.manager.viewmodel

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.FileOperationUtil
import me.huidoudour.file.manager.util.FileSortUtil
import me.huidoudour.file.manager.util.SortMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 剪贴板数据 (复制/剪切) */
data class ClipboardData(val items: List<FileItem>, val isCut: Boolean)

/** 文件操作进度 */
data class OperationProgress(
    val title: String,
    val currentName: String,
    val processed: Int,
    val total: Int
)

/** 目录统计信息 (属性对话框用) */
data class DirStats(
    val size: Long,
    val fileCount: Int,
    val dirCount: Int,
    val finished: Boolean
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** 存储根路径 */
        val STORAGE_ROOT = Environment.getExternalStorageDirectory().absolutePath
        /** 日期格式化 */
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        /** SharedPreferences 键 */
        private const val PREFS_NAME = "file_manager_prefs"
        private const val KEY_LAST_PATH = "last_path"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_FAVORITES = "favorites"
        /** 搜索结果上限 */
        private const val MAX_SEARCH_RESULTS = 300
    }

    private val prefs =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- 状态 ---

    private val _currentPath = MutableStateFlow(STORAGE_ROOT)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.NAME)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    /** 多选: 已选中的文件路径集合 */
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    /** 剪贴板 */
    private val _clipboard = MutableStateFlow<ClipboardData?>(null)
    val clipboard: StateFlow<ClipboardData?> = _clipboard.asStateFlow()

    /** 文件操作进度 (null 表示无操作进行中) */
    private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
    val operationProgress: StateFlow<OperationProgress?> = _operationProgress.asStateFlow()

    /** 粘贴冲突的文件名列表 (null 表示无冲突待处理) */
    private val _pasteConflicts = MutableStateFlow<List<String>?>(null)
    val pasteConflicts: StateFlow<List<String>?> = _pasteConflicts.asStateFlow()

    /** 是否显示隐藏文件 */
    private val _showHidden = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HIDDEN, false))
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    /** 搜索状态 */
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<FileItem>>(emptyList())
    val searchResults: StateFlow<List<FileItem>> = _searchResults.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    private var searchJob: Job? = null

    /** 收藏目录路径列表 */
    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    /** 轻提示消息 (Snackbar) */
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    /** 属性对话框目标与统计 */
    private val _propertiesTarget = MutableStateFlow<FileItem?>(null)
    val propertiesTarget: StateFlow<FileItem?> = _propertiesTarget.asStateFlow()

    private val _propertiesStats = MutableStateFlow<DirStats?>(null)
    val propertiesStats: StateFlow<DirStats?> = _propertiesStats.asStateFlow()

    private var statsJob: Job? = null

    /** 导航历史栈 */
    private val _navigationHistory = mutableListOf<String>()

    /** 是否处于文件选取模式 */
    private var _pickerMode = false

    init {
        val startPath = getSavedPath()
        loadDirectory(startPath)
    }

    // --- 公开方法 ---

    fun setPickerMode(enabled: Boolean) {
        _pickerMode = enabled
    }

    fun isPickerMode(): Boolean = _pickerMode

    /**
     * 加载指定目录的文件列表
     */
    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val fileList = withContext(Dispatchers.IO) {
                    val dir = File(path)
                    if (!dir.exists() || !dir.isDirectory) {
                        throw Exception("目录不存在或无法访问: $path")
                    }
                    if (!dir.canRead()) {
                        throw Exception("没有读取权限: $path")
                    }
                    dir.listFiles()
                        ?.filter { _showHidden.value || !it.isHidden }
                        ?.map { toFileItem(it) }
                        ?: emptyList()
                }
                _currentPath.value = path
                _files.value = FileSortUtil.sort(fileList, _sortMode.value, _sortAscending.value)
                saveCurrentPath()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "未知错误"
                _files.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 刷新当前目录
     */
    fun refresh() {
        loadDirectory(_currentPath.value)
    }

    /**
     * 进入子目录
     */
    fun navigateToDirectory(fileItem: FileItem) {
        if (fileItem.isDirectory && fileItem.canRead) {
            clearSelection()
            _navigationHistory.add(0, _currentPath.value)
            loadDirectory(fileItem.path)
        }
    }

    /**
     * 返回上级目录
     */
    fun navigateUp(): Boolean {
        val currentPath = _currentPath.value
        if (currentPath == STORAGE_ROOT || currentPath == "/") return false

        val parentFile = File(currentPath).parentFile
        if (parentFile != null && parentFile.canRead()) {
            if (_navigationHistory.isNotEmpty()) {
                _navigationHistory.removeFirstOrNull()
            }
            loadDirectory(parentFile.absolutePath)
            return true
        }
        return false
    }

    /**
     * 后退 (使用历史栈)
     */
    fun navigateBack(): Boolean {
        if (_navigationHistory.isNotEmpty()) {
            val previousPath = _navigationHistory.removeAt(0)
            loadDirectory(previousPath)
            return true
        }
        return false
    }

    /**
     * 切换排序方式
     */
    fun setSortMode(mode: SortMode) {
        if (_sortMode.value == mode) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortMode.value = mode
            _sortAscending.value = true
        }
        _files.value = FileSortUtil.sort(_files.value, _sortMode.value, _sortAscending.value)
        _searchResults.value =
            FileSortUtil.sort(_searchResults.value, _sortMode.value, _sortAscending.value)
    }

    /**
     * 格式化日期
     */
    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    /**
     * 获取文件路径各部分 (用于面包屑导航)
     */
    fun getPathSegments(path: String): List<Pair<String, String>> {
        val segments = mutableListOf<Pair<String, String>>()
        val parts = path.split(File.separator).filter { it.isNotEmpty() }
        var accumulatedPath =
            if (path.startsWith(File.separator)) File.separator else ""
        // 存储根显示为 "内部存储"
        segments.add("内部存储" to STORAGE_ROOT)
        for (part in parts) {
            accumulatedPath = if (accumulatedPath.endsWith(File.separator))
                accumulatedPath + part
            else
                accumulatedPath + File.separator + part
            if (accumulatedPath == STORAGE_ROOT) continue
            segments.add(part to accumulatedPath)
        }
        return segments
    }

    /** 清除错误信息 */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 保存当前路径（仅在非选取模式下持久化）
     */
    private fun saveCurrentPath() {
        if (!_pickerMode) {
            prefs.edit().putString(KEY_LAST_PATH, _currentPath.value).apply()
        }
    }

    /**
     * 读取上次保存的路径，验证有效性后返回；无效则返回存储根
     */
    private fun getSavedPath(): String {
        val saved = prefs.getString(KEY_LAST_PATH, null)
        if (saved != null) {
            val dir = File(saved)
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                return saved
            }
        }
        return STORAGE_ROOT
    }

    /** File -> FileItem 转换 */
    private fun toFileItem(file: File): FileItem = FileItem(
        name = file.name,
        path = file.absolutePath,
        parentPath = file.parent ?: "",
        isDirectory = file.isDirectory,
        size = if (file.isFile) file.length() else 0L,
        lastModified = file.lastModified(),
        extension = if (file.isFile) file.extension else "",
        canRead = file.canRead(),
        canWrite = file.canWrite(),
        isHidden = file.isHidden
    )

    // =========================================================================
    //  多选
    // =========================================================================

    /** 当前展示列表 (搜索中则为搜索结果) */
    private fun displayedList(): List<FileItem> =
        if (_isSearchActive.value && _searchQuery.value.isNotBlank()) _searchResults.value
        else _files.value

    fun toggleSelection(item: FileItem) {
        _selectedPaths.value = _selectedPaths.value.toMutableSet().apply {
            if (!add(item.path)) remove(item.path)
        }
    }

    fun selectAll() {
        _selectedPaths.value = displayedList().map { it.path }.toSet()
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }

    /** 当前选中的 FileItem 列表 */
    fun selectedItems(): List<FileItem> =
        displayedList().filter { it.path in _selectedPaths.value }

    // =========================================================================
    //  剪贴板 / 复制 / 移动
    // =========================================================================

    fun copyToClipboard(items: List<FileItem>) {
        if (items.isEmpty()) return
        _clipboard.value = ClipboardData(items, isCut = false)
        clearSelection()
        _toastMessage.value = "已复制 ${items.size} 项, 到目标目录后粘贴"
    }

    fun cutToClipboard(items: List<FileItem>) {
        if (items.isEmpty()) return
        _clipboard.value = ClipboardData(items, isCut = true)
        clearSelection()
        _toastMessage.value = "已剪切 ${items.size} 项, 到目标目录后粘贴"
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    /** 请求粘贴: 检测冲突, 有冲突则先弹窗确认 */
    fun requestPaste() {
        val clip = _clipboard.value ?: return
        val destDir = File(_currentPath.value)
        val conflicts = clip.items
            .filter { File(destDir, File(it.path).name).exists() }
            .map { it.name }
        if (conflicts.isNotEmpty()) {
            _pasteConflicts.value = conflicts
        } else {
            performPaste(overwrite = false)
        }
    }

    /** 冲突处理: overwrite=true 覆盖 / false 跳过同名项 */
    fun resolvePasteConflict(overwrite: Boolean) {
        _pasteConflicts.value = null
        performPaste(overwrite)
    }

    fun cancelPasteConflict() {
        _pasteConflicts.value = null
    }

    private fun performPaste(overwrite: Boolean) {
        val clip = _clipboard.value ?: return
        val destDir = File(_currentPath.value)
        val title = if (clip.isCut) "移动中" else "复制中"
        viewModelScope.launch {
            _operationProgress.value = OperationProgress(title, "", 0, clip.items.size)
            var failed = 0
            withContext(Dispatchers.IO) {
                clip.items.forEachIndexed { index, item ->
                    val src = File(item.path)
                    _operationProgress.value =
                        OperationProgress(title, src.name, index, clip.items.size)
                    if (!src.exists()) {
                        failed++
                        return@forEachIndexed
                    }
                    // 禁止把目录粘贴到自己内部
                    if (FileOperationUtil.isSubPath(src, destDir)) {
                        failed++
                        return@forEachIndexed
                    }
                    val dest = File(destDir, src.name)
                    // 同一位置粘贴直接跳过
                    if (src.absolutePath == dest.absolutePath) return@forEachIndexed
                    if (dest.exists()) {
                        if (!overwrite) return@forEachIndexed
                        FileOperationUtil.deleteRecursively(dest)
                    }
                    val ok = if (clip.isCut) {
                        FileOperationUtil.move(src, dest)
                    } else {
                        FileOperationUtil.copyRecursively(src, dest)
                    }
                    if (!ok) failed++
                }
            }
            _operationProgress.value = null
            if (clip.isCut) _clipboard.value = null
            _toastMessage.value = if (failed == 0) "操作完成" else "$failed 项操作失败"
            refresh()
        }
    }

    // =========================================================================
    //  删除 / 重命名 / 新建
    // =========================================================================

    fun deleteFiles(items: List<FileItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _operationProgress.value = OperationProgress("删除中", "", 0, items.size)
            var failed = 0
            withContext(Dispatchers.IO) {
                items.forEachIndexed { index, item ->
                    _operationProgress.value =
                        OperationProgress("删除中", item.name, index, items.size)
                    if (!FileOperationUtil.deleteRecursively(File(item.path))) failed++
                }
            }
            _operationProgress.value = null
            clearSelection()
            _toastMessage.value = if (failed == 0) "已删除 ${items.size} 项" else "$failed 项删除失败"
            refresh()
        }
    }

    fun renameFile(item: FileItem, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val src = File(item.path)
            val dest = File(src.parentFile, newName)
            when {
                newName.isBlank() -> _toastMessage.value = "名称不能为空"
                dest.exists() -> _toastMessage.value = "已存在同名文件"
                !src.renameTo(dest) -> _toastMessage.value = "重命名失败"
                else -> {
                    // 同步更新收藏中的旧路径
                    if (item.path in _favorites.value) {
                        toggleFavorite(item.path)
                        toggleFavorite(dest.absolutePath)
                    }
                    _toastMessage.value = "已重命名为 $newName"
                }
            }
            clearSelection()
            refresh()
        }
    }

    fun createFolder(name: String) {
        createItem(name, isFolder = true)
    }

    fun createFile(name: String) {
        createItem(name, isFolder = false)
    }

    private fun createItem(name: String, isFolder: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = File(_currentPath.value, name)
            when {
                name.isBlank() -> _toastMessage.value = "名称不能为空"
                target.exists() -> _toastMessage.value = "已存在同名文件"
                else -> {
                    val ok = try {
                        if (isFolder) target.mkdirs() else target.createNewFile()
                    } catch (_: Exception) {
                        false
                    }
                    _toastMessage.value = if (ok) "已创建 $name" else "创建失败"
                }
            }
            refresh()
        }
    }

    // =========================================================================
    //  隐藏文件
    // =========================================================================

    fun toggleShowHidden() {
        val newValue = !_showHidden.value
        _showHidden.value = newValue
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, newValue).apply()
        refresh()
    }

    // =========================================================================
    //  搜索 (当前目录及子目录递归)
    // =========================================================================

    fun openSearch() {
        _isSearchActive.value = true
    }

    fun closeSearch() {
        searchJob?.cancel()
        _isSearchActive.value = false
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearchLoading.value = false
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _isSearchLoading.value = true
            val results = mutableListOf<FileItem>()
            val queue = ArrayDeque<File>().apply { add(File(_currentPath.value)) }
            val showHidden = _showHidden.value
            while (queue.isNotEmpty() && results.size < MAX_SEARCH_RESULTS) {
                ensureActive()
                val dir = queue.removeFirst()
                val children = dir.listFiles() ?: continue
                for (child in children) {
                    if (!showHidden && child.isHidden) continue
                    if (child.isDirectory) queue.add(child)
                    if (child.name.contains(query, ignoreCase = true)) {
                        results.add(toFileItem(child))
                        if (results.size >= MAX_SEARCH_RESULTS) break
                    }
                }
                // 增量刷新结果
                _searchResults.value = results.toList()
            }
            _searchResults.value =
                FileSortUtil.sort(results, _sortMode.value, _sortAscending.value)
            if (isActive) _isSearchLoading.value = false
        }
    }

    // =========================================================================
    //  收藏
    // =========================================================================

    private fun loadFavorites(): List<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())
            .orEmpty()
            .sortedBy { File(it).name.lowercase() }

    fun isFavorite(path: String): Boolean = path in _favorites.value

    fun toggleFavorite(path: String) {
        val current = _favorites.value.toMutableSet()
        val added = current.add(path)
        if (!added) current.remove(path)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        _favorites.value = current.sortedBy { File(it).name.lowercase() }
        if (added) _toastMessage.value = "已收藏"
    }

    // =========================================================================
    //  属性
    // =========================================================================

    fun showProperties(item: FileItem) {
        _propertiesTarget.value = item
        statsJob?.cancel()
        if (item.isDirectory) {
            _propertiesStats.value = DirStats(0L, 0, 0, finished = false)
            statsJob = viewModelScope.launch(Dispatchers.IO) {
                val (size, fileCount, dirCount) =
                    FileOperationUtil.computeStats(File(item.path)) { isActive }
                if (isActive) {
                    _propertiesStats.value = DirStats(size, fileCount, dirCount, finished = true)
                }
            }
        } else {
            _propertiesStats.value = DirStats(item.size, 1, 0, finished = true)
        }
    }

    fun closeProperties() {
        statsJob?.cancel()
        _propertiesTarget.value = null
        _propertiesStats.value = null
    }

    /** 清除轻提示 */
    fun clearToast() {
        _toastMessage.value = null
    }
}
