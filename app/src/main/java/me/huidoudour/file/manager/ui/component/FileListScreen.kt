package me.huidoudour.file.manager.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.SortMode
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File

/**
 * 文件管理器主界面 — MT 管理器风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    viewModel: FileManagerViewModel,
    isPickerMode: Boolean = false,
    selectedFile: FileItem? = null,
    onFileSelected: ((FileItem) -> Unit)? = null,
    onPickConfirmed: ((FileItem) -> Unit)? = null,
    onPickCancelled: (() -> Unit)? = null,
    onShareFiles: ((List<FileItem>) -> Unit)? = null,
    canGoBack: Boolean = false
) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
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
    val toastMessage by viewModel.toastMessage.collectAsState()
    val propertiesTarget by viewModel.propertiesTarget.collectAsState()
    val propertiesStats by viewModel.propertiesStats.collectAsState()

    var showSortDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    // true=新建文件夹, false=新建文件, null=不显示
    var createDialogIsFolder by remember { mutableStateOf<Boolean?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    // 长按弹出操作对话框的目标文件
    var actionTarget by remember { mutableStateOf<FileItem?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val selectionMode = selectedPaths.isNotEmpty()
    val searching = isSearchActive && searchQuery.isNotBlank()
    val displayedFiles = if (searching) searchResults else files

    // 当前目录名
    val currentDirName = remember(currentPath) {
        val name = File(currentPath).name
        if (name.isEmpty()) "内部存储" else name
    }

    // ==================== 轻提示 ====================
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    // ==================== 返回键处理 ====================
    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            selectionMode -> viewModel.clearSelection()
            isSearchActive -> viewModel.closeSearch()
            isPickerMode && canGoBack -> onPickCancelled?.invoke()
            else -> viewModel.navigateUp()
        }
    }

    // ==================== 对话框 ====================
    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("错误") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("确定")
                }
            }
        )
    }

    if (showSortDialog) {
        SortDialog(
            currentMode = sortMode,
            currentAscending = sortAscending,
            onModeSelected = { mode ->
                viewModel.setSortMode(mode)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
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

    // ==================== 主体: 抽屉 + Scaffold ====================
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isPickerMode && (drawerState.isOpen || (!selectionMode && !isSearchActive)),
        drawerContent = {
            DrawerContent(
                currentPath = currentPath,
                favorites = favorites,
                showHidden = showHidden,
                onNavigate = { path ->
                    scope.launch { drawerState.close() }
                    viewModel.closeSearch()
                    viewModel.clearSelection()
                    viewModel.loadDirectory(path)
                },
                onRemoveFavorite = { viewModel.toggleFavorite(it) },
                onToggleShowHidden = { viewModel.toggleShowHidden() }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    when {
                        selectionMode -> SelectionTopBar(
                            count = selectedPaths.size,
                            onClose = { viewModel.clearSelection() },
                            onSelectAll = { viewModel.selectAll() }
                        )
                        isSearchActive -> SearchTopBar(
                            query = searchQuery,
                            isLoading = isSearchLoading,
                            onQueryChange = { viewModel.setSearchQuery(it) },
                            onClose = { viewModel.closeSearch() }
                        )
                        else -> NormalTopBar(
                            currentDirName = currentDirName,
                            currentPath = currentPath,
                            isPickerMode = isPickerMode,
                            showMoreMenu = showMoreMenu,
                            showHidden = showHidden,
                            onShowMoreMenuChange = { showMoreMenu = it },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onPickCancelled = onPickCancelled,
                            onPathClick = { path -> viewModel.loadDirectory(path) },
                            onSearchClick = { viewModel.openSearch() },
                            onSortClick = { showSortDialog = true },
                            onRefreshClick = { viewModel.refresh() },
                            onCreateFolder = { createDialogIsFolder = true },
                            onCreateFile = { createDialogIsFolder = false },
                            onToggleShowHidden = { viewModel.toggleShowHidden() }
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            },
            bottomBar = {
                Column {
                    // ---- 多选操作栏 ----
                    if (selectionMode && !isPickerMode) {
                        SelectionActionBar(
                            singleSelection = selectedPaths.size == 1,
                            viewModel = viewModel,
                            onCopy = { viewModel.copyToClipboard(viewModel.selectedItems()) },
                            onCut = { viewModel.cutToClipboard(viewModel.selectedItems()) },
                            onDelete = { deleteTargets = viewModel.selectedItems() },
                            onRename = {
                                renameTarget = viewModel.selectedItems().firstOrNull()
                            },
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
                            }
                        )
                    }

                    // ---- 粘贴栏 ----
                    if (clipboard != null && !selectionMode && !isPickerMode) {
                        PasteBar(
                            itemCount = clipboard!!.items.size,
                            isCut = clipboard!!.isCut,
                            onPaste = { viewModel.requestPaste() },
                            onCancel = { viewModel.clearClipboard() }
                        )
                    }

                    // ---- 选取模式确认栏 ----
                    AnimatedVisibility(
                        visible = isPickerMode && selectedFile != null,
                        enter = fadeIn(),
                        exit = fadeOut()
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
                                        text = "已选择:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = selectedFile?.name ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(onClick = {
                                    selectedFile?.let { onPickConfirmed?.invoke(it) }
                                }) {
                                    Text("确认选择")
                                }
                            }
                        }
                    }

                    // ---- MT 风格底部状态栏 ----
                    if (!isPickerMode && !selectionMode && clipboard == null &&
                        !isLoading && displayedFiles.isNotEmpty()
                    ) {
                        Column {
                            HorizontalDivider(thickness = 0.5.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${displayedFiles.size} 项" +
                                        if (searching) " (搜索结果)" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = sortMode.label + if (sortAscending) " ↑" else " ↓",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    isLoading && !searching -> {
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
                                text = "无匹配结果",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    displayedFiles.isEmpty() && errorMessage == null -> {
                        EmptyPlaceholder(
                            text = "目录为空",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // 返回上级目录 (搜索时隐藏)
                            if (!searching &&
                                currentPath != FileManagerViewModel.STORAGE_ROOT &&
                                currentPath != "/"
                            ) {
                                item(key = "parent_dir") {
                                    ParentDirectoryRow(
                                        onClick = { viewModel.navigateUp() }
                                    )
                                }
                            }

                            itemsIndexed(
                                items = displayedFiles,
                                key = { _, item -> item.path }
                            ) { _, fileItem ->
                                // 长按菜单定位: 按下位置 + 行高 (DropdownMenu 默认锚在行底部)
                                var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
                                var rowHeight by remember { mutableStateOf(0.dp) }
                                val density = LocalDensity.current

                                Box(
                                    modifier = Modifier.onSizeChanged {
                                        rowHeight = with(density) { it.height.toDp() }
                                    }
                                ) {
                                    FileItemRow(
                                        fileItem = fileItem,
                                        viewModel = viewModel,
                                        isSelected = selectedFile?.path == fileItem.path,
                                        isChecked = fileItem.path in selectedPaths,
                                        selectionMode = selectionMode,
                                        isFavorite = fileItem.path in favorites,
                                        onItemClick = {
                                            when {
                                                selectionMode ->
                                                    viewModel.toggleSelection(fileItem)
                                                fileItem.isDirectory -> {
                                                    if (searching) viewModel.closeSearch()
                                                    viewModel.navigateToDirectory(fileItem)
                                                }
                                                else -> onFileSelected?.invoke(fileItem)
                                            }
                                        },
                                        onItemLongClick = if (isPickerMode) null else {
                                            { pressOffset ->
                                                if (selectionMode) {
                                                    viewModel.toggleSelection(fileItem)
                                                } else {
                                                    menuOffset = pressOffset
                                                    actionTarget = fileItem
                                                }
                                            }
                                        }
                                    )

                                    // ---- 长按弹出式操作菜单 (跟随按压位置) ----
                                    FileActionMenu(
                                        expanded = actionTarget?.path == fileItem.path,
                                        item = fileItem,
                                        isFavorite = fileItem.path in favorites,
                                        offset = DpOffset(
                                            x = menuOffset.x,
                                            y = menuOffset.y - rowHeight
                                        ),
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
                                                FileAction.PROPERTIES ->
                                                    viewModel.showProperties(fileItem)
                                                FileAction.MULTI_SELECT ->
                                                    viewModel.toggleSelection(fileItem)
                                            }
                                            actionTarget = null
                                        },
                                        onDismiss = { actionTarget = null }
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

// =============================================================================
//  长按弹出式操作菜单
// =============================================================================

/** 长按文件弹出的操作项 */
private enum class FileAction(val label: String) {
    COPY("复制"),
    CUT("剪切"),
    DELETE("删除"),
    RENAME("重命名"),
    SHARE("分享"),
    FAVORITE("收藏"),
    PROPERTIES("属性"),
    MULTI_SELECT("多选")
}

@Composable
private fun FileActionMenu(
    expanded: Boolean,
    item: FileItem,
    isFavorite: Boolean,
    offset: DpOffset,
    onAction: (FileAction) -> Unit,
    onDismiss: () -> Unit
) {
    val actions = buildList {
        add(FileAction.COPY.label to FileAction.COPY)
        add(FileAction.CUT.label to FileAction.CUT)
        add(FileAction.DELETE.label to FileAction.DELETE)
        add(FileAction.RENAME.label to FileAction.RENAME)
        if (!item.isDirectory) add(FileAction.SHARE.label to FileAction.SHARE)
        if (item.isDirectory) {
            add((if (isFavorite) "取消收藏" else "收藏") to FileAction.FAVORITE)
        }
        add(FileAction.PROPERTIES.label to FileAction.PROPERTIES)
        add(FileAction.MULTI_SELECT.label to FileAction.MULTI_SELECT)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset
    ) {
        actions.forEach { (label, action) ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = label,
                        color = if (action == FileAction.DELETE)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = { onAction(action) }
            )
        }
    }
}

// =============================================================================
//  普通模式顶栏
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalTopBar(
    currentDirName: String,
    currentPath: String,
    isPickerMode: Boolean,
    showMoreMenu: Boolean,
    showHidden: Boolean,
    onShowMoreMenuChange: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onPickCancelled: (() -> Unit)?,
    onPathClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    onToggleShowHidden: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = currentDirName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BreadcrumbBar(
                    currentPath = currentPath,
                    onPathClick = onPathClick
                )
            }
        },
        navigationIcon = {
            if (isPickerMode) {
                IconButton(onClick = { onPickCancelled?.invoke() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "取消"
                    )
                }
            } else {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "菜单"
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索"
                )
            }
            IconButton(onClick = onSortClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "排序"
                )
            }
            Box {
                IconButton(onClick = { onShowMoreMenuChange(true) }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多"
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { onShowMoreMenuChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("刷新") },
                        leadingIcon = {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        },
                        onClick = {
                            onRefreshClick()
                            onShowMoreMenuChange(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("新建文件夹") },
                        onClick = {
                            onCreateFolder()
                            onShowMoreMenuChange(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("新建文件") },
                        onClick = {
                            onCreateFile()
                            onShowMoreMenuChange(false)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (showHidden) "隐藏隐藏文件" else "显示隐藏文件")
                        },
                        onClick = {
                            onToggleShowHidden()
                            onShowMoreMenuChange(false)
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
//  多选模式顶栏
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "已选 $count 项",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "退出多选"
                )
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Filled.SelectAll,
                    contentDescription = "全选"
                )
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
@OptIn(ExperimentalMaterial3Api::class)
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
                placeholder = { Text("搜索当前目录及子目录") },
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
                                contentDescription = "清空"
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
                    contentDescription = "退出搜索"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// =============================================================================
//  多选底部操作栏
// =============================================================================
@Composable
private fun SelectionActionBar(
    singleSelection: Boolean,
    viewModel: FileManagerViewModel,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val singleItem = if (singleSelection) viewModel.selectedItems().firstOrNull() else null

    Column {
        HorizontalDivider(thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionBarItem(Icons.Filled.ContentCopy, "复制", onClick = onCopy)
            ActionBarItem(Icons.Filled.ContentCut, "剪切", onClick = onCut)
            ActionBarItem(Icons.Filled.Delete, "删除", onClick = onDelete)
            ActionBarItem(
                Icons.Filled.DriveFileRenameOutline, "重命名",
                enabled = singleSelection, onClick = onRename
            )
            ActionBarItem(Icons.Filled.Share, "分享", onClick = onShare)
            Box {
                ActionBarItem(Icons.Filled.MoreVert, "更多", onClick = { showMenu = true })
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (singleItem != null) {
                        DropdownMenuItem(
                            text = { Text("属性") },
                            onClick = {
                                onProperties()
                                showMenu = false
                            }
                        )
                        if (singleItem.isDirectory) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (viewModel.isFavorite(singleItem.path)) "取消收藏"
                                        else "收藏"
                                    )
                                },
                                onClick = {
                                    onToggleFavorite()
                                    showMenu = false
                                }
                            )
                        }
                    } else {
                        DropdownMenuItem(
                            text = { Text("属性 (仅单选可用)") },
                            enabled = false,
                            onClick = {}
                        )
                    }
                }
            }
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
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
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
//  粘贴栏
// =============================================================================
@Composable
private fun PasteBar(
    itemCount: Int,
    isCut: Boolean,
    onPaste: () -> Unit,
    onCancel: () -> Unit
) {
    Column {
        HorizontalDivider(thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCut) Icons.Filled.ContentCut else Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = (if (isCut) "待移动" else "待复制") + " $itemCount 项",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onCancel) { Text("取消") }
            Spacer(modifier = Modifier.width(4.dp))
            Button(onClick = onPaste) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("粘贴到此处")
            }
        }
    }
}

// =============================================================================
//  空列表占位
// =============================================================================
@Composable
private fun EmptyPlaceholder(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
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
//  紧凑面包屑 (MT 风格)
// =============================================================================
@Composable
private fun BreadcrumbBar(
    currentPath: String,
    onPathClick: (String) -> Unit
) {
    val segments = remember(currentPath) { buildPathSegments(currentPath) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, (name, path) ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = if (index == segments.lastIndex)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onPathClick(path) }
            )
            if (index < segments.lastIndex) {
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun buildPathSegments(path: String): List<Pair<String, String>> {
    val segments = mutableListOf<Pair<String, String>>()
    val parts = path.split(File.separator).filter { it.isNotEmpty() }
    var accumulatedPath =
        if (path.startsWith(File.separator)) File.separator else ""

    segments.add("内部存储" to FileManagerViewModel.STORAGE_ROOT)

    for (part in parts) {
        accumulatedPath = if (accumulatedPath.endsWith(File.separator))
            accumulatedPath + part
        else
            accumulatedPath + File.separator + part
        if (accumulatedPath == FileManagerViewModel.STORAGE_ROOT) continue
        val displayName = if (part.length > 16) part.take(13) + ".." else part
        segments.add(displayName to accumulatedPath)
    }
    return segments
}

// =============================================================================
//  返回上级目录行 (MT 风格)
// =============================================================================
@Composable
private fun ParentDirectoryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.SubdirectoryArrowRight,
            contentDescription = "返回上级",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "..",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// =============================================================================
//  排序对话框
// =============================================================================
@Composable
private fun SortDialog(
    currentMode: SortMode,
    currentAscending: Boolean,
    onModeSelected: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序方式") },
        text = {
            Column {
                SortMode.entries.forEach { mode ->
                    val isSelected = mode == currentMode
                    val arrow = if (isSelected) {
                        if (currentAscending) " ↑" else " ↓"
                    } else ""
                    TextButton(
                        onClick = { onModeSelected(mode) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = mode.label + arrow,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
