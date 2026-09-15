package com.flx_apps.digitaldetox

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a [SimpleListTile] still does while the settings are locked: its control takes no tap, no
 * key press and no action from an accessibility service, but a drag that starts on it scrolls the
 * list around it like anywhere else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockedTileTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val listState = LazyListState()
    private var toggled = false

    private fun showTiles(locked: Boolean, header: @Composable () -> Unit = {}) = composeRule.setContent {
        CompositionLocalProvider(LocalSettingsLocked provides locked) {
            LazyColumn(state = listState, modifier = Modifier.height(400.dp)) {
                item { header() }
                items(30) { index ->
                    SimpleListTile(
                        titleText = "Setting $index",
                        subtitleText = "Locked",
                        trailing = { Checkbox(checked = false, onCheckedChange = { toggled = true }) }
                    )
                }
            }
        }
    }

    // by position: a locked control is gone from the semantics tree, so a tag could not find it
    private fun tapControl() = composeRule.onNodeWithText("Setting 1").performTouchInput {
        click(Offset(right - 40.dp.toPx(), centerY))
    }

    @Test
    fun `a drag starting on a locked tile scrolls the list`() {
        showTiles(locked = true)
        composeRule.onNodeWithText("Setting 1").performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 300.dp.toPx())
        }
        composeRule.runOnIdle { assertTrue(listState.firstVisibleItemIndex > 0) }
    }

    @Test
    fun `a tap on the control of a locked tile does nothing`() {
        showTiles(locked = true)
        tapControl()
        composeRule.runOnIdle { assertFalse(toggled) }
    }

    @Test
    fun `a tap on the control of an unlocked tile toggles it`() {
        showTiles(locked = false)
        tapControl()
        composeRule.runOnIdle { assertTrue(toggled) }
    }

    // the button stands in for the unlock button above a locked screen, so the first tab has
    // somewhere to go and the second one shows whether a locked control comes next
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a keyboard cannot reach the control of a locked tile`() {
        showTiles(locked = true, header = { Button(onClick = {}) { Text("Unlock") } })
        composeRule.onRoot().performKeyInput {
            pressKey(Key.Tab)
            pressKey(Key.Tab)
            pressKey(Key.Enter)
        }
        composeRule.runOnIdle { assertFalse(toggled) }
    }

    @Test
    fun `accessibility services find nothing to act on in a locked tile`() {
        showTiles(locked = true)
        composeRule.onAllNodesWithText("Setting", substring = true).fetchSemanticsNodes()
            .let { assertTrue(it.isNotEmpty()) }
        composeRule.onAllNodes(hasClickAction() and isEnabled()).assertCountEquals(0)
    }
}
