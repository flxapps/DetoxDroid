package com.flx_apps.digitaldetox.ui.screens.rule_groups

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.rules.BudgetPeriod
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.util.KnownApps
import com.flx_apps.digitaldetox.util.RankedAppRow
import com.flx_apps.digitaldetox.websites.WebsiteCollection
import com.flx_apps.digitaldetox.websites.Websites
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** The headings the templates are listed under. */
enum class TemplateSection(@StringRes val titleRes: Int) {
    WHAT(R.string.ruleGroups_templates_what),
    WHEN(R.string.ruleGroups_templates_when),
    PHONE(R.string.ruleGroups_templates_phone),
}

/**
 * The ready-made starting points for a new group. Everything in them can be changed afterwards.
 * Each has one of the app's colors, the way the home screen's chart hands them out.
 */
enum class GroupTemplate(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    @ColorRes val colorRes: Int,
    val section: TemplateSection,
) {
    FEEDS(
        R.string.ruleGroups_template_feeds, R.string.ruleGroups_template_feeds_description,
        Icons.Default.Forum, R.color.orange, TemplateSection.WHAT
    ),
    VIDEOS_AND_GAMES(
        R.string.ruleGroups_template_videosAndGames, R.string.ruleGroups_template_videosAndGames_description,
        Icons.Default.SportsEsports, R.color.green, TemplateSection.WHAT
    ),
    SHOPPING(
        R.string.ruleGroups_template_shopping, R.string.ruleGroups_template_shopping_description,
        Icons.Default.ShoppingBag, R.color.yellow, TemplateSection.WHAT
    ),
    GAMBLING_AND_PORN(
        R.string.ruleGroups_template_gamblingAndPorn, R.string.ruleGroups_template_gamblingAndPorn_description,
        Icons.Default.Block, R.color.pink, TemplateSection.WHAT
    ),
    NIGHTS_OFF(
        R.string.ruleGroups_template_nightsOff, R.string.ruleGroups_template_nightsOff_description,
        Icons.Default.Bedtime, R.color.blue, TemplateSection.WHEN
    ),
    WORK_HOURS(
        R.string.ruleGroups_template_workHours, R.string.ruleGroups_template_workHours_description,
        Icons.Default.Work, R.color.blue, TemplateSection.WHEN
    ),
    GRAY_ESSENTIALS(
        R.string.ruleGroups_template_grayEssentials, R.string.ruleGroups_template_grayEssentials_description,
        Icons.Default.Contrast, R.color.purple, TemplateSection.PHONE
    ),
    EMPTY(
        R.string.ruleGroups_template_empty, R.string.ruleGroups_template_empty_description,
        Icons.Default.Add, R.color.gray, TemplateSection.PHONE
    ),
}

/** The schedules the setup offers as one tap each. */
enum class SchedulePreset(@StringRes val titleRes: Int) {
    ALWAYS(R.string.ruleGroups_schedulePreset_always),
    WEEKDAYS(R.string.ruleGroups_schedulePreset_weekdays),
    WORK_HOURS(R.string.ruleGroups_schedulePreset_workHours),
    EVENINGS(R.string.ruleGroups_schedulePreset_evenings),
    NIGHTS(R.string.ruleGroups_schedulePreset_nights),
    MORNINGS(R.string.ruleGroups_schedulePreset_mornings),
    WEEKENDS(R.string.ruleGroups_schedulePreset_weekends);

    fun rules(): Set<FeatureScheduleRule> = when (this) {
        ALWAYS -> emptySet()
        WEEKDAYS -> setOf(FeatureScheduleRule(weekdays, LocalTime.of(10, 0), LocalTime.of(22, 0)))
        WORK_HOURS -> setOf(FeatureScheduleRule(weekdays, LocalTime.of(9, 0), LocalTime.of(17, 0)))
        EVENINGS -> setOf(FeatureScheduleRule(emptyList(), LocalTime.of(18, 0), LocalTime.of(23, 0)))
        NIGHTS -> setOf(FeatureScheduleRule(emptyList(), LocalTime.of(22, 0), LocalTime.of(7, 0)))
        // from well before anyone gets up, so an early riser is covered too
        MORNINGS -> setOf(FeatureScheduleRule(emptyList(), LocalTime.of(4, 0), LocalTime.of(9, 0)))
        WEEKENDS -> setOf(
            FeatureScheduleRule(
                listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), LocalTime.MIDNIGHT, LocalTime.MIDNIGHT
            )
        )
    }

    private companion object {
        val weekdays = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
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

/**
 * What each template picks and sets. The apps come from [KnownApps] and the store's categories,
 * the websites from the collections that ship with the app and a few named sites.
 */
object GroupTemplates {
    private val RankedAppRow.isFeed get() = KnownApps.isFeed(packageName, osCategory)
    private val RankedAppRow.isStreaming get() = KnownApps.isStreaming(packageName, osCategory)
    private val RankedAppRow.isNews get() = KnownApps.isNews(packageName, osCategory)
    private val RankedAppRow.isShop get() = KnownApps.isShop(packageName)
    private val RankedAppRow.isGame get() = KnownApps.isGame(packageName, osCategory)
    private val RankedAppRow.isBetting get() = KnownApps.isBetting(packageName)

    private fun site(input: String): String = requireNotNull(Websites.siteEntry(input)) { "not a site: $input" }
    private fun collection(collection: WebsiteCollection): String = Websites.collectionEntry(collection)

    private val social get() = collection(WebsiteCollection.SOCIAL)
    private val video get() = collection(WebsiteCollection.VIDEO)

    private fun minutes(minutes: Long): Long = TimeUnit.MINUTES.toMillis(minutes)

    /** Builds the draft [template] stands for, picking apps out of [installed]. */
    fun draft(context: Context, template: GroupTemplate, installed: List<RankedAppRow>): GroupDraft {
        fun pick(predicate: (RankedAppRow) -> Boolean): Set<String> =
            installed.filter(predicate).mapTo(mutableSetOf()) { it.packageName }

        val base = GroupDraft(
            template = template,
            name = context.getString(template.titleRes),
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
            // the whole ladder: scroll breaks all along, gray before the end, then the lock. News
            // apps are feeds too, the same thumb does the same thing in them
            GroupTemplate.FEEDS -> base.copy(
                apps = pick { it.isFeed || it.isNews },
                websites = setOf(social, site("youtube.com/shorts")),
                budgetMs = minutes(30),
                grayAfterMs = minutes(20),
                locks = true,
                scrollBreakAfterMs = BreakDoomScrollingFeature.DefaultTimeUntilWarningMs,
            )

            GroupTemplate.VIDEOS_AND_GAMES -> base.copy(
                apps = pick { it.isStreaming || it.isGame },
                websites = setOf(video),
                budgetMs = minutes(60),
                grayAfterMs = minutes(45),
                locks = true,
            )

            // the wait is the point: an impulse rarely survives ten seconds
            GroupTemplate.SHOPPING -> base.copy(
                apps = pick { it.isShop },
                websites = setOf(collection(WebsiteCollection.SHOPPING)),
                waitMs = DefaultWaitMs,
                budgetMs = minutes(30),
                locks = true,
            )

            GroupTemplate.GAMBLING_AND_PORN -> base.copy(
                apps = pick { it.isBetting },
                websites = setOf(collection(WebsiteCollection.GAMBLING), collection(WebsiteCollection.ADULT)),
                locks = true,
            )

            GroupTemplate.NIGHTS_OFF -> base.copy(
                apps = pick { it.isFeed || it.isNews || it.isStreaming || it.isGame },
                websites = setOf(social, video),
                locks = true,
                schedule = SchedulePreset.NIGHTS,
            )

            GroupTemplate.WORK_HOURS -> base.copy(
                apps = pick { it.isFeed || it.isNews || it.isStreaming || it.isGame || it.isShop },
                websites = setOf(social, video),
                locks = true,
                schedule = SchedulePreset.WORK_HOURS,
            )

            GroupTemplate.GRAY_ESSENTIALS -> base.copy(
                listType = AppExceptionListType.NOT_LIST,
                apps = essentials(context, installed),
                grayAfterMs = 0L,
            )

            GroupTemplate.EMPTY -> base.copy(name = "")
        }
    }

    /** The websites the setup offers for [template] as chips, the draft's own among them. */
    fun websiteSuggestions(template: GroupTemplate): List<String> = when (template) {
        GroupTemplate.FEEDS -> listOf(
            social, site("youtube.com/shorts"), site("instagram.com/reels"), site("youtube.com"), site("news.google.com")
        )

        GroupTemplate.VIDEOS_AND_GAMES -> listOf(video)
        GroupTemplate.SHOPPING -> listOf(collection(WebsiteCollection.SHOPPING))
        GroupTemplate.GAMBLING_AND_PORN ->
            listOf(collection(WebsiteCollection.GAMBLING), collection(WebsiteCollection.ADULT))

        GroupTemplate.NIGHTS_OFF, GroupTemplate.WORK_HOURS -> listOf(social, video)
        GroupTemplate.GRAY_ESSENTIALS -> listOf(site("maps.google.com"), site("wikipedia.org"))
        GroupTemplate.EMPTY -> listOf(social, video, site("youtube.com"), site("news.google.com"))
    }

    /**
     * The apps a gray phone still needs in color: the dialer, the camera, maps, the messages app,
     * the clock, the calendar and the messengers that are installed. The phone's own defaults are
     * resolved rather than named, since every manufacturer ships its own.
     */
    private fun essentials(context: Context, installed: List<RankedAppRow>): Set<String> {
        val packageManager = context.packageManager
        val listed = installed.mapTo(mutableSetOf()) { it.packageName }
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
        resolved += KnownApps.messengers
        resolved += KnownApps.clocksAndCalendars
        return resolved.filterTo(mutableSetOf()) { it in listed || packageManager.isInstalled(it) }
    }

    private fun android.content.pm.PackageManager.isInstalled(packageName: String): Boolean =
        runCatching { getApplicationInfo(packageName, 0) }.isSuccess
}
