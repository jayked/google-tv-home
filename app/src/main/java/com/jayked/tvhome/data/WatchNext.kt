package com.jayked.tvhome.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract.WatchNextPrograms
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

data class WatchNextItem(
    val id: String,
    val packageName: String?,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val progress: Float?,
    val intent: Intent,
    val lastWatched: Long,
    /** Reopens the app's player directly; set while the app's media session is still around. */
    val sessionActivity: PendingIntent? = null,
    /** Playing right now, e.g. a stream left running behind the home screen. */
    val playing: Boolean = false,
)

/**
 * The most recently watched titles: what apps published to Watch Next, plus what the media
 * session listener saw playing. Opening one resumes it through the app's deep link when it gave
 * one, otherwise by opening the app (which usually returns to where playback stopped).
 */
object ContinueWatching {
    /** Titles already looked up on TMDB this run, so misses aren't retried on every refresh. */
    private val lookedUp = ConcurrentHashMap.newKeySet<String>()

    /** Some apps (Disney+) report no artwork at all: find it on TMDB by title instead. */
    private fun WatchedMedia.withLookedUpArtwork(history: MediaHistory): WatchedMedia {
        if (artwork != null || !Tmdb.isConfigured || !lookedUp.add(title.lowercase())) return this
        val found = runCatching { Tmdb.backdrop(title) }.getOrNull() ?: return this
        history.setArtwork(this, found)
        return copy(artwork = found)
    }

    /** Next-episode lookups by series and episode; empty after a miss, so it isn't retried. */
    private val nextEpisodes = ConcurrentHashMap<String, Optional<Pair<Tmdb.Episode, Tmdb.Episode>>>()

    /**
     * For an episode watched to the end (into the credits), the one after it, found on TMDB, once
     * it is likely out in the app too.
     */
    private fun WatchedMedia.nextEpisode(): Tmdb.Episode? {
        val position = positionMs ?: return null
        val duration = durationMs ?: return null
        if (position < duration * FINISHED || !Tmdb.isConfigured) return null
        // e.g. Disney+'s "S4: Afl. 7 Bears" or "S4:E7 Bears"
        val match = EPISODE.find(subtitle ?: return null) ?: return null
        val (season, episode) = match.destructured
        val (current, next) = nextEpisodes.getOrPut("${title.lowercase()}|$season|$episode") {
            Optional.ofNullable(runCatching { Tmdb.nextEpisode(title, season.toInt(), episode.toInt()) }.getOrNull())
        }.orElse(null) ?: return null
        // TMDB has the original air dates; apps here often release later. How long after its air
        // date this episode was watched bounds that delay; capped, so a years-old show counts as out.
        val watched = Instant.ofEpochMilli(watchedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val delay = ChronoUnit.DAYS.between(current.aired, watched).coerceIn(0, MAX_RELEASE_DELAY_DAYS)
        return next.takeUnless { it.aired.plusDays(delay).isAfter(LocalDate.now()) }
    }

    fun load(context: Context, limit: Int): List<WatchNextItem> {
        val pm = context.packageManager
        val history = MediaHistory(context)
        val playing = MediaHistory.playing.value
        val fromSessions = history.load().take(limit).map { media -> media.withLookedUpArtwork(history) }.mapNotNull { media ->
            // Not FLAG_ACTIVITY_RESET_TASK_IF_NEEDED: bring the app's task back as it was left.
            val intent = pm.getLeanbackLaunchIntentForPackage(media.packageName)
                ?: pm.getLaunchIntentForPackage(media.packageName)
                ?: return@mapNotNull null
            val next = media.nextEpisode()
            WatchNextItem(
                id = "session:${media.packageName}:${media.title}",
                packageName = media.packageName,
                title = media.title,
                subtitle = next?.let { listOfNotNull("S${it.season}E${it.number}", it.name).joinToString(" · ") } ?: media.subtitle,
                image = next?.still ?: media.artwork,
                progress = if (next != null) null else progress(media.positionMs, media.durationMs),
                intent = intent,
                lastWatched = media.watchedAt,
                sessionActivity = MediaHistory.sessionActivities[MediaHistory.key(media.packageName, media.title)],
                playing = MediaHistory.key(media.packageName, media.title) in playing,
            )
        }
        return (WatchNextRepository.load(context) + fromSessions)
            // What's playing comes first, ahead of its own Watch Next entry.
            .sortedWith(compareByDescending<WatchNextItem> { it.playing }.thenByDescending { it.lastWatched })
            .distinctBy { it.packageName to it.title.lowercase() }
            .take(limit)
    }

    private const val MAX_RELEASE_DELAY_DAYS = 30L

    /** Watched this far, an episode is done: what's left is credits. */
    private const val FINISHED = 0.95

    private val EPISODE = Regex("""^S(?:eason|eizoen)?\s*(\d+)(?!\d)\W*(?:E|Ep|Episode|Afl|Aflevering)?\.?\s*(\d+)\b""", RegexOption.IGNORE_CASE)
}

private fun progress(position: Long?, duration: Long?) =
    if (position != null && duration != null && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null

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

        return WatchNextItem(
            id = "provider:${long(WatchNextPrograms._ID) ?: return null}",
            packageName = string(WatchNextPrograms.COLUMN_PACKAGE_NAME),
            title = title,
            subtitle = subtitle,
            image = string(WatchNextPrograms.COLUMN_THUMBNAIL_URI) ?: string(WatchNextPrograms.COLUMN_POSTER_ART_URI),
            progress = progress(
                long(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS),
                long(WatchNextPrograms.COLUMN_DURATION_MILLIS),
            ),
            intent = intent,
            lastWatched = long(WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS) ?: 0L,
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
