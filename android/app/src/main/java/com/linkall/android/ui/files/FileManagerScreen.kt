package com.linkall.android.ui.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.util.SafHelper
import com.linkall.android.util.Util

/**
 * 文件管理器：SAF 双向传输、目录浏览、上传/下载队列、传输统计
 * Phase 5 增强：断点续传、进度通知、外部 SD 卡读写
 */
@Composable
fun FileManagerScreen(
    transferManager: FileTransferManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0=远程浏览, 1=本地上传, 2=传输队列
    var currentPath by remember { mutableStateOf("/") }
    val pathHistory = remember { mutableStateListOf("/") }

    // 远程目录条目由 FileTransferManager 持有（file_list_response 到达时填充）
    val remoteEntries = transferManager.remoteEntries
    var remoteLoading by remember { mutableStateOf(false) }

    // 远程目录条目变化时结束加载状态
    LaunchedEffect(remoteEntries) {
        if (remoteEntries.isNotEmpty()) remoteLoading = false
    }

    // 上传文件选择器
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            val name = SafHelper.queryFileName(context, uri) ?: "unknown"
            val size = SafHelper.queryFileSize(context, uri)
            val hash = runCatching { SafHelper.computeSha256(context, uri) }.getOrDefault("")
            transferManager.enqueueUpload(uri, name, size, hash, currentPath)
        }
    }

    Column(modifier.fillMaxSize()) {
        // Tab 栏
        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 },
                text = { Text(stringResource(R.string.control_files_remote)) })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 },
                text = { Text(stringResource(R.string.control_files_upload)) })
            Tab(selected = activeTab == 2, onClick = { activeTab = 2 },
                text = { Text(stringResource(R.string.control_files_queue) + " (${transferManager.tasks.size})") })
        }

        when (activeTab) {
            0 -> {
                // 远程目录浏览
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (pathHistory.size > 1) {
                            pathHistory.removeAt(pathHistory.lastIndex)
                            currentPath = pathHistory.last()
                        }
                    }, enabled = pathHistory.size > 1) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "上级")
                    }
                    Text(currentPath, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        // 请求刷新远程目录（通过 DataChannel 发送 file_list_request）
                        remoteLoading = true
                        transferManager.requestRemoteList(currentPath)
                    }) { Text(stringResource(R.string.common_refresh)) }
                }
                HorizontalDivider()
                if (remoteLoading) {
                    Text(stringResource(R.string.common_loading),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (remoteEntries.isEmpty()) {
                    Text(stringResource(R.string.control_files_empty),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(remoteEntries) { entry ->
                            RemoteFileRow(entry, onClick = {
                                if (entry.isDir) {
                                    val newPath = if (currentPath.endsWith("/"))
                                        currentPath + entry.name
                                    else "$currentPath/${entry.name}"
                                    pathHistory.add(newPath)
                                    currentPath = newPath
                                    remoteLoading = true
                                    transferManager.requestRemoteList(newPath)
                                } else {
                                    // 请求下载
                                    transferManager.enqueueDownload(
                                        entry.name, entry.size,
                                        if (currentPath.endsWith("/")) currentPath + entry.name
                                        else "$currentPath/${entry.name}"
                                    )
                                    activeTab = 2
                                }
                            })
                        }
                    }
                }
            }
            1 -> {
                // 本地上传
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.control_files_drag),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { uploadLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.control_files_upload))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("目标路径: $currentPath",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            2 -> {
                // 传输队列
                TransferQueueView(transferManager)
            }
        }
    }
}

@Composable
private fun RemoteFileRow(entry: RemoteFileEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (entry.isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDir) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium)
            if (!entry.isDir) {
                Text(Util.formatBytes(entry.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!entry.isDir) {
            Text("↓", color = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider()
}

@Composable
private fun TransferQueueView(transferManager: FileTransferManager) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // 传输统计
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("↑ ${Util.formatBytes(transferManager.totalUploaded)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Text("↓ ${Util.formatBytes(transferManager.totalDownloaded)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary)
            Text("${transferManager.activeCount} ${stringResource(R.string.control_files_transferring)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (transferManager.tasks.isEmpty()) {
            Text(stringResource(R.string.control_files_empty),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(transferManager.tasks) { task ->
                    TransferTaskRow(task,
                        onPause = { transferManager.pauseTask(task.id) },
                        onResume = { transferManager.resumeTask(task.id) },
                        onCancel = { transferManager.cancelTask(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferTaskRow(
    task: TransferTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (task.direction == TransferDirection.UPLOAD) "↑" else "↓",
                    color = if (task.direction == TransferDirection.UPLOAD)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(task.name, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                Text(task.status.label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${Util.formatBytes(task.size)} · ${task.remotePath}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()) {
                when (task.status) {
                    TransferStatus.TRANSFERRING -> {
                        OutlinedButton(onClick = onPause) {
                            Text(stringResource(R.string.control_files_pause_btn))
                        }
                    }
                    TransferStatus.PAUSED -> {
                        OutlinedButton(onClick = onResume) {
                            Text(stringResource(R.string.control_files_resume_btn))
                        }
                    }
                    else -> {}
                }
                if (task.status != TransferStatus.DONE && task.status != TransferStatus.TRANSFERRING) {
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.control_files_cancel_btn))
                    }
                }
            }
        }
    }
}
