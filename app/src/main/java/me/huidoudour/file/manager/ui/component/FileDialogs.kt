package me.huidoudour.file.manager.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.signing.ApkSignTask
import me.huidoudour.file.manager.signing.KeystoreConfig
import me.huidoudour.file.manager.signing.KeystoreRepository
import me.huidoudour.file.manager.signing.SignResult
import me.huidoudour.file.manager.signing.SignStage
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.viewmodel.DirStats
import me.huidoudour.file.manager.viewmodel.OperationProgress
import java.io.File

// =============================================================================
//  文件操作相关对话框集合
// =============================================================================

/** 重命名对话框 */
@Composable
fun RenameDialog(
    item: FileItem,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.new_name)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty() && name.trim() != item.name
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** 新建文件夹/文件对话框 */
@Composable
fun CreateItemDialog(
    isFolder: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isFolder) Icons.Filled.CreateNewFolder
                else Icons.AutoMirrored.Filled.NoteAdd,
                contentDescription = null
            )
        },
        title = { Text(stringResource(if (isFolder) R.string.create_folder else R.string.create_file)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** 跳转到路径对话框 (照搬 MaterialFiles 的 NavigateToPathDialogFragment) */
@Composable
fun NavigateToPathDialog(
    currentPath: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var path by remember { mutableStateOf(currentPath) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
        title = { Text(stringResource(R.string.navigate_to)) },
        text = {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                singleLine = true,
                label = { Text(stringResource(R.string.navigate_to_path_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(path.trim()) },
                enabled = path.trim().isNotEmpty() && path.trim() != currentPath
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** 删除确认对话框 */
@Composable
fun DeleteConfirmDialog(
    items: List<FileItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.action_delete)) },
        text = {
            val desc = if (items.size == 1) "\"${items.first().name}\""
            else stringResource(R.string.delete_desc_multiple, items.size)
            Text(stringResource(R.string.delete_confirm_message, desc))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** 粘贴冲突对话框 */
@Composable
fun ConflictDialog(
    conflictNames: List<String>,
    onOverwrite: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = { Text(stringResource(R.string.conflict_title)) },
        text = {
            Column {
                Text(stringResource(R.string.conflict_message, conflictNames.size))
                Spacer(modifier = Modifier.height(6.dp))
                conflictNames.take(5).forEach { name ->
                    Text(
                        text = "• $name",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (conflictNames.size > 5) {
                    Text(
                        text = stringResource(R.string.conflict_more, conflictNames.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text(stringResource(R.string.overwrite), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.skip)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

/** 操作进度对话框 (不可取消关闭) */
@Composable
fun OperationProgressDialog(progress: OperationProgress) {
    AlertDialog(
        onDismissRequest = { /* 操作进行中不允许关闭 */ },
        title = { Text(progress.title) },
        text = {
            Column {
                Text(
                    text = progress.currentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.processed / progress.total.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${progress.processed} / ${progress.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {}
    )
}

/** 属性对话框 */
@Composable
fun PropertiesDialog(
    item: FileItem,
    stats: DirStats?,
    formatDate: (Long) -> String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // 标题直接显示文件/文件夹名称 (不再展示图标与重复的"名称"行)
        title = {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                PropertyRow(stringResource(R.string.prop_path), item.path)
                PropertyRow(
                    stringResource(R.string.prop_type),
                    stringResource(FileTypeUtil.getCategory(item).labelRes)
                )
                if (stats != null && !stats.finished) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PropertyRow(stringResource(R.string.prop_size), stringResource(R.string.calculating))
                        Spacer(modifier = Modifier.width(6.dp))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(14.dp)
                                .height(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    PropertyRow(stringResource(R.string.prop_size), FileItem.formatSize(stats?.size ?: item.size))
                    if (item.isDirectory && stats != null) {
                        PropertyRow(
                            stringResource(R.string.prop_contains),
                            stringResource(R.string.contains_counts, stats.fileCount, stats.dirCount)
                        )
                    }
                }
                PropertyRow(stringResource(R.string.prop_modified), formatDate(item.lastModified))
                PropertyRow(
                    stringResource(R.string.prop_permissions),
                    buildList {
                        if (item.canRead) add(stringResource(R.string.perm_readable))
                        if (item.canWrite) add(stringResource(R.string.perm_writable))
                        if (item.isHidden) add(stringResource(R.string.perm_hidden))
                    }.joinToString(" / ").ifEmpty { stringResource(R.string.none) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(64.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * APK 签名对话框。
 *
 * 使用设置中管理的密钥库在应用内完成签名，可选 V1/V2/V3 方案；
 * 产物默认写在原 APK 同目录 (xxx-signed.apk)，目录不可写时退到 Download/ApkSign/。
 * 签名成功后可直接调起安装器，也可关闭对话框后自行处理产物。
 */
@Composable
fun ApkSignDialog(
    item: FileItem,
    onInstall: (File) -> Unit,
    onSigned: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { KeystoreRepository(context) }

    // 已保存的密钥列表；null 表示仍在读取
    var configs by remember { mutableStateOf<List<KeystoreConfig>?>(null) }
    var selectedKey by remember { mutableStateOf<KeystoreConfig?>(null) }
    var showKeyPicker by remember { mutableStateOf(false) }

    var v1 by remember { mutableStateOf(true) }
    var v2 by remember { mutableStateOf(true) }
    var v3 by remember { mutableStateOf(true) }

    // stage 非空即表示正在签名，用它同时驱动进度显示与交互禁用
    var stage by remember { mutableStateOf<SignStage?>(null) }
    var result by remember { mutableStateOf<SignResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val running = stage != null

    LaunchedEffect(Unit) {
        val (list, lastUsedId) = withContext(Dispatchers.IO) {
            val loaded = runCatching { repository.loadAll() }.getOrDefault(emptyList())
            loaded to repository.lastUsedId
        }
        configs = list
        selectedKey = list.firstOrNull { it.id == lastUsedId } ?: list.firstOrNull()
    }

    AlertDialog(
        // 签名进行中不允许关闭, 避免中途退出后留下不完整的产物
        onDismissRequest = { if (!running) onDismiss() },
        icon = { Icon(Icons.Filled.Android, contentDescription = null) },
        title = { Text(stringResource(R.string.apk_sign_title)) },
        text = {
            Column {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = FileItem.formatSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.apk_sign_key_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !running && result == null && configs != null) {
                            showKeyPicker = true
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedKey?.name ?: if (configs == null) {
                                stringResource(R.string.apk_sign_key_loading)
                            } else {
                                stringResource(R.string.apk_sign_key_none)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        selectedKey?.let { key ->
                            Text(
                                text = "${key.type} · ${key.keyAlias}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedKey?.isBuiltin == true) {
                    Text(
                        text = stringResource(R.string.apk_sign_builtin_key_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // 方案选择只在开始前可改
                if (!running && result == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignSchemeCheckbox("V1", v1, enabled = !running) { v1 = it }
                        SignSchemeCheckbox("V2", v2, enabled = !running) { v2 = it }
                        SignSchemeCheckbox("V3", v3, enabled = !running) { v3 = it }
                    }
                }

                if (running) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (stage) {
                            SignStage.READING -> stringResource(R.string.apk_sign_stage_reading)
                            SignStage.SIGNING -> stringResource(R.string.apk_sign_stage_signing)
                            else -> stringResource(R.string.apk_sign_stage_writing)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                result?.let { signed ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.apk_sign_success, signed.elapsedMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.apk_sign_output, signed.output.absolutePath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                error?.let { message ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.apk_sign_failed, message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (result != null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.apk_sign_done)) }
            } else if (!running) {
                TextButton(
                    onClick = {
                        val config = selectedKey ?: return@TextButton
                        error = null
                        stage = SignStage.READING
                        val source = File(item.path)
                        scope.launch {
                            try {
                                val identity = repository.resolveIdentity(config)
                                val output = ApkSignTask.resolveOutput(source)
                                result = ApkSignTask.sign(context, source, output, v1, v2, v3, identity) {
                                    stage = it
                                }
                                repository.lastUsedId = config.id
                                onSigned()
                            } catch (e: Exception) {
                                error = e.message ?: e.toString()
                            } finally {
                                stage = null
                            }
                        }
                    },
                    // 至少要启用一种签名方案，且必须选中可用密钥
                    enabled = (v1 || v2 || v3) && selectedKey != null
                ) {
                    Text(stringResource(R.string.apk_sign_start))
                }
            }
        },
        dismissButton = {
            if (result != null) {
                TextButton(onClick = { result?.output?.let(onInstall) }) {
                    Text(stringResource(R.string.action_install))
                }
            } else {
                TextButton(onClick = onDismiss, enabled = !running) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )

    if (showKeyPicker) {
        KeyPickerDialog(
            configs = configs.orEmpty(),
            selected = selectedKey,
            onSelect = {
                selectedKey = it
                showKeyPicker = false
            },
            onDismiss = { showKeyPicker = false }
        )
    }
}

/** 签名密钥选择对话框 */
@Composable
private fun KeyPickerDialog(
    configs: List<KeystoreConfig>,
    selected: KeystoreConfig?,
    onSelect: (KeystoreConfig) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apk_sign_key_pick_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (configs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.apk_sign_key_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    configs.forEach { config ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(config) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = config.id == selected?.id,
                                onClick = { onSelect(config) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = config.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${config.type} · ${config.keyAlias}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** 签名方案勾选项 */
@Composable
private fun SignSchemeCheckbox(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.width(8.dp))
    }
}
