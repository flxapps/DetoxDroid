package com.flx_apps.digitaldetox.ui.screens.feature

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.MainActivity
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.schedule.timeSpanText
import com.flx_apps.digitaldetox.ui.screens.schedule.weekDaysText
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile

/**
 * A tile that opens the schedule screen. A single rule is spelled out, since "1 rule" says nothing
 * about when the feature runs.
 */
@Composable
fun OpenScheduleTile(
    featureViewModel: FeatureViewModel = viewModel(),
    navViewModel: NavViewModel = viewModel(viewModelStoreOwner = LocalContext.current as MainActivity)
) {
    val context = LocalContext.current
    val rules = (featureViewModel.feature as SupportsScheduleFeature).scheduleRules
    SimpleListTile(titleText = stringResource(id = R.string.feature_settings_schedule),
        subtitleText = when (rules.size) {
            0 -> stringResource(id = R.string.feature_settings_schedule_hint_activeAllTheTime)
            1 -> rules.first().let {
                "${weekDaysText(context, it.daysOfWeek)}, ${timeSpanText(context, it.start, it.end)}"
            }

            else -> stringResource(id = R.string.feature_settings_schedule_hint, rules.size)
        },
        trailing = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Manage Schedule",
                modifier = Modifier.size(24.dp)
            )
        },
        leadingIcon = Icons.Default.EditCalendar,
        allowClickWhenLocked = true,
        onClick = {
            navViewModel.openRoute(NavigationRoutes.FeatureSchedule(featureId = featureViewModel.feature.id))
        })
}