package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.websites.WebPage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What browsers put in their address bar, and what DetoxDroid makes of it. The texts are the
 * shapes the bars show between and on pages: with and without scheme and "www.", a host alone,
 * their own placeholder, a search being typed, internal pages. Firefox puts the address into the
 * description of its bar instead: after the page title in a custom tab, before the bar's hint
 * always, and on a search results page the search in its place.
 */
class WebPageParseTest {

    @Test
    fun `addresses as browsers show them`() {
        mapOf(
            "youtube.com/shorts/Xy12AbC" to WebPage("youtube.com", "/shorts/Xy12AbC"),
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42" to WebPage("m.youtube.com", "/watch"),
            "www.reddit.com/r/androiddev/" to WebPage("www.reddit.com", "/r/androiddev"),
            "instagram.com" to WebPage("instagram.com", ""),
            "http://192.168.178.1:8080/login#top" to WebPage("192.168.178.1", "/login"),
            "münchen.de/rathaus" to WebPage("xn--mnchen-3ya.de", "/rathaus"),
            "EN.Wikipedia.org/wiki/Doomscrolling" to WebPage("en.wikipedia.org", "/wiki/Doomscrolling"),
            "l.facebook.com/l.php?u=https://example.com" to WebPage("l.facebook.com", "/l.php"),
        ).forEach { (text, page) -> assertEquals(text, page, WebPage.parse(text)) }
    }

    @Test
    fun `placeholders, searches and internal pages are no website`() {
        listOf(
            "",
            "Search or type web address",
            "Suche oder Webadresse eingeben",
            "doomscrolling",
            "YouTube",
            "about:blank",
            "chrome://newtab/",
            "chrome-native://newtab/",
            "file:///sdcard/Download/page.html",
            "content://media/external/file/12",
        ).forEach { assertEquals(it, null, WebPage.parse(it)) }
    }

    @Test
    fun `firefox describes its bar`() {
        mapOf(
            " youtube.com/shorts/Xy12AbC. Search or enter address" to
                    WebPage("youtube.com", "/shorts/Xy12AbC"),
            "Dr. Who - Wikipedia en.m.wikipedia.org. Suche oder Adresse eingeben" to
                    WebPage("en.m.wikipedia.org", ""),
            " cute cats. Search or enter address" to null,
            " . Search or enter address" to null,
            "Search or enter address" to null,
        ).forEach { (text, page) -> assertEquals(text, page, WebPage.parseDescription(text)) }
    }
}
