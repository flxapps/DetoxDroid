package com.flx_apps.digitaldetox.websites

import android.content.Context
import androidx.annotation.StringRes
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * A list of sites that comes with the app and goes into a feature's list as one entry, in the
 * order the screen shows them. The social and video lists are written by hand. The large ones
 * come from the porn and gambling extensions of the StevenBlack hosts lists and from the UT1
 * blacklists of the Université Toulouse 1 Capitole (see the header of each file for the sources
 * and their licenses), refreshed with `scripts/update_website_lists.py` before a release. Nothing
 * is downloaded at runtime.
 *
 * @param named Whether the file opens with its best-known sites, so the row can name them: true
 *   for a list we wrote and for a large one we gave a head. The other rows show size and source.
 * @param advanced Whether the screen folds the list away under "Advanced". The porn and gambling
 *   lists are for the few who come looking for them, and everyone else should not have to read
 *   past them.
 * @param source Where a large list comes from, for its row; null for one that is entirely ours.
 */
enum class WebsiteCollection(
    val id: String,
    @StringRes val title: Int,
    val asset: String,
    val named: Boolean,
    val advanced: Boolean,
    @StringRes val source: Int? = null,
) {
    SOCIAL(
        "social", R.string.websites_collection_social, "websites/social.txt",
        named = true, advanced = false
    ),
    VIDEO(
        "video", R.string.websites_collection_video, "websites/video.txt",
        named = true, advanced = false
    ),
    SHOPPING(
        "shopping", R.string.websites_collection_shopping, "websites/shopping.txt",
        named = true, advanced = false, source = R.string.websites_source_ut1
    ),
    DATING(
        "dating", R.string.websites_collection_dating, "websites/dating.txt",
        named = true, advanced = false, source = R.string.websites_source_ut1
    ),
    ADULT(
        "adult", R.string.websites_collection_adult, "websites/adult.txt",
        named = false, advanced = true, source = R.string.websites_source_stevenBlack
    ),
    GAMBLING(
        "gambling", R.string.websites_collection_gambling, "websites/gambling.txt",
        named = false, advanced = true, source = R.string.websites_source_stevenBlackAndUt1
    );

    companion object {
        fun byId(id: String): WebsiteCollection? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The domains of each [WebsiteCollection], loaded on first use. The large lists run to tens of
 * thousands of domains and are asked about on every page change, from the main thread, so they are
 * kept as sorted 64-bit hashes rather than strings: well under a megabyte for the largest, and a
 * binary search per lookup. Two different domains sharing a hash is too unlikely to matter.
 */
object WebsiteCollections {
    /** The domains of a loaded collection. */
    class Domains(private val hashes: LongArray, val size: Int, val sample: List<String>) {
        fun containsHash(hash: Long): Boolean = hashes.binarySearch(hash) >= 0
    }

    private val loaded = ConcurrentHashMap<WebsiteCollection, Domains>()
    private val loading = ConcurrentHashMap.newKeySet<WebsiteCollection>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** How many sites of a named collection are shown on screen before "and N more". */
    private const val SAMPLE_SIZE = 4

    /**
     * Whether [host] or one of its parent domains is in [collection]. A collection that is not
     * loaded yet answers no and starts loading: matching runs on the main thread and must not wait
     * for a file, and [prefetch] is there to have it ready before it is asked.
     */
    fun contains(collection: WebsiteCollection, host: String): Boolean {
        val domains = loaded[collection] ?: run {
            prefetch(listOf(collection))
            return false
        }
        var start = 0
        while (true) {
            if (domains.containsHash(hash(host, start))) return true
            val nextDot = host.indexOf('.', start)
            // stop at the last two labels: "co.uk" or "com" alone are no site
            if (nextDot < 0 || host.indexOf('.', nextDot + 1) < 0) return false
            start = nextDot + 1
        }
    }

    /**
     * Loads [collections] in the background, those that are not loaded yet. [onLoaded] runs (on a
     * background thread) once for every collection this call actually loaded.
     */
    fun prefetch(collections: Collection<WebsiteCollection>, onLoaded: () -> Unit = {}) {
        collections.forEach { collection ->
            if (loaded.containsKey(collection) || !loading.add(collection)) return@forEach
            scope.launch {
                try {
                    load(DetoxDroidApplication.appContext, collection)
                    onLoaded()
                } finally {
                    loading.remove(collection)
                }
            }
        }
    }

    /** The domains of [collection] if they are loaded already, without loading them. */
    fun loadedOrNull(collection: WebsiteCollection): Domains? = loaded[collection]

    /** The domains of [collection], read from its file the first time. Blocking. */
    fun load(context: Context, collection: WebsiteCollection): Domains = loaded.getOrPut(collection) {
        val hashes = ArrayList<Long>()
        val sample = ArrayList<String>()
        runCatching {
            context.assets.open(collection.asset).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val domain = line.substringBefore('#').trim().substringAfterLast(' ')
                        .lowercase(Locale.ROOT).removePrefix("www.")
                    if (domain.isEmpty()) return@forEach
                    hashes += hash(domain, 0)
                    if (sample.size < SAMPLE_SIZE) sample += domain
                }
            }
        }.onFailure { Timber.e(it, "Could not read the website collection %s", collection.id) }
        val sorted = hashes.toLongArray().also { it.sort() }
        Domains(sorted, sorted.size, sample)
    }

    /** FNV-1a over the characters of [text] from [start] on, so a parent domain needs no copy. */
    private fun hash(text: String, start: Int): Long {
        var hash = -0x340d631b7bdddcdbL
        for (i in start until text.length) {
            hash = hash xor text[i].code.toLong()
            hash *= 0x100000001b3L
        }
        return hash
    }
}
