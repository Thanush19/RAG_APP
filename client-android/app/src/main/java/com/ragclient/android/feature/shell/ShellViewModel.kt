package com.ragclient.android.feature.shell

import androidx.lifecycle.viewModelScope
import com.ragclient.android.core.mvi.BaseViewModel
import com.ragclient.android.data.auth.AuthRepository
import com.ragclient.android.data.repository.ConversationRepository
import com.ragclient.android.data.repository.DocumentRepository
import com.ragclient.android.data.repository.LibraryOutcome
import com.ragclient.android.data.repository.StagedUpload
import com.ragclient.android.data.repository.UploadStager
import com.ragclient.android.data.upload.UploadQueue
import com.ragclient.android.domain.model.DocumentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class ShellViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val conversations: ConversationRepository,
    private val documents: DocumentRepository,
    private val uploadStager: UploadStager,
    private val uploadQueue: UploadQueue
) : BaseViewModel<ShellState, ShellIntent, ShellResult>(ShellState()) {

    init {
        viewModelScope.launch {
            authRepository.session.collect { reduceResult(ShellResult.SessionChanged(it)) }
        }
        viewModelScope.launch {
            conversations.observeConversations()
                .collect { reduceResult(ShellResult.ConversationsChanged(it)) }
        }
        viewModelScope.launch {
            documents.observeLibrary()
                .collect { reduceResult(ShellResult.DocumentsChanged(it)) }
        }
        // The queue lives in WorkManager's own storage, so this list is
        // rebuilt from scratch after the process was killed.
        viewModelScope.launch {
            uploadQueue.observe().collect { reduceResult(ShellResult.UploadsChanged(it)) }
        }
        // Documents move UPLOADED -> PROCESSING -> READY on the server,
        // so the list is polled faster while anything is still working.
        viewModelScope.launch {
            while (isActive) {
                documents.refresh()
                val busy = state.value.documents.any {
                    it.status == DocumentStatus.UPLOADED ||
                        it.status == DocumentStatus.PROCESSING
                }
                delay(if (busy) BUSY_POLL else IDLE_POLL)
            }
        }
    }

    override fun handle(intent: ShellIntent) {
        when (intent) {
            ShellIntent.Refresh -> viewModelScope.launch { apply(documents.refresh()) }

            is ShellIntent.Upload -> viewModelScope.launch {
                // Copying locally is fast; the slow network part is handed
                // to WorkManager so it survives leaving the app.
                when (val staged = uploadStager.stage(intent.uri)) {
                    is StagedUpload.Ready -> {
                        uploadQueue.enqueue(staged.file)
                        reduceResult(ShellResult.Banner(QUEUED))
                    }
                    StagedUpload.TooLarge ->
                        reduceResult(ShellResult.Banner("Files must be 10 MB or smaller."))
                    StagedUpload.Unreadable ->
                        reduceResult(ShellResult.Banner("Couldn't read that file."))
                }
            }

            is ShellIntent.CancelUpload -> uploadQueue.cancel(intent.job)

            is ShellIntent.DeleteConversation -> viewModelScope.launch {
                conversations.deleteConversation(intent.conversationId)
                reduceResult(ShellResult.Banner("Chat deleted."))
            }

            is ShellIntent.DeleteChatsForDocument -> viewModelScope.launch {
                val removed = conversations.deleteAllForDocument(intent.documentId)
                reduceResult(
                    ShellResult.Banner(
                        if (removed == 0) "No chats in that document." else "Chats deleted."
                    )
                )
            }

            is ShellIntent.DeleteDocument -> viewModelScope.launch {
                // A thread scoped to a gone document can never retrieve
                // again, so the file and its chats leave together.
                conversations.deleteAllForDocument(intent.documentId)
                apply(documents.remove(intent.documentId), successBanner = "Document deleted.")
            }

            is ShellIntent.RetryDocument -> viewModelScope.launch {
                apply(documents.reprocess(intent.documentId), successBanner = "Reprocessing…")
            }

            ShellIntent.SignOut -> viewModelScope.launch { authRepository.signOut() }

            ShellIntent.BannerShown -> reduceResult(ShellResult.BannerCleared)
        }
    }

    private fun apply(
        outcome: LibraryOutcome,
        successBanner: String? = null
    ) {
        when (outcome) {
            LibraryOutcome.Ok -> successBanner?.let { reduceResult(ShellResult.Banner(it)) }
            is LibraryOutcome.Failed -> reduceResult(ShellResult.Banner(outcome.message))
        }
    }

    override fun reduce(state: ShellState, result: ShellResult): ShellState =
        ShellReducer.reduce(state, result)

    private companion object {
        const val BUSY_POLL = 5_000L
        const val IDLE_POLL = 30_000L
        const val QUEUED = "Added to the upload queue — it continues if you close the app."
    }
}
