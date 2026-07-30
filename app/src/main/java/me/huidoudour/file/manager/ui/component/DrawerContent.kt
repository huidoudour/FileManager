package me.huidoudour.file.manager.ui.component

import android.os.Environment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File

// =============================================================================
//  侧边栏抽屉: 快捷目录 + 收藏
// =============================================================================

private data class QuickDir(val label: String, val path: String, val icon: ImageVector)

/** 常用快捷目录 (仅列出实际存在的) */
private fun buildQuickDirs(): List<QuickDir> {
    fun publicDir(type: String) =
        Environment.getExternalStoragePublicDirectory(type).absolutePath

    return listOf(
        QuickDir("内部存储", FileManagerViewModel.STORAGE_ROOT, Icons.Filled.PhoneAndroid),
        QuickDir("下载", publicDir(Environment.DIRECTORY_DOWNLOADS), Icons.Filled.Download),
        QuickDir("相机", publicDir(Environment.DIRECTORY_DCIM), Icons.Filled.PhotoCamera),
        QuickDir("图片", publicDir(Environment.DIRECTORY_PICTURES), Icons.Filled.Image),
        QuickDir("视频", publicDir(Environment.DIRECTORY_MOVIES), Icons.Filled.Movie),
        QuickDir("音乐", publicDir(Environment.DIRECTORY_MUSIC), Icons.Filled.MusicNote),
        QuickDir("文档", publicDir(Environment.DIRECTORY_DOCUMENTS), Icons.Filled.Description)
    ).filter { File(it.path).exists() }
}

@Composable
fun DrawerContent(
    currentPath: String,
    favorites: List<String>,
    showHidden: Boolean,
    onNavigate: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onToggleShowHidden: () -> Unit
) {
    val quickDirs = remember { buildQuickDirs() }

    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            // ---- 标题 ----
            Text(
                text = "文件管理器",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
            HorizontalDivider()

            // ---- 快捷目录 ----
            SectionLabel("位置")
            quickDirs.forEach { dir ->
                NavigationDrawerItem(
                    label = { Text(dir.label) },
                    icon = { Icon(imageVector = dir.icon, contentDescription = null) },
                    selected = currentPath == dir.path,
                    onClick = { onNavigate(dir.path) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // ---- 收藏 ----
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            SectionLabel("收藏")
            if (favorites.isEmpty()) {
                Text(
                    text = "长按文件夹选择\"收藏\"添加",
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
                                    tint = Color(0xFFF9A825)
                                )
                            },
                            selected = currentPath == path,
                            onClick = { if (exists) onNavigate(path) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemoveFavorite(path) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "移除收藏",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---- 设置项 ----
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            SectionLabel("显示")
            NavigationDrawerItem(
                label = { Text(if (showHidden) "隐藏隐藏文件" else "显示隐藏文件") },
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
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}
