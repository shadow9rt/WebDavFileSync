package com.sync.webdav.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sync.webdav.data.local.SyncDirection
import com.sync.webdav.data.local.SyncPlanEntity
import com.sync.webdav.ui.components.WebDavFolderPickerDialog
import com.sync.webdav.utils.PathUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPlanScreen(
    plans: List<SyncPlanEntity>,
    webDavUrl: String,
    webDavUser: String,
    webDavPass: String,
    onAddPlan: (SyncPlanEntity) -> Unit,
    onUpdatePlan: (SyncPlanEntity) -> Unit,
    onDeletePlan: (SyncPlanEntity) -> Unit
) {
    var showAddEditDialog by remember { mutableStateOf(false) }
    var planToEdit by remember { mutableStateOf<SyncPlanEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("同步计划设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                windowInsets = WindowInsets.statusBars
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    planToEdit = null
                    showAddEditDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新增同步计划", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(14.dp)
        ) {
            if (plans.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无同步计划，点击右下角添加",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(plans) { plan ->
                        SyncPlanCard(
                            plan = plan,
                            onToggleEnable = { enabled ->
                                onUpdatePlan(plan.copy(isEnabled = enabled))
                            },
                            onEdit = {
                                planToEdit = plan
                                showAddEditDialog = true
                            },
                            onDelete = { onDeletePlan(plan) }
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditPlanDialog(
            planToEdit = planToEdit,
            webDavUrl = webDavUrl,
            webDavUser = webDavUser,
            webDavPass = webDavPass,
            onDismiss = { showAddEditDialog = false },
            onConfirm = { plan ->
                if (planToEdit != null) {
                    onUpdatePlan(plan)
                } else {
                    onAddPlan(plan)
                }
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun SyncPlanCard(
    plan: SyncPlanEntity,
    onToggleEnable: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val readablePath = remember(plan.localUriString) {
        PathUtils.formatDisplayPath(context, plan.localUriString)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = plan.planName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = plan.isEnabled,
                        onCheckedChange = onToggleEnable
                    )
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "修改",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "☁️ 云盘路径: ${plan.webDavPath.ifEmpty { "/" }}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "📱 本地目录: $readablePath",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔄 同步模式: ", style = MaterialTheme.typography.bodyMedium)
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = when (plan.syncDirection) {
                                    SyncDirection.TWO_WAY -> "双向合并 (合并增量文件)"
                                    SyncDirection.LOCAL_TO_REMOTE -> "单向：仅上传至云盘"
                                    SyncDirection.REMOTE_TO_LOCAL -> "单向：仅从云盘下载"
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPlanDialog(
    planToEdit: SyncPlanEntity? = null,
    webDavUrl: String,
    webDavUser: String,
    webDavPass: String,
    onDismiss: () -> Unit,
    onConfirm: (SyncPlanEntity) -> Unit
) {
    val context = LocalContext.current

    var planName by remember { mutableStateOf(planToEdit?.planName ?: "") }
    // No hardcoded "/sync_folder". Default to empty string for new plan.
    var webDavPath by remember { mutableStateOf(planToEdit?.webDavPath ?: "") }
    var selectedLocalUriString by remember { mutableStateOf(planToEdit?.localUriString ?: "") }
    var selectedDirection by remember { mutableStateOf(planToEdit?.syncDirection ?: SyncDirection.TWO_WAY) }

    var showWebDavBrowser by remember { mutableStateOf(false) }

    val readableLocalPath = remember(selectedLocalUriString) {
        if (selectedLocalUriString.isEmpty()) "点击选择本地文件夹"
        else PathUtils.formatDisplayPath(context, selectedLocalUriString)
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedLocalUriString = uri.toString()
            if (planName.isEmpty()) {
                planName = uri.lastPathSegment?.substringAfterLast(':') ?: "新同步计划"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (planToEdit != null) "修改文件同步计划" else "新建文件同步计划") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = planName,
                    onValueChange = { planName = it },
                    label = { Text("计划名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Remote WebDAV Directory Picker Button
                OutlinedCard(
                    onClick = { showWebDavBrowser = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("云盘 WebDAV 文件夹", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = if (webDavPath.isEmpty()) "点击选择云盘文件夹" else webDavPath,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (webDavPath.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Local Directory Picker Button
                OutlinedCard(
                    onClick = { dirPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("本地文件夹路径", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = readableLocalPath,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedLocalUriString.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Text("同步模式:", style = MaterialTheme.typography.labelLarge)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedDirection == SyncDirection.TWO_WAY,
                            onClick = { selectedDirection = SyncDirection.TWO_WAY }
                        )
                        Text("双向合并 (补全两端差异文件)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedDirection == SyncDirection.LOCAL_TO_REMOTE,
                            onClick = { selectedDirection = SyncDirection.LOCAL_TO_REMOTE }
                        )
                        Text("单向上传 (本地 -> 云盘)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedDirection == SyncDirection.REMOTE_TO_LOCAL,
                            onClick = { selectedDirection = SyncDirection.REMOTE_TO_LOCAL }
                        )
                        Text("单向下载 (云盘 -> 本地)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (planName.isNotEmpty() && selectedLocalUriString.isNotEmpty()) {
                        val finalWebDavPath = if (webDavPath.isEmpty()) "/" else webDavPath
                        val plan = planToEdit?.copy(
                            planName = planName,
                            webDavPath = finalWebDavPath,
                            localUriString = selectedLocalUriString,
                            syncDirection = selectedDirection
                        ) ?: SyncPlanEntity(
                            planName = planName,
                            webDavPath = finalWebDavPath,
                            localUriString = selectedLocalUriString,
                            syncDirection = selectedDirection
                        )
                        onConfirm(plan)
                    }
                },
                enabled = planName.isNotEmpty() && selectedLocalUriString.isNotEmpty()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showWebDavBrowser) {
        WebDavFolderPickerDialog(
            initialPath = webDavPath,
            webDavUrl = webDavUrl,
            webDavUser = webDavUser,
            webDavPass = webDavPass,
            onDismiss = { showWebDavBrowser = false },
            onFolderSelected = { path ->
                webDavPath = path
                showWebDavBrowser = false
            }
        )
    }
}
