package com.flx_apps.digitaldetox

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Which scroll events the doom scrolling detection and the usage stats leave out: only a text
 * field scrolling to its cursor while someone types. Swiping sideways through a deck of cards is
 * scrolling like any other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrollEventFilterTest {

    private fun scrollEvent(deltaX: Int, deltaY: Int, editable: Boolean = false) =
        AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {
            scrollDeltaX = deltaX
            scrollDeltaY = deltaY
            shadowOf(this).setSourceNode(AccessibilityNodeInfo().apply { isEditable = editable })
        }

    @Test
    fun `a text field scrolling to its cursor is left out`() {
        assertTrue(AccessibilityEventUtil.isTextFieldScrollEvent(scrollEvent(48, 0, true)))
        assertTrue(AccessibilityEventUtil.isTextFieldScrollEvent(scrollEvent(0, 60, true)))
    }

    @Test
    fun `lists, card decks and scrolls without any delta are not`() {
        assertFalse(AccessibilityEventUtil.isTextFieldScrollEvent(scrollEvent(0, 120)))
        assertFalse(AccessibilityEventUtil.isTextFieldScrollEvent(scrollEvent(48, 0)))
        assertFalse(AccessibilityEventUtil.isTextFieldScrollEvent(scrollEvent(0, 0)))
    }
}
