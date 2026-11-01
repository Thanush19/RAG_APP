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
        val uploadKey = inputData.getString(KEY_UPLOAD_KEY) ?: fileName

        return try {
            transfer(path, fileName, uploadKey)
        } catch (cancelled: CancellationException) {
            // A cancelled job leaves no terminal notification behind.
            notifier.dismiss(uploadKey)
            throw cancelled
        }
    }

    private suspend fun transfer(
        path: String,
        fileName: String,
        uploadKey: String
    ): Result = coroutineScope {
        val file = File(path)
        if (!file.exists()) {
            notifier.markFailed(uploadKey, fileName, MISSING_FILE)
            return@coroutineScope Result.failure(failureData(MISSING_FILE))
        }

        // OkHttp calls the body writer on its own thread and setProgress is a
        // suspend call, so ticks cross over through a conflated channel that
        // always keeps only the newest percentage.
        val ticks = Channel<Int>(Channel.CONFLATED)
        val reporter = launch {
            for (percent in ticks) {
                setProgress(progressData(uploadKey, fileName, percent))
                notifier.showProgress(uploadKey, fileName, percent)
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
                notifier.markFinished(uploadKey, fileName)
                Result.success()
            }

            is LibraryOutcome.Failed -> handleFailure(uploadKey, fileName, outcome)
        }
    }

    private fun handleFailure(
        uploadKey: String,
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

        notifier.markFailed(uploadKey, fileName, outcome.message)
        return Result.failure(failureData(outcome.message))
    }

    /** Required for expedited work, and the row shown before the first tick. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "Document"
        val uploadKey = inputData.getString(KEY_UPLOAD_KEY) ?: fileName
        val notification: Notification = notifier.showProgress(uploadKey, fileName, 0)
        return ForegroundInfo(notifier.notificationIdFor(uploadKey), notification)
    }

    private fun progressData(uploadKey: String, fileName: String, percent: Int) =
        workDataOf(
            KEY_PROGRESS to percent,
            KEY_FILE_NAME to fileName,
            KEY_UPLOAD_KEY to uploadKey
        )

    private fun failureData(message: String) = workDataOf(KEY_FAILURE to message)

    companion object {
        const val TAG = "document-upload"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_UPLOAD_KEY = "uploadKey"
        const val KEY_PROGRESS = "percent"
        const val KEY_FAILURE = "failureMessage"

        private const val MISSING_FILE = "The staged copy of that file is gone."
        private const val MAX_ATTEMPTS = 3
    }
}
