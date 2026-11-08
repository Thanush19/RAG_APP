package com.ragclient.android.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageLevelTest {

    @Test
    fun `empty library stays emerald`() {
        assertEquals(StorageLevel.Normal, storageLevel(0, 1000))
    }

    @Test
    fun `just under the last third stays emerald`() {
        assertEquals(StorageLevel.Normal, storageLevel(699, 1000))
    }

    @Test
    fun `last third of free space turns orange`() {
        assertEquals(StorageLevel.Warning, storageLevel(700, 1000))
        assertEquals(StorageLevel.Warning, storageLevel(899, 1000))
    }

    @Test
    fun `last tenth of free space turns red`() {
        assertEquals(StorageLevel.Critical, storageLevel(900, 1000))
        assertEquals(StorageLevel.Critical, storageLevel(1000, 1000))
    }

    @Test
    fun `over quota stays red`() {
        assertEquals(StorageLevel.Critical, storageLevel(1200, 1000))
    }

    @Test
    fun `default quota mirrors the server cap`() {
        assertEquals(50L * 1024 * 1024, USER_STORAGE_BYTES)
        assertEquals(StorageLevel.Normal, storageLevel(0))
        assertEquals(StorageLevel.Critical, storageLevel(USER_STORAGE_BYTES))
    }
}