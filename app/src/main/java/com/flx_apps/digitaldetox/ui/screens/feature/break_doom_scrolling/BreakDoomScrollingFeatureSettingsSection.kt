package com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DoomScrollingSensitivity
import com.flx_apps.digitaldetox.rules.Step
import com.flx_apps.digitaldetox.ui.screens.rule_groups.colorRes
import com.flx_apps.digitaldetox.ui.theme.brandTone
import com.flx_apps.digitaldetox.ui.widgets.CardOption
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.OptionCards
import com.flx_apps.digitaldetox.ui.widgets.SectionHeader
import com.flx_apps.digitaldetox.ui.widgets.SettingsGroup
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import kotlin.math.roundToInt

/**
 * The settings of the scroll break step: how easily it fires and how long the cooldown lasts.
 * Which apps, when and after how long of scrolling is set on the groups.
 */
@Composable
fun BreakDoomScrollingFeatureSettingsSection(
    viewModel: BreakDoomScrollingFeatureSettingsViewModel = viewModel()
) {
    SectionHeader(title = stringResource(id = R.string.feature_doomScrolling_sensitivity))
    Text(
        text = stringResource(id = R.string.feature_doomScrolling_sensitivity_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 2.dp, bottom = 10.dp)
    )
    SensitivityCards()
    SettingsGroup {
        CooldownTimeTile()
    }
}

/**
 * How easily the scroll-intensity trigger fires (see [DoomScrollingSensitivity]), one card per
 * level, each with the pace it starts at.
 */
@Composable
fun SensitivityCards(viewModel: BreakDoomScrollingFeatureSettingsViewModel = viewModel()) {
    val options = listOf(
        DoomScrollingSensitivity.RELAXED to Icons.Default.SentimentSatisfied,
        DoomScrollingSensitivity.BALANCED to Icons.Default.Balance,
        DoomScrollingSensitivity.STRICT to Icons.Default.Bolt,
    ).map { (level, icon) ->
        CardOption(
            value = level,
            icon = icon,
            title = stringResource(id = level.titleRes),
            description = stringResource(
                id = R.string.feature_doomScrolling_sensitivity_screensPerMinute,
                level.minScreenHeightsPerMinute.roundToInt()
            ),
        )
    }
    OptionCards(
        options = options,
        selected = viewModel.detectionSensitivity.collectAsState().value,
        tone = brandTone(Step.SCROLL_BREAK.colorRes),
        onSelect = viewModel::setDetectionSensitivity,
    )
}

private val DoomScrollingSensitivity.titleRes: Int
    get() = when (this) {
        DoomScrollingSensitivity.RELAXED -> R.string.feature_doomScrolling_sensitivity_relaxed
        DoomScrollingSensitivity.BALANCED -> R.string.feature_doomScrolling_sensitivity_balanced
        DoomScrollingSensitivity.STRICT -> R.string.feature_doomScrolling_sensitivity_strict
    }

/**
 * Lets the user choose how long an app (or the feed that caused the incident) stays locked after
 * a doom-scrolling break.
 */
@Composable
fun CooldownTimeTile(
    viewModel: BreakDoomScrollingFeatureSettingsViewModel = viewModel()
) {
    val cooldownTime = viewModel.cooldownTime.collectAsState().value
    if (viewModel.showCooldownTimeNumberPickerDialog.collectAsState().value) {
        val context = LocalContext.current
        NumberPickerDialog(
            titleText = stringResource(id = R.string.feature_doomScrolling_cooldownTime),
            initialValue = cooldownTime,
            onValueSelected = {
                viewModel.setCooldownTime(it)
            },
            onDismissRequest = {
                viewModel.setCooldownTimeNumberPickerDialogVisible(false)
            },
            label = {
                context.getString(R.string.duration_minutes, it)
            },
            range = 1..60
        )
    }

    SimpleListTile(
        titleText = stringResource(id = R.string.feature_doomScrolling_cooldownTime),
        subtitleText = stringResource(id = R.string.feature_doomScrolling_cooldownTime_description),
        trailing = {
            Text(text = stringResource(id = R.string.duration_minutes, cooldownTime))
        },
        onClick = {
            viewModel.setCooldownTimeNumberPickerDialogVisible(true)
        },
        leadingIcon = Icons.Default.Timer
    )
}
