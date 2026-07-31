package me.huidoudour.file.manager.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("新名称") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty() && name.trim() != item.name
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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
        title = { Text(if (isFolder) "新建文件夹" else "新建文件") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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
        title = { Text("删除") },
        text = {
            val desc = if (items.size == 1) "\"${items.first().name}\""
            else "这 ${items.size} 项"
            Text("确定删除 $desc 吗？此操作不可恢复。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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
        title = { Text("存在同名文件") },
        text = {
            Column {
                Text("目标目录中已存在 ${conflictNames.size} 个同名项:")
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
                        text = "… 等 ${conflictNames.size} 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text("覆盖", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text("跳过") }
                TextButton(onClick = onCancel) { Text("取消") }
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
        title = { Text("属性") },
        text = {
            Column {
                PropertyRow("名称", item.name)
                PropertyRow("路径", item.path)
                PropertyRow("类型", FileTypeUtil.getCategory(item).label)
                if (stats != null && !stats.finished) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PropertyRow("大小", "计算中")
                        Spacer(modifier = Modifier.width(6.dp))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(14.dp)
                                .height(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    PropertyRow("大小", FileItem.formatSize(stats?.size ?: item.size))
                    if (item.isDirectory && stats != null) {
                        PropertyRow("包含", "${stats.fileCount} 个文件, ${stats.dirCount} 个文件夹")
                    }
                }
                PropertyRow("修改时间", formatDate(item.lastModified))
                PropertyRow(
                    "权限",
                    buildList {
                        if (item.canRead) add("可读")
                        if (item.canWrite) add("可写")
                        if (item.isHidden) add("隐藏")
                    }.joinToString(" / ").ifEmpty { "无" }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
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
