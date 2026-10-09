package me.huidoudour.file.manager.ui.component

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.FileCategory
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.util.MediaProbe
import java.io.File
import java.util.Locale

/**
 * 音视频预览入口 — 按类别分发
 *
 * - 视频: 全屏黑底播放器 (PlayerView 铺满屏幕, 悬浮文件名/关闭与媒体信息),
 *   无法播放时回退展示 ffmpeg 抽取的缩略图
 * - 音频: 播放列表对话框 (见 AudioPreviewDialog.kt) — 自动识别同文件夹音频,
 *   支持播放模式切换与后台播放 (MediaSession 系统媒体控制)
 * - [onOpenWith] 携带当前预览的文件 (音频自动续播后为当前曲目), 可交给其他应用打开
 */
@Composable
fun MediaPreviewDialog(
    item: FileItem,
    onOpenWith: (FileItem) -> Unit,
    onDismiss: () -> Unit
) {
    when (FileTypeUtil.getCategory(item)) {
        FileCategory.VIDEO -> VideoPreviewDialog(
            item = item,
            onOpenWith = onOpenWith,
            onDismiss = onDismiss
        )

        else -> AudioPreviewDialog(
            item = item,
            onOpenWith = onOpenWith,
            onDismiss = onDismiss
        )
    }
}

/**
 * 视频预览 — ExoPlayer 播放 + FFmpeg 解析
 *
 * 信息区: 容器/时长/总码率/视频流/音频流 (ffprobe 解析);
 * 无法播放时回退展示 ffmpeg 抽取的缩略图, 可通过 [onOpenWith] 交给其他应用打开
 */
@Composable
private fun VideoPreviewDialog(
    item: FileItem,
    onOpenWith: (FileItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // ---- FFmpeg 解析: 媒体信息 + 缩略图 ----
    var parsing by remember { mutableStateOf(true) }
    var parseFailed by remember { mutableStateOf(false) }
    var mediaInfo by remember { mutableStateOf<MediaProbe.MediaInfo?>(null) }
    var artworkFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(item.path) {
        if (!MediaProbe.isSupported) {
            parsing = false
            parseFailed = true
            return@LaunchedEffect
        }
        val info = MediaProbe.probe(item.path)
        mediaInfo = info
        parseFailed = info == null
        parsing = false
        artworkFile = if (info == null) null else MediaProbe.extractThumbnail(context.cacheDir, item.path)
    }

    // ---- ExoPlayer 播放 ----
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(item.path))))
            prepare()
            playWhenReady = true
        }
    }
    var playFailed by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playFailed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    VideoFullScreenDialog(
        item = item,
        player = player,
        artworkFile = artworkFile,
        playFailed = playFailed,
        mediaInfo = mediaInfo,
        parsing = parsing,
        onOpenWith = { onOpenWith(item) },
        onDismiss = onDismiss
    )
}

/**
 * 视频播放 — 全屏黑底对话框
 *
 * PlayerView 铺满整屏 (按视频比例居中适配), 顶部/底部为悬浮渐变信息栏:
 * - 顶栏: 文件名 + 关闭
 * - 底栏: 媒体信息摘要 / 播放失败提示 + "用其他应用打开"
 * - 播放失败时回退展示 ffmpeg 抽取的缩略图
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoFullScreenDialog(
    item: FileItem,
    player: ExoPlayer,
    artworkFile: File?,
    playFailed: Boolean,
    mediaInfo: MediaProbe.MediaInfo?,
    parsing: Boolean,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (playFailed) {
                // 回退: 缩略图居中展示 (无法播放的提示位于底栏内)
                val bitmap = remember(artworkFile) {
                    artworkFile?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            keepScreenOn = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            this.player = player
                        }
                    },
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
                            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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

            // ---- 底栏: 媒体信息摘要 / 播放失败提示 + 用其他应用打开 ----
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                when {
                    playFailed -> Text(
                        text = stringResource(R.string.media_preview_play_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    parsing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.media_preview_parsing),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }

                    else -> buildMediaSummary(mediaInfo)?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            player.pause()
                            onOpenWith()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.media_preview_open_with),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/** 单行媒体摘要: "MP4 · 1:32 · H264 · 1920x1080 · AAC" (无有效字段时返回 null) */
private fun buildMediaSummary(info: MediaProbe.MediaInfo?): String? {
    val mediaInfo = info ?: return null
    val parts = buildList {
        mediaInfo.formatLong?.let { add(it) }
        mediaInfo.durationMs?.takeIf { it > 0 }?.let { add(formatTime(it)) }
        mediaInfo.video?.let { video ->
            video.codec?.let { add(it) }
            if (video.width != null && video.height != null) add("${video.width}x${video.height}")
        }
        mediaInfo.audio?.let { audio -> audio.codec?.let { add(it) } }
    }
    return parts.joinToString(" · ").ifEmpty { null }
}

/** 毫秒 -> "mm:ss" (超过 1 小时为 "h:mm:ss"); 音频/视频预览共用 */
internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
