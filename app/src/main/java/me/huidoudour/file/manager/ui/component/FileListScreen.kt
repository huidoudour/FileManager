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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.huidoudour.file.manager.model.FileItem
import me.huidoudour.file.manager.util.FileTypeUtil
import me.huidoudour.file.manager.util.SortMode
import me.huidoudour.file.manager.viewmodel.FileManagerViewModel
import java.io.File

/**
 * 文件管理器主界面 — 参考「质感文件」Material Files 风格
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
    canGoBack: Boolean = false
) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()

    var showSortDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // ==================== 返回键处理 ====================
    BackHandler(enabled = true) {
        if (isPickerMode && canGoBack) {
            onPickCancelled?.invoke()
        } else if (!viewModel.navigateUp()) {
            // 已在根目录，不做任何操作
        }
    }

    // ==================== 错误对话框 ====================
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

    // ==================== 排序对话框 ====================
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

    Scaffold(
        topBar = {
            Column {
                // ---- 顶部标题栏 ----
                TopAppBar(
                    title = {
                        Text(
                            text = if (isPickerMode) "选择文件" else "文件管理器",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (isPickerMode) {
                            IconButton(onClick = { onPickCancelled?.invoke() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "取消"
                                )
                            }
                        }
                    },
                    actions = {
                        // 排序
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "排序"
                            )
                        }
                        // 刷新
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "刷新"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // ---- 面包屑路径导航栏 ----
                BreadcrumbBar(
                    currentPath = currentPath,
                    onPathClick = { path -> viewModel.loadDirectory(path) }
                )

                HorizontalDivider(thickness = 0.5.dp)
            }
        },
        bottomBar = {
            // 选取模式下的确认栏
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
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = { selectedFile?.let { onPickConfirmed?.invoke(it) } }) {
                            Text("确认选择")
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
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                files.isEmpty() && errorMessage == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "目录为空",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // 返回上级目录 (非存储根目录时显示)
                        if (currentPath != FileManagerViewModel.STORAGE_ROOT
                            && currentPath != "/"
                        ) {
                            item(key = "parent_dir") {
                                ParentDirectoryRow(
                                    onClick = { viewModel.navigateUp() }
                                )
                            }
                        }

                        itemsIndexed(
                            items = files,
                            key = { _, item -> item.path }
                        ) { _, fileItem ->
                            val isSelected = selectedFile?.path == fileItem.path
                            FileItemRow(
                                fileItem = fileItem,
                                viewModel = viewModel,
                                isSelected = isSelected,
                                onItemClick = {
                                    if (fileItem.isDirectory) {
                                        viewModel.navigateToDirectory(fileItem)
                                    } else {
                                        // ✅ BugFix: 非选取模式下也触发文件打开
                                        onFileSelected?.invoke(fileItem)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
//  面包屑导航栏
// =============================================================================
@Composable
private fun BreadcrumbBar(
    currentPath: String,
    onPathClick: (String) -> Unit
) {
    val segments = buildPathSegments(currentPath)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, (name, path) ->
            if (index > 0) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(horizontal = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            val isLast = index == segments.lastIndex
            Text(
                text = name,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !isLast) { onPathClick(path) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (isLast)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 将路径拆分为面包屑段
 */
private fun buildPathSegments(path: String): List<Pair<String, String>> {
    val segments = mutableListOf<Pair<String, String>>()
    val parts = path.split(File.separator).filter { it.isNotEmpty() }
    var accumulatedPath =
        if (path.startsWith(File.separator)) File.separator else ""

    // 首段显示为 "内部存储"
    segments.add("内部存储" to FileManagerViewModel.STORAGE_ROOT)

    for (part in parts) {
        accumulatedPath = if (accumulatedPath.endsWith(File.separator))
            accumulatedPath + part
        else
            accumulatedPath + File.separator + part
        if (accumulatedPath == FileManagerViewModel.STORAGE_ROOT) continue
        // 截断过长的名称
        val displayName = if (part.length > 18) part.take(15) + ".." else part
        segments.add(displayName to accumulatedPath)
    }
    return segments
}

// =============================================================================
//  返回上级目录行
// =============================================================================
@Composable
private fun ParentDirectoryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.SubdirectoryArrowRight,
            contentDescription = "返回上级",
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "..",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// =============================================================================
//  排序选择对话框
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
