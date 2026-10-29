package com.jayked.tvhome.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract.WatchNextPrograms
import android.net.Uri

data class WatchNextItem(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val image: Uri?,
    val progress: Float?,
    val intent: Intent,
)

/**
 * Reads the system "Watch Next" list that apps publish to the TV provider. Google TV's own
 * continue-watching row also mixes in server-side data, so not every app shows up here.
 */
object WatchNextRepository {
    const val PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA"

    fun load(context: Context): List<WatchNextItem> {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val cursor = try {
            context.contentResolver.query(
                WatchNextPrograms.CONTENT_URI,
                null,
                null,
                null,
                "${WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC",
            )
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        return cursor.use { c ->
            buildList { while (c.moveToNext()) c.toItem(context)?.let(::add) }
        }.take(MAX_ITEMS)
    }

    private fun Cursor.toItem(context: Context): WatchNextItem? {
        if (long(WatchNextPrograms.COLUMN_BROWSABLE) == 0L) return null
        val title = string(WatchNextPrograms.COLUMN_TITLE) ?: return null
        val intent = string(WatchNextPrograms.COLUMN_INTENT_URI)
            ?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            ?: string(WatchNextPrograms.COLUMN_PACKAGE_NAME)
                ?.let { context.packageManager.getLeanbackLaunchIntentForPackage(it) }
            ?: return null

        val episode = listOfNotNull(
            string(WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER)?.let { "S$it" },
            string(WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER)?.let { "E$it" },
        ).joinToString("")
        val subtitle = listOfNotNull(episode.ifBlank { null }, string(WatchNextPrograms.COLUMN_EPISODE_TITLE))
            .joinToString(" · ")
            .ifBlank { null }

        val position = long(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
        val duration = long(WatchNextPrograms.COLUMN_DURATION_MILLIS)
        val progress = if (position != null && duration != null && duration > 0) {
            (position.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            null
        }

        return WatchNextItem(
            id = long(WatchNextPrograms._ID) ?: return null,
            title = title,
            subtitle = subtitle,
            image = (string(WatchNextPrograms.COLUMN_THUMBNAIL_URI)
                ?: string(WatchNextPrograms.COLUMN_POSTER_ART_URI))?.let(Uri::parse),
            progress = progress,
            intent = intent,
        )
    }

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)?.ifBlank { null }
    }

    private fun Cursor.long(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private const val MAX_ITEMS = 20
}
