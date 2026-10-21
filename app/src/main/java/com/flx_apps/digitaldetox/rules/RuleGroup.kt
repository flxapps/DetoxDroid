package com.flx_apps.digitaldetox.rules

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import java.time.LocalDateTime

/** The id of a [RuleGroup], see [RuleGroups.isGroupId]. It prefixes every key the group stores. */
typealias GroupId = String

/** What a group's budget is counted against. */
enum class BudgetPeriod {
    /** From the moment the group's window opens, or from midnight without a schedule. */
    DAY,

    /** From every full hour. */
    HOUR
}

/**
 * What a group does to its apps and websites, in the order the steps take hold. Each step is run
 * by one of the features, which the group screen reaches for the parts that are about the phone
 * rather than about the group.
 */
enum class Step(val feature: Feature) {
    /** Every open waits first, see [RuleGroup.waitBeforeOpeningMs]. */
    WAIT(DisableAppsFeature),

    /** The apps lose their color, from the start or once part of the budget is used. */
    GRAY(GrayscaleAppsFeature),

    /** Sustained scrolling gets a break screen, see [RuleGroup.scrollBreakAfterMs]. */
    SCROLL_BREAK(BreakDoomScrollingFeature),

    /** The apps are kept out of reach once the budget is used, or always without one. */
    LOCK(DisableAppsFeature),
}

/**
 * A rule the user wrote: these apps and websites, at these times, get this much, and this is what
 * happens to them. The apps, websites and times are the same lists the features used to carry on
 * their own, so the screens that edit them work on a group as they did on a feature. The budget and
 * the steps are the group's own.
 *
 * Several groups can cover the same app. Each counts its own time, and for every step the
 * strictest group wins, see [Rules].
 */
class RuleGroup internal constructor(val id: GroupId) :
    SupportsAppExceptionsFeature by SupportsAppExceptionsFeature.Impl(
        id, defaultExceptionListType = AppExceptionListType.ONLY_LIST
    ),
    SupportsScheduleFeature by SupportsScheduleFeature.Impl(id) {

    companion object {
        /** [budgetMs] without a limit. */
        const val NO_BUDGET = -1L

        /** [grayAfterMs] or [scrollBreakAfterMs] for a step that is off. */
        const val STEP_OFF = -1L
    }

    var name: String by DataStoreProperty(stringPreferencesKey("${id}_name"), "")

    /** A group that is off keeps everything it was told and does nothing. */
    var isEnabled: Boolean by DataStoreProperty(booleanPreferencesKey("${id}_isActivated"), true)

    /**
     * How long the group's apps and websites may be used per [budgetPeriod], together, or
     * [NO_BUDGET]. What happens when it is used up is up to the steps.
     */
    var budgetMs: Long by DataStoreProperty(longPreferencesKey("${id}_budgetMs"), NO_BUDGET)

    var budgetPeriod: BudgetPeriod by DataStoreProperty(
        stringPreferencesKey("${id}_budgetPeriod"),
        BudgetPeriod.DAY,
        dataTransformer = DataStorePropertyTransformer.EnumStorePropertyTransformer(
            BudgetPeriod::class.java
        )
    )

    /** How long each open of a listed app waits before it goes through, 0 for no wait. */
    var waitBeforeOpeningMs: Long by DataStoreProperty(longPreferencesKey("${id}_waitMs"), 0L)

    /**
     * After how much of the budget the apps turn gray: 0 from the first minute, [STEP_OFF] never.
     * Without a budget it is either of those two.
     */
    var grayAfterMs: Long by DataStoreProperty(longPreferencesKey("${id}_grayAfterMs"), STEP_OFF)

    /** After how long of sustained scrolling the break screen shows, [STEP_OFF] for never. */
    var scrollBreakAfterMs: Long by DataStoreProperty(
        longPreferencesKey("${id}_scrollBreakAfterMs"), STEP_OFF
    )

    /** Whether the apps are kept out of reach once the budget is used, or always without one. */
    var locks: Boolean by DataStoreProperty(booleanPreferencesKey("${id}_locks"), false)

    val hasBudget: Boolean get() = budgetMs > 0L

    val waits: Boolean get() = waitBeforeOpeningMs > 0L

    val grays: Boolean get() = grayAfterMs >= 0L

    /** Gray from the first minute, rather than once part of the budget is used. */
    val graysFromStart: Boolean get() = grayAfterMs == 0L

    val breaksScrolling: Boolean get() = scrollBreakAfterMs > 0L

    /**
     * Whether the lock and the wait reach the group's apps. Only a group of selected apps can hold
     * them back: "every app except a few" would put a block screen over the dialer and the
     * settings. Its websites are held back either way, a browser kept to a few sites is a browser
     * one can still use.
     */
    val canHoldBackApps: Boolean get() = appExceptionListType == AppExceptionListType.ONLY_LIST

    fun hasStep(step: Step): Boolean = when (step) {
        Step.WAIT -> waits
        Step.GRAY -> grays
        Step.SCROLL_BREAK -> breaksScrolling
        Step.LOCK -> locks
    }

    /** The steps that are on, in the order they take hold. */
    val steps: List<Step> get() = Step.entries.filter(::hasStep)

    /** Whether any of the group's steps is run by [feature]. */
    fun usesFeature(feature: Feature): Boolean = steps.any { it.feature == feature }

    /**
     * Whether the group counts right now: switched on and inside its schedule. A running brake
     * opens every schedule, as it does for the features.
     */
    fun isActiveAt(atDateTime: LocalDateTime = LocalDateTime.now()): Boolean =
        isEnabled && (isScheduled(atDateTime) || EmergencyBrakeFeature.isBraking())

    /**
     * Puts the fields back into the shape the rest of the app relies on: a gray threshold is a
     * point inside the budget, and without a budget gray means from the start.
     */
    fun normalize() {
        if (budgetMs <= 0L && budgetMs != NO_BUDGET) budgetMs = NO_BUDGET
        if (grayAfterMs > 0L) {
            grayAfterMs = if (hasBudget) grayAfterMs.coerceAtMost(budgetMs) else 0L
        }
        if (waitBeforeOpeningMs < 0L) waitBeforeOpeningMs = 0L
    }
}
