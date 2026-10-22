package com.flx_apps.digitaldetox.widgets.today

import androidx.annotation.StringRes
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.widgets.WidgetPart

/**
 * What the screen time widget can show besides the screen time. Each widget picks its own, and
 * shows each picked part where its size has room for it.
 */
enum class TodayWidgetPart(@StringRes override val labelRes: Int) : WidgetPart {
    Unlocks(R.string.widget_today_part_unlocks),
    Scrolled(R.string.widget_today_part_scrolled),
    VsUsual(R.string.widget_today_part_vsUsual),
    Apps(R.string.widget_today_part_apps),
    Budgets(R.string.widget_today_part_budgets),
    ScrollComparison(R.string.widget_today_part_scrollComparison);

    companion object {
        val all: Set<TodayWidgetPart> = entries.toSet()
    }
}
