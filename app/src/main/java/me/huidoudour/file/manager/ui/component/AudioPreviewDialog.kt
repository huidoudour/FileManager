package me.huidoudour.file.manager.ui.component

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.playback.AudioPlaybackService
import me.huidoudour.file.manager.util.FileCategory
import me.huidoudour.file.manager.util.FileSortUtil
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.util.MediaProbe
import me.huidoudour.file.manager.util.SortMode
import java.io.File

/** 音频偏好存储 (与 ViewModel 使用同一偏好文件) */
private const val PREFS_NAME = "file_manager_prefs"
private const val KEY_BACKGROUND_PLAY = "audio_background_play"
private const val KEY_PLAY_MODE = "audio_play_mode"

/** 音频播放方式: 顺序 / 列表循环 / 单曲循环 / 随机 (映射 ExoPlayer 的 repeatMode + shuffle) */
private enum class AudioPlayMode(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val repeatMode: Int,
    val shuffle: Boolean
) {
    SEQUENTIAL(R.string.audio_mode_sequential, Icons.AutoMirrored.Filled.PlaylistPlay, Player.REPEAT_MODE_OFF, false),
    REPEAT_ALL(R.string.audio_mode_repeat_all, Icons.Filled.Repeat, Player.REPEAT_MODE_ALL, false),
    REPEAT_ONE(R.string.audio_mode_repeat_one, Icons.Filled.RepeatOne, Player.REPEAT_MODE_ONE, false),
    SHUFFLE(R.string.audio_mode_shuffle, Icons.Filled.Shuffle, Player.REPEAT_MODE_ALL, true);

    companion object {
        fun fromPlayer(repeatMode: Int, shuffle: Boolean): AudioPlayMode = when {
            shuffle -> SHUFFLE
            repeatMode == Player.REPEAT_MODE_ONE -> REPEAT_ONE
            repeatMode == Player.REPEAT_MODE_ALL -> REPEAT_ALL
            else -> SEQUENTIAL
        }
    }
}

/**
 * 音频预览对话框 — 播放列表 + 播放模式 + 后台播放
 *
 * - 自动识别当前文件所在文件夹的全部音频文件, 按名称自然排序构建播放列表
 * - 通过 MediaController 连接 [AudioPlaybackService] (MediaSessionService):
 *   播放时系统媒体控制 (通知栏 / 锁屏 / 耳机按键) 可直接控制
 * - 右上角"后台播放"开关: 开启后关闭对话框仍继续播放, 列表内自动顺序续播
 * - 播放模式: 顺序 / 列表循环 / 单曲循环 / 随机 (持久化到偏好设置)
 */
@Composable
internal fun AudioPreviewDialog(
    item: FileItem,
    onOpenWith: (FileItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // ---- 播放列表: 当前文件所在文件夹的全部音频 (自然排序), 至少包含当前文件 ----
    val playlist = remember(item.path) { buildAudioPlaylist(item) }
    val startIndex = remember(playlist, item.path) {
        playlist.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
    }

    // ---- 播放偏好 ----
    var backgroundPlay by remember {
        mutableStateOf(prefs.getBoolean(KEY_BACKGROUND_PLAY, false))
    }
    var playMode by remember {
        mutableStateOf(
            AudioPlayMode.entries.getOrElse(prefs.getInt(KEY_PLAY_MODE, 0)) { AudioPlayMode.SEQUENTIAL }
        )
    }

    // ---- 媒体控制器: 连接后台播放服务 ----
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(startIndex) }
    var playFailed by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    val sliderState = rememberSliderState()

    DisposableEffect(Unit) {
        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        ).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            // 后台播放开关关闭时: 关闭对话框即停止播放并清空播放列表
            if (!backgroundPlay) {
                controller?.run {
                    pause()
                    clearMediaItems()
                }
            }
            // 仅断开控制器连接, 不影响服务的后台播放
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    // ---- 初始化播放: 已在后台播放当前文件则继续, 否则下发播放列表并从当前文件开始 ----
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        if (c.currentMediaItem?.mediaId == item.path) {
            playMode = AudioPlayMode.fromPlayer(c.repeatMode, c.shuffleModeEnabled)
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0L)
            if (!c.isPlaying) c.play()
        } else {
            c.setMediaItems(playlist.map { it.toMediaItem() }, startIndex, 0L)
            c.repeatMode = playMode.repeatMode
            c.shuffleModeEnabled = playMode.shuffle
            c.prepare()
            c.play()
        }
    }

    // ---- 播放状态订阅 ----
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                playFailed = false
                currentIndex = c.currentMediaItemIndex.coerceAtLeast(0)
            }

            override fun onPlayerError(error: PlaybackException) {
                playFailed = true
                isPlaying = false
            }
        }
        c.addListener(listener)
        isPlaying = c.isPlaying
        currentIndex = c.currentMediaItemIndex.coerceAtLeast(0)
        onDispose { c.removeListener(listener) }
    }

    // 当前播放曲目 (随自动续播/切歌变化)
    val currentItem = playlist.getOrNull(currentIndex) ?: item

    // ---- FFmpeg 解析当前曲目: 媒体信息 + 波形图 ----
    var parsing by remember { mutableStateOf(true) }
    var parseFailed by remember { mutableStateOf(false) }
    var mediaInfo by remember { mutableStateOf<MediaProbe.MediaInfo?>(null) }
    var artworkFile by remember { mutableStateOf<File?>(null) }

    // 解析信息区显隐 (由"详细信息"按钮控制)
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(currentItem.path) {
        parsing = true
        parseFailed = false
        mediaInfo = null
        artworkFile = null
        if (!MediaProbe.isSupported) {
            parsing = false
            parseFailed = true
            return@LaunchedEffect
        }
        val info = MediaProbe.probe(currentItem.path)
        mediaInfo = info
        parseFailed = info == null
        parsing = false
        artworkFile = if (info == null) null else MediaProbe.extractWaveform(context.cacheDir, currentItem.path)
    }

    // ---- 播放进度轮询 (300ms 刷新一次), 拖动中不覆盖滑块值 ----
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (isActive) {
            val duration = c.duration
            if (duration > 0) durationMs = duration
            val position = c.currentPosition.coerceAtLeast(0L)
            positionMs = position
            val total = if (duration > 0) duration else (mediaInfo?.durationMs ?: 0L)
            if (!sliderState.isDragging && total > 0) {
                sliderState.value = (position.toFloat() / total).coerceIn(0f, 1f)
            }
            delay(300)
        }
    }

    // 通知权限 (Android 13+): 打开后台播放开关时请求, 用于系统媒体通知
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // ---- 播放控制 ----
    fun togglePlay() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_ENDED -> {
                c.seekTo(0, 0L)
                c.play()
            }
            else -> {
                if (playFailed) c.prepare()
                c.play()
            }
        }
    }

    fun applyPlayMode(mode: AudioPlayMode) {
        playMode = mode
        prefs.edit { putInt(KEY_PLAY_MODE, mode.ordinal) }
        controller?.run {
            repeatMode = mode.repeatMode
            shuffleModeEnabled = mode.shuffle
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
                // ---- 顶栏: 文件名 + 后台播放开关 (文本在开关左侧) ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentItem.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.audio_background_play),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = backgroundPlay,
                        onCheckedChange = { checked ->
                            backgroundPlay = checked
                            prefs.edit { putBoolean(KEY_BACKGROUND_PLAY, checked) }
                            if (checked &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = Modifier.scale(0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // ---- 波形区 ----
                AudioSurface(artworkFile = artworkFile, parsing = parsing, playFailed = playFailed)

                // ---- 播放控制: 模式 | 上一首/播放/下一首 | 序号 ----
                if (!playFailed) {
                    var showModeMenu by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(onClick = { showModeMenu = true }) {
                                Icon(
                                    imageVector = playMode.icon,
                                    contentDescription = stringResource(playMode.labelRes)
                                )
                            }
                            DropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false }
                            ) {
                                AudioPlayMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(mode.labelRes)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = mode.icon,
                                                contentDescription = null
                                            )
                                        },
                                        trailingIcon = {
                                            if (mode == playMode) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        onClick = {
                                            applyPlayMode(mode)
                                            showModeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { controller?.seekToPreviousMediaItem() },
                            enabled = playlist.size > 1
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.audio_prev)
                            )
                        }
                        IconButton(onClick = { togglePlay() }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(
                            onClick = { controller?.seekToNextMediaItem() },
                            enabled = playlist.size > 1
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = stringResource(R.string.audio_next)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(
                                R.string.audio_playlist_index, currentIndex + 1, playlist.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ---- 进度条 ----
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
                        Text(
                            text = formatTime(shownMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            state = sliderState,
                            onValueChange = { sliderState.value = it },
                            onValueChangeFinished = {
                                if (totalMs > 0) controller?.seekTo((sliderState.value * totalMs).toLong())
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

                // ---- 媒体信息区 (ffprobe 解析结果, 由"详细信息"按钮控制显隐) ----
                AnimatedVisibility(visible = showDetails) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
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
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---- 底部按钮: 详细信息 (左) | 用其他应用打开 / 关闭 (右) ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(
                            text = stringResource(
                                if (showDetails) R.string.audio_details_collapse
                                else R.string.audio_details
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onOpenWith(currentItem) }) {
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
}

/**
 * 构建播放列表: 当前文件所在文件夹的全部音频文件 (排除隐藏文件), 按名称自然排序;
 * 扫描失败或当前文件不在结果中时, 退化为仅当前文件的单曲列表
 */
private fun buildAudioPlaylist(item: FileItem): List<FileItem> {
    val parent = File(item.path).parentFile ?: return listOf(item)
    val audios = parent.listFiles()
        ?.filter { it.isFile && !it.isHidden }
        ?.map { it.toFileItem() }
        ?.filter { FileTypeUtil.getCategory(it) == FileCategory.AUDIO }
        ?: return listOf(item)
    val sorted = FileSortUtil.sort(audios, SortMode.NAME, ascending = true, directoriesFirst = false)
    return when {
        sorted.isEmpty() -> listOf(item)
        sorted.none { it.path == item.path } -> listOf(item) + sorted
        else -> sorted
    }
}

/** File -> FileItem 转换 (与 ViewModel 的 toFileItem 保持一致) */
private fun File.toFileItem(): FileItem = FileItem(
    name = name,
    path = absolutePath,
    parentPath = parent ?: "",
    isDirectory = isDirectory,
    size = if (isFile) length() else 0L,
    lastModified = lastModified(),
    extension = if (isFile) extension else "",
    canRead = canRead(),
    canWrite = canWrite(),
    isHidden = isHidden
)

/** FileItem -> media3 MediaItem (mediaId 用文件路径, 便于识别当前播放文件) */
private fun FileItem.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(Uri.fromFile(File(path)))
    .setMediaId(path)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
    .build()

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

/** 比特率 -> "Kbps" / "Mbps" */
private fun formatBitrate(bitsPerSecond: Long): String = when {
    bitsPerSecond >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f Mbps", bitsPerSecond / 1_000_000.0)
    bitsPerSecond >= 1_000 -> String.format(java.util.Locale.US, "%.0f kbps", bitsPerSecond / 1_000.0)
    else -> "$bitsPerSecond bps"
}
