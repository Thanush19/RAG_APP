package com.ragclient.android.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface StagedUpload {
    data class Ready(val file: File) : StagedUpload
    data object TooLarge : StagedUpload
    data object Unreadable : StagedUpload
}

/**
 * SAF picks arrive as content:// uris; the backend wants a multipart
 * file part, so the bytes are copied into app storage first.
 *
 * The copy is deliberately in filesDir, not cacheDir: the worker may run
 * long after the app was killed, and cache files can be evicted while a
 * job is still queued.
 */
@Singleton
class UploadStager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Each pick gets its own directory so the file keeps its display name. */
    suspend fun stage(uri: Uri): StagedUpload = withContext(Dispatchers.IO) {
        // Failing here costs one query; failing after the copy costs the
        // user a multi-minute upload that the server rejects at the end.
        val declared = querySize(uri)
        if (declared != null && declared > MAX_BYTES) return@withContext StagedUpload.TooLarge

        val name = queryName(uri) ?: "document.pdf"
        val target = File(
            File(context.filesDir, "uploads").apply { mkdirs() },
            System.currentTimeMillis().toString()
        ).apply { mkdirs() }.let { dir -> File(dir, sanitize(name)) }

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.isSuccess

        when {
            !copied || !target.exists() -> {
                target.parentFile?.deleteRecursively()
                StagedUpload.Unreadable
            }
            // Some providers report no size, so the real copy is the check.
            target.length() > MAX_BYTES -> {
                target.parentFile?.deleteRecursively()
                StagedUpload.TooLarge
            }
            else -> StagedUpload.Ready(target)
        }
    }

    private fun queryName(uri: Uri): String? = queryColumn(uri, OpenableColumns.DISPLAY_NAME) {
        it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
    }

    private fun querySize(uri: Uri): Long? = queryColumn(uri, OpenableColumns.SIZE) { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
    }

    private fun <T> queryColumn(
        uri: Uri,
        column: String,
        read: (android.database.Cursor) -> T?
    ): T? {
        val cursor = runCatching {
            context.contentResolver.query(uri, null, null, null, null)
        }.getOrNull() ?: return null
        return cursor.use {
            val index = it.getColumnIndex(column)
            if (index >= 0 && it.moveToFirst()) read(it) else null
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "document.pdf" }

    private companion object {
        /** Mirrors `DocumentService.MAX_FILE_SIZE` on the backend. */
        const val MAX_BYTES = 10L * 1024 * 1024
    }
}
