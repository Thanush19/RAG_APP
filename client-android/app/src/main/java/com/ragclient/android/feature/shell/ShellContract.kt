package com.ragclient.android.feature.shell

import com.ragclient.android.data.repository.LibraryDocument
import com.ragclient.android.data.storage.Session
import com.ragclient.android.data.upload.UploadJob
import com.ragclient.android.domain.model.ConversationSummary

data class ShellState(
    val session: Session = Session(),
    val conversations: List<ConversationSummary> = emptyList(),
    val documents: List<LibraryDocument> = emptyList(),
    val uploads: List<UploadJob> = emptyList(),
    val banner: String? = null
)

sealed interface ShellIntent {
    data object Refresh : ShellIntent
    data class Upload(val uri: android.net.Uri) : ShellIntent
    data class CancelUpload(val workId: String) : ShellIntent
    data class DeleteConversation(val conversationId: String) : ShellIntent
    data class DeleteDocument(val documentId: String) : ShellIntent
    data class RetryDocument(val documentId: String) : ShellIntent
    data object SignOut : ShellIntent
    data object BannerShown : ShellIntent
}

sealed interface ShellResult {
    data class SessionChanged(val session: Session) : ShellResult
    data class ConversationsChanged(val conversations: List<ConversationSummary>) : ShellResult
    data class DocumentsChanged(val documents: List<LibraryDocument>) : ShellResult
    data class UploadsChanged(val uploads: List<UploadJob>) : ShellResult
    data class Banner(val text: String) : ShellResult
    data object BannerCleared : ShellResult
}

object ShellReducer {

    fun reduce(state: ShellState, result: ShellResult): ShellState = when (result) {
        is ShellResult.SessionChanged -> state.copy(session = result.session)

        is ShellResult.ConversationsChanged ->
            state.copy(conversations = result.conversations)

        is ShellResult.DocumentsChanged -> state.copy(documents = result.documents)

        // The queue is WorkManager's, so this reducer only mirrors it; the
        // row that appears here can outlive the screen that started it.
        is ShellResult.UploadsChanged -> state.copy(uploads = result.uploads)

        is ShellResult.Banner -> state.copy(banner = result.text)

        ShellResult.BannerCleared -> state.copy(banner = null)
    }
}
