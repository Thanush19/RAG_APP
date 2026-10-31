package com.ragclient.android.data.api.dto

import kotlinx.serialization.Serializable

/** Mirrors backend ApiErrorResponse (code/message drive UI copy). */
@Serializable
data class ApiErrorDto(
    val status: Int = 0,
    val code: String = "",
    val message: String = "",
    val path: String? = null
)
