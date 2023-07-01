package com.chatho.chatransfer.api

import android.widget.Toast
import com.chatho.chatransfer.Constants
import com.chatho.chatransfer.handle.HandleFileSystem
import com.chatho.chatransfer.handle.HandleFileSystem.Companion.clearPathDirectory
import com.chatho.chatransfer.handle.HandleNotification
import com.chatho.chatransfer.holder.DownloadFilesProgressHolder
import com.chatho.chatransfer.holder.MainActivityHolder
import com.chatho.chatransfer.view.MainActivity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.net.ProtocolException
import java.util.concurrent.TimeUnit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory

class FlaskAPI(private val handleNotification: HandleNotification?) {
    private val domain = "http://192.168.1.7:5050"
    private var activity = MainActivityHolder.activity as MainActivity

    fun getServerStatus(callback: (Boolean) -> Unit) {
        val retrofit =
            Retrofit.Builder().baseUrl(domain).addConverterFactory(GsonConverterFactory.create())
                .build()
        val service = retrofit.create(FlaskApiService::class.java)

        val connection = service.getServerStatus()
        connection.timeout().timeout(TIMEOUT.toLong(), TimeUnit.SECONDS)

        connection.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>, response: Response<ResponseBody>
            ) {
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    val responseBodyString = responseBody?.string()
                    val jsonResponse = Gson().fromJson(
                        responseBodyString, ServerStatusResponse::class.java
                    )

                    callback(jsonResponse.status == "ONLINE")
                } else {
                    Toast.makeText(
                        activity,
                        "HTTP request failed with status code: ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                    callback(false)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(
                    activity, "HTTP request failed: ${t.message}", Toast.LENGTH_LONG
                ).show()
                callback(false)
            }
        })
    }

    fun getFiles(callback: (ArrayList<String>) -> Unit) {
        val retrofit =
            Retrofit.Builder().baseUrl(domain).addConverterFactory(GsonConverterFactory.create())
                .build()
        val service = retrofit.create(FlaskApiService::class.java)

        val connection = service.getFilenames()
        connection.timeout().timeout(TIMEOUT.toLong(), TimeUnit.SECONDS)

        connection.enqueue(object : Callback<List<String>> {
            override fun onResponse(
                call: Call<List<String>>, response: Response<List<String>>
            ) {
                if (response.isSuccessful) {
                    val responseBody = response.body() ?: emptyList()
                    val filenames: ArrayList<String> = ArrayList(responseBody)

                    activity.runOnUiThread {
                        callback(filenames)
                    }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "HTTP request failed with status code: ${response.code()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onFailure(call: Call<List<String>>, t: Throwable) {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity, "HTTP request failed: ${t.message}", Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    fun downloadFiles(
        fileNames: ArrayList<String>
    ) {
        val savePath = "${DownloadFilesProgressHolder.saveFolderPath}/${fileNames[0]}"
        DownloadFilesProgressHolder.totalFilesSize = fileNames.size
        singleFileDownload(fileNames[0], savePath, 0, downloadFilesCallback(fileNames))
    }

    private fun singleFileDownload(
        fileName: String,
        savePath: String,
        index: Int,
        callback: (Int) -> Unit,
    ) {
        val fileUrl = "/download_file?filename=$fileName"

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val retrofit = Retrofit.Builder().baseUrl(domain).build()
                val apiService = retrofit.create(FlaskApiService::class.java)

                val response = apiService.downloadFiles(fileUrl)

                if (response.isSuccessful) {
                    val responseBody: ResponseBody? = response.body()

                    if (responseBody != null) {
                        val outputStream = FileOutputStream(File(savePath))
                        val inputStream = responseBody.byteStream()

                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        var totalBytesRead: Long = 0
                        val totalBytes: Long = responseBody.contentLength()

                        val updateInterval =
                            totalBytes / (if (totalBytes > 1024.0 * 1024.0 * 50.0) 1000 else 100) // Update every 0.1% if file size is bigger than 50 MB else 1% progress
                        var bytesReadSinceLastUpdate = 0

                        handleNotification!!.startForeground()

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead.toLong()
                            bytesReadSinceLastUpdate += bytesRead

                            if (bytesReadSinceLastUpdate >= updateInterval) {
                                bytesReadSinceLastUpdate = 0

                                val progress =
                                    (totalBytesRead.toDouble() / totalBytes * 100).toInt()
                                handleNotification.updateForeground(
                                    "downloadFiles", progress, fileName, index
                                )

                                activity.runOnUiThread {
                                    activity.downloadFilesProgressCallback(
                                        fileName, totalBytesRead, totalBytes
                                    )
                                }
                            }
                        }
                        outputStream.close()
                        inputStream.close()
                    }
                } else {
                    val errorBody: ResponseBody? = response.errorBody()
                    if (errorBody != null) {
                        activity.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "Error has occured in ${fileName}: $errorBody",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                callback(index)
            } catch (e: Exception) {
                if (e is ProtocolException) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity, "Protocol error.", Toast.LENGTH_SHORT
                        ).show()
                    }

                    callback(index)
                } else {
                    activity.runOnUiThread {
                        println(e.message)
                        Toast.makeText(
                            activity, "Download failed: ${e.message}", Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun downloadFilesCallback(filenames: ArrayList<String>): (Int) -> Unit {
        return { downloadedIndex ->
            activity.runOnUiThread {
                activity.downloadFilesCallback(filenames[downloadedIndex])
            }

            if (downloadedIndex == filenames.size - 1) {
                handleNotification!!.finishForeground("downloadFiles",
                    filenames.map { filename -> "${Constants.downloadNotificationEmoji} $filename" })
                clearPathDirectory(activity.cacheDir)
            } else {
                val savePath =
                    "${DownloadFilesProgressHolder.saveFolderPath}/${filenames[downloadedIndex + 1]}"

                singleFileDownload(
                    filenames[downloadedIndex + 1],
                    savePath,
                    downloadedIndex + 1,
                    downloadFilesCallback(filenames)
                )
            }
        }
    }

    fun uploadFiles(files: List<File>) {
        UploadFilesProgressRequestBody.totalFilesSize = files.size
        UploadFilesProgressRequestBody.totalBytesRead = 0L
        UploadFilesProgressRequestBody.toUploadBytesTotal = files[0].length()

        val fileSizeInBytes = files[0].length()
        val fileSizeInGB = fileSizeInBytes / (1024.0 * 1024.0 * 1024.0)
        val chunkMethod = if (fileSizeInGB > 1.0) {
            "splitFile"
        } else {
            "individual"
        }

        handleSingleFileUpload(files[0], 0, uploadFilesCallback(files), chunkMethod)
    }

    private fun handleSingleFileUpload(
        file: File, index: Int, callback: (Int) -> Unit, chunkMethod: String
    ) {
        val retrofit =
            Retrofit.Builder().baseUrl(domain).addConverterFactory(GsonConverterFactory.create())
                .build()

        val uploadProgressListener: UploadFilesProgressRequestBody.ProgressListener =
            object : UploadFilesProgressRequestBody.ProgressListener {
                private var oldProgress = -1

                override fun onProgress(
                    totalBytesRead: Long, toUploadBytesTotal: Long, done: Boolean, fileName: String
                ) {
                    val progress =
                        (totalBytesRead.toFloat() / toUploadBytesTotal.toFloat() * 100).toInt()

                    if (progress != oldProgress) {
                        handleNotification!!.updateForeground(
                            "uploadFiles", progress, fileName, index
                        )
                        activity.runOnUiThread {
                            activity.uploadFilesProgressCallback(false, fileName, progress)
                        }

                        oldProgress = progress
                    }
                }
            }

        handleNotification!!.startForeground()

        val fileUploadService = retrofit.create(FlaskApiService::class.java)
        val defaultChunkSize = 2048 * 1024

        when (chunkMethod) {
            "splitFile" -> {
                val chunks = HandleFileSystem.splitFileIntoChunks(file, defaultChunkSize)

                for (chunkId in chunks.indices) {
                    singleFileUpload(
                        chunkId,
                        chunks.size,
                        uploadProgressListener,
                        file.name,
                        chunks[chunkId],
                        fileUploadService,
                        callback,
                        index
                    )
                }
            }

            "individual" -> {

                val totalChunks =
                    HandleFileSystem.calculateTotalChunks(file.length(), defaultChunkSize)
                for (chunkId in 0 until totalChunks) {
                    val chunkData = HandleFileSystem.getFileChunk(file, chunkId, defaultChunkSize)

                    singleFileUpload(
                        chunkId,
                        totalChunks,
                        uploadProgressListener,
                        file.name,
                        chunkData,
                        fileUploadService,
                        callback,
                        index
                    )
                }
            }
        }
    }

    private fun singleFileUpload(
        chunkId: Int,
        totalChunks: Int,
        uploadProgressListener: UploadFilesProgressRequestBody.ProgressListener,
        filename: String,
        chunkData: ByteArray,
        fileUploadService: FlaskApiService,
        callback: (Int) -> Unit,
        index: Int
    ) {
        val chunkIdRequestBody =
            RequestBody.create(MediaType.parse("text/plain"), chunkId.toString())
        val totalChunksRequestBody =
            RequestBody.create(MediaType.parse("text/plain"), totalChunks.toString())
        val chunkRequestBody = UploadFilesProgressRequestBody(
            chunkData, MediaType.parse("multipart/form-data"), uploadProgressListener, filename
        )

        val chunk = MultipartBody.Part.createFormData(
            "chunk", filename, chunkRequestBody
        )
        val call = fileUploadService.uploadFiles(
            chunk, chunkIdRequestBody, totalChunksRequestBody
        )

        call.enqueue(object : Callback<UploadFilesResponse> {
            override fun onResponse(
                call: Call<UploadFilesResponse>, response: Response<UploadFilesResponse>
            ) {
                if (response.isSuccessful) {
                    val responseBody = response.body()!!
                    if (responseBody.success) {
                        if (responseBody.data.type == "file") {
                            println(responseBody.message)
                            callback(index)
                        } else {
                            println(responseBody.message)
                        }
                    } else {
                        println("Process has failed:")
                        println(responseBody.message)
                    }
                } else {
                    println("Http request has failed:")
                    println(response.errorBody().toString())
                }
            }

            override fun onFailure(call: Call<UploadFilesResponse>, t: Throwable) {
                println("Failure has occurred:")
                println(t.message)
            }
        })
    }

    private fun uploadFilesCallback(files: List<File>): (Int) -> Unit {
        return { uploadedIndex ->

            if (uploadedIndex == files.size - 1) {
                activity.runOnUiThread {
                    activity.uploadFilesProgressCallback(true, null, null)
                }
                handleNotification!!.finishForeground("uploadFiles",
                    files.map { file -> "${Constants.uploadNotificationEmoji} ${file.name}" })
                clearPathDirectory(activity.cacheDir)
            } else {
                val fileSizeInBytes = files[uploadedIndex + 1].length()
                val fileSizeInGB = fileSizeInBytes / (1024.0 * 1024.0 * 1024.0)
                val chunkMethod = if (fileSizeInGB > 1.0) {
                    "splitFile"
                } else {
                    "individual"
                }

                UploadFilesProgressRequestBody.totalBytesRead = 0L
                UploadFilesProgressRequestBody.toUploadBytesTotal =
                    files[uploadedIndex + 1].length()

                handleSingleFileUpload(
                    files[uploadedIndex + 1],
                    uploadedIndex + 1,
                    uploadFilesCallback(files),
                    chunkMethod
                )
            }
        }
    }

    companion object {
        private const val TIMEOUT = 5
    }
}
