package com.ragclient.android.data.api

import com.ragclient.android.data.api.dto.LoginRequestDto
import com.ragclient.android.data.api.dto.LoginResponseDto
import com.ragclient.android.data.api.dto.RefreshTokenRequestDto
import com.ragclient.android.data.api.dto.RegisterRequestDto
import com.ragclient.android.data.api.dto.RegisterResponseDto
import com.ragclient.android.data.api.dto.UserInfoDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): RegisterResponseDto

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequestDto): LoginResponseDto

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body request: RefreshTokenRequestDto): Unit

    @GET("api/v1/auth/me")
    suspend fun me(): UserInfoDto
}
