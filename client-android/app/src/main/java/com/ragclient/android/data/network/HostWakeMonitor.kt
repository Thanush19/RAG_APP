package com.ragclient.android.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Prod-only signal for Render's free tier. The instance sleeps when idle, and
 * the first request after that can sit open for about a minute. Callers flip
 * this on once a call has been in flight longer than [slowAfterMs], so the UI
 * can show a loader instead of looking stuck.
 */
class HostWakeMonitor(
    private val enabled: Boolean,
    private val scope: CoroutineScope,
    private val slowAfterMs: Long
) {

    private val _waking = MutableStateFlow(false)
    val waking: StateFlow<Boolean> = _waking.asStateFlow()

    private val lock = Any()
    private var inFlight = 0
    private var slowJob: Job? = null

    fun onCallStart() {
        if (!enabled) return
        val startTimer = synchronized(lock) {
            inFlight += 1
            inFlight == 1
        }
        if (!startTimer) return
        slowJob = scope.launch {
            delay(slowAfterMs)
            val stillWaiting = synchronized(lock) { inFlight > 0 }
            if (stillWaiting) _waking.value = true
        }
    }

    fun onCallEnd() {
        if (!enabled) return
        val idle = synchronized(lock) {
            inFlight = (inFlight - 1).coerceAtLeast(0)
            inFlight == 0
        }
        if (!idle) return
        slowJob?.cancel()
        _waking.value = false
    }

    companion object {
        /** Long enough that a normal response never flashes the wake copy. */
        const val SLOW_AFTER_MS = 3_000L
    }
}
