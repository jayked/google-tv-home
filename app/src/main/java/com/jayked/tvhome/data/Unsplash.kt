package com.jayked.tvhome.data

import android.content.Context
import androidx.core.content.edit
import com.jayked.tvhome.BuildConfig
import org.json.JSONArray
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

    // One request covers half an hour of wallpapers, well under the 50 requests/hour demo limit.
    private const val BATCH_SIZE = 30
}

class WallpaperSettings(context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    var query: String
        get() = prefs.getString(KEY_QUERY, null) ?: DEFAULT_QUERY
        set(value) = prefs.edit { putString(KEY_QUERY, value) }

    companion object {
        const val DEFAULT_QUERY = "landscape"
        private const val KEY_QUERY = "wallpaper_query"
    }
}
