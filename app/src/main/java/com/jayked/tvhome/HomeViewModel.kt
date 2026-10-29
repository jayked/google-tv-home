package com.jayked.tvhome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jayked.tvhome.data.AppEntry
import com.jayked.tvhome.data.AppRepository
import com.jayked.tvhome.data.Shortcut
import com.jayked.tvhome.data.WatchNextItem
import com.jayked.tvhome.data.WatchNextRepository
import com.jayked.tvhome.data.Weather
import com.jayked.tvhome.data.WeatherClient
import com.jayked.tvhome.data.loadShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AppRepository(app)
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val weather = withContext(Dispatchers.IO) { runCatching { WeatherClient.fetch() }.getOrNull() }
                if (weather != null) _state.update { it.copy(weather = weather) }
                delay(if (weather != null) WEATHER_INTERVAL_MS else WEATHER_RETRY_MS)
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

    private fun favoritesOf(apps: List<AppEntry>): List<AppEntry> {
        val byPackage = apps.associateBy { it.packageName }
        return repository.favorites().mapNotNull(byPackage::get)
    }

    private companion object {
        const val WEATHER_INTERVAL_MS = 30 * 60_000L
        const val WEATHER_RETRY_MS = 2 * 60_000L
    }
}
