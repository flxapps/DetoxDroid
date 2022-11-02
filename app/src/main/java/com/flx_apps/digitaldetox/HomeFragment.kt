package com.flx_apps.digitaldetox

import android.app.NotificationManager
import android.content.Context
import android.content.Context.RESTRICTIONS_SERVICE
import android.content.Intent
import android.content.RestrictionsManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.flx_apps.digitaldetox.prefs.Prefs_
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_home.*
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EFragment
import java.util.*


@EFragment(R.layout.fragment_home)
open class HomeFragment : Fragment() {
    private val ACCESSIBILITY_SERVICE_COMPONENT = DetoxAccessibilityService_::class.java.`package`!!.name + "/" + DetoxAccessibilityService_::class.java.name
    lateinit var prefs: Prefs_

    @AfterViews
    fun init() {
        prefs = Prefs_(context)

        val TYPE_DELEGATION = "com.oasisfeng.island.delegation";
        val DELEGATION_APP_OPS = "-island-delegation-app-ops";

        checkAndRequestIslandPermission(requireContext());
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    open fun checkAndRequestIslandPermission(context: Context): Boolean {
        val TYPE_DELEGATION = "com.oasisfeng.island.delegation"
        val DELEGATION_APP_OPS = "-island-delegation-app-ops"
        val DELEGATION_PACKAGE_ACCESS = "delegation-package-access"
        val rm = context.getSystemService(RESTRICTIONS_SERVICE) as RestrictionsManager
        if (rm != null && rm.hasRestrictionsProvider()) { // Otherwise, current user is not managed by Island or the version of Island is too low.
            val delegations = rm.applicationRestrictions.getStringArray(TYPE_DELEGATION)
            if (delegations == null || !Arrays.asList(delegations)
                    .contains(DELEGATION_PACKAGE_ACCESS)
            ) {
                val request = PersistableBundle()
                request.putString(RestrictionsManager.REQUEST_KEY_DATA, DELEGATION_PACKAGE_ACCESS)
                rm.requestPermission(
                    TYPE_DELEGATION,
                    "com.flx_apps.detoxdroid.app-ops",
                    request
                )
            } else {
                return true
            }
        }
        return false
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
            showAskForPermissionsSnackbar(btnToggleZenModeDefault, {
                startActivityForResult(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), 0)
            })
        }
    }

    @AfterViews
    @Click
    fun btnToggleDeactivateAppsClicked() {
        if (btnToggleDeactivateApps.isPressed) {
            prefs.edit().deactivateAppsEnabled().put(!prefs.deactivateAppsEnabled().get()).apply()
        }
        btnToggleDeactivateApps.isChecked = prefs.deactivateAppsEnabled().get()

        if (
            prefs.deactivateAppsEnabled().get() &&
            !DetoxDroidDeviceAdminReceiver.isGranted(requireContext())
        ) {
            btnToggleDeactivateApps.isChecked = false
            prefs.edit().deactivateAppsEnabled().put(false).apply()
            showAskForPermissionsSnackbar(btnToggleZenModeDefault, {
                findNavController().navigate(
                    R.id.nav_no_permissions,
                    bundleOf("rootCommand" to "dpm set-device-owner com.flx_apps.digitaldetox/.DetoxDroidDeviceAdminReceiver")
                )
            })
        }
        else {
            DetoxUtil.setAppsDeactivated(requireContext(), prefs.isRunning().get() && prefs.deactivateAppsEnabled().get(), forceSetting = true)
        }
    }

    @AfterViews
    @Click
    fun btnToggleGrayscaleClicked() {
        if (btnToggleGrayscale.isPressed) {
            prefs.edit().grayscaleEnabled().put(!prefs.grayscaleEnabled().get()).apply()
        }

        btnToggleGrayscale.isChecked = prefs.grayscaleEnabled().get()

        if (
            prefs.grayscaleEnabled().get() &&
            requireContext().checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED
        ) {
            btnToggleGrayscale.isChecked = false
            prefs.edit().grayscaleEnabled().put(false).apply()
            showAskForPermissionsSnackbar(btnToggleGrayscale, {
                findNavController().navigate(
                    R.id.nav_no_permissions,
                    bundleOf("rootCommand" to "pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS")
                )
            })
            return
        }

        DetoxUtil.setGrayscale(
            requireContext(),
            prefs.isRunning().get() && btnToggleGrayscale.isChecked && !prefs.grayscaleExceptions().get().contains(requireContext().packageName),
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
            showAskForPermissionsSnackbar(btnToggleBreakDoomScrolling, {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), 0)
            })
        }
    }

    @Click
    @AfterViews
    fun btnToggleDetoxClicked() {
        var isRunning = prefs.isRunning().get()
        if (btnToggleDetox.isPressed) {
            isRunning = !isRunning
        }

        val contentResolver = context?.contentResolver
        val accessibilityServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val accessibilityServiceIsActivated = accessibilityServices.split(":").contains(ACCESSIBILITY_SERVICE_COMPONENT)

        if (requireActivity().checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED) {
            // we can (de-)activate the accessibility service programmatically, so let's do so
            val accessibilityServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            if (isRunning && !accessibilityServiceIsActivated) {
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    "$accessibilityServices:$ACCESSIBILITY_SERVICE_COMPONENT".trim(':')
                )
                Settings.Secure.putString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                requireContext().startService(Intent(context, DetoxAccessibilityService_::class.java))
            }
            else if (!isRunning && accessibilityServiceIsActivated) {
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    accessibilityServices.replace(ACCESSIBILITY_SERVICE_COMPONENT, "").trim(':')
                )
            }
        }
        else if (isRunning && !accessibilityServiceIsActivated) {
            // we need to ask the user to activate the accessibility service manually
            showAskForPermissionsSnackbar(btnToggleDetox, {
                startActivityForResult(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 0)
            })
            isRunning = false
        }

        btnToggleDetox.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                if (isRunning) R.drawable.ic_pause else R.drawable.ic_play
            )
        )
        DetoxUtil.setActive(requireContext(), isRunning, grayscale = isRunning && !prefs.grayscaleExceptions().get().contains(requireContext().packageName))
        prefs.edit().isRunning().put(isRunning).apply()
    }

    private fun showAskForPermissionsSnackbar(view: View, onClickListener: View.OnClickListener, btnText: String = getString(R.string.action_go)) {
        Snackbar.make(view, R.string.action_requestPermissions, Snackbar.LENGTH_INDEFINITE)
            .setAction(btnText, onClickListener)
            .show()
    }
}