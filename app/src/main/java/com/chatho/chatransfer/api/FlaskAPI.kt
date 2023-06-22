package com.chatho.chatransfer.api

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.reflect.typeOf

class FlaskAPI(private val activity: AppCompatActivity) {
    private val domain = "http://192.168.1.7:5050"

    fun getServerStatus(callback: (Boolean) -> Unit) {
        val retrofit = Retrofit.Builder()
            .baseUrl(domain)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        val service = retrofit.create(FlaskApiService::class.java)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val timeout = 5
                val connection = service.getStatus()
                connection.timeout().timeout(timeout.toLong(), TimeUnit.SECONDS)
                val response = connection.execute()

                if (response.isSuccessful) {
                    val jsonResponse = response.body()?.let { JSONObject(it) }
                    val status = jsonResponse!!.get("status")

                    callback(status == "ONLINE")
                } else {
                    Toast.makeText(
                        activity,
                        "HTTP request failed with status code: ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                    callback(false)
                }
            } catch (e: Exception) {
                callback(false)
            }
        }

    }

    fun getFiles(callback: (ArrayList<String>) -> Unit) {
        val retrofit = Retrofit.Builder()
            .baseUrl(domain)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        val service = retrofit.create(FlaskApiService::class.java)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val timeout = 5
                val connection = service.getFilenames()
                connection.timeout().timeout(timeout.toLong(), TimeUnit.SECONDS)
                val response = connection.execute()

                if (response.isSuccessful) {
                    val jsonResponse = response.body()?.let { JSONArray(it) }

                    val filenames = ArrayList<String>()
                    for (i in 0 until jsonResponse!!.length()) {
                        val element = jsonResponse[i] as String
                        filenames.add(element)
                    }

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
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun downloadFiles(
        fileUrl: String,
        savePath: String,
        progressCallback: (bytesRead: Long, totalBytes: Long) -> Unit,
        callback: () -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl(domain)
                    .build()

                val apiService = retrofit.create(FlaskApiService::class.java)

                val response = apiService.downloadFile(fileUrl)

                if (response.isSuccessful) {
                    val responseBody: ResponseBody? = response.body()

                    if (responseBody != null) {
                        val outputStream = FileOutputStream(File(savePath))

                        val inputStream = responseBody.byteStream()

                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        var totalBytesRead: Long = 0
                        val totalBytes: Long = responseBody.contentLength()

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead.toLong()

                            activity.runOnUiThread {
                                progressCallback(totalBytesRead, totalBytes)
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
                                "Error has occured in errorBody: $errorBody",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                activity.runOnUiThread {
                    callback()
                }
            } catch (e: Exception) {
                if (e is ProtocolException) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Protocol error.",
                            Toast.LENGTH_SHORT
                        ).show()
                        callback()
                    }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Download failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    fun uploadFiles(
        files: List<File>,
        progressListener: ProgressRequestBody.ProgressListener
    ) {
        val retrofit = Retrofit.Builder()
            .baseUrl(domain)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        val service = retrofit.create(FlaskApiService::class.java)

        val parts = mutableListOf<MultipartBody.Part>()

        files.forEach { file ->
            val requestFile =
                ProgressRequestBody(file, MediaType.parse("multipart/form-data"), progressListener)
            val part = MultipartBody.Part.createFormData("files", file.name, requestFile)
            parts.add(part)
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = service.uploadFiles(parts).execute()

                if (response.isSuccessful) {
                    val message = response.body()
                    println(message)
                    clearPathDirectory(activity.cacheDir)
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Upload failed: ${response.code()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Upload failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    }

    private fun clearPathDirectory(path: File) {
        path.absoluteFile.listFiles()?.forEach { file ->
            println(file.name)
            if (file.isDirectory) {
                clearPathDirectory(file)
            } else {
                file.delete()
            }
        }
    }
}