package me.huidoudour.file.manager.ui.component

import android.os.Environment
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.ui.theme.FileTintFolder
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File

// =============================================================================
//  侧边栏抽屉: 快捷目录 + 收藏 (照搬 MaterialFiles 的 NavigationView 分组列表)
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
    showHidden: Boolean,
    hiddenQuickDirs: Set<String>,
    onNavigate: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onToggleShowHidden: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val allQuickDirs = remember { buildAllQuickDirs() }
    val quickDirs = allQuickDirs.filter { it.id !in hiddenQuickDirs && File(it.path).exists() }

    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            // ---- 位置 (标准目录组) ----
            quickDirs.forEach { dir ->
                DrawerItem(
                    icon = dir.icon,
                    title = stringResource(dir.labelRes),
                    selected = currentPath == dir.path,
                    onClick = { onNavigate(dir.path) }
                )
            }

            DrawerDivider()

            // ---- 收藏 ----
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
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

            DrawerDivider()

            // ---- 菜单组 (显示隐藏文件 / 设置) ----
            DrawerItem(
                icon = if (showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                title = stringResource(
                    if (showHidden) R.string.hide_hidden_files
                    else R.string.show_hidden_files
                ),
                onClick = onToggleShowHidden
            )
            DrawerItem(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.settings),
                onClick = onOpenSettings
            )
        }
    }
}

/**
 * 抽屉列表项 (照搬 MaterialFiles 的 navigation_item.xml: 48dp 高, 图标 + 单行标题)
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = when {
                iconTint != Color.Unspecified -> iconTint
                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = when {
                titleColor != Color.Unspecified -> titleColor
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 分组分隔线 (照搬 MaterialFiles 的 navigation_divider_item.xml)
 */
@Composable
private fun DrawerDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
