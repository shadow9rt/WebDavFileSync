package com.sync.webdav.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sync.webdav.data.remote.WebDavClient
import com.sync.webdav.data.remote.WebDavResource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavFolderPickerDialog(
    initialPath: String,
    webDavUrl: String,
    webDavUser: String,
    webDavPass: String,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    // Default to root "/" when initialPath is empty or "/sync_folder"
    var currentPath by remember {
        mutableStateOf(
            if (initialPath.isEmpty() || initialPath == "/sync_folder") "/" else initialPath
        )
    }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var folderList by remember { mutableStateOf<List<WebDavResource>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()

    fun loadFolders(path: String) {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            if (webDavUrl.isEmpty()) {
                errorMessage = "请先在账号页面配置 WebDAV 服务器地址"
                isLoading = false
                return@launch
            }
            val client = WebDavClient(webDavUrl, webDavUser, webDavPass)
            val result = client.listRemoteFiles(path)
            if (result.isSuccess) {
                folderList = result.getOrDefault(emptyList()).filter { it.isDirectory }
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "无法获取目录列表"
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentPath) {
        loadFolders(currentPath)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentPath != "/") {
                    IconButton(
                        onClick = {
                            val parent = currentPath.trimEnd('/').substringBeforeLast('/', "")
                            currentPath = if (parent.isEmpty()) "/" else parent
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上一级")
                    }
                }
                Text(
                    text = if (currentPath == "/") "浏览 WebDAV 根目录" else "浏览云盘路径",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                // Current Path Display
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "当前: $currentPath",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                    errorMessage != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    folderList.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "（当前目录无子文件夹）",
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(folderList) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val nextPath = if (currentPath.endsWith("/")) "$currentPath${item.name}" else "$currentPath/${item.name}"
                                            currentPath = nextPath
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onFolderSelected(currentPath) }
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("选择此文件夹")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
