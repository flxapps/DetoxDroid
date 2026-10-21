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

/**
 * The parts of a group's sentence that open an editor when tapped: the three that lead to a
 * screen, and the values of the steps, which open their pickers in place.
 */
enum class SentencePart {
    SCOPE, BUDGET, SCHEDULE, STEPS, WAIT, GRAY, SCROLL;
}

/**
 * A group's rule in words: the main sentence, and a second paragraph with what happens as the
 * budget runs down, or that there is none.
 */
class GroupSentenceText(val main: AnnotatedString, val steps: AnnotatedString?)

/**
 * A group's rule as sentences whose parts are links: "Instagram, TikTok and 4 websites get 30
 * minutes on weekdays from 10:00 to 22:00." and below it "Each open waits 10s first. From 20 min
 * on, they turn gray, and after that, they lock." The screen stays readable however much the
 * group expresses, and only what can be changed is a link: the apps, the budget, the schedule,
 * and the values of the steps.
 *
 * The templates come from the string resources with the parts as `{scope}`, `{budget}`,
 * `{schedule}`, `{steps}`, `{wait}`, `{gray}` and `{scroll}`, so every language orders them its
 * own way. The verbs agree with how many things the group names, see [GroupTexts.scope].
 */
object GroupSentence {
    fun build(
        context: Context,
        group: RuleGroup,
        linkStyle: SpanStyle,
        onPart: (SentencePart) -> Unit,
    ): GroupSentenceText {
        val links = Links(linkStyle, onPart)
        val scope = GroupTexts.scope(context, group)
            ?: return GroupSentenceText(
                main = render(
                    links,
                    Template(
                        context.getString(R.string.ruleGroups_sentence_empty),
                        mapOf(SentencePart.SCOPE to context.getString(R.string.ruleGroups_scope_nothingYet))
                    )
                ),
                steps = null
            )
        val (scopeText, count) = scope
        val schedule = GroupTexts.schedule(context, group.scheduleRules)
        val scheduleJoin = schedule?.let { context.getString(R.string.ruleGroups_sentence_scheduleJoin, it) }
        val values = stepValues(context, group)
        if (group.hasBudget) {
            val main = Template(
                context.resources.getQuantityString(R.plurals.ruleGroups_sentence_budget, count),
                values + mapOf(
                    SentencePart.SCOPE to scopeText,
                    SentencePart.BUDGET to GroupTexts.budget(context, group).orEmpty(),
                    SentencePart.SCHEDULE to schedule.orEmpty(),
                ),
                scheduleJoin
            )
            val steps = stepsWithBudget(context, group, count).map { Template(it, values) }
            return GroupSentenceText(render(links, main), render(links, *steps.toTypedArray()))
        }
        val stepsPhrase = stepsWithoutBudget(context, group, count)
        val main = Template(
            context.resources.getQuantityString(R.plurals.ruleGroups_sentence_noBudget, count),
            values + mapOf(
                SentencePart.SCOPE to scopeText,
                SentencePart.STEPS to (stepsPhrase
                    ?: context.resources.getQuantityString(R.plurals.ruleGroups_steps_nothingYet, count)),
                SentencePart.SCHEDULE to schedule.orEmpty(),
            ),
            scheduleJoin,
            // the steps are words around their values, only "no steps yet" is a link of its own
            plain = if (stepsPhrase != null) setOf(SentencePart.STEPS) else emptySet()
        )
        // the budget there is none of, as the way to add one
        val noBudget = Template(
            context.getString(
                if (stepsPhrase != null) R.string.ruleGroups_noBudget_withSteps else R.string.ruleGroups_noBudget
            ),
            mapOf(SentencePart.BUDGET to context.getString(R.string.ruleGroups_noBudget_value))
        )
        return GroupSentenceText(render(links, main), render(links, noBudget))
    }

    /**
     * Whether the group's lock and wait reach anything: its apps when it names them, its websites
     * when it has any. A lock a group of all apps but a few keeps switched on holds nothing.
     */
    private val RuleGroup.holdsBack: Boolean get() = canHoldBackApps || reachesWebsites

    private val RuleGroup.locksHere: Boolean get() = locks && holdsBack

    private val RuleGroup.waitsHere: Boolean get() = waits && holdsBack

    /** The values of the steps that are on, by the part that stands for them. */
    private fun stepValues(context: Context, group: RuleGroup): Map<SentencePart, String> = buildMap {
        if (group.waitsHere) put(SentencePart.WAIT, GroupTexts.seconds(context, group.waitBeforeOpeningMs))
        if (group.grays && !group.graysFromStart) {
            put(SentencePart.GRAY, GroupTexts.minutes(context, group.grayAfterMs))
        }
        if (group.breaksScrolling) {
            put(SentencePart.SCROLL, GroupTexts.minutes(context, group.scrollBreakAfterMs))
        }
        put(SentencePart.STEPS, context.getString(R.string.ruleGroups_steps_nothingHappens))
    }

    /**
     * What happens around a budget, as sentences: what applies while there is budget left (the
     * wait, gray from the start, scroll breaks), then what happens when it is used up.
     */
    private fun stepsWithBudget(context: Context, group: RuleGroup, count: Int): List<String> {
        val res = context.resources
        val sentences = mutableListOf<String>()
        if (group.waitsHere) sentences += context.getString(R.string.ruleGroups_before_wait)
        if (group.grays && group.graysFromStart) {
            sentences += context.getString(R.string.ruleGroups_before_grayAlways)
        }
        if (group.breaksScrolling) sentences += context.getString(R.string.ruleGroups_before_scroll)
        val graysInside = group.grays && !group.graysFromStart && group.grayAfterMs < group.budgetMs
        val graysAtEnd = group.grays && !group.graysFromStart && group.grayAfterMs >= group.budgetMs
        when {
            graysInside && group.locksHere ->
                sentences += res.getQuantityString(R.plurals.ruleGroups_after_grayFromThenLock, count)

            graysInside -> sentences += res.getQuantityString(R.plurals.ruleGroups_after_grayFrom, count)
            group.locksHere -> sentences += res.getQuantityString(R.plurals.ruleGroups_after_lock, count)
            graysAtEnd -> sentences += res.getQuantityString(R.plurals.ruleGroups_after_gray, count)
        }
        // a budget that ends in nothing is said so, whatever runs while it lasts
        if (!group.locksHere && !graysAtEnd) {
            sentences += context.getString(R.string.ruleGroups_after_nothing)
        }
        return sentences
    }

    /** "are gray", "are locked": the steps of a group without a budget, as one phrase. */
    private fun stepsWithoutBudget(context: Context, group: RuleGroup, count: Int): String? {
        val res = context.resources
        val phrases = buildList {
            if (group.waitsHere) add(res.getQuantityString(R.plurals.ruleGroups_steps_wait, count))
            if (group.grays) add(res.getQuantityString(R.plurals.ruleGroups_steps_gray, count))
            if (group.breaksScrolling) add(res.getQuantityString(R.plurals.ruleGroups_steps_scroll, count))
            if (group.locksHere) add(res.getQuantityString(R.plurals.ruleGroups_steps_lock, count))
        }
        return phrases.takeIf { it.isNotEmpty() }?.let { GroupTexts.joinNatural(it) }
    }

    private fun render(links: Links, vararg templates: Template): AnnotatedString =
        buildAnnotatedString {
            templates.forEachIndexed { index, template ->
                if (index > 0) append(" ")
                template.appendTo(this, links)
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
     * One sentence template with the texts for its tokens. A [plain] part, or one whose text holds
     * tokens of its own (the steps of a group without a budget hold their values), is written out
     * as words with those linked, so a value is a link and the words around it are not.
     */
    private class Template(
        val template: String,
        val parts: Map<SentencePart, String>,
        val scheduleJoin: String? = null,
        val plain: Set<SentencePart> = emptySet(),
    ) {
        fun appendTo(builder: AnnotatedString.Builder, links: Links) =
            appendTokens(builder, links, template)

        private fun appendTokens(builder: AnnotatedString.Builder, links: Links, text: String) {
            with(links) {
                var last = 0
                TOKENS.findAll(text).forEach { match ->
                    builder.append(text.substring(last, match.range.first))
                    last = match.range.last + 1
                    val part = SentencePart.valueOf(match.groupValues[1].uppercase())
                    val partText = parts[part].orEmpty()
                    when {
                        partText.isEmpty() -> {}
                        part == SentencePart.SCHEDULE -> {
                            // the join (a space, a comma) stays plain, only the words are the link
                            val joined = scheduleJoin ?: partText
                            builder.append(joined.substring(0, joined.indexOf(partText).coerceAtLeast(0)))
                            builder.link(part, partText)
                        }

                        part in plain || TOKENS.containsMatchIn(partText) ->
                            appendTokens(builder, links, partText)

                        else -> builder.link(part, partText)
                    }
                }
                builder.append(text.substring(last))
            }
        }
    }

    // both braces escaped: Android's regex engine, unlike the JVM's, refuses a bare "}"
    private val TOKENS = Regex("\\{(scope|budget|schedule|steps|wait|gray|scroll)\\}")
}
