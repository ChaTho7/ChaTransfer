package com.chatho.chatransfer.api

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.chatho.chatransfer.BuildConfig
import com.chatho.chatransfer.Constants
import com.chatho.chatransfer.Utils
import com.chatho.chatransfer.handle.HandleFileSystem
import com.chatho.chatransfer.handle.HandleFileSystem.Companion.clearPathDirectory
import com.chatho.chatransfer.handle.HandleNotification
import com.chatho.chatransfer.holder.DownloadFilesProgressHolder
import com.chatho.chatransfer.view.MainActivity
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.net.ProtocolException
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.min

class FlaskAPI(
    private var activity: MainActivity, private val handleNotification: HandleNotification?
) {
    private val domain = "http://192.168.1.7:5050"
    private var totalBytesRead: Long = 0
    private var downloadedChunkStartIndexes: ArrayList<Long> = arrayListOf()
    private var totalUploadedChunkSize = 0

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

    fun getFileInfoList(callback: (List<GetFileInfoListResponse>) -> Unit) {
        val retrofit =
            Retrofit.Builder().baseUrl(domain).addConverterFactory(GsonConverterFactory.create())
                .build()
        val service = retrofit.create(FlaskApiService::class.java)

        val connection = service.getFileInfoList()
        connection.timeout().timeout(TIMEOUT.toLong(), TimeUnit.SECONDS)

        connection.enqueue(object : Callback<List<GetFileInfoListResponse>> {
            override fun onResponse(
                call: Call<List<GetFileInfoListResponse>>,
                response: Response<List<GetFileInfoListResponse>>
            ) {
                if (response.isSuccessful) {
                    val responseBody = response.body() ?: emptyList()

                    activity.runOnUiThread {
                        callback(responseBody)
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

            override fun onFailure(call: Call<List<GetFileInfoListResponse>>, t: Throwable) {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity, "HTTP request failed: ${t.message}", Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    fun downloadFiles(
        fileInfoList: ArrayList<GetFileInfoListResponse>
    ) {
        val filenames = fileInfoList.map { it.filename }
        val fileSizes = fileInfoList.map { it.fileSize }

        val chunkRanges = HandleFileSystem.calculateChunkRanges(fileSizes[0])
        val downloadedChunks = mutableListOf<DownloadedChunk>()

        handleNotification!!.startForeground()
        DownloadFilesProgressHolder.totalFilesCount = filenames.size
        DownloadFilesProgressHolder.totalFilesSize = fileSizes.sumOf { it.toLong() }
        DownloadFilesProgressHolder.startTime = Date().time
        DownloadFilesProgressHolder.currentFileSize = fileSizes[0].toLong()

        Log.i("Download File", "${filenames[0]}'S TOTAL CHUNK SIZE: ${chunkRanges.size}")

        for (i in 0 until chunkRanges.size) {
            chunkDownload(
                filenames[0], chunkRanges[i], 0, i, chunkRanges.size, downloadedChunks
            )
        }
        downloadFileCallback(
            ArrayList(filenames), fileSizes, downloadedChunks, 0
        )

    }

    private fun chunkDownload(
        filename: String,
        rangeHeader: String,
        fileIndex: Int,
        chunkIndex: Int,
        chunkSize: Int,
        downloadedChunks: MutableList<DownloadedChunk>
    ) {
        val encodedFilename = Uri.encode(filename)
        val startIndex = rangeHeader.substringAfter("bytes=").substringBefore("-").toLong()
        downloadedChunkStartIndexes.add(startIndex)
        val endIndex = rangeHeader.substringAfter("-").toLong()

        val tempFile = File.createTempFile("chunk_${chunkIndex}_", null)
        val tempFilePath = tempFile.absolutePath

        Utils.runInCoroutineScope(Dispatchers.IO) {
            try {
                val retrofit = Retrofit.Builder().baseUrl(domain).build()
                val apiService = retrofit.create(FlaskApiService::class.java)

                val responseBody = apiService.downloadFile(encodedFilename, rangeHeader)
                Log.i("Download File", "${chunkIndex + 1}. ($startIndex) CHUNK HAS BEEN CAME")

                val buffer = ByteArray(responseBody.contentLength().toInt())
                var bytesRead: Int

                val updateInterval =
                    DownloadFilesProgressHolder.currentFileSize / (if (DownloadFilesProgressHolder.currentFileSize > 50 * 1024 * 1024) 1000 else 100)   // Update every 0.1% if file size is bigger than 50 MB else 1% progress
                var bytesReadSinceLastUpdate = 0

                val outputStream = FileOutputStream(File(tempFilePath))
                val inputStream = responseBody.byteStream()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead.toLong()
                    bytesReadSinceLastUpdate += bytesRead

                    if (bytesReadSinceLastUpdate >= updateInterval) {
                        bytesReadSinceLastUpdate = 0

                        val progress =
                            (totalBytesRead.toDouble() / DownloadFilesProgressHolder.currentFileSize * 100).toInt()
                        handleNotification!!.updateForeground(
                            progress, filename, fileIndex
                        )

                        activity.runOnUiThread {
                            activity.downloadFilesProgressCallback(
                                filename, progress
                            )
                        }
                    }
                }

                Log.i("Download File", "$startIndex CHUNK ADDED TO LIST")
                downloadedChunks.add(DownloadedChunk(startIndex, endIndex, tempFilePath))

                responseBody.close()
            } catch (e: Exception) {
                if (e is ProtocolException) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity, "Protocol error.", Toast.LENGTH_SHORT
                        ).show()
                    }

                    downloadedChunks.add(DownloadedChunk(startIndex, endIndex, tempFilePath))
                    handleNotification!!.updateForeground(
                        ((downloadedChunks.size.toDouble() / chunkSize) * 100).toInt(),
                        filename,
                        fileIndex
                    )
                } else {
                    activity.runOnUiThread {
                        Log.e(
                            "Download File",
                            e.message
                                ?: "Error has been occured which has no message, while downloading file..."
                        )
                        Toast.makeText(
                            activity, "Download failed: ${e.message}", Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun downloadFileCallback(
        filenames: ArrayList<String>,
        fileSizes: List<Int>,
        downloadedChunks: MutableList<DownloadedChunk>,
        downloadedFileIndex: Int,
    ) {
        Log.i("Download File", "CALLBACK HAS BEEN CALLED")
        Utils.runInCoroutineScope(Dispatchers.IO) {
            while (true) {
                if (downloadedChunks.size > 0) {
                    var fileSavePath =
                        "${DownloadFilesProgressHolder.saveFolderPath}/${filenames[downloadedFileIndex]}"
                    var file = File(fileSavePath)

                    var index = 1
                    val filenameWithoutExtension = file.nameWithoutExtension
                    val fileExtension = file.extension
                    while (file.exists()) {
                        fileSavePath =
                            "${DownloadFilesProgressHolder.saveFolderPath}/${filenameWithoutExtension}_${index}.${fileExtension}"
                        file = File(fileSavePath)
                        index += 1
                    }

                    HandleFileSystem.combineDownloadedFileChunks(
                        downloadedChunks, fileSavePath, downloadedChunkStartIndexes
                    )

                    Log.i("Download File", "COMBINE FINISHED")
                    activity.runOnUiThread {
                        activity.downloadFilesCallback(filenames[downloadedFileIndex])
                    }

                    clearPathDirectory(activity.cacheDir)
                    totalBytesRead = 0
                    downloadedChunkStartIndexes = arrayListOf()

                    break
                } else {
                    Log.i("Download File", "COMBINING RETRYING")
                    Thread.sleep(THREAD_SLEEP_TIME)
                }
            }


            if (downloadedFileIndex == filenames.size - 1) {
                DownloadFilesProgressHolder.endTime = Date().time
                handleNotification!!.finishForeground(filenames.map { filename -> "${Constants.downloadNotificationEmoji} $filename" })
            } else {
                val newChunkRanges =
                    HandleFileSystem.calculateChunkRanges(fileSizes[downloadedFileIndex + 1])
                val newDownloadedChunks = mutableListOf<DownloadedChunk>()

                DownloadFilesProgressHolder.currentFileSize =
                    fileSizes[downloadedFileIndex + 1].toLong()
                Log.i(
                    "Download File",
                    "${filenames[downloadedFileIndex + 1]}'S TOTAL CHUNK SIZE: ${newChunkRanges.size}"
                )

                for (i in 0 until newChunkRanges.size) {
                    chunkDownload(
                        filenames[downloadedFileIndex + 1],
                        newChunkRanges[i],
                        downloadedFileIndex + 1,
                        i,
                        newChunkRanges.size,
                        newDownloadedChunks
                    )
                }

                downloadFileCallback(
                    filenames, fileSizes, newDownloadedChunks, downloadedFileIndex + 1
                )
            }
        }
    }

    fun uploadFiles(files: List<File>) {
        UploadFilesProgressRequestBody.totalFilesCount = files.size
        UploadFilesProgressRequestBody.totalFilesSize = files.sumOf { it.length() }
        UploadFilesProgressRequestBody.totalBytesRead = 0L
        UploadFilesProgressRequestBody.toUploadBytesTotal = files[0].length()
        UploadFilesProgressRequestBody.startTime = Date().time

        handleSingleFileUpload(files[0], 0, uploadFileCallback(files))
    }

    private fun handleSingleFileUpload(
        file: File, index: Int, callback: (Int) -> Unit
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
                            progress, fileName, index
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

        HandleFileSystem.logMemoryUsage(activity)
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val totalChunks = HandleFileSystem.calculateTotalChunks(file.length())
            val totalBatchs = ceil(totalChunks / HandleFileSystem.BATCH_SIZE.toDouble()).toInt()

            for (batch in 0 until totalBatchs) {
                val batchCompletion = CompletableDeferred<Unit>()

                for (chunkId in batch * HandleFileSystem.BATCH_SIZE until min(
                    (batch + 1) * HandleFileSystem.BATCH_SIZE, totalChunks
                )) {
                    val chunkData = HandleFileSystem.getFileChunk(file, chunkId)
                    chunkUpload(
                        chunkId,
                        totalChunks,
                        uploadProgressListener,
                        file.name,
                        chunkData,
                        fileUploadService,
                        callback,
                        index,
                        batchCompletion
                    )
                }

                HandleFileSystem.logMemoryUsage(activity)
                batchCompletion.await()
            }
        }
    }

    private fun chunkUpload(
        chunkId: Int,
        totalChunks: Int,
        uploadProgressListener: UploadFilesProgressRequestBody.ProgressListener,
        filename: String,
        chunkData: ByteArray,
        fileUploadService: FlaskApiService,
        callback: (Int) -> Unit,
        index: Int,
        batchCompletion: CompletableDeferred<Unit>? = null
    ) {
        val chunkIdRequestBody = chunkId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val totalChunksRequestBody =
            totalChunks.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val chunkRequestBody = UploadFilesProgressRequestBody(
            chunkData, "multipart/form-data".toMediaTypeOrNull(), uploadProgressListener, filename
        )

        val chunk = MultipartBody.Part.createFormData(
            "chunk", filename, chunkRequestBody
        )
        val call = fileUploadService.uploadFile(
            chunk, chunkIdRequestBody, totalChunksRequestBody
        )

        call.enqueue(object : Callback<UploadFilesResponse> {
            override fun onResponse(
                call: Call<UploadFilesResponse>, response: Response<UploadFilesResponse>
            ) {
                if (response.isSuccessful) {
                    val responseBody = response.body()!!
                    if (responseBody.success) {
                        totalUploadedChunkSize += 1
                        if (totalUploadedChunkSize % HandleFileSystem.BATCH_SIZE == 0 || totalUploadedChunkSize == totalChunks) batchCompletion?.complete(
                            Unit
                        )
                        if (totalUploadedChunkSize == totalChunks) {
                            totalUploadedChunkSize = 0
                            callback(index)
                        }
                    } else {
                        Log.v("Upload File", "Process has failed:")
                        Log.v("Upload File", responseBody.message)

                        chunkUpload(
                            chunkId,
                            totalChunks,
                            uploadProgressListener,
                            filename,
                            chunkData,
                            fileUploadService,
                            callback,
                            index,
                            batchCompletion
                        )
                    }
                } else {
                    Log.v("Upload File", "Http request has failed:")
                    Log.v("Upload File", response.errorBody().toString())

                    chunkUpload(
                        chunkId,
                        totalChunks,
                        uploadProgressListener,
                        filename,
                        chunkData,
                        fileUploadService,
                        callback,
                        index,
                        batchCompletion
                    )
                }
            }

            override fun onFailure(call: Call<UploadFilesResponse>, t: Throwable) {
                Log.e("Upload File", "Failure has occurred on $chunkId:")
                Log.e("Upload File", t.message ?: "Failure has no message...")

                if (t.message == "unexpected end of stream" && BuildConfig.BUILD_TYPE == "debug") { // Emulator bug...
                    totalUploadedChunkSize += 1
                    if (totalUploadedChunkSize % HandleFileSystem.BATCH_SIZE == 0 || totalUploadedChunkSize == totalChunks) batchCompletion?.complete(
                        Unit
                    )
                    if (totalUploadedChunkSize == totalChunks) {
                        totalUploadedChunkSize = 0
                        callback(index)
                    }
                } else {
                    chunkUpload(
                        chunkId,
                        totalChunks,
                        uploadProgressListener,
                        filename,
                        chunkData,
                        fileUploadService,
                        callback,
                        index,
                        batchCompletion
                    )
                }
            }
        })
    }

    private fun uploadFileCallback(files: List<File>): (Int) -> Unit {
        return { uploadedIndex ->
            if (uploadedIndex == files.size - 1) {
                activity.runOnUiThread {
                    activity.uploadFilesProgressCallback(true, null, null)
                }
                UploadFilesProgressRequestBody.endTime = Date().time
                handleNotification!!.finishForeground(files.map { file -> "${Constants.uploadNotificationEmoji} ${file.name}" })
                clearPathDirectory(activity.cacheDir)
            } else {
                UploadFilesProgressRequestBody.totalBytesRead = 0L
                UploadFilesProgressRequestBody.toUploadBytesTotal =
                    files[uploadedIndex + 1].length()

                handleSingleFileUpload(
                    files[uploadedIndex + 1], uploadedIndex + 1, uploadFileCallback(files)
                )
            }
        }
    }

    data class DownloadedChunk(
        val startIndex: Long, val endIndex: Long, val tempFilePath: String
    )

    companion object {
        private const val TIMEOUT = 5
        const val THREAD_SLEEP_TIME = 250L
    }
}
