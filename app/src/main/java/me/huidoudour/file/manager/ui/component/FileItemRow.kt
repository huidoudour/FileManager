package me.huidoudour.file.manager.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.FileCategory
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel

/** 文件图标背景色（类似质感文件的色调） */
private fun iconBackgroundColor(category: FileCategory): Color = when (category) {
    FileCategory.FOLDER -> Color(0xFFFFCC02).copy(alpha = 0.18f)    // 黄色
    FileCategory.IMAGE -> Color(0xFF4CAF50).copy(alpha = 0.18f)     // 绿色
    FileCategory.VIDEO -> Color(0xFFF44336).copy(alpha = 0.18f)     // 红色
    FileCategory.AUDIO -> Color(0xFFFF9800).copy(alpha = 0.18f)     // 橙色
    FileCategory.DOCUMENT -> Color(0xFF2196F3).copy(alpha = 0.18f)  // 蓝色
    FileCategory.PDF -> Color(0xFFE91E63).copy(alpha = 0.18f)       // 粉色
    FileCategory.ARCHIVE -> Color(0xFF795548).copy(alpha = 0.18f)   // 棕色
    FileCategory.CODE -> Color(0xFF00BCD4).copy(alpha = 0.18f)      // 青色
    FileCategory.APK -> Color(0xFF9C27B0).copy(alpha = 0.18f)       // 紫色
    FileCategory.OTHER -> Color(0xFF607D8B).copy(alpha = 0.18f)     // 灰色
}

/** 文件图标前景色 */
private fun iconTintColor(category: FileCategory): Color = when (category) {
    FileCategory.FOLDER -> Color(0xFFF9A825)
    FileCategory.IMAGE -> Color(0xFF4CAF50)
    FileCategory.VIDEO -> Color(0xFFF44336)
    FileCategory.AUDIO -> Color(0xFFFF9800)
    FileCategory.DOCUMENT -> Color(0xFF2196F3)
    FileCategory.PDF -> Color(0xFFE91E63)
    FileCategory.ARCHIVE -> Color(0xFF795548)
    FileCategory.CODE -> Color(0xFF00BCD4)
    FileCategory.APK -> Color(0xFF9C27B0)
    FileCategory.OTHER -> Color(0xFF607D8B)
}

/** 文件图标 */
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

/**
 * 文件列表单项 — Material Files 风格
 * - 左侧: 圆角方块图标 + 类型颜色背景
 * - 中间: 文件名 + 日期 + 大小
 * - 右侧: 扩展名标签 / 目录箭头
 */
@Composable
fun FileItemRow(
    fileItem: FileItem,
    viewModel: FileManagerViewModel,
    isSelected: Boolean = false,
    onItemClick: () -> Unit,
    onItemLongClick: (() -> Unit)? = null
) {
    val category = FileTypeUtil.getCategory(fileItem)
    val bgColor = iconBackgroundColor(category)
    val tint = iconTintColor(category)
    val icon = fileIcon(category)

    val rowBg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onItemClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ======== 文件图标（圆角背景方块） ========
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = category.label,
                modifier = Modifier.size(22.dp),
                tint = tint
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // ======== 文件信息 ========
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (fileItem.canRead)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = viewModel.formatDate(fileItem.lastModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!fileItem.isDirectory) {
                    Text(
                        text = FileItem.formatSize(fileItem.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ======== 右侧类型标签 ========
        if (fileItem.isDirectory) {
            Text(
                text = "${fileItem.name.count { it == '.' }}项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = fileItem.extension.uppercase().take(4),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 分割线
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}
