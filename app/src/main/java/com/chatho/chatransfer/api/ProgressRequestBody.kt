package com.chatho.chatransfer.api

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException

class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val progressListener: ProgressListener
) : RequestBody() {

    interface ProgressListener {
        fun onProgress(currentBytes: Long, totalBytes: Long, done: Boolean, fileName: String)
    }

    override fun contentType(): MediaType? {
        return contentType
    }

    @Throws(IOException::class)
    override fun contentLength(): Long {
        return file.length()
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        file.inputStream().use { inputStream ->
            val totalBytes = file.length()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead: Long = 0
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                sink.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                progressListener.onProgress(totalBytesRead, totalBytes, false, file.name)
            }
            progressListener.onProgress(totalBytesRead, totalBytes, true, file.name)
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 2048
    }
}