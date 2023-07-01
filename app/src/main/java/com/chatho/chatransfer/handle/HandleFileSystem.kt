package com.chatho.chatransfer.util

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class HandleFileSystem(
    private val activity: AppCompatActivity,
    private val callback: (List<File>) -> Unit
) {
    var getFilePickerLauncher: ActivityResultLauncher<String>

    init {
        this.getFilePickerLauncher = filePickerLauncher()
    }

    private fun filePickerLauncher(): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris != null) {
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