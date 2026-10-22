package me.huidoudour.file.manager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.ui.component.CrashReportDialog
import me.huidoudour.file.manager.ui.component.FileListScreen
import me.huidoudour.file.manager.ui.theme.FileManagerTheme
import me.huidoudour.file.manager.util.CrashLogStore
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import me.huidoudour.file.manager.viewmodel.ThemeMode
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: FileManagerViewModel
    private var isPickerMode = false
    private var isSaveMode = false

    /**
     * 待打开的音频播放界面请求 (点击系统媒体通知回到应用时设置)；
     * 由 FileListScreen 消费后清空。
     */
    private var openPlayerRequest by mutableStateOf<FileItem?>(null)

    /**
     * 上次运行产生的、尚未提示过的崩溃报告。
     * 在 setContent 中异步读取 ACRA 写入的日志文件，避免阻塞启动。
     */
    private var pendingCrashReport by mutableStateOf<CrashLogStore.CrashLog?>(null)

    // 权限请求 launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                viewModel.refresh()
                Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化 ViewModel
        viewModel = ViewModelProvider(this)[FileManagerViewModel::class.java]

        // 检查是否是被其他 App 调用的文件选取模式
        handleIntent(intent)

        // 请求权限
        requestStoragePermissions()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            FileManagerTheme(darkTheme = darkTheme) {
                FileListScreen(
                    viewModel = viewModel,
                    openPlayerRequest = openPlayerRequest,
                    onOpenPlayerRequestConsumed = { openPlayerRequest = null },
                    onFileSelected = { file ->
                        if (isPickerMode) {
                            returnFileToCaller(file)
                        } else {
                            // 非选取模式点击文件：尝试用其他 App 打开
                            openFile(file)
                        }
                    },
                    onPickCancelled = {
                        setResult(RESULT_CANCELED)
                        finishWithSlideBack()
                    },
                    onSaveConfirmed = {
                        performFileSave()
                    },
                    onSaveCancelled = {
                        viewModel.clearSaveData()
                        setResult(RESULT_CANCELED)
                        finishWithSlideBack()
                    },
                    onShareFiles = { files ->
                        shareFiles(files)
                    },
                    onOpenWith = ::openFile,
                    onExitApp = {
                        // 主动退出：与返回调用方一致，整体向右滑出
                        finishWithSlideBack()
                    }
                )

                // 启动时异步读取最近一次未提示的崩溃报告 (ACRA 在崩溃时落盘)
                LaunchedEffect(Unit) {
                    // 被其他应用调起选取/保存文件时不打断调用方
                    if (isPickerMode || isSaveMode) return@LaunchedEffect
                    pendingCrashReport = withContext(Dispatchers.IO) {
                        CrashLogStore.latestUnseen(this@MainActivity)
                    }
                }
                pendingCrashReport?.let { log ->
                    CrashReportDialog(
                        log = log,
                        titleRes = R.string.crash_dialog_title,
                        showIntro = true,
                        onDelete = {
                            CrashLogStore.delete(log)
                            pendingCrashReport = null
                        },
                        onDismiss = { pendingCrashReport = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * 处理 Intent：文件选取/保存模式等
     */
    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val action = it.action
            when (action) {
                Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                    handleSendIntent(it)
                }
                Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK -> {
                    isPickerMode = true
                    viewModel.setPickerMode(true)
                }
                ACTION_OPEN_PLAYER -> {
                    // 系统媒体通知：回到应用并重新打开音频播放界面
                    requestOpenAudioPlayer()
                }
            }
        }
    }

    /**
     * 点击系统媒体通知回到应用时：恢复音频播放界面。
     *
     * 后台播放的当前曲目路径由 [me.huidoudour.file.manager.playback.AudioPlaybackService]
     * 写入偏好设置，这里读取并转为请求交给 FileListScreen 打开播放对话框 (无缝续播当前曲目)。
     */
    private fun requestOpenAudioPlayer() {
        val path = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_AUDIO_CURRENT_PATH, null) ?: return
        val file = File(path)
        if (!file.isFile) return
        openPlayerRequest = file.toFileItem()
    }

    /** File -> FileItem 转换 (与 ViewModel / 播放对话框保持一致) */
    private fun File.toFileItem(): FileItem = FileItem(
        name = name,
        path = absolutePath,
        parentPath = parent ?: "",
        isDirectory = isDirectory,
        size = if (isFile) length() else 0L,
        lastModified = lastModified(),
        extension = if (isFile) extension else "",
        canRead = canRead(),
        canWrite = canWrite(),
        isHidden = isHidden
    )

    /**
     * 处理 ACTION_SEND / ACTION_SEND_MULTIPLE 分享意图
     */
    private fun handleSendIntent(intent: Intent) {
        isSaveMode = true
        viewModel.setSaveMode(true)

        val uris = mutableListOf<Uri>()
        var textContent: String? = null

        when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            }
            Intent.ACTION_SEND -> {
                // 尝试获取文件 URI
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
                // 如果分享的是文本内容 (无文件 URI)
                if (uris.isEmpty()) {
                    textContent = intent.getStringExtra(Intent.EXTRA_TEXT)
                }
            }
        }

        // 兼容部分 App 通过 ClipData 传递文件 Uri (不放入 EXTRA_STREAM)
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris.add(it) }
            }
        }

        if (uris.isEmpty() && textContent.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.save_failed, "未接收到可保存的内容"), Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finishWithSlideBack()
            return
        }

        viewModel.setSaveData(uris, textContent)
    }

    /**
     * 将选中的文件返回给调用方
     */
    private fun returnFileToCaller(fileItem: FileItem) {
        val file = File(fileItem.path)
        if (file.exists()) {
            try {
                val uri: Uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                val resultIntent = Intent().apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                setResult(RESULT_OK, resultIntent)
            } catch (_: Exception) {
                // 降级：直接用文件路径的 Uri
                val uri = Uri.fromFile(file)
                val resultIntent = Intent().apply {
                    data = uri
                }
                setResult(RESULT_OK, resultIntent)
            }
            finishWithSlideBack()
        } else {
            setResult(RESULT_CANCELED)
            finishWithSlideBack()
        }
    }

    /**
     * 执行保存操作：将分享的文件写入当前浏览的目录
     */
    private fun performFileSave() {
        val destDir = viewModel.currentPath.value
        viewModel.viewModelScope.launch {
            val savedCount = viewModel.performSave()
            viewModel.clearSaveData()
            viewModel.refresh()
            if (savedCount > 0) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.save_success, File(destDir).name),
                    Toast.LENGTH_SHORT
                ).show()
                setResult(RESULT_OK)
                finishWithSlideBack()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.save_failed, ""),
                    Toast.LENGTH_SHORT
                ).show()
                setResult(RESULT_CANCELED)
                finishWithSlideBack()
            }
        }
    }

    /**
     * 以"从左往右滑动"的返回动画关闭当前界面。
     *
     * 关闭时让本界面整体向右滑出屏幕、调用方界面从左侧滑入，
     * 也就是系统标准的 Activity 返回转场（不再使用自定义缩放/淡出）。
     */
    private fun finishWithSlideBack() {
        if (isFinishing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 使用新的窗口动画 API
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            finish()
        } else {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    companion object {
        /** APK 安装器包名 */
        private const val INSTALLER_PACKAGE = "io.github.huidoudour.Installer"

        /** 点击系统媒体通知重新打开播放界面的 Action (由 AudioPlaybackService 使用) */
        const val ACTION_OPEN_PLAYER = "me.huidoudour.file.manager.action.OPEN_PLAYER"

        /** 偏好文件名 (与 ViewModel / 播放对话框共用) */
        private const val PREFS_NAME = "file_manager_prefs"

        /** 后台播放当前曲目路径的偏好键 (由 AudioPlaybackService 写入) */
        private const val KEY_AUDIO_CURRENT_PATH = "audio_current_path"

        /** 目录的 MIME 类型 (照搬 MaterialFiles 的 MimeType.DIRECTORY) */
        private const val DIRECTORY_MIME_TYPE = "resource/folder"
    }

    /**
     * 分享文件或文件夹到其他 App (照搬 MaterialFiles 的 share())
     */
    private fun shareFiles(items: List<FileItem>) {
        val targets = items.filter { File(it.path).exists() }
        if (targets.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_no_files), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uris = ArrayList<Uri>(targets.map { item ->
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(item.path))
            })
            val intent = if (uris.size == 1) {
                val item = targets.first()
                Intent(Intent.ACTION_SEND).apply {
                    type = if (item.isDirectory) DIRECTORY_MIME_TYPE
                    else me.huidoudour.file.manager.util.FileTypeUtil.getMimeType(item)
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, getString(R.string.share_files_title, uris.size)))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.share_failed, e.message ?: ""),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 用其他 App 打开文件
     */
    private fun openFile(fileItem: FileItem) {
        if (fileItem.isDirectory) {
            viewModel.navigateToDirectory(fileItem)
            return
        }
        val file = File(fileItem.path)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        // APK 文件：仅通过指定安装器安装
        if (fileItem.extension.equals("apk", ignoreCase = true)) {
            installApk(file)
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val mimeType = me.huidoudour.file.manager.util.FileTypeUtil.getMimeType(fileItem)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, getString(R.string.no_app_to_open), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.open_file_failed, e.message ?: ""),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 使用指定安装器 io.github.huidoudour.Installer 安装 APK
     */
    private fun installApk(file: File) {
        try {
            // 先确认安装器包是否存在
            try {
                packageManager.getPackageInfo(INSTALLER_PACKAGE, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                Toast.makeText(
                    this,
                    getString(R.string.installer_required),
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            // 构建 ACTION_VIEW Intent，锁定到指定安装器
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 仅允许 io.github.huidoudour.Installer 响应
                setPackage(INSTALLER_PACKAGE)
            }

            // 授权 URI 给安装器
            grantUriPermission(
                INSTALLER_PACKAGE,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            startActivity(intent)
        } catch (e: Exception) {
            val msg = if (e is android.content.ActivityNotFoundException) {
                getString(R.string.installer_unsupported)
            } else {
                getString(R.string.install_failed, e.message ?: "")
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 请求存储权限
     */
    private fun requestStoragePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        val needsPermission = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 全文件访问权限
                if (!Environment.isExternalStorageManager()) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = "package:$packageName".toUri()
                    }
                    startActivity(intent)
                }
            } else {
                requestPermissionLauncher.launch(permissions)
            }
        }
    }
}
