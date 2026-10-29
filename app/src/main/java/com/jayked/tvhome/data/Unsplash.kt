package com.jayked.tvhome.data

import android.content.Context
import androidx.core.content.edit
import com.jayked.tvhome.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class Photo(val id: String, val imageUrl: String, val photographer: String, val downloadLocation: String)

/** Random landscape photos from the Unsplash API. Blocking; call off the main thread. */
object Unsplash {
    val isConfigured get() = BuildConfig.UNSPLASH_ACCESS_KEY.isNotBlank()

    fun randomPhotos(query: String): List<Photo> {
        val url = "https://api.unsplash.com/photos/random?count=$BATCH_SIZE&orientation=landscape" +
            "&content_filter=high&query=" + URLEncoder.encode(query, "UTF-8")
        val photos = JSONArray(httpGet(url, headers))
        return (0 until photos.length()).map { i ->
            val photo = photos.getJSONObject(i)
            Photo(
                id = photo.getString("id"),
                // The raw URL already has a query string; add imgix sizing for a 1080p screen.
                imageUrl = photo.getJSONObject("urls").getString("raw") + "&w=1920&h=1080&fit=crop&q=80&fm=jpg",
                photographer = photo.getJSONObject("user").getString("name"),
                downloadLocation = photo.getJSONObject("links").getString("download_location"),
            )
        }
    }

    /** Unsplash's API guidelines ask apps to report each photo they put to use. */
    fun trackDownload(photo: Photo) {
        httpGet(photo.downloadLocation, headers)
    }

    private val headers
        get() = mapOf("Authorization" to "Client-ID ${BuildConfig.UNSPLASH_ACCESS_KEY}", "Accept-Version" to "v1")

    // One request covers hours of wallpapers; with download tracking that stays well under the
    // 50 requests/hour demo limit.
    private const val BATCH_SIZE = 30
}

class WallpaperSettings(context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    var query: String
        get() = prefs.getString(KEY_QUERY, null) ?: DEFAULT_QUERY
        set(value) = prefs.edit { putString(KEY_QUERY, value) }

    /** The photo on screen, shown again right away after a restart. */
    var current: Photo?
        get() = prefs.getString(KEY_CURRENT, null)?.let { runCatching { JSONObject(it).toPhoto() }.getOrNull() }
        set(value) = prefs.edit { putString(KEY_CURRENT, value?.toJson()?.toString()) }

    /** Fetched photos not shown yet, so a restart doesn't spend an API request on a new batch. */
    fun queue(query: String): List<Photo> {
        if (prefs.getString(KEY_QUEUE_QUERY, null) != query) return emptyList()
        val json = runCatching { JSONArray(prefs.getString(KEY_QUEUE, null)) }.getOrNull() ?: return emptyList()
        return (0 until json.length()).mapNotNull { runCatching { json.getJSONObject(it).toPhoto() }.getOrNull() }
    }

    fun saveQueue(query: String, photos: Collection<Photo>) = prefs.edit {
        putString(KEY_QUEUE_QUERY, query)
        putString(KEY_QUEUE, JSONArray(photos.map { it.toJson() }).toString())
    }

    private fun Photo.toJson() = JSONObject()
        .put("id", id)
        .put("imageUrl", imageUrl)
        .put("photographer", photographer)
        .put("downloadLocation", downloadLocation)

    private fun JSONObject.toPhoto() = Photo(
        id = getString("id"),
        imageUrl = getString("imageUrl"),
        photographer = getString("photographer"),
        downloadLocation = getString("downloadLocation"),
    )

    companion object {
        const val DEFAULT_QUERY = "landscape"
        private const val KEY_QUERY = "wallpaper_query"
        private const val KEY_CURRENT = "wallpaper_current"
        private const val KEY_QUEUE = "wallpaper_queue"
        private const val KEY_QUEUE_QUERY = "wallpaper_queue_query"
    }
}
