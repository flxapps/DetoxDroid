package com.flx_apps.digitaldetox.ui.screens.rule_groups

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.ui.graphics.vector.ImageVector
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.rules.BudgetPeriod
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.util.DistractingAppsHeuristic
import com.flx_apps.digitaldetox.util.KnownAppCategory
import com.flx_apps.digitaldetox.util.RankedAppRow
import com.flx_apps.digitaldetox.websites.WebsiteCollection
import com.flx_apps.digitaldetox.websites.Websites
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** The ready-made starting points for a new group. Everything in them can be changed afterwards. */
enum class GroupTemplate(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
) {
    FEEDS(R.string.ruleGroups_template_feeds, R.string.ruleGroups_template_feeds_description, Icons.Default.Forum),
    SHORT_VIDEO(
        R.string.ruleGroups_template_shortVideo, R.string.ruleGroups_template_shortVideo_description,
        Icons.Default.SmartDisplay
    ),
    GRAY_ESSENTIALS(
        R.string.ruleGroups_template_grayEssentials, R.string.ruleGroups_template_grayEssentials_description,
        Icons.Default.Contrast
    ),
    NIGHTS_OFF(R.string.ruleGroups_template_nightsOff, R.string.ruleGroups_template_nightsOff_description, Icons.Default.Bedtime),
    EMPTY(R.string.ruleGroups_template_empty, R.string.ruleGroups_template_empty_description, Icons.Default.Add),
}

/** The schedules the setup offers as one tap each. */
enum class SchedulePreset(@StringRes val titleRes: Int) {
    ALWAYS(R.string.ruleGroups_schedulePreset_always),
    WEEKDAYS(R.string.ruleGroups_schedulePreset_weekdays),
    EVENINGS(R.string.ruleGroups_schedulePreset_evenings),
    NIGHTS(R.string.ruleGroups_schedulePreset_nights),
    WEEKENDS(R.string.ruleGroups_schedulePreset_weekends);

    fun rules(): Set<FeatureScheduleRule> = when (this) {
        ALWAYS -> emptySet()
        WEEKDAYS -> setOf(
            FeatureScheduleRule(
                listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(10, 0), LocalTime.of(22, 0)
            )
        )

        EVENINGS -> setOf(FeatureScheduleRule(emptyList(), LocalTime.of(18, 0), LocalTime.of(23, 0)))
        NIGHTS -> setOf(FeatureScheduleRule(emptyList(), LocalTime.of(22, 0), LocalTime.of(7, 0)))
        WEEKENDS -> setOf(
            FeatureScheduleRule(
                listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), LocalTime.MIDNIGHT, LocalTime.MIDNIGHT
            )
        )
    }
}

/**
 * A group before it exists: what the setup collects and [NewRuleGroupViewModel.create] writes.
 * @param apps the packages selected in the list; under [AppExceptionListType.NOT_LIST] the ones
 * the group leaves alone
 */
data class GroupDraft(
    val template: GroupTemplate,
    val name: String,
    val listType: AppExceptionListType,
    val apps: Set<String>,
    val websites: Set<String>,
    val budgetMs: Long,
    val period: BudgetPeriod,
    val waitMs: Long,
    val grayAfterMs: Long,
    val scrollBreakAfterMs: Long,
    val locks: Boolean,
    val schedule: SchedulePreset,
)

object GroupTemplates {
    /** The short-video apps, which no category tells apart from the rest of social media. */
    private val shortVideoApps = setOf(
        "com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.trill",
        "com.ss.android.ugc.trill.go", "com.kwai.video", "video.like", "video.like.lite",
        "com.ss.android.ugc.boom", "com.ss.android.ugc.boomlite", "com.google.android.youtube"
    )

    /** Messengers worth keeping in color while everything else is gray. */
    private val messengers = setOf(
        "org.thoughtcrime.securesms", "com.whatsapp", "org.telegram.messenger", "ch.threema.app",
        "com.google.android.apps.messaging"
    )

    /** Builds the draft [template] stands for, picking apps out of [ranked]. */
    fun draft(context: Context, template: GroupTemplate, ranked: List<RankedAppRow>): GroupDraft {
        val name = context.getString(template.titleRes)
        val base = GroupDraft(
            template = template,
            name = name,
            listType = AppExceptionListType.ONLY_LIST,
            apps = emptySet(),
            websites = emptySet(),
            budgetMs = RuleGroup.NO_BUDGET,
            period = BudgetPeriod.DAY,
            waitMs = 0L,
            grayAfterMs = RuleGroup.STEP_OFF,
            scrollBreakAfterMs = RuleGroup.STEP_OFF,
            locks = false,
            schedule = SchedulePreset.ALWAYS,
        )
        return when (template) {
            GroupTemplate.FEEDS -> base.copy(
                apps = ranked.filter { it.isSocial() }.mapTo(mutableSetOf()) { it.packageName },
                websites = setOf(Websites.collectionEntry(WebsiteCollection.SOCIAL)),
                budgetMs = TimeUnit.MINUTES.toMillis(30),
                grayAfterMs = TimeUnit.MINUTES.toMillis(20),
                locks = true,
                scrollBreakAfterMs = BreakDoomScrollingFeature.DefaultTimeUntilWarningMs,
                schedule = SchedulePreset.WEEKDAYS,
            )

            GroupTemplate.SHORT_VIDEO -> base.copy(
                apps = ranked.filter { it.packageName in shortVideoApps }.mapTo(mutableSetOf()) { it.packageName },
                websites = setOf(
                    Websites.siteEntry("youtube.com/shorts")!!, Websites.siteEntry("tiktok.com")!!
                ),
                locks = true,
            )

            GroupTemplate.GRAY_ESSENTIALS -> base.copy(
                listType = AppExceptionListType.NOT_LIST,
                apps = essentials(context, ranked),
                grayAfterMs = 0L,
            )

            GroupTemplate.NIGHTS_OFF -> base.copy(
                apps = ranked.filter { it.isSocial() || it.isVideo() || it.isGame() }
                    .mapTo(mutableSetOf()) { it.packageName },
                locks = true,
                schedule = SchedulePreset.NIGHTS,
            )

            GroupTemplate.EMPTY -> base.copy(name = "")
        }
    }

    /** The websites the setup suggests for [template], as chips next to the apps. */
    fun websiteSuggestions(template: GroupTemplate): List<String> = when (template) {
        GroupTemplate.FEEDS -> listOf(
            Websites.collectionEntry(WebsiteCollection.SOCIAL),
            Websites.siteEntry("reddit.com")!!, Websites.siteEntry("x.com")!!,
            Websites.siteEntry("youtube.com")!!
        )

        GroupTemplate.SHORT_VIDEO -> listOf(
            Websites.siteEntry("youtube.com/shorts")!!, Websites.siteEntry("tiktok.com")!!,
            Websites.siteEntry("instagram.com/reels")!!
        )

        GroupTemplate.GRAY_ESSENTIALS -> listOf(
            Websites.siteEntry("maps.google.com")!!, Websites.siteEntry("wikipedia.org")!!
        )

        GroupTemplate.NIGHTS_OFF -> listOf(
            Websites.collectionEntry(WebsiteCollection.SOCIAL),
            Websites.collectionEntry(WebsiteCollection.VIDEO),
            Websites.siteEntry("reddit.com")!!
        )

        GroupTemplate.EMPTY -> listOf(
            Websites.collectionEntry(WebsiteCollection.SOCIAL),
            Websites.collectionEntry(WebsiteCollection.VIDEO),
            Websites.siteEntry("youtube.com")!!, Websites.siteEntry("reddit.com")!!
        )
    }

    /**
     * The apps a gray phone still needs in color: the dialer, the camera, maps, the messages app
     * and the messengers that are installed. Resolved on this phone rather than by package name,
     * since every manufacturer ships its own.
     */
    private fun essentials(context: Context, ranked: List<RankedAppRow>): Set<String> {
        val packageManager = context.packageManager
        val installed = ranked.mapTo(mutableSetOf()) { it.packageName }
        val resolved = mutableSetOf<String>()
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { resolved += it }
        }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context)?.let { resolved += it } }
        listOf(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=home")),
        ).forEach { intent ->
            runCatching {
                packageManager.queryIntentActivities(intent, 0)
                    .mapTo(resolved) { it.activityInfo.packageName }
            }
        }
        resolved += messengers
        // whatever the phone resolved that the list also knows, plus the known ones in the list
        return resolved.filterTo(mutableSetOf()) { it in installed || packageManager.isInstalled(it) }
    }

    private fun android.content.pm.PackageManager.isInstalled(packageName: String): Boolean =
        runCatching { getApplicationInfo(packageName, 0) }.isSuccess

    private fun RankedAppRow.isSocial() =
        knownCategory == KnownAppCategory.SOCIAL || osCategory == DistractingAppsHeuristic.OS_CATEGORY_SOCIAL

    private fun RankedAppRow.isVideo() =
        knownCategory == KnownAppCategory.VIDEO || osCategory == DistractingAppsHeuristic.OS_CATEGORY_VIDEO

    private fun RankedAppRow.isGame() = osCategory == DistractingAppsHeuristic.OS_CATEGORY_GAME
}
