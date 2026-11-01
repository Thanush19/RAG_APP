package com.ragclient.android.data.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UploadJob(
    val workId: String,
    val fileName: String,
    val percent: Int,
    val running: Boolean
)

/**
 * The upload queue, backed by WorkManager rather than an in-memory flag so
 * it survives the app being closed, swiped away or rebooted.
 */
@Singleton
class UploadQueue @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun enqueue(staged: File) {
        val uploadKey = "${System.currentTimeMillis()}-${staged.name}"
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .addTag(UploadWorker.TAG)
            .setInputData(
                workDataOf(
                    UploadWorker.KEY_FILE_PATH to staged.absolutePath,
                    UploadWorker.KEY_FILE_NAME to staged.name,
                    UploadWorker.KEY_UPLOAD_KEY to uploadKey
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(uploadKey, ExistingWorkPolicy.KEEP, request)
    }

    /** Cancel by id: WorkInfo no longer exposes inputData, but always has an id. */
    fun cancel(workId: String) {
        runCatching { UUID.fromString(workId) }
            .onSuccess { workManager.cancelWorkById(it) }
    }

    fun observe(): Flow<List<UploadJob>> =
        workManager.getWorkInfosByTagFlow(UploadWorker.TAG).map { infos ->
            infos.filter { it.state in LIVE_STATES }
                .map { info ->
                    UploadJob(
                        workId = info.id.toString(),
                        fileName = info.progress
                            .getString(UploadWorker.KEY_FILE_NAME) ?: PREPARING,
                        percent = info.progress.getInt(UploadWorker.KEY_PROGRESS, 0),
                        running = info.state == WorkInfo.State.RUNNING
                    )
                }
        }

    private companion object {
        val LIVE_STATES = setOf(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING,
            WorkInfo.State.BLOCKED
        )
        const val BACKOFF_SECONDS = 30L
        const val PREPARING = "Preparing upload"
    }
}
