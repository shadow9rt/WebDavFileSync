package com.sync.webdav.data.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sync.webdav.data.local.*
import com.sync.webdav.data.remote.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class SyncEngine(private val context: Context) {

    private val settingsDataStore = SettingsDataStore(context)
    private val database = SyncDatabase.getInstance(context)
    private val dao = database.syncDao()

    suspend fun executeSyncForPlan(
        planId: Long,
        onProgressUpdate: (SyncProgressState) -> Unit
    ): Result<SyncLogEntity> = withContext(Dispatchers.IO) {
        val plan = dao.getPlanById(planId)
            ?: return@withContext Result.failure(Exception("找不到同步计划 ID: $planId"))

        val url = settingsDataStore.webDavUrl.first()
        val user = settingsDataStore.webDavUser.first()
        val pass = settingsDataStore.webDavPass.first()

        if (url.isEmpty()) {
            return@withContext Result.failure(Exception("请先在账号页面配置 WebDAV 服务器信息"))
        }

        val syncModeText = when (plan.syncDirection) {
            SyncDirection.TWO_WAY -> "双向合并"
            SyncDirection.LOCAL_TO_REMOTE -> "单向上传"
            SyncDirection.REMOTE_TO_LOCAL -> "单向下载"
        }

        var progressState = SyncProgressState(
            isSyncing = true,
            planName = plan.planName,
            syncModeText = syncModeText,
            currentOperationText = "正在解析两端目录结构..."
        )
        onProgressUpdate(progressState)

        val logId = dao.insertLog(
            SyncLogEntity(
                planId = plan.id,
                planName = plan.planName,
                status = "RUNNING",
                summary = "正在进行比对与同步...",
                detailLog = "启动同步计划: ${plan.planName}\n云盘路径: ${plan.webDavPath}\n"
            )
        )

        var uploadedCount = 0
        var downloadedCount = 0
        val logDetails = StringBuilder("--- 文件比对与同步日志 ---\n")
        val startTime = System.currentTimeMillis()

        try {
            val webDavClient = WebDavClient(url, user, pass)

            ensureActive()
            progressState = progressState.copy(currentOperationText = "正在读取云盘文件列表...")
            onProgressUpdate(progressState)

            // 1. Fetch Remote Files
            val remoteResult = webDavClient.listRemoteFiles(plan.webDavPath)
            if (remoteResult.isFailure) {
                val err = remoteResult.exceptionOrNull()?.message ?: "远程文件列表读取失败"
                throw Exception(err)
            }
            val remoteResources = remoteResult.getOrDefault(emptyList()).filter { !it.isDirectory }
            val remoteFileMap = remoteResources.associateBy { it.name }

            logDetails.append("云盘已有文件数: ${remoteFileMap.size}\n")

            ensureActive()
            progressState = progressState.copy(currentOperationText = "正在读取本地文件夹...")
            onProgressUpdate(progressState)

            // 2. Fetch Local Files via SAF
            val localTreeUri = Uri.parse(plan.localUriString)
            val rootDoc = DocumentFile.fromTreeUri(context, localTreeUri)
                ?: throw Exception("无法读取本地文件夹，请重新选择本地目录")

            val localFiles = rootDoc.listFiles().filter { it.isFile && it.name != null }
            val localFileMap = localFiles.associateBy { it.name!! }

            logDetails.append("本地已有文件数: ${localFileMap.size}\n")

            // 3. Difference Comparison by File Name (including extension)
            val remoteFileNames = remoteFileMap.keys
            val localFileNames = localFileMap.keys

            val missingOnRemote = localFileNames - remoteFileNames
            val missingOnLocal = remoteFileNames - localFileNames

            val totalDiffFiles = missingOnRemote.size + missingOnLocal.size
            val totalAllFiles = (remoteFileNames + localFileNames).size

            progressState = progressState.copy(
                totalFilesCount = totalAllFiles,
                checkedFilesCount = totalAllFiles,
                currentOperationText = "比对完成，发现待处理差异文件 ${totalDiffFiles} 个"
            )
            onProgressUpdate(progressState)

            logDetails.append("待上传至云盘的文件 (${missingOnRemote.size}): $missingOnRemote\n")
            logDetails.append("待下载至本地的文件 (${missingOnLocal.size}): $missingOnLocal\n")

            // 4. Perform Sync based on Direction
            val direction = plan.syncDirection
            var processedCount = 0

            // Upload Local -> WebDAV
            if (direction == SyncDirection.TWO_WAY || direction == SyncDirection.LOCAL_TO_REMOTE) {
                for (fileName in missingOnRemote) {
                    ensureActive()

                    progressState = progressState.copy(
                        syncedFilesCount = processedCount,
                        progressPercent = if (totalDiffFiles > 0) processedCount.toFloat() / totalDiffFiles else 1f,
                        currentOperationText = "$fileName 正在上传至云盘..."
                    )
                    onProgressUpdate(progressState)

                    val docFile = localFileMap[fileName] ?: continue
                    val remoteFilePath = if (plan.webDavPath.endsWith("/")) "${plan.webDavPath}$fileName" else "${plan.webDavPath}/$fileName"

                    val inputStream = context.contentResolver.openInputStream(docFile.uri)
                    if (inputStream != null) {
                        val uploadRes = webDavClient.uploadFile(remoteFilePath, inputStream, docFile.length())
                        inputStream.close()

                        if (uploadRes.isSuccess) {
                            uploadedCount++
                            logDetails.append("✔ 成功上传文件到云盘: $fileName\n")
                        } else {
                            logDetails.append("❌ 上传失败 [$fileName]: ${uploadRes.exceptionOrNull()?.message}\n")
                        }
                    }
                    processedCount++
                }
            }

            // Download WebDAV -> Local
            if (direction == SyncDirection.TWO_WAY || direction == SyncDirection.REMOTE_TO_LOCAL) {
                for (fileName in missingOnLocal) {
                    ensureActive()

                    progressState = progressState.copy(
                        syncedFilesCount = processedCount,
                        progressPercent = if (totalDiffFiles > 0) processedCount.toFloat() / totalDiffFiles else 1f,
                        currentOperationText = "$fileName 正在从云盘下载..."
                    )
                    onProgressUpdate(progressState)

                    val remoteResource = remoteFileMap[fileName] ?: continue
                    val remoteFilePath = if (plan.webDavPath.endsWith("/")) "${plan.webDavPath}$fileName" else "${plan.webDavPath}/$fileName"

                    val mimeType = context.contentResolver.getType(Uri.parse(remoteResource.href)) ?: "application/octet-stream"
                    val newDoc = rootDoc.createFile(mimeType, fileName)

                    if (newDoc != null) {
                        val outputStream = context.contentResolver.openOutputStream(newDoc.uri)
                        if (outputStream != null) {
                            val downloadRes = webDavClient.downloadFile(remoteFilePath, outputStream)
                            outputStream.close()

                            if (downloadRes.isSuccess) {
                                downloadedCount++
                                logDetails.append("✔ 成功从云盘下载文件: $fileName\n")
                            } else {
                                logDetails.append("❌ 下载失败 [$fileName]: ${downloadRes.exceptionOrNull()?.message}\n")
                            }
                        }
                    }
                    processedCount++
                }
            }

            val summaryMsg = "同步完成！上传 $uploadedCount 个，下载 $downloadedCount 个"
            logDetails.append("\n$summaryMsg")

            progressState = progressState.copy(
                isSyncing = false,
                syncedFilesCount = totalDiffFiles,
                progressPercent = 1f,
                currentOperationText = "同步完成"
            )
            onProgressUpdate(progressState)

            val completedLog = SyncLogEntity(
                id = logId,
                planId = plan.id,
                planName = plan.planName,
                timestamp = System.currentTimeMillis(),
                status = "SUCCESS",
                summary = summaryMsg,
                filesUploadedCount = uploadedCount,
                filesDownloadedCount = downloadedCount,
                detailLog = logDetails.toString()
            )

            dao.insertLog(completedLog)
            dao.updatePlan(
                plan.copy(
                    lastSyncTime = System.currentTimeMillis(),
                    lastSyncStatus = summaryMsg
                )
            )

            Result.success(completedLog)
        } catch (e: Exception) {
            val failSummary = if (e is kotlinx.coroutines.CancellationException) "同步已被用户取消" else "同步失败: ${e.message}"
            logDetails.append("\n❌ 异常/取消: ${e.message}")

            progressState = progressState.copy(
                isSyncing = false,
                currentOperationText = failSummary
            )
            onProgressUpdate(progressState)

            val failedLog = SyncLogEntity(
                id = logId,
                planId = plan.id,
                planName = plan.planName,
                timestamp = System.currentTimeMillis(),
                status = if (e is kotlinx.coroutines.CancellationException) "CANCELLED" else "FAILED",
                summary = failSummary,
                filesUploadedCount = uploadedCount,
                filesDownloadedCount = downloadedCount,
                detailLog = logDetails.toString()
            )

            dao.insertLog(failedLog)
            dao.updatePlan(
                plan.copy(
                    lastSyncTime = System.currentTimeMillis(),
                    lastSyncStatus = failSummary
                )
            )

            Result.failure(e)
        }
    }
}
