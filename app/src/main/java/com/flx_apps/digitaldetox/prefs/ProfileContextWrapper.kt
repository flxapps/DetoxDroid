package com.flx_apps.digitaldetox.prefs

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * Creation Date: 1/18/22
 * @author felix
 */
class ProfileContextWrapper(
    var profileName: String = "", context: Context,
) : ContextWrapper(context) {
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        return super.getSharedPreferences(name + profileName, mode)
    }
}