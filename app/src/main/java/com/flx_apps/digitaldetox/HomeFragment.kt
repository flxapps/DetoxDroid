package com.flx_apps.digitaldetox

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Button
import android.widget.NumberPicker
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    lateinit var btnToggleGrayscale: HomeFragmentCardView

    @Pref
    lateinit var prefs: Prefs_

    @AfterViews
    fun init() {
        if (context!!.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            fragmentManager!!.beginTransaction()
                .replace(R.id.nav_host_fragment, NoPermissionsFragment())
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

        val requestPermission = prefs.zenModeDefaultEnabled().get() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !(context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
        if (requestPermission) {
            btnToggleZenModeDefault.isChecked = false
            prefs.edit().zenModeDefaultEnabled().put(false).apply()
            Snackbar.make(
                btnToggleZenModeDefault,
                R.string.action_requestPermissions,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.action_go) {
                    startActivityForResult(
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                        0
                    )
                }
                .show()
        }
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
            DetoxUtil.setGrayscale(context!!, btnToggleGrayscale.isChecked && !prefs.grayscaleExceptions().get().contains(context!!.packageName))
        }
    }

    @Click
    fun btnManageGrayscaleExceptionsClicked() {
        fragmentManager?.beginTransaction()
            ?.replace(R.id.nav_host_fragment, AppExceptionsListFragment_.builder().build())
            ?.addToBackStack(javaClass.name)?.commit()
    }

    @Click(R.id.btnSetPauseDuration, R.id.btnSetTimeBetweenPauses)
    fun btnConfigurePauseClicked(btn: Button) {
        val numberPicker = NumberPicker(context).apply {
            minValue = if (btn.id == R.id.btnSetPauseDuration) 1 else 0
            maxValue = 60
            value = if (btn.id == R.id.btnSetPauseDuration) prefs.pauseDuration().get() else prefs.timeBetweenPauses().get()
        }
        MaterialAlertDialogBuilder(context!!).apply {
            setTitle(btn.text)
            setView(numberPicker)
            setPositiveButton(R.string.action_save) { _, _ ->
                val editorField = when (btn.id) {
                    R.id.btnSetPauseDuration -> prefs.edit().pauseDuration()
                    else -> prefs.edit().timeBetweenPauses()
                }
                editorField.put(numberPicker.value).apply()
            }
            setNegativeButton(R.string.action_cancel, null)
        }.show()
    }

    @Click
    fun btnToggleDetoxClicked() {
        prefs.edit().isRunning.put(!prefs.isRunning().get()).apply()
        setDetoxIsActive(prefs.isRunning().get())
    }

    fun setDetoxIsActive(isActive: Boolean) {
        btnToggleDetox.setImageDrawable(ContextCompat.getDrawable(context!!, if (isActive) R.drawable.ic_pause else R.drawable.ic_play))
        DetoxUtil.setGrayscale(context!!, isActive)

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
            context!!.startService(Intent(context, DetoxAccessibilityService_::class.java))
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