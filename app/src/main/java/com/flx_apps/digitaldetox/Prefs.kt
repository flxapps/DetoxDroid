package com.flx_apps.digitaldetox

import org.androidannotations.annotations.sharedpreferences.DefaultBoolean
import org.androidannotations.annotations.sharedpreferences.DefaultLong
import org.androidannotations.annotations.sharedpreferences.DefaultStringSet
import org.androidannotations.annotations.sharedpreferences.SharedPref

/**
 * Creation Date: 11/15/20
 * @author felix
 */
@SharedPref(SharedPref.Scope.APPLICATION_DEFAULT)
interface Prefs {
    @DefaultBoolean(false)
    fun isRunning(): Boolean

    @DefaultBoolean(true)
    fun zenModeDefaultEnabled(): Boolean

    @DefaultBoolean(false)
    fun grayscaleEnabled(): Boolean

    @DefaultStringSet()
    fun grayscaleExceptions(): Set<String>

    @DefaultLong(-1)
    fun pauseUntil(): Long
}