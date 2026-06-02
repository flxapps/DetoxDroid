package com.flx_apps.digitaldetox.ui.screens.device_admin_revoked

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import com.flx_apps.digitaldetox.util.NavigationUtil
import timber.log.Timber

/**
 * Full-screen activity shown when Device Admin is revoked or the Accessibility Service is disabled
 * while Commitment Password is active. Forces the user to acknowledge the decision by typing a
 * specific phrase before dismissing, and optionally re-enables the accessibility service
 * automatically if WRITE_SECURE_SETTINGS is available.
 */
class DeviceAdminRevokedWarningActivity : ComponentActivity() {

    enum class WarningReason(val value: String) {
        DEVICE_ADMIN_REVOKED("device_admin_revoked"),
        ACCESSIBILITY_DISABLED("accessibility_disabled");

        companion object {
            fun from(value: String?): WarningReason =
                values().firstOrNull { it.value == value } ?: DEVICE_ADMIN_REVOKED
        }
    }

    companion object {
        private const val PREFS_NAME = "device_admin_revoked"
        private const val KEY_WARNING_REQUIRED = "warning_required"
        private const val KEY_WARNING_REASON = "warning_reason"
        private const val EXTRA_WARNING_REASON = "warning_reason"

        fun launch(context: Context, reason: WarningReason = getWarningReason(context)) {
            context.startActivity(Intent(context, DeviceAdminRevokedWarningActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                putExtra(EXTRA_WARNING_REASON, reason.value)
            })
        }

        fun setWarningRequired(context: Context, required: Boolean, reason: WarningReason = getWarningReason(context)) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_WARNING_REQUIRED, required)
                .putString(KEY_WARNING_REASON, reason.value)
                .apply()
        }

        fun isWarningRequired(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_WARNING_REQUIRED, false)

        fun getWarningReason(context: Context): WarningReason =
            WarningReason.from(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_WARNING_REASON, WarningReason.DEVICE_ADMIN_REVOKED.value)
            )

        fun requireDeviceAdminWarning(context: Context) {
            setWarningRequired(context, true, WarningReason.DEVICE_ADMIN_REVOKED)
        }

        fun requireAccessibilityWarning(context: Context) {
            // Don't overwrite a pending device admin warning
            if (getWarningReason(context) == WarningReason.DEVICE_ADMIN_REVOKED && isWarningRequired(context)) return
            setWarningRequired(context, true, WarningReason.ACCESSIBILITY_DISABLED)
        }

        fun shouldShowWarning(context: Context, reason: WarningReason = getWarningReason(context)): Boolean {
            if (!isWarningRequired(context) || !CommitmentPasswordFeature.isActivated) return false
            return when (reason) {
                WarningReason.DEVICE_ADMIN_REVOKED ->
                    !DetoxDroidDeviceAdminReceiver.hasDeviceAdminPermission(context)
                WarningReason.ACCESSIBILITY_DISABLED ->
                    !isAccessibilityServiceEnabled(context)
            }
        }

        private fun accessibilityServiceComponent(context: Context): String =
            "${context.packageName}/${DetoxDroidAccessibilityService::class.java.name}"

        private fun hasWriteSecureSettingsPermission(context: Context): Boolean =
            context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
                    PackageManager.PERMISSION_GRANTED

        private fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabled.split(':').any { it == accessibilityServiceComponent(context) }
        }

        private fun tryEnableAccessibilityService(context: Context): Boolean {
            val current = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val updated = current.split(':')
                .filter { it.isNotBlank() }
                .toMutableSet()
                .apply { add(accessibilityServiceComponent(context)) }
                .joinToString(":")

            val wrote = Settings.Secure.putString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated
            )
            val enabledWrote = Settings.Secure.putString(
                context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1"
            )
            if (!wrote || !enabledWrote) return false
            context.startService(Intent(context, DetoxDroidAccessibilityService::class.java))
            return isAccessibilityServiceEnabled(context)
        }
    }

    private var acknowledged = false
    private lateinit var warningReason: WarningReason

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        warningReason = WarningReason.from(intent.getStringExtra(EXTRA_WARNING_REASON))

        // For accessibility, try to auto re-enable if we have WRITE_SECURE_SETTINGS
        if (warningReason == WarningReason.ACCESSIBILITY_DISABLED && hasWriteSecureSettingsPermission(this)) {
            val reEnabled = runCatching { tryEnableAccessibilityService(this) }.getOrDefault(false)
            if (reEnabled) {
                setWarningRequired(this, false, warningReason)
                acknowledged = true
                finish()
                return
            }
        }

        if (!shouldShowWarning(this, warningReason)) {
            setWarningRequired(this, false, warningReason)
            acknowledged = true
            finish()
            return
        }

        setFinishOnTouchOutside(false)
        setWarningRequired(this, true, warningReason)

        setContent {
            DetoxDroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeviceAdminRevokedWarningScreen(
                        warningReason = warningReason,
                        onAcknowledge = {
                            acknowledged = true
                            setWarningRequired(this, false, warningReason)
                            when (warningReason) {
                                WarningReason.DEVICE_ADMIN_REVOKED -> {
                                    runCatching {
                                        startActivity(
                                            DetoxDroidDeviceAdminReceiver.createRequestDeviceAdminIntent(
                                                this,
                                                getString(R.string.deviceAdminRevoked_reenable_explanation)
                                            )
                                        )
                                    }.onFailure { Timber.e(it, "Failed to open Device Admin request intent") }
                                }
                                WarningReason.ACCESSIBILITY_DISABLED ->
                                    NavigationUtil.openAccessibilitySettings(this)
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun finish() {
        if (acknowledged) super.finish()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!acknowledged && shouldShowWarning(this, warningReason)) {
            launch(this, warningReason)
        }
    }
}

@Composable
private fun DeviceAdminRevokedWarningScreen(
    warningReason: DeviceAdminRevokedWarningActivity.WarningReason,
    onAcknowledge: () -> Unit,
) {
    val requiredPhrase = stringResource(R.string.deviceAdminRevoked_requiredPhrase)
    var input by remember { mutableStateOf("") }
    val canContinue = input.trim() == requiredPhrase

    BackHandler(enabled = true) { /* consume – user must type the phrase */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.feature_commitmentPassword_tamper_iconDescription),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = when (warningReason) {
                        DeviceAdminRevokedWarningActivity.WarningReason.DEVICE_ADMIN_REVOKED ->
                            stringResource(R.string.deviceAdminRevoked_title)
                        DeviceAdminRevokedWarningActivity.WarningReason.ACCESSIBILITY_DISABLED ->
                            stringResource(R.string.accessibilityDisabledWarning_title)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (warningReason) {
                        DeviceAdminRevokedWarningActivity.WarningReason.DEVICE_ADMIN_REVOKED ->
                            stringResource(R.string.deviceAdminRevoked_message)
                        DeviceAdminRevokedWarningActivity.WarningReason.ACCESSIBILITY_DISABLED ->
                            stringResource(R.string.accessibilityDisabledWarning_message)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.deviceAdminRevoked_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = requiredPhrase,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.deviceAdminRevoked_inputLabel)) },
                    singleLine = false,
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onErrorContainer,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                        focusedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                        cursorColor = MaterialTheme.colorScheme.onErrorContainer,
                        focusedTextColor = MaterialTheme.colorScheme.onErrorContainer,
                        unfocusedTextColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAcknowledge,
                    enabled = canContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onErrorContainer,
                        contentColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(stringResource(R.string.deviceAdminRevoked_continue))
                }
            }
        }
    }
}
