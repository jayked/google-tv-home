package com.jayked.tvhome.data

import com.jayked.tvhome.BuildConfig
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Artwork from The Movie Database, for apps (like Disney+) whose media sessions report only a
 * title. Blocking; call off the main thread.
 */
object Tmdb {
    val isConfigured get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    /** A wide backdrop for the series or movie called [title], or null if TMDB doesn't know it. */
    fun backdrop(title: String): String? {
        if (!isConfigured) return null
        val key = BuildConfig.TMDB_API_KEY
        // Accept both kinds of TMDB credentials: a v4 read access token (a JWT) or a v3 API key.
        val isToken = key.startsWith("eyJ")
        val url = "https://api.themoviedb.org/3/search/multi?include_adult=false&query=" +
            URLEncoder.encode(title, "UTF-8") + if (isToken) "" else "&api_key=$key"
        val headers = if (isToken) mapOf("Authorization" to "Bearer $key") else emptyMap()
        val results = JSONObject(httpGet(url, headers)).getJSONArray("results")
        val candidates = (0 until results.length()).map { results.getJSONObject(it) }
            .filter { it.optString("media_type") in setOf("tv", "movie") && it.optString("backdrop_path").isNotBlank() }
        // Prefer an exact title match over TMDB's most popular guess.
        val match = candidates.firstOrNull { it.optString("name", it.optString("title")).equals(title, ignoreCase = true) }
            ?: candidates.firstOrNull()
            ?: return null
        return IMAGE_BASE + match.getString("backdrop_path")
    }

    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w780"
}
