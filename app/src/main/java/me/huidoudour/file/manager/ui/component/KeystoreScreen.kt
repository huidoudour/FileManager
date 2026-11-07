package me.huidoudour.file.manager.ui.component

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.huidoudour.file.manager.R
import me.huidoudour.file.manager.signing.KeyGenHelper
import me.huidoudour.file.manager.signing.KeystoreConfig
import me.huidoudour.file.manager.signing.KeystoreHelper
import me.huidoudour.file.manager.signing.KeystoreRepository
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 签名密钥管理页 (设置 → 签名密钥)。
 *
 * 密钥库文件存放在应用私有目录，列表展示配置信息，支持导入 JKS/PKCS12/BKS、
 * 应用内生成密钥、导出与删除；内置公开 testkey 常驻且不可删除。
 */
@Composable
fun KeystoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { KeystoreRepository(context) }

    var configs by remember { mutableStateOf<List<KeystoreConfig>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAddChoice by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<KeystoreConfig?>(null) }
    var exportTarget by remember { mutableStateOf<KeystoreConfig?>(null) }

    fun reload() {
        scope.launch {
            configs = withContext(Dispatchers.IO) { repository.loadAll() }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val source = repository.getKeystoreFile(target)
                    if (!source.exists()) throw IOException(context.getString(R.string.keystore_file_missing))
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("无法写入所选位置")
                    output.use { out -> source.inputStream().use { it.copyTo(out) } }
                    null
                } catch (e: Exception) {
                    e.message ?: e.toString()
                }
            }
            if (error == null) {
                Toast.makeText(context, R.string.keystore_export_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.keystore_export_failed, error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 返回键优先关闭本页，而不是退出设置
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.keystore_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddChoice = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.keystore_add)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.keystore_storage_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                if (configs.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.keystore_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    items(configs, key = { it.id }) { config ->
                        KeystoreRow(
                            config = config,
                            onExport = {
                                exportTarget = config
                                exportLauncher.launch(config.fileName)
                            },
                            onDelete = if (config.isBuiltin) null else ({ deleteTarget = config })
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showAddChoice) {
        AlertDialog(
            onDismissRequest = { showAddChoice = false },
            title = { Text(stringResource(R.string.keystore_add)) },
            text = {
                Column {
                    AddChoiceRow(
                        icon = Icons.Filled.UploadFile,
                        title = stringResource(R.string.keystore_import),
                        description = stringResource(R.string.keystore_import_desc)
                    ) {
                        showAddChoice = false
                        showImportDialog = true
                    }
                    AddChoiceRow(
                        icon = Icons.Filled.VpnKey,
                        title = stringResource(R.string.keystore_generate),
                        description = stringResource(R.string.keystore_generate_desc)
                    ) {
                        showAddChoice = false
                        showGenerateDialog = true
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddChoice = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showImportDialog) {
        ImportKeystoreDialog(
            repository = repository,
            onImported = {
                showImportDialog = false
                reload()
            },
            onDismiss = { showImportDialog = false }
        )
    }

    if (showGenerateDialog) {
        GenerateKeyDialog(
            repository = repository,
            onGenerated = {
                showGenerateDialog = false
                reload()
            },
            onDismiss = { showGenerateDialog = false }
        )
    }

    deleteTarget?.let { config ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.keystore_delete_title)) },
            text = { Text(stringResource(R.string.keystore_delete_message, config.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.delete(config) }
                        deleteTarget = null
                        reload()
                    }
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** 密钥列表行 */
@Composable
private fun KeystoreRow(
    config: KeystoreConfig,
    onExport: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.VpnKey,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.keystore_detail,
                    config.type,
                    config.keyAlias,
                    formatKeystoreDate(config.createdAt)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onExport) {
            Icon(
                imageVector = Icons.Filled.SaveAlt,
                contentDescription = stringResource(R.string.keystore_export)
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** 「添加密钥」中的选项行 */
@Composable
private fun AddChoiceRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 导入密钥库对话框 */
@Composable
private fun ImportKeystoreDialog(
    repository: KeystoreRepository,
    onImported: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedFile by remember { mutableStateOf<File?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    // 密码用 TextFieldState 承载, 交给 OutlinedSecureTextField (旧的 visualTransformation 方案已废弃)
    val storePasswordState = rememberTextFieldState()
    val keyPasswordState = rememberTextFieldState()
    var configName by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf<List<String>>(emptyList()) }
    var alias by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf<KeystoreHelper.Loaded?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var aliasMenu by remember { mutableStateOf(false) }

    // 对话框关闭时清理缓存的待导入文件
    DisposableEffect(Unit) {
        onDispose { pickedFile?.delete() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val name = queryDisplayName(context, uri)
                val temp = withContext(Dispatchers.IO) {
                    val file = File(context.cacheDir, "import_${System.currentTimeMillis()}")
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("无法读取所选文件")
                    input.use { source -> file.outputStream().use { source.copyTo(it) } }
                    file
                }
                pickedFile?.delete()
                pickedFile = temp
                pickedName = name
                if (configName.isBlank()) configName = name.substringBeforeLast('.')
                aliases = emptyList()
                alias = null
                loaded = null
                status = null
                error = null
            } catch (e: Exception) {
                error = context.getString(R.string.keystore_load_failed, e.message ?: e.toString())
            }
        }
    }

    fun loadAliases() {
        val file = pickedFile
        if (file == null) {
            error = context.getString(R.string.keystore_pick_file_first)
            return
        }
        val storePwd = storePasswordState.text.toString()
        if (storePwd.isEmpty()) {
            error = context.getString(R.string.keystore_required)
            return
        }
        busy = true
        error = null
        status = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val result = KeystoreHelper.load(file, storePwd.toCharArray())
                    result to result.keyAliases()
                }
                if (result.second.isEmpty()) {
                    loaded = null
                    aliases = emptyList()
                    alias = null
                    error = context.getString(R.string.keystore_no_key_alias)
                } else {
                    loaded = result.first
                    aliases = result.second
                    alias = result.second.first()
                    status = context.getString(R.string.keystore_load_success, result.first.type)
                }
            } catch (e: Exception) {
                loaded = null
                aliases = emptyList()
                alias = null
                error = context.getString(R.string.keystore_load_failed, e.message ?: e.toString())
            } finally {
                busy = false
            }
        }
    }

    fun confirm() {
        val file = pickedFile
        val current = loaded
        val selectedAlias = alias
        if (file == null) {
            error = context.getString(R.string.keystore_pick_file_first)
            return
        }
        if (current == null || selectedAlias == null) {
            error = context.getString(R.string.keystore_load_aliases_first)
            return
        }
        if (configName.isBlank()) {
            error = context.getString(R.string.keystore_required)
            return
        }
        busy = true
        error = null
        // 在主线程取出密码快照, 供 IO 线程校验与入库使用
        val storePwd = storePasswordState.text.toString()
        val keyPwd = keyPasswordState.text.toString()
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 以当前密码重新加载并校验别名密码能取出私钥，再复制文件入库
                    val verified = KeystoreHelper.load(file, storePwd.toCharArray())
                    verified.getSigner(selectedAlias, keyPwd.toCharArray())
                    val fileName = file.inputStream().use {
                        repository.importKeystoreFile(it, pickedName)
                    }
                    repository.add(
                        KeystoreConfig.create(
                            name = configName.trim(),
                            fileName = fileName,
                            type = verified.type,
                            storePassword = storePwd,
                            keyAlias = selectedAlias,
                            keyPassword = keyPwd
                        )
                    )
                }
                Toast.makeText(context, R.string.keystore_import_success, Toast.LENGTH_SHORT).show()
                onImported()
            } catch (e: Exception) {
                error = context.getString(R.string.keystore_verify_failed, e.message ?: e.toString())
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
        title = { Text(stringResource(R.string.keystore_import_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.keystore_import_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = pickedName ?: stringResource(R.string.keystore_pick_file),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedSecureTextField(
                    state = storePasswordState,
                    label = { Text(stringResource(R.string.keystore_store_password)) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { loadAliases() },
                    enabled = !busy && pickedFile != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.keystore_load_aliases))
                }

                status?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (aliases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        OutlinedButton(
                            onClick = { aliasMenu = true },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = alias ?: stringResource(R.string.keystore_alias),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(
                            expanded = aliasMenu,
                            onDismissRequest = { aliasMenu = false }
                        ) {
                            aliases.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        alias = item
                                        aliasMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedSecureTextField(
                        state = keyPasswordState,
                        label = { Text(stringResource(R.string.keystore_key_password)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = configName,
                        onValueChange = { configName = it },
                        label = { Text(stringResource(R.string.keystore_config_name)) },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (busy) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.keystore_working),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = !busy) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** 生成密钥对话框 */
@Composable
private fun GenerateKeyDialog(
    repository: KeystoreRepository,
    onGenerated: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var configName by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    // 密码用 TextFieldState 承载, 交给 OutlinedSecureTextField (旧的 visualTransformation 方案已废弃)
    val passwordState = rememberTextFieldState()
    var commonName by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var organizationalUnit by remember { mutableStateOf("") }
    var validityYears by remember { mutableStateOf("25") }
    var algorithm by remember { mutableStateOf(KeyGenHelper.ALG_RSA_2048) }
    var algorithmMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun confirm() {
        val pwd = passwordState.text.toString()
        if (configName.isBlank() || alias.isBlank() || pwd.isEmpty() || commonName.isBlank()) {
            error = context.getString(R.string.keystore_required)
            return
        }
        val years = validityYears.trim().toIntOrNull()
        if (years == null || years !in 1..100) {
            error = context.getString(R.string.keystore_validity_invalid)
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val fileName = KeyGenHelper.generate(
                        keystoreDir = repository.keystoreDir,
                        alias = alias.trim(),
                        password = pwd.toCharArray(),
                        algorithm = algorithm,
                        cn = commonName.trim(),
                        o = organization.trim(),
                        ou = organizationalUnit.trim(),
                        validityYears = years
                    )
                    repository.add(
                        KeystoreConfig.create(
                            name = configName.trim(),
                            fileName = fileName,
                            type = KeystoreHelper.TYPE_PKCS12,
                            storePassword = pwd,
                            keyAlias = alias.trim(),
                            keyPassword = pwd
                        )
                    )
                }
                Toast.makeText(context, R.string.keystore_generate_success, Toast.LENGTH_SHORT).show()
                onGenerated()
            } catch (e: Exception) {
                error = context.getString(R.string.keystore_generate_failed, e.message ?: e.toString())
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.VpnKey, contentDescription = null) },
        title = { Text(stringResource(R.string.keystore_generate_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = configName,
                    onValueChange = { configName = it },
                    label = { Text(stringResource(R.string.keystore_config_name)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.keystore_alias)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedSecureTextField(
                    state = passwordState,
                    label = { Text(stringResource(R.string.keystore_password)) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = commonName,
                    onValueChange = { commonName = it },
                    label = { Text(stringResource(R.string.keystore_cn)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it },
                    label = { Text(stringResource(R.string.keystore_organization)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = organizationalUnit,
                    onValueChange = { organizationalUnit = it },
                    label = { Text(stringResource(R.string.keystore_ou)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = validityYears,
                    onValueChange = { validityYears = it },
                    label = { Text(stringResource(R.string.keystore_validity_years)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { algorithmMenu = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                R.string.keystore_algorithm_value,
                                stringResource(R.string.keystore_algorithm),
                                algorithm
                            )
                        )
                    }
                    DropdownMenu(
                        expanded = algorithmMenu,
                        onDismissRequest = { algorithmMenu = false }
                    ) {
                        KeyGenHelper.algorithms.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    algorithm = item
                                    algorithmMenu = false
                                }
                            )
                        }
                    }
                }

                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (busy) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.keystore_generating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = !busy) {
                Text(stringResource(R.string.keystore_generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** 读取 SAF 文件的显示名 */
private fun queryDisplayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) {
                cursor.getString(index)?.let { return it }
            }
        }
    } catch (_: Exception) {
        // 读取失败时用默认名
    }
    return "keystore"
}

private fun formatKeystoreDate(timestamp: Long): String =
    if (timestamp <= 0) "-" else {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
