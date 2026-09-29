package com.flx_apps.digitaldetox.feature_types

import android.content.Context

/**
 * A [Feature] that has something to do at the moment a brake starts or ends, beyond looking at the
 * app in front again.
 * @see com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
 */
interface BrakeAwareFeature {
    /**
     * Called on a running feature when a brake that covers it has started or ended.
     */
    fun onBrakeChanged(context: Context)
}
