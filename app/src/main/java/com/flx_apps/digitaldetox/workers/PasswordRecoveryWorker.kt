package com.flx_apps.digitaldetox.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that sends a notification when the 24-hour password recovery period is ready.
 * Uses @HiltWorker so dependencies can be injected if needed in the future.
 */
@HiltWorker
class PasswordRecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "password_recovery_notification"
        private const val NOTIFICATION_CHANNEL_ID = "commitment_password_recovery"
        private const val NOTIFICATION_ID = 2001

        fun schedule(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<PasswordRecoveryWorker>()
                .setInitialDelay(24, TimeUnit.HOURS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
            Timber.d("Password recovery notification scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.d("Password recovery notification cancelled")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            if (CommitmentPasswordFeature.isRecoveryReady()) {
                sendRecoveryReadyNotification()
                Result.success()
            } else {
                Timber.w("Recovery not ready when worker ran — rescheduling not needed")
                Result.failure()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to send recovery notification")
            Result.failure()
        }
    }

    private fun sendRecoveryReadyNotification() {
        createNotificationChannel()

        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock)
                .setContentTitle(applicationContext.getString(R.string.feature_commitmentPassword_recovery_ready))
                .setContentText(applicationContext.getString(R.string.feature_commitmentPassword_recovery_ready_message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                applicationContext.getString(R.string.feature_commitmentPassword_recovery_channelName),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = applicationContext.getString(R.string.feature_commitmentPassword_recovery_channelDescription)
            }
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
