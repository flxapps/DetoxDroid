package com.flx_apps.digitaldetox.ui.screens.feature.commitment_password

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.Context
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.OverlayContent
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CommitmentPasswordTamperType(
    val value: String,
    val overlayMessageRes: Int,
    val toastMessageRes: Int
) {
    Uninstall(
        "uninstall",
        R.string.feature_commitmentPassword_tamper_overlay_message_uninstall,
        R.string.feature_commitmentPassword_tamper_toast_uninstall
    ),
    DeviceAdmin(
        "device_admin",
        R.string.feature_commitmentPassword_tamper_overlay_message_deviceAdmin,
        R.string.feature_commitmentPassword_tamper_toast_deviceAdmin
    ),
    Accessibility(
        "accessibility",
        R.string.feature_commitmentPassword_tamper_overlay_message_accessibility,
        R.string.feature_commitmentPassword_tamper_toast_accessibility
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.feature_commitmentPassword_tamper_iconDescription),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = stringResource(R.string.feature_commitmentPassword_tamper_overlay_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(id = tamperType.value.overlayMessageRes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(id = R.string.feature_commitmentPassword_tamper_overlay_reassurance),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.92f)
                    )
                    Spacer(modifier = Modifier.size(20.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { (context as OverlayService).closeOverlay() }
                    ) {
                        Text(text = stringResource(id = R.string.feature_commitmentPassword_tamper_overlay_action_backHome))
                    }
                }
            }
        }
    }
}
