package com.flx_apps.digitaldetox.websites

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Keeps track of the page the browser in front shows, for the accessibility service. Everything
 * here runs on the main thread.
 *
 * The page is read when a browser comes to the front and again after its window content changed.
 * Content changes come by the dozen while a page loads, renders or plays, so a change only asks for
 * a read a moment later, and the changes until then ride along with it.
 *
 * Every read is a call into the browser, which its main thread answers. So the address bar found
 * once is kept and only refreshed after that: finding it again means searching the window, and for
 * a bar made with Compose that is a walk through hundreds of nodes, one call each.
 *
 * @param readRoot The active window, or null. Recycled here after use.
 * @param onPageChanged Called when the page changed, or when [recheck] asked for a report anyway.
 */
class WebsiteMonitor(
    private val addressBars: Map<String, List<String>>,
    private val readRoot: () -> AccessibilityNodeInfo?,
    private val onPageChanged: () -> Unit,
) {
    companion object {
        /** How long after a content change the address bar is read. */
        private const val READ_DELAY_MS = 300L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var readPending = false
    private var reportUnchanged = false
    private val readRunnable = Runnable {
        readPending = false
        readAgain()
    }

    /** The address bar of [browserPackage] as found last, see [currentAddressBar]. */
    private var addressBar: AccessibilityNodeInfo? = null

    /** The browser in front, or null when the app in front is none DetoxDroid can read. */
    var browserPackage: String? = null
        private set

    /** The page [browserPackage] shows, or null when it shows none (a new tab, its settings). */
    var page: WebPage? = null
        private set

    /** The browsers DetoxDroid can read the address bar of. */
    val browsers: Set<String> get() = addressBars.keys

    /**
     * The app in front changed to [packageName]. The page of a browser is read right away, so the
     * features hear about the browser and the site it shows at once instead of one after the other.
     * Without [watch] (no feature has a website listed) nothing is read at all.
     */
    fun onForegroundApp(packageName: String, watch: Boolean) {
        cancelRead()
        forgetAddressBar()
        val ids = addressBars[packageName]
        if (!watch || ids == null) {
            browserPackage = null
            page = null
            return
        }
        browserPackage = packageName
        page = (read(packageName, ids) as? AddressBarReading.Shown)?.page
    }

    /** The content of a window of [packageName] changed. */
    fun onContentChanged(packageName: CharSequence?) {
        val browser = browserPackage ?: return
        if (packageName?.toString() != browser || readPending) return
        readPending = true
        handler.postDelayed(readRunnable, READ_DELAY_MS)
    }

    /**
     * Reads the page again after [delayMs] and reports it even if it did not change. For going
     * back from a website: the page before can belong to the same site, which has to be judged
     * again.
     */
    fun recheck(delayMs: Long) {
        cancelRead()
        reportUnchanged = true
        readPending = true
        handler.postDelayed(readRunnable, delayMs)
    }

    /** Forgets the browser and its page, e.g. when the screen goes off. */
    fun reset() {
        cancelRead()
        forgetAddressBar()
        browserPackage = null
        page = null
    }

    private fun cancelRead() {
        handler.removeCallbacks(readRunnable)
        readPending = false
        reportUnchanged = false
    }

    private fun readAgain() {
        val browser = browserPackage ?: return
        val ids = addressBars[browser] ?: return
        val newPage = when (val reading = read(browser, ids)) {
            is AddressBarReading.Shown -> reading.page
            // still the page it was: a bar that is hidden or being typed in says nothing new
            AddressBarReading.Missing, AddressBarReading.Editing -> page
        }
        if (newPage == page && !reportUnchanged) return
        reportUnchanged = false
        page = newPage
        onPageChanged()
    }

    private fun read(browser: String, ids: List<String>): AddressBarReading = try {
        currentAddressBar(browser, ids)?.let(BrowserAddressBars::read) ?: AddressBarReading.Missing
    } catch (e: RuntimeException) {
        // a window can go away while it is read
        forgetAddressBar()
        AddressBarReading.Missing
    }

    /**
     * The address bar of [browser]: the one kept from the last read while it is still on screen,
     * otherwise found anew in the active window. Null while the browser shows none.
     */
    private fun currentAddressBar(browser: String, ids: List<String>): AccessibilityNodeInfo? {
        addressBar?.let { if (it.refresh()) return it }
        forgetAddressBar()
        val root = readRoot() ?: return null
        return try {
            BrowserAddressBars.find(root, browser, ids)
        } finally {
            root.recycle()
        }.also { addressBar = it }
    }

    private fun forgetAddressBar() {
        addressBar?.recycle()
        addressBar = null
    }
}
