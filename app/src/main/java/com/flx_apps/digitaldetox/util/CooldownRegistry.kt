package com.flx_apps.digitaldetox.util

/**
 * Registry of app cooldowns after a doom-scrolling incident: once the user left (or was guided
 * out of) an app, re-entering it is blocked until the cooldown expires.
 *
 * A cooldown either covers the whole app (`surfaceId == null`) or just one scroll surface within
 * it, keyed by the view-id resource name of the scroll view that caused the incident — e.g. only
 * Instagram's reels pager, while the DM list stays usable. Surface scoping errs on the permissive
 * side: a scroll event without a surface id is only stopped by whole-app cooldowns, so harmless
 * surfaces are never locked by mistake.
 */
class CooldownRegistry(private val nowMs: () -> Long = { System.currentTimeMillis() }) {

    /** A cooldown a scroll event ran into: when it ends and whether it locks the whole app. */
    data class ActiveCooldown(val endsAtMs: Long, val wholeApp: Boolean)

    private data class Key(val packageName: String, val surfaceId: String?)

    private val endTimes = HashMap<Key, Long>()

    /** Starts (or restarts) a cooldown for [packageName], scoped to [surfaceId] if non-null. */
    @Synchronized
    fun start(packageName: String, surfaceId: String?, durationMs: Long) {
        endTimes[Key(packageName, surfaceId)] = nowMs() + durationMs
    }

    /** Lifts all cooldowns of [packageName]. */
    @Synchronized
    fun clear(packageName: String) {
        endTimes.keys.removeAll { it.packageName == packageName }
    }

    /**
     * Returns the cooldown that a scroll event in [packageName] on [surfaceId] runs into, or null
     * if that surface may be scrolled. A whole-app cooldown matches any surface; a surface
     * cooldown only matches its own surface.
     */
    @Synchronized
    fun activeCooldownFor(packageName: String, surfaceId: String?): ActiveCooldown? {
        prune()
        endTimes[Key(packageName, null)]?.let { return ActiveCooldown(it, wholeApp = true) }
        if (surfaceId != null) {
            endTimes[Key(packageName, surfaceId)]?.let {
                return ActiveCooldown(it, wholeApp = false)
            }
        }
        return null
    }

    /**
     * Returns the end time of a whole-app cooldown of [packageName], or null. Checked when the
     * app is opened; surface-scoped cooldowns deliberately don't lock the app itself.
     */
    @Synchronized
    fun appLockEndMs(packageName: String): Long? {
        prune()
        return endTimes[Key(packageName, null)]
    }

    private fun prune() {
        val now = nowMs()
        endTimes.values.removeAll { it <= now }
    }
}
