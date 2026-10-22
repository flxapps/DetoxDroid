package com.flx_apps.digitaldetox.widgets.today

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import com.flx_apps.digitaldetox.MainActivity
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.TodaySummary
import com.flx_apps.digitaldetox.system_integration.TodaySummary.Companion.TOP_APP_LIMIT
import com.flx_apps.digitaldetox.util.appIconBitmap
import com.flx_apps.digitaldetox.util.appLabel
import com.flx_apps.digitaldetox.util.formatDistance
import com.flx_apps.digitaldetox.util.formatDurationMsCompact
import com.flx_apps.digitaldetox.util.scrollComparisonText
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.widgets.WidgetStyle
import java.text.NumberFormat
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Builds the screen time widget's layouts from one [TodaySummary]. Labels and icons are read once
 * and shared by every layout, since Android 12 and later get all of them in a single update.
 * @param detoxDroidRunning whether DetoxDroid runs. While it doesn't, the widget says so in place of
 * the label under the screen time, and a tap leads to where it is switched on.
 */
internal class TodayWidgetViews(
    private val context: Context,
    private val summary: TodaySummary,
    private val detoxDroidRunning: Boolean,
) {
    private val packageName = context.packageName
    private val screenTime = summary.screenTimeMs.milliseconds.toHrMinString(context)
    private val unlocks = summary.unlocks?.let { NumberFormat.getIntegerInstance().format(it) }
    private val scrolled = formatDistance(context, summary.scrolledMeters)
    private val comparison = scrollComparisonText(context, summary.scrolledMeters)
    private val vsUsual = summary.vsUsual?.text(context)
    private val vsUsualShort = summary.vsUsual?.shortText(context)
    private val open: PendingIntent = if (detoxDroidRunning) {
        MainActivity.openUsageStatsIntent(context)
    } else {
        MainActivity.openHomeIntent(context)
    }

    private val budgets = summary.budgets.map {
        BudgetLine(
            iconRes = if (it.locks) R.drawable.ic_disable_app else R.drawable.ic_contrast,
            name = it.name,
            budget = it,
        )
    }

    private val apps: List<AppLine> by lazy {
        val iconSizePx = (APP_ICON_SIZE_DP * context.resources.displayMetrics.density).roundToInt()
        val longestMs = summary.topApps.maxOfOrNull { it.screenTimeMs }?.coerceAtLeast(1L) ?: 1L
        summary.topApps.map { app ->
            AppLine(
                packageName = app.packageName,
                label = context.appLabel(app.packageName),
                time = app.screenTimeMs.milliseconds.toHrMinString(context),
                icon = context.appIconBitmap(app.packageName, iconSizePx),
                sharePercent = (app.screenTimeMs * 100 / longestMs).toInt()
            )
        }
    }

    /** Every tier at once, for the host to choose from by the size it gives the widget. */
    @RequiresApi(Build.VERSION_CODES.S)
    fun responsive(style: WidgetStyle, parts: Set<TodayWidgetPart>): RemoteViews = RemoteViews(
        TodayWidgetTier.entries.associate {
            SizeF(it.minWidthDp, it.minHeightDp) to build(it, style, parts)
        }
    )

    fun build(tier: TodayWidgetTier, style: WidgetStyle, parts: Set<TodayWidgetPart>): RemoteViews {
        val content = when (tier) {
            TodayWidgetTier.Tiny -> total(formatDurationMsCompact(context, summary.screenTimeMs))
            TodayWidgetTier.Total -> total(screenTime)
            TodayWidgetTier.Column -> column(parts)
            else -> full(tier, parts)
        }
        return style.frame(context, content).apply {
            setOnClickPendingIntent(android.R.id.background, open)
        }
    }

    private fun total(text: String) = RemoteViews(packageName, R.layout.widget_today_total).apply {
        setTextViewText(R.id.widget_today_screen_time, text)
        bindCaption()
    }

    /** The stats stacked, with one line below: the comparison with a usual day, or the distance's. */
    private fun column(parts: Set<TodayWidgetPart>) =
        RemoteViews(packageName, R.layout.widget_today_column).apply {
            bindStats(parts)
            bindLine(
                R.id.widget_today_comparison,
                vsUsual?.takeIf { TodayWidgetPart.VsUsual in parts }
                    ?: comparison?.takeIf { TodayWidgetPart.ScrollComparison in parts }
            )
        }

    /**
     * The stats with as much below them as [tier] has room for, in [listSlotsOf] lines: the
     * comparison with a usual day first, then the budgets, then the apps, and with
     * [TodayWidgetTier.Full] and up the scroll comparison at the bottom. What [parts] leaves out
     * leaves its lines to the others. With nothing below, the stats sit in the middle, and the
     * comparison with a usual day is one of them where they have room for it.
     */
    private fun full(tier: TodayWidgetTier, parts: Set<TodayWidgetPart>) =
        RemoteViews(packageName, R.layout.widget_today_full).apply {
            val large = tier == TodayWidgetTier.Full || tier == TodayWidgetTier.Tall
            var slots = listSlotsOf(tier)
            val showsVsUsual = TodayWidgetPart.VsUsual in parts
            val vsUsualLine = vsUsual?.takeIf { showsVsUsual && slots > 0 }
            // with no line below the numbers, it is one of them, where there is room for one more
            val numbers = 1 + listOf(
                TodayWidgetPart.Unlocks in parts && unlocks != null,
                TodayWidgetPart.Scrolled in parts
            ).count { it }
            val maxNumbers = if (tier == TodayWidgetTier.WideRow) 4 else 3
            val vsUsualTile =
                vsUsualShort?.takeIf { showsVsUsual && slots == 0 && numbers < maxNumbers }
            if (vsUsualLine != null) slots--
            // the apps tier keeps to the apps, unless they are left out
            val budgetLines = if (
                TodayWidgetPart.Budgets in parts && (large || TodayWidgetPart.Apps !in parts)
            ) budgets.take(slots) else emptyList()
            slots -= budgetLines.size
            val appCount = if (TodayWidgetPart.Apps in parts) minOf(slots, TOP_APP_LIMIT) else 0
            val comparisonLine =
                comparison?.takeIf { TodayWidgetPart.ScrollComparison in parts && large }

            bindStats(parts)
            if (vsUsualTile != null) setTextViewText(R.id.widget_today_vs_usual_value, vsUsualTile)
            setViewVisibility(R.id.widget_today_vs_usual_group, visibleIf(vsUsualTile != null))
            bindLine(R.id.widget_today_vs_usual, vsUsualLine)
            val hasList = appCount > 0 || budgetLines.isNotEmpty() || comparisonLine != null
            setInt(
                R.id.widget_today_full,
                "setGravity",
                if (hasList) Gravity.TOP else Gravity.CENTER_VERTICAL
            )
            setViewVisibility(R.id.widget_today_separator, visibleIf(hasList))
            bindApps(appCount)
            bindBudgets(budgetLines)
            if (comparisonLine != null) setTextViewText(R.id.widget_today_comparison, comparisonLine)
            setViewVisibility(R.id.widget_today_comparison_group, visibleIf(comparisonLine != null))
        }

    /** How many apps, budgets and lines like them fit below the stats in [tier]. */
    private fun listSlotsOf(tier: TodayWidgetTier) = when (tier) {
        TodayWidgetTier.Apps -> 3
        TodayWidgetTier.Full -> 5
        TodayWidgetTier.Tall -> 7
        else -> 0
    }

    private fun RemoteViews.bindStats(parts: Set<TodayWidgetPart>) {
        setTextViewText(R.id.widget_today_screen_time, screenTime)
        bindCaption()
        val showUnlocks = TodayWidgetPart.Unlocks in parts && unlocks != null
        if (showUnlocks) setTextViewText(R.id.widget_today_unlocks, unlocks)
        setViewVisibility(R.id.widget_today_unlocks_group, visibleIf(showUnlocks))
        val showScrolled = TodayWidgetPart.Scrolled in parts
        if (showScrolled) setTextViewText(R.id.widget_today_scrolled, scrolled)
        setViewVisibility(R.id.widget_today_scrolled_group, visibleIf(showScrolled))
    }

    /** The label under the screen time, or in its place that DetoxDroid is off. */
    private fun RemoteViews.bindCaption() {
        setViewVisibility(R.id.widget_today_caption, visibleIf(detoxDroidRunning))
        setViewVisibility(R.id.widget_today_off, visibleIf(!detoxDroidRunning))
    }

    /** Shows [text] in the view with [viewId], or hides it without any. */
    private fun RemoteViews.bindLine(@IdRes viewId: Int, text: String?) {
        if (text != null) setTextViewText(viewId, text)
        setViewVisibility(viewId, visibleIf(text != null))
    }

    /** Lists the [count] most used apps, or says there are none yet. No list at all for 0. */
    private fun RemoteViews.bindApps(count: Int) {
        val shown = apps.take(count)
        removeAllViews(R.id.widget_today_apps)
        setViewVisibility(R.id.widget_today_apps, visibleIf(count > 0))
        setViewVisibility(R.id.widget_today_apps_empty, visibleIf(count > 0 && shown.isEmpty()))
        setTextViewText(
            R.id.widget_today_apps_empty,
            context.getString(
                if (summary.leftOutMs > 0L) R.string.widget_today_allLeftOut
                else R.string.widget_today_noApps
            )
        )
        shown.forEach { app ->
            val row = RemoteViews(packageName, R.layout.widget_today_app_row)
            if (app.icon != null) row.setImageViewBitmap(R.id.widget_today_app_icon, app.icon)
            row.setTextViewText(R.id.widget_today_app_name, app.label)
            row.setTextViewText(R.id.widget_today_app_time, app.time)
            row.setProgressBar(R.id.widget_today_app_bar, 100, app.sharePercent, false)
            row.setOnClickPendingIntent(
                R.id.widget_today_app_row,
                MainActivity.openAppUsageIntent(context, app.packageName)
            )
            addView(R.id.widget_today_apps, row)
        }
    }

    private fun RemoteViews.bindBudgets(lines: List<BudgetLine>) {
        removeAllViews(R.id.widget_today_budgets)
        setViewVisibility(R.id.widget_today_budgets, visibleIf(lines.isNotEmpty()))
        lines.forEach { line ->
            val row = RemoteViews(packageName, R.layout.widget_today_budget_row)
            row.setImageViewResource(R.id.widget_today_budget_icon, line.iconRes)
            row.setTextViewText(R.id.widget_today_budget_name, line.name)
            row.setTextViewText(R.id.widget_today_budget_left, line.budget.leftText(context))
            row.setProgressBar(
                R.id.widget_today_budget_bar,
                100,
                (line.budget.leftMs * 100 / line.budget.allowedMs).toInt(),
                false
            )
            row.setOnClickPendingIntent(
                R.id.widget_today_budget_row,
                MainActivity.openGroupIntent(context, line.budget.groupId)
            )
            addView(R.id.widget_today_budgets, row)
        }
    }

    private fun visibleIf(visible: Boolean) = if (visible) View.VISIBLE else View.GONE

    private class AppLine(
        val packageName: String,
        val label: String,
        val time: String,
        val icon: Bitmap?,
        val sharePercent: Int,
    )

    private class BudgetLine(
        @DrawableRes val iconRes: Int,
        val name: String,
        val budget: TodaySummary.Budget,
    )

    private companion object {
        const val APP_ICON_SIZE_DP = 20
    }
}
