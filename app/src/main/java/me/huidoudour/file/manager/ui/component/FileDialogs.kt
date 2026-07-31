package me.huidoudour.file.manager.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.viewmodel.DirStats
import me.huidoudour.file.manager.viewmodel.OperationProgress

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
        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
        title = { Text(stringResource(R.string.action_properties)) },
        text = {
            Column {
                PropertyRow(stringResource(R.string.name), item.name)
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
