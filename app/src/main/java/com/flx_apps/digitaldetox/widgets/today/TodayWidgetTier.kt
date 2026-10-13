package com.flx_apps.digitaldetox.widgets.today

/**
 * The layouts of the screen time widget, each with the size it needs in dp. The widget shows the
 * largest one that fits, so it picks up detail as it is pulled bigger.
 */
enum class TodayWidgetTier(val minWidthDp: Float, val minHeightDp: Float) {
    /** The total alone, shortened to fit a single cell. */
    Tiny(40f, 40f),

    /** The total alone, for 2×1. */
    Total(100f, 40f),

    /** Screen time, unlocks and scroll distance side by side, for a row of three cells. */
    Row(200f, 40f),

    /**
     * The row with room for a fourth number, the comparison with a usual day: four cells on most
     * phones. Narrower, four numbers would have to be cut off, which reads worse than three.
     */
    WideRow(290f, 40f),

    /** The same three stacked, with the scroll distance put in proportion: 2×2. */
    Column(110f, 170f),

    /**
     * The row plus the most used apps, or the budgets where the apps are left out: 3×2 and 4×2.
     * The comparison with a usual day takes the place of an app.
     */
    Apps(200f, 170f),

    /**
     * The most used apps, what is left of the budgets and the scroll comparison: 4×3, which is
     * also roughly the size of the widget area on the lock screen. A budget, like the comparison
     * with a usual day, takes the place of an app here.
     */
    Full(200f, 280f),

    /** The same with room for all the apps next to the budgets. */
    Tall(200f, 340f);

    private val area get() = minWidthDp * minHeightDp

    companion object {
        /**
         * The largest tier, by area, that fits [widthDp]×[heightDp], or the smallest if none does.
         * Android 12 and later pick from the sized layouts the same way on their own; this is for
         * the versions before, which hand the widget its size and leave the choice to it.
         */
        fun pick(widthDp: Float, heightDp: Float): TodayWidgetTier =
            entries.filter { it.minWidthDp <= widthDp && it.minHeightDp <= heightDp }
                .maxByOrNull { it.area } ?: Tiny
    }
}
