package com.flx_apps.digitaldetox.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Wakes up at the end (or start) of a feature's schedule, or at the end of a brake, and applies
 * the change, then arms itself for the next boundary. Without it a schedule only takes effect when something else happens to
 * ask which features are active, which on an idle phone can be a long time after the fact.
 */
@HiltWorker
class ScheduleBoundaryWorker @AssistedInject constructor(
    @Assisted private val appContext: Context, @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.i("ScheduleBoundaryWorker: applying schedule transitions")
        runCatching {
            // on the main thread, where the service and the brake's own timer run it as well, so
            // the features hear of a brake's end from one thread; releasing it applies the
            // transitions on its own
            val released = withContext(Dispatchers.Main) { EmergencyBrakeFeature.settle(appContext) }
            if (!released) FeaturesProvider.applyScheduleTransitions()
        }.onFailure {
            Timber.w(it, "ScheduleBoundaryWorker: could not apply schedule transitions")
        }
        ServiceReliabilityScheduler.scheduleNextScheduleBoundary(appContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "schedule_boundary"
    }
}
