package com.flx_apps.digitaldetox

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.flx_apps.digitaldetox.prefs.Prefs_
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.ViewById
import org.androidannotations.annotations.sharedpreferences.Pref


@EFragment(R.layout.fragment_home)
open class HomeFragment : Fragment() {
    private val ACCESSIBILITY_SERVICE_COMPONENT = DetoxAccessibilityService_::class.java.`package`!!.name + "/" + DetoxAccessibilityService_::class.java.name

    @ViewById
    lateinit var btnToggleDetox: FloatingActionButton

    @ViewById
    lateinit var btnToggleZenModeDefault: HomeFragmentCardView

    @ViewById
    lateinit var btnToggleBreakDoomScrolling: HomeFragmentCardView

    @ViewById
    lateinit var btnToggleGrayscale: HomeFragmentCardView

    @Pref
    lateinit var prefs: Prefs_

    @AfterViews
    fun init() {
        if (requireContext().checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            MainActivity.hasWriteSecureSettingsPermission = false
            log("Showing NoPermissionsFragment...")
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, NoPermissionsFragment_.builder().build())
                .commit()
            return
        }

        setDetoxIsActive(prefs.isRunning.get())
    }

    @AfterViews
    @Click
    fun btnToggleZenModeDefaultClicked() {
        if (btnToggleZenModeDefault.isPressed) {
            prefs.edit().zenModeDefaultEnabled().put(!prefs.zenModeDefaultEnabled().get()).apply()
        }
        btnToggleZenModeDefault.isChecked = prefs.zenModeDefaultEnabled().get()

        if (
            prefs.zenModeDefaultEnabled().get() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !(requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
        ) {
            btnToggleZenModeDefault.isChecked = false
            prefs.edit().zenModeDefaultEnabled().put(false).apply()
            showAskForPermissionsSnackbar(
                btnToggleZenModeDefault,
                Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            )
        }
    }

    @AfterViews
    @Click
    fun btnToggleGrayscaleClicked() {
        if (btnToggleGrayscale.isPressed) {
            prefs.edit().grayscaleEnabled().put(!prefs.grayscaleEnabled().get()).apply()
        }
        btnToggleGrayscale.isChecked = prefs.grayscaleEnabled().get()
        DetoxUtil.setGrayscale(
            requireContext(),
            prefs.isRunning().get() && btnToggleGrayscale.isChecked && !prefs.grayscaleExceptions()
                .get().contains(
                requireContext().packageName
            ),
            forceSetting = true
        )
    }

    @AfterViews
    @Click
    fun btnToggleBreakDoomScrollingClicked() {
        if (btnToggleBreakDoomScrolling.isPressed) {
            prefs.edit().breakDoomScrollingEnabled().put(!prefs.breakDoomScrollingEnabled().get()).apply()
        }
        btnToggleBreakDoomScrolling.isChecked = prefs.breakDoomScrollingEnabled().get()

        if (
            prefs.breakDoomScrollingEnabled().get() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(requireContext())
        ) {
            btnToggleBreakDoomScrolling.isChecked = false
            prefs.edit().breakDoomScrollingEnabled().put(false).apply()
            showAskForPermissionsSnackbar(
                btnToggleBreakDoomScrolling,
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            )
        }
    }

    @Click
    fun btnToggleDetoxClicked() {
        prefs.edit().isRunning.put(!prefs.isRunning().get()).apply()
        setDetoxIsActive(prefs.isRunning().get())
    }

    private fun showAskForPermissionsSnackbar(view: View, intentAction: String) {
        Snackbar.make(view, R.string.action_requestPermissions, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.action_go) {
                startActivityForResult(
                    Intent(intentAction),
                    0
                )
            }
            .show()
    }

    private fun setDetoxIsActive(isActive: Boolean) {
        btnToggleDetox.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                if (isActive) R.drawable.ic_pause else R.drawable.ic_play
            )
        )
        DetoxUtil.setGrayscale(requireContext(), isActive)

        val contentResolver = context?.contentResolver

        // (de-)activate accessibility service
        val accessibilityServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        if (isActive && !accessibilityServices.split(":")
                .contains(ACCESSIBILITY_SERVICE_COMPONENT)
        ) {
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                "$accessibilityServices:$ACCESSIBILITY_SERVICE_COMPONENT".trim(':')
            )
            Settings.Secure.putString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            requireContext().startService(Intent(context, DetoxAccessibilityService_::class.java))
        } else if (!isActive && accessibilityServices.split(":")
                .contains(ACCESSIBILITY_SERVICE_COMPONENT)
        ) {
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                accessibilityServices.replace(ACCESSIBILITY_SERVICE_COMPONENT, "").trim(':')
            )

        }
    }
}