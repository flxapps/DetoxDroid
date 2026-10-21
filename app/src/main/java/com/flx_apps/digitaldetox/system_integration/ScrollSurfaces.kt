package com.flx_apps.digitaldetox.system_integration

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.flx_apps.digitaldetox.R
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber

/**
 * The scroll surfaces of known apps that the scroll break treats differently from a feed: the
 * pagers of short videos, from `res/xml/scroll_surfaces.xml`. One swipe there is one video, and a
 * swipe every ten seconds never looks fast, so on them the break goes by time and swipes, see
 * [com.flx_apps.digitaldetox.util.ShortVideoRuns].
 *
 * The table only ever adds to what the generic detection does. An app it does not name, a surface
 * it does not name and a table that cannot be read all fall through to the scroll physics, and
 * nothing here navigates or blocks on its own.
 */
object ScrollSurfaces {
    /**
     * How many ancestors of a scrolled view without an id are looked at for a known one. A
     * ViewPager2 scrolls an inner list that has no id, and a pager can sit a level or two deeper.
     */
    private const val MAX_CLIMB = 4

    @Volatile
    private var table: Map<String, Set<String>>? = null

    /**
     * The surface a scroll event in [packageName] happened on: the known pager the [source] scrolls
     * inside of, or else the source's own view id. Only a source without an id of its own is
     * climbed from; a list with an id is a surface of its own, even inside a pager. Blocking the
     * first time, when the table is read. The caller owns [source]; what is looked at above it is
     * recycled here.
     */
    fun surfaceOf(context: Context, packageName: String?, source: AccessibilityNodeInfo): String? {
        val own = source.viewIdResourceName
        val known = packageName?.let { tableOf(context)[it] } ?: return own
        if (own != null) return own
        var node = runCatching { source.parent }.getOrNull()
        var climbed = 0
        while (climbed < MAX_CLIMB) {
            val current = node ?: return null
            val id = current.viewIdResourceName
            if (id != null && id in known) {
                current.recycle()
                return id
            }
            node = runCatching { current.parent }.getOrNull()
            current.recycle()
            climbed++
        }
        node?.recycle()
        return null
    }

    /** Whether [surfaceId] is a short-video pager of [packageName]. */
    fun isShortVideoPager(context: Context, packageName: String, surfaceId: String?): Boolean =
        surfaceId != null && tableOf(context)[packageName]?.contains(surfaceId) == true

    private fun tableOf(context: Context): Map<String, Set<String>> =
        table ?: load(context).also { table = it }

    /** App package → the ids of its short-video pagers. */
    private fun load(context: Context): Map<String, Set<String>> = runCatching {
        val apps = HashMap<String, MutableSet<String>>()
        val parser = context.resources.getXml(R.xml.scroll_surfaces)
        var app: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "app" -> app = parser.getAttributeValue(null, "package")
                "shortVideos" -> {
                    val pkg = app ?: continue
                    val id = parser.getAttributeValue(null, "id") ?: continue
                    apps.getOrPut(pkg) { HashSet() } += if (id.startsWith("id/")) "$pkg:$id" else id
                }
            }
        }
        parser.close()
        apps
    }.getOrElse {
        Timber.e(it, "Could not read the scroll surfaces")
        emptyMap()
    }
}
