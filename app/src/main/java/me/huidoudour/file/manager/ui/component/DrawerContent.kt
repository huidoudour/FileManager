package me.huidoudour.file.manager.ui.component

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.ui.theme.FileTintFolder
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File

// =============================================================================
//  侧边栏抽屉: 快捷目录 + 收藏
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
        ) {
            // ---- 头部 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.drawer_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.drawer_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings)
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // ---- 快捷目录 ----
            SectionLabel(stringResource(R.string.section_locations))
            quickDirs.forEach { dir ->
                NavigationDrawerItem(
                    label = { Text(stringResource(dir.labelRes)) },
                    icon = { Icon(imageVector = dir.icon, contentDescription = null) },
                    selected = currentPath == dir.path,
                    onClick = { onNavigate(dir.path) },
                    colors = NavigationDrawerItemDefaults.colors(),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // ---- 收藏 ----
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            SectionLabel(stringResource(R.string.section_favorites))
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            } else {
                favorites.forEach { path ->
                    val exists = remember(path) { File(path).exists() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = File(path).name.ifEmpty { path },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (exists)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = FileTintFolder
                                )
                            },
                            selected = currentPath == path,
                            onClick = { if (exists) onNavigate(path) },
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

            // ---- 设置项 ----
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            SectionLabel(stringResource(R.string.section_display))
            NavigationDrawerItem(
                label = {
                    Text(
                        stringResource(
                            if (showHidden) R.string.hide_hidden_files
                            else R.string.show_hidden_files
                        )
                    )
                },
                icon = {
                    Icon(
                        imageVector = if (showHidden) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                },
                selected = false,
                onClick = onToggleShowHidden,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
    )
}
