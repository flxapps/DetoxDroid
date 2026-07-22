package com.flx_apps.digitaldetox.ui.screens.shizuku_setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.widgets.IconCard
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.ShizukuUtils
import com.flx_apps.digitaldetox.util.observeAsState
import dev.olshevski.navigation.reimagined.hilt.hiltViewModel

/**
 * Guided, computer-free setup for the WRITE_SECURE_SETTINGS permission via Shizuku: install →
 * start via wireless debugging → allow DetoxDroid → one-tap grant. The wizard advances by itself
 * (the view model polls the Shizuku state) and finishes any pending grayscale activation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuSetupScreen(
    viewModel: ShizukuSetupViewModel = hiltViewModel(),
    navViewModel: NavViewModel = NavViewModel.navViewModel()
) {
    val context = LocalContext.current
    val setupState by viewModel.setupState.collectAsState()
    val isRootAvailable by viewModel.isRootAvailable.collectAsState()
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.Event.ON_RESUME) viewModel.refreshState()
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(id = R.string.shizukuSetup_title)) },
            navigationIcon = {
                IconButton(onClick = { navViewModel.onBackPress() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.action_back)
                    )
                }
            })
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (setupState == ShizukuSetupState.COMPLETED) {
                IconCard(icon = Icons.Default.CheckCircle) {
                    Text(
                        text = stringResource(id = R.string.shizukuSetup_completed_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.shizukuSetup_completed_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = { navViewModel.onBackPress() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(stringResource(id = R.string.shizukuSetup_completed_done))
                }
                return@Column
            }

            Text(
                text = stringResource(id = R.string.shizukuSetup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isRootAvailable) {
                IconCard(icon = Icons.Default.Tag) {
                    Text(
                        text = stringResource(id = R.string.shizukuSetup_rooted),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { viewModel.grantViaRoot() }) {
                        Text(stringResource(id = R.string.shizukuSetup_rooted_go))
                    }
                }
            }

            WizardStep(
                number = 1,
                title = stringResource(id = R.string.shizukuSetup_step1_title),
                description = stringResource(id = R.string.shizukuSetup_step1_description),
                done = setupState != ShizukuSetupState.NOT_INSTALLED,
                active = setupState == ShizukuSetupState.NOT_INSTALLED
            ) {
                Button(onClick = { openShizukuPlayStorePage(context) }) {
                    Text(stringResource(id = R.string.shizukuSetup_step1_go))
                }
            }
            WizardStep(
                number = 2,
                title = stringResource(id = R.string.shizukuSetup_step2_title),
                description = stringResource(id = R.string.shizukuSetup_step2_description) + "\n\n" + stringResource(
                    id = R.string.shizukuSetup_step2_rebootHint
                ),
                done = setupState in listOf(
                    ShizukuSetupState.RUNNING_NOT_GRANTED, ShizukuSetupState.READY
                ),
                active = setupState == ShizukuSetupState.INSTALLED_NOT_RUNNING
            ) {
                Row {
                    OutlinedButton(onClick = { openShizukuApp(context) }) {
                        Text(stringResource(id = R.string.shizukuSetup_step2_openShizuku))
                    }
                    OutlinedButton(
                        onClick = { NavigationUtil.openDeveloperSettings(context) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(stringResource(id = R.string.shizukuSetup_step2_openDeveloperSettings))
                    }
                }
            }
            WizardStep(
                number = 3,
                title = stringResource(id = R.string.shizukuSetup_step3_title),
                description = stringResource(id = R.string.shizukuSetup_step3_description),
                done = setupState == ShizukuSetupState.READY,
                active = setupState == ShizukuSetupState.RUNNING_NOT_GRANTED
            ) {
                Button(onClick = { viewModel.requestShizukuAccess() }) {
                    Text(stringResource(id = R.string.shizukuSetup_step3_go))
                }
            }
            WizardStep(
                number = 4,
                title = stringResource(id = R.string.shizukuSetup_step4_title),
                description = stringResource(id = R.string.shizukuSetup_step4_description),
                done = false,
                active = setupState == ShizukuSetupState.READY
            ) {
                Button(onClick = { viewModel.grantViaShizuku() }) {
                    Text(stringResource(id = R.string.shizukuSetup_step4_go))
                }
            }
        }
    }
}

/**
 * One numbered card of the wizard. The active step is highlighted and shows its action buttons,
 * completed steps get a checkmark, upcoming steps are dimmed.
 */
@Composable
private fun WizardStep(
    number: Int,
    title: String,
    description: String,
    done: Boolean,
    active: Boolean,
    actions: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (done || active) 1f else 0.5f),
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (done) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = number.toString(),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            if (active) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                actions()
            }
        }
    }
}

private fun openShizukuPlayStorePage(context: Context) {
    val playIntent = Intent(
        Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuUtils.SHIZUKU_PACKAGE_NAME}")
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { context.startActivity(playIntent) }.onFailure {
        // no Play Store — fall back to the browser (which may be missing too on exotic setups)
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuUtils.SHIZUKU_PACKAGE_NAME}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
}

private fun openShizukuApp(context: Context) {
    // the manager package is the legacy install variant that isShizukuInstalled also accepts
    val launchIntent = context.packageManager.getLaunchIntentForPackage(
        ShizukuUtils.SHIZUKU_PACKAGE_NAME
    ) ?: context.packageManager.getLaunchIntentForPackage(ShizukuUtils.SHIZUKU_MANAGER_PACKAGE_NAME)
    launchIntent?.let { runCatching { context.startActivity(it) } }
}
