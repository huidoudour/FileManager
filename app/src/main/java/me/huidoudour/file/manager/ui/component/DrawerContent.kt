package me.huidoudour.file.manager.ui.component

import android.os.Environment
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.ui.theme.FileTintFolder
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File

// =============================================================================
//  侧边栏抽屉 (MD3: 品牌渐变头部 + 分组标签 + 胶囊式导航项 + 底部版本胶囊)
// =============================================================================

data class QuickDir(
    val id: String,
    @StringRes val labelRes: Int,
    val path: String,
    val icon: ImageVector
)

/** 常用快捷目录 (含实际不存在的, 用于设置页统一展示) */
fun buildAllQuickDirs(): List<QuickDir> {
    fun publicDir(type: String) =
        Environment.getExternalStoragePublicDirectory(type).absolutePath

    return listOf(
        QuickDir(FileManagerViewModel.QUICK_DIR_INTERNAL, R.string.internal_storage, FileManagerViewModel.storageRoot, Icons.Filled.PhoneAndroid),
        QuickDir(FileManagerViewModel.QUICK_DIR_DOWNLOADS, R.string.quick_downloads, publicDir(Environment.DIRECTORY_DOWNLOADS), Icons.Filled.Download),
        QuickDir(FileManagerViewModel.QUICK_DIR_CAMERA, R.string.quick_camera, publicDir(Environment.DIRECTORY_DCIM), Icons.Filled.PhotoCamera),
        QuickDir(FileManagerViewModel.QUICK_DIR_PICTURES, R.string.quick_pictures, publicDir(Environment.DIRECTORY_PICTURES), Icons.Filled.Image),
        QuickDir(FileManagerViewModel.QUICK_DIR_VIDEOS, R.string.quick_videos, publicDir(Environment.DIRECTORY_MOVIES), Icons.Filled.Movie),
        QuickDir(FileManagerViewModel.QUICK_DIR_MUSIC, R.string.quick_music, publicDir(Environment.DIRECTORY_MUSIC), Icons.Filled.MusicNote),
        QuickDir(FileManagerViewModel.QUICK_DIR_DOCUMENTS, R.string.quick_documents, publicDir(Environment.DIRECTORY_DOCUMENTS), Icons.Filled.Description)
    )
}

@Composable
fun DrawerContent(
    currentPath: String,
    favorites: List<String>,
    hiddenQuickDirs: Set<String>,
    onNavigate: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val allQuickDirs = remember { buildAllQuickDirs() }
    val quickDirs = allQuickDirs.filter { it.id !in hiddenQuickDirs && File(it.path).exists() }

    val context = LocalContext.current
    // 完整版本信息: 版本名 (versionName) + 版本号 (versionCode)
    val versionLabel = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "FileManager v${info.versionName ?: "1.0"} (${info.longVersionCode})"
        } catch (_: Exception) {
            "FileManager v1.0"
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // ---- 头部: 品牌渐变 + 应用标识 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.drawer_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.drawer_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
            }

            // ---- 列表区 (可滚动, 头部与底部版本信息固定) ----
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ---- 位置 (标准目录组) ----
                DrawerSectionLabel(R.string.section_locations)
                quickDirs.forEach { dir ->
                    DrawerItem(
                        icon = dir.icon,
                        title = stringResource(dir.labelRes),
                        selected = currentPath == dir.path,
                        onClick = { onNavigate(dir.path) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- 收藏 ----
                DrawerSectionLabel(R.string.section_favorites)
                if (favorites.isEmpty()) {
                    Text(
                        text = stringResource(R.string.favorites_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                } else {
                    favorites.forEach { path ->
                        val name = remember(path) { File(path).name.ifEmpty { path } }
                        val exists = remember(path) { File(path).exists() }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DrawerItem(
                                icon = Icons.Filled.Star,
                                title = name,
                                selected = currentPath == path,
                                enabled = exists,
                                iconTint = FileTintFolder,
                                titleColor = if (exists) Color.Unspecified
                                else MaterialTheme.colorScheme.error,
                                onClick = { onNavigate(path) },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemoveFavorite(path) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.remove_favorite),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- 显示 (设置) ----
                DrawerSectionLabel(R.string.section_display)
                DrawerItem(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.settings),
                    onClick = onOpenSettings
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ---- Footer: 版本信息胶囊 ----
            DrawerDivider()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 抽屉分组标签 (与设置页分区标题保持一致)
 */
@Composable
private fun DrawerSectionLabel(@StringRes labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp)
    )
}

/**
 * 抽屉菜单项 (MD3 胶囊造型)
 *
 * - 选中: NavigationDrawerItem (secondaryContainer 胶囊 + 强调文字)
 * - 未选中: 无描边的普通行, 仅保留水波纹反馈, 更贴合 MD3 导航抽屉
 * - iconTint / titleColor / enabled 保留给收藏项等定制场景使用
 */
@Composable
private fun DrawerItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    iconTint: Color = Color.Unspecified,
    titleColor: Color = Color.Unspecified
) {
    if (selected) {
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            selected = true,
            onClick = { if (enabled) onClick() },
            modifier = modifier
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedIconColor = if (iconTint == Color.Unspecified)
                    MaterialTheme.colorScheme.onSecondaryContainer else iconTint,
                selectedTextColor = if (titleColor == Color.Unspecified)
                    MaterialTheme.colorScheme.onSecondaryContainer else titleColor
            )
        )
    } else {
        Row(
            modifier = modifier
                .padding(horizontal = 12.dp, vertical = 1.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = when {
                    iconTint != Color.Unspecified -> iconTint
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    titleColor != Color.Unspecified -> titleColor
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 抽屉分隔线 (使用 MD3 outlineVariant)
 */
@Composable
private fun DrawerDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp
    )
}
