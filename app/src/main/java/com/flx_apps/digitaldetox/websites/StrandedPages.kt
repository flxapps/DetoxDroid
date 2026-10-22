package com.flx_apps.digitaldetox.websites

/**
 * Remembers a website that going back could not leave. In a tab with nothing to go back to, going
 * back sends the browser into the background and keeps the tab: opening the browser again shows
 * the same page, held back again, and going back again would only repeat that. Such a page is
 * left another way the next time, see [isStranded].
 *
 * Everything here runs on the main thread of the accessibility service.
 */
class StrandedPages {
    companion object {
        /**
         * How soon after going back the browser has to leave the front for the page to count as
         * stranded. The browser goes at once; an app switch later than this is the user's own.
         */
        private const val LEFT_FRONT_WITHIN_MS = 3_000L
    }

    private data class Visit(val browser: String, val page: WebPage)

    private var wentBackFrom: Visit? = null
    private var wentBackAtMs = 0L
    private var stranded: Visit? = null

    /** Going back from [page] in [browser] was just tried. */
    fun onWentBack(browser: String, page: WebPage, nowMs: Long) {
        wentBackFrom = Visit(browser, page)
        wentBackAtMs = nowMs
    }

    /**
     * [app] came to the front. When that is another app right after going back, the browser left
     * the front instead of the page.
     */
    fun onAppSwitch(app: String, nowMs: Long) {
        val visit = wentBackFrom ?: return
        wentBackFrom = null
        if (app != visit.browser && nowMs - wentBackAtMs < LEFT_FRONT_WITHIN_MS) stranded = visit
    }

    /**
     * [browser] is in front and shows [page]. Once it shows another page, the stranded one is no
     * longer stuck, and going back from it is worth a try again.
     */
    fun onPageShown(browser: String, page: WebPage?) {
        val visit = stranded ?: return
        if (visit.browser == browser && visit.page != page) stranded = null
    }

    /** Whether going back from [page] in [browser] took the browser away from the front before. */
    fun isStranded(browser: String, page: WebPage): Boolean = stranded == Visit(browser, page)
}
