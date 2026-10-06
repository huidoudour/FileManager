package me.huidoudour.file.manager.ui.anim

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 主动退出（关闭）时的动画风格。
 *
 * 三者都走同一套"轻微缩放 + 淡出"的动作，只是节奏略有区别。
 *
 * 注意：这里的风格只用于**用户主动触发**的退出（点取消、选取完成、保存完成、菜单退出应用）。
 * 返回手势 / 返回键回到根目录时不会走这里，而是把返回交还给系统，
 * 由系统播放预测性返回动画（窗口随手指缩小并实时预览即将返回的界面）。
 */
enum class ExitStyle {
    /** 成功把结果返回给调用方（选取文件 / 保存文件完成） */
    RETURN_RESULT,

    /** 用户主动取消（取消按钮） */
    CANCEL,

    /** 用户主动退出应用（右上角菜单触发） */
    EXIT
}

/**
 * 单次退出的动画参数。
 *
 * @param durationMillis 动画时长（毫秒）
 * @param targetScale    动画结束时的缩放比例，越接近 1 越"静"，只保留淡出感
 * @param easing         缓动曲线
 */
private data class ExitSpec(
    val durationMillis: Int,
    val targetScale: Float,
    val easing: Easing = FastOutSlowInEasing
)

private fun specOf(style: ExitStyle): ExitSpec = when (style) {
    // 返回结果：稍微慢一点、缩放略明显，像是把内容"递"出去
    ExitStyle.RETURN_RESULT -> ExitSpec(durationMillis = 200, targetScale = 0.97f)

    // 取消：最快、几乎没有缩放，干脆利落
    ExitStyle.CANCEL -> ExitSpec(durationMillis = 150, targetScale = 0.99f)

    // 主动退出应用：一次轻缓的谢幕，比取消多一丝仪式感
    ExitStyle.EXIT -> ExitSpec(durationMillis = 200, targetScale = 0.97f)
}

/**
 * 退出过渡动画容器。
 *
 * 当 [exitStyle] 从 `null` 变为某个 [ExitStyle] 时，界面会做一个很克制的
 * "轻微缩小 + 淡出"，动画播放完毕后回调 [onExitFinished]
 * （通常在这里调用 `Activity.finish()`）。
 *
 * 这里刻意不使用位移、圆角收起或背景压暗：整体观感更像一次干净的交叉淡出，
 * 再配合窗口本身的淡出（由 Activity 的窗口转场动画负责），
 * 视觉上就是平滑地溶解回调用方的界面。
 *
 * 若系统关闭了动画（开发者选项中的动画时长缩放为 0），则直接回调，不做延迟。
 *
 * @param exitStyle       当前的退出风格，`null` 表示不退出
 * @param onExitFinished  动画播放完毕（或无需播放）时的回调
 * @param modifier        容器修饰符
 * @param content         需要被动画包裹的真实页面内容
 */
@Composable
fun ExitTransitionHost(
    exitStyle: ExitStyle?,
    onExitFinished: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val currentOnExitFinished by rememberUpdatedState(onExitFinished)
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(exitStyle) {
        val style = exitStyle ?: return@LaunchedEffect
        if (finished) return@LaunchedEffect

        // 系统禁用了动画 → 立即结束，保证使用体验不被拖慢
        if (!ValueAnimator.areAnimatorsEnabled()) {
            finished = true
            currentOnExitFinished()
            return@LaunchedEffect
        }

        val spec = specOf(style)
        progress.animateTo(1f, tween(durationMillis = spec.durationMillis, easing = spec.easing))
        finished = true
        currentOnExitFinished()
    }

    // 注意：下面的 progress 在 deferred read（图层阶段）中读取，
    // 这样动画每帧只会更新图层属性，不会触发整棵界面的重组，动画更顺滑。
    val targetScale = exitStyle?.let { specOf(it).targetScale } ?: 1f

    Box(
        // 固定一层主题背景色，避免内容淡出时露出窗口底色造成闪白 / 闪黑
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    alpha = 1f - p
                    val s = 1f + (targetScale - 1f) * p
                    scaleX = s
                    scaleY = s
                }
        ) {
            content()
        }
    }
}
