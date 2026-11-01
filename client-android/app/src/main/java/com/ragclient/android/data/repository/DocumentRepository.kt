package com.ragclient.android.data.repository

import com.ragclient.android.data.api.DocumentApi
import com.ragclient.android.data.api.dto.DocumentResponseDto
import com.ragclient.android.data.local.DocumentCacheDao
import com.ragclient.android.data.local.DocumentCacheEntity
import com.ragclient.android.data.network.ApiErrorMapper
import com.ragclient.android.data.upload.FileProgressRequestBody
import com.ragclient.android.domain.model.DocumentStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import java.io.File

data class LibraryDocument(
    val id: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val status: DocumentStatus,
    val createdAt: String?
)

sealed interface LibraryOutcome {
    data object Ok : LibraryOutcome
    data class Failed(val message: String, val code: String? = null) : LibraryOutcome
}

/**
 * Document library. The server is the source of truth; Room holds the
 * last known list so the drawer paints instantly and offline.
 */
@Singleton
class DocumentRepository @Inject constructor(
    private val documentApi: DocumentApi,
    private val documentCacheDao: DocumentCacheDao,
    private val errorMapper: ApiErrorMapper
) {

    fun observeLibrary(): Flow<List<LibraryDocument>> =
        documentCacheDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun refresh(): LibraryOutcome =
        runCatching { documentApi.list() }
            .fold(
                onSuccess = { documents ->
                    documentCacheDao.upsertAll(documents.map { it.toCacheEntity() })
                    LibraryOutcome.Ok
                },
                onFailure = { failed(it, "Couldn't load your documents.") }
            )

    suspend fun upload(
        file: File,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): LibraryOutcome {
        val mediaType = (if (file.name.endsWith(".pdf", true)) "application/pdf" else "text/plain")
            .toMediaType()
        val part = MultipartBody.Part.createFormData(
            "file",
            file.name,
            FileProgressRequestBody(file, mediaType, onProgress)
        )

        return runCatching { documentApi.upload(part) }
            .fold(
                onSuccess = { created ->
                    documentCacheDao.upsertAll(listOf(created.toCacheEntity()))
                    LibraryOutcome.Ok
                },
                onFailure = { failed(it, "Upload failed.") }
            )
    }

    suspend fun remove(documentId: String): LibraryOutcome =
        runCatching { documentApi.delete(documentId) }
            .fold(
                onSuccess = {
                    documentCacheDao.deleteById(documentId)
                    LibraryOutcome.Ok
                },
                onFailure = { failed(it, "Couldn't delete that document.") }
            )

    suspend fun reprocess(documentId: String): LibraryOutcome =
        runCatching { documentApi.reprocess(documentId) }
            .fold(
                onSuccess = { updated ->
                    documentCacheDao.upsertAll(listOf(updated.toCacheEntity()))
                    LibraryOutcome.Ok
                },
                onFailure = { failed(it, "Couldn't restart processing.") }
            )

    /** PDF bytes for the viewer; the auth interceptor attaches the token. */
    suspend fun pdfBytes(documentId: String): Result<ByteArray> =
        runCatching { documentApi.bytes(documentId).bytes() }

    fun describeError(error: Throwable, fallback: String): String =
        errorMapper.describe(error, fallback).message

    private fun failed(error: Throwable, fallback: String): LibraryOutcome {
        // `runCatching` also catches CancellationException. Swallowing it turns
        // a cancelled upload into an ordinary retryable failure, so the worker
        // never reaches its cancel handling and the ongoing notification is
        // stranded for good. Let cancellation travel instead.
        if (error is CancellationException) throw error
        val failure = errorMapper.describe(error, fallback)
        return LibraryOutcome.Failed(failure.message, failure.code)
    }

    private fun DocumentResponseDto.toCacheEntity() = DocumentCacheEntity(
        id = id,
        fileName = fileName,
        fileType = fileType,
        fileSize = fileSize,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cachedAt = System.currentTimeMillis()
    )

    private fun DocumentCacheEntity.toDomain() = LibraryDocument(
        id = id,
        fileName = fileName ?: "document",
        fileType = fileType ?: "pdf",
        fileSize = fileSize ?: 0L,
        status = status,
        createdAt = createdAt
    )
}
