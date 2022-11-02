package com.flx_apps.digitaldetox

import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.stericson.RootShell.RootShell
import com.stericson.RootShell.execution.Command
import kotlinx.android.synthetic.main.fragment_no_permissions.*
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.FragmentArg


@EFragment(R.layout.fragment_no_permissions)
open class NoPermissionsFragment : Fragment() {
    /**
     * root command, if the required permissions can be granted on rooted devices
     */
    @FragmentArg
    lateinit var rootCommand: String

    @AfterViews
    fun init() {
        if (this::rootCommand.isInitialized) {
            if (RootShell.isRootAvailable()) {
                txtHint.text = getString(R.string.noPermissions_text_rooted)
                btnGrantPermissions.visibility = View.VISIBLE
            }
            txtAdbCommand.text = "adb shell $rootCommand"
        }
    }

    @Click
    fun btnGrantPermissionsClicked() {
        if (RootShell.isAccessGiven()) {
            RootShell.getShell(true).add(GrantPermissionsRootCommand(rootCommand))
        }
    }

    inner class GrantPermissionsRootCommand(rootCommand: String) : Command(0, rootCommand) {
        override fun commandCompleted(id: Int, exitcode: Int) {
            super.commandCompleted(id, exitcode)
            log("commandCompleted=$exitcode")
            findNavController().navigateUp()
        }
    }
}