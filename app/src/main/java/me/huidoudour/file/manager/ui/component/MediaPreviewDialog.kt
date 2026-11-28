package me.huidoudour.file.manager.ui.component

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3UiR
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.FileCategory
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.util.MediaProbe
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 音视频预览入口 — 按类别分发
 *
 * - 视频: 全屏黑底播放器 (PlayerView 铺满屏幕; 顶栏悬浮文件名/媒体摘要/打开/关闭,
 *   控制器仅保留播放/暂停与进度条), 无法播放时回退展示 ffmpeg 抽取的缩略图
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
 * PlayerView 铺满整屏 (按视频比例居中适配; 单文件播放, 控制器仅保留播放/暂停与进度条):
 * - 顶栏: 文件名 + 媒体信息摘要 (播放失败/解析中显示对应状态) + 旋转屏幕/"用其他应用打开"/关闭;
 *   与底部控制器同步显示/隐藏 (进入播放后自动隐藏, 点击画面一起出现)
 * - 手势: 双击播放/暂停 (仅两次都是真正的点击才触发, 快速连续滑动不会误判);
 *   左右滑动快退/快进 (滑满全屏 = 1 分钟, 松手跳转, 拖动中悬浮预览位置);
 *   左侧上下滑调节亮度 / 右侧上下滑调节音量 (滑满全屏 = 100%, 拖动中悬浮预览);
 *   长按 2x 倍速 (按住上滑加速/下滑减速, 松手恢复)
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
    // ---- 滑动快进/快退预览 (拖动中非空, 松手清空) ----
    var seekPreview by remember { mutableStateOf<SeekPreview?>(null) }
    // ---- 长按倍速档位 (长按倍速中非空) ----
    var boostLevel by remember { mutableStateOf<Int?>(null) }
    // ---- 亮度/音量调节 HUD (垂直拖动中非空, 松手清空) ----
    var adjustHud by remember { mutableStateOf<AdjustHud?>(null) }
    // ---- 顶栏是否显示: 跟随播放控制器 —— 播放开始后自动隐藏, 点击画面一起出现 ----
    var controlsVisible by remember(player) { mutableStateOf(player.isPlaying) }
    val playerViewRef = remember { PlayerViewRef() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // 开始播放后立即隐藏控制器, 让顶栏与底部控制器一起消失
                if (isPlaying) playerViewRef.view?.hideController()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 方向与宿主 Activity 需在 Dialog 外获取: 系统返回键的处理在 onDismissRequest 中
    val context = LocalContext.current
    val hostActivity = remember(context) { findActivity(context) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = {
            // 横屏时系统返回键先切回竖屏, 再按一次才关闭播放器
            val activity = hostActivity
            if (isLandscape && activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 亮度仅覆盖当前对话框窗口, 不修改系统设置; 关闭对话框后自动失效
        val dialogView = LocalView.current
        val adjustWindow = remember(dialogView) {
            findDialogWindow(dialogView) ?: findActivity(context)?.window
        }
        val audioManager = remember(context) {
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
        val maxVolume = remember(audioManager) {
            audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
        }
        val originalBrightness = remember(adjustWindow) {
            adjustWindow?.attributes?.screenBrightness
        }
        // 旋转屏幕: Dialog 无法自行旋转, 只能请求宿主 Activity 改变方向; 关闭播放器时恢复原方向
        val originalOrientation = remember(hostActivity) { hostActivity?.requestedOrientation }
        DisposableEffect(hostActivity) {
            onDispose {
                if (hostActivity != null && originalOrientation != null) {
                    hostActivity.requestedOrientation = originalOrientation
                }
            }
        }
        // 横屏播放时隐藏状态栏 (沉浸式全屏); 回到竖屏或关闭播放器时恢复
        DisposableEffect(adjustWindow, isLandscape) {
            val window = adjustWindow
            val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // 同时设置 FLAG_FULLSCREEN: 部分机型上对话框窗口不是 insets 控制目标, 仅靠 hide() 不生效
            if (window != null) {
                val params = window.attributes
                params.flags = if (isLandscape) {
                    params.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
                } else {
                    params.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
                }
                window.attributes = params
            }
            if (isLandscape) {
                controller?.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller?.show(WindowInsetsCompat.Type.statusBars())
            }
            onDispose {
                if (isLandscape) {
                    window?.let {
                        val params = it.attributes
                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
                        it.attributes = params
                    }
                    controller?.show(WindowInsetsCompat.Type.statusBars())
                }
            }
        }

        fun currentAdjustValue(kind: VerticalAdjustKind): Float = when (kind) {
            VerticalAdjustKind.BRIGHTNESS -> readScreenBrightness(context, adjustWindow)
            VerticalAdjustKind.VOLUME -> audioManager
                ?.getStreamVolume(AudioManager.STREAM_MUSIC)
                ?.let { it.toFloat() / maxVolume }
                ?: 0f
        }

        fun applyAdjust(kind: VerticalAdjustKind, value: Float) {
            val clamped = value.coerceIn(0f, 1f)
            when (kind) {
                VerticalAdjustKind.BRIGHTNESS -> adjustWindow?.let { window ->
                    val params = window.attributes
                    params.screenBrightness = clamped.coerceAtLeast(MIN_WINDOW_BRIGHTNESS)
                    window.attributes = params
                }

                VerticalAdjustKind.VOLUME -> audioManager?.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (clamped * maxVolume).roundToInt().coerceIn(0, maxVolume),
                    0
                )
            }
            adjustHud = AdjustHud(kind, clamped)
        }

        DisposableEffect(adjustWindow) {
            onDispose {
                // 恢复原亮度覆盖值 (含 -1 = 跟随系统); Dialog 窗口随对话框销毁, 无副作用
                if (adjustWindow != null && originalBrightness != null) {
                    runCatching {
                        val params = adjustWindow.attributes
                        params.screenBrightness = originalBrightness
                        adjustWindow.attributes = params
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (playFailed) {
                // 回退: 缩略图居中展示 (无法播放的提示位于顶栏内)
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
                            // 单文件播放: 隐藏上一首/下一首与快退/快进按钮, 控制器仅保留播放/暂停与进度条
                            setShowPreviousButton(false)
                            setShowNextButton(false)
                            setShowRewindButton(false)
                            setShowFastForwardButton(false)
                            // 手势: 双击播放/暂停; 左右滑动快退/快进; 左侧亮度/右侧音量; 长按倍速
                            //       (起手在按钮/进度条上时不拦截)
                            setOnTouchListener(
                                VideoGestureListener(
                                    player = player,
                                    viewConfiguration = ViewConfiguration.get(ctx),
                                    density = ctx.resources.displayMetrics.density,
                                    onSeekPreview = { seekPreview = it },
                                    onSpeedBoost = { boostLevel = it },
                                    readAdjustValue = { kind -> currentAdjustValue(kind) },
                                    onVerticalAdjust = { kind, value -> applyAdjust(kind, value) },
                                    onVerticalAdjustEnd = { adjustHud = null }
                                )
                            )
                            // 控制器显示/隐藏时同步顶栏 (播放中自动隐藏, 单击画面一起出现)
                            setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    controlsVisible = visibility == View.VISIBLE
                                }
                            )
                            this.player = player
                            playerViewRef.view = this
                            // 视频直接开始播放, 进入即隐藏控制器和顶栏, 需要时单击画面唤出
                            hideController()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ---- 手势浮层: 滑动快进/快退目标位置 / 长按倍速档位 ----
            seekPreview?.let { preview ->
                GestureHud(
                    icon = if (preview.forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                    text = "${formatTime(preview.targetMs)} / ${formatTime(preview.durationMs)}"
                )
            }
            boostLevel?.let { level ->
                GestureHud(
                    icon = Icons.Filled.Speed,
                    text = stringResource(R.string.media_preview_speed_boost, level)
                )
            }
            adjustHud?.let { hud ->
                // 亮度使用由低到高的图标; 音量为 0 时显示静音图标
                GestureHud(
                    icon = when (hud.kind) {
                        VerticalAdjustKind.BRIGHTNESS ->
                            if (hud.value < 0.5f) Icons.Filled.BrightnessLow else Icons.Filled.BrightnessHigh

                        VerticalAdjustKind.VOLUME ->
                            if (hud.value <= 0f) Icons.AutoMirrored.Filled.VolumeOff
                            else Icons.AutoMirrored.Filled.VolumeUp
                    },
                    text = when (hud.kind) {
                        VerticalAdjustKind.BRIGHTNESS ->
                            stringResource(R.string.media_preview_brightness, (hud.value * 100).roundToInt())

                        VerticalAdjustKind.VOLUME ->
                            stringResource(R.string.media_preview_volume, (hud.value * 100).roundToInt())
                    }
                )
            }

            AnimatedVisibility(
                visible = playFailed || controlsVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                // ---- 顶栏: 文件名 + 媒体摘要 (或状态) + 旋转屏幕/用其他应用打开/关闭 ----
                // 与底部播放控制器同步显示/隐藏 (播放失败时常驻, 便于关闭)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        // 横屏时状态栏被隐藏, 额外留白避开屏幕圆角与摄像头挖孔
                        .padding(
                            start = if (isLandscape) 24.dp else 16.dp,
                            end = if (isLandscape) 24.dp else 4.dp,
                            top = if (isLandscape) 16.dp else 8.dp,
                            bottom = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        when {
                            playFailed -> Text(
                                text = stringResource(R.string.media_preview_play_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            parsing -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.media_preview_parsing),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            }

                            else -> buildMediaSummary(mediaInfo)?.let { summary ->
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (hostActivity != null) {
                        IconButton(
                            onClick = {
                                // 按当前实际方向切换到另一方向, 不依赖额外状态, 旋转后仍能正确判断
                                val isLandscape = hostActivity.resources.configuration.orientation ==
                                    Configuration.ORIENTATION_LANDSCAPE
                                hostActivity.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ScreenRotation,
                                contentDescription = stringResource(R.string.media_preview_rotate),
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            player.pause()
                            onOpenWith()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.media_preview_open_with),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/** 手势浮层: 居中偏上, 半透明黑底圆角, 图标 + 文本 (滑动预览/长按倍速共用) */
@Composable
private fun BoxScope.GestureHud(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(y = (-80).dp)
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = Color.White)
    }
}

/** 水平滑动满屏对应的快退/快进时长 (与片长无关) */
private const val SLIDE_PER_SCREEN_MS = 60_000L

/** 长按倍速: 基础(=按住起始)档位与可调范围 (上滑最高 4x, 下滑最低 1x) */
private const val SPEED_BOOST_BASE = 2
private const val SPEED_BOOST_MIN = 1
private const val SPEED_BOOST_MAX = 4

/** 长按倍速调节步长: 每滑动该距离 (dp) 加减 1 档 */
private const val SPEED_BOOST_STEP_DP = 48f

/** 窗口亮度下限: 避免滑到 0 时黑屏 */
private const val MIN_WINDOW_BRIGHTNESS = 0.01f

/** 滑动快进/快退预览状态 (targetMs 目标位置, durationMs 片长, forward 是否快进方向) */
private data class SeekPreview(val targetMs: Long, val durationMs: Long, val forward: Boolean)

/** 垂直滑动调节类型: 屏幕左侧亮度 / 右侧音量 */
private enum class VerticalAdjustKind { BRIGHTNESS, VOLUME }

/** 垂直滑动调节 HUD 状态 (value 0f..1f) */
private data class AdjustHud(val kind: VerticalAdjustKind, val value: Float)

/** PlayerView 引用持有者 (跨重组稳定), 用于播放开始时主动隐藏控制器 */
private class PlayerViewRef {
    var view: PlayerView? = null
}

/**
 * 视频手势 — 双击播放/暂停, 左右滑动快退/快进, 左侧亮度/右侧音量, 长按倍速
 *
 * - 双击: 只有两次都是"真正的点击"(位移不超过触控阈值, 且不是拖动/倍速) 才切换播放/暂停,
 *   避免快速连续上下滑动被误判为双击; 第二下按下后先消费事件, 若中途变成拖动则按拖动处理
 * - 水平滑动: 滑满全屏对应 [SLIDE_PER_SCREEN_MS], 拖动中回调 [onSeekPreview] 供浮层预览, 松手执行 seek
 * - 垂直滑动: 起手在左半屏调节亮度, 右半屏调节音量, 滑满全屏 = 100%;
 *   拖动中回调 [onVerticalAdjust], 松手回调 [onVerticalAdjustEnd]
 * - 长按: 长按超时后进入倍速 (基础 [SPEED_BOOST_BASE]x, 仅播放中生效);
 *   保持按住向上滑加速至 [SPEED_BOOST_MAX]x, 向下滑减速至 [SPEED_BOOST_MIN]x, 松手立即恢复本速
 * - 起手落在中央按钮/底部进度条区域内时不拦截, 保证控制器自身交互
 */
private class VideoGestureListener(
    private val player: ExoPlayer,
    viewConfiguration: ViewConfiguration,
    density: Float,
    private val onSeekPreview: (SeekPreview?) -> Unit,
    private val onSpeedBoost: (Int?) -> Unit,
    private val readAdjustValue: (VerticalAdjustKind) -> Float,
    private val onVerticalAdjust: (VerticalAdjustKind, Float) -> Unit,
    private val onVerticalAdjustEnd: () -> Unit
) : View.OnTouchListener {

    private val touchSlop = viewConfiguration.scaledTouchSlop
    private val doubleTapSlop = viewConfiguration.scaledDoubleTapSlop
    private val doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val boostStepPx = SPEED_BOOST_STEP_DP * density

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastUpTime = 0L
    private var lastUpX = 0f
    private var lastUpY = 0f
    private var startPositionMs = 0L
    private var skipTouch = false
    private var seeking = false
    private var seekTargetMs = 0L
    private var movedBeyondSlop = false
    private var doubleTapCandidate = false
    private var lastTapValid = false
    private var verticalKind: VerticalAdjustKind? = null
    private var verticalStartValue = 0f
    private var speedBoosting = false
    private var boostLevel = SPEED_BOOST_BASE

    /** 长按超时回调: 播放中进入倍速模式 */
    private val longPressRunnable = Runnable {
        if (player.isPlaying && !seeking && verticalKind == null) {
            doubleTapCandidate = false
            speedBoosting = true
            boostLevel = SPEED_BOOST_BASE
            player.setPlaybackSpeed(SPEED_BOOST_BASE.toFloat())
            onSpeedBoost(SPEED_BOOST_BASE)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                skipTouch = isTouchOnControls(v, event)
                seeking = false
                movedBeyondSlop = false
                verticalKind = null
                seekTargetMs = 0L
                // 仅上一次是真正的点击时才可能是双击; 本序列先消费事件, 松手时再决定是否切换播放/暂停
                doubleTapCandidate = lastTapValid && !skipTouch &&
                    event.eventTime - lastUpTime in 1 until doubleTapTimeoutMs &&
                    abs(event.x - lastUpX) < doubleTapSlop &&
                    abs(event.y - lastUpY) < doubleTapSlop
                lastTapValid = false
                // 长按倍速计时 (起手在按钮/进度条上时不启用)
                if (!skipTouch) {
                    v.postDelayed(longPressRunnable, longPressTimeoutMs)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (speedBoosting) {
                    // 长按倍速中: 以上滑/下滑距离调节倍速档位
                    val level = (SPEED_BOOST_BASE + (downY - event.y) / boostStepPx).toInt()
                        .coerceIn(SPEED_BOOST_MIN, SPEED_BOOST_MAX)
                    if (level != boostLevel) {
                        boostLevel = level
                        player.setPlaybackSpeed(level.toFloat())
                        onSpeedBoost(level)
                    }
                    return true
                }
                if (verticalKind != null) {
                    updateVerticalAdjust(v, event)
                    return true
                }
                if (seeking) {
                    val duration = player.duration.takeIf { it > 0 } ?: return true
                    val width = v.width.coerceAtLeast(1)
                    seekTargetMs = (startPositionMs + (event.x - downX) / width * SLIDE_PER_SCREEN_MS)
                        .toLong().coerceIn(0L, duration)
                    onSeekPreview(SeekPreview(seekTargetMs, duration, seekTargetMs >= startPositionMs))
                    return true
                }
                val dx = event.x - downX
                val dy = event.y - downY
                // 位移超过触控阈值 → 取消长按计时, 且本序列不再可能是点击
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    movedBeyondSlop = true
                    v.removeCallbacks(longPressRunnable)
                }
                if (!movedBeyondSlop) return doubleTapCandidate
                if (skipTouch) return false
                doubleTapCandidate = false
                if (abs(dx) > abs(dy)) {
                    // 明显偏水平方向 → 进入快退/快进拖动
                    seeking = true
                    startPositionMs = player.currentPosition.coerceAtLeast(0L)
                } else {
                    // 明显偏垂直方向 → 左半屏亮度 / 右半屏音量
                    verticalKind = if (event.x < v.width / 2f) {
                        VerticalAdjustKind.BRIGHTNESS
                    } else {
                        VerticalAdjustKind.VOLUME
                    }
                    verticalStartValue = readAdjustValue(verticalKind!!)
                    updateVerticalAdjust(v, event)
                }
            }

            MotionEvent.ACTION_UP -> {
                v.removeCallbacks(longPressRunnable)
                if (speedBoosting) {
                    // 长按结束: 恢复本速, 本序列不参与单击/双击判定
                    speedBoosting = false
                    player.setPlaybackSpeed(1f)
                    onSpeedBoost(null)
                    lastTapValid = false
                    lastUpTime = 0L
                    return true
                }
                if (verticalKind != null) {
                    verticalKind = null
                    onVerticalAdjustEnd()
                    lastTapValid = false
                    lastUpTime = 0L
                    return true
                }
                if (seeking) {
                    seeking = false
                    val target = seekTargetMs
                    onSeekPreview(null)
                    player.seekTo(target)
                    lastTapValid = false
                    lastUpTime = 0L
                    return true
                }
                // 真正的点击: 位移与持续时间都在点击范围内 (滑动序列不会进入此分支)
                val isClick = !skipTouch && !movedBeyondSlop &&
                    abs(event.x - downX) <= touchSlop &&
                    abs(event.y - downY) <= touchSlop &&
                    event.eventTime - downTime <= longPressTimeoutMs
                if (doubleTapCandidate && isClick) {
                    doubleTapCandidate = false
                    if (player.isPlaying) player.pause() else player.play()
                    lastTapValid = false
                    lastUpTime = 0L
                    return true
                }
                doubleTapCandidate = false
                lastTapValid = isClick
                lastUpTime = if (isClick) event.eventTime else 0L
                lastUpX = event.x
                lastUpY = event.y
            }

            MotionEvent.ACTION_CANCEL -> {
                v.removeCallbacks(longPressRunnable)
                doubleTapCandidate = false
                lastTapValid = false
                if (speedBoosting) {
                    speedBoosting = false
                    player.setPlaybackSpeed(1f)
                    onSpeedBoost(null)
                }
                if (verticalKind != null) {
                    verticalKind = null
                    onVerticalAdjustEnd()
                }
                if (seeking) {
                    seeking = false
                    onSeekPreview(null)
                }
            }
        }
        // 拖动/倍速/双击候选中消费剩余事件; 其余放行给 PlayerView (单击显隐控制器/进度条拖动等)
        return seeking || doubleTapCandidate || speedBoosting || verticalKind != null
    }

    /** 计算并应用垂直滑动调节值 (向上增加, 滑满全屏 = 100%) */
    private fun updateVerticalAdjust(view: View, event: MotionEvent) {
        val kind = verticalKind ?: return
        val height = view.height.coerceAtLeast(1)
        val value = (verticalStartValue - (event.y - downY) / height).coerceIn(0f, 1f)
        onVerticalAdjust(kind, value)
    }
}

/** 从 Hook 视图向上查找所在 Dialog 的 Window (Compose Dialog 的根视图或其父级) */
private fun findDialogWindow(view: View): Window? {
    var current: View? = view
    while (current != null) {
        (current as? DialogWindowProvider)?.let { return it.window }
        current = current.parent as? View
    }
    return null
}

/** 仅在无法取得 Dialog 窗口时回退查找 Activity 窗口 */
private fun findActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** 读取当前屏幕亮度 (0f..1f): 优先窗口覆盖值, 否则读取系统亮度 */
private fun readScreenBrightness(context: Context, window: Window?): Float {
    val override = window?.attributes?.screenBrightness
        ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    if (override >= 0f) return override.coerceIn(0f, 1f)
    val systemValue = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (_: Settings.SettingNotFoundException) {
        128
    }
    return (systemValue / 255f).coerceIn(0f, 1f)
}

/** 触摸起手是否落在中央按钮/底部进度条区域内 (控制器自身交互不应被手势拦截) */
@SuppressLint("PrivateResource")
private fun isTouchOnControls(view: View, event: MotionEvent): Boolean {
    val location = IntArray(2)
    val ids = intArrayOf(Media3UiR.id.exo_center_controls, Media3UiR.id.exo_bottom_bar)
    return ids.any { id ->
        val area = view.findViewById<View>(id) ?: return@any false
        if (!area.isShown) return@any false
        area.getLocationOnScreen(location)
        event.rawX >= location[0] && event.rawX <= location[0] + area.width &&
            event.rawY >= location[1] && event.rawY <= location[1] + area.height
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
