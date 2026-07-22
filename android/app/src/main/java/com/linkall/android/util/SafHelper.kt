package com.linkall.android.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * SAF（Storage Access Framework）文件传输工具
 * 支持双向传输、断点续传、外部 SD 卡读写
 */
object SafHelper {

    /**
     * 从 SAF Uri 读取文件名
     */
    fun queryFileName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return uri.lastPathSegment
    }

    /**
     * 从 SAF Uri 读取文件大小
     */
    fun queryFileSize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) return cursor.getLong(idx)
        }
        return 0L
    }

    /**
     * 以流形式打开 SAF 文件用于读取
     */
    fun openInputStream(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("无法打开输入流: $uri")

    /**
     * 以流形式打开 SAF 文件用于写入
     */
    fun openOutputStream(context: Context, uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri, "wt")
            ?: throw java.io.IOException("无法打开输出流: $uri")

    /**
     * 分块读取文件并回调
     * @param chunkSize 每块字节数（默认 256KB，协议上限）
     */
    fun readInChunks(
        context: Context, uri: Uri, chunkSize: Int = 262144,
        onChunk: (chunkId: Int, offset: Long, data: ByteArray) -> Boolean
    ): Long {
        var total = 0L
        var chunkId = 0
        val buffer = ByteArray(chunkSize)
        openInputStream(context, uri).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                val chunk = if (read == chunkSize) buffer else buffer.copyOf(read)
                if (!onChunk(chunkId, total, chunk)) break
                total += read
                chunkId++
            }
        }
        return total
    }

    /**
     * 计算文件 SHA-256（Hex）
     */
    fun computeSha256(context: Context, uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        openInputStream(context, uri).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 断点续传：从指定 offset 开始读取
     */
    fun readFromOffset(
        context: Context, uri: Uri, offset: Long,
        chunkSize: Int = 262144,
        onChunk: (chunkId: Int, offset: Long, data: ByteArray) -> Boolean
    ): Long {
        var total = 0L
        var chunkId = 0
        val buffer = ByteArray(chunkSize)
        openInputStream(context, uri).use { input ->
            input.skip(offset)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                val chunk = if (read == chunkSize) buffer else buffer.copyOf(read)
                if (!onChunk(chunkId, offset + total, chunk)) break
                total += read
                chunkId++
            }
        }
        return total
    }
}
