package me.huidoudour.file.manager.ui.component

import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.LineEnding
import me.huidoudour.file.manager.util.TextEncoding
import me.huidoudour.file.manager.util.TextFileUtil
import me.huidoudour.file.manager.util.TextLoadResult
import java.io.File

/** 撤销历史的内存预算 (按字符数估算): 超出后丢弃最旧的快照, 大文件也能安全编辑 */
private const val UNDO_CHAR_BUDGET = 8_000_000

/** 连续输入合并为一步撤销的时间窗口 */
private const val UNDO_MERGE_INTERVAL_MS = 800L

/** 编辑会话状态: 用普通对象承载, 输入时不会触发额外重组 */
private class EditorSession {
    /** 最近一次已同步到历史的编辑内容 */
    var lastSnapshot = TextFieldValue()

    /** 待消费的历史恢复次数 (连续撤销/重做时避免误记为普通编辑) */
    var pendingRestores = 0

    var lastEditAt = 0L

    var undoChars = 0L
}

/**
 * 文本编辑 — 全屏编辑器
 *
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
    var savedText by remember(item.path) { mutableStateOf("") }
    var encoding by remember(item.path) { mutableStateOf(TextEncoding.UTF8) }
    var lineEnding by remember(item.path) { mutableStateOf(LineEnding.LF) }
    var saving by remember(item.path) { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    // 编辑缓冲: 文本与光标由 TextFieldState 承载, 撤销历史保存 TextFieldValue 快照
    val textState = rememberTextFieldState()
    val session = remember(item.path) { EditorSession() }
    val undoStack = remember(item.path) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(item.path) { mutableStateListOf<TextFieldValue>() }

    val document = (loadResult as? TextLoadResult.Success)?.document
    val readOnly = !item.canWrite
    val dirty = document != null && !textState.text.contentEquals(savedText)
    // 点额外按键时若编辑框已失焦, 用它在不打断输入的前提下唤回光标
    val focusRequester = remember { FocusRequester() }

    val savedMessage = stringResource(R.string.text_editor_saved)
    val saveFailedTemplate = stringResource(R.string.text_editor_save_failed)

    LaunchedEffect(item.path) {
        val result = TextFileUtil.load(file)
        if (result is TextLoadResult.Success) {
            encoding = result.document.encoding
            lineEnding = result.document.lineEnding
            savedText = result.document.content
            session.lastSnapshot = TextFieldValue(result.document.content, TextRange(0))
            textState.edit {
                replace(0, length, result.document.content)
                selection = TextRange(0)
            }
        }
        loadResult = result
    }

    fun requestClose() {
        if (dirty) showDiscardDialog = true else onDismiss()
    }

    /** 记录一步撤销快照, 超出字符预算时丢弃最旧的 */
    fun pushUndo(snapshot: TextFieldValue) {
        undoStack.add(snapshot)
        session.undoChars += snapshot.text.length
        while (undoStack.size > 1 && session.undoChars > UNDO_CHAR_BUDGET) {
            session.undoChars -= undoStack.removeAt(0).text.length
        }
    }

    /** 将历史快照写回编辑缓冲 (光标一并还原) */
    fun applySnapshot(snapshot: TextFieldValue) {
        session.pendingRestores++
        textState.edit {
            replace(0, length, snapshot.text)
            selection = snapshot.selection
        }
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        session.undoChars -= previous.text.length
        redoStack.add(TextFieldValue(textState.text.toString(), textState.selection))
        applySnapshot(previous)
        session.lastEditAt = 0L
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        pushUndo(TextFieldValue(textState.text.toString(), textState.selection))
        applySnapshot(next)
        session.lastEditAt = 0L
    }

    fun save() {
        if (!dirty || saving || readOnly) return
        val snapshot = textState.text.toString()
        saving = true
        scope.launch {
            val result = runCatching { TextFileUtil.save(file, snapshot, encoding, lineEnding) }
            saving = false
            if (result.isSuccess) {
                savedText = snapshot
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
     * 符号栏属于编辑辅助输入, 点击时编辑框可能已失焦,
     * 这里先唤回焦点再插入, 保证光标位置与键盘状态一致。
     */
    fun insertExtraKey(label: String) {
        if (readOnly) return
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // 编辑框尚未挂载时可忽略, 直接插入即可
        }
        val insert = if (label == EXTRA_KEY_TAB) "\t" else label
        textState.edit {
            val start = selection.min
            val end = selection.max
            replace(start, end, insert)
            selection = TextRange(start + insert.length)
        }
    }

    // 文本变化 → 记录撤销点; 程序恢复历史快照时跳过
    LaunchedEffect(textState) {
        snapshotFlow { TextFieldValue(textState.text.toString(), textState.selection) }
            .collect { next ->
                if (next == session.lastSnapshot) return@collect
                if (session.pendingRestores > 0) {
                    session.pendingRestores--
                    session.lastSnapshot = next
                    return@collect
                }
                val now = SystemClock.uptimeMillis()
                // 连续输入合并为一步撤销, 避免每个字符都进历史
                if (now - session.lastEditAt > UNDO_MERGE_INTERVAL_MS) {
                    pushUndo(session.lastSnapshot)
                    redoStack.clear()
                }
                session.lastEditAt = now
                session.lastSnapshot = next
            }
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 键盘 insets 交给 Compose 的 imePadding 处理, 窗口自身不平移/缩放
        val view = LocalView.current
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
            onDispose { }
        }

        val subtitle = when {
            loadResult == null -> stringResource(R.string.text_editor_loading)
            document == null -> stringResource(R.string.text_editor_load_failed)
            readOnly -> stringResource(R.string.text_editor_read_only)
            dirty -> stringResource(R.string.text_editor_dirty)
            else -> stringResource(R.string.text_editor_saved)
        }
        val subtitleError = loadResult != null && document == null

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                EditorTopBar(
                    title = item.name,
                    subtitle = subtitle,
                    subtitleError = subtitleError,
                    readOnly = readOnly,
                    canSave = dirty && !saving,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
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
                        val cursor = remember(textState.text, textState.selection) {
                            textPosition(textState.text, textState.selection.end)
                        }
                        EditorStatusBar(
                            encoding = encoding,
                            lineEnding = lineEnding,
                            cursorLine = cursor.first,
                            cursorColumn = cursor.second,
                            lineCount = remember(textState.text) {
                                textState.text.count { it == '\n' } + 1
                            },
                            charCount = textState.text.length
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

                    is TextLoadResult.Success -> BasicTextField(
                        state = textState,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false
                        ),
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        readOnly = readOnly
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
                    .statusBarsPadding()
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
    cursorLine: Int,
    cursorColumn: Int,
    lineCount: Int,
    charCount: Int
) {
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
                        cursorLine,
                        cursorColumn
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.text_editor_stats, lineCount, charCount),
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

/** 光标所在行列 (从 1 开始) */
private fun textPosition(text: CharSequence, offset: Int): Pair<Int, Int> {
    val end = offset.coerceIn(0, text.length)
    var line = 1
    var lineStart = 0
    for (index in 0 until end) {
        if (text[index] == '\n') {
            line++
            lineStart = index + 1
        }
    }
    return line to (end - lineStart + 1)
}
