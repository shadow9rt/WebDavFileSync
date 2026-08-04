package com.sync.webdav.data.sync

import kotlinx.coroutines.Job

data class SyncProgressState(
    val isSyncing: Boolean = false,
    val planName: String = "",
    val syncModeText: String = "",
    val totalFilesCount: Int = 0,
    val checkedFilesCount: Int = 0,
    val syncedFilesCount: Int = 0,
    val elapsedSeconds: Long = 0L,
    val progressPercent: Float = 0f,
    val currentOperationText: String = "",
    val syncJob: Job? = null
)
