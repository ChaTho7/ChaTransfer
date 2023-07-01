package com.chatho.chatransfer.handle

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.chatho.chatransfer.holder.MainActivityHolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class HandleFileSystem(private val callback: (List<File>) -> Unit) {
    private val activity = MainActivityHolder.activity
    var getFilePickerLauncher: ActivityResultLauncher<String>

    init {
        this.getFilePickerLauncher = filePickerLauncher()
    }

    private fun filePickerLauncher(): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (!isNullOrDefault(uris)) {
                val fileList = uris.map { uriString ->
                    val uri = Uri.parse(uriString.toString())
                    val contentResolver: ContentResolver = activity.contentResolver

                    val originalFileName = getOriginalFileName(contentResolver, uri)
                    val fileExtension = getFileExtension(originalFileName)
                    val tempFile = File.createTempFile("temp", fileExtension)
                    val file = File(tempFile.parent, originalFileName)

                    val outputStream = FileOutputStream(file)

                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }

                    file
                }

                uploadFileList = fileList
                callback(fileList)
            }
        }
    }

    companion object {
        var uploadFileList: List<File>? = null

        fun getDownloadsDirectory(): String? {
            val directory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return directory?.absolutePath
        }

        fun clearPathDirectory(path: File) {
            path.absoluteFile.listFiles()?.forEach { file ->
                println(file.name)
                if (file.isDirectory) {
                    clearPathDirectory(file)
                } else {
                    file.delete()
                }
            }
        }

        fun calculateTotalChunks(fileSize: Long, chunkSize: Int): Int {
            return ((fileSize + chunkSize - 1) / chunkSize).toInt()
        }

        fun splitFileIntoChunks(file: File, chunkSize: Int): List<ByteArray> {
            val fileSize = file.length()
            val totalChunks = Math.ceil(fileSize.toDouble() / chunkSize).toInt()

            val chunks = mutableListOf<ByteArray>()

            file.inputStream().use { inputStream ->
                var bytesRead: Int
                var totalBytesRead = 0
                val buffer = ByteArray(chunkSize)

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    chunks.add(buffer.copyOfRange(0, bytesRead))
                    totalBytesRead += bytesRead

                    if (totalBytesRead >= fileSize) {
                        break
                    }
                }
            }

            return chunks
        }

        fun getFileChunk(file: File, chunkId: Int, chunkSize: Int): ByteArray {
            val startPos = chunkId * chunkSize
            val chunkSizeToRead = minOf(chunkSize.toLong(), file.length() - startPos).toInt()

            val chunkData = ByteArray(chunkSizeToRead)
            file.inputStream().use { inputStream ->
                inputStream.skip(startPos.toLong())
                try {
                    inputStream.read(chunkData)
                } catch (e: IOException) {
                    // Handle read error
                }
            }

            return chunkData
        }

        fun isNullOrDefault(value: Any?): Boolean {
            return value == null || value == defaultValue(value)
        }

        private fun defaultValue(value: Any): Any {
            return when (value) {
                is String -> ""
                is Long -> 0L
                is List<*> -> List<Any>(0) {
                    return Any()
                }

                else -> throw IllegalArgumentException("Unknown type: ${value.javaClass}")
            }
        }

        private fun getFileExtension(fileName: String): String {
            val extension = fileName.substringAfterLast('.', "")
            return if (extension.isNotEmpty()) ".$extension" else ""
        }

        private fun getOriginalFileName(contentResolver: ContentResolver, uri: Uri): String {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayName = it.getString(it.getColumnIndexOrThrow("_display_name"))
                    if (displayName != null) {
                        return displayName
                    }
                }
            }
            // If the display name couldn't be retrieved, fall back to using the last segment of the URI as the file name
            return uri.lastPathSegment ?: throw IllegalArgumentException("Invalid URI: $uri")
        }
    }
}