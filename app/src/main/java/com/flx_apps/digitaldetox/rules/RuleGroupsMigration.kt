package com.flx_apps.digitaldetox.rules

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeatureId
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.DisableAppsFeatureId
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeatureId
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Turns the rules the features used to carry on their own into groups, once, on the first start
 * after the update. Each configured feature becomes one group that does what the feature did, so
 * nothing changes for anyone: the gray list stays gray, the blocked apps stay blocked at the same
 * budget, the scroll breaks keep their time.
 *
 * The old keys are left where they are. A version before this one reads them as before, and a
 * settings file from before carries them in, which is how a restore of such a file ends up with
 * groups as well: the version mark is not in the file, so this runs again after the restart.
 */
object RuleGroupsMigration {
    /** The shape of the store this build writes. */
    private const val CURRENT_VERSION = 1

    /** The value Disable Apps stored for "no daily limit". */
    private const val LEGACY_NO_LIMIT = -1L

    /**
     * A feature's rules as they were stored before groups, read from their keys alone so the
     * features themselves no longer have to carry them. The list type defaults are the ones each
     * feature had, since a default is not written to the store.
     */
    private class LegacyRules(
        featureId: FeatureId,
        defaultListType: AppExceptionListType,
        private val forcedListType: AppExceptionListType? = null,
    ) {
        val scope = SupportsAppExceptionsFeature.Impl(featureId, defaultListType)
        val schedule = SupportsScheduleFeature.Impl(featureId)
        val isActivated: Boolean by DataStoreProperty(
            booleanPreferencesKey("${featureId}_isActivated"), false
        )

        val listType: AppExceptionListType get() = forcedListType ?: scope.appExceptionListType

        /** Whether the user ever gave the feature a list or a schedule. */
        val isConfigured: Boolean
            get() = isActivated || scope.appExceptions.isNotEmpty() ||
                    schedule.scheduleRules.isNotEmpty()
    }

    private object LegacyValues {
        val grayAllowanceMs: Long by DataStoreProperty(
            longPreferencesKey("${GrayscaleAppsFeatureId}_allowedDailyColorScreenTime"), 0L
        )
        val lockAllowanceMs: Long by DataStoreProperty(
            longPreferencesKey("${DisableAppsFeatureId}_allowedDailyScreenTime"), 0L
        )
        val waitMs: Long by DataStoreProperty(
            longPreferencesKey("${DisableAppsFeatureId}_waitBeforeOpening"), 0L
        )
        val timeUntilWarningMs: Long by DataStoreProperty(
            longPreferencesKey("${BreakDoomScrollingFeatureId}_timeUntilWarning"),
            TimeUnit.MINUTES.toMillis(3)
        )
    }

    /** Runs the migration if the store is from before groups. Safe to call on every start. */
    fun runIfNeeded(context: Context) {
        if (RuleGroups.storeVersion >= CURRENT_VERSION) return
        runCatching { migrate(context) }
            .onFailure { Timber.e(it, "Could not migrate the feature rules into groups") }
        RuleGroups.storeVersion = CURRENT_VERSION
    }

    private fun migrate(context: Context) {
        if (RuleGroups.all.isNotEmpty()) return
        val migrated = mutableListOf<FeatureId>()

        val gray = LegacyRules(GrayscaleAppsFeatureId, AppExceptionListType.NOT_LIST)
        if (gray.isConfigured) {
            RuleGroups.create {
                name = context.getString(R.string.ruleGroups_migrated_gray)
                copyScope(gray)
                val allowanceMs = LegacyValues.grayAllowanceMs
                if (allowanceMs > 0L) {
                    budgetMs = allowanceMs
                    grayAfterMs = allowanceMs
                } else {
                    grayAfterMs = 0L
                }
            }
            migrated += GrayscaleAppsFeatureId
        }

        // the feature only ever offered a list of apps to block, whatever its stored type said
        val lock = LegacyRules(
            DisableAppsFeatureId, AppExceptionListType.ONLY_LIST, AppExceptionListType.ONLY_LIST
        )
        if (lock.isConfigured) {
            RuleGroups.create {
                name = context.getString(R.string.ruleGroups_migrated_locked)
                copyScope(lock)
                waitBeforeOpeningMs = LegacyValues.waitMs
                val allowanceMs = LegacyValues.lockAllowanceMs
                when {
                    allowanceMs == LEGACY_NO_LIMIT -> locks = false
                    allowanceMs > 0L -> {
                        budgetMs = allowanceMs
                        locks = true
                    }

                    else -> locks = true
                }
            }
            migrated += DisableAppsFeatureId
        }

        val scroll = LegacyRules(BreakDoomScrollingFeatureId, AppExceptionListType.ONLY_LIST)
        if (scroll.isConfigured) {
            RuleGroups.create {
                name = context.getString(R.string.ruleGroups_migrated_scrollBreaks)
                copyScope(scroll)
                scrollBreakAfterMs =
                    LegacyValues.timeUntilWarningMs.coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
            }
            migrated += BreakDoomScrollingFeatureId
        }

        // the password locked the features' settings; those settings now live in the groups
        val locked = CommitmentPasswordFeature.lockedFeatureIds
        if (locked.any { it in migrated }) {
            CommitmentPasswordFeature.lockedFeatureIds = locked + RuleGroups.LOCK_ID
        }
        Timber.i("Migrated %d feature rule sets into groups", migrated.size)
    }

    private fun RuleGroup.copyScope(legacy: LegacyRules) {
        isEnabled = legacy.isActivated
        appExceptions = legacy.scope.appExceptions
        appExceptionListType = legacy.listType
        websiteListType = legacy.scope.websiteListType
        scheduleRules = legacy.schedule.scheduleRules
    }
}
