package com.flx_apps.digitaldetox.ui.screens.rule_groups

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.rules.RuleGroup

/** The parts of a group's sentence that open an editor when tapped. */
enum class SentencePart { SCOPE, BUDGET, SCHEDULE, STEPS }

/**
 * A group's rule as one sentence whose parts are links: "Instagram, TikTok and 4 websites get 30
 * minutes on weekdays from 10:00 to 22:00. After that, they turn gray, then lock." The screen
 * stays one readable sentence however much the group expresses.
 *
 * The templates come from the string resources with `{scope}`, `{budget}`, `{schedule}` and
 * `{steps}` as tokens, so every language orders the parts its own way. The verbs agree with
 * how many things the group names, see [GroupTexts.scope].
 */
object GroupSentence {
    /**
     * Builds the sentence for [group]. A tap on a part calls [onPart] with it.
     */
    fun build(
        context: Context,
        group: RuleGroup,
        linkStyle: SpanStyle,
        onPart: (SentencePart) -> Unit,
    ): AnnotatedString {
        val links = Links(linkStyle, onPart)
        val scope = GroupTexts.scope(context, group)
        val sentences = mutableListOf<Template>()
        if (scope == null) {
            sentences += Template(
                context.getString(R.string.ruleGroups_sentence_empty),
                mapOf(
                    SentencePart.SCOPE to context.getString(R.string.ruleGroups_scope_nothingYet),
                )
            )
        } else {
            val (scopeText, count) = scope
            val schedule = GroupTexts.schedule(context, group.scheduleRules)
            val scheduleText = schedule?.let { context.getString(R.string.ruleGroups_sentence_scheduleJoin, it) }
            val parts = mutableMapOf(
                SentencePart.SCOPE to scopeText,
                SentencePart.SCHEDULE to (schedule ?: ""),
            )
            if (group.hasBudget) {
                parts[SentencePart.BUDGET] = GroupTexts.budget(context, group).orEmpty()
                sentences += Template(
                    context.resources.getQuantityString(R.plurals.ruleGroups_sentence_budget, count),
                    parts, scheduleJoin = scheduleText
                )
                stepsWithBudget(context, group, count).forEach {
                    sentences += Template(it, mapOf(SentencePart.STEPS to it), whole = SentencePart.STEPS)
                }
            } else {
                val steps = stepsWithoutBudget(context, group, count)
                parts[SentencePart.STEPS] = steps
                    ?: context.resources.getQuantityString(R.plurals.ruleGroups_steps_nothingYet, count)
                sentences += Template(
                    context.resources.getQuantityString(R.plurals.ruleGroups_sentence_noBudget, count),
                    parts, scheduleJoin = scheduleText
                )
            }
        }
        return buildAnnotatedString {
            sentences.forEachIndexed { index, template ->
                if (index > 0) append(" ")
                template.appendTo(this, links)
            }
        }
    }

    /** How the parts are styled and what a tap on one does. */
    private class Links(val style: SpanStyle, val onPart: (SentencePart) -> Unit) {
        fun AnnotatedString.Builder.link(part: SentencePart, text: String) {
            withLink(
                LinkAnnotation.Clickable(
                    tag = part.name,
                    styles = TextLinkStyles(style = style),
                    linkInteractionListener = { onPart(part) }
                )
            ) { append(text) }
        }
    }

    /**
     * What happens around a budget, as extra sentences: what applies while there is budget left
     * (the wait, gray from the start, scroll breaks), then what happens when it is used up.
     */
    private fun stepsWithBudget(context: Context, group: RuleGroup, count: Int): List<String> {
        val res = context.resources
        val sentences = mutableListOf<String>()
        if (group.waits) {
            sentences += context.getString(
                R.string.ruleGroups_before_wait, GroupTexts.seconds(context, group.waitBeforeOpeningMs)
            )
        }
        if (group.grays && group.graysFromStart) {
            sentences += context.getString(R.string.ruleGroups_before_grayAlways)
        }
        if (group.breaksScrolling) {
            sentences += context.getString(
                R.string.ruleGroups_before_scroll, GroupTexts.minutes(context, group.scrollBreakAfterMs)
            )
        }
        val graysInside = group.grays && !group.graysFromStart && group.grayAfterMs < group.budgetMs
        val graysAtEnd = group.grays && !group.graysFromStart && group.grayAfterMs >= group.budgetMs
        val grayFrom = GroupTexts.minutes(context, group.grayAfterMs)
        when {
            graysInside && group.locks -> sentences += res.getQuantityString(
                R.plurals.ruleGroups_after_grayFromThenLock, count, grayFrom
            )

            graysInside -> sentences += res.getQuantityString(
                R.plurals.ruleGroups_after_grayFrom, count, grayFrom
            )

            group.locks -> sentences += res.getQuantityString(R.plurals.ruleGroups_after_lock, count)
            graysAtEnd -> sentences += res.getQuantityString(R.plurals.ruleGroups_after_gray, count)
        }
        if (sentences.isEmpty()) sentences += context.getString(R.string.ruleGroups_after_nothing)
        return sentences
    }

    /** "are gray", "are locked every day from 22:00 to 07:00": the steps of a group without a budget. */
    private fun stepsWithoutBudget(context: Context, group: RuleGroup, count: Int): String? {
        val res = context.resources
        val phrases = buildList {
            if (group.waits) {
                add(
                    res.getQuantityString(
                        R.plurals.ruleGroups_steps_wait, count,
                        GroupTexts.seconds(context, group.waitBeforeOpeningMs)
                    )
                )
            }
            if (group.grays) add(res.getQuantityString(R.plurals.ruleGroups_steps_gray, count))
            if (group.breaksScrolling) {
                add(
                    res.getQuantityString(
                        R.plurals.ruleGroups_steps_scroll, count,
                        GroupTexts.minutes(context, group.scrollBreakAfterMs)
                    )
                )
            }
            if (group.locks) add(res.getQuantityString(R.plurals.ruleGroups_steps_lock, count))
        }
        return phrases.takeIf { it.isNotEmpty() }?.let { GroupTexts.joinNatural(it) }
    }

    /**
     * One sentence template with the texts for its tokens. A [whole] part tags the entire
     * template, for sentences that are one link from start to end.
     */
    private class Template(
        val template: String,
        val parts: Map<SentencePart, String>,
        val scheduleJoin: String? = null,
        val whole: SentencePart? = null,
    ) {
        fun appendTo(builder: AnnotatedString.Builder, links: Links) = with(links) {
            if (whole != null) {
                builder.link(whole, template)
                return
            }
            // both braces escaped: Android's regex engine, unlike the JVM's, refuses a bare "}"
            val tokens = Regex("\\{(scope|budget|schedule|steps)\\}")
            var last = 0
            tokens.findAll(template).forEach { match ->
                builder.append(template.substring(last, match.range.first))
                last = match.range.last + 1
                val part = SentencePart.valueOf(match.groupValues[1].uppercase())
                if (part == SentencePart.SCHEDULE) {
                    // the join (a space, a comma) stays plain, only the words are the link
                    val schedule = parts[part].orEmpty()
                    if (schedule.isEmpty()) return@forEach
                    val joined = scheduleJoin ?: schedule
                    val at = joined.indexOf(schedule)
                    builder.append(joined.substring(0, at.coerceAtLeast(0)))
                    builder.link(part, schedule)
                    return@forEach
                }
                builder.link(part, parts[part].orEmpty())
            }
            builder.append(template.substring(last))
        }
    }
}
