package com.example.capture

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

interface ImageUploadApi {

    @Multipart
    @POST
    suspend fun uploadImage(
        @Url url: String,
        @Header("X-API-KEY") apiKey: String,
        @Header("hiveId") hiveId: String,
        @Part file: MultipartBody.Part
    ): Response<Unit>
}