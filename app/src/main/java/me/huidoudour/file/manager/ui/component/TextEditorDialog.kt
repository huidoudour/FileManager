package me.huidoudour.file.manager.ui.component

import android.content.Context
import android.graphics.Typeface
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.LineEnding
import me.huidoudour.file.manager.util.TextEncoding
import me.huidoudour.file.manager.util.TextFileUtil
import me.huidoudour.file.manager.util.TextLoadResult
import java.io.File

/** 换行开关与行号固定的记忆位置: 显示偏好与文件无关, 所有文档共用 */
private const val EDITOR_PREFS_NAME = "text_editor_prefs"
private const val KEY_WORD_WRAP = "word_wrap"
private const val KEY_LINE_NUMBER_PINNED = "line_number_pinned"

/** 编辑会话状态: 用普通对象承载, 只在保存/关闭等低频时机读写 */
private class EditorSession {
    /** 最近一次保存或加载的文本; 仅低频时机做全文比对, 输入路径不读它 */
    var savedText = ""
}

/** factory 创建编辑器后持有实例; Compose 侧通过它调用撤销/保存/额外按键等操作 */
private class EditorHolder {
    var editor: CodeEditor? = null
}

/** 底栏统计: 光标行列 (从 1 开始) / 总行数 / 字符数 */
private data class EditorStats(
    val cursorLine: Int,
    val cursorColumn: Int,
    val lineCount: Int,
    val charCount: Int
)

/** 右侧快速滚动条状态: 内容是否超出一屏 + 当前纵向滚动比例 (0..1) */
private class EditorScrollState {
    var scrollable by mutableStateOf(false)
    var fraction by mutableStateOf(0f)
}

/**
 * 文本编辑 — 全屏编辑器
 *
 * - 编辑区基于 Sora Editor (行虚拟化渲染), 大文件只排版可见行,
 *   300KB~1MB 的文本也能流畅输入与滚动, 不再是 Compose 文本框的整段排版
 * - 行号常显, 默认横向滚动时固定 (更多菜单可关); 更多菜单还控制自动换行 (记住上次选择),
 *   符号栏单行横滑, 可在底栏收起
 * - 右侧快速滚动条: 拖动拇指按比例跳转, 长文件不必逐屏滑动
 * - 顶栏: 关闭 / 文件名与保存状态 / 更多菜单 (保存 / 撤销 / 重做 / 自动换行 / 行号列固定)
 * - 底栏: 编码 · 换行风格 · 光标位置 · 行数与字符数
 * - 编码与换行沿用原文件 (见 [TextFileUtil]), 保存后其他应用打开时显示效果不变
 * - 无写权限的文件进入只读模式; 二进制内容或超过 [TextFileUtil.MAX_EDITABLE_BYTES]
 *   的文件提示改用其他应用打开
 * - 有未保存修改时关闭需二次确认
 */
@Composable
fun TextEditorDialog(
    item: FileItem,
    onSaved: () -> Unit,
    onOpenWith: () -> Unit,
    onDismiss: () -> Unit
) {
    val file = remember(item.path) { File(item.path) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var loadResult by remember(item.path) { mutableStateOf<TextLoadResult?>(null) }
    var encoding by remember(item.path) { mutableStateOf(TextEncoding.UTF8) }
    var lineEnding by remember(item.path) { mutableStateOf(LineEnding.LF) }
    var saving by remember(item.path) { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    // 未保存标记: 编辑器的事件回调里只置位, 输入路径上不做全文比较
    var dirty by remember(item.path) { mutableStateOf(false) }
    // 撤销/重做可用性: 由编辑器事件驱动刷新, 顶栏按钮据此启用
    var canUndo by remember(item.path) { mutableStateOf(false) }
    var canRedo by remember(item.path) { mutableStateOf(false) }
    // 底栏统计: 行虚拟化编辑器可直接 O(1) 取出行列/行数/字符数, 事件里即时刷新
    var stats by remember(item.path) { mutableStateOf(EditorStats(1, 1, 1, 0)) }
    val session = remember(item.path) { EditorSession() }
    val holder = remember(item.path) { EditorHolder() }
    // 快速滚动条状态: 滚动事件的回调里更新, 仅滚动条自身重组
    val scrollState = remember(item.path) { EditorScrollState() }
    // 换行开关: 首次打开沿用上次选择; 关闭换行后长行改为横向滚动
    val context = LocalContext.current
    val editorPrefs = remember {
        context.getSharedPreferences(EDITOR_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var wordWrap by remember(item.path) {
        mutableStateOf(editorPrefs.getBoolean(KEY_WORD_WRAP, true))
    }
    // 行号列固定: 关闭换行横向滚动时, 行号槽是否留在原地
    var lineNumberPinned by remember(item.path) {
        mutableStateOf(editorPrefs.getBoolean(KEY_LINE_NUMBER_PINNED, true))
    }
    // 符号栏展开状态: 键盘弹出时可收起, 给编辑区让出空间
    var keysExpanded by rememberSaveable(item.path) { mutableStateOf(true) }

    val document = (loadResult as? TextLoadResult.Success)?.document
    val readOnly = !item.canWrite

    val savedMessage = stringResource(R.string.text_editor_saved)
    val saveFailedTemplate = stringResource(R.string.text_editor_save_failed)

    /** 刷新底栏统计与撤销状态; 读取均为 O(1), 可在每次输入/光标移动时调用 */
    fun refreshEditorState(editor: CodeEditor) {
        val cursor = editor.cursor
        val text = editor.text
        val next = EditorStats(
            cursorLine = cursor.rightLine + 1,
            cursorColumn = cursor.rightColumn + 1,
            lineCount = text.lineCount,
            charCount = text.length
        )
        if (next != stats) stats = next
        canUndo = editor.canUndo()
        canRedo = editor.canRedo()
    }

    /** 同步快速滚动条: 内容超过一屏才显示, 位置取当前纵向偏移 */
    fun syncScrollBar(editor: CodeEditor) {
        val max = editor.scrollMaxY
        scrollState.scrollable = max > 0
        scrollState.fraction = if (max > 0) {
            (editor.offsetY.toFloat() / max).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * 拖动快速滚动条: 按比例跳转。
     *
     * 拇指位置以实际滚动偏移为准 (见 [syncScrollBar]), 因此与正文位置一一对应;
     * startScroll(duration = 0) + abortAnimation 与 Sora 内部的无动画跳转一致, 立即生效。
     */
    fun seekTo(fraction: Float) {
        val editor = holder.editor ?: return
        val max = editor.scrollMaxY
        if (max <= 0) return
        val scroller = editor.getScroller()
        val target = (fraction.coerceIn(0f, 1f) * max).roundToInt()
        // 先结束进行中的惯性滑动, 否则残余动画会和手动定位抢位置
        scroller.forceFinished(true)
        scroller.startScroll(
            scroller.getCurrX(),
            scroller.getCurrY(),
            0,
            target - scroller.getCurrY(),
            0
        )
        scroller.abortAnimation()
        editor.postInvalidateOnAnimation()
        syncScrollBar(editor)
    }

    LaunchedEffect(item.path) {
        val result = TextFileUtil.load(file)
        if (result is TextLoadResult.Success) {
            encoding = result.document.encoding
            lineEnding = result.document.lineEnding
            session.savedText = result.document.content
        }
        loadResult = result
    }

    fun requestClose() {
        val editor = holder.editor
        // 关闭前精确比对一次 (仅用户主动关闭时发生, 低频, 允许一次全文比较);
        // 没有输入过 (dirty 为 false) 时直接跳过比较
        val hasUnsavedChanges = editor != null && dirty &&
            editor.text.toString() != session.savedText
        dirty = hasUnsavedChanges
        if (hasUnsavedChanges) showDiscardDialog = true else onDismiss()
    }

    fun undo() {
        val editor = holder.editor ?: return
        if (!editor.canUndo()) return
        editor.undo()
        // 撤销可能正好回到已保存内容, 精确刷新未保存标记 (低频操作)
        refreshEditorState(editor)
        dirty = editor.text.toString() != session.savedText
    }

    fun redo() {
        val editor = holder.editor ?: return
        if (!editor.canRedo()) return
        editor.redo()
        refreshEditorState(editor)
        dirty = editor.text.toString() != session.savedText
    }

    fun save() {
        val editor = holder.editor ?: return
        if (!dirty || saving || readOnly) return
        val snapshot = editor.text.toString()
        saving = true
        scope.launch {
            val result = runCatching { TextFileUtil.save(file, snapshot, encoding, lineEnding) }
            saving = false
            if (result.isSuccess) {
                session.savedText = snapshot
                dirty = false
                onSaved()
                snackbarHostState.showSnackbar(savedMessage)
            } else {
                val reason = result.exceptionOrNull()?.message.orEmpty()
                snackbarHostState.showSnackbar(saveFailedTemplate.format(reason))
            }
        }
    }

    /**
     * 在光标处插入额外键 (选中内容会被替换)。
     *
     * 符号栏属于编辑辅助输入, 点击时编辑框可能已失焦且键盘已收起,
     * 先唤回焦点与键盘, 再走编辑器的文本提交通道, 保证撤销历史与光标位置一致。
     */
    fun insertExtraKey(label: String) {
        val editor = holder.editor ?: return
        if (readOnly) return
        val insert = if (label == EXTRA_KEY_TAB) "\t" else label
        editor.showSoftInput()
        // 关闭自动缩进与符号补全: 与普通文本编辑行为一致, 插入内容与按键一致
        editor.commitText(insert, false, false)
    }

    fun toggleWordWrap() {
        wordWrap = !wordWrap
        holder.editor?.let { editor ->
            editor.setWordwrap(wordWrap)
            // 换行改变内容高度: 布局完成后重新同步滚动条可见性
            editor.post { syncScrollBar(editor) }
        }
        editorPrefs.edit { putBoolean(KEY_WORD_WRAP, wordWrap) }
    }

    fun toggleLineNumberPin() {
        lineNumberPinned = !lineNumberPinned
        holder.editor?.setPinLineNumber(lineNumberPinned)
        editorPrefs.edit { putBoolean(KEY_LINE_NUMBER_PINNED, lineNumberPinned) }
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 键盘 insets 交给 Compose 的 imePadding 处理, 窗口自身不平移/缩放;
        // 编辑器会盖住整个屏幕 (含状态栏区域), 而 Dialog 是独立 window,
        // 系统栏图标默认按浅色绘制, 浅色主题下会与顶栏底色撞在一起看不清,
        // 所以这里显式按 App 主题背景的明暗切图标颜色 (不跟随系统深色模式)。
        val view = LocalView.current
        val lightSystemBarBackground = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        DisposableEffect(view, lightSystemBarBackground) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
            window?.let { WindowCompat.getInsetsController(it, view) }?.apply {
                isAppearanceLightStatusBars = lightSystemBarBackground
                isAppearanceLightNavigationBars = lightSystemBarBackground
            }
            onDispose { }
        }

        val subtitleError = loadResult != null && document == null
        val backgroundColor = MaterialTheme.colorScheme.background
        val textColor = MaterialTheme.colorScheme.onSurface
        // 选区底色对齐 Compose 默认的 primary 40% 透明度
        val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        val cursorColor = MaterialTheme.colorScheme.primary
        val actionWindowBackground = MaterialTheme.colorScheme.surfaceVariant
        val actionWindowIcon = MaterialTheme.colorScheme.onSurfaceVariant
        val mutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        val dividerColor = MaterialTheme.colorScheme.outlineVariant
        // 字号与行高沿用旧编辑框: 14sp 正文 / 22sp 行高
        val density = LocalDensity.current
        val textSizePx = with(density) { 14.sp.toPx() }
        val lineHeightPx = with(density) { 22.sp.toPx() }
        // 行号左侧留白, 紧贴编辑区边缘
        val lineNumberMarginLeftPx = with(density) { 8.dp.toPx() }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // 易变状态在顶栏自身的组合范围内读取, 输入时不牵连整个编辑页重组
                val subtitle = when {
                    loadResult == null -> stringResource(R.string.text_editor_loading)
                    document == null -> stringResource(R.string.text_editor_load_failed)
                    readOnly -> stringResource(R.string.text_editor_read_only)
                    dirty -> stringResource(R.string.text_editor_dirty)
                    else -> stringResource(R.string.text_editor_saved)
                }
                EditorTopBar(
                    title = item.name,
                    subtitle = subtitle,
                    subtitleError = subtitleError,
                    readOnly = readOnly,
                    wordWrap = wordWrap,
                    lineNumberPinned = lineNumberPinned,
                    canSave = dirty && !saving,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onClose = ::requestClose,
                    onToggleWordWrap = ::toggleWordWrap,
                    onToggleLineNumberPin = ::toggleLineNumberPin,
                    onUndo = ::undo,
                    onRedo = ::redo,
                    onSave = ::save
                )
            },
            bottomBar = {
                if (document != null) {
                    Column {
                        // 额外按键栏: 常用符号快捷输入 (只读时无意义, 不显示; 可收起)
                        if (!readOnly && keysExpanded) {
                            EditorExtraKeysBar(onKey = ::insertExtraKey)
                        }
                        // 统计值通过 lambda 延迟到状态栏内读取: 数值更新只重组状态栏
                        EditorStatusBar(
                            encoding = encoding,
                            lineEnding = lineEnding,
                            stats = { stats },
                            keysExpanded = if (readOnly) null else keysExpanded,
                            onToggleKeys = { keysExpanded = !keysExpanded }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (val result = loadResult) {
                    null -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )

                    is TextLoadResult.Success -> AndroidView(
                        factory = { context ->
                            CodeEditor(context).apply {
                                holder.editor = this
                                // 纯文本编辑: 等宽字体, 行号常显且横向滚动时固定, 关闭当前行高亮/滚动条/括号匹配
                                setTypefaceText(Typeface.MONOSPACE)
                                setTypefaceLineNumber(Typeface.MONOSPACE)
                                setLineNumberEnabled(true)
                                // 关闭换行后内容可横向滚动, 行号槽默认不跟随滚动 (菜单可切换)
                                setPinLineNumber(lineNumberPinned)
                                setLineNumberMarginLeft(lineNumberMarginLeftPx)
                                setHighlightCurrentLine(false)
                                setHighlightBracketPair(false)
                                setScrollBarEnabled(false)
                                // 去掉底部半屏留白: 滚动上限与正文高度一致, 滚动条比例才对应真实位置
                                setVerticalExtraSpaceFactor(0f)
                                setWordwrap(wordWrap)
                                setTabWidth(4)
                                props.autoIndent = false
                                props.symbolPairAutoCompletion = false
                                props.highlightMatchingDelimiters = false
                                setEditable(!readOnly)
                                setTextSizePx(textSizePx)
                                val fontMetrics = textPaint.fontMetricsInt
                                setLineSpacingExtra(
                                    (lineHeightPx - (fontMetrics.descent - fontMetrics.ascent))
                                        .coerceAtLeast(0f)
                                )
                                colorScheme = EditorColorScheme()
                                applyPlainTextColors(
                                    backgroundColor = backgroundColor.toArgb(),
                                    textColor = textColor.toArgb(),
                                    mutedTextColor = mutedTextColor.toArgb(),
                                    dividerColor = dividerColor.toArgb(),
                                    selectionColor = selectionColor.toArgb(),
                                    cursorColor = cursorColor.toArgb(),
                                    actionWindowBackground = actionWindowBackground.toArgb(),
                                    actionWindowIcon = actionWindowIcon.toArgb()
                                )
                                // 内容变化: 置位未保存标记并刷新撤销/统计状态;
                                // 初始加载 (ACTION_SET_NEW_TEXT) 不算用户输入
                                subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                                    if (event.action == ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                                        return@subscribeEvent
                                    }
                                    dirty = true
                                    refreshEditorState(this@apply)
                                }
                                subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
                                    refreshEditorState(this@apply)
                                }
                                // 逐帧同步: 编辑器在绘制前由 computeScroll() 写入真实偏移,
                                // 该回调与正文在同一帧触发, 拖动与惯性滑动时拇指既不跳跃也不落后
                                setOnScrollChangeListener { _, _, _, _, _ -> syncScrollBar(this) }
                                // 补充同步: 仅滚动上限变化时 (换行/撤销等改变布局高度) 偏移可能不变,
                                // 上面的回调不会触发, 这里按最新上限重算比例
                                subscribeEvent(ScrollEvent::class.java) { _, _ ->
                                    syncScrollBar(this@apply)
                                }
                                setText(session.savedText)
                                setSelection(0, 0)
                                // 挂载后同步一次统计与历史状态; 期间若已产生输入则不覆盖脏标记
                                post {
                                    refreshEditorState(this@apply)
                                    syncScrollBar(this@apply)
                                    if (!canUndo()) dirty = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            // 行号槽自带左间距, 左侧只留少量空白
                            .padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        update = { editor ->
                            editor.setEditable(!readOnly)
                            editor.applyPlainTextColors(
                                backgroundColor = backgroundColor.toArgb(),
                                textColor = textColor.toArgb(),
                                mutedTextColor = mutedTextColor.toArgb(),
                                dividerColor = dividerColor.toArgb(),
                                selectionColor = selectionColor.toArgb(),
                                cursorColor = cursorColor.toArgb(),
                                actionWindowBackground = actionWindowBackground.toArgb(),
                                actionWindowIcon = actionWindowIcon.toArgb()
                            )
                        },
                        onRelease = { editor ->
                            holder.editor = null
                            editor.release()
                        }
                    )

                    TextLoadResult.TooLarge -> EditorMessage(
                        icon = Icons.Filled.Description,
                        message = stringResource(
                            R.string.text_editor_too_large,
                            FileItem.formatSize(TextFileUtil.MAX_EDITABLE_BYTES)
                        ),
                        onOpenWith = onOpenWith
                    )

                    TextLoadResult.Binary -> EditorMessage(
                        icon = Icons.Filled.Description,
                        message = stringResource(R.string.text_editor_binary),
                        onOpenWith = onOpenWith
                    )

                    is TextLoadResult.Failure -> EditorMessage(
                        icon = Icons.Filled.ErrorOutline,
                        message = stringResource(R.string.text_editor_load_failed),
                        onOpenWith = onOpenWith
                    )
                }
                // 快速滚动条: 盖在编辑区右边缘 (编辑器右侧内边距内), 内容不足一屏时自行隐藏
                if (document != null) {
                    EditorFastScrollBar(
                        scrollState = scrollState,
                        onSeek = ::seekTo,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            // 与编辑区的上下内边距一致: 轨道高度等于正文可视高度,
                            // 拇指到顶/到底才对应首行/末行, 比例换算没有固定偏移
                            .padding(top = 12.dp, bottom = 12.dp)
                    )
                }
            }
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text(stringResource(R.string.text_editor_discard_title)) },
                text = {
                    Text(stringResource(R.string.text_editor_discard_message, item.name))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardDialog = false
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.text_editor_discard_confirm),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

/** 同步编辑器配色到当前 Material 主题: 背景/正文/行号/选区/光标/文本操作浮窗 */
private fun CodeEditor.applyPlainTextColors(
    backgroundColor: Int,
    textColor: Int,
    mutedTextColor: Int,
    dividerColor: Int,
    selectionColor: Int,
    cursorColor: Int,
    actionWindowBackground: Int,
    actionWindowIcon: Int
) {
    setBackgroundColor(backgroundColor)
    colorScheme.apply {
        setColor(EditorColorScheme.WHOLE_BACKGROUND, backgroundColor)
        setColor(EditorColorScheme.TEXT_NORMAL, textColor)
        // 行号与行号槽: 次要文字色 + 与编辑区同底的背景
        setColor(EditorColorScheme.LINE_NUMBER, mutedTextColor)
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, textColor)
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, backgroundColor)
        setColor(EditorColorScheme.LINE_DIVIDER, dividerColor)
        setColor(EditorColorScheme.LINE_NUMBER_PANEL, actionWindowBackground)
        setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, mutedTextColor)
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selectionColor)
        setColor(EditorColorScheme.SELECTION_INSERT, cursorColor)
        setColor(EditorColorScheme.SELECTION_HANDLE, cursorColor)
        setColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, actionWindowBackground)
        setColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, actionWindowIcon)
    }
}

/** 顶栏: 关闭 + 文件名/状态 + 更多菜单 (保存/撤销/重做/自动换行/行号列固定), 与主界面 TopAppBar 同为 surface 底色 */
@Composable
private fun EditorTopBar(
    title: String,
    subtitle: String,
    subtitleError: Boolean,
    readOnly: Boolean,
    wordWrap: Boolean,
    lineNumberPinned: Boolean,
    canSave: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onClose: () -> Unit,
    onToggleWordWrap: () -> Unit,
    onToggleLineNumberPin: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 同时避开状态栏与刘海/挖孔 (横屏时挖孔在侧边)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .heightIn(min = 64.dp)
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (subtitleError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.more)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        // 保存: 菜单第一项, 只读文件没有保存入口
                        if (!readOnly) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.text_editor_save)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Save,
                                        contentDescription = null
                                    )
                                },
                                enabled = canSave,
                                onClick = {
                                    menuExpanded = false
                                    onSave()
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.text_editor_undo)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = null
                                )
                            },
                            enabled = canUndo,
                            onClick = {
                                menuExpanded = false
                                onUndo()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.text_editor_redo)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = null
                                )
                            },
                            enabled = canRedo,
                            onClick = {
                                menuExpanded = false
                                onRedo()
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (wordWrap) {
                                            R.string.text_editor_word_wrap_on
                                        } else {
                                            R.string.text_editor_word_wrap_off
                                        }
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.WrapText,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleWordWrap()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (lineNumberPinned) {
                                            R.string.text_editor_line_number_pin_on
                                        } else {
                                            R.string.text_editor_line_number_pin_off
                                        }
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleLineNumberPin()
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** 底栏: 符号栏开关 · 编码 · 换行风格 | 光标位置 | 行数与字符数 */
@Composable
private fun EditorStatusBar(
    encoding: TextEncoding,
    lineEnding: LineEnding,
    stats: () -> EditorStats,
    keysExpanded: Boolean?,
    onToggleKeys: () -> Unit
) {
    val current = stats()
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 键盘弹出时导航栏已被 IME 覆盖: 排除这段 inset, 避免底栏与键盘之间多出一条空白
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                            .exclude(WindowInsets.ime)
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 符号栏收起后, 这里保留展开入口
                if (keysExpanded != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onToggleKeys),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (keysExpanded) {
                                Icons.Filled.KeyboardArrowDown
                            } else {
                                Icons.Filled.KeyboardArrowUp
                            },
                            contentDescription = stringResource(
                                if (keysExpanded) {
                                    R.string.text_editor_keys_collapse
                                } else {
                                    R.string.text_editor_keys_expand
                                }
                            ),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "${encoding.label} · ${lineEnding.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(
                        R.string.text_editor_cursor,
                        current.cursorLine,
                        current.cursorColumn
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        R.string.text_editor_stats,
                        current.lineCount,
                        current.charCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 右侧快速滚动条: 内容超过一屏时显示, 拖动拇指按比例跳转
 *
 * 拇指位置取编辑器真实偏移 (见 [syncScrollBar] 与编辑器控件的滚动回调), 与正文同帧更新,
 * 拖动和惯性滑动时都随正文一起移动, 不会先跳到终点;
 * 按在拇指上拖动时保持相对位置, 按在轨道空白处才直接跳到对应位置。
 */
@Composable
private fun EditorFastScrollBar(
    scrollState: EditorScrollState,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!scrollState.scrollable) return
    val density = LocalDensity.current
    val thumbHeight = 44.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }
    val grabSlopPx = with(density) { 6.dp.toPx() }
    var trackHeightPx by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    Box(
        modifier = modifier
            .width(16.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(trackHeightPx, thumbHeightPx) {
                if (trackHeightPx <= thumbHeightPx) return@pointerInput
                fun seekAt(thumbTopY: Float) {
                    val fraction = (thumbTopY / travel).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
                // 手指与拇指中心的偏移: 按在拇指上时保持相对位置, 拇指不会跳一下
                var grabOffsetPx = 0f
                detectDragGestures(
                    orientationLock = null,
                    // 按下即响应, 拖动不必先越过触摸斜率, 跟手更直接
                    shouldAwaitTouchSlop = { false },
                    onDragStart = { down, _, _ ->
                        dragging = true
                        val thumbCenter = thumbHeightPx / 2f + travel * scrollState.fraction
                        val onThumb = abs(down.position.y - thumbCenter) <=
                            thumbHeightPx / 2f + grabSlopPx
                        grabOffsetPx = if (onThumb) down.position.y - thumbCenter else 0f
                        if (!onThumb) seekAt(down.position.y - thumbHeightPx / 2f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        seekAt(change.position.y - grabOffsetPx - thumbHeightPx / 2f)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // 位置在布局阶段读取 fraction: 滚动时不触发重组, 只重新摆放
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(x = 0, y = (travel * scrollState.fraction).roundToInt())
                }
                .width(if (dragging) 6.dp else 4.dp)
                .height(thumbHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.7f else 0.45f)
                )
        )
    }
}

/** 额外按键栏 Tab 键的标签 (实际插入制表符) */
private const val EXTRA_KEY_TAB = "Tab"

/**
 * 额外按键栏的全部按键: 高频符号 + Tab
 *
 * 原先分两行平铺, 键盘弹出后底栏过高挤占编辑区;
 * 改为单行横向滑动, 按键保持固定宽度, 不随屏宽拉伸。
 */
private val EXTRA_KEYS = listOf(
    EXTRA_KEY_TAB, "(", ")", "[", "]", "{", "}", "<", ">", "\"", "'",
    "_", "#", "$", "&", "*", "+", "-", "=", "/", "\\", "|"
)

/** 额外按键栏: 单行常用符号, 可横向滑动 */
@Composable
private fun EditorExtraKeysBar(onKey: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EXTRA_KEYS.forEach { key ->
                    ExtraKeyButton(label = key, onClick = { onKey(key) })
                }
            }
        }
    }
}

/** 单个符号键: 等宽字体居中, 固定小格子 (Tab 稍宽) */
@Composable
private fun ExtraKeyButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(if (label == EXTRA_KEY_TAB) 44.dp else 36.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 无法编辑时的占位: 图标 + 说明 + 用其他应用打开 */
@Composable
private fun EditorMessage(
    icon: ImageVector,
    message: String,
    onOpenWith: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onOpenWith) {
            Text(stringResource(R.string.media_preview_open_with))
        }
    }
}
