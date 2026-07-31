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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.ui.component.FileListScreen
import me.huidoudour.file.manager.ui.theme.FileManagerTheme
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: FileManagerViewModel
    private var isPickerMode = false

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
            FileManagerTheme {
                var selectedFile by remember { mutableStateOf<FileItem?>(null) }

                FileListScreen(
                    viewModel = viewModel,
                    isPickerMode = isPickerMode,
                    selectedFile = selectedFile,
                    onFileSelected = { file ->
                        if (isPickerMode) {
                            selectedFile = file
                        } else {
                            // 非选取模式点击文件：尝试用其他 App 打开
                            openFile(file)
                        }
                    },
                    onPickConfirmed = { file ->
                        returnFileToCaller(file)
                    },
                    onPickCancelled = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onShareFiles = { files ->
                        shareFiles(files)
                    },
                    canGoBack = isPickerMode
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * 处理 Intent，判断是否是文件选取模式
     */
    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val action = it.action
            if (action == Intent.ACTION_OPEN_DOCUMENT ||
                action == Intent.ACTION_GET_CONTENT ||
                action == Intent.ACTION_PICK
            ) {
                isPickerMode = true
                viewModel.setPickerMode(true)
            }
        }
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
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    companion object {
        /** APK 安装器包名 */
        private const val INSTALLER_PACKAGE = "io.github.huidoudour.Installer"
    }

    /**
     * 分享文件到其他 App (仅支持文件, 文件夹会被过滤)
     */
    private fun shareFiles(items: List<FileItem>) {
        val files = items.filter { !it.isDirectory }.map { File(it.path) }.filter { it.exists() }
        if (files.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_no_files), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uris = ArrayList<Uri>(files.map { file ->
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            })
            val intent = if (uris.size == 1) {
                val item = items.first { !it.isDirectory }
                Intent(Intent.ACTION_SEND).apply {
                    type = me.huidoudour.file.manager.util.FileTypeUtil.getMimeType(item)
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
