package me.huidoudour.file.manager.viewmodel

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.FileSortUtil
import me.huidoudour.file.manager.util.SortMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** 存储根路径 */
        val STORAGE_ROOT = Environment.getExternalStorageDirectory().absolutePath
        /** 日期格式化 */
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        /** SharedPreferences 键 */
        private const val PREFS_NAME = "file_manager_prefs"
        private const val KEY_LAST_PATH = "last_path"
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
                    dir.listFiles()?.map { file ->
                        FileItem(
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
                    } ?: emptyList()
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
            val previousPath = _navigationHistory.removeFirst()
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
}
