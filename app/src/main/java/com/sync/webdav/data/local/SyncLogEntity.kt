package com.sync.webdav.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val planName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "SUCCESS", "FAILED", "RUNNING"
    val summary: String,
    val filesUploadedCount: Int = 0,
    val filesDownloadedCount: Int = 0,
    val detailLog: String = ""
)
