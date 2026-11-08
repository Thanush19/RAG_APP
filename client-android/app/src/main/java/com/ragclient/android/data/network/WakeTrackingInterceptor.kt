package com.ragclient.android.data.network

import okhttp3.Interceptor
import okhttp3.Response

/** Outermost interceptor: the wake timer covers retries, not just one attempt. */
class WakeTrackingInterceptor(
    private val monitor: HostWakeMonitor
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        monitor.onCallStart()
        try {
            return chain.proceed(chain.request())
        } finally {
            monitor.onCallEnd()
        }
    }
}
