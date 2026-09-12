package com.flx_apps.digitaldetox

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a [SimpleListTile] still does while the settings are locked: its control ignores taps, but
 * a drag that starts on it scrolls the list around it like anywhere else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockedTileTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val listState = LazyListState()
    private var toggled = false

    @Before
    fun setUp() {
        composeRule.setContent {
            CompositionLocalProvider(LocalSettingsLocked provides true) {
                LazyColumn(state = listState, modifier = Modifier.height(400.dp)) {
                    items(30) { index ->
                        SimpleListTile(
                            titleText = "Setting $index",
                            subtitleText = "Locked",
                            trailing = {
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = { toggled = true },
                                    modifier = Modifier.testTag("checkbox $index")
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a drag starting on a locked tile scrolls the list`() {
        composeRule.onNodeWithText("Setting 1").performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 300.dp.toPx())
        }
        composeRule.runOnIdle { assertTrue(listState.firstVisibleItemIndex > 0) }
    }

    @Test
    fun `a tap on the control of a locked tile does nothing`() {
        composeRule.onNodeWithTag("checkbox 1").performTouchInput { click() }
        composeRule.runOnIdle { assertFalse(toggled) }
    }
}
