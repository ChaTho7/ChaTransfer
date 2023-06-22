package com.chatho.chatransfer.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface FlaskApiService {
    @GET("/status")
    fun getStatus(): Call<String>

    @GET("/get_filenames")
    fun getFilenames(): Call<String>

    @GET
    @Streaming
    suspend fun downloadFile(@Url fileUrl: String): Response<ResponseBody>

    @Multipart
    @POST("/upload_files")
    fun uploadFiles(@Part files: List<MultipartBody.Part>): Call<String>
}