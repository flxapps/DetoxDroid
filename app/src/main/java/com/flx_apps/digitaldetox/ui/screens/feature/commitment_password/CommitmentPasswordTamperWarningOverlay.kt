package com.flx_apps.digitaldetox.ui.screens.feature.commitment_password

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.OverlayContent
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What someone tried while the settings were locked, with the message that says it can't be done.
 * The overlay shows it, or a toast where DetoxDroid may not draw over other apps.
 */
enum class CommitmentPasswordTamperType(val value: String, val messageRes: Int) {
    Uninstall("uninstall", R.string.feature_commitmentPassword_tamper_overlay_message_uninstall),
    DeviceAdmin(
        "device_admin", R.string.feature_commitmentPassword_tamper_overlay_message_deviceAdmin
    ),
    Accessibility(
        "accessibility", R.string.feature_commitmentPassword_tamper_overlay_message_accessibility
    );

    companion object {
        fun from(value: String?): CommitmentPasswordTamperType =
            values().firstOrNull { it.value == value } ?: Uninstall
    }
}

class CommitmentPasswordTamperWarningOverlayService :
    OverlayService(OverlayContent { CommitmentPasswordTamperWarningOverlay() }) {
    companion object {
        const val EXTRA_TAMPER_TYPE = "tamperType"

        private val _tamperType = MutableStateFlow(CommitmentPasswordTamperType.Uninstall)
        val tamperType = _tamperType.asStateFlow()

        fun createStartIntent(
            context: Context,
            tamperType: CommitmentPasswordTamperType
        ) = android.content.Intent(context, CommitmentPasswordTamperWarningOverlayService::class.java).apply {
            putExtra(EXTRA_TAMPER_TYPE, tamperType.value)
        }
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        _tamperType.value = CommitmentPasswordTamperType.from(intent?.getStringExtra(EXTRA_TAMPER_TYPE))
        return super.onStartCommand(intent, flags, startId)
    }
}

@Preview
@Composable
fun CommitmentPasswordTamperWarningOverlay() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tamperType: State<CommitmentPasswordTamperType> =
        CommitmentPasswordTamperWarningOverlayService.tamperType.collectAsState()

    DetoxDroidTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // laid out like the app's dialogs, the one thing in red being the icon
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.feature_commitmentPassword_tamper_overlay_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = stringResource(id = tamperType.value.messageRes) + " " +
                                stringResource(R.string.feature_commitmentPassword_tamper_overlay_reassurance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        onClick = { (context as OverlayService).closeOverlay() }
                    ) {
                        Text(text = stringResource(id = R.string.feature_commitmentPassword_tamper_overlay_action_backHome))
                    }
                }
            }
        }
    }
}
