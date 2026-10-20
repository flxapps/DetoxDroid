package com.flx_apps.digitaldetox.websites

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.flx_apps.digitaldetox.R
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber

/**
 * What a browser's address bar says right now.
 */
sealed interface AddressBarReading {
    /**
     * No address bar in the window: a video in full screen, a menu, the browser's tab overview.
     * Says nothing about the page, which is still the one it was.
     */
    data object Missing : AddressBarReading

    /** The user is typing in it. Nothing has been opened yet, so the page is still the one it was. */
    data object Editing : AddressBarReading

    /** The address bar shows [page], or nothing that names a site (null), like a new tab. */
    data class Shown(val page: WebPage?) : AddressBarReading
}

/**
 * The browsers whose websites DetoxDroid tells apart, from `res/xml/browser_address_bars.xml`, and
 * reading their address bar.
 */
object BrowserAddressBars {
    /**
     * How many nodes a walk for a Compose tag looks at before giving up. The address bar sits at
     * the top or the bottom of the window and turns up early, while the page itself can run to
     * thousands of nodes.
     */
    private const val MAX_WALKED_NODES = 400

    /** Browser package → the ids its address bar may have, in the order they are tried. */
    fun load(context: Context): Map<String, List<String>> = runCatching {
        val browsers = LinkedHashMap<String, MutableList<String>>()
        val parser = context.resources.getXml(R.xml.browser_address_bars)
        var browser: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "browser" -> browser = parser.getAttributeValue(null, "package")
                "addressBar" -> {
                    val pkg = browser ?: continue
                    val id = parser.getAttributeValue(null, "id") ?: continue
                    browsers.getOrPut(pkg) { mutableListOf() } +=
                        if (id.startsWith("id/")) "$pkg:$id" else id
                }
            }
        }
        parser.close()
        browsers
    }.getOrElse {
        Timber.e(it, "Could not read the browser table")
        emptyMap()
    }

    /**
     * Reads the address bar of [browser] from [root], the active window, trying [ids] in turn.
     * Neither [root] nor anything it hands out stays in use afterwards; the nodes found here are
     * recycled, [root] is the caller's.
     */
    fun read(root: AccessibilityNodeInfo, browser: String, ids: List<String>): AddressBarReading {
        // a dialog of another app, or the split-screen partner, can be the active window
        if (root.packageName?.toString() != browser) return AddressBarReading.Missing
        for (id in ids) {
            val node = find(root, id) ?: continue
            try {
                if (node.isFocused) return AddressBarReading.Editing
                if (node.isShowingHintText) return AddressBarReading.Shown(null)
                // Firefox and the browsers built on it leave the text of their bar empty and put
                // the address into its description
                val text = node.text
                return AddressBarReading.Shown(
                    if (!text.isNullOrBlank()) WebPage.parse(text)
                    else WebPage.parseDescription(node.contentDescription)
                )
            } finally {
                node.recycle()
            }
        }
        return AddressBarReading.Missing
    }

    private fun find(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        if (id.contains(":id/")) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }
                .getOrNull().orEmpty()
            nodes.drop(1).forEach { it.recycle() }
            return nodes.firstOrNull()
        }
        return walkFor(root, id)
    }

    /**
     * Finds the node whose view id is the Compose tag [tag], breadth first, so the bars at the
     * edges of the window come before the depths of the page.
     */
    private fun walkFor(root: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let(queue::add)
        var walked = 0
        var found: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty() && walked < MAX_WALKED_NODES) {
            val node = queue.removeFirst()
            walked++
            if (node.viewIdResourceName == tag) {
                found = node
                break
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            node.recycle()
        }
        // recycle() is a no-op from API 33 on, but frees native memory on older devices
        queue.forEach { it.recycle() }
        return found
    }
}
