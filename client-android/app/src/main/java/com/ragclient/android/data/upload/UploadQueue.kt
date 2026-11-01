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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
    @ApplicationContext private val context: Context,
    private val notifier: UploadNotifier
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    suspend fun enqueue(staged: File) {
        // Keyed on the staged file, not on "now": a timestamp makes every
        // enqueue unique, so ExistingWorkPolicy.KEEP could never match
        // anything and the dedupe reads as working when it cannot.
        val uniqueName = staged.absolutePath
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .addTag(UploadWorker.TAG)
            // Mirrored as a tag because WorkInfo never exposes input data, and
            // the pruning pass below needs to know which copies a live job owns.
            .addTag(uniqueName)
            .setInputData(
                workDataOf(
                    UploadWorker.KEY_FILE_PATH to staged.absolutePath,
                    UploadWorker.KEY_FILE_NAME to staged.name
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

        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
        pruneOrphanStagedFiles()
    }

    /**
     * Staged copies live in filesDir, which the system never trims, and a job
     * cancelled while it is parked in retry backoff has no worker left to
     * clean up after it. Every enqueue sweeps the directory, so an abandoned
     * upload costs storage until the next one rather than forever.
     */
    private suspend fun pruneOrphanStagedFiles() = withContext(Dispatchers.IO) {
        val owned = workManager.getWorkInfosByTagFlow(UploadWorker.TAG)
            .first()
            .filter { it.state in LIVE_STATES }
            .flatMap { info -> info.tags }
            .toSet()

        File(context.filesDir, "uploads")
            .listFiles()
            .orEmpty()
            .flatMap { batch -> batch.listFiles().orEmpty().toList() }
            .filterNot { it.absolutePath in owned }
            .forEach { staged -> staged.parentFile?.deleteRecursively() }
    }

    fun cancel(workId: String) {
        val id = runCatching { UUID.fromString(workId) }.getOrNull() ?: return
        // A job parked in retry backoff has no live worker left to clear its
        // own ongoing row, so the queue dismisses it here. The row is keyed on
        // the work id, which is the one handle the UI always has.
        notifier.dismiss(workId)
        workManager.cancelWorkById(id)
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
