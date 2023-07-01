package com.chatho.chatransfer.api

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

class ProgressRequestBody(
    private val chunk: ByteArray,
    private val contentType: MediaType?,
    private val progressListener: ProgressListener,
    private val fileName: String
) : RequestBody() {
    interface ProgressListener {
        fun onProgress(
            totalBytesRead: Long, toUploadBytesTotal: Long, done: Boolean, fileName: String
        )
    }

    override fun contentType(): MediaType? {
        return contentType
    }

    @Throws(IOException::class)
    override fun contentLength(): Long {
        return chunk.size.toLong()
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        chunk.inputStream().use { inputStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                sink.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                progressListener.onProgress(
                    totalBytesRead, toUploadBytesTotal, false, fileName
                )
            }
            progressListener.onProgress(
                totalBytesRead, toUploadBytesTotal, true, fileName
            )
        }
    }

    companion object {
        var toUploadBytesTotal = 0L
        var totalBytesRead = 0L
        private const val DEFAULT_BUFFER_SIZE = 2048
    }
}