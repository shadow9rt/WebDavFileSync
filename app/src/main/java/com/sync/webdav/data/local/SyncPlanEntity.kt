package com.sync.webdav.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncDirection {
    TWO_WAY,        // 双向合并
    LOCAL_TO_REMOTE,// 单向：本地 -> 云盘
    REMOTE_TO_LOCAL // 单向：云盘 -> 本地
}

@Entity(tableName = "sync_plans")
data class SyncPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planName: String,
    val webDavPath: String,
    val localUriString: String,
    val syncDirection: SyncDirection = SyncDirection.TWO_WAY,
    val isEnabled: Boolean = true,
    val lastSyncTime: Long = 0L,
    val lastSyncStatus: String = "未执行"
)
