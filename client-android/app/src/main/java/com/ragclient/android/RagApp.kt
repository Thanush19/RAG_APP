package com.ragclient.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ragclient.android.data.network.ServerWarmup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RagApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var serverWarmup: ServerWarmup

    override fun onCreate() {
        super.onCreate()
        serverWarmup.start()
    }

    // On-demand initialisation: the androidx.startup default initializer is
    // removed in the manifest so WorkManager is never built before Hilt is.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
