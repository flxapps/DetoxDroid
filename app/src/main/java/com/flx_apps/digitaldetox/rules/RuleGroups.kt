package com.flx_apps.digitaldetox.rules

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.data.DataStore
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.removeKeysWithPrefix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.SecureRandom
import java.time.LocalDateTime

/**
 * The groups the user has, in the order they are shown. Each group keeps its own fields under its
 * id (see [RuleGroup]); this object only keeps the ids, so the settings file carries the groups
 * without knowing about them.
 *
 * Reads come from a snapshot, so the service can ask for the groups on every app switch while a
 * screen is editing one. Every change ends in [onChanged], which is what makes it take effect.
 */
object RuleGroups {
    private const val ID_PREFIX = "group_"
    private const val ORDER_SEPARATOR = ","

    /**
     * Stands for every group at once: what the onboarding and the brake ask about when they mean
     * all of them, and what the commitment password's lock list held before groups could be
     * locked one by one.
     */
    const val LOCK_ID = "RuleGroups"

    /** The ids in display order. */
    private var order: String by DataStoreProperty(stringPreferencesKey("ruleGroups_order"), "")

    /**
     * Which shape the stored groups have. 0 means no groups were ever written, which is what the
     * migration from the feature lists looks for.
     * @see RuleGroupsMigration
     */
    var storeVersion: Int by DataStoreProperty(intPreferencesKey("ruleGroups_version"), 0)

    @Volatile
    private var groups: List<RuleGroup> = order.split(ORDER_SEPARATOR)
        .filter { it.isNotBlank() }
        .map { RuleGroup(it) }

    private val _changes = MutableStateFlow(0L)

    /** Bumped by [onChanged], for the screens that show the groups. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    /** Every group, in display order. */
    val all: List<RuleGroup> get() = groups

    fun get(id: GroupId): RuleGroup? = groups.firstOrNull { it.id == id }

    /** Whether [id] names a group rather than a feature, by the prefix every group id carries. */
    fun isGroupId(id: String): Boolean = id.startsWith(ID_PREFIX)

    /** The groups that are switched on. */
    fun enabled(): List<RuleGroup> = groups.filter { it.isEnabled }

    /** The groups that count right now, see [RuleGroup.isActiveAt]. */
    fun active(atDateTime: LocalDateTime = LocalDateTime.now()): List<RuleGroup> =
        groups.filter { it.isActiveAt(atDateTime) }

    /** Whether any switched-on group has [step], which is when the step's feature has work. */
    fun usesStep(step: Step): Boolean = groups.any { it.isEnabled && it.hasStep(step) }

    /**
     * Adds a group and hands it to [configure] before anything can see it. The caller calls
     * [onChanged] once it is done with it.
     */
    @Synchronized
    fun create(configure: RuleGroup.() -> Unit = {}): RuleGroup {
        val group = RuleGroup(newId()).apply(configure)
        group.normalize()
        groups = groups + group
        order = groups.joinToString(ORDER_SEPARATOR) { it.id }
        return group
    }

    /** Removes the group and every key it stored. */
    @Synchronized
    fun delete(id: GroupId) {
        if (groups.none { it.id == id }) return
        groups = groups.filterNot { it.id == id }
        order = groups.joinToString(ORDER_SEPARATOR) { it.id }
        GroupBudgets.forget(id)
        DataStore.removeKeysWithPrefix("${id}_")
        onChanged()
    }

    /** Moves the group with [id] to [index] in the display order. */
    @Synchronized
    fun move(id: GroupId, index: Int) {
        val group = get(id) ?: return
        val rest = groups.filterNot { it.id == id }.toMutableList()
        rest.add(index.coerceIn(0, rest.size), group)
        groups = rest
        order = groups.joinToString(ORDER_SEPARATOR) { it.id }
        _changes.update { it + 1 }
    }

    /**
     * [name], or [name] with a number behind it where a group of that name exists already.
     */
    fun uniqueName(name: String): String {
        val taken = groups.mapTo(mutableSetOf()) { it.name }
        if (name !in taken) return name
        var n = 2
        while ("$name $n" in taken) n++
        return "$name $n"
    }

    /**
     * Makes a change to any group take effect: the screens redraw, the step features start or
     * stop, and the app in front is judged again.
     */
    fun onChanged() {
        groups.forEach { it.normalize() }
        _changes.update { it + 1 }
        Rules.onGroupsChanged()
    }

    private fun newId(): GroupId {
        val random = SecureRandom()
        while (true) {
            val id = ID_PREFIX + List(8) { "0123456789abcdef"[random.nextInt(16)] }.joinToString("")
            if (groups.none { it.id == id }) return id
        }
    }
}
