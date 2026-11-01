package com.ragclient.android.data.local

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun ragDatabase(@ApplicationContext context: Context): RagDatabase =
        Room.databaseBuilder(context, RagDatabase::class.java, "rag-client.db")
            .addMigrations(RagDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun documentCacheDao(database: RagDatabase): DocumentCacheDao =
        database.documentCacheDao()

    @Provides
    fun conversationDao(database: RagDatabase): ConversationDao =
        database.conversationDao()

    @Provides
    fun messageDao(database: RagDatabase): MessageDao =
        database.messageDao()
}
