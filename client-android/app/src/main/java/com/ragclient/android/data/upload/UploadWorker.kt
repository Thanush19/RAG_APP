package com.ragclient.android.data.upload

import android.app.Notification
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ragclient.android.data.network.ApiErrorMapper
import com.ragclient.android.data.repository.DocumentRepository
import com.ragclient.android.data.repository.LibraryOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Uploads one staged document. Runs outside any ViewModel, so closing the
 * app — or the system killing it — does not cancel the transfer.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val documents: DocumentRepository,
    private val notifier: UploadNotifier
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_FILE_PATH)
            ?: return Result.failure(failureData("Upload request was malformed."))
        val fileName = inputData.getString(KEY_FILE_NAME) ?: File(path).name
        // The work id is the notification key: unlike the staged path the UI
        // already holds it, so a cancel can always name the row it needs to clear.
        val jobId = params.id.toString()

        return try {
            transfer(path, fileName, jobId)
        } catch (cancelled: CancellationException) {
            // A cancelled job leaves no ongoing row and no staged copy behind.
            notifier.dismiss(jobId)
            discardStagedFile()
            throw cancelled
        }
    }

    private suspend fun transfer(
        path: String,
        fileName: String,
        jobId: String
    ): Result = coroutineScope {
        val file = File(path)
        if (!file.exists()) {
            notifier.markFailed(jobId, fileName, MISSING_FILE)
            return@coroutineScope Result.failure(failureData(MISSING_FILE))
        }

        // OkHttp calls the body writer on its own thread and setProgress is a
        // suspend call, so ticks cross over through a conflated channel that
        // always keeps only the newest percentage.
        val ticks = Channel<Int>(Channel.CONFLATED)
        val reporter = launch {
            for (percent in ticks) {
                setProgress(progressData(fileName, percent))
                notifier.showProgress(jobId, fileName, percent)
            }
        }

        val outcome = documents.upload(file) { written, total ->
            ticks.trySend(if (total <= 0L) 0 else ((written * 100) / total).toInt())
        }

        ticks.close()
        reporter.join()

        when (outcome) {
            LibraryOutcome.Ok -> {
                file.parentFile?.deleteRecursively()
                notifier.markFinished(jobId, fileName)
                Result.success()
            }

            is LibraryOutcome.Failed -> handleFailure(jobId, fileName, outcome)
        }
    }

    private fun handleFailure(
        jobId: String,
        fileName: String,
        outcome: LibraryOutcome.Failed
    ): Result {
        val retryable = outcome.code == ApiErrorMapper.CONNECTION_FAILED &&
            params.runAttemptCount < MAX_ATTEMPTS

        if (retryable) {
            // Leave the ongoing row alone; WorkManager backs off and the
            // same notification slot is updated when the attempt restarts.
            return Result.retry()
        }

        notifier.markFailed(jobId, fileName, outcome.message)
        discardStagedFile()
        return Result.failure(failureData(outcome.message))
    }

    /**
     * The staged copy lives in filesDir, where nothing trims it, so a job that
     * will never run again removes its own. CoroutineWorker.onStopped is final,
     * so the cancel path is the CancellationException catch in doWork.
     */
    private fun discardStagedFile() {
        val path = inputData.getString(KEY_FILE_PATH) ?: return
        File(path).parentFile?.deleteRecursively()
    }

    /** Required for expedited work, and the row shown before the first tick. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "Document"
        val jobId = params.id.toString()
        val notification: Notification = notifier.showProgress(jobId, fileName, 0)
        return ForegroundInfo(notifier.notificationIdFor(jobId), notification)
    }

    private fun progressData(fileName: String, percent: Int) =
        workDataOf(
            KEY_PROGRESS to percent,
            KEY_FILE_NAME to fileName
        )

    private fun failureData(message: String) = workDataOf(KEY_FAILURE to message)

    companion object {
        const val TAG = "document-upload"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_PROGRESS = "percent"
        const val KEY_FAILURE = "failureMessage"

        private const val MISSING_FILE = "The staged copy of that file is gone."
        private const val MAX_ATTEMPTS = 3
    }
}
