package com.flx_apps.digitaldetox.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.navigation.findNavController
import androidx.preference.Preference
import com.flx_apps.digitaldetox.AppExceptionsListFragment_
import com.flx_apps.digitaldetox.MainActivity
import com.flx_apps.digitaldetox.R

/**
 * Creation Date: 1/30/21
 * @author felix
 */
class ExceptionsPreference : Preference {
    constructor(context: Context): super(context)
    constructor(context: Context, attributeSet: AttributeSet): super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int): super(context, attributeSet, defStyleAttr)
    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int, defStyleRes: Int): super(context, attributeSet, defStyleAttr, defStyleRes)

    init {
        title = "$title…"
    }

    override fun onClick() {
        super.onClick()
        (context as MainActivity).findNavController(R.id.nav_host_fragment).navigate(
            R.id.appExceptionsListFragment_,
            AppExceptionsListFragment_.builder().exceptionsSetKey(key).build().arguments
        )
    }

    override fun getSummary(): CharSequence {
        return super.getSummary().toString().format(sharedPreferences.getStringSet(key, emptySet())!!.size)
    }
}