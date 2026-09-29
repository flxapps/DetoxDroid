package com.flx_apps.digitaldetox.util

/**
 * Counts how often something happened within a sliding window. Kept in memory only: what it counts
 * towards is a tightening, so a count lost with the process errs on the lenient side.
 */
class RecentEvents {
    private val times = ArrayDeque<Long>()

    /**
     * Notes an event at [nowMs] and returns how many there were within the last [windowMs],
     * this one included.
     */
    @Synchronized
    fun record(nowMs: Long, windowMs: Long): Int {
        // a clock that went backwards makes everything noted before it look like the future
        times.removeAll { it > nowMs || nowMs - it >= windowMs }
        times.addLast(nowMs)
        return times.size
    }

    @Synchronized
    fun clear() = times.clear()
}
