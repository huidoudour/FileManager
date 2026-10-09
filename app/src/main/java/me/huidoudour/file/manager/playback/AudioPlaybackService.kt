package me.huidoudour.file.manager.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import me.huidoudour.file.manager.MainActivity

/**
 * 音频后台播放服务 — MediaSessionService
 *
 * 持有带媒体会话 (MediaSession) 的 ExoPlayer:
 * - 注册媒体会话后, 系统媒体控制 (通知栏 / 锁屏 / 耳机按键) 可直接控制播放
 * - 应用退到后台后继续播放 (前台服务类型 mediaPlayback, 自动发布媒体通知)
 * - 播放列表由 UI 侧通过 MediaController 下发, 支持自动顺序续播
 *
 * UI 侧通过 MediaController 连接本服务, 关闭预览对话框仅断开控制器, 不影响后台播放。
 */
class AudioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // 音频焦点: 被来电/其他应用打断时自动暂停, 获得焦点后恢复
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // 拔出耳机/蓝牙断开时自动暂停
            .setHandleAudioBecomingNoisy(true)
            // 后台播放保持 CPU 唤醒 (配合 WAKE_LOCK 权限)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(createSessionActivity())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** 应用被从最近任务移除时: 未在播放则停止服务 (播放中则保持后台播放) */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** 点击系统媒体通知/锁屏卡片时回到应用 */
    private fun createSessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
