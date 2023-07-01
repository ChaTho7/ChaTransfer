package com.chatho.chatransfer.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface FlaskApiService {
    @GET("/status")
    fun getServerStatus(): Call<ResponseBody>

    @GET("/get_filenames")
    fun getFilenames(): Call<List<String>>

    @GET
    @Streaming
    suspend fun downloadFiles(@Url fileUrl: String): Response<ResponseBody>

    @Multipart
    @POST("/upload_files")
    fun uploadFiles(
        @Part chunkData: MultipartBody.Part,
        @Part("chunkId") chunkId: RequestBody,
        @Part("totalChunks") totalChunks: RequestBody
    ): Call<UploadFilesResponse>
}

data class ServerStatusResponse(val status: String)

data class UploadFilesResponse(
    val data: UploadFilesResponseData, val message: String, val success: Boolean
)

data class UploadFilesResponseData(val type: String, val name: String)
