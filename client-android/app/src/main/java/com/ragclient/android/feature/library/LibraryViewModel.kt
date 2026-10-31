package com.ragclient.android.feature.library

import com.ragclient.android.core.mvi.BaseViewModel
import com.ragclient.android.data.api.dto.DocumentResponseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** M2 lands list/upload/delete/view/retry against DocumentApi. */

data class LibraryState(
    val documents: List<DocumentResponseDto> = emptyList(),
    val isLoading: Boolean = false,
    val uploadInProgress: Boolean = false,
    val errorMessage: String? = null
)

sealed interface LibraryIntent {
    data object Load : LibraryIntent
    data class Delete(val documentId: String) : LibraryIntent
    data class Retry(val documentId: String) : LibraryIntent
    data class View(val documentId: String) : LibraryIntent
}

sealed interface LibraryResult

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val documentApi: com.ragclient.android.data.api.DocumentApi
) : BaseViewModel<LibraryState, LibraryIntent, LibraryResult>(LibraryState()) {

    override fun handle(intent: LibraryIntent) {
        // M2: intent -> actor -> LibraryResult -> reduce
    }

    override fun reduce(state: LibraryState, result: LibraryResult): LibraryState = state
}
