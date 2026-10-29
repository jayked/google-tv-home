package com.jayked.tvhome.data

import android.text.Html
import java.util.Locale

/**
 * Prime Video's media session reports an empty title and only a content ID
 * (`amzn1.dv.gti.…`); its public detail page has the title and artwork. Blocking; call off the
 * main thread.
 */
object PrimeVideo {
    const val PACKAGE = "com.amazon.amazonvideo.livingroom"

    data class Details(val title: String, val subtitle: String?, val image: String?)

    fun isContentId(mediaId: String) = mediaId.startsWith("amzn1.dv.gti.")

    fun details(contentId: String): Details? {
        val page = httpGet(
            "https://www.primevideo.com/detail/$contentId/",
            mapOf(
                // The site serves a minimal page to unknown clients; ask for the regular one, in the TV's language.
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
                "Accept-Language" to Locale.getDefault().toLanguageTag(),
            ),
        )
        // e.g. "Prime Video: Pokémon de Serie: Het Begin, Seizoen 1"
        val pageTitle = Regex("<title>([^<]+)</title>").find(page)?.groupValues?.get(1)?.unescape()
            ?.removePrefix("Prime Video:")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val image = Regex("<meta property=\"og:image\" content=\"([^\"]+)\"").find(page)?.groupValues?.get(1)?.unescape()
        // A trailing short part with a number ("Seizoen 1", "Season 2") reads better as the subtitle.
        val season = pageTitle.substringAfterLast(", ", "").takeIf { it.length <= 20 && it.any(Char::isDigit) }
        return if (season != null) {
            Details(pageTitle.removeSuffix(", $season"), season, image)
        } else {
            Details(pageTitle, null, image)
        }
    }

    private fun String.unescape() = Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()
}
