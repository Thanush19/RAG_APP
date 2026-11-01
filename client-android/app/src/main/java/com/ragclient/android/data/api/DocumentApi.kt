package com.ragclient.android.data.api

import com.ragclient.android.data.api.dto.DocumentResponseDto
import okhttp3.MultipartBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface DocumentApi {

    @GET("api/v1/documents")
    suspend fun list(): List<DocumentResponseDto>

    @GET("api/v1/documents/{documentId}")
    suspend fun get(@Path("documentId") documentId: String): DocumentResponseDto

    @Multipart
    @POST("api/v1/documents")
    suspend fun upload(@Part file: MultipartBody.Part): DocumentResponseDto

    @POST("api/v1/documents/{documentId}/reprocess")
    suspend fun reprocess(@Path("documentId") documentId: String): DocumentResponseDto

    @DELETE("api/v1/documents/{documentId}")
    suspend fun delete(@Path("documentId") documentId: String): Unit

    /** Raw PDF bytes for the in-app viewer (PdfRenderer consumes these). */
    @GET("api/v1/documents/{documentId}/bytes")
    suspend fun bytes(@Path("documentId") documentId: String): okhttp3.ResponseBody
}
