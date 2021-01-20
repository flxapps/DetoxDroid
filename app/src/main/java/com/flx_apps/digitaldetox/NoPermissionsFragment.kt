package com.flx_apps.digitaldetox

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.stericson.RootShell.RootShell
import com.stericson.RootShell.execution.Command
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.ViewById


@EFragment(R.layout.fragment_no_permissions)
open class NoPermissionsFragment : Fragment() {
    @ViewById
    lateinit var txtHint: TextView

    @ViewById
    lateinit var btnGrantPermissions: Button

    @AfterViews
    fun init() {
        if (RootShell.isRootAvailable()) {
            txtHint.text = getString(R.string.noPermissions_text_rooted)
            btnGrantPermissions.visibility = View.VISIBLE
        }
    }

    @Click
    fun btnGrantPermissionsClicked() {
        if (RootShell.isAccessGiven()) {
            RootShell.getShell(true).add(GrantPermissionsRootCommand())
        }
    }

    inner class GrantPermissionsRootCommand : Command(0, "pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS") {
        override fun commandCompleted(id: Int, exitcode: Int) {
            super.commandCompleted(id, exitcode)
            log("commandCompleted=$exitcode")
            fragmentManager!!.beginTransaction()
                .replace(R.id.nav_host_fragment, HomeFragment_.builder().build())
                .commitNowAllowingStateLoss()
        }
    }
}