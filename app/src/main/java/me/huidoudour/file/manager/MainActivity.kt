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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.ui.anim.ExitStyle
import me.huidoudour.file.manager.ui.anim.ExitTransitionHost
import me.huidoudour.file.manager.ui.component.FileListScreen
import me.huidoudour.file.manager.ui.theme.FileManagerTheme
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import me.huidoudour.file.manager.viewmodel.ThemeMode
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: FileManagerViewModel
    private var isPickerMode = false
    private var isSaveMode = false

    /**
     * 退出过渡动画状态。为 null 表示当前未在退出；
     * 一旦被赋值，界面会播放退场动画，动画结束后才真正 finish()。
     */
    private var exitStyle by mutableStateOf<ExitStyle?>(null)

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
                // 退出过渡动画：
                //  - 点击"取消 / 选取文件完成 / 保存完成"等主动退出 → 播放自定义淡出动画
                //  - 返回手势→ 根目录时不再拦截返回，交给系统播放预测性返回动画（随手指缩放）
                ExitTransitionHost(
                    exitStyle = exitStyle,
                    onExitFinished = { finishAfterExitAnimation() }
                ) {
                    FileListScreen(
                        viewModel = viewModel,
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
                            startExit(ExitStyle.CANCEL)
                        },
                        onSaveConfirmed = {
                            performFileSave()
                        },
                        onSaveCancelled = {
                            viewModel.clearSaveData()
                            setResult(RESULT_CANCELED)
                            startExit(ExitStyle.CANCEL)
                        },
                        onShareFiles = { files ->
                            shareFiles(files)
                        },
                        onOpenWith = ::openFile,
                        onCreateShortcut = { path ->
                            pinShortcut(path)
                        },
                        onExitApp = {
                            // 主动退出：走统一的退场动画，动画结束后 finish()
                            startExit(ExitStyle.EXIT)
                        }
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
     * 处理 Intent：文件选取/保存模式，或桌面快捷方式打开指定目录
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
                ACTION_OPEN_PATH -> {
                    // 桌面快捷方式：打开指定目录
                    it.getStringExtra(EXTRA_PATH)?.let { path -> viewModel.loadDirectory(path) }
                }
            }
        }
    }

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
            startExit(ExitStyle.CANCEL)
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
            startExit(ExitStyle.RETURN_RESULT)
        } else {
            setResult(RESULT_CANCELED)
            startExit(ExitStyle.CANCEL)
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
                startExit(ExitStyle.RETURN_RESULT)
            } else {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.save_failed, ""),
                    Toast.LENGTH_SHORT
                ).show()
                setResult(RESULT_CANCELED)
                startExit(ExitStyle.CANCEL)
            }
        }
    }

    /**
     * 触发退出过渡动画。
     *
     * 这里只负责切换状态，真正的 `finish()` 会在动画播放结束后由
     * [finishAfterExitAnimation] 执行。重复调用会被忽略，避免动画被打断或重复播放。
     */
    private fun startExit(style: ExitStyle) {
        if (exitStyle != null) return
        exitStyle = style
    }

    /**
     * 退出动画播放完毕后的收尾工作：关闭当前 Activity。
     *
     * 关闭的同时给窗口本身加一段淡出转场，让调用方的界面从下方柔和地浮现出来，
     * 而不是等界面内容淡出后窗口突然消失（否则会出现"闪一下底色"的观感）。
     */
    private fun finishAfterExitAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 使用新的窗口动画 API
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.window_exit_fade)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.window_exit_fade)
        }
        finish()
    }

    companion object {
        /** APK 安装器包名 */
        private const val INSTALLER_PACKAGE = "io.github.huidoudour.Installer"

        /** 通过 Intent 打开指定目录的 Action (桌面快捷方式使用) */
        private const val ACTION_OPEN_PATH = "me.huidoudour.file.manager.action.OPEN_PATH"

        /** 目标目录路径 Extra */
        private const val EXTRA_PATH = "me.huidoudour.file.manager.extra.PATH"

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
     * 为指定目录创建桌面快捷方式 (照搬 MaterialFiles 的 createShortcut)
     */
    private fun pinShortcut(path: String) {
        val shortcut = ShortcutInfoCompat.Builder(this, path)
            .setShortLabel(File(path).name.ifEmpty { path })
            .setIntent(
                Intent(this, MainActivity::class.java).apply {
                    action = ACTION_OPEN_PATH
                    putExtra(EXTRA_PATH, path)
                }
            )
            .setIcon(IconCompat.createWithResource(this, R.mipmap.directory_shortcut_icon))
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, getString(R.string.shortcut_created), Toast.LENGTH_SHORT).show()
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
