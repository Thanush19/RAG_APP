package com.ragclient.android.data.api.dto

import com.ragclient.android.domain.model.DocumentStatus
import kotlinx.serialization.Serializable

@Serializable
data class DocumentResponseDto(
    val id: String,
    val fileName: String? = null,
    val fileType: String? = null,
    val fileSize: Long? = null,
    val status: DocumentStatus = DocumentStatus.UPLOADED,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
