package com.linkall.android.ui.screen.controller

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.util.SafHelper
import com.linkall.android.util.Util

/**
 * 文件管理：调用 SAF 双向传输，进度通知，支持外部 SD 卡
 */
@Composable
fun FileTransferScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val transfers = remember { mutableStateListOf<TransferItem>() }

    // 上传：选择文件
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            val name = SafHelper.queryFileName(context, uri) ?: "unknown"
            val size = SafHelper.queryFileSize(context, uri)
            transfers.add(TransferItem(name, size, 0f, TransferStatus.PENDING, uri))
        }
    }

    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.control_files), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { uploadLauncher.launch(arrayOf("*/*")) }, Modifier.weight(1f)) {
                Text(stringResource(R.string.control_files_upload))
            }
            Button(onClick = { /* TODO: 下载需要远端路径，由会话提供 */ }, Modifier.weight(1f)) {
                Text(stringResource(R.string.control_files_download))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.control_files_queue), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        if (transfers.isEmpty()) {
            Text(stringResource(R.string.control_files_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(transfers) { item ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                            Text(Util.formatBytes(item.size), style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                            Text(item.status.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

data class TransferItem(
    val name: String,
    val size: Long,
    val progress: Float,
    val status: TransferStatus,
    val uri: Uri? = null
)

enum class TransferStatus(val label: String) {
    PENDING("等待中"), TRANSFERRING("传输中"), DONE("已完成"), FAILED("失败"), PAUSED("已暂停")
}
