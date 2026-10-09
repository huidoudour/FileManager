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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.FileCategory
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.util.MediaProbe
import java.io.File
import java.util.Locale

/**
 * 音视频预览对话框 — ExoPlayer 播放 + FFmpeg 解析
 *
 * - 视频: PlayerView 内嵌播放 (系统硬解), 无法播放时回退展示 ffmpeg 抽取的缩略图
 * - 音频: ExoPlayer 播放 + 自定义控制条, 波形图由 ffmpeg 生成
 * - 信息区: 容器/时长/总码率/视频流/音频流 (ffprobe 解析)
 * - 无法播放的格式可通过 [onOpenWith] 交给其他应用打开
 */
@Composable
fun MediaPreviewDialog(
    item: FileItem,
    onOpenWith: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = remember { FileTypeUtil.getCategory(item) == FileCategory.VIDEO }

    // ---- FFmpeg 解析: 媒体信息 + 缩略图/波形 ----
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
        artworkFile = if (info == null) null else if (isVideo) {
            MediaProbe.extractThumbnail(context.cacheDir, item.path)
        } else {
            MediaProbe.extractWaveform(context.cacheDir, item.path)
        }
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
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // 进度条状态 (0..1 比例值, 拖动时由 SliderState 接管)
    val sliderState = rememberSliderState()

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                playFailed = true
                isPlaying = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // 播放进度轮询 (300ms 刷新一次), 拖动中不覆盖滑块值
    LaunchedEffect(player) {
        while (isActive) {
            val duration = player.duration
            if (duration > 0) durationMs = duration
            val position = player.currentPosition.coerceAtLeast(0L)
            positionMs = position
            val total = if (duration > 0) duration else (mediaInfo?.durationMs ?: 0L)
            if (!sliderState.isDragging && total > 0) {
                sliderState.value = (position.toFloat() / total).coerceIn(0f, 1f)
            }
            delay(300)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ---- 文件名 ----
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ---- 媒体区 ----
                if (isVideo) {
                    VideoSurface(player = player, artworkFile = artworkFile, playFailed = playFailed)
                } else {
                    AudioSurface(artworkFile = artworkFile, parsing = parsing, playFailed = playFailed)

                    // 音频播放控制条 (播放/暂停 + 进度拖动 + 时间)
                    if (!playFailed) {
                        val totalMs = if (durationMs > 0) durationMs else (mediaInfo?.durationMs ?: 0L)
                        val shownMs = if (sliderState.isDragging) {
                            (sliderState.value * totalMs).toLong()
                        } else {
                            positionMs
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = formatTime(shownMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                state = sliderState,
                                onValueChange = { sliderState.value = it },
                                onValueChangeFinished = {
                                    if (totalMs > 0) player.seekTo((sliderState.value * totalMs).toLong())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                text = formatTime(totalMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- 媒体信息区 (ffprobe 解析结果) ----
                when {
                    parsing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.media_preview_parsing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    }

                    parseFailed -> Text(
                        text = stringResource(R.string.media_preview_parse_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> mediaInfo?.let { info ->
                        Column {
                            info.formatLong?.let {
                                InfoRow(stringResource(R.string.media_info_format), it)
                            }
                            info.durationMs?.let {
                                if (it > 0) InfoRow(stringResource(R.string.media_info_duration), formatTime(it))
                            }
                            info.bitrate?.let {
                                if (it > 0) InfoRow(stringResource(R.string.media_info_total_bitrate), formatBitrate(it))
                            }
                            info.video?.let {
                                InfoRow(stringResource(R.string.media_info_video), buildStreamSummary(it, video = true))
                            }
                            info.audio?.let {
                                InfoRow(stringResource(R.string.media_info_audio), buildStreamSummary(it, video = false))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---- 底部按钮 ----
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
                        Text(stringResource(R.string.media_preview_open_with))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

/**
 * 视频播放区: PlayerView 内嵌播放; 播放失败时回退展示缩略图 + 提示
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    player: ExoPlayer,
    artworkFile: File?,
    playFailed: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (playFailed) {
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
            Text(
                text = stringResource(R.string.media_preview_play_failed),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 音频展示区: 波形图 (ffmpeg 生成); 无波形时显示占位图标
 */
@Composable
private fun AudioSurface(
    artworkFile: File?,
    parsing: Boolean,
    playFailed: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = remember(artworkFile) {
            artworkFile?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        }
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentScale = ContentScale.Fit
            )

            parsing -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )

            else -> Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        if (playFailed) {
            Text(
                text = stringResource(R.string.media_preview_play_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** 信息行 (与 FileDialogs 的 PropertyRow 风格一致, 标签稍宽以容纳三字标签) */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** 流信息摘要: "H264 · 1920x1080 · 30fps" / "AAC · 44100Hz · stereo" */
private fun buildStreamSummary(stream: MediaProbe.StreamInfo, video: Boolean): String {
    val parts = buildList {
        stream.codec?.let { add(it) }
        if (video) {
            if (stream.width != null && stream.height != null) add("${stream.width}x${stream.height}")
            stream.frameRate?.let { add("${it}fps") }
        } else {
            stream.sampleRate?.let { add("${it}Hz") }
            stream.channelLayout?.let { add(it) }
        }
    }
    return parts.joinToString(" · ").ifEmpty { "-" }
}

/** 毫秒 -> "mm:ss" (超过 1 小时为 "h:mm:ss") */
private fun formatTime(ms: Long): String {
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

/** 比特率 -> "Kbps" / "Mbps" */
private fun formatBitrate(bitsPerSecond: Long): String = when {
    bitsPerSecond >= 1_000_000 -> String.format(Locale.US, "%.1f Mbps", bitsPerSecond / 1_000_000.0)
    bitsPerSecond >= 1_000 -> String.format(Locale.US, "%.0f kbps", bitsPerSecond / 1_000.0)
    else -> "$bitsPerSecond bps"
}
