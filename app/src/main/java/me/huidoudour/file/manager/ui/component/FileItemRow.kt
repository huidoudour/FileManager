package me.huidoudour.file.manager.ui.component

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
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
//  MD3 风格 — 分类色调圆形图标容器 + 圆角列表项
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

/** 图标着色 (MD3 风格: 按分类着色, 配浅色调容器背景) */
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    fileItem: FileItem,
    viewModel: FileManagerViewModel,
    isSelected: Boolean = false,
    isChecked: Boolean = false,
    selectionMode: Boolean = false,
    isFavorite: Boolean = false,
    isMenuShown: Boolean = false,
    onItemClick: () -> Unit,
    onItemLongClick: (() -> Unit)? = null
) {
    val category = FileTypeUtil.getCategory(fileItem)
    val icon = fileIcon(category)

    // 记录最后一次按下的位置 (相对行左上角), 供菜单展开期间保持按压涟漪
    var pressPosition by remember { mutableStateOf(Offset.Zero) }
    val interactionSource = remember { MutableInteractionSource() }

    // 菜单展开时注入合成按压, 保留长按特效; 菜单关闭 (取消/执行操作) 时释放
    var heldPress by remember { mutableStateOf<PressInteraction.Press?>(null) }
    LaunchedEffect(isMenuShown) {
        if (isMenuShown) {
            val press = PressInteraction.Press(pressPosition)
            interactionSource.emit(press)
            heldPress = press
        } else {
            heldPress?.let {
                interactionSource.emit(PressInteraction.Release(it))
                heldPress = null
            }
        }
    }

    val rowBg = when {
        isChecked -> MaterialTheme.colorScheme.secondaryContainer
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> Color.Transparent
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(rowBg)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        pressPosition = down.position
                    }
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onItemClick,
                    onLongClick = onItemLongClick
                )
                .padding(horizontal = 10.dp, vertical = 9.dp),
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
                    contentDescription = stringResource(category.labelRes),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(icon),
                    fallback = rememberVectorPainter(icon)
                )
            } else {
                val tint = iconTint(category)
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
                            contentDescription = stringResource(R.string.favorite_added),
                            modifier = Modifier.size(14.dp),
                            tint = FileTintFolder
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
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
    }
}
