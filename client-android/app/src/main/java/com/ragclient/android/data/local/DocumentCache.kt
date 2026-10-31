package com.ragclient.android.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.ragclient.android.domain.model.DocumentStatus
import kotlinx.coroutines.flow.Flow

/**
 * Room skeleton (client plan 3): convenience cache only — the server
 * is the source of truth. M2 populates it for instant library paint.
 */
@Entity(tableName = "document_cache")
data class DocumentCacheEntity(
    @PrimaryKey val id: String,
    val fileName: String?,
    val fileType: String?,
    val fileSize: Long?,
    val status: DocumentStatus,
    val createdAt: String?,
    val updatedAt: String?,
    val cachedAt: Long
)

@Dao
interface DocumentCacheDao {

    @Query("SELECT * FROM document_cache ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DocumentCacheEntity>)

    @Query("DELETE FROM document_cache")
    suspend fun clear()
}
