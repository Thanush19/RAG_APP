package com.ragclient.android.data.repository

import com.ragclient.android.data.local.ConversationDao
import com.ragclient.android.data.local.MessageDao
import com.ragclient.android.data.local.MessageEntity
import com.ragclient.android.domain.model.ChatMessage
import com.ragclient.android.domain.model.ChatRole
import com.ragclient.android.domain.model.ConversationSummary
import com.ragclient.android.domain.model.RagSourceModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Local chat threads. The backend answers one question at a time; the
 * thread lives here so the drawer can show history offline.
 */
@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val json: Json
) {

    fun observeConversations(): Flow<List<ConversationSummary>> =
        conversationDao.observeRecent().map { rows ->
            rows.map {
                ConversationSummary(
                    id = it.id,
                    title = it.title,
                    updatedAt = it.updatedAt,
                    scopedDocumentId = it.scopedDocumentId
                )
            }
        }

    fun observeScopedDocumentId(conversationId: String): Flow<String?> =
        conversationDao.observeById(conversationId).map { it?.scopedDocumentId }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messageDao.observeForConversation(conversationId).map { rows ->
            rows.map { it.toChatMessage() }
        }

    /** Resumes the newest thread, or starts one when scoped to a document. */
    suspend fun openOrCreate(scopedDocumentId: String? = null): String {
        if (scopedDocumentId != null) {
            conversationDao.latest()?.let { latest ->
                if (latest.scopedDocumentId == scopedDocumentId) return latest.id
            }
            return newConversation(scopedDocumentId)
        }
        conversationDao.latest()?.let { return it.id }
        return newConversation(null)
    }

    suspend fun newConversation(scopedDocumentId: String? = null): String {
        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        conversationDao.create(
            id = id,
            title = "New chat",
            scopedDocumentId = scopedDocumentId,
            now = now
        )
        return id
    }

    suspend fun renameToFirstQuestion(conversationId: String, question: String) {
        val current = conversationDao.observeById(conversationId).first() ?: return
        if (current.title != "New chat") return
        conversationDao.upsert(
            current.copy(
                title = question.trim().take(MAX_TITLE).ifBlank { "New chat" },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun appendUserMessage(conversationId: String, text: String) {
        messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = ChatRole.User.name,
                text = text,
                createdAt = System.currentTimeMillis()
            )
        )
        conversationDao.touch(conversationId, System.currentTimeMillis())
    }

    suspend fun persistAssistantMessage(
        conversationId: String,
        text: String,
        sources: List<RagSourceModel>
    ) {
        messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = ChatRole.Assistant.name,
                text = text,
                sourcesJson = if (sources.isEmpty()) {
                    null
                } else {
                    json.encodeToString(sources.map { RagSourceModelDto.from(it) })
                },
                createdAt = System.currentTimeMillis()
            )
        )
        conversationDao.touch(conversationId, System.currentTimeMillis())
    }

    suspend fun setScope(conversationId: String, documentId: String?) {
        val current = conversationDao.observeById(conversationId).first() ?: return
        conversationDao.upsert(
            current.copy(
                scopedDocumentId = documentId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteConversation(conversationId: String) {
        messageDao.clearForConversation(conversationId)
        conversationDao.deleteById(conversationId)
    }

    private fun MessageEntity.toChatMessage(): ChatMessage = ChatMessage(
        id = id,
        role = if (role == ChatRole.Assistant.name) ChatRole.Assistant else ChatRole.User,
        text = text,
        sources = sourcesJson?.let { payload ->
            runCatching {
                json.decodeFromString<List<RagSourceModelDto>>(payload).map { it.toModel() }
            }.getOrDefault(emptyList())
        } ?: emptyList()
    )

    private companion object {
        const val MAX_TITLE = 60
    }
}

@kotlinx.serialization.Serializable
private data class RagSourceModelDto(
    val chunkId: String,
    val documentId: String,
    val pageNumber: Int? = null,
    val score: Float = 0f
) {
    companion object {
        fun from(model: RagSourceModel) = RagSourceModelDto(
            chunkId = model.chunkId,
            documentId = model.documentId,
            pageNumber = model.pageNumber,
            score = model.score
        )
    }
}

private fun RagSourceModelDto.toModel(): RagSourceModel =
    RagSourceModel(chunkId, documentId, pageNumber, score)
