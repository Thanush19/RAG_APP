package com.ragclient.android.data.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ragclient.android.MainActivity
import com.ragclient.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * One ongoing notification per upload. The id is derived from the work id
 * rather than a counter or the file path: the work id survives process death
 * and, unlike the path, the UI already has it, so a cancel can always name the
 * row it needs to clear.
 */
@Singleton
class UploadNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun notificationIdFor(jobId: String): Int =
        SLOT_BASE + (jobId.hashCode().absoluteValue % SLOT_POOL)

    /** Posted on every tick and handed to WorkManager as the expedited notification. */
    fun showProgress(jobId: String, fileName: String, percent: Int): Notification {
        val notification = builder(fileName)
            .setContentTitle(fileName)
            .setContentText(context.getString(R.string.upload_progress_percent, percent))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.upload_progress, fileName, percent))
            )
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .build()
        post(jobId, notification)
        return notification
    }

    fun markFinished(jobId: String, fileName: String) {
        post(
            jobId,
            builder(fileName)
                .setContentTitle(context.getString(R.string.upload_done_title))
                .setContentText(fileName)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.upload_done, fileName))
                )
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
        )
    }

    fun markFailed(jobId: String, fileName: String, reason: String) {
        post(
            jobId,
            builder(fileName)
                .setContentTitle(context.getString(R.string.upload_failed_title))
                // The collapsed row has to name the file too: with several uploads
                // in the shade, a bare reason is impossible to attribute.
                .setContentText("$fileName — $reason")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText("$fileName — $reason")
                )
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
        )
    }

    /** Used when a job is cancelled: nobody else will clear the ongoing row. */
    fun dismiss(jobId: String) {
        manager.cancel(notificationIdFor(jobId))
    }

    private fun post(jobId: String, notification: Notification) {
        beginChannel()
        manager.notify(notificationIdFor(jobId), notification)
    }

    private fun builder(fileName: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setGroup(GROUP_KEY)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent(fileName))

    private fun beginChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.upload_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.upload_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun openAppIntent(fileName: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_FILE_NAME, fileName)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            fileName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "document-uploads"
        const val GROUP_KEY = "document-uploads-group"
        const val EXTRA_FILE_NAME = "file_name"

        private const val SLOT_BASE = 2000
        private const val SLOT_POOL = 1000
    }
}
