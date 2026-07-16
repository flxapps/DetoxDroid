package com.flx_apps.digitaldetox.screenshot

import java.io.File

/**
 * Locale configuration for a marketing screenshot set.
 *
 * @param bcp47     fastlane listing directory name (e.g. "en-US", "de-DE").
 * @param qualifier Android resource qualifier string (e.g. "en-rUS", "de-rDE").
 */
data class MarketingLocale(val bcp47: String, val qualifier: String)

private const val STRINGS_SCREENSHOT = "strings_screenshot.xml"

/**
 * Marketing locales auto-discovered at runtime by scanning the resource directory for
 * [STRINGS_SCREENSHOT] files. The default `values/` folder maps to en-US. Additional locales in
 * `values-XX(-rYY)/` subdirectories are added in alphabetical order.
 *
 * **To add a new marketing locale**, create `src/main/res/values-XX/strings_screenshot.xml` with
 * translated captions — no code changes required. The next test run generates screenshots for it.
 */
val MARKETING_LOCALES: List<MarketingLocale> by lazy {
    // Try module-relative path first, then project-relative path (depends on the gradle CWD).
    val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
    val resDir = candidates.firstOrNull { it.exists() && it.isDirectory }
        ?: error("Could not find res/ directory in: ${candidates.map { it.absolutePath }}")

    buildList {
        if (File(resDir, "values/$STRINGS_SCREENSHOT").exists()) {
            add(MarketingLocale("en-US", "en-rUS"))
        }

        resDir.listFiles()
            ?.filter { dir ->
                dir.isDirectory &&
                        dir.name.startsWith("values-") &&
                        File(dir, STRINGS_SCREENSHOT).exists()
            }
            ?.sortedBy { it.name }
            ?.forEach { dir ->
                val q = dir.name.removePrefix("values-").trim()
                val baseLang = q.substringBefore("-")
                val bcp47 = when (baseLang) {
                    "cs" -> "cs-CZ"
                    "de" -> "de-DE"
                    "es" -> "es-ES"
                    "fr" -> "fr-FR"
                    "hu" -> "hu-HU"
                    "id", "in" -> "id"
                    "it" -> "it-IT"
                    "ja" -> "ja-JP"
                    "nl" -> "nl-NL"
                    "pl" -> "pl-PL"
                    "pt" -> if (q.contains("rBR")) "pt-BR" else "pt-PT"
                    "ru" -> "ru-RU"
                    "tr" -> "tr-TR"
                    "ko" -> "ko-KR"
                    "zh" -> "zh-CN"
                    else -> if (q.contains("-r")) q.replace("-r", "-") else q
                }
                add(MarketingLocale(bcp47 = bcp47, qualifier = q))
            }
    }
}

/** Device qualifiers for phone-size screenshots (440×800 dp at xxhdpi = 1320×2400 px). */
const val PHONE_QUALIFIERS = "w440dp-h800dp-xxhdpi"

/**
 * Output path for screenshot [index] of [locale], in the fastlane metadata tree consumed by both
 * F-Droid and the Gradle Play Publisher.
 */
fun screenshotPath(locale: String, index: Int): String {
    val root = if (File("src/main/res").exists()) "." else "app"
    return "$root/../fastlane/metadata/android/$locale/images/phoneScreenshots/$index.png"
}
