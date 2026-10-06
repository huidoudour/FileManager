package me.huidoudour.file.manager.ui.component

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.ui.theme.FileTintFolder
import me.huidoudour.file.manager.util.FileCategory
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File

// =============================================================================
//  MaterialFiles 风格列表项
//  - 行高 72dp, 左侧 48dp 图标触摸区 (40dp 彩色图标), 右侧 48dp 三点菜单按钮
//  - 选中时图标右下角显示圆形对勾徽章, 行本身无背景色变化
// =============================================================================

/** 单条文件的长按/菜单操作 */
enum class FileAction(val labelRes: Int) {
    COPY(R.string.action_copy),
    CUT(R.string.action_cut),
    DELETE(R.string.action_delete),
    RENAME(R.string.action_rename),
    SHARE(R.string.action_share),
    FAVORITE(R.string.action_favorite),
    PIN_SIZE(R.string.action_pin_size),
    REFRESH_SIZE(R.string.action_refresh_size),
    PROPERTIES(R.string.action_properties),
    MULTI_SELECT(R.string.action_multi_select)
}

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

/** 文件类型彩色图标 (照搬 MaterialFiles 的 file_*_icon 系列) */
@DrawableRes
private fun fileIconRes(category: FileCategory): Int = when (category) {
    FileCategory.FOLDER -> R.drawable.file_directory_icon
    FileCategory.IMAGE -> R.drawable.file_image_icon
    FileCategory.VIDEO -> R.drawable.file_video_icon
    FileCategory.AUDIO -> R.drawable.file_audio_icon
    FileCategory.DOCUMENT -> R.drawable.file_document_icon
    FileCategory.PDF -> R.drawable.file_pdf_icon
    FileCategory.ARCHIVE -> R.drawable.file_archive_icon
    FileCategory.CODE -> R.drawable.file_code_icon
    FileCategory.APK -> R.drawable.file_apk_icon
    FileCategory.OTHER -> R.drawable.file_generic_icon
}

@Composable
fun FileItemRow(
    fileItem: FileItem,
    viewModel: FileManagerViewModel,
    isChecked: Boolean = false,
    isFavorite: Boolean = false,
    isMenuShown: Boolean = false,
    showThumbnails: Boolean = true,
    onItemClick: () -> Unit,
    onIconClick: () -> Unit = {},
    onItemLongClick: (() -> Unit)? = null,
    onMenuClick: () -> Unit = {},
    onMenuDismiss: () -> Unit = {},
    onAction: (FileAction) -> Unit = {}
) {
    val category = FileTypeUtil.getCategory(fileItem)
    val iconRes = fileIconRes(category)
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ======== 图标 / 缩略图 (48dp 触摸区, 40dp 内容, 点击切换选择) ========
        Box(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = onIconClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (showThumbnails &&
                (category == FileCategory.IMAGE || category == FileCategory.VIDEO)
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(fileItem.path))
                        .build(),
                    imageLoader = ThumbnailLoader.get(context),
                    contentDescription = stringResource(category.labelRes),
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Crop,
                    error = painterResource(iconRes),
                    fallback = painterResource(iconRes)
                )
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = stringResource(category.labelRes),
                    modifier = Modifier.size(40.dp)
                )
            }

            // 选中徽章: 右下角 18dp 圆形对勾 (primary 底 + onPrimary 勾)
            if (isChecked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // ======== 文件名 + 描述 ========
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodyLarge,
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
                        contentDescription = stringResource(R.string.favorite_added),
                        modifier = Modifier.size(14.dp),
                        tint = FileTintFolder
                    )
                }
            }
            // 描述行: 文件显示 "修改时间 · 大小"; 文件夹仅在计算/已缓存大小时显示
            val description = if (fileItem.isDirectory) {
                val cache = viewModel.getCachedFolderSize(fileItem.path)
                when {
                    cache != null -> FileItem.formatSize(cache.size)
                    viewModel.isPinned(fileItem.path) -> stringResource(R.string.pin_calculating)
                    else -> null
                }
            } else {
                "${viewModel.formatDate(fileItem.lastModified)} · ${FileItem.formatSize(fileItem.size)}"
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fileItem.isDirectory && viewModel.isPinned(fileItem.path))
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ======== 右侧三点菜单按钮 ========
        Box(modifier = Modifier.padding(start = 8.dp, end = 8.dp)) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FileActionMenu(
                expanded = isMenuShown,
                item = fileItem,
                isFavorite = isFavorite,
                isPinned = viewModel.isPinned(fileItem.path),
                onAction = onAction,
                onDismiss = onMenuDismiss
            )
        }
    }
}

/** 三点按钮弹出的操作菜单 (照搬 MaterialFiles 的 file_item 菜单形态) */
@Composable
private fun FileActionMenu(
    expanded: Boolean,
    item: FileItem,
    isFavorite: Boolean,
    isPinned: Boolean,
    onAction: (FileAction) -> Unit,
    onDismiss: () -> Unit
) {
    if (!expanded) return

    val actions = buildList {
        add(FileAction.CUT.labelRes to FileAction.CUT)
        add(FileAction.COPY.labelRes to FileAction.COPY)
        add(FileAction.DELETE.labelRes to FileAction.DELETE)
        add(FileAction.RENAME.labelRes to FileAction.RENAME)
        if (!item.isDirectory) add(FileAction.SHARE.labelRes to FileAction.SHARE)
        if (item.isDirectory) {
            add(
                (if (isFavorite) R.string.action_unfavorite else R.string.action_favorite)
                    to FileAction.FAVORITE
            )
            if (isPinned) {
                add(R.string.action_refresh_size to FileAction.REFRESH_SIZE)
                add(R.string.action_unpin_size to FileAction.PIN_SIZE)
            } else {
                add(R.string.action_pin_size to FileAction.PIN_SIZE)
            }
        }
        add(FileAction.PROPERTIES.labelRes to FileAction.PROPERTIES)
        add(FileAction.MULTI_SELECT.labelRes to FileAction.MULTI_SELECT)
    }

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(4.dp)
    ) {
        actions.forEach { (labelRes, action) ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (action == FileAction.DELETE)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = { onAction(action) }
            )
        }
    }
}
