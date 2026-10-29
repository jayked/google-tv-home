package com.jayked.tvhome.data

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.graphics.scale
import java.io.File
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

data class WatchedMedia(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val artwork: String?,
    val positionMs: Long?,
    val durationMs: Long?,
    val watchedAt: Long,
)

/** What video apps reported playing through their media sessions, newest first. */
class MediaHistory(private val context: Context) {
    private val file = File(context.filesDir, "media_history.json")
    private val artworkDir = File(context.filesDir, "artwork")

    fun load(): List<WatchedMedia> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        runCatching {
            val items = JSONArray(file.readText())
            (0 until items.length()).map { items.getJSONObject(it).toMedia() }
        }.getOrDefault(emptyList())
    }

    fun record(media: WatchedMedia) = synchronized(lock) {
        val previous = load()
        // Apps often send artwork in only some of their updates: keep what we already have.
        val merged = if (media.artwork != null) media else media.copy(artwork = previous.find { it.sameAs(media) }?.artwork)
        val items = (listOf(merged) + previous.filterNot { it.sameAs(media) }).take(MAX_ITEMS)
        file.writeText(JSONArray(items.map { it.toJson() }).toString())
        // Drop artwork that no longer belongs to any remembered item.
        val kept = items.mapNotNull { it.artwork }.toSet()
        artworkDir.listFiles()?.filter { Uri.fromFile(it).toString() !in kept }?.forEach { it.delete() }
    }

    /** Fills in artwork found elsewhere, without changing the item's place in the history. */
    fun setArtwork(media: WatchedMedia, artwork: String) = synchronized(lock) {
        val items = load().map { if (it.sameAs(media) && it.artwork == null) it.copy(artwork = artwork) else it }
        file.writeText(JSONArray(items.map { it.toJson() }).toString())
    }

    /** Stores a session's artwork bitmap, since apps' own artwork URIs are often unreadable to us. */
    fun saveArtwork(media: WatchedMedia, bitmap: Bitmap): String {
        artworkDir.mkdirs()
        // Size in the name: a different image for the same title (e.g. a logo, then real artwork) gets its own file.
        val target = File(artworkDir, "${(media.packageName + media.title).hashCode().toUInt()}-${bitmap.width}x${bitmap.height}.jpg")
        if (!target.exists()) {
            val scaled = if (bitmap.width > ARTWORK_WIDTH) {
                bitmap.scale(ARTWORK_WIDTH, bitmap.height * ARTWORK_WIDTH / bitmap.width)
            } else {
                bitmap
            }
            target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        return Uri.fromFile(target).toString()
    }

    private fun WatchedMedia.sameAs(other: WatchedMedia) =
        packageName == other.packageName && title.equals(other.title, ignoreCase = true)

    private fun WatchedMedia.toJson() = JSONObject()
        .put("package", packageName)
        .put("title", title)
        .putOpt("subtitle", subtitle)
        .putOpt("artwork", artwork)
        .putOpt("position", positionMs)
        .putOpt("duration", durationMs)
        .put("watchedAt", watchedAt)

    private fun JSONObject.toMedia() = WatchedMedia(
        packageName = getString("package"),
        title = getString("title"),
        subtitle = optString("subtitle").ifBlank { null },
        artwork = optString("artwork").ifBlank { null },
        positionMs = if (has("position")) getLong("position") else null,
        durationMs = if (has("duration")) getLong("duration") else null,
        watchedAt = getLong("watchedAt"),
    )

    companion object {
        /**
         * Each session's own "open the player" intent, keyed by [key]. Only valid while this
         * process lives, so it is kept in memory rather than in the history file.
         */
        val sessionActivities = ConcurrentHashMap<String, PendingIntent>()

        fun key(packageName: String, title: String) = "$packageName|${title.lowercase()}"

        private val lock = Any()
        private const val MAX_ITEMS = 20
        private const val ARTWORK_WIDTH = 640
    }
}

/**
 * Watches other apps' media sessions and records what they play. Reading sessions needs
 * notification access, which Google TV has no settings screen for; scripts/install.sh grants it.
 */
class MediaHistoryService : NotificationListenerService() {
    private lateinit var history: MediaHistory
    private lateinit var sessionManager: MediaSessionManager
    private val tracked = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { track(it.orEmpty()) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lookups = Executors.newSingleThreadExecutor()

    /** Prime Video details by content ID; empty while the lookup runs, and after it failed (not retried). */
    private val primeDetails = ConcurrentHashMap<String, Optional<PrimeVideo.Details>>()

    override fun onListenerConnected() {
        history = MediaHistory(this)
        sessionManager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, MediaHistoryService::class.java)
        sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, component)
        track(sessionManager.getActiveSessions(component))
    }

    override fun onListenerDisconnected() {
        sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged)
        track(emptyList())
    }

    private fun track(active: List<MediaController>) {
        val activeTokens = active.map { it.sessionToken }.toSet()
        tracked.keys.filter { it !in activeTokens }.forEach { token ->
            tracked.remove(token)?.let { (controller, callback) -> controller.unregisterCallback(callback) }
        }
        active.filter { it.sessionToken !in tracked && isVideoApp(it.packageName) }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = record(controller)
                override fun onPlaybackStateChanged(state: PlaybackState?) = record(controller)
            }
            controller.registerCallback(callback)
            tracked[controller.sessionToken] = controller to callback
            record(controller)
        }
    }

    private fun record(controller: MediaController) {
        val state = controller.playbackState ?: return
        if (state.state != PlaybackState.STATE_PLAYING && state.state != PlaybackState.STATE_PAUSED) return
        // Without a title (Netflix reports none) there's nothing useful to show.
        val metadata = controller.metadata ?: return
        val reportedTitle = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata.text(MediaMetadata.METADATA_KEY_TITLE)
        val prime = if (reportedTitle == null) primeDetails(controller, metadata) ?: return else null
        val title = reportedTitle ?: prime?.title ?: return
        val media = WatchedMedia(
            packageName = controller.packageName,
            title = title,
            subtitle = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: metadata.text(MediaMetadata.METADATA_KEY_ARTIST)
                ?: prime?.subtitle
                ?: metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION),
            artwork = null,
            positionMs = state.position.takeIf { it > 0 },
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
            watchedAt = System.currentTimeMillis(),
        )
        // Real artwork first; the display icon can be just the app's logo (Spotify).
        val art = listOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART)
            .mapNotNull(metadata::getBitmap)
            .maxByOrNull { it.width * it.height }
        val artwork = art?.let { history.saveArtwork(media, it) }
            ?: metadata.text(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.text(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: prime?.image
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { history.saveArtwork(media, it) }
        history.record(media.copy(artwork = artwork))
        controller.sessionActivity?.let { MediaHistory.sessionActivities[MediaHistory.key(media.packageName, title)] = it }
    }

    /**
     * Prime Video's title, looked up once per content ID in the background; records the session
     * again when it arrives. Null until then, or when the session isn't Prime Video's.
     */
    private fun primeDetails(controller: MediaController, metadata: MediaMetadata): PrimeVideo.Details? {
        if (controller.packageName != PrimeVideo.PACKAGE) return null
        val contentId = metadata.text(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf(PrimeVideo::isContentId) ?: return null
        primeDetails[contentId]?.let { return it.orElse(null) }
        primeDetails[contentId] = Optional.empty()
        lookups.execute {
            val details = runCatching { PrimeVideo.details(contentId) }.getOrNull() ?: return@execute
            primeDetails[contentId] = Optional.of(details)
            mainHandler.post { record(controller) }
        }
        return null
    }

    private fun isVideoApp(packageName: String): Boolean {
        if (packageName == this.packageName || packageName in MUSIC_APPS) return false
        val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return false
        return info.category != ApplicationInfo.CATEGORY_AUDIO
    }

    private fun MediaMetadata.text(key: String) = getString(key)?.ifBlank { null }

    private companion object {
        // Music apps that don't declare android:appCategory="audio".
        val MUSIC_APPS = setOf(
            "com.google.android.youtube.tvmusic",
            "com.amazon.music.tv",
            "deezer.android.tv",
            "com.aspiro.tidal",
            "com.google.android.bluetooth",
        )
    }
}
