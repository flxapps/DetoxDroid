package com.flx_apps.digitaldetox.prefs

import org.androidannotations.annotations.sharedpreferences.*

/**
 * Creation Date: 11/15/20
 * @author felix
 */
@SharedPref(SharedPref.Scope.APPLICATION_DEFAULT)
interface Prefs {
    @DefaultBoolean(false)
    fun isRunning(): Boolean

    @DefaultBoolean(false)
    fun zenModeDefaultEnabled(): Boolean

    @DefaultBoolean(false)
    fun grayscaleEnabled(): Boolean

    @DefaultBoolean(false)
    fun grayscaleExtraDim(): Boolean

    @DefaultStringSet()
    fun grayscaleExceptions(): Set<String>

    @DefaultBoolean(false)
    fun grayscaleIgnoreNonFullScreen(): Boolean

    @DefaultLong(-1)
    fun pauseUntil(): Long

    @DefaultInt(1)
    fun pauseDuration(): Int

    @DefaultLong(-1)
    fun nextPauseAllowedAt(): Long

    @DefaultInt(5)
    fun timeBetweenPauses(): Int

    @DefaultBoolean(false)
    fun breakDoomScrollingEnabled(): Boolean

    @DefaultStringSet()
    fun breakDoomScrollingExceptions(): Set<String>

    @DefaultStringSet()
    fun timeRules(): Set<String>

    @DefaultInt(3)
    fun timeUntilDoomScrollingWarning(): Int

    @DefaultBoolean(false)
    fun deactivateAppsEnabled(): Boolean

    @DefaultStringSet()
    fun deactivatedApps(): Set<String>
}