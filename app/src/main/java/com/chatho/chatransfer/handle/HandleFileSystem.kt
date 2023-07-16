package com.chatho.chatransfer.handle

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.chatho.chatransfer.api.FlaskAPI
import com.chatho.chatransfer.holder.MainActivityHolder
import java.io.File
import java.io.FileInputStream
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
            Log.i("HandleFileSystem", "-----CACHE CLEANING STARTING-----")
            path.absoluteFile.listFiles()?.forEach { file ->
                println(file.name)
                if (file.isDirectory) {
                    clearPathDirectory(file)
                } else {
                    file.delete()
                }
            }
            Log.i("HandleFileSystem", "-----CACHE CLEANING FINISHED-----")
        }

        fun calculateTotalChunks(fileSize: Long, chunkSize: Int): Int {
            return ((fileSize + chunkSize - 1) / chunkSize).toInt()
        }

        fun calculateChunkRanges(fileSizes: List<Int>, fileIndex: Int): MutableList<String> {
            val chunkSize = 1024 * 1024 * 2 // 2 MB
            val numChunks = fileSizes[fileIndex] / chunkSize
            val remainder = fileSizes[fileIndex] % chunkSize
            val chunkRanges = mutableListOf<String>()

            for (i in 0 until numChunks) {
                val startIndex = i * chunkSize
                val endIndex = startIndex + chunkSize - 1
                chunkRanges.add("bytes=$startIndex-$endIndex")
            }
            if (remainder > 0) {
                val startIndex = numChunks * chunkSize
                val endIndex = startIndex + remainder - 1
                chunkRanges.add("bytes=$startIndex-$endIndex")
            }

            return chunkRanges
        }

        fun combineDownloadedFileChunks(
            chunks: List<FlaskAPI.DownloadedChunk>,
            outputPath: String,
            downloadedChunkStartIndexes: ArrayList<Long>
        ) {
            Log.e("Download File", "COMBINE STARTED")
            val sortedDownloadedChunkStartIndexes = downloadedChunkStartIndexes.sortedBy { it }

            FileOutputStream(outputPath).use { outputStream ->
                for (sortedDownloadedChunkStartIndex in sortedDownloadedChunkStartIndexes) {
                    while (true) {
                        val chunk = chunks.find { it.startIndex == sortedDownloadedChunkStartIndex }
                        if (chunk != null) {
                            Log.e("Download File", "$sortedDownloadedChunkStartIndex COMBINING")
                            FileInputStream(chunk.tempFilePath).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                            break
                        } else {
                            Log.e(
                                "Download File",
                                "$sortedDownloadedChunkStartIndex WAITING TO COMBINE"
                            )
                            Thread.sleep(FlaskAPI.THREAD_SLEEP_TIME)
                        }
                    }

                }
            }
        }

        fun splitFileIntoChunks(file: File, chunkSize: Int): List<ByteArray> {
            val fileSize = file.length()

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