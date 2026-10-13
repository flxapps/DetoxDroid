package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.widgets.today.TodayWidgetTier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [TodayWidgetTier.pick] turns the size a launcher gives the widget into the layout it shows. The
 * sizes below are the smallest ones Android's widget guidelines promise for each cell count,
 * upright, so a tier that needs more than that would never show on some phones.
 */
class TodayWidgetTierTest {
    @Test
    fun oneRowShowsTheTotalUntilThereIsRoomForThreeNumbers() {
        assertEquals(TodayWidgetTier.Tiny, TodayWidgetTier.pick(57f, 102f))
        assertEquals(TodayWidgetTier.Total, TodayWidgetTier.pick(130f, 102f))
        assertEquals(TodayWidgetTier.Row, TodayWidgetTier.pick(203f, 102f))
        // four cells at their narrowest still get three numbers rather than four cut off ones
        assertEquals(TodayWidgetTier.Row, TodayWidgetTier.pick(276f, 102f))
        assertEquals(TodayWidgetTier.WideRow, TodayWidgetTier.pick(320f, 102f))
    }

    @Test
    fun twoRowsStackTheNumbersOrAddTheAppsWhenWide() {
        assertEquals(TodayWidgetTier.Column, TodayWidgetTier.pick(130f, 220f))
        assertEquals(TodayWidgetTier.Apps, TodayWidgetTier.pick(203f, 220f))
        assertEquals(TodayWidgetTier.Apps, TodayWidgetTier.pick(276f, 220f))
    }

    @Test
    fun threeRowsShowEverythingAndFourMakeRoomForAllTheApps() {
        assertEquals(TodayWidgetTier.Full, TodayWidgetTier.pick(276f, 337f))
        assertEquals(TodayWidgetTier.Tall, TodayWidgetTier.pick(276f, 455f))
        // too narrow for the app list, so it stays a column however tall it gets
        assertEquals(TodayWidgetTier.Column, TodayWidgetTier.pick(130f, 337f))
    }

    @Test
    fun aWidgetTurnedSidewaysFallsBackToTheRow() {
        // 4×2 in landscape is wide but too short for the app list
        assertEquals(TodayWidgetTier.WideRow, TodayWidgetTier.pick(554f, 117f))
    }

    @Test
    fun aSizeBelowEveryTierStillGetsTheTotal() {
        assertEquals(TodayWidgetTier.Tiny, TodayWidgetTier.pick(0f, 0f))
    }
}
