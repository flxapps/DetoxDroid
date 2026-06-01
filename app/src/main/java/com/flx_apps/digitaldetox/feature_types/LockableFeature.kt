package com.flx_apps.digitaldetox.feature_types

/**
 * Marker interface for features that can be locked by the Commitment Password Feature.
 * Implement this interface on any feature whose settings should be lockable.
 */
interface LockableFeature {
    val lockedByDefault: Boolean
        get() = true
}
