package me.huidoudour.file.manager.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.FileCategory
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.util.MediaProbe
import me.huidoudour.file.manager.util.SortMode
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File

/**
 * 文件管理器主界面 — MaterialFiles 风格
 */
@Composable
fun FileListScreen(
    viewModel: FileManagerViewModel,
    onFileSelected: ((FileItem) -> Unit)? = null,
    onPickCancelled: (() -> Unit)? = null,
    onSaveConfirmed: (() -> Unit)? = null,
    onSaveCancelled: (() -> Unit)? = null,
    onShareFiles: ((List<FileItem>) -> Unit)? = null,
    onOpenWith: ((FileItem) -> Unit)? = null,
    onCreateShortcut: ((String) -> Unit)? = null,
    onExitApp: (() -> Unit)? = null
) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val sortDirectoriesFirst by viewModel.sortDirectoriesFirst.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val operationProgress by viewModel.operationProgress.collectAsState()
    val pasteConflicts by viewModel.pasteConflicts.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearchLoading by viewModel.isSearchLoading.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val hiddenQuickDirs by viewModel.hiddenQuickDirs.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val showThumbnails by viewModel.showThumbnails.collectAsState()
    val pinnedFolders by viewModel.pinnedFolders.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val propertiesTarget by viewModel.propertiesTarget.collectAsState()
    val propertiesStats by viewModel.propertiesStats.collectAsState()
    val saveFileCount by viewModel.saveFileCount.collectAsState()
    val pickerMode by viewModel.pickerMode.collectAsState()
    val saveMode by viewModel.saveMode.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var createDialogIsFolder by remember { mutableStateOf<Boolean?>(null) }
    var showNavigateToDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var actionTarget by remember { mutableStateOf<FileItem?>(null) }
    var previewTarget by remember { mutableStateOf<FileItem?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val selectionMode = selectedPaths.isNotEmpty()
    val searching = isSearchActive && searchQuery.isNotBlank()
    val displayedFiles = if (searching) searchResults else files

    val internalStorageLabel = stringResource(R.string.internal_storage)

    // 顶栏副标题: 条目统计 (照搬 MaterialFiles 的 getSubtitle)
    val subtitle = when {
        errorMessage != null -> stringResource(R.string.subtitle_error)
        isLoading && !searching -> stringResource(R.string.subtitle_loading)
        else -> {
            val folderCount = displayedFiles.count { it.isDirectory }
            val fileCount = displayedFiles.size - folderCount
            val folderText = if (folderCount > 0) {
                stringResource(R.string.subtitle_folder_count, folderCount)
            } else null
            val fileText = if (fileCount > 0) {
                stringResource(R.string.subtitle_file_count, fileCount)
            } else null
            when {
                folderText != null && fileText != null ->
                    folderText + stringResource(R.string.subtitle_separator) + fileText
                folderText != null -> folderText
                fileText != null -> fileText
                else -> stringResource(R.string.subtitle_empty)
            }
        }
    }

    // 多选模式下唯一选中项 (顶栏收藏按钮状态)
    val singleSelected = if (selectedPaths.size == 1) {
        displayedFiles.firstOrNull { it.path in selectedPaths }
    } else null

    // FAB 与列表底部留白的显示条件
    val fabVisible = !pickerMode && !saveMode && !selectionMode && !isSearchActive

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    // 当前目录是否还能返回上级
    val canNavigateUp =
        currentPath != "/" && currentPath != FileManagerViewModel.storageRoot

    // 根目录（或界面上没有可处理的返回动作）时，不要把返回事件拦下来：
    // 只有「应用内没有回调拦截返回」时，系统才会播放预测性返回动画
    // （窗口随手指缩小、并实时预览即将返回的调用方界面 / 主屏幕）。
    val handleBackInternally = drawerState.isOpen || selectionMode || isSearchActive || canNavigateUp

    BackHandler(enabled = handleBackInternally) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            selectionMode -> viewModel.clearSelection()
            isSearchActive -> viewModel.closeSearch()
            // 剩余情况只会是"还能返回上级目录"
            else -> viewModel.navigateUp()
        }
    }

    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    createDialogIsFolder?.let { isFolder ->
        CreateItemDialog(
            isFolder = isFolder,
            onConfirm = { name ->
                if (isFolder) viewModel.createFolder(name) else viewModel.createFile(name)
                createDialogIsFolder = null
            },
            onDismiss = { createDialogIsFolder = null }
        )
    }

    if (showNavigateToDialog) {
        NavigateToPathDialog(
            currentPath = currentPath,
            onConfirm = { path ->
                viewModel.loadDirectory(path)
                showNavigateToDialog = false
            },
            onDismiss = { showNavigateToDialog = false }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            item = target,
            onConfirm = { newName ->
                viewModel.renameFile(target, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTargets?.let { targets ->
        DeleteConfirmDialog(
            items = targets,
            onConfirm = {
                viewModel.deleteFiles(targets)
                deleteTargets = null
            },
            onDismiss = { deleteTargets = null }
        )
    }

    pasteConflicts?.let { conflicts ->
        ConflictDialog(
            conflictNames = conflicts,
            onOverwrite = { viewModel.resolvePasteConflict(overwrite = true) },
            onSkip = { viewModel.resolvePasteConflict(overwrite = false) },
            onCancel = { viewModel.cancelPasteConflict() }
        )
    }

    operationProgress?.let { progress ->
        OperationProgressDialog(progress)
    }

    propertiesTarget?.let { target ->
        PropertiesDialog(
            item = target,
            stats = propertiesStats,
            formatDate = { viewModel.formatDate(it) },
            onDismiss = { viewModel.closeProperties() }
        )
    }

    // 音视频预览对话框 (ExoPlayer 播放 + ffmpeg 解析)
    previewTarget?.let { target ->
        MediaPreviewDialog(
            item = target,
            onOpenWith = {
                previewTarget = null
                onOpenWith?.invoke(target)
            },
            onDismiss = { previewTarget = null }
        )
    }

    // 页面切换（主界面 ↔ 设置页）：完全照搬 Dtool 的 AnimatedContent 联动过渡,
    // 旧页退场与新页入场同时进行 (滑动 + 淡入淡出 + 轻微缩放)。
    // 设置页打开时按返回键先关闭设置页 (Dtool 同款 BackHandler 处理)
    BackHandler(enabled = showSettings) { showSettings = false }

    // 防止深色模式下闪白的背景层 (照搬 Dtool)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = showSettings,
            transitionSpec = {
                if (targetState) {
                    // 前进（打开设置）: 设置页从右滑入 + 淡入 + 轻微缩放, 主界面左移 1/3 + 淡出 + 轻微缩放
                    (
                        slideInHorizontally(
                            animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
                            initialOffsetX = { fullWidth -> fullWidth }
                        ) +
                        fadeIn(
                            animationSpec = tween(durationMillis = 350, delayMillis = 50)
                        ) +
                        scaleIn(
                            animationSpec = tween(durationMillis = 350, easing = FastOutLinearInEasing),
                            initialScale = 0.95f
                        )
                    ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> -fullWidth / 3 }
                        ) +
                        fadeOut(
                            animationSpec = tween(durationMillis = 250)
                        ) +
                        scaleOut(
                            animationSpec = tween(durationMillis = 350),
                            targetScale = 0.95f
                        )
                    )
                } else {
                    // 后退（关闭设置）: 主界面从左滑入 + 淡入 + 轻微缩放, 设置页右移 1/3 + 淡出 + 轻微缩放
                    (
                        slideInHorizontally(
                            animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
                            initialOffsetX = { fullWidth -> -fullWidth }
                        ) +
                        fadeIn(
                            animationSpec = tween(durationMillis = 350, delayMillis = 50)
                        ) +
                        scaleIn(
                            animationSpec = tween(durationMillis = 350, easing = FastOutLinearInEasing),
                            initialScale = 0.95f
                        )
                    ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> fullWidth / 3 }
                        ) +
                        fadeOut(
                            animationSpec = tween(durationMillis = 250)
                        ) +
                        scaleOut(
                            animationSpec = tween(durationMillis = 350),
                            targetScale = 0.95f
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { settingsOpen ->
            if (settingsOpen) {
                // 设置页
                SettingsScreen(
                    hiddenQuickDirs = hiddenQuickDirs,
                    themeMode = themeMode,
                    showThumbnails = showThumbnails,
                    showHidden = showHidden,
                    onToggleQuickDir = { id, hidden -> viewModel.setQuickDirHidden(id, hidden) },
                    onThemeModeChange = { viewModel.setThemeMode(it) },
                    onShowThumbnailsChange = { viewModel.setShowThumbnails(it) },
                    onShowHiddenChange = { viewModel.setShowHidden(it) },
                    onBack = { showSettings = false }
                )
            } else {
                // 主界面
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = !pickerMode && !saveMode && (drawerState.isOpen || (!selectionMode && !isSearchActive)),
                    drawerContent = {
                        DrawerContent(
                            currentPath = currentPath,
                            favorites = favorites,
                            hiddenQuickDirs = hiddenQuickDirs,
                            onNavigate = { path ->
                                scope.launch { drawerState.close() }
                                viewModel.closeSearch()
                                viewModel.clearSelection()
                                viewModel.loadDirectory(path)
                            },
                            onRemoveFavorite = { viewModel.toggleFavorite(it) },
                            onOpenSettings = {
                                scope.launch { drawerState.close() }
                                showSettings = true
                            }
                        )
                    }
                ) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            when {
                                selectionMode -> SelectionTopBar(
                                    count = selectedPaths.size,
                                    singleSelection = selectedPaths.size == 1,
                                    isFavorite = singleSelected?.let { it.path in favorites } == true,
                                    isDirectory = singleSelected?.isDirectory == true,
                                    onClose = { viewModel.clearSelection() },
                                    onCut = { viewModel.cutToClipboard(viewModel.selectedItems()) },
                                    onCopy = { viewModel.copyToClipboard(viewModel.selectedItems()) },
                                    onDelete = { deleteTargets = viewModel.selectedItems() },
                                    onRename = { renameTarget = viewModel.selectedItems().firstOrNull() },
                                    onShare = {
                                        onShareFiles?.invoke(viewModel.selectedItems())
                                        viewModel.clearSelection()
                                    },
                                    onProperties = {
                                        viewModel.selectedItems().firstOrNull()?.let {
                                            viewModel.showProperties(it)
                                        }
                                    },
                                    onToggleFavorite = {
                                        viewModel.selectedItems().firstOrNull()?.let {
                                            viewModel.toggleFavorite(it.path)
                                        }
                                        viewModel.clearSelection()
                                    },
                                    onSelectAll = { viewModel.selectAll() }
                                )
                                isSearchActive -> SearchTopBar(
                                    query = searchQuery,
                                    isLoading = isSearchLoading,
                                    onQueryChange = { viewModel.setSearchQuery(it) },
                                    onClose = { viewModel.closeSearch() }
                                )
                                saveMode -> SaveModeTopBar(
                                    currentDirName = if (currentPath == FileManagerViewModel.storageRoot) {
                                        internalStorageLabel
                                    } else {
                                        File(currentPath).name
                                    },
                                    onCancel = { onSaveCancelled?.invoke() }
                                )
                                else -> NormalTopBar(
                                    subtitle = subtitle,
                                    currentPath = currentPath,
                                    isPickerMode = pickerMode,
                                    canNavigateUp = canNavigateUp,
                                    sortMode = sortMode,
                                    sortAscending = sortAscending,
                                    sortDirectoriesFirst = sortDirectoriesFirst,
                                    isBookmarked = currentPath in favorites,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onNavigateBack = {
                                        if (!viewModel.navigateUp()) {
                                            onPickCancelled?.invoke()
                                        }
                                    },
                                    onNavigateUp = { viewModel.navigateUp() },
                                    onNavigateTo = { showNavigateToDialog = true },
                                    onPathClick = { path -> viewModel.loadDirectory(path) },
                                    onSearchClick = { viewModel.openSearch() },
                                    onSortModeSelected = { viewModel.setSortMode(it) },
                                    onToggleSortOrder = { viewModel.setSortMode(sortMode) },
                                    onToggleDirectoriesFirst = { viewModel.toggleSortDirectoriesFirst() },
                                    onSelectAll = { viewModel.selectAll() },
                                    onRefreshClick = { viewModel.refresh() },
                                    onToggleBookmark = { viewModel.toggleFavorite(currentPath) },
                                    onShareCurrentDir = {
                                        // 分享当前目录 (照搬 MaterialFiles 的 share())
                                        val dir = File(currentPath)
                                        onShareFiles?.invoke(
                                            listOf(
                                                FileItem(
                                                    name = dir.name,
                                                    path = dir.absolutePath,
                                                    parentPath = dir.parent ?: "",
                                                    isDirectory = true,
                                                    size = 0L,
                                                    lastModified = dir.lastModified(),
                                                    extension = "",
                                                    canRead = dir.canRead(),
                                                    canWrite = dir.canWrite(),
                                                    isHidden = dir.isHidden
                                                )
                                            )
                                        )
                                    },
                                    onCopyPath = { viewModel.copyPathToClipboard(currentPath) },
                                    onCreateShortcut = { onCreateShortcut?.invoke(currentPath) },
                                    onExitApp = { onExitApp?.invoke() }
                                )
                            }
                        },
                        bottomBar = {
                            Column {
                                if (clipboard != null && !selectionMode && !pickerMode && !saveMode) {
                                    PasteBar(
                                        itemCount = clipboard!!.items.size,
                                        isCut = clipboard!!.isCut,
                                        onPaste = { viewModel.requestPaste() },
                                        onCancel = { viewModel.clearClipboard() }
                                    )
                                }

                                AnimatedVisibility(
                                    visible = saveMode && saveFileCount > 0,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    SaveModeBar(
                                        fileCount = saveFileCount,
                                        onSave = { onSaveConfirmed?.invoke() },
                                        onCancel = { onSaveCancelled?.invoke() }
                                    )
                                }

                                // 底部操控栏 (MT 风格): 与 FAB 并存的个性化组件
                                if (fabVisible) {
                                    BottomNavBar(
                                        canBack = canGoBack,
                                        canForward = canGoForward,
                                        atRoot = !canNavigateUp,
                                        onBack = { viewModel.navigateBack() },
                                        onForward = { viewModel.navigateForward() },
                                        onHome = { viewModel.navigateHome() },
                                        onCreateFolder = { createDialogIsFolder = true },
                                        onNavigateUp = { viewModel.navigateUp() }
                                    )
                                }
                            }
                        },
                        floatingActionButton = {
                            if (fabVisible) {
                                FabSpeedDial(
                                    expanded = fabExpanded,
                                    onExpandedChange = { fabExpanded = it },
                                    onCreateFolder = {
                                        fabExpanded = false
                                        createDialogIsFolder = true
                                    },
                                    onCreateFile = {
                                        fabExpanded = false
                                        createDialogIsFolder = false
                                    }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when {
                                isLoading && !searching && displayedFiles.isEmpty() -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                                searching && displayedFiles.isEmpty() -> {
                                    if (isSearchLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    } else {
                                        EmptyPlaceholder(
                                            text = stringResource(R.string.no_search_results),
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                                displayedFiles.isEmpty() && errorMessage == null -> {
                                    EmptyPlaceholder(
                                        text = stringResource(R.string.dir_empty),
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                                else -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            top = 4.dp,
                                            bottom = if (fabVisible) 88.dp else 8.dp
                                        )
                                    ) {
                                        items(
                                            items = displayedFiles,
                                            key = { it.path }
                                        ) { fileItem ->
                                            FileItemRow(
                                                fileItem = fileItem,
                                                viewModel = viewModel,
                                                isChecked = fileItem.path in selectedPaths,
                                                isFavorite = fileItem.path in favorites,
                                                isMenuShown = actionTarget?.path == fileItem.path,
                                                showThumbnails = showThumbnails,
                                                onItemClick = {
                                                    when {
                                                        selectionMode ->
                                                            viewModel.toggleSelection(fileItem)
                                                        fileItem.isDirectory -> {
                                                            if (searching) viewModel.closeSearch()
                                                            viewModel.navigateToDirectory(fileItem)
                                                        }
                                                        else -> {
                                                            val category = FileTypeUtil.getCategory(fileItem)
                                                            if (!pickerMode && !saveMode && MediaProbe.isSupported &&
                                                                (category == FileCategory.VIDEO || category == FileCategory.AUDIO)
                                                            ) {
                                                                // 音视频文件: 打开内置预览 (解析信息/缩略图/波形 + 播放)
                                                                previewTarget = fileItem
                                                            } else {
                                                                onFileSelected?.invoke(fileItem)
                                                            }
                                                        }
                                                    }
                                                },
                                                onIconClick = if (pickerMode || saveMode) {
                                                    {}
                                                } else {
                                                    { viewModel.toggleSelection(fileItem) }
                                                },
                                                onItemLongClick = if (pickerMode || saveMode) null else ({
                                                    if (selectionMode) {
                                                        if (fileItem.isDirectory) {
                                                            if (searching) viewModel.closeSearch()
                                                            viewModel.navigateToDirectory(fileItem)
                                                        } else {
                                                            onFileSelected?.invoke(fileItem)
                                                        }
                                                    } else {
                                                        viewModel.toggleSelection(fileItem)
                                                    }
                                                }),
                                                onMenuClick = { actionTarget = fileItem },
                                                onMenuDismiss = { actionTarget = null },
                                                onAction = { action ->
                                                    when (action) {
                                                        FileAction.COPY ->
                                                            viewModel.copyToClipboard(listOf(fileItem))
                                                        FileAction.CUT ->
                                                            viewModel.cutToClipboard(listOf(fileItem))
                                                        FileAction.DELETE ->
                                                            deleteTargets = listOf(fileItem)
                                                        FileAction.RENAME ->
                                                            renameTarget = fileItem
                                                        FileAction.SHARE ->
                                                            onShareFiles?.invoke(listOf(fileItem))
                                                        FileAction.FAVORITE ->
                                                            viewModel.toggleFavorite(fileItem.path)
                                                        FileAction.PIN_SIZE ->
                                                            viewModel.togglePinFolder(fileItem.path)
                                                        FileAction.REFRESH_SIZE ->
                                                            viewModel.refreshFolderSize(fileItem.path)
                                                        FileAction.PROPERTIES ->
                                                            viewModel.showProperties(fileItem)
                                                        FileAction.MULTI_SELECT ->
                                                            viewModel.toggleSelection(fileItem)
                                                    }
                                                    actionTarget = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
//  普通模式顶栏
// =============================================================================
@Composable
private fun NormalTopBar(
    subtitle: String,
    currentPath: String,
    isPickerMode: Boolean,
    canNavigateUp: Boolean,
    sortMode: SortMode,
    sortAscending: Boolean,
    sortDirectoriesFirst: Boolean,
    isBookmarked: Boolean,
    onOpenDrawer: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateTo: () -> Unit,
    onPathClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSortModeSelected: (SortMode) -> Unit,
    onToggleSortOrder: () -> Unit,
    onToggleDirectoriesFirst: () -> Unit,
    onSelectAll: () -> Unit,
    onRefreshClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShareCurrentDir: () -> Unit,
    onCopyPath: () -> Unit,
    onCreateShortcut: () -> Unit,
    onExitApp: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = stringResource(R.string.dis_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Crossfade(targetState = subtitle, label = "subtitle") { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                if (isPickerMode) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                } else {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.menu)
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search)
                    )
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.sort_by)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(stringResource(mode.labelRes)) },
                                leadingIcon = {
                                    if (mode == sortMode) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null
                                        )
                                    }
                                },
                                onClick = {
                                    onSortModeSelected(mode)
                                    showSortMenu = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_ascending)) },
                            trailingIcon = {
                                if (sortAscending) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                onToggleSortOrder()
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_directories_first)) },
                            trailingIcon = {
                                if (sortDirectoriesFirst) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                onToggleDirectoriesFirst()
                                showSortMenu = false
                            }
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.more)
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_up)) },
                            enabled = canNavigateUp,
                            onClick = {
                                onNavigateUp()
                                showMoreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.navigate_to)) },
                            onClick = {
                                onNavigateTo()
                                showMoreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.refresh)) },
                            onClick = {
                                onRefreshClick()
                                showMoreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.select_all)) },
                            onClick = {
                                onSelectAll()
                                showMoreMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            onClick = {
                                onShareCurrentDir()
                                showMoreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_path)) },
                            onClick = {
                                onCopyPath()
                                showMoreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (isBookmarked) R.string.bookmark_remove
                                        else R.string.bookmark_add
                                    )
                                )
                            },
                            onClick = {
                                onToggleBookmark()
                                showMoreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.create_shortcut)) },
                            onClick = {
                                onCreateShortcut()
                                showMoreMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.exit_app)) },
                            onClick = {
                                onExitApp()
                                showMoreMenu = false
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        BreadcrumbBar(
            currentPath = currentPath,
            onPathClick = onPathClick
        )
    }
}

// =============================================================================
//  多选模式顶栏 (照搬 MaterialFiles 的 OverlayToolbar)
// =============================================================================
@Composable
private fun SelectionTopBar(
    count: Int,
    singleSelection: Boolean,
    isFavorite: Boolean,
    isDirectory: Boolean,
    onClose: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectAll: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.selected_count, count),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.exit_selection)
                )
            }
        },
        actions = {
            IconButton(onClick = onCut) {
                Icon(
                    imageVector = Icons.Filled.ContentCut,
                    contentDescription = stringResource(R.string.action_cut)
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete)
                )
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.more)
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        enabled = singleSelection,
                        onClick = {
                            onRename()
                            showMoreMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        onClick = {
                            onShare()
                            showMoreMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_properties)) },
                        enabled = singleSelection,
                        onClick = {
                            onProperties()
                            showMoreMenu = false
                        }
                    )
                    if (isDirectory) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (isFavorite) R.string.action_unfavorite
                                        else R.string.action_favorite
                                    )
                                )
                            },
                            enabled = singleSelection,
                            onClick = {
                                onToggleFavorite()
                                showMoreMenu = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.select_all)) },
                        onClick = {
                            onSelectAll()
                            showMoreMenu = false
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// =============================================================================
//  搜索顶栏
// =============================================================================
@Composable
private fun SearchTopBar(
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                trailingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.exit_search)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// =============================================================================
//  保存模式顶栏 (接收其他 App 分享的文件)
// =============================================================================
@Composable
private fun SaveModeTopBar(
    currentDirName: String,
    onCancel: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.save_mode_select_folder),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentDirName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cancel)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

// =============================================================================
//  保存模式底部栏
// =============================================================================
@Composable
private fun SaveModeBar(
    fileCount: Int,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.save_files_count, fileCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.save_here),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = onSave) {
                Text(stringResource(R.string.save_here))
            }
        }
    }
}

// =============================================================================
//  FAB 速度拨号 (照搬 MaterialFiles 的 SpeedDialView)
// =============================================================================
@Composable
private fun FabSpeedDial(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f,
        label = "fabRotation"
    )
    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(horizontalAlignment = Alignment.End) {
                SpeedDialAction(
                    icon = Icons.Filled.CreateNewFolder,
                    label = stringResource(R.string.create_folder),
                    onClick = onCreateFolder
                )
                Spacer(modifier = Modifier.height(12.dp))
                SpeedDialAction(
                    icon = Icons.AutoMirrored.Filled.NoteAdd,
                    label = stringResource(R.string.create_file),
                    onClick = onCreateFile
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.nav_create),
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

@Composable
private fun SpeedDialAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label
            )
        }
    }
}

// =============================================================================
//  底部操控栏 (MT 风格)
// =============================================================================
@Composable
private fun BottomNavBar(
    canBack: Boolean,
    canForward: Boolean,
    atRoot: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onCreateFolder: () -> Unit,
    onNavigateUp: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionBarItem(
                Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back),
                enabled = canBack, onClick = onBack
            )
            ActionBarItem(
                Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.nav_forward),
                enabled = canForward, onClick = onForward
            )
            ActionBarItem(
                Icons.Filled.Home, stringResource(R.string.nav_home),
                enabled = !atRoot, onClick = onHome
            )
            ActionBarItem(
                Icons.Filled.CreateNewFolder, stringResource(R.string.nav_create),
                onClick = onCreateFolder
            )
            ActionBarItem(
                Icons.Filled.ArrowUpward, stringResource(R.string.nav_up),
                enabled = !atRoot, onClick = onNavigateUp
            )
        }
    }
}

@Composable
private fun ActionBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = tint
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

// =============================================================================
//  粘贴栏 (照搬 MaterialFiles 的底部工具栏)
// =============================================================================
@Composable
private fun PasteBar(
    itemCount: Int,
    isCut: Boolean,
    onPaste: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cancel)
                )
            }
            Text(
                text = stringResource(
                    if (isCut) R.string.pending_move else R.string.pending_copy,
                    itemCount
                ),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 8.dp)
            )
            TextButton(onClick = onPaste) {
                Text(stringResource(R.string.paste))
            }
        }
    }
}

// =============================================================================
//  空列表占位 (照搬 MaterialFiles 的 empty view: 240dp 大图标 + 文案)
// =============================================================================
@Composable
private fun EmptyPlaceholder(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.empty_icon_240dp),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =============================================================================
//  面包屑 (照搬 MaterialFiles 的 BreadcrumbLayout)
// =============================================================================
@Composable
private fun BreadcrumbBar(
    currentPath: String,
    onPathClick: (String) -> Unit
) {
    val internalStorage = stringResource(R.string.internal_storage)
    val segments = remember(currentPath) { buildPathSegments(currentPath, internalStorage) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 60.dp, end = 4.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, (name, path) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onPathClick(path) }
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (index == segments.lastIndex)
                        FontWeight.Medium
                    else
                        FontWeight.Normal,
                    color = if (index == segments.lastIndex)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (index < segments.lastIndex) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun buildPathSegments(path: String, rootLabel: String): List<Pair<String, String>> {
    val segments = mutableListOf<Pair<String, String>>()
    val parts = path.split(File.separator).filter { it.isNotEmpty() }
    var accumulatedPath =
        if (path.startsWith(File.separator)) File.separator else ""

    for (part in parts) {
        accumulatedPath = if (accumulatedPath.endsWith(File.separator))
            accumulatedPath + part
        else
            accumulatedPath + File.separator + part

        // 跳过存储根上方的系统路径（如 /storage、/storage/emulated），这些路径没有读取权限
        if (accumulatedPath != FileManagerViewModel.storageRoot &&
            !accumulatedPath.startsWith(FileManagerViewModel.storageRoot + File.separator))
            continue

        val displayName = if (accumulatedPath == FileManagerViewModel.storageRoot) rootLabel
            else if (part.length > 16) part.take(13) + ".."
            else part
        segments.add(displayName to accumulatedPath)
    }
    return segments
}
