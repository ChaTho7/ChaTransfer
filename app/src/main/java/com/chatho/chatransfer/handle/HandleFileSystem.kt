package com.chatho.chatransfer.handle

import android.app.ActivityManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.chatho.chatransfer.api.FlaskAPI
import com.chatho.chatransfer.view.MainActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class HandleFileSystem(
    private var activity: MainActivity, private val callback: (List<File>) -> Unit
) {
    var getFilePickerLauncher: ActivityResultLauncher<String>

    init {
        this.getFilePickerLauncher = filePickerLauncher()
    }

    private fun filePickerLauncher(): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (!isNullOrDefault(uris)) {
                val fileList = uris.mapNotNull { uriString ->
                    val uri = Uri.parse(uriString.toString())
//                    val contentResolver: ContentResolver = activity.contentResolver
//
//                    val originalFileName = getOriginalFileName(contentResolver, uri)
//                    val filePath = getDataColumn(contentResolver, originalFileName)
//
//                    if (!filePath.isNullOrBlank()) {
//                        File(filePath)
//                    } else {
//                        null
//                    }

                    val file = File(
                        Environment.getExternalStorageDirectory().absolutePath + "/${
                            uri.lastPathSegment?.substringAfter(
                                ":"
                            )
                        }"
                    )
                    if (file.isFile) file else null
                }

                if (fileList.isNotEmpty()) {
                    callback(fileList)
                }
            }
        }
    }

    fun getFileFromUri(uri: Uri): File? {
        var filePath: String? = null

        if ("file".equals(uri.scheme, true)) {
            filePath = uri.path
        } else if ("content".equals(uri.scheme, true)) {
            val file = File(
                Environment.getExternalStorageDirectory().absolutePath + "/${
                    uri.lastPathSegment?.substringAfter(
                        ":"
                    )
                }"
            )

            if (file.isFile) {
                filePath = file.path
            } else {
                val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
                val cursor = activity.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex =
                            it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                        filePath = it.getString(columnIndex)
                    }
                }
            }
        }

        return filePath?.let { path -> File(path).takeIf { it.isFile } }
    }

    private fun scanFiles(file: File) {
        if (file.isFile) {
            MediaScannerConnection.scanFile(
                activity, arrayOf(file.absolutePath), null, null
            )
        } else if (file.isDirectory) {
            file.listFiles()?.forEach {
                scanFiles(it)
            }
        }
    }

    companion object {
        private const val CHUNK_SIZE = 2 * 1024 * 1024  // 2 MB
        const val BATCH_SIZE = 50

        fun getDownloadsDirectory(): String? {
            val directory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return directory?.absolutePath
        }

        fun clearPathDirectory(path: File) {
            Log.i("HandleFileSystem", "-----CACHE CLEANING STARTING-----")
            path.absoluteFile.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    clearPathDirectory(file)
                } else {
                    file.delete()
                    Log.d("HandleFileSystem", "File Deleted: ${file.name}")
                }
            }
            Log.i("HandleFileSystem", "-----CACHE CLEANING FINISHED-----")
        }

        fun calculateTotalChunks(fileSize: Long): Int {
            return ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        }

        fun calculateChunkRanges(fileSize: Int): MutableList<String> {
            val numChunks = fileSize / CHUNK_SIZE
            val remainder = fileSize % CHUNK_SIZE
            val chunkRanges = mutableListOf<String>()

            for (i in 0 until numChunks) {
                val startIndex = i * CHUNK_SIZE
                val endIndex = startIndex + CHUNK_SIZE - 1
                chunkRanges.add("bytes=$startIndex-$endIndex")
            }
            if (remainder > 0) {
                val startIndex = numChunks * CHUNK_SIZE
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
                            Log.e(
                                "Download File",
                                "${(sortedDownloadedChunkStartIndex / CHUNK_SIZE) + 1}. ($sortedDownloadedChunkStartIndex) CHUNK COMBINING"
                            )
                            FileInputStream(chunk.tempFilePath).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                            break
                        } else {
                            Log.e(
                                "Download File",
                                "${(sortedDownloadedChunkStartIndex / CHUNK_SIZE) + 1}. ($sortedDownloadedChunkStartIndex) CHUNK WAITING TO COMBINE"
                            )
                            Thread.sleep(FlaskAPI.THREAD_SLEEP_TIME)
                        }
                    }
                }
            }
        }

        fun getFileChunk(file: File, chunkId: Int): ByteArray {
            val startPos = chunkId * CHUNK_SIZE
            val chunkSizeToRead = minOf(CHUNK_SIZE.toLong(), file.length() - startPos).toInt()

            val chunkData = ByteArray(chunkSizeToRead)
            file.inputStream().use { inputStream ->
                inputStream.skip(startPos.toLong())
                try {
                    inputStream.read(chunkData)
                } catch (e: IOException) {
                    println("READ ERROR")
                } finally {
                    inputStream.close()
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

        fun logMemoryUsage(activity: MainActivity) {
            val memoryInfo = ActivityManager.MemoryInfo()
            (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(
                memoryInfo
            )
            val nativeHeapSize = memoryInfo.totalMem
            val nativeHeapFreeSize = memoryInfo.availMem
            val usedMemInBytes = nativeHeapSize - nativeHeapFreeSize
            val usedMemInPercentage = usedMemInBytes * 100 / nativeHeapSize
            Log.d(
                "MemoryLog", "Total: ${
                    Formatter.formatFileSize(
                        activity, nativeHeapSize
                    )
                }" + "\nFree: ${
                    Formatter.formatFileSize(
                        activity, nativeHeapFreeSize
                    )
                }" + "\nUsed: ${
                    Formatter.formatFileSize(
                        activity, usedMemInBytes
                    )
                } ($usedMemInPercentage%)"
            )
        }
    }
}