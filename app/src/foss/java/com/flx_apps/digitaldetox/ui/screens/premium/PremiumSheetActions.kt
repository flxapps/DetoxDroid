package com.flx_apps.digitaldetox.ui.screens.premium

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R

/**
 * FOSS bottom section of the locked premium sheet: the external donation links plus the
 * honor-system unlocks — the lock is a nudge, not DRM (PREMIUM_TIER_PLAN.md §1).
 *
 * The sibling `src/googlePlay/…/PremiumSheetActions.kt` (private overlay, gitignored) renders the
 * Play Billing purchase actions instead, under the same fully-qualified name — [PremiumSheetHost]
 * needs no flavor branching.
 */
@Composable
fun PremiumLockedSheetActions(onUnlock: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
