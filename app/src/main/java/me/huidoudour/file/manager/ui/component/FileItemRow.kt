package me.huidoudour.file.manager.ui.component

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.FileCategory
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File

// =============================================================================
//  MT 风格 — 简洁图标 + 灰度配色
// =============================================================================

/** 缩略图 ImageLoader 单例 (支持视频帧) */
private object ThumbnailLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .components { add(VideoFrameDecoder.Factory()) }
                .crossfade(false)
                .build()
                .also { instance = it }
        }
}

/** 文件图标 (MT 风格: 文件夹暖黄, 其他统一灰色) */
private fun fileIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.FOLDER -> Icons.Filled.Folder
    FileCategory.IMAGE -> Icons.Filled.Image
    FileCategory.VIDEO -> Icons.Filled.Movie
    FileCategory.AUDIO -> Icons.Filled.MusicNote
    FileCategory.DOCUMENT -> Icons.Filled.Description
    FileCategory.PDF -> Icons.Filled.PictureAsPdf
    FileCategory.ARCHIVE -> Icons.Filled.Archive
    FileCategory.CODE -> Icons.Filled.Code
    FileCategory.APK -> Icons.Filled.Android
    FileCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/** 图标着色 (MT 风格: 仅文件夹暖金, 其余灰色) */
private fun iconTint(category: FileCategory): Color = when (category) {
    FileCategory.FOLDER -> Color(0xFFF9A825)
    else -> Color(0xFF757575)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    fileItem: FileItem,
    viewModel: FileManagerViewModel,
    isSelected: Boolean = false,
    isChecked: Boolean = false,
    selectionMode: Boolean = false,
    isFavorite: Boolean = false,
    onItemClick: () -> Unit,
    onItemLongClick: (() -> Unit)? = null
) {
    val category = FileTypeUtil.getCategory(fileItem)
    val icon = fileIcon(category)

    val rowBg = when {
        isChecked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg)
                .combinedClickable(
                    onClick = onItemClick,
                    onLongClick = onItemLongClick
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ======== 图标 / 缩略图 ========
            if (category == FileCategory.IMAGE || category == FileCategory.VIDEO) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(fileItem.path))
                        .build(),
                    imageLoader = ThumbnailLoader.get(context),
                    contentDescription = category.label,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(icon),
                    fallback = rememberVectorPainter(icon)
                )
            } else {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = category.label,
                        modifier = Modifier.size(24.dp),
                        tint = iconTint(category)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ======== 文件名 + 元信息 ========
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fileItem.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        color = if (fileItem.canRead)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.error
                    )
                    if (isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "已收藏",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFF9A825)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = viewModel.formatDate(fileItem.lastModified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (fileItem.isDirectory) "--" else FileItem.formatSize(fileItem.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ======== 右侧: 复选框 / 目录箭头 / 扩展名 ========
            Spacer(modifier = Modifier.width(8.dp))
            when {
                selectionMode -> {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { onItemClick() }
                    )
                }
                fileItem.isDirectory -> {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                else -> {
                    Text(
                        text = fileItem.extension.uppercase().take(5),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 62.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
