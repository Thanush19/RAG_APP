package com.ragclient.android.feature.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ragclient.android.core.mvi.BaseViewModel
import com.ragclient.android.data.pdf.OpenedPdf
import com.ragclient.android.data.pdf.PdfRepository
import com.ragclient.android.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ViewerState(
    val documentId: String = "",
    val fileName: String = "",
    val isLoading: Boolean = true,
    val pageCount: Int = 0,
    val initialPage: Int = 0,
    val error: String? = null,
    val pageAspects: List<Float> = emptyList()
)

sealed interface ViewerIntent {
    data object Retry : ViewerIntent
}

sealed interface ViewerResult {
    data class Opened(
        val pageCount: Int,
        val initialPage: Int,
        val fileName: String,
        val pageAspects: List<Float>
    ) : ViewerResult
    data class Failed(val message: String) : ViewerResult
    data object Loading : ViewerResult
}

object ViewerReducer {

    fun reduce(state: ViewerState, result: ViewerResult): ViewerState = when (result) {
        ViewerResult.Loading -> state.copy(isLoading = true, error = null)
        is ViewerResult.Opened -> state.copy(
            isLoading = false,
            pageCount = result.pageCount,
            fileName = result.fileName,
            pageAspects = result.pageAspects,
            initialPage = result.initialPage.coerceIn(0, (result.pageCount - 1).coerceAtLeast(0)),
            error = null
        )
        is ViewerResult.Failed -> state.copy(isLoading = false, error = result.message)
    }
}

@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pdfRepository: PdfRepository,
    private val documentRepository: DocumentRepository
) : BaseViewModel<ViewerState, ViewerIntent, ViewerResult>(ViewerState()) {

    /** PdfRenderer allows a single open page at a time. */
    private val renderLock = Mutex()
    private var opened: OpenedPdf? = null

    private val documentId: String =
        savedStateHandle.get<String>(ARG_DOCUMENT).orEmpty()

    private val stagedDirectory: String =
        savedStateHandle.get<String>(ARG_DIRECTORY).orEmpty()

    private val stagedName: String =
        savedStateHandle.get<String>(ARG_NAME).orEmpty()

    private val startPage: Int =
        (savedStateHandle.get<Int>(ARG_PAGE) ?: 1) - 1

    init {
        onIntent(ViewerIntent.Retry)
    }

    override fun handle(intent: ViewerIntent) {
        when (intent) {
            ViewerIntent.Retry -> load()
        }
    }

    private fun load() {
        val staged = stagedDirectory.isNotBlank() && stagedName.isNotBlank()
        if (!staged && documentId.isBlank()) {
            reduceResult(ViewerResult.Failed("No document selected."))
            return
        }
        reduceResult(ViewerResult.Loading)
        viewModelScope.launch {
            opened?.close()
            opened = null
            val outcome = if (staged) {
                pdfRepository.openStaged(stagedDirectory, stagedName)
            } else {
                pdfRepository.open(documentId)
            }
            outcome.onSuccess { handle ->
                opened = handle
                val name = if (staged) stagedName else libraryName()
                val aspects = withContext(Dispatchers.IO) { handle.pageAspects() }
                reduceResult(ViewerResult.Opened(handle.pageCount, startPage, name, aspects))
            }.onFailure { error ->
                reduceResult(
                    ViewerResult.Failed(
                        documentRepository.describeError(
                            error,
                            "This PDF could not be opened."
                        )
                    )
                )
            }
        }
    }

    private suspend fun libraryName(): String =
        documentRepository.observeLibrary().first()
            .firstOrNull { it.id == documentId }?.fileName ?: "Document"

    /**
     * The calling page composable owns the returned bitmap, so scrolling away
     * from a page releases it instead of accumulating the whole document.
     */
    suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap? =
        renderLock.withLock {
            runCatching { opened?.render(index, widthPx) }.getOrNull()
        }

    override fun reduce(state: ViewerState, result: ViewerResult): ViewerState =
        ViewerReducer.reduce(state, result)

    override fun onCleared() {
        opened?.close()
        opened = null
        super.onCleared()
    }

    companion object {
        const val ARG_DOCUMENT = "documentId"
        const val ARG_PAGE = "page"
        const val ARG_DIRECTORY = "directory"
        const val ARG_NAME = "name"
    }
}
