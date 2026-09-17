package com.flx_apps.digitaldetox.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flx_apps.digitaldetox.features.FeaturesProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Wakes up at the end (or start) of a feature's schedule and applies the change, then arms itself
 * for the next boundary. Without it a schedule only takes effect when something else happens to
 * ask which features are active, which on an idle phone can be a long time after the fact.
 */
@HiltWorker
class ScheduleBoundaryWorker @AssistedInject constructor(
    @Assisted private val appContext: Context, @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.i("ScheduleBoundaryWorker: applying schedule transitions")
        runCatching { FeaturesProvider.applyScheduleTransitions() }.onFailure {
            Timber.w(it, "ScheduleBoundaryWorker: could not apply schedule transitions")
        }
        ServiceReliabilityScheduler.scheduleNextScheduleBoundary(appContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "schedule_boundary"
    }
}
