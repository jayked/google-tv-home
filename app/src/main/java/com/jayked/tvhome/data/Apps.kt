package com.jayked.tvhome.data

import com.jayked.tvhome.R

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
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
            .map { info -> info.toEntry(Intent(query).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
            .let { apps ->
                // Apps the user has arranged come first in their order; new installs go at the end.
                val position = packageList(KEY_APP_ORDER).withIndex().associate { (i, pkg) -> pkg to i }
                apps.sortedWith(compareBy({ position[it.packageName] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
            }
    }

    /** Google TV's own home screen, for opening it on purpose. */
    fun loadStockHome(): AppEntry? {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(STOCK_LAUNCHER)
        // Its banner is a blank placeholder: show the icon with a clear name instead.
        return context.packageManager.resolveActivity(query, 0)?.toEntry(query)
            ?.copy(label = context.getString(R.string.stock_home), banner = null)
    }

    private fun ResolveInfo.toEntry(baseIntent: Intent): AppEntry {
        val pm = context.packageManager
        val activity = activityInfo
        val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        return AppEntry(
            packageName = activity.packageName,
            label = loadLabel(pm).toString(),
            banner = banner?.toBitmap(320, 180)?.asImageBitmap(),
            icon = loadIcon(pm).toBitmap(128, 128).asImageBitmap(),
            intent = Intent(baseIntent)
                .setClassName(activity.packageName, activity.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun saveAppOrder(packageNames: List<String>) = savePackageList(KEY_APP_ORDER, packageNames)

    fun favorites(): List<String> = packageList(KEY_FAVORITES)

    fun toggleFavorite(packageName: String) {
        val favorites = favorites().toMutableList()
        if (!favorites.remove(packageName)) favorites.add(packageName)
        savePackageList(KEY_FAVORITES, favorites)
    }

    fun moveFavorite(packageName: String, delta: Int) {
        val favorites = favorites().toMutableList()
        val from = favorites.indexOf(packageName)
        if (from < 0) return
        favorites.removeAt(from)
        favorites.add((from + delta).coerceIn(0, favorites.size), packageName)
        savePackageList(KEY_FAVORITES, favorites)
    }

    private fun packageList(key: String): List<String> =
        prefs.getString(key, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    private fun savePackageList(key: String, packageNames: List<String>) =
        prefs.edit { putString(key, packageNames.joinToString(",")) }

    companion object {
        const val STOCK_LAUNCHER = "com.google.android.apps.tv.launcherx"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_APP_ORDER = "app_order"
    }
}
