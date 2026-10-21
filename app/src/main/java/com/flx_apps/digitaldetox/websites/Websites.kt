package com.flx_apps.digitaldetox.websites

import android.content.Context
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import java.util.Locale

/**
 * Websites live in the same lists as apps ([SupportsAppExceptionsFeature.appExceptions]), as
 * entries no package name can look like:
 *
 * - `web:youtube.com` or `web:youtube.com/shorts`, a site the user added, with every subdomain and,
 *   for one with a path, everything below that path
 * - `weblist:adult`, one of the [WebsiteCollection]s that come with the app
 *
 * Storing, copying and backing up a list needs nothing new for them. Anything that treats an entry
 * as an installed app has to leave them out, see [isWebsite] and [appsOf]. Whether the listed
 * websites are the only ones a feature covers or the ones it spares is set apart from the apps,
 * see [SupportsAppExceptionsFeature.websiteListType].
 */
object Websites {
    private const val SITE_PREFIX = "web:"
    private const val COLLECTION_PREFIX = "weblist:"

    /** Host prefixes that only pick a version of the same site, see [siteOf]. */
    private val VERSION_PREFIXES = listOf("www.", "m.", "mobile.")

    fun isWebsite(entry: String): Boolean =
        entry.startsWith(SITE_PREFIX) || entry.startsWith(COLLECTION_PREFIX)

    /** The entries of [entries] that name apps. */
    fun appsOf(entries: Collection<String>): Set<String> =
        entries.filterNotTo(mutableSetOf(), ::isWebsite)

    /** The entries of [entries] that name websites or collections of them. */
    fun websitesOf(entries: Collection<String>): Set<String> =
        entries.filterTo(mutableSetOf(), ::isWebsite)

    /**
     * The entry for a site the user typed, in whatever shape it was pasted or typed:
     * "https://www.youtube.com/shorts/" becomes `web:youtube.com/shorts`. Null for anything that
     * names no site.
     */
    fun siteEntry(input: String): String? {
        val page = WebPage.parse(input) ?: return null
        return SITE_PREFIX + page.host.removePrefix("www.") + page.path.lowercase(Locale.ROOT)
    }

    fun collectionEntry(collection: WebsiteCollection): String = COLLECTION_PREFIX + collection.id

    /**
     * The site [page] belongs to: its host without a prefix that only picks a version of the same
     * site, so time on youtube.com and m.youtube.com adds up to one site, and both are one site to
     * a feature that covers every website but the listed ones.
     */
    fun siteOf(page: WebPage): String {
        val host = page.host
        val prefix = VERSION_PREFIXES.firstOrNull { host.startsWith(it) } ?: return host
        val rest = host.removePrefix(prefix)
        // "m.de" stays itself: what is left must still be a host name with a dot
        return if (rest.contains('.')) rest else host
    }

    /** The list entry that stands for the whole of [site] (see [siteOf]). */
    fun entryOfSite(site: String): String = SITE_PREFIX + site

    /** The site a `web:` [entry] stands for, or null for a collection or an app. */
    fun siteOfEntry(entry: String): String? =
        entry.takeIf { it.startsWith(SITE_PREFIX) }?.removePrefix(SITE_PREFIX)

    /**
     * Whether [feature] covers [site] as a website, through an entry of its own, a collection, or
     * "every website but the listed ones". The browser the site is shown in is not asked.
     */
    fun covers(feature: SupportsAppExceptionsFeature, site: String): Boolean =
        foregroundEntry(feature, WebPage(site, ""))?.let(feature::appliesTo) ?: false

    /**
     * Whether [feature] cares which website a browser shows: it lists websites, or covers every
     * website but the listed ones. Otherwise a browser is an app to it like any other.
     */
    fun matterTo(feature: SupportsAppExceptionsFeature): Boolean =
        feature.websiteListType == AppExceptionListType.NOT_LIST ||
                feature.appExceptions.any(::isWebsite)

    /**
     * What [feature] is told is in front while a browser shows [page]: the entry of its list the
     * page falls under, or under "every website except the listed ones" the page's own site, as an
     * entry the list does not hold. Null leaves the page to the browser, for a feature that only
     * covers the websites it lists and lists none that [page] falls under.
     */
    fun foregroundEntry(feature: SupportsAppExceptionsFeature, page: WebPage): String? =
        bestMatch(feature.appExceptions, page)
            ?: if (feature.websiteListType == AppExceptionListType.NOT_LIST) {
                entryOfSite(siteOf(page))
            } else null

    /** The collection [entry] stands for, or null for a site or an app. */
    fun collectionOf(entry: String): WebsiteCollection? =
        if (entry.startsWith(COLLECTION_PREFIX)) {
            WebsiteCollection.byId(entry.removePrefix(COLLECTION_PREFIX))
        } else null

    /**
     * How [entry] is shown: the site as typed, without the prefix, or the collection's name. An
     * app's package comes back unchanged.
     */
    fun displayName(context: Context, entry: String): String = when {
        entry.startsWith(SITE_PREFIX) -> entry.removePrefix(SITE_PREFIX)
        else -> collectionOf(entry)?.let { context.getString(it.title) } ?: entry
    }

    /**
     * The entry among [entries] that [page] falls under, or null when none does. Where several do,
     * the most specific one wins: a path before its whole site, a subdomain before its parent, a
     * site of its own before a collection. Features then tell `youtube.com/shorts` from the rest of
     * YouTube even when both are listed somewhere.
     */
    fun bestMatch(entries: Collection<String>, page: WebPage): String? {
        var best: String? = null
        var bestRank = -1
        for (entry in entries) {
            val rank = when {
                entry.startsWith(SITE_PREFIX) -> siteRank(entry.removePrefix(SITE_PREFIX), page)
                entry.startsWith(COLLECTION_PREFIX) ->
                    if (collectionOf(entry)?.let { WebsiteCollections.contains(it, page.host) } == true) 0
                    else -1

                else -> -1
            }
            if (rank > bestRank) {
                best = entry
                bestRank = rank
            }
        }
        return best
    }

    /**
     * How specifically [site] (host, optionally followed by a path) covers [page], or -1 when it
     * does not cover it at all. Every site ranks above every collection.
     */
    private fun siteRank(site: String, page: WebPage): Int {
        val slash = site.indexOf('/')
        val host = if (slash < 0) site else site.substring(0, slash)
        val path = if (slash < 0) "" else site.substring(slash)
        val hostMatches = page.host == host || page.host.endsWith(".$host")
        if (!hostMatches) return -1
        val pathMatches = path.isEmpty() || page.path.equals(path, ignoreCase = true) ||
                page.path.startsWith("$path/", ignoreCase = true)
        if (!pathMatches) return -1
        return 1 + host.length + (if (path.isEmpty()) 0 else 1_000 + path.length)
    }
}
