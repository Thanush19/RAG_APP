package com.ragclient.android.data.api

import com.ragclient.android.data.api.dto.AskRequestDto
import com.ragclient.android.data.api.dto.AskResponseDto
import com.ragclient.android.data.api.dto.SearchRequestDto
import com.ragclient.android.data.api.dto.SearchResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface RagApi {

    @POST("api/v1/search")
    suspend fun search(@Body request: SearchRequestDto): SearchResponseDto

    @POST("api/v1/rag/ask")
    suspend fun ask(@Body request: AskRequestDto): AskResponseDto
}
