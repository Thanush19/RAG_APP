package com.ragclient.android.data.repository

import app.cash.turbine.test
import com.ragclient.android.data.local.ConversationDao
import com.ragclient.android.data.local.ConversationEntity
import com.ragclient.android.data.local.MessageDao
import com.ragclient.android.data.local.MessageEntity
import com.ragclient.android.domain.model.ChatRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeMessageDao : MessageDao {
        val messages = mutableListOf<MessageEntity>()
        val flow = MutableStateFlow<List<MessageEntity>>(emptyList())

        fun sync() {
            flow.value = messages.toList()
        }

        override fun observeForConversation(conversationId: String): Flow<List<MessageEntity>> =
            flow.map { list -> list.filter { it.conversationId == conversationId } }

        override suspend fun insertAll(messages: List<MessageEntity>) {
            this.messages.addAll(messages)
            sync()
        }

        override suspend fun insert(message: MessageEntity): Long {
            messages.add(message)
            sync()
            return messages.size.toLong()
        }

        override suspend fun clearForConversation(conversationId: String) {
            messages.removeAll { it.conversationId == conversationId }
            sync()
        }

        override suspend fun clearForConversations(conversationIds: List<String>) {
            messages.removeAll { it.conversationId in conversationIds }
            sync()
        }

        override suspend fun replaceContent(id: Long, text: String, sourcesJson: String?) {
            val idx = messages.indexOfFirst { it.id == id }
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(text = text, sourcesJson = sourcesJson)
                sync()
            }
        }
    }

    private class FakeConversationDao(private val messageDao: FakeMessageDao) : ConversationDao {
        val conversations = mutableListOf<ConversationEntity>()
        private val _flow = MutableStateFlow<List<ConversationEntity>>(emptyList())

        fun sync() {
            // Mirrors: WHERE EXISTS (SELECT 1 FROM messages WHERE messages.conversationId = c.id)
            val hasMessages = conversations.filter { c ->
                messageDao.messages.any { m -> m.conversationId == c.id }
            }.sortedByDescending { it.updatedAt }
            _flow.value = hasMessages
        }

        override fun observeRecent(): Flow<List<ConversationEntity>> = _flow.asStateFlow()

        override fun observeById(id: String): Flow<ConversationEntity?> =
            _flow.map { list -> conversations.firstOrNull { it.id == id } }

        override suspend fun latest(): ConversationEntity? =
            conversations.filter { c ->
                messageDao.messages.any { m -> m.conversationId == c.id }
            }.maxByOrNull { it.updatedAt }

        override suspend fun latestScopedTo(documentId: String): ConversationEntity? =
            conversations.filter { c ->
                c.scopedDocumentId == documentId &&
                    messageDao.messages.any { m -> m.conversationId == c.id }
            }.maxByOrNull { it.updatedAt }

        override suspend fun idsScopedTo(documentId: String): List<String> =
            conversations.filter { it.scopedDocumentId == documentId }.map { it.id }

        override suspend fun exists(id: String): Boolean =
            conversations.any { it.id == id }

        override suspend fun upsert(conversation: ConversationEntity) {
            val idx = conversations.indexOfFirst { it.id == conversation.id }
            if (idx >= 0) {
                conversations[idx] = conversation
            } else {
                conversations.add(conversation)
            }
            sync()
        }

        override suspend fun touch(id: String, now: Long) {
            val idx = conversations.indexOfFirst { it.id == id }
            if (idx >= 0) {
                conversations[idx] = conversations[idx].copy(updatedAt = now)
                sync()
            }
        }

        override suspend fun deleteById(id: String) {
            conversations.removeAll { it.id == id }
            sync()
        }

        override suspend fun deleteByIds(ids: List<String>) {
            conversations.removeAll { it.id in ids }
            sync()
        }

        override suspend fun deleteEmpty() {
            conversations.removeAll { c ->
                messageDao.messages.none { m -> m.conversationId == c.id }
            }
            sync()
        }
    }

    private lateinit var messageDao: FakeMessageDao
    private lateinit var conversationDao: FakeConversationDao
    private lateinit var repository: ConversationRepository

    @Before
    fun setup() {
        messageDao = FakeMessageDao()
        conversationDao = FakeConversationDao(messageDao)
        repository = ConversationRepository(conversationDao, messageDao, json)
    }

    @Test
    fun newConversation_doesNotPersistImmediately() = runTest {
        val id = repository.newConversation(scopedDocumentId = "doc1")
        assertTrue(id.isNotBlank())
        // Thread has no conversation yet, so it is not in the database
        assertFalse(conversationDao.exists(id))
        assertEquals(0, conversationDao.conversations.size)
    }

    @Test
    fun observeConversations_onlyEmitsThreadsWithMessages() = runTest {
        repository.observeConversations().test {
            assertEquals(emptyList<Any>(), awaitItem())

            // Create a conversation entity with no messages (e.g. legacy/stray)
            conversationDao.create("c1", "Empty chat", null, System.currentTimeMillis())
            conversationDao.sync()
            // Still no messages, so observeConversations must remain empty
            expectNoEvents()

            // User sends a question -> conversation is created and message inserted
            repository.appendUserMessage("c2", "What is the policy?")
            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("c2", updated[0].id)
            assertEquals("What is the policy?", updated[0].title)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun openOrCreate_resumesThreadWithMessages_orStartsFreshWithoutPersisting() = runTest {
        // Cold start with no chats: generates fresh ID, leaves DB empty
        val emptyId = repository.openOrCreate()
        assertTrue(emptyId.isNotBlank())
        assertEquals(0, conversationDao.conversations.size)

        // After adding messages to a conversation:
        repository.appendUserMessage(emptyId, "Hello world")
        assertEquals(1, conversationDao.conversations.size)

        // Next openOrCreate returns that thread
        val resumedId = repository.openOrCreate()
        assertEquals(emptyId, resumedId)
    }

    @Test
    fun openForDocument_resumesScopedThreadWithMessages_orStartsFresh() = runTest {
        val newScopedId = repository.openForDocument("docA")
        assertTrue(newScopedId.isNotBlank())
        assertFalse(conversationDao.exists(newScopedId))

        // Asking in docA persists it
        repository.appendUserMessage(newScopedId, "Doc question", scopedDocumentId = "docA")
        assertTrue(conversationDao.exists(newScopedId))

        // Opening again for docA resumes the thread
        val reopenedId = repository.openForDocument("docA")
        assertEquals(newScopedId, reopenedId)

        // Opening for docB starts fresh without persisting
        val docBId = repository.openForDocument("docB")
        assertNotEquals(newScopedId, docBId)
        assertFalse(conversationDao.exists(docBId))
    }

    @Test
    fun newConversation_prunesPreviousEmptyConversations() = runTest {
        // Manually place an empty legacy conversation in DB
        conversationDao.create("empty1", "New chat", null, 1000L)
        conversationDao.sync()
        assertEquals(1, conversationDao.conversations.size)

        // Calling newConversation prunes the empty one
        val freshId = repository.newConversation()
        assertEquals(0, conversationDao.conversations.size)
        assertFalse(conversationDao.exists(freshId))
    }

    @Test
    fun appendUserMessage_createsConversationWithTitleAndScope_ifNotExists() = runTest {
        val id = repository.newConversation(scopedDocumentId = "sample.pdf")
        assertFalse(conversationDao.exists(id))

        repository.appendUserMessage(id, "How many pages?", scopedDocumentId = "sample.pdf")
        assertTrue(conversationDao.exists(id))

        val created = conversationDao.conversations.first { it.id == id }
        assertEquals("How many pages?", created.title)
        assertEquals("sample.pdf", created.scopedDocumentId)
        assertEquals(1, messageDao.messages.size)
        assertEquals("How many pages?", messageDao.messages[0].text)
        assertEquals(ChatRole.User.name, messageDao.messages[0].role)
    }
}
