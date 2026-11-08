package com.ragclient.android.data.network

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HostWakeEntryPoint {
    fun hostWakeMonitor(): HostWakeMonitor
}
