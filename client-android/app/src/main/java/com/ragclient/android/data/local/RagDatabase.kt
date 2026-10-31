package com.ragclient.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentCacheEntity::class],
    version = 1,
    exportSchema = true
)
abstract class RagDatabase : RoomDatabase() {

    abstract fun documentCacheDao(): DocumentCacheDao
}
