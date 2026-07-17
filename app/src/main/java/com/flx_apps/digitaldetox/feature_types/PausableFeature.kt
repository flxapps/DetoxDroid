package com.flx_apps.digitaldetox.feature_types

/**
 * Marks a [Feature] whose effect the [com.flx_apps.digitaldetox.features.PauseButtonFeature] can
 * temporarily suspend during a pause.
 *
 * The Pause settings let the user choose which of these features a pause actually affects (default:
 * all of them, see [com.flx_apps.digitaldetox.features.PauseButtonFeature.pauseExemptFeatureIds]).
 * Meta-features such as the pause button itself and the commitment password are intentionally *not*
 * pausable — a pause should never lift the commitment lock, and pausing the pause button is
 * meaningless.
 */
interface PausableFeature
