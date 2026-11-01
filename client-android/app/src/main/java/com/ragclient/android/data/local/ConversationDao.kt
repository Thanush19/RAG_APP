package com.ragclient.android.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Local chat history (client plan 5.2 / phase C). Conversations are a
 * client-side concept: the backend is stateless per question, the app
 * groups questions into threads.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val scopedDocumentId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val text: String,
    val sourcesJson: String? = null,
    val createdAt: Long
)

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun create(
        id: String,
        title: String,
        scopedDocumentId: String?,
        now: Long
    ): String {
        upsert(
            ConversationEntity(
                id = id,
                title = title,
                scopedDocumentId = scopedDocumentId,
                createdAt = now,
                updatedAt = now
            )
        )
        return id
    }
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearForConversation(conversationId: String)

    @Query("UPDATE messages SET text = :text, sourcesJson = :sourcesJson WHERE id = :id")
    suspend fun replaceContent(id: Long, text: String, sourcesJson: String?)

    @Transaction
    suspend fun replaceConversationMessages(
        conversationId: String,
        messages: List<MessageEntity>
    ) {
        clearForConversation(conversationId)
        insertAll(messages)
    }
}
