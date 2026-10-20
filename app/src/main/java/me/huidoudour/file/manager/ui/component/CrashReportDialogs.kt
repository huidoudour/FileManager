package me.huidoudour.file.manager.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.util.CrashLogStore
import me.huidoudour.file.manager.util.CrashLogStore.CrashLog

/**
 * 单条崩溃报告详情对话框。
 *
 * @param log 要展示的崩溃报告
 * @param titleRes 标题
 * @param showIntro 是否显示"应用上次运行异常"的说明（启动弹窗使用）
 * @param onDelete 删除该报告
 * @param onDismiss 关闭对话框
 */
@Composable
fun CrashReportDialog(
    log: CrashLog,
    @StringRes titleRes: Int = R.string.crash_detail_title,
    showIntro: Boolean = false,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (showIntro) {
                    Text(
                        text = stringResource(R.string.crash_dialog_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                InfoLine(R.string.crash_info_time, log.crashTime)
                InfoLine(R.string.crash_info_version, log.versionName)
                InfoLine(
                    R.string.crash_info_device,
                    "${log.phoneModel} / Android ${log.androidVersion}"
                )
                InfoLine(R.string.crash_info_source, crashSourceLabel(log.source))
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = stringResource(R.string.crash_info_stack),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = log.stackTrace,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete))
                }
                TextButton(onClick = { CrashLogStore.share(context, log) }) {
                    Text(stringResource(R.string.action_share))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    )
}

/**
 * 崩溃日志列表对话框（设置页入口）。
 *
 * @param logs 崩溃报告列表
 * @param onSelect 查看某条报告详情
 * @param onDelete 删除某条报告
 * @param onClearAll 清空全部报告
 * @param onDismiss 关闭对话框
 */
@Composable
fun CrashLogListDialog(
    logs: List<CrashLog>,
    onSelect: (CrashLog) -> Unit,
    onDelete: (CrashLog) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_logs_title)) },
        text = {
            if (logs.isEmpty()) {
                Text(
                    text = stringResource(R.string.crash_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(logs, key = { it.file.absolutePath }) { log ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(log) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = log.exceptionSummary.ifBlank {
                                    context.getString(R.string.unknown_error)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${log.crashTime}  ${log.versionName}  ·  " +
                                        crashSourceLabel(log.source),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { onDelete(log) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = {
            if (logs.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.crash_clear_all))
                }
            }
        }
    )
}

/** 详情中的一行键值信息 */
@Composable
private fun InfoLine(@StringRes labelRes: Int, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 崩溃报告来源文案 */
@Composable
private fun crashSourceLabel(source: CrashLogStore.Source): String = stringResource(
    when (source) {
        CrashLogStore.Source.ACRA -> R.string.crash_source_acra
        CrashLogStore.Source.XCRASH -> R.string.crash_source_xcrash
    }
)
