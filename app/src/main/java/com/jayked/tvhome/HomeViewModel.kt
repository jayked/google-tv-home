package com.jayked.tvhome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.jayked.tvhome.data.AppEntry
import com.jayked.tvhome.data.AppRepository
import com.jayked.tvhome.data.Photo
import com.jayked.tvhome.data.Shortcut
import com.jayked.tvhome.data.Unsplash
import com.jayked.tvhome.data.WallpaperSettings
import com.jayked.tvhome.data.WatchNextItem
import com.jayked.tvhome.data.WatchNextRepository
import com.jayked.tvhome.data.Weather
import com.jayked.tvhome.data.WeatherClient
import com.jayked.tvhome.data.loadShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeState(
    val loaded: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val favorites: List<AppEntry> = emptyList(),
    val watchNext: List<WatchNextItem> = emptyList(),
    val shortcuts: List<Shortcut> = emptyList(),
    val weather: Weather? = null,
    val wallpaper: Photo? = null,
    val wallpaperQuery: String = WallpaperSettings.DEFAULT_QUERY,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AppRepository(app)
    private val wallpaperSettings = WallpaperSettings(app)
    private val _state = MutableStateFlow(HomeState(wallpaperQuery = wallpaperSettings.query))
    val state = _state.asStateFlow()

    /** Whether the home screen is on screen; wallpaper rotation pauses while another app is open. */
    private val visible = MutableStateFlow(false)
    private var wallpaperJob: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                val weather = withContext(Dispatchers.IO) { runCatching { WeatherClient.fetch() }.getOrNull() }
                if (weather != null) _state.update { it.copy(weather = weather) }
                delay(if (weather != null) WEATHER_INTERVAL_MS else WEATHER_RETRY_MS)
            }
        }
        startWallpaper()
    }

    fun setVisible(visible: Boolean) {
        this.visible.value = visible
    }

    fun setWallpaperQuery(query: String) {
        val trimmed = query.trim().ifEmpty { WallpaperSettings.DEFAULT_QUERY }
        wallpaperSettings.query = trimmed
        _state.update { it.copy(wallpaperQuery = trimmed) }
        startWallpaper()
    }

    /** Shows a new Unsplash photo every minute while the home screen is visible. */
    private fun startWallpaper() {
        if (!Unsplash.isConfigured) return
        wallpaperJob?.cancel()
        wallpaperJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val query = _state.value.wallpaperQuery
            val queue = ArrayDeque<Photo>()
            while (true) {
                visible.first { it }
                if (queue.isEmpty()) {
                    val photos = withContext(Dispatchers.IO) { runCatching { Unsplash.randomPhotos(query) }.getOrNull() }
                    if (photos.isNullOrEmpty()) {
                        delay(WALLPAPER_RETRY_MS)
                        continue
                    }
                    queue.addAll(photos)
                }
                val photo = queue.removeFirst()
                // Load the photo fully before showing it, so the crossfade never fades to an empty frame.
                val request = ImageRequest.Builder(app).data(photo.imageUrl).build()
                if (app.imageLoader.execute(request) !is SuccessResult) continue
                _state.update { it.copy(wallpaper = photo) }
                withContext(Dispatchers.IO) { runCatching { Unsplash.trackDownload(photo) } }
                delay(WALLPAPER_INTERVAL_MS)
            }
        }
    }

    /** Reloads apps, continue watching and shortcuts; called whenever the home screen resumes. */
    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val apps = withContext(Dispatchers.IO) { repository.loadApps() }
            val watchNext = withContext(Dispatchers.IO) { WatchNextRepository.load(context) }
            val shortcuts = withContext(Dispatchers.IO) { loadShortcuts(context) }
            _state.update {
                it.copy(
                    loaded = true,
                    apps = apps,
                    favorites = favoritesOf(apps),
                    watchNext = watchNext,
                    shortcuts = shortcuts,
                )
            }
        }
    }

    fun toggleFavorite(packageName: String) {
        repository.toggleFavorite(packageName)
        _state.update { it.copy(favorites = favoritesOf(it.apps)) }
    }

    fun moveFavorite(packageName: String, delta: Int) {
        repository.moveFavorite(packageName, delta)
        _state.update { it.copy(favorites = favoritesOf(it.apps)) }
    }

    /**
     * Moves an app in the Apps grid by [delta] positions (±1 sideways, ±columns vertically).
     * Moves past either end are ignored, except moving down into a shorter last row.
     */
    fun moveApp(packageName: String, delta: Int) {
        val apps = _state.value.apps.toMutableList()
        val from = apps.indexOfFirst { it.packageName == packageName }
        if (from < 0) return
        var to = from + delta
        if (to < 0) return
        if (to > apps.lastIndex) {
            val rowBelowExists = delta > 1 && from / delta < apps.lastIndex / delta
            if (!rowBelowExists) return
            to = apps.lastIndex
        }
        apps.add(to, apps.removeAt(from))
        repository.saveAppOrder(apps.map { it.packageName })
        _state.update { it.copy(apps = apps) }
    }

    private fun favoritesOf(apps: List<AppEntry>): List<AppEntry> {
        val byPackage = apps.associateBy { it.packageName }
        return repository.favorites().mapNotNull(byPackage::get)
    }

    private companion object {
        const val WEATHER_INTERVAL_MS = 30 * 60_000L
        const val WEATHER_RETRY_MS = 2 * 60_000L
        const val WALLPAPER_INTERVAL_MS = 60_000L
        const val WALLPAPER_RETRY_MS = 5 * 60_000L
    }
}
