package com.flx_apps.digitaldetox.ui.screens.premium

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
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.premium.PremiumManager
import com.flx_apps.digitaldetox.premium.PremiumSheetController
import com.flx_apps.digitaldetox.premium.PremiumSheetTrigger
import com.flx_apps.digitaldetox.premium.PremiumSupport

/**
 * App-global host for the premium bottom sheet. Rendered once near the navigation root; it observes
 * [PremiumSheetController] and shows the sheet whenever a trigger is set — whether that is an
 * explicit tap, a locked premium control, or a capped power-use nudge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSheetHost() {
    val trigger by PremiumSheetController.trigger.collectAsState()
    val currentTrigger = trigger
    if (currentTrigger != null) {
        val isUnlocked by PremiumManager.isPremiumUnlocked.collectAsState()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // GP-ready: refresh entitlement when the sheet appears (no-op in the FOSS build).
        LaunchedEffect(Unit) { PremiumSupport.refreshEntitlement() }
        ModalBottomSheet(
            onDismissRequest = { PremiumSheetController.onDismiss() },
            sheetState = sheetState,
        ) {
            PremiumSheetContent(
                trigger = currentTrigger,
                isUnlocked = isUnlocked,
                onUnlock = { PremiumManager.unlock() },
                onReset = { PremiumManager.relock() },
                onDone = { PremiumSheetController.hide() },
            )
        }
    }
}

@Composable
private fun PremiumSheetContent(
    trigger: PremiumSheetTrigger,
    isUnlocked: Boolean,
    onUnlock: () -> Unit,
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
        PremiumHeroIcon()
        Spacer(Modifier.height(16.dp))

        if (isUnlocked) {
            Text(
                text = stringResource(R.string.premium_unlocked_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.premium_unlocked_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            SupportButtons(uriHandler)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.premium_action_relock))
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.premium_action_done))
            }
        } else {
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
            Spacer(Modifier.height(16.dp))
            PremiumValueCard()
            Spacer(Modifier.height(16.dp))
            SupportButtons(uriHandler)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
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
private fun PremiumHeroIcon() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

@Composable
private fun PremiumValueCard() {
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
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.premium_value_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.premium_value_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SupportButtons(uriHandler: UriHandler) {
    PremiumSupport.supportLinks.forEach { link ->
        val url = stringResource(link.urlRes)
        FilledTonalButton(
            onClick = { uriHandler.openUri(url) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Icon(link.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(link.labelRes))
        }
    }
}
