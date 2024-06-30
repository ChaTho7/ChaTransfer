package com.chatho.chatransfer.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*
import java.io.Serializable

interface FlaskApiService {
    @GET("/status")
    fun getServerStatus(): Call<ResponseBody>

    @GET("/get_file_info_list")
    fun getFileInfoList(): Call<List<GetFileInfoListResponse>>

    @GET("/download_file/{filename}")
    @Streaming
    @Headers("Accept: application/octet-stream")
    suspend fun downloadFile(
        @Path("filename") filename: String,
        @Header("Range") range: String
    ): ResponseBody

    @Multipart
    @POST("/upload_file")
    fun uploadFile(
        @Part chunkData: MultipartBody.Part,
        @Part("chunkId") chunkId: RequestBody,
        @Part("totalChunks") totalChunks: RequestBody
    ): Call<UploadFilesResponse>
}

data class ServerStatusResponse(val status: String)

data class GetFileInfoListResponse(
    val filename: String, @SerializedName("file_size") val fileSize: Int
) : Serializable

data class UploadFilesResponse(
    val data: UploadFilesResponseData, val message: String, val success: Boolean
)

data class UploadFilesResponseData(val type: String, val name: String)

enum class API_METHOD {UPLOAD, DOWNLOAD}
