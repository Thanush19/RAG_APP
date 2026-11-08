package com.ragclient.android.data.network

import com.ragclient.android.BuildConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Pokes the prod origin as soon as the process starts. Render is awake by
 * the time the user submits sign-in when the nap was short; when it was not,
 * [HostWakeMonitor] is already showing the loader.
 *
 * Any HTTP status counts as awake — the root is authenticated, so a healthy
 * cold start comes back 401. The body is discarded.
 */
@Singleton
class ServerWarmup @Inject constructor(
    private val client: OkHttpClient,
    @BaseUrl private val baseUrl: String
) {

    fun start() {
        if (BuildConfig.IS_DEV_BUILD) return
        val call = client.newCall(Request.Builder().url(baseUrl).get().build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
