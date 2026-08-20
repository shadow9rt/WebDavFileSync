package com.sync.webdav.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sync.webdav.data.local.SettingsDataStore
import com.sync.webdav.data.local.SyncDatabase
import com.sync.webdav.data.local.ThemeMode
import com.sync.webdav.data.sync.SyncEngine
import com.sync.webdav.data.sync.SyncProgressState
import com.sync.webdav.ui.components.NotificationDialog
import com.sync.webdav.ui.components.SettingsModal
import com.sync.webdav.ui.components.SyncHistoryDialog
import com.sync.webdav.ui.screens.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : BottomNavItem("home", "主页", Icons.Filled.Home, Icons.Outlined.Home)
    object SyncPlan : BottomNavItem("sync_plan", "同步计划", Icons.Filled.Sync, Icons.Outlined.Sync)
    object Account : BottomNavItem("account", "账号", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
}

@Composable
fun MainScreen(
    syncDatabase: SyncDatabase,
    settingsDataStore: SettingsDataStore,
    syncEngine: SyncEngine
) {
    var currentScreen by remember { mutableStateOf<BottomNavItem>(BottomNavItem.Home) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog / Modal Visibility States
    var showSettingsModal by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showSyncHistoryDialog by remember { mutableStateOf(false) }

    // Sync State
    var progressState by remember { mutableStateOf(SyncProgressState()) }
    var activeSyncJob by remember { mutableStateOf<Job?>(null) }

    // Timer Job
    var timerJob by remember { mutableStateOf<Job?>(null) }

    // Data Store flows
    val themeMode by settingsDataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val wifiOnly by settingsDataStore.wifiOnly.collectAsState(initial = false)
    val webDavUrl by settingsDataStore.webDavUrl.collectAsState(initial = "")
    val webDavUser by settingsDataStore.webDavUser.collectAsState(initial = "")
    val webDavPass by settingsDataStore.webDavPass.collectAsState(initial = "")

    // Room DB flows
    val plans by syncDatabase.syncDao().getAllPlans().collectAsState(initial = emptyList())
    val recentLogs by syncDatabase.syncDao().getRecentLogs().collectAsState(initial = emptyList())

    val navItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.SyncPlan,
        BottomNavItem.Account
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
        },
        bottomBar = {
            NavigationBar(
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                navItems.forEach { item ->
                    val isSelected = currentScreen.route == item.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = item },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (currentScreen) {
                BottomNavItem.Home -> HomeScreen(
                    progressState = progressState,
                    onSyncNowClicked = {
                        val enabledPlans = plans.filter { it.isEnabled }
                        if (enabledPlans.isEmpty()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("暂无可执行的同步计划，请先在同步计划页面新增或开启计划")
                            }
                            return@HomeScreen
                        }

                        // Start Sync
                        activeSyncJob = coroutineScope.launch {
                            var elapsedSec = 0L
                            timerJob?.cancel()
                            timerJob = launch {
                                while (true) {
                                    delay(1000L)
                                    elapsedSec++
                                    progressState = progressState.copy(elapsedSeconds = elapsedSec)
                                }
                            }

                            for (plan in enabledPlans) {
                                syncEngine.executeSyncForPlan(plan.id) { state ->
                                    progressState = state.copy(elapsedSeconds = elapsedSec)
                                }
                            }

                            timerJob?.cancel()
                            progressState = progressState.copy(isSyncing = false)
                            snackbarHostState.showSnackbar("同步已完成")
                        }
                    },
                    onCancelSyncClicked = {
                        activeSyncJob?.cancel()
                        timerJob?.cancel()
                        progressState = progressState.copy(isSyncing = false, currentOperationText = "同步已取消")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("已取消本次同步")
                        }
                    },
                    onOpenNotifications = { showNotificationsDialog = true },
                    onOpenSettings = { showSettingsModal = true },
                    onOpenSyncHistory = { showSyncHistoryDialog = true }
                )

                BottomNavItem.SyncPlan -> SyncPlanScreen(
                    plans = plans,
                    webDavUrl = webDavUrl,
                    webDavUser = webDavUser,
                    webDavPass = webDavPass,
                    onAddPlan = { newPlan ->
                        coroutineScope.launch {
                            syncDatabase.syncDao().insertPlan(newPlan)
                            snackbarHostState.showSnackbar("已添加同步计划: ${newPlan.planName}")
                        }
                    },
                    onUpdatePlan = { updatedPlan ->
                        coroutineScope.launch {
                            syncDatabase.syncDao().updatePlan(updatedPlan)
                            snackbarHostState.showSnackbar("已更新同步计划: ${updatedPlan.planName}")
                        }
                    },
                    onDeletePlan = { planToDelete ->
                        coroutineScope.launch {
                            syncDatabase.syncDao().deletePlan(planToDelete)
                            snackbarHostState.showSnackbar("已删除同步计划: ${planToDelete.planName}")
                        }
                    }
                )

                BottomNavItem.Account -> AccountScreen(
                    currentUrl = webDavUrl,
                    currentUser = webDavUser,
                    currentPass = webDavPass,
                    onSaveAccount = { url, user, pass ->
                        coroutineScope.launch {
                            settingsDataStore.saveWebDavAccount(url, user, pass)
                            snackbarHostState.showSnackbar("WebDAV 账号配置保存成功")
                        }
                    }
                )
            }
        }
    }

    // Modals / Dialogs
    if (showSettingsModal) {
        SettingsModal(
            currentThemeMode = themeMode,
            isWifiOnly = wifiOnly,
            onThemeModeChanged = { newMode ->
                coroutineScope.launch { settingsDataStore.saveThemeMode(newMode) }
            },
            onWifiOnlyChanged = { newWifi ->
                coroutineScope.launch { settingsDataStore.saveWifiOnly(newWifi) }
            },
            onClearLogsClicked = {
                coroutineScope.launch {
                    syncDatabase.syncDao().clearAllLogs()
                    snackbarHostState.showSnackbar("已清空历史同步日志")
                }
            },
            onDismiss = { showSettingsModal = false }
        )
    }

    if (showNotificationsDialog) {
        NotificationDialog(
            logs = recentLogs,
            onDismiss = { showNotificationsDialog = false }
        )
    }

    if (showSyncHistoryDialog) {
        SyncHistoryDialog(
            logs = recentLogs,
            onDismiss = { showSyncHistoryDialog = false }
        )
    }
}
