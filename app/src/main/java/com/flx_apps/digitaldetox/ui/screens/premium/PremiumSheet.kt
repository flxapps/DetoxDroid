package com.flx_apps.digitaldetox.ui.screens.premium

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.premium.PremiumManager
import com.flx_apps.digitaldetox.premium.PremiumSheetController
import com.flx_apps.digitaldetox.premium.PremiumSheetTrigger
import com.flx_apps.digitaldetox.premium.PremiumSupport
import com.flx_apps.digitaldetox.util.formatDurationMsShort

/**
 * App-global host for the premium bottom sheet. Rendered once near the navigation root; it observes
 * [PremiumSheetController] and shows the sheet whenever a trigger is set — whether that is an
 * explicit tap, a locked premium control, or a capped power-use nudge.
 *
 * The sheet's persuasion strategy is deliberately reciprocity-over-pressure (see
 * PREMIUM_TIER_PLAN.md §1): show what DetoxDroid has already done for *this* user, be upfront that
 * everything can be unlocked for free, and make the tip the natural way to say thanks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSheetHost(viewModel: PremiumSheetViewModel = viewModel()) {
    val trigger by PremiumSheetController.trigger.collectAsState()
    val currentTrigger = trigger
    if (currentTrigger != null) {
        val isUnlocked by PremiumManager.isPremiumUnlocked.collectAsState()
        val impactStats by viewModel.impactStats.collectAsState()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        LaunchedEffect(Unit) {
            viewModel.loadImpactStats()
            // GP-ready: refresh entitlement when the sheet appears (no-op in the FOSS build).
            PremiumSupport.refreshEntitlement()
        }
        ModalBottomSheet(
            onDismissRequest = { PremiumSheetController.onDismiss() },
            sheetState = sheetState,
        ) {
            Crossfade(targetState = isUnlocked, label = "premiumSheetState") { unlocked ->
                if (unlocked) {
                    PremiumUnlockedContent(
                        onReset = { PremiumManager.relock() },
                        onDone = { PremiumSheetController.hide() },
                    )
                } else {
                    PremiumLockedContent(
                        trigger = currentTrigger,
                        impactStats = impactStats,
                        onUnlock = { PremiumManager.unlock() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumLockedContent(
    trigger: PremiumSheetTrigger,
    impactStats: PremiumImpactStats?,
    onUnlock: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PremiumHeroIcon(icon = Icons.Default.VolunteerActivism)
        Spacer(Modifier.height(16.dp))
        Text(
            text = titleForTrigger(trigger),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.premium_sheet_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (impactStats != null && !impactStats.isEmpty) {
            Spacer(Modifier.height(16.dp))
            ImpactCard(impactStats)
        }

        Spacer(Modifier.height(16.dp))
        ExtrasCard()

        Spacer(Modifier.height(16.dp))
        SupportButtons(uriHandler)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.premium_action_donatedUnlock))
        }
        TextButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.premium_action_freeUnlock))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.premium_locked_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PremiumUnlockedContent(
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PremiumHeroIcon(icon = Icons.Default.Favorite)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.premium_unlocked_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.premium_unlocked_subtitle),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.premium_unlocked_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        SupportButtons(uriHandler)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.premium_action_done))
        }
        TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.premium_action_relock))
        }
    }
}

@Composable
private fun titleForTrigger(trigger: PremiumSheetTrigger): String = when (trigger) {
    PremiumSheetTrigger.Generic -> stringResource(R.string.premium_sheet_title_generic)
    PremiumSheetTrigger.PowerUseNudge -> stringResource(R.string.premium_sheet_title_nudge)
    is PremiumSheetTrigger.LockedFeature -> stringResource(
        R.string.premium_sheet_title_locked, stringResource(trigger.featureLabelRes)
    )
}

@Composable
private fun PremiumHeroIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    )
                ),
                CircleShape,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(36.dp),
        )
    }
}

/**
 * "Your detox so far" — the moments DetoxDroid already stepped in for this user, from the local
 * usage history. A tip request next to real personal numbers beats any generic pitch, and it's
 * data the app has anyway (on-device only).
 */
@Composable
private fun ImpactCard(stats: PremiumImpactStats) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.premium_impact_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (stats.interventionCount > 0) {
                    ImpactTile(
                        value = stringResource(
                            R.string.premium_impact_count, stats.interventionCount
                        ),
                        label = stringResource(R.string.premium_impact_interventions),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (stats.grayscaleTimeMs > 0) {
                    ImpactTile(
                        value = formatDurationMsShort(context, stats.grayscaleTimeMs),
                        label = stringResource(R.string.premium_impact_grayscale),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (stats.daysTracked > 0) {
                    ImpactTile(
                        value = stats.daysTracked.toString(),
                        label = stringResource(R.string.premium_impact_days),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImpactTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * What a tip gets you — and, just as important, what stays free. Being explicit that the detox
 * features are never gated is the strongest trust signal this sheet can send.
 */
@Composable
private fun ExtrasCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.premium_extras_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.premium_extras_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SupportButtons(uriHandler: UriHandler) {
    PremiumSupport.supportLinks.forEachIndexed { index, link ->
        val url = stringResource(link.urlRes)
        val content: @Composable () -> Unit = {
            Icon(link.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(link.labelRes))
        }
        val modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (index == 0) 0.dp else 8.dp)
        if (index == 0) {
            // the first (one-time tip) link is the action this sheet exists for
            Button(onClick = { uriHandler.openUri(url) }, modifier = modifier) { content() }
        } else {
            FilledTonalButton(onClick = { uriHandler.openUri(url) }, modifier = modifier) {
                content()
            }
        }
    }
}
