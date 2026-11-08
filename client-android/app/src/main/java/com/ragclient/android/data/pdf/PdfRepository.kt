package com.ragclient.android.data.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ragclient.android.data.repository.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-house PDF rendering (client plan D2): `android.graphics.pdf` only,
 * no third-party dependency, and page targeting so search and citation
 * chips can deep-link straight to a page.
 */
class OpenedPdf(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    val pageCount: Int get() = renderer.pageCount

    /** Height/width per page, so the scroll list can reserve exact page heights before rendering. */
    fun pageAspects(): List<Float> = (0 until pageCount).map { index ->
        renderer.openPage(index).use { page -> page.height.toFloat() / page.width }
    }

    fun render(pageIndex: Int, widthPx: Int): ImageBitmap {
        require(pageIndex in 0 until pageCount)
        return renderer.openPage(pageIndex).use { page ->
            val ratio = widthPx.toFloat() / page.width
            val height = (page.height * ratio).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.asImageBitmap()
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

@Singleton
class PdfRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documents: DocumentRepository
) {

    suspend fun open(documentId: String): Result<OpenedPdf> = withContext(Dispatchers.IO) {
        documents.pdfBytes(documentId)
            .mapCatching { bytes ->
                val file = File(context.cacheDir, "pdf-$documentId.pdf")
                file.writeBytes(bytes)
                rendererFor(file)
            }
    }

    /**
     * First page at card width for the documents manager. The bytes are the
     * same file the viewer caches, so a card that was opened once costs no
     * network at all; the bitmap cache keeps list recompositions cheap.
     */
    suspend fun thumbnail(documentId: String, widthPx: Int): Result<ImageBitmap> =
        withContext(Dispatchers.IO) {
            thumbnails.get(documentId)?.let { return@withContext Result.success(it) }
            runCatching {
                val file = File(context.cacheDir, "pdf-$documentId.pdf")
                if (!file.isFile) {
                    file.writeBytes(documents.pdfBytes(documentId).getOrThrow())
                }
                val handle = rendererFor(file)
                try {
                    handle.render(0, widthPx)
                } finally {
                    handle.close()
                }
            }.onSuccess { thumbnails.put(documentId, it) }
        }

    private val thumbnails = object : LruCache<String, ImageBitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /**
     * A staged upload is a local file that has no server copy yet, so the
     * drawer can preview it while the bytes are still moving. The path arrives
     * as navigation arguments, which is untrusted input even when our own
     * staging code wrote it, so it is confined to the uploads directory.
     */
    suspend fun openStaged(directory: String, name: String): Result<OpenedPdf> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = File(context.filesDir, "uploads").canonicalFile
                val file = stagedFile(root, directory, name)
                    ?: error("That preview is no longer available.")
                rendererFor(file)
            }
        }

    private fun rendererFor(file: File): OpenedPdf {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return OpenedPdf(descriptor, PdfRenderer(descriptor))
    }
}

/**
 * Resolves a staged upload to a real file, refusing anything that escapes
 * [root] once symlinks and `..` are collapsed. The staged layout is always
 * `<uploads>/<batch>/<file>`, so both segments come from navigation arguments.
 */
internal fun stagedFile(root: File, directory: String, name: String): File? {
    val candidate = File(root, "$directory${File.separator}$name").canonicalFile
    return candidate.takeIf {
        it.toPath().startsWith(root.canonicalFile.toPath()) && it.isFile
    }
}

private const val THUMBNAIL_CACHE_BYTES = 8 * 1024 * 1024
