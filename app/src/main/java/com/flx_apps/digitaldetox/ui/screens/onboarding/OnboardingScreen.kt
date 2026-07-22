package com.flx_apps.digitaldetox.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.widgets.InfoCard
import com.flx_apps.digitaldetox.util.DistractingAppsHeuristic
import com.flx_apps.digitaldetox.util.toHrMinString
import dev.olshevski.navigation.reimagined.hilt.hiltViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The onboarding flow for new users: identifies distracting apps, lets the user pick an intensity
 * preset and requests the required permissions. Every step is skippable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    navViewModel: NavViewModel = NavViewModel.navViewModel()
) {
    val step by viewModel.step.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()
    var showSkipDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = step != OnboardingStep.WELCOME) {
        viewModel.previousStep()
    }

    if (showSkipDialog) {
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            title = { Text(stringResource(id = R.string.onboarding_skipSetup_dialog_title)) },
            text = { Text(stringResource(id = R.string.onboarding_skipSetup_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSkipDialog = false
                    viewModel.skipOnboarding()
                    navViewModel.exitOnboarding()
                }) {
                    Text(stringResource(id = R.string.onboarding_skipSetup_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            val stepNumber = step.ordinal + 1
            val stepCount = OnboardingStep.entries.size
            val progress by animateFloatAsState(
                targetValue = stepNumber.toFloat() / stepCount, label = "OnboardingProgress"
            )
            val progressDescription =
                stringResource(id = R.string.onboarding_progress_stepOf, stepNumber, stepCount)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .semantics { contentDescription = progressDescription }
            )
        }, actions = {
            if (step != OnboardingStep.DONE) {
                TextButton(onClick = { showSkipDialog = true }) {
                    Text(stringResource(id = R.string.onboarding_action_skipSetup))
                }
            }
        })
    }, bottomBar = {
        OnboardingBottomBar(
            step = step,
            // don't let the user rush past the analysis with an empty selection
            nextEnabled = !(step == OnboardingStep.PICK_APPS && isLoadingApps),
            onBack = { viewModel.previousStep() },
            onNext = {
                if (step == OnboardingStep.DONE) {
                    // completeOnboarding is single-shot: a double-tap must not pop twice
                    if (viewModel.completeOnboarding()) navViewModel.exitOnboarding()
                } else {
                    viewModel.nextStep()
                }
            }
        )
    }) { paddingValues ->
        AnimatedContent(
            targetState = step,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            transitionSpec = {
                // slide forward or backward depending on the step direction
                val forward = targetState.ordinal >= initialState.ordinal
                val enter = slideInHorizontally { if (forward) it else -it } + fadeIn()
                val exit = slideOutHorizontally { if (forward) -it else it } + fadeOut()
                enter togetherWith exit
            },
            label = "OnboardingStep"
        ) { currentStep ->
            when (currentStep) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.USAGE_ACCESS -> UsageAccessStep(viewModel)
                OnboardingStep.PICK_APPS -> PickAppsStep(viewModel)
                OnboardingStep.PRESET -> PresetStep(viewModel)
                OnboardingStep.PERMISSIONS -> PermissionsStep(viewModel)
                OnboardingStep.DONE -> DoneStep(viewModel)
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    step: OnboardingStep, nextEnabled: Boolean, onBack: () -> Unit, onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (step != OnboardingStep.WELCOME) {
            TextButton(onClick = onBack) {
                Text(stringResource(id = R.string.action_back))
            }
        } else {
            Spacer(modifier = Modifier.size(1.dp)) // keeps the primary button right-aligned
        }
        Button(onClick = onNext, enabled = nextEnabled) {
            Text(
                stringResource(
                    id = when (step) {
                        OnboardingStep.WELCOME -> R.string.onboarding_action_getStarted
                        OnboardingStep.DONE -> R.string.onboarding_action_finish
                        else -> R.string.onboarding_action_next
                    }
                )
            )
        }
    }
}

/**
 * Headline plus short explanation, shared by all steps.
 */
@Composable
fun OnboardingStepHeader(title: String, message: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
    )
}

/**
 * Scrollable step layout with a headline and a short explanation, shared by all steps.
 */
@Composable
fun OnboardingStepColumn(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        OnboardingStepHeader(title = title, message = message)
        content()
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground_cropped),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
        Text(
            text = stringResource(id = R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = stringResource(id = R.string.onboarding_welcome_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
        InfoCard(infoText = stringResource(id = R.string.onboarding_welcome_privacy))
    }
}

@Composable
private fun DoneStep(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    val appRows by viewModel.appRows.collectAsState()
    val preset by viewModel.preset.collectAsState()
    val budgetMinutes by viewModel.budgetMinutes.collectAsState()
    val overlayGranted by viewModel.overlayGranted.collectAsState()
    val writeSecureSettingsGranted by viewModel.writeSecureSettingsGranted.collectAsState()
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsState()
    val selectedCount = appRows.count { it.checked }
    val budgetText = budgetMinutes.minutes.toHrMinString(context)
    // Strict staggers the thresholds: grayscale warns before apps are blocked at the full budget
    val grayscaleBudgetText = if (preset == OnboardingPreset.STRICT) {
        DistractingAppsHeuristic.strictGrayscaleBudgetMs(
            budgetMinutes.minutes.inWholeMilliseconds
        ).milliseconds.toHrMinString(context)
    } else {
        budgetText
    }
    // any feature that is configured but can't switch on yet because a permission is still missing
    val hasPendingPermission = selectedCount > 0 && (!overlayGranted ||
            (preset != OnboardingPreset.GENTLE && !writeSecureSettingsGranted))

    OnboardingStepColumn(
        title = stringResource(id = R.string.onboarding_done_title),
        message = stringResource(id = R.string.onboarding_done_message)
    ) {
        if (selectedCount == 0) {
            SummaryRow(
                icon = Icons.Default.Info,
                text = stringResource(id = R.string.onboarding_done_summary_nothing)
            )
        } else {
            // scroll breaks (needs the overlay permission to actually show a break screen)
            if (overlayGranted) {
                SummaryRow(
                    icon = Icons.Default.CheckCircle,
                    text = pluralStringResource(
                        id = R.plurals.onboarding_done_summary_scrollBreaks,
                        selectedCount,
                        selectedCount
                    )
                )
            } else {
                WarningRow(
                    text = stringResource(id = R.string.onboarding_done_summary_scrollBreaksPending)
                )
            }
            if (preset != OnboardingPreset.GENTLE) {
                if (writeSecureSettingsGranted) {
                    SummaryRow(
                        icon = Icons.Default.CheckCircle,
                        text = stringResource(
                            id = R.string.onboarding_done_summary_grayscale, grayscaleBudgetText
                        )
                    )
                } else {
                    WarningRow(
                        text = stringResource(id = R.string.onboarding_done_summary_grayscalePending)
                    )
                }
            }
            if (preset == OnboardingPreset.STRICT) {
                if (overlayGranted) {
                    SummaryRow(
                        icon = Icons.Default.CheckCircle,
                        text = stringResource(
                            id = R.string.onboarding_done_summary_blocking, budgetText
                        )
                    )
                } else {
                    WarningRow(
                        text = stringResource(id = R.string.onboarding_done_summary_blockingPending)
                    )
                }
            }
            if (viewModel.isRerun) {
                SummaryRow(
                    icon = Icons.Default.Info,
                    text = stringResource(id = R.string.onboarding_done_summary_rerunHint)
                )
            }
        }
        if (!accessibilityEnabled && !writeSecureSettingsGranted) {
            SummaryRow(
                icon = Icons.Default.Info,
                text = stringResource(id = R.string.onboarding_done_summary_accessibilityPending)
            )
        }
        if (hasPendingPermission) {
            Text(
                text = stringResource(id = R.string.onboarding_done_summary_permissionsHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

/** A summary line flagging something that won't switch on until a permission is granted. */
@Composable
private fun WarningRow(text: String) {
    SummaryRow(
        icon = Icons.Default.Warning,
        text = text,
        tint = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
