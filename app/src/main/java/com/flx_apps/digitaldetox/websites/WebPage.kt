package com.flx_apps.digitaldetox.websites

import java.net.IDN
import java.util.Locale

/**
 * The page a browser shows, as far as matching goes: the host and the path, without scheme, port,
 * query or fragment. The host is lower case and in its ASCII (punycode) form, the path has no
 * trailing slash and is empty for the start page of a site.
 */
data class WebPage(val host: String, val path: String) {
    companion object {
        /**
         * Reads a page from what a browser's address bar shows. Browsers show the address in many
         * shapes: with or without the scheme, with "www." or without, sometimes only the host, and
         * between pages their own placeholder ("Search or type web address") or a search term. Only
         * something that names a host with at least one dot counts; everything else is null, which
         * leaves the browser itself in front as far as the features go.
         */
        fun parse(text: CharSequence?): WebPage? {
            var rest = text?.toString()?.trim().orEmpty()
            if (rest.isEmpty() || rest.any { it.isWhitespace() }) return null
            val schemeEnd = rest.indexOf("://")
            if (schemeEnd >= 0) {
                val scheme = rest.substring(0, schemeEnd).lowercase(Locale.ROOT)
                if (scheme != "http" && scheme != "https") return null
                rest = rest.substring(schemeEnd + 3)
            }

            val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
                .let { if (it < 0) rest.length else it }
            var authority = rest.substring(0, authorityEnd).substringAfterLast('@')
            val portStart = authority.lastIndexOf(':')
            if (portStart >= 0) {
                // "about:blank" and the like end up here, with something that is no port
                if (authority.substring(portStart + 1).any { !it.isDigit() }) return null
                authority = authority.substring(0, portStart)
            }
            val host = runCatching { IDN.toASCII(authority.trimEnd('.')) }.getOrNull()
                ?.lowercase(Locale.ROOT)
                ?.takeIf(::isHostName) ?: return null

            val afterAuthority = rest.substring(authorityEnd)
            val pathEnd = afterAuthority.indexOfFirst { it == '?' || it == '#' }
                .let { if (it < 0) afterAuthority.length else it }
            val path = afterAuthority.substring(0, pathEnd).trimEnd('/')
            return WebPage(host, path)
        }

        /**
         * Reads a page from an address bar's description, for the bars that leave their text empty
         * and describe themselves instead: the page title where they show one, the address, a full
         * stop and their hint ("Doomscrolling - Wikipedia en.wikipedia.org. Search or enter
         * address"). The address is the last word before the last full stop that ends one. The
         * hint is in the phone's language, so it is cut off by that full stop and never read.
         */
        fun parseDescription(description: CharSequence?): WebPage? {
            val text = description?.toString() ?: return null
            val address = text.substringBeforeLast(". ", text).trim()
                .takeLastWhile { !it.isWhitespace() }.trimEnd('.')
            return parse(address)
        }

        /**
         * Whether [host] is a name with at least two labels, made of what a host name may contain.
         * A single word is what a search term or a browser's own page looks like.
         */
        private fun isHostName(host: String): Boolean {
            val labels = host.split('.')
            if (labels.size < 2) return false
            return labels.all { label ->
                label.isNotEmpty() && label.length <= 63 &&
                        label.first() != '-' && label.last() != '-' &&
                        label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
            }
        }
    }
}
