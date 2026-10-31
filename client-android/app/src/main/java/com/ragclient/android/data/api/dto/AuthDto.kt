package com.ragclient.android.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequestDto(
    val name: String,
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String
)

@Serializable
data class UserInfoDto(
    val id: String,
    val name: String? = null,
    val email: String? = null
)

@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val tokenType: String? = null,
    val expiresIn: Long = 0,
    val user: UserInfoDto? = null,
    val refreshToken: String? = null
)

@Serializable
data class RegisterResponseDto(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val createdAt: String? = null
)
