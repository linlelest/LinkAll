package com.linkall.android.ui.files

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.linkall.android.data.model.Envelope
import com.linkall.android.data.model.FileCancelPayload
import com.linkall.android.data.model.FileChunkPayload
import com.linkall.android.data.model.FileCompletePayload
import com.linkall.android.data.model.FileMetaPayload
import com.linkall.android.data.model.FileResumePayload
import com.linkall.android.util.SafHelper
import com.linkall.android.webrtc.ControlMessageBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/** 传输方向 */
enum class TransferDirection(val label: String) {
    UPLOAD("上传"), DOWNLOAD("下载")
}

/** 传输状态 */
enum class TransferStatus(val label: String) {
    PENDING("等待中"),
    TRANSFERRING("传输中"),
    PAUSED("已暂停"),
    DONE("已完成"),
    FAILED("失败")
}

/** 传输任务 */
data class TransferTask(
    val id: String,
    val name: String,
    val size: Long,
    val remotePath: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val progress: Float,
    val transferred: Long = 0,
    val uri: Uri? = null,
    val hash: String = "",
    val error: String? = null
)

/** 远程文件条目（文件管理器浏览） */
data class RemoteFileEntry(
    val name: String,
    val size: Long,
    val isDir: Boolean
)

/**
 * 文件传输管理器：SAF 双向传输、断点续传、传输队列、进度统计
 * 通过 DataChannel 发送文件分片协议消息（file_meta/file_chunk/file_complete/file_resume/file_cancel/file_list_request）
 * 使用 Compose 可观察状态驱动 UI 刷新
 */
class FileTransferManager(
    private val context: Context,
    private val messageBuilder: ControlMessageBuilder,
    private val sender: (Envelope) -> Boolean
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()

    /** 传输任务队列（Compose 可观察） */
    val tasks = mutableStateListOf<TransferTask>()

    /** 累计上传字节 */
    var totalUploaded by mutableStateOf(0L)
        private set

    /** 累计下载字节 */
    var totalDownloaded by mutableStateOf(0L)
        private set

    /** 当前活跃传输数 */
    val activeCount: Int
        get() = tasks.count { it.status == TransferStatus.TRANSFERRING }

    /** 远程目录条目（由 file_list_response 填充，UI 可观察） */
    var remoteEntries by mutableStateOf<List<RemoteFileEntry>>(emptyList())
        private set

    companion object {
        /** 单分片大小上限 256KB（与协议一致） */
        const val CHUNK_SIZE = 262144
    }

    /**
     * 加入上传队列并开始传输
     * @param uri 本地文件 SAF Uri
     * @param name 文件名
     * @param size 文件字节数
     * @param hash SHA-256（Hex）
     * @param remotePath 远端目标路径
     */
    fun enqueueUpload(uri: Uri, name: String, size: Long, hash: String, remotePath: String) {
        val id = UUID.randomUUID().toString()
        val task = TransferTask(
            id = id,
            name = name,
            size = size,
            remotePath = remotePath,
            direction = TransferDirection.UPLOAD,
            status = TransferStatus.PENDING,
            progress = 0f,
            transferred = 0L,
            uri = uri,
            hash = hash
        )
        tasks.add(task)
        startUpload(task, fromOffset = 0L)
    }

    /**
     * 加入下载队列（向远端请求文件，远端将推送 file_chunk）
     * @param name 文件名
     * @param size 文件字节数
     * @param remotePath 远端源路径
     */
    fun enqueueDownload(name: String, size: Long, remotePath: String) {
        val id = UUID.randomUUID().toString()
        val task = TransferTask(
            id = id,
            name = name,
            size = size,
            remotePath = remotePath,
            direction = TransferDirection.DOWNLOAD,
            status = TransferStatus.PENDING,
            progress = 0f,
            transferred = 0L
        )
        tasks.add(task)
        // 发送 file_meta 请求远端推送文件（direction=download）
        val meta = FileMetaPayload(
            transferId = id,
            name = name,
            size = size,
            hash = "",
            direction = "download",
            remotePath = remotePath,
            chunkSize = CHUNK_SIZE
        )
        if (!sender(messageBuilder.fileMeta(meta))) {
            updateTask(id) { it.copy(status = TransferStatus.FAILED, error = "DataChannel 未就绪") }
            return
        }
        updateTask(id) { it.copy(status = TransferStatus.TRANSFERRING) }
    }

    /** 请求远端目录列表（触发 file_list_response） */
    fun requestRemoteList(path: String) {
        sender(messageBuilder.fileListRequest(path))
    }

    /** 暂停任务：取消协程，记录已传输偏移 */
    fun pauseTask(taskId: String) {
        jobs.remove(taskId)?.cancel()
        updateTask(taskId) { it.copy(status = TransferStatus.PAUSED) }
    }

    /** 恢复任务：发送 file_resume 断点续传请求，从已传输偏移继续 */
    fun resumeTask(taskId: String) {
        val task = tasks.find { it.id == taskId } ?: return
        val startChunkId = (task.transferred / CHUNK_SIZE).toInt()
        sender(
            messageBuilder.fileResume(
                FileResumePayload(task.id, task.transferred, startChunkId)
            )
        )
        when (task.direction) {
            TransferDirection.UPLOAD -> startUpload(task, fromOffset = task.transferred)
            TransferDirection.DOWNLOAD -> updateTask(taskId) {
                it.copy(status = TransferStatus.TRANSFERRING)
            }
        }
    }

    /** 取消任务：发送 file_cancel，移除协程 */
    fun cancelTask(taskId: String) {
        jobs.remove(taskId)?.cancel()
        sender(messageBuilder.fileCancel(FileCancelPayload(taskId, "user_cancel")))
        updateTask(taskId) { it.copy(status = TransferStatus.FAILED, error = "已取消") }
    }

    /**
     * 更新下载进度（由调用方在收到 file_chunk 时回调）
     */
    fun updateDownloadProgress(taskId: String, transferred: Long) {
        val task = tasks.find { it.id == taskId } ?: return
        val delta = transferred - task.transferred
        if (delta > 0) totalDownloaded += delta
        val progress = if (task.size > 0)
            (transferred.toFloat() / task.size).coerceIn(0f, 1f) else 0f
        updateTask(taskId) {
            it.copy(
                transferred = transferred,
                progress = progress,
                status = TransferStatus.TRANSFERRING
            )
        }
    }

    /**
     * 标记下载完成（由调用方在收到 file_complete 时回调）
     */
    fun markDownloadComplete(taskId: String, ok: Boolean) {
        updateTask(taskId) {
            it.copy(
                status = if (ok) TransferStatus.DONE else TransferStatus.FAILED,
                progress = if (ok) 1f else it.progress,
                error = if (ok) null else "哈希校验失败"
            )
        }
    }

    /**
     * 更新远程目录条目（由调用方在收到 file_list_response 时回调）
     */
    fun updateRemoteEntries(entries: List<RemoteFileEntry>) {
        remoteEntries = entries
    }

    /**
     * 启动上传：分片读取并依次发送 file_chunk，完成后发送 file_complete
     * @param fromOffset 断点续传起始偏移（0=从头开始）
     */
    private fun startUpload(task: TransferTask, fromOffset: Long) {
        val uri = task.uri ?: run {
            updateTask(task.id) { it.copy(status = TransferStatus.FAILED, error = "缺少文件 Uri") }
            return
        }
        updateTask(task.id) { it.copy(status = TransferStatus.TRANSFERRING) }

        // 首次上传发送 file_meta；断点续传由 resumeTask 已发 file_resume
        if (fromOffset == 0L) {
            val meta = FileMetaPayload(
                transferId = task.id,
                name = task.name,
                size = task.size,
                hash = task.hash,
                direction = "upload",
                remotePath = task.remotePath,
                chunkSize = CHUNK_SIZE
            )
            if (!sender(messageBuilder.fileMeta(meta))) {
                updateTask(task.id) { it.copy(status = TransferStatus.FAILED, error = "DataChannel 未就绪") }
                return
            }
        }

        val job = scope.launch {
            val onChunk: (chunkId: Int, offset: Long, data: ByteArray) -> Boolean = { _, offset, data ->
                val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
                val env = messageBuilder.fileChunk(
                    FileChunkPayload(task.id, (offset / CHUNK_SIZE).toInt(), offset, base64)
                )
                val ok = sender(env)
                if (ok) {
                    val transferred = offset + data.size
                    val progress = if (task.size > 0)
                        (transferred.toFloat() / task.size).coerceIn(0f, 1f) else 0f
                    totalUploaded += data.size.toLong()
                    updateTask(task.id) {
                        it.copy(transferred = transferred, progress = progress)
                    }
                }
                ok
            }
            try {
                if (fromOffset > 0) {
                    SafHelper.readFromOffset(context, uri, fromOffset, CHUNK_SIZE, onChunk)
                } else {
                    SafHelper.readInChunks(context, uri, CHUNK_SIZE, onChunk)
                }
                // 发送传输完成（hash 由接收方校验）
                sender(messageBuilder.fileComplete(FileCompletePayload(task.id, true, task.hash)))
                updateTask(task.id) { it.copy(status = TransferStatus.DONE, progress = 1f) }
            } catch (e: Exception) {
                updateTask(task.id) { it.copy(status = TransferStatus.FAILED, error = e.message) }
            }
        }
        jobs[task.id] = job
    }

    /** 原子更新任务状态 */
    private fun updateTask(taskId: String, block: (TransferTask) -> TransferTask) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0) tasks[idx] = block(tasks[idx])
    }
}
