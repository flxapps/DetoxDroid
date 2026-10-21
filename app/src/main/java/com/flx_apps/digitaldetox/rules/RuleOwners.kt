package com.flx_apps.digitaldetox.rules

import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.workers.ServiceReliabilityScheduler

/**
 * Who an app list or a schedule belongs to, by id: a group, or a feature that still carries one
 * of its own (Do Not Disturb keeps a schedule). The screens that edit lists and schedules were
 * written for features and take an id; this is what lets them take a group's.
 */
object RuleOwners {
    /** The app and website list under [id], or null when nothing with that id has one. */
    fun appScope(id: String): SupportsAppExceptionsFeature? =
        RuleGroups.get(id) ?: FeaturesProvider.getFeatureById(id) as? SupportsAppExceptionsFeature

    /** The schedule under [id], or null when nothing with that id has one. */
    fun schedule(id: String): SupportsScheduleFeature? =
        RuleGroups.get(id) ?: FeaturesProvider.getFeatureById(id) as? SupportsScheduleFeature

    /** Whether the owner is switched on, which is when its schedule is doing anything. */
    fun isEnabled(id: String): Boolean =
        RuleGroups.get(id)?.isEnabled ?: FeaturesProvider.getFeatureById(id)?.isActivated ?: false

    /** Makes a change to the list or schedule under [id] take effect. */
    fun onChanged(id: String) {
        val group = RuleGroups.get(id)
        if (group != null) {
            RuleGroups.onChanged()
            return
        }
        val feature = FeaturesProvider.getFeatureById(id) ?: return
        FeaturesProvider.startOrStopFeature(feature)
        ServiceReliabilityScheduler.scheduleNextScheduleBoundary(DetoxDroidApplication.appContext)
    }
}
