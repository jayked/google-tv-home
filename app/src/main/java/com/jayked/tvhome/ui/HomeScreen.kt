package com.jayked.tvhome.ui

import android.content.Intent
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jayked.tvhome.HomeState
import com.jayked.tvhome.R
import com.jayked.tvhome.data.AppEntry
import com.jayked.tvhome.data.Shortcut
import com.jayked.tvhome.data.WatchNextItem
import com.jayked.tvhome.data.Weather
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Gutter = 48.dp
private val CardSpacing = 18.dp
private const val AppColumns = 5

/**
 * Scrolls only as far as needed to show the focused tile. The TV default keeps the focused tile
 * about 30% down the screen, which makes the page jump when moving between the top bar and the grid.
 */
@OptIn(ExperimentalFoundationApi::class)
private val ScrollOnlyWhenNeeded = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        // Leave room for the focused tile's scale-up and border when scrolling to it.
        val margin = containerSize * 0.06f
        return when {
            offset >= 0 && trailing <= containerSize -> 0f
            offset < 0 || size > containerSize -> offset - margin
            else -> trailing - containerSize + margin
        }
    }
}

/** An app tile, and whether it was picked in the Favorites row or in the Apps grid. */
private data class AppTarget(val app: AppEntry, val inFavorites: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    homePresses: Int,
    onLaunch: (Intent) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onMoveApp: (String, Int) -> Unit,
    onAppInfo: (String) -> Unit,
) {
    var menuTarget by remember { mutableStateOf<AppTarget?>(null) }
    var moving by remember { mutableStateOf<AppTarget?>(null) }
    val listState = rememberLazyListState()
    val initialFocus = remember { FocusRequester() }
    // Tile to pull focus onto: the one being moved, or the one whose menu just closed. Both may
    // have been recomposed elsewhere (another row, or after the Favorites row appeared).
    var focusTarget by remember { mutableStateOf<AppTarget?>(null) }
    val targetFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Focusing the top row of content scrolls all the way up so the clock and weather show again.
    val revealTopBar = Modifier.onFocusChanged {
        if (it.isFocused) scope.launch { listState.animateScrollToItem(0) }
    }

    LaunchedEffect(focusTarget, state.apps, state.favorites) {
        if (focusTarget == null) return@LaunchedEffect
        runCatching { targetFocus.requestFocus() }
        if (moving == null) focusTarget = null
    }

    fun stopMoving() {
        moving = null
        focusTarget = null
    }
    BackHandler(enabled = moving != null) { stopMoving() }

    fun AppTarget?.matches(app: AppEntry, inFavorites: Boolean) =
        this != null && this.app.packageName == app.packageName && this.inFavorites == inFavorites

    LaunchedEffect(state.loaded, homePresses) {
        if (!state.loaded) return@LaunchedEffect
        listState.scrollToItem(0)
        runCatching { initialFocus.requestFocus() }
    }

    val hero = state.watchNext.firstOrNull()
    CompositionLocalProvider(LocalBringIntoViewSpec provides ScrollOnlyWhenNeeded) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Background)
                .onPreviewKeyEvent { event ->
                    val target = moving ?: return@onPreviewKeyEvent false
                    val delta = when (event.key) {
                        Key.DirectionLeft -> -1
                        Key.DirectionRight -> 1
                        Key.DirectionUp -> if (target.inFavorites) 0 else -AppColumns
                        Key.DirectionDown -> if (target.inFavorites) 0 else AppColumns
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (event.type == KeyEventType.KeyUp) stopMoving()
                            return@onPreviewKeyEvent true
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.type == KeyEventType.KeyDown && delta != 0) {
                        val packageName = target.app.packageName
                        if (target.inFavorites) onMoveFavorite(packageName, delta) else onMoveApp(packageName, delta)
                    }
                    true
                },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                item { TopBar(state.weather, state.shortcuts, onLaunch) }
                if (hero != null) {
                    item { Hero(hero, Modifier.focusRequester(initialFocus).then(revealTopBar)) { onLaunch(hero.intent) } }
                }
                if (state.watchNext.isNotEmpty()) {
                    item {
                        Section(stringResource(R.string.continue_watching)) {
                            items(state.watchNext, key = { it.id }) { item ->
                                WatchNextCard(item) { onLaunch(item.intent) }
                            }
                        }
                    }
                }
                if (state.favorites.isNotEmpty()) {
                    item {
                        Section(stringResource(R.string.favorites), hint = if (moving?.inFavorites == true) stringResource(R.string.move_hint_row) else null) {
                            items(state.favorites, key = { it.packageName }) { app ->
                                val first = hero == null && app == state.favorites.first()
                                val isMoving = moving.matches(app, inFavorites = true)
                                AppCard(
                                    app = app,
                                    moving = isMoving,
                                    modifier = Modifier
                                        .width(208.dp)
                                        .then(if (first) Modifier.focusRequester(initialFocus) else Modifier)
                                        .then(if (focusTarget.matches(app, inFavorites = true)) Modifier.focusRequester(targetFocus) else Modifier)
                                        .then(if (hero == null) revealTopBar else Modifier),
                                    onClick = { onLaunch(app.intent) },
                                    onLongClick = { menuTarget = AppTarget(app, inFavorites = true) },
                                )
                            }
                        }
                    }
                }
                item {
                    Column {
                        SectionTitle(stringResource(R.string.apps), hint = if (moving?.inFavorites == false) stringResource(R.string.move_hint_grid) else null)
                        Column(
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(CardSpacing),
                        ) {
                            state.apps.chunked(AppColumns).forEachIndexed { rowIndex, row ->
                                val topRow = rowIndex == 0 && hero == null && state.favorites.isEmpty()
                                Row(horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
                                    row.forEach { app ->
                                        val first = hero == null && state.favorites.isEmpty() && app == state.apps.first()
                                        val isMoving = moving.matches(app, inFavorites = false)
                                        AppCard(
                                            app = app,
                                            moving = isMoving,
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(if (first) Modifier.focusRequester(initialFocus) else Modifier)
                                                .then(if (focusTarget.matches(app, inFavorites = false)) Modifier.focusRequester(targetFocus) else Modifier)
                                                .then(if (topRow) revealTopBar else Modifier),
                                            onClick = { onLaunch(app.intent) },
                                            onLongClick = { menuTarget = AppTarget(app, inFavorites = false) },
                                        )
                                    }
                                    // Keep the last row's cards the same width as the rows above.
                                    repeat(AppColumns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    menuTarget?.let { target ->
        val app = target.app
        val isFavorite = state.favorites.any { it.packageName == app.packageName }
        fun close(refocus: AppTarget = target) {
            menuTarget = null
            focusTarget = refocus
        }
        AppMenu(
            app = app,
            isFavorite = isFavorite,
            onDismiss = { close() },
            onToggleFavorite = {
                onToggleFavorite(app.packageName)
                // Removing it from Favorites drops that tile, so land on its tile in the grid instead.
                close(if (target.inFavorites && isFavorite) target.copy(inFavorites = false) else target)
            },
            onMove = {
                moving = target
                close()
            },
            onAppInfo = {
                onAppInfo(app.packageName)
                close()
            },
        )
    }
}

@Composable
private fun Section(title: String, hint: String? = null, content: LazyListScope.() -> Unit) {
    Column {
        SectionTitle(title, hint)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    Row(
        modifier = Modifier.padding(start = Gutter, end = Gutter, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (hint != null) {
            Spacer(Modifier.width(20.dp))
            Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TopBar(weather: Weather?, shortcuts: List<Shortcut>, onLaunch: (Intent) -> Unit) {
    // The settings menu stays focusable but invisible until focus moves up into it; it then
    // fades in over the weather.
    var menuFocused by remember { mutableStateOf(false) }
    var focusedLabel by remember { mutableStateOf("") }
    val menuAlpha by animateFloatAsState(if (menuFocused) 1f else 0f, label = "settingsMenu")

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clock()
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.CenterEnd) {
            if (weather != null) Weather(weather, Modifier.alpha(1f - menuAlpha))
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .alpha(menuAlpha)
                    .onFocusChanged { menuFocused = it.hasFocus },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shortcuts.forEachIndexed { index, shortcut ->
                        IconButton(
                            onClick = { onLaunch(shortcut.intent) },
                            modifier = Modifier
                                .onFocusChanged { if (it.isFocused) focusedLabel = shortcut.label }
                                // Only Down leaves the menu.
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                    if (index == 0) left = FocusRequester.Cancel
                                    if (index == shortcuts.lastIndex) right = FocusRequester.Cancel
                                },
                        ) {
                            Icon(shortcut.icon, contentDescription = shortcut.label)
                        }
                    }
                }
                Text(
                    text = focusedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Weather(weather: Weather, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(
            text = "${weather.temperature}${weather.unit}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = listOfNotNull(weather.description?.let { stringResource(it) }, weather.place).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val datePattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM") }
    Column {
        Text(
            text = DateFormat.getTimeFormat(context).format(now),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = DateFormat.format(datePattern, now).toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Hero(item: WatchNextItem, buttonModifier: Modifier, onResume: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = Gutter)
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AsyncImage(
            model = item.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.9f), 0.7f to Color.Transparent)),
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(40.dp).fillMaxWidth(0.5f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.continue_watching), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f)) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onResume, modifier = buttonModifier) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.resume))
            }
        }
    }
}

@Composable
private fun AppMenu(
    app: AppEntry,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMove: () -> Unit,
    onAppInfo: () -> Unit,
) {
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstButton.requestFocus() } }
    // The menu opens while OK is still held from the long press. Swallow every event of that press
    // (repeats and the release) so letting go doesn't click the focused button.
    val openedAt = remember { SystemClock.uptimeMillis() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF2A2B30)),
        ) {
            Column(
                modifier = Modifier
                    .onPreviewKeyEvent { it.nativeKeyEvent.downTime < openedAt }
                    .padding(28.dp)
                    .width(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                Spacer(Modifier.height(6.dp))
                MenuButton(
                    text = stringResource(if (isFavorite) R.string.menu_remove_favorite else R.string.menu_add_favorite),
                    modifier = Modifier.focusRequester(firstButton),
                    onClick = onToggleFavorite,
                )
                MenuButton(stringResource(R.string.menu_move), onClick = onMove)
                MenuButton(stringResource(R.string.menu_app_info), onClick = onAppInfo)
            }
        }
    }
}

@Composable
private fun MenuButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) { Text(text) }
}
