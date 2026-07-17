package com.flx_apps.digitaldetox.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flx_apps.digitaldetox.data.repository.UsageStatsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.time.LocalDate

/** Periodic WorkManager worker that snapshots today's usage stats into Room. */
@HiltWorker
class UsageStatsSnapshotWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: UsageStatsRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            repository.snapshotToday()
            val cutoff = LocalDate.now().minusMonths(UsageStatsRepository.HISTORY_RETENTION_MONTHS)
            val pruned = repository.pruneOlderThan(cutoff)
            Timber.d("Usage stats snapshot taken, pruned $pruned old records")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to take usage stats snapshot")
            Result.retry()
        }
    }
}