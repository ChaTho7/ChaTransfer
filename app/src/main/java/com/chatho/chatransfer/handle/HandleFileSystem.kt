package com.chatho.chatransfer.handle

import android.content.ContentResolver
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
                    uploadFileList = fileList
                    callback(fileList)
                }
            }
        }
    }

    private fun getDataColumn(
        contentResolver: ContentResolver, originalFileName: String
    ): String? {
//        val folderFile = File(Environment.getExternalStorageDirectory().absolutePath)
//        folderFile.listFiles()?.forEach {
//            scanFiles(it)
//        }

//        contentResolver.delete(uri, null, null)


        var cursor: Cursor? = null
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
        val includedArgs = arrayOf(originalFileName)
        val selectionArgs = includedArgs.map { arg -> "%$arg%" }.toTypedArray()

        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val pathColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                return cursor.getString(pathColumn)
            }
        } finally {
            cursor?.close()
        }
        return null
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

        fun calculateTotalChunks(fileSize: Long): Int {
            return ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        }

        fun calculateChunkRanges(fileSizes: List<Int>, fileIndex: Int): MutableList<String> {
            val numChunks = fileSizes[fileIndex] / CHUNK_SIZE
            val remainder = fileSizes[fileIndex] % CHUNK_SIZE
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