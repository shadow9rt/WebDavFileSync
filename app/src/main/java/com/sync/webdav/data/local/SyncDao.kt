package com.sync.webdav.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {

    // Plans
    @Query("SELECT * FROM sync_plans ORDER BY id DESC")
    fun getAllPlans(): Flow<List<SyncPlanEntity>>

    @Query("SELECT * FROM sync_plans WHERE isEnabled = 1")
    suspend fun getEnabledPlans(): List<SyncPlanEntity>

    @Query("SELECT * FROM sync_plans WHERE id = :id")
    suspend fun getPlanById(id: Long): SyncPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: SyncPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: SyncPlanEntity)

    @Delete
    suspend fun deletePlan(plan: SyncPlanEntity)

    // Logs
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLogEntity): Long

    @Query("DELETE FROM sync_logs")
    suspend fun clearAllLogs()
}
