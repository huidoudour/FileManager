package me.huidoudour.file.manager.ui.component

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import me.huidoudour.file.manager.R
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
    val pinnedFolders by viewModel.pinnedFolders.collectAsState()
    val folderSizeCache by viewModel.folderSizeCache.collectAsState()  // 触发重组使 FileItemRow 感知缓存更新
    val toastMessage by viewModel.toastMessage.collectAsState()
    val propertiesTarget by viewModel.propertiesTarget.collectAsState()
    val propertiesStats by viewModel.propertiesStats.collectAsState()
    val canNavBack by viewModel.canGoBack.collectAsState()
    val canNavForward by viewModel.canGoForward.collectAsState()

    var showSortDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var createDialogIsFolder by remember { mutableStateOf<Boolean?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var actionTarget by remember { mutableStateOf<FileItem?>(null) }
    var actionPressOffset by remember { mutableStateOf(Offset.Zero) }
    var blockItemClicks by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = LocalActivity.current
    var lastBackPressTime by remember { mutableStateOf(0L) }

    val selectionMode = selectedPaths.isNotEmpty()
    val searching = isSearchActive && searchQuery.isNotBlank()
    val displayedFiles = if (searching) searchResults else files

    val internalStorageLabel = stringResource(R.string.internal_storage)
    val currentDirName = remember(currentPath) {
        val name = File(currentPath).name
        if (name.isEmpty()) internalStorageLabel else name
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    val pressBackHint = stringResource(R.string.press_back_again)
    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            selectionMode -> viewModel.clearSelection()
            isSearchActive -> viewModel.closeSearch()
            isPickerMode && canGoBack -> onPickCancelled?.invoke()
            else -> {
                if (!viewModel.navigateUp()) {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressTime < 2000L) {
                        activity?.finish()
                    } else {
                        lastBackPressTime = now
                        scope.launch {
                            snackbarHostState.showSnackbar(pressBackHint)
                        }
                    }
                }
            }
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
                }
            },
            bottomBar = {
                Column {
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

                    if (clipboard != null && !selectionMode && !isPickerMode) {
                        PasteBar(
                            itemCount = clipboard!!.items.size,
                            isCut = clipboard!!.isCut,
                            onPaste = { viewModel.requestPaste() },
                            onCancel = { viewModel.clearClipboard() }
                        )
                    }

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
                                        text = stringResource(R.string.file_selected),
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
                                    Text(stringResource(R.string.confirm_select))
                                }
                            }
                        }
                    }

                    if (!isPickerMode && !selectionMode && !isSearchActive) {
                        BottomNavBar(
                            canBack = canNavBack,
                            canForward = canNavForward,
                            atRoot = currentPath == FileManagerViewModel.STORAGE_ROOT ||
                                currentPath == "/",
                            onBack = { viewModel.navigateBack() },
                            onForward = { viewModel.navigateForward() },
                            onHome = { viewModel.navigateHome() },
                            onCreateFolder = { createDialogIsFolder = true },
                            onNavigateUp = { viewModel.navigateUp() }
                        )
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
                            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
                        ) {
                            itemsIndexed(
                                items = displayedFiles,
                                key = { _, item -> item.path }
                            ) { _, fileItem ->
                                val anchorState =
                                    remember { mutableStateOf(IntRect.Zero) }

                                Box(
                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                        anchorState.value =
                                            coordinates.boundsInWindow().let { r ->
                                                IntRect(
                                                    r.left.roundToInt(),
                                                    r.top.roundToInt(),
                                                    r.right.roundToInt(),
                                                    r.bottom.roundToInt()
                                                )
                                            }
                                    }
                                ) {
                                    FileItemRow(
                                        fileItem = fileItem,
                                        viewModel = viewModel,
                                        isSelected = selectedFile?.path == fileItem.path,
                                        isChecked = fileItem.path in selectedPaths,
                                        selectionMode = selectionMode,
                                        isFavorite = fileItem.path in favorites,
                                        isMenuShown = actionTarget?.path == fileItem.path,
                                        onItemClick = {
                                            if (blockItemClicks) {
                                                blockItemClicks = false
                                            } else {
                                                when {
                                                    selectionMode ->
                                                        viewModel.toggleSelection(fileItem)
                                                    fileItem.isDirectory -> {
                                                        if (searching) viewModel.closeSearch()
                                                        viewModel.navigateToDirectory(fileItem)
                                                    }
                                                    else -> onFileSelected?.invoke(fileItem)
                                                }
                                            }
                                        },
                                        onItemLongClick = if (isPickerMode) null else { pressOffset ->
                                            if (blockItemClicks) {
                                                blockItemClicks = false
                                            } else if (selectionMode) {
                                                viewModel.toggleSelection(fileItem)
                                            } else {
                                                actionPressOffset = pressOffset
                                                actionTarget = fileItem
                                            }
                                        }
                                    )

                                    val pressWindowPoint = IntOffset(
                                        (anchorState.value.left + actionPressOffset.x).roundToInt(),
                                        (anchorState.value.top + actionPressOffset.y).roundToInt()
                                    )

                                    FileActionMenu(
                                        expanded = actionTarget?.path == fileItem.path,
                                        item = fileItem,
                                        isFavorite = fileItem.path in favorites,
                                        isPinned = fileItem.path in pinnedFolders,
                                        anchorPoint = pressWindowPoint,
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
                                        },
                                        onDismiss = {
                                            actionTarget = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (actionTarget != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {
                                actionTarget = null
                            }
                        )
                )
            }
        }
    }
}

// =============================================================================
//  长按弹出式操作菜单 (Popup 显式定位)
// =============================================================================

private enum class FileAction(val labelRes: Int) {
    COPY(R.string.action_copy),
    CUT(R.string.action_cut),
    DELETE(R.string.action_delete),
    RENAME(R.string.action_rename),
    SHARE(R.string.action_share),
    FAVORITE(R.string.action_favorite),
    PIN_SIZE(R.string.action_pin_size),
    REFRESH_SIZE(R.string.action_refresh_size),
    PROPERTIES(R.string.action_properties),
    MULTI_SELECT(R.string.action_multi_select)
}

@Composable
private fun FileActionMenu(
    expanded: Boolean,
    item: FileItem,
    isFavorite: Boolean,
    isPinned: Boolean,
    anchorPoint: IntOffset,
    onAction: (FileAction) -> Unit,
    onDismiss: () -> Unit
) {
    if (!expanded) return

    val actions = buildList {
        add(FileAction.COPY.labelRes to FileAction.COPY)
        add(FileAction.CUT.labelRes to FileAction.CUT)
        add(FileAction.DELETE.labelRes to FileAction.DELETE)
        add(FileAction.RENAME.labelRes to FileAction.RENAME)
        if (!item.isDirectory) add(FileAction.SHARE.labelRes to FileAction.SHARE)
        if (item.isDirectory) {
            add(
                (if (isFavorite) R.string.action_unfavorite else R.string.action_favorite)
                    to FileAction.FAVORITE
            )
            if (isPinned) {
                add(R.string.action_refresh_size to FileAction.REFRESH_SIZE)
                add(R.string.action_unpin_size to FileAction.PIN_SIZE)
            } else {
                add(R.string.action_pin_size to FileAction.PIN_SIZE)
            }
        }
        add(FileAction.PROPERTIES.labelRes to FileAction.PROPERTIES)
        add(FileAction.MULTI_SELECT.labelRes to FileAction.MULTI_SELECT)
    }

    val density = LocalDensity.current
    val popupPositionProvider = remember(anchorPoint) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                popupAnchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val offsetPx = with(density) { 8.dp.roundToPx() }
                val marginPx = with(density) { 4.dp.roundToPx() }

                // 优先在点击位置下方显示，水平居中于点击点
                var x = anchorPoint.x - popupContentSize.width / 2
                var y = anchorPoint.y + offsetPx

                // 下方放不下则翻到上方
                if (y + popupContentSize.height > windowSize.height - marginPx) {
                    y = anchorPoint.y - popupContentSize.height - offsetPx
                    if (y < marginPx) y = marginPx
                }

                // 水平 clamp
                if (x + popupContentSize.width > windowSize.width) {
                    x = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                }
                if (x < marginPx) x = marginPx

                return IntOffset(x, y)
            }
        }
    }

    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = popupPositionProvider,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Column {
                actions.forEach { (labelRes, action) ->
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 180.dp)
                            .clickable { onAction(action) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            color = if (action == FileAction.DELETE)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
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
            IconButton(onClick = onSortClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.sort_by)
                )
            }
            Box {
                IconButton(onClick = { onShowMoreMenuChange(true) }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.more)
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { onShowMoreMenuChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.refresh)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        },
                        onClick = {
                            onRefreshClick()
                            onShowMoreMenuChange(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_folder)) },
                        onClick = {
                            onCreateFolder()
                            onShowMoreMenuChange(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_file)) },
                        onClick = {
                            onCreateFile()
                            onShowMoreMenuChange(false)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (showHidden) R.string.hide_hidden_files
                                    else R.string.show_hidden_files
                                )
                            )
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                text = stringResource(R.string.selected_count, count),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
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
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Filled.SelectAll,
                    contentDescription = stringResource(R.string.select_all)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionBarItem(Icons.Filled.ContentCopy, stringResource(R.string.action_copy), onClick = onCopy)
            ActionBarItem(Icons.Filled.ContentCut, stringResource(R.string.action_cut), onClick = onCut)
            ActionBarItem(Icons.Filled.Delete, stringResource(R.string.action_delete), onClick = onDelete)
            ActionBarItem(
                Icons.Filled.DriveFileRenameOutline, stringResource(R.string.action_rename),
                enabled = singleSelection, onClick = onRename
            )
            ActionBarItem(Icons.Filled.Share, stringResource(R.string.action_share), onClick = onShare)
            Box {
                ActionBarItem(Icons.Filled.MoreVert, stringResource(R.string.more), onClick = { showMenu = true })
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (singleItem != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_properties)) },
                            onClick = {
                                onProperties()
                                showMenu = false
                            }
                        )
                        if (singleItem.isDirectory) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (viewModel.isFavorite(singleItem.path))
                                                R.string.action_unfavorite
                                            else R.string.action_favorite
                                        )
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
                            text = { Text(stringResource(R.string.properties_single_only)) },
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
//  底部导航栏 (MT 风格)
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

// =============================================================================
//  粘贴栏 (MD3 悬浮卡片风格)
// =============================================================================
@Composable
private fun PasteBar(
    itemCount: Int,
    isCut: Boolean,
    onPaste: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCut) Icons.Filled.ContentCut else Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (isCut) R.string.pending_move else R.string.pending_copy,
                    itemCount
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Spacer(modifier = Modifier.width(4.dp))
            Button(onClick = onPaste) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.paste_here))
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
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =============================================================================
//  紧凑面包屑 (MD3 风格)
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
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, (name, path) ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (index == segments.lastIndex)
                    FontWeight.SemiBold
                else
                    FontWeight.Normal,
                color = if (index == segments.lastIndex)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onPathClick(path) }
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            )
            if (index < segments.lastIndex) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun buildPathSegments(path: String, rootLabel: String): List<Pair<String, String>> {
    val segments = mutableListOf<Pair<String, String>>()
    val parts = path.split(File.separator).filter { it.isNotEmpty() }
    var accumulatedPath =
        if (path.startsWith(File.separator)) File.separator else ""

    segments.add(rootLabel to FileManagerViewModel.STORAGE_ROOT)

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
        title = { Text(stringResource(R.string.sort_by)) },
        text = {
            Column {
                SortMode.entries.forEach { mode ->
                    val isSelected = mode == currentMode
                    val arrow = if (isSelected) {
                        if (currentAscending) " ↑" else " ↓"
                    } else ""
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onModeSelected(mode) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onModeSelected(mode) }
                        )
                        Text(
                            text = stringResource(mode.labelRes) + arrow,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                Text(stringResource(R.string.close))
            }
        }
    )
}
