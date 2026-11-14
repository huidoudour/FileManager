package me.huidoudour.file.manager.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 图片预览 — 全屏黑底查看器
 *
 * - Coil 解码本地图片, 按 ContentScale.Fit 适配屏幕
 * - 解码尺寸显式指定为屏幕尺寸 (不能依赖 Coil 的 drawSize, 否则 Loading 态死锁)
 * - 支持双指缩放 (1x~6x)、单指拖动平移、双击在 1x/2.5x 间切换
 * - 顶栏: 文件名 + 关闭; 底栏: 分辨率/大小 + 用其他应用打开
 * - 解码失败时提示可通过其他应用打开
 */
@Composable
fun ImagePreviewDialog(
    item: FileItem,
    onOpenWith: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val context = LocalContext.current
        // 显式指定解码尺寸: 未指定时 Coil 的 size resolver 会等待 AsyncImagePainter.onDraw
        // 写入的 drawSize, 而 Loading 态只画转圈不画 painter, 请求将永远挂起 (一直转圈)。
        // 按屏幕尺寸解码既能立即出图, 也避免超大图按原始尺寸解码导致 OOM。
        val decodeSize = remember(context) {
            with(context.resources.displayMetrics) { Size(widthPixels, heightPixels) }
        }
        // 请求对象需固定, 避免每次重组都让 Coil 重新执行请求
        val painter = rememberAsyncImagePainter(
            model = remember(item.path, decodeSize) {
                ImageRequest.Builder(context)
                    .data(File(item.path))
                    .size(decodeSize)
                    .crossfade(false)
                    .build()
            }
        )
        val imageDimension = rememberImageDimension(item.path)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )

                is AsyncImagePainter.State.Error -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.image_preview_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }

                else -> ZoomableImage(
                    painter = painter,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ---- 顶栏: 文件名 + 关闭 ----
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White
                    )
                }
            }

            // ---- 底栏: 分辨率/大小 + 用其他应用打开 ----
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = imageMeta(imageDimension, item),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                TextButton(onClick = onOpenWith) {
                    Text(
                        text = stringResource(R.string.media_preview_open_with),
                        color = Color.White
                    )
                }
            }
        }
    }
}

/** 底栏信息: "1920 × 1080 · 2.3 MB" (分辨率取自文件头, 不随解码尺寸变化) */
private fun imageMeta(dimension: IntSize?, item: FileItem): String {
    val resolution = dimension
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { "${it.width} × ${it.height}" }
    return listOfNotNull(resolution, FileItem.formatSize(item.size)).joinToString(" · ")
}

/** 图片原始分辨率: 只解析文件头不解码像素, 读取失败返回 null */
@Composable
private fun rememberImageDimension(path: String): IntSize? {
    var dimension by remember(path) { mutableStateOf<IntSize?>(null) }
    LaunchedEffect(path) {
        dimension = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                IntSize(options.outWidth, options.outHeight)
            } else {
                null
            }
        }
    }
    return dimension
}

/**
 * 可缩放/平移的图片层
 *
 * 平移范围按"图片在容器内的适配显示尺寸 × 当前缩放"限制, 避免拖出大面积黑边。
 */
@Composable
private fun ZoomableImage(
    painter: Painter,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val intrinsicSize = painter.intrinsicSize

    fun clampOffset(candidate: Offset, currentScale: Float): Offset {
        val imgWidth = intrinsicSize.width
        val imgHeight = intrinsicSize.height
        // 容器或图片尺寸未就绪 (NaN / 0) 时不做平移限制
        if (containerSize == IntSize.Zero || !imgWidth.isFinite() || !imgHeight.isFinite() ||
            imgWidth <= 0f || imgHeight <= 0f
        ) {
            return candidate
        }
        val fit = min(containerSize.width / imgWidth, containerSize.height / imgHeight)
        val maxX = max(0f, (imgWidth * fit * currentScale - containerSize.width) / 2f)
        val maxY = max(0f, (imgHeight * fit * currentScale - containerSize.height) / 2f)
        return Offset(
            candidate.x.coerceIn(-maxX, maxX),
            candidate.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                    scale = newScale
                    offset = if (newScale <= 1f) Offset.Zero
                    else clampOffset(offset + pan, newScale)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = clampOffset(Offset.Zero, 2.5f)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
