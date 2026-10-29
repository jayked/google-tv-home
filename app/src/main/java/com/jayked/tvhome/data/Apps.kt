package com.jayked.tvhome.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
    val intent: Intent,
)

/** Installed TV apps plus the user's ordered list of favorites. */
class AppRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun loadApps(): List<AppEntry> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val activity = info.activityInfo
                val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
                AppEntry(
                    packageName = activity.packageName,
                    label = info.loadLabel(pm).toString(),
                    banner = banner?.toBitmap(320, 180)?.asImageBitmap(),
                    icon = info.loadIcon(pm).toBitmap(128, 128).asImageBitmap(),
                    intent = Intent(query)
                        .setClassName(activity.packageName, activity.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun favorites(): List<String> =
        prefs.getString(KEY_FAVORITES, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    fun toggleFavorite(packageName: String) {
        val favorites = favorites().toMutableList()
        if (!favorites.remove(packageName)) favorites.add(packageName)
        save(favorites)
    }

    fun moveFavorite(packageName: String, delta: Int) {
        val favorites = favorites().toMutableList()
        val from = favorites.indexOf(packageName)
        if (from < 0) return
        favorites.removeAt(from)
        favorites.add((from + delta).coerceIn(0, favorites.size), packageName)
        save(favorites)
    }

    private fun save(favorites: List<String>) =
        prefs.edit { putString(KEY_FAVORITES, favorites.joinToString(",")) }

    private companion object {
        const val KEY_FAVORITES = "favorites"
    }
}
