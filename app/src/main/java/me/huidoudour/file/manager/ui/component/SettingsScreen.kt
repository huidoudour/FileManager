package me.huidoudour.file.manager.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.util.CrashLogStore
import me.huidoudour.file.manager.viewmodel.ThemeMode
import java.io.File

/**
 * 设置页 — 侧栏快捷目录(子菜单对话框) + 外观(主题 / 缩略图 / 隐藏文件)
 *
 * @param hiddenQuickDirs 当前被隐藏的快捷目录 id 集合
 * @param themeMode 当前主题模式 (跟随系统 / 浅色 / 深色)
 * @param showThumbnails 是否加载图片/视频缩略图
 * @param showHidden 是否显示隐藏文件
 * @param onToggleQuickDir 切换某个快捷目录的显示/隐藏 (id, 是否隐藏)
 * @param onThemeModeChange 切换主题模式
 * @param onShowThumbnailsChange 切换缩略图加载
 * @param onShowHiddenChange 切换隐藏文件显示
 * @param onBack 返回主界面
 */
@Composable
fun SettingsScreen(
    hiddenQuickDirs: Set<String>,
    themeMode: ThemeMode,
    showThumbnails: Boolean,
    showHidden: Boolean,
    onToggleQuickDir: (String, Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onShowThumbnailsChange: (Boolean) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val quickDirs = remember { buildAllQuickDirs() }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSidebarDialog by remember { mutableStateOf(false) }
    var showKeystorePage by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var crashLogs by remember { mutableStateOf<List<CrashLogStore.CrashLog>>(emptyList()) }
    var showCrashLogsDialog by remember { mutableStateOf(false) }
    var selectedCrashLog by remember { mutableStateOf<CrashLogStore.CrashLog?>(null) }

    // 读取崩溃日志涉及磁盘 I/O，放到后台线程避免阻塞设置页打开
    LaunchedEffect(Unit) {
        crashLogs = withContext(Dispatchers.IO) { CrashLogStore.list(context) }
    }

    // 签名密钥管理子页面
    if (showKeystorePage) {
        KeystoreScreen(onBack = { showKeystorePage = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(R.string.sidebar_special_folders) {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.ViewSidebar,
                    title = stringResource(R.string.sidebar_special_folders),
                    subtitle = stringResource(R.string.settings_sidebar_dirs_desc),
                    onClick = { showSidebarDialog = true },
                    showDivider = false,
                    trailing = { ChevronTrailing() }
                )
            }

            SettingsSection(R.string.settings_section_appearance) {
                SettingsItem(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(R.string.settings_theme_mode),
                    subtitle = stringResource(themeModeLabelRes(themeMode)),
                    onClick = { showThemeDialog = true },
                    trailing = { ChevronTrailing() }
                )
                SettingsItem(
                    icon = Icons.Filled.Image,
                    title = stringResource(R.string.settings_show_thumbnails),
                    subtitle = stringResource(R.string.settings_show_thumbnails_desc),
                    onClick = { onShowThumbnailsChange(!showThumbnails) },
                    trailing = {
                        Switch(
                            checked = showThumbnails,
                            onCheckedChange = onShowThumbnailsChange
                        )
                    }
                )
                SettingsItem(
                    icon = Icons.Filled.Visibility,
                    title = stringResource(R.string.show_hidden_files),
                    subtitle = stringResource(R.string.settings_show_hidden_desc),
                    onClick = { onShowHiddenChange(!showHidden) },
                    showDivider = false,
                    trailing = {
                        Switch(
                            checked = showHidden,
                            onCheckedChange = onShowHiddenChange
                        )
                    }
                )
            }

            SettingsSection(R.string.settings_section_signing) {
                SettingsItem(
                    icon = Icons.Filled.VpnKey,
                    title = stringResource(R.string.settings_keystore_manager),
                    subtitle = stringResource(R.string.settings_keystore_manager_desc),
                    onClick = { showKeystorePage = true },
                    showDivider = false,
                    trailing = { ChevronTrailing() }
                )
            }

            SettingsSection(R.string.settings_section_diagnostics) {
                SettingsItem(
                    icon = Icons.Filled.BugReport,
                    title = stringResource(R.string.crash_logs_title),
                    subtitle = if (crashLogs.isEmpty()) {
                        stringResource(R.string.crash_none)
                    } else {
                        stringResource(R.string.crash_settings_desc_count, crashLogs.size)
                    },
                    onClick = { showCrashLogsDialog = true },
                    showDivider = false,
                    trailing = { ChevronTrailing() }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showThemeDialog) {
            ThemeModeDialog(
                current = themeMode,
                onSelect = {
                    onThemeModeChange(it)
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false }
            )
        }

        if (showSidebarDialog) {
            SidebarDirsDialog(
                quickDirs = quickDirs,
                hiddenQuickDirs = hiddenQuickDirs,
                onToggleQuickDir = onToggleQuickDir,
                onDismiss = { showSidebarDialog = false }
            )
        }

        if (showCrashLogsDialog) {
            CrashLogListDialog(
                logs = crashLogs,
                onSelect = { selectedCrashLog = it },
                onDelete = { log ->
                    if (CrashLogStore.delete(log)) {
                        crashLogs = crashLogs.filterNot {
                            it.file.absolutePath == log.file.absolutePath
                        }
                    }
                },
                onClearAll = {
                    CrashLogStore.clear(context)
                    crashLogs = emptyList()
                    showCrashLogsDialog = false
                },
                onDismiss = { showCrashLogsDialog = false }
            )
        }

        selectedCrashLog?.let { log ->
            CrashReportDialog(
                log = log,
                onDelete = {
                    if (CrashLogStore.delete(log)) {
                        crashLogs = crashLogs.filterNot {
                            it.file.absolutePath == log.file.absolutePath
                        }
                    }
                    selectedCrashLog = null
                },
                onDismiss = { selectedCrashLog = null }
            )
        }
    }
}

/** 分区标题 */
@Composable
private fun SectionHeader(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** 一组设置项 —— MD3 填充式圆角卡片容器 */
@Composable
private fun SettingsSection(
    @StringRes titleRes: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(titleRes)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(content = content)
        }
    }
}

/** 单条设置项：圆形图标徽章 + 标题/描述 + 尾部控件 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconBadge(icon)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

/** MD3 圆形图标徽章 */
@Composable
private fun SettingsIconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 右侧折叠箭头 */
@Composable
private fun ChevronTrailing() {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 主题模式文案 */
@StringRes
private fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** 主题模式图标 */
private fun themeModeIcon(mode: ThemeMode): ImageVector = when (mode) {
    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.DARK -> Icons.Filled.DarkMode
}

/** 主题模式选择对话框 */
@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme_mode)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val selected = mode == current
                    val container = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    }
                    val content = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(container)
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = themeModeIcon(mode),
                            contentDescription = null,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(themeModeLabelRes(mode)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = content,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(mode) }
                        )
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

/** 侧栏快捷目录选择对话框 (子菜单) */
@Composable
private fun SidebarDirsDialog(
    quickDirs: List<QuickDir>,
    hiddenQuickDirs: Set<String>,
    onToggleQuickDir: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sidebar_special_folders)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.settings_change_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                quickDirs.forEach { dir ->
                    val exists = remember(dir.path) { File(dir.path).exists() }
                    val hidden = dir.id in hiddenQuickDirs
                    val badgeContainer = if (exists) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                    val badgeContent = if (exists) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = exists) {
                                onToggleQuickDir(dir.id, !hidden)
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(badgeContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = dir.icon,
                                contentDescription = null,
                                tint = badgeContent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(dir.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (exists) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            if (!exists) {
                                Text(
                                    text = stringResource(R.string.not_available),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Switch(
                            checked = !hidden,
                            onCheckedChange = { checked -> onToggleQuickDir(dir.id, !checked) },
                            enabled = exists
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}
