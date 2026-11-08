package com.ragclient.android.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostWakeMonitorTest {

    @Test
    fun reportsWakingWhileTheCallIsStillOpen() = runBlocking {
        val monitor = HostWakeMonitor(enabled = true, scope = this, slowAfterMs = 30)
        monitor.onCallStart()
        delay(80)
        assertTrue(monitor.waking.value)
        monitor.onCallEnd()
        assertFalse(monitor.waking.value)
    }

    @Test
    fun staysQuietWhenTheCallFinishesQuickly() = runBlocking {
        val monitor = HostWakeMonitor(enabled = true, scope = this, slowAfterMs = 200)
        monitor.onCallStart()
        monitor.onCallEnd()
        delay(250)
        assertFalse(monitor.waking.value)
    }

    @Test
    fun disabledMonitorNeverWakes() = runBlocking {
        val monitor = HostWakeMonitor(enabled = false, scope = this, slowAfterMs = 10)
        monitor.onCallStart()
        delay(40)
        assertFalse(monitor.waking.value)
        monitor.onCallEnd()
    }
}
