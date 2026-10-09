package me.huidoudour.file.manager.ui.component

import android.content.Context
import android.util.LruCache
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.ui.theme.FileTintApk
import me.huidoudour.file.manager.ui.theme.FileTintArchive
import me.huidoudour.file.manager.ui.theme.FileTintAudio
import me.huidoudour.file.manager.ui.theme.FileTintCode
import me.huidoudour.file.manager.ui.theme.FileTintDocument
import me.huidoudour.file.manager.ui.theme.FileTintFolder
import me.huidoudour.file.manager.ui.theme.FileTintImage
import me.huidoudour.file.manager.ui.theme.FileTintOther
import me.huidoudour.file.manager.ui.theme.FileTintPdf
import me.huidoudour.file.manager.ui.theme.FileTintVideo
import me.huidoudour.file.manager.util.FileCategory
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File

// =============================================================================
//  文件列表项
//  - 行高 72dp, 左侧 48dp 图标触摸区, 右侧 48dp 三点菜单按钮
//  - 图标: 彩色矢量图标 + 圆形淡色底 (按文件类型显示对应图标)
//  - 图片/视频显示圆角缩略图; 单 APK 识别成功后显示其应用图标
//  - 选中时图标右下角显示圆形对勾徽章
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

/** 文件类型对应的彩色矢量图标 (按类别显示对应图标) */
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

/** 图标 / 圆形淡色底对应的类别颜色 */
private fun iconTint(category: FileCategory): Color = when (category) {
    FileCategory.FOLDER -> FileTintFolder
    FileCategory.IMAGE -> FileTintImage
    FileCategory.VIDEO -> FileTintVideo
    FileCategory.AUDIO -> FileTintAudio
    FileCategory.DOCUMENT -> FileTintDocument
    FileCategory.PDF -> FileTintPdf
    FileCategory.ARCHIVE -> FileTintArchive
    FileCategory.CODE -> FileTintCode
    FileCategory.APK -> FileTintApk
    FileCategory.OTHER -> FileTintOther
}

/**
 * 单 APK 应用图标加载器。
 *
 * 通过 PackageManager 解析 APK 包信息并提取应用图标;
 * 分包 / 损坏 / 非标准 APK 识别失败时返回 null, 由调用方回落为类型图标。
 */
private object ApkIconLoader {
    /** 解析失败的结果用哨兵占位, 避免对同一个 APK 反复重试 */
    private val none = Any()
    private val cache = LruCache<String, Any>(64)

    suspend fun load(context: Context, path: String): ImageBitmap? {
        cache.get(path)?.let { return it as? ImageBitmap }
        return withContext(Dispatchers.IO) {
            val icon = extract(context, path)
            cache.put(path, icon ?: none)
            icon
        }
    }

    private fun extract(context: Context, path: String): ImageBitmap? = try {
        val pm = context.packageManager
        val appInfo = pm.getPackageArchiveInfo(path, 0)?.applicationInfo
        if (appInfo == null) {
            null
        } else {
            // 必须设置 sourceDir, 否则 loadIcon 无法定位 APK 内的图标资源
            appInfo.sourceDir = path
            appInfo.publicSourceDir = path
            // 显式给尺寸: AdaptiveIconDrawable 的 intrinsic 尺寸为 -1, 直接 toBitmap 会失败
            val size = with(context.resources.displayMetrics) { (48 * density).toInt() }
            appInfo.loadIcon(pm)?.toBitmap(size, size)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
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
    val icon = fileIcon(category)
    val tint = iconTint(category)
    val interactionSource = remember { MutableInteractionSource() }

    // APK 应用图标: 单 APK 识别成功后显示 (跟随缩略图开关)
    val isApk = category == FileCategory.APK
    val context = LocalContext.current
    var apkIcon by remember(fileItem.path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(fileItem.path) {
        if (isApk) {
            apkIcon = ApkIconLoader.load(context, fileItem.path)
        }
    }

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
            val currentApkIcon = apkIcon
            when {
                // APK 应用图标 (单 APK 识别成功时, 替换为软件包的真实图标)
                showThumbnails && currentApkIcon != null -> {
                    Icon(
                        painter = remember(currentApkIcon) { BitmapPainter(currentApkIcon) },
                        contentDescription = stringResource(category.labelRes),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        tint = Color.Unspecified
                    )
                }
                // 图片 / 视频缩略图 (圆角, 失败时回落为类型图标)
                showThumbnails &&
                    (category == FileCategory.IMAGE || category == FileCategory.VIDEO) -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(fileItem.path))
                            .build(),
                        imageLoader = ThumbnailLoader.get(context),
                        contentDescription = stringResource(category.labelRes),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                        error = rememberVectorPainter(icon),
                        fallback = rememberVectorPainter(icon)
                    )
                }
                // 类型图标: 40dp 圆形淡色底 + 22dp 彩色矢量图标
                else -> {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(category.labelRes),
                            modifier = Modifier.size(22.dp),
                            tint = tint
                        )
                    }
                }
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
