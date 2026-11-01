package com.jayked.tvhome.data

import com.jayked.tvhome.BuildConfig
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Artwork and episode details from The Movie Database, for apps (like Disney+) whose media
 * sessions report only a title. Blocking; call off the main thread.
 */
object Tmdb {
    val isConfigured get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    data class Episode(val season: Int, val number: Int, val name: String?, val still: String?, val aired: LocalDate)

    /** A wide backdrop for the series or movie called [title], or null if TMDB doesn't know it. */
    fun backdrop(title: String): String? {
        if (!isConfigured) return null
        val results = get("search/multi", "include_adult=false&query=" + URLEncoder.encode(title, "UTF-8")).getJSONArray("results")
        val candidates = (0 until results.length()).map { results.getJSONObject(it) }
            .filter { it.optString("media_type") in setOf("tv", "movie") && it.optString("backdrop_path").isNotBlank() }
        val match = candidates.bestMatch(title) ?: return null
        return IMAGE_BASE + match.getString("backdrop_path")
    }

    /**
     * Season [season] episode [episode] of series [title], and the episode after it: the next one
     * in the season, else the first of the next season. Null if either is unknown or unaired.
     */
    fun nextEpisode(title: String, season: Int, episode: Int): Pair<Episode, Episode>? {
        if (!isConfigured) return null
        val results = get("search/tv", "include_adult=false&query=" + URLEncoder.encode(title, "UTF-8")).getJSONArray("results")
        val series = (0 until results.length()).map { results.getJSONObject(it) }.bestMatch(title) ?: return null
        val id = series.getLong("id")
        val current = episode(id, season, episode)?.toEpisode() ?: return null
        val next = (episode(id, season, episode + 1) ?: episode(id, season + 1, 1))?.toEpisode() ?: return null
        return current to next
    }

    private fun JSONObject.toEpisode(): Episode? {
        val aired = optString("air_date").ifBlank { null }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return Episode(
            season = getInt("season_number"),
            number = getInt("episode_number"),
            name = optString("name").ifBlank { null },
            still = optString("still_path").ifBlank { null }?.let { IMAGE_BASE + it },
            aired = aired,
        )
    }

    /** One episode's details, or null when the series has no such episode. */
    private fun episode(seriesId: Long, season: Int, number: Int): JSONObject? = try {
        get("tv/$seriesId/season/$season/episode/$number")
    } catch (e: FileNotFoundException) {
        null
    }

    /** Prefers an exact title match over TMDB's most popular guess. */
    private fun List<JSONObject>.bestMatch(title: String) =
        firstOrNull { it.optString("name", it.optString("title")).equals(title, ignoreCase = true) } ?: firstOrNull()

    private fun get(path: String, query: String = ""): JSONObject {
        val key = BuildConfig.TMDB_API_KEY
        // Accept both kinds of TMDB credentials: a v4 read access token (a JWT) or a v3 API key.
        val isToken = key.startsWith("eyJ")
        val params = listOfNotNull(query.ifEmpty { null }, if (isToken) null else "api_key=$key").joinToString("&")
        val url = "https://api.themoviedb.org/3/$path" + if (params.isEmpty()) "" else "?$params"
        val headers = if (isToken) mapOf("Authorization" to "Bearer $key") else emptyMap()
        return JSONObject(httpGet(url, headers))
    }

    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w780"
}
