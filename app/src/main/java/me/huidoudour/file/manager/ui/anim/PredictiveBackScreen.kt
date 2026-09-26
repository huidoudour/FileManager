package me.huidoudour.file.manager.ui.anim

import android.animation.ValueAnimator
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * 当前系统是否支持"能跟随手指走"的预测性返回。
 *
 * 只有 Android 14（API 34）及以上才会提供可逐帧读取的返回进度（`BackEventCompat.progress`），
 * 更低版本只能拿到一次性的返回事件；系统把动画时长缩放关掉时也一并视为不支持。
 */
fun isPredictiveBackSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        ValueAnimator.areAnimatorsEnabled()

/**
 * 应用内页面的预测性返回容器。
 *
 * 行为随系统能力自动适配：
 * - **支持预测性返回（Android 14+ 且动画开启）**：使用 [PredictiveBackHandler] 订阅返回手势，
 *   页面实时跟着手指向右滑出（露出下层界面）；松手继续滑走则完成返回，
 *   半途撤回则回弹到原位。
 * - **不支持的版本**：回退成普通的 [BackHandler]，按下即返回，与改动前的行为完全一致。
 *
 * @param onBack    页面真正返回（关闭）时的回调
 * @param modifier  容器修饰符
 * @param content   页面内容，回调参数 [requestBack] 用于"点返回按钮"等主动返回场景，
 *                  同样会先播放滑出动画再返回（不支持的版本上则立即返回）
 */
@Composable
fun PredictiveBackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (requestBack: () -> Unit) -> Unit
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    val supportsPredictiveBack = remember { isPredictiveBackSupported() }

    // 主动返回（返回按钮）：先播完滑出动画再真正返回；
    // 不支持的版本上直接返回，保持旧行为。
    val requestBack: () -> Unit = remember(supportsPredictiveBack) {
        if (supportsPredictiveBack) {
            {
                scope.launch {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    )
                    currentOnBack()
                }
            }
        } else {
            { currentOnBack() }
        }
    }

    if (supportsPredictiveBack) {
        PredictiveBackHandler { backEvents ->
            try {
                backEvents.collect { event ->
                    // 跟手：直接把手势进度映射成动画进度
                    progress.snapTo(event.progress.coerceIn(0f, 1f))
                }
                // 手势提交：补齐剩余位移后返回
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                )
                currentOnBack()
            } catch (_: CancellationException) {
                // 手势取消：回弹到原位
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    } else {
        BackHandler { currentOnBack() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // 注意：在图层阶段读取 progress，动画每帧不会触发界面重组
                val p = progress.value.coerceIn(0f, 1f)
                translationX = size.width * p
            }
    ) {
        content(requestBack)
    }
}
