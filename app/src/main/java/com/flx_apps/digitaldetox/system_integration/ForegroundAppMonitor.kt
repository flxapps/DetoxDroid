package com.flx_apps.digitaldetox.system_integration

/**
 * A window event reduced to the three things that decide which app is in front.
 */
data class WindowSignal(
    val packageName: String,
    val className: String,
    val isFullScreen: Boolean,
)

/**
 * What a [WindowSignal] means for the app in front.
 */
sealed interface ForegroundDecision {
    /**
     * The window does not change which app is in front. Whatever was published stands, and a
     * switch that was still settling keeps settling.
     */
    data object Unchanged : ForegroundDecision

    /**
     * The window that was still settling turned out not to be a switch after all, because the app
     * that was in front before is in front again. Drop the settle callback.
     */
    data object Cancelled : ForegroundDecision

    /**
     * [packageName] is in front and the features can be told about it now.
     */
    data class Changed(val packageName: String, val isFullScreen: Boolean) : ForegroundDecision

    /**
     * [packageName] may be in front. Call [ForegroundAppMonitor.settle] after [delayMs] and act on
     * what it returns. Any signal arriving in the meantime replaces or cancels this one.
     */
    data class Settling(val packageName: String, val delayMs: Long) : ForegroundDecision
}

/**
 * Decides which app is in front from the stream of window events the accessibility service sees,
 * so that every feature reads one answer instead of interpreting raw events for itself.
 *
 * Two kinds of window never mean "the user switched apps". Transient surfaces are dropped outright
 * ([isTransient]). The rest is a timing problem: opening a share sheet, swiping between apps or
 * pulling up recents makes two packages alternate for a few hundred milliseconds, and a feature
 * that acts on each one in turn (the screen filter above all) makes the screen flicker for as long
 * as it lasts. So a switch that follows hard on the heels of the previous one has to hold still for
 * [settleWindowMs] before it counts, and coming straight back to the app that was already in front
 * cancels it. A switch that arrives out of a quiet period is taken at face value and published
 * immediately, which is the case every deliberate app switch falls into.
 */
class ForegroundAppMonitor(
    private val ownPackageName: String,
    private val settleWindowMs: Long = DEFAULT_SETTLE_WINDOW_MS,
) {
    companion object {
        /**
         * How much quiet a window event needs around it to be taken at face value. Long enough to
         * cover the back-and-forth of an app switch animation, short enough that the screen filter
         * still lands before the user has read anything.
         */
        const val DEFAULT_SETTLE_WINDOW_MS = 300L

        /**
         * Class-name prefixes of transient system surfaces (keyboard, volume dialog, the share
         * sheet) whose window events must not be treated as "an app was opened".
         *
         * The share sheet is the reason the list carries three spellings of one thing: it was an
         * internal activity until Android 13 unbundled it into its own package, and OEMs ship both.
         */
        private val IGNORED_CLASS_PREFIXES = listOf(
            "android.inputmethodservice.SoftInputWindow",
            "com.android.systemui.volume",
            "com.android.internal.app.ChooserActivity",
            "com.android.internal.app.ResolverActivity",
            "com.android.intentresolver."
        )
    }

    /**
     * Packages whose windows never mean "an app was opened". The system UI package covers the
     * notification shade, the quick settings panel and the keyguard: each of them opens on top of
     * the app the user is in and leaves it in front, but on the skins where the shade reports
     * itself as a full-screen window it would otherwise become the foreground app and the screen
     * filter would follow it there and stay, because collapsing the shade does not have to emit a
     * window event for the app underneath.
     *
     * The enabled keyboards are added through [ignorePackages].
     */
    private val ignoredPackages = mutableSetOf("com.android.systemui")

    /**
     * Packages that always have to wait out the settle window, however quiet it was before them,
     * and that do not count as movement for anything that follows them. The home launcher is the
     * one that matters: with gesture navigation it shows for a moment on the way from one app to
     * the next, so taking it at face value would turn every such switch into two — while an app
     * opened from the home screen still has to reach the features without waiting, because that is
     * how most apps are opened.
     */
    private val alwaysSettlingPackages = mutableSetOf<String>()

    /**
     * The package that is in front as far as the features are concerned ("" before the first one).
     */
    var currentPackage: String = ""
        private set

    private var currentIsFullScreen = false
    private var pending: WindowSignal? = null

    /**
     * When the last window that says something about the foreground arrived. The settle window is
     * measured from here rather than from the last publication, because an alternation that keeps
     * going is a screen that has not come to rest: measuring from the publication would let the
     * window expire in the middle of one and publish a frame of it.
     */
    private var lastSignalAtMs = Long.MIN_VALUE / 2

    /** @see ignoredPackages */
    fun ignorePackages(packageNames: Collection<String>) {
        ignoredPackages += packageNames
    }

    /** @see alwaysSettlingPackages */
    fun settleForPackages(packageNames: Collection<String>) {
        alwaysSettlingPackages += packageNames
    }

    /**
     * Drops what is known about the foreground, so the next window event counts as a switch even
     * if it names the same app. Used when the screen turns off: features tear their effects down
     * for the lock screen and need a chance to put them back on the way in.
     */
    fun forget() {
        currentPackage = ""
        currentIsFullScreen = false
        lastSignalAtMs = Long.MIN_VALUE / 2
        pending = null
    }

    /**
     * Feeds a window event in. [nowMs] is a monotonic clock reading; the caller passes the same
     * clock to [settle].
     */
    fun onWindow(signal: WindowSignal, nowMs: Long): ForegroundDecision {
        if (isTransient(signal)) return ForegroundDecision.Unchanged
        val alwaysSettles = signal.packageName in alwaysSettlingPackages
        val quietForMs = nowMs - lastSignalAtMs
        if (!alwaysSettles) lastSignalAtMs = nowMs
        if (signal.packageName == currentPackage && !isFullScreenUpgrade(signal)) {
            val hadPending = pending != null
            pending = null
            return if (hadPending) ForegroundDecision.Cancelled else ForegroundDecision.Unchanged
        }
        pending = signal
        val delayMs = when {
            alwaysSettles -> settleWindowMs
            currentPackage.isEmpty() -> 0L
            else -> (settleWindowMs - quietForMs).coerceAtLeast(0L)
        }
        if (delayMs == 0L) return publish(signal, nowMs)
        return ForegroundDecision.Settling(signal.packageName, delayMs)
    }

    /**
     * Publishes the switch that has been settling, if it is still standing. Returns null when
     * something already cancelled or replaced it.
     */
    fun settle(nowMs: Long): ForegroundDecision.Changed? {
        val signal = pending ?: return null
        return publish(signal, nowMs)
    }

    /**
     * Publishes [packageName] without waiting for anything, for the cases where the foreground was
     * read directly rather than announced by an event (the screen coming back on).
     */
    fun publishNow(packageName: String, isFullScreen: Boolean, nowMs: Long): ForegroundDecision {
        val signal = WindowSignal(packageName, "", isFullScreen)
        if (isTransient(signal)) return ForegroundDecision.Unchanged
        if (signal.packageName == currentPackage && !isFullScreenUpgrade(signal)) {
            return ForegroundDecision.Unchanged
        }
        return publish(signal, nowMs)
    }

    private fun publish(signal: WindowSignal, nowMs: Long): ForegroundDecision.Changed {
        pending = null
        currentPackage = signal.packageName
        currentIsFullScreen = signal.isFullScreen
        lastSignalAtMs = nowMs
        return ForegroundDecision.Changed(signal.packageName, signal.isFullScreen)
    }

    /**
     * A window that does not cover the screen (a dialog, a partial overlay) does not change which
     * app the user is in, and a feature that skips such a window must not lose the app over it: the
     * app's own full-screen window afterwards names the same package and would otherwise be
     * deduplicated away, so the feature would never hear about that app at all.
     */
    private fun isFullScreenUpgrade(signal: WindowSignal) =
        signal.isFullScreen && !currentIsFullScreen

    private fun isTransient(signal: WindowSignal): Boolean =
        signal.packageName.isEmpty() ||
                signal.packageName in ignoredPackages ||
                IGNORED_CLASS_PREFIXES.any { signal.className.startsWith(it) } ||
                isRecentsWindow(signal.className) ||
                isOwnOverlayWindow(signal)

    /**
     * Whether a window is the recents/overview screen. Recents carries a different class name on
     * every skin, and on several of them it lives inside the launcher's own package, so a list of
     * exact class names only ever covers the devices somebody has reported from: matching the name
     * covers the rest. An app that ships a screen of its own by that name is already the foreground
     * package by the time it opens one, so its window is deduplicated before it reaches here.
     */
    private fun isRecentsWindow(className: String) =
        className.contains("recents", ignoreCase = true) ||
                className.contains("overview", ignoreCase = true)

    /**
     * Whether the window is one DetoxDroid put on the screen itself rather than a screen the user
     * navigated to: the grayscale screen filter, the doomscroll break, the block screen. They are
     * reported under DetoxDroid's own package, and counting one as an app switch is a loop. Showing
     * the filter looks like leaving the app the filter was meant for, so the filter comes back off,
     * which makes that app current again, which puts the filter back.
     *
     * DetoxDroid's activities are told apart by their class name living inside the package. An
     * overlay reports the class of whatever view it attached, which does not.
     */
    private fun isOwnOverlayWindow(signal: WindowSignal) =
        signal.packageName == ownPackageName && !signal.className.startsWith("$ownPackageName.")
}
