package com.flx_apps.digitaldetox

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.ViewById
import org.androidannotations.annotations.sharedpreferences.Pref

@EFragment(R.layout.fragment_home)
open class HomeFragment : Fragment() {
    private val ACCESSIBILITY_SERVICE_COMPONENT = "com.flx_apps.digitaldetox/com.flx_apps.digitaldetox.DigitalDetoxAccessibilityService_"
    private val NOTIFICATION_SERVICE_COMPONENT = "com.flx_apps.digitaldetox/com.flx_apps.digitaldetox.DigitalDetoxNotificationListenerService"

    @ViewById
    lateinit var btnToggleDetox: FloatingActionButton

    @ViewById
    lateinit var btnToggleZenModeDefault: HomeFragmentCardView

    @ViewById
    lateinit var btnToggleGrayscale: HomeFragmentCardView

    @Pref
    lateinit var prefs: Prefs_

    var hasPermission = false

    @AfterViews
    fun init() {
        if (context?.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(
                activity?.findViewById(android.R.id.content)!!,
                R.string.home_noPermissions,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.home_noPermissions_how) {
                    startActivityForResult(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 0)
                }
                .show()
        }

        setDetoxIsActive(prefs.isRunning().get())
    }

    @AfterViews
    @Click
    fun btnToggleZenModeDefaultClicked() {
        if (btnToggleZenModeDefault.isPressed) {
            prefs.edit().zenModeDefaultEnabled().put(!prefs.zenModeDefaultEnabled().get()).apply()
        }
        btnToggleZenModeDefault.isChecked = prefs.zenModeDefaultEnabled().get()
    }

    @Click
    fun btnConfigZenModeClicked() {
        startActivity(Intent("android.settings.ZEN_MODE_PRIORITY_SETTINGS"))
    }

    @AfterViews
    @Click
    fun btnToggleGrayscaleClicked() {
        if (btnToggleGrayscale.isPressed) {
            prefs.edit().grayscaleEnabled().put(!prefs.grayscaleEnabled().get()).apply()
        }
        btnToggleGrayscale.isChecked = prefs.grayscaleEnabled().get()
        if (prefs.isRunning.get()) {
            AccessibilityUtil.setGrayscale(context!!, btnToggleGrayscale.isChecked)
        }
    }

    @Click
    fun btnManageGrayscaleExceptionsClicked() {
        fragmentManager?.beginTransaction()
            ?.replace(R.id.nav_host_fragment, ExceptionsListFragment_.builder().build())
            ?.addToBackStack(javaClass.name)?.commit()
    }

    @Click
    fun btnToggleDetoxClicked() {
        prefs.edit().isRunning.put(!prefs.isRunning().get()).apply()
        setDetoxIsActive(prefs.isRunning().get())
    }

    fun setDetoxIsActive(isActive: Boolean) {
        btnToggleDetox.setImageDrawable(ContextCompat.getDrawable(context!!, if (isActive) R.drawable.ic_pause else R.drawable.ic_play))
        AccessibilityUtil.setGrayscale(context!!, isActive)

        if (hasPermission) {
            val contentResolver = context?.contentResolver

            // (de-)activate accessibility service
            val accessibilityServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            if (isActive && !accessibilityServices.split(":").contains(ACCESSIBILITY_SERVICE_COMPONENT)) {
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "$accessibilityServices:ACCESSIBILITY_SERVICE_COMPONENT".trim(':')
                )
            }
            else if (!isActive && accessibilityServices.split(":").contains(ACCESSIBILITY_SERVICE_COMPONENT)) {
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, accessibilityServices.replace(ACCESSIBILITY_SERVICE_COMPONENT, "").trim(':')
                )
            }
        }
    }
}