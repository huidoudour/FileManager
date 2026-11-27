package me.huidoudour.file.manager.ui.component

import android.graphics.Typeface
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.launch
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.LineEnding
import me.huidoudour.file.manager.util.TextEncoding
import me.huidoudour.file.manager.util.TextFileUtil
import me.huidoudour.file.manager.util.TextLoadResult
import java.io.File

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

/**
 * 文本编辑 — 全屏编辑器
 *
 * - 编辑区基于 Sora Editor (行虚拟化渲染), 大文件只排版可见行,
 *   300KB~1MB 的文本也能流畅输入与滚动, 不再是 Compose 文本框的整段排版
 * - 顶栏: 关闭 / 文件名与保存状态 / 撤销 / 重做 / 保存
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
        // 字号与行高沿用旧编辑框: 14sp 正文 / 22sp 行高
        val density = LocalDensity.current
        val textSizePx = with(density) { 14.sp.toPx() }
        val lineHeightPx = with(density) { 22.sp.toPx() }

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
                    canSave = dirty && !saving,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onClose = ::requestClose,
                    onUndo = ::undo,
                    onRedo = ::redo,
                    onSave = ::save
                )
            },
            bottomBar = {
                if (document != null) {
                    Column {
                        // 额外按键栏: 常用符号快捷输入 (只读时无意义, 不显示)
                        if (!readOnly) {
                            EditorExtraKeysBar(onKey = ::insertExtraKey)
                        }
                        // 统计值通过 lambda 延迟到状态栏内读取: 数值更新只重组状态栏
                        EditorStatusBar(
                            encoding = encoding,
                            lineEnding = lineEnding,
                            stats = { stats }
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
                                // 纯文本编辑: 等宽字体, 关闭行号/当前行高亮/滚动条/括号匹配
                                setTypefaceText(Typeface.MONOSPACE)
                                setLineNumberEnabled(false)
                                setHighlightCurrentLine(false)
                                setHighlightBracketPair(false)
                                setScrollBarEnabled(false)
                                setWordwrap(true)
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
                                setText(session.savedText)
                                setSelection(0, 0)
                                // 挂载后同步一次统计与历史状态; 期间若已产生输入则不覆盖脏标记
                                post {
                                    refreshEditorState(this@apply)
                                    if (!canUndo()) dirty = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        update = { editor ->
                            editor.setEditable(!readOnly)
                            editor.applyPlainTextColors(
                                backgroundColor = backgroundColor.toArgb(),
                                textColor = textColor.toArgb(),
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

/** 同步编辑器配色到当前 Material 主题: 纯文本编辑只需背景/正文/选区/光标/文本操作浮窗 */
private fun CodeEditor.applyPlainTextColors(
    backgroundColor: Int,
    textColor: Int,
    selectionColor: Int,
    cursorColor: Int,
    actionWindowBackground: Int,
    actionWindowIcon: Int
) {
    setBackgroundColor(backgroundColor)
    colorScheme.apply {
        setColor(EditorColorScheme.WHOLE_BACKGROUND, backgroundColor)
        setColor(EditorColorScheme.TEXT_NORMAL, textColor)
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selectionColor)
        setColor(EditorColorScheme.SELECTION_INSERT, cursorColor)
        setColor(EditorColorScheme.SELECTION_HANDLE, cursorColor)
        setColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, actionWindowBackground)
        setColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, actionWindowIcon)
    }
}

/** 顶栏: 关闭 + 文件名/状态 + 撤销/重做 + 保存 (与主界面 TopAppBar 同为 surface 底色) */
@Composable
private fun EditorTopBar(
    title: String,
    subtitle: String,
    subtitleError: Boolean,
    readOnly: Boolean,
    canSave: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit
) {
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
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.text_editor_undo)
                    )
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.text_editor_redo)
                    )
                }
                if (!readOnly) {
                    TextButton(onClick = onSave, enabled = canSave) {
                        Text(
                            text = stringResource(R.string.text_editor_save),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** 底栏: 编码 · 换行风格 | 光标位置 | 行数与字符数 */
@Composable
private fun EditorStatusBar(
    encoding: TextEncoding,
    lineEnding: LineEnding,
    stats: () -> EditorStats
) {
    val current = stats()
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

/** 额外按键栏 Tab 键的标签 (实际插入制表符) */
private const val EXTRA_KEY_TAB = "Tab"

/**
 * 额外按键栏: 常用符号快捷输入
 *
 * 布局思路对齐 Termux 的 extra-keys (等宽小格子、按屏宽平分),
 * 只保留文本/代码编辑里高频的符号 —— 全部加满会挤窄按键, 反而不便点按。
 */
private val EXTRA_KEY_ROWS = listOf(
    listOf(EXTRA_KEY_TAB, "(", ")", "[", "]", "{", "}", "<", ">", "\"", "'"),
    listOf("_", "#", "$", "&", "*", "+", "-", "=", "/", "\\", "|")
)

/** 额外按键栏: 两行常用符号, 紧贴状态栏上方 */
@Composable
private fun EditorExtraKeysBar(onKey: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EXTRA_KEY_ROWS.forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowKeys.forEach { key ->
                            ExtraKeyButton(
                                label = key,
                                modifier = Modifier.weight(1f),
                                onClick = { onKey(key) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单个符号键: 等宽字体居中, 底色与状态栏区分但保持同色系 */
@Composable
private fun ExtraKeyButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .height(34.dp),
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
