package com.jayked.tvhome.ui

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jayked.tvhome.HomeState
import com.jayked.tvhome.data.AppEntry
import com.jayked.tvhome.data.WatchNextItem
import com.jayked.tvhome.data.Weather
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

private val Gutter = 48.dp

@Composable
fun HomeScreen(
    state: HomeState,
    homePresses: Int,
    onLaunch: (Intent) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onAppInfo: (String) -> Unit,
) {
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    val listState = rememberLazyListState()
    val initialFocus = remember { FocusRequester() }

    LaunchedEffect(state.loaded, homePresses) {
        if (!state.loaded) return@LaunchedEffect
        listState.scrollToItem(0)
        runCatching { initialFocus.requestFocus() }
    }

    val hero = state.watchNext.firstOrNull()
    Box(Modifier.fillMaxSize().background(Background)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            item { TopBar(state.weather) }
            if (hero != null) {
                item { Hero(hero, Modifier.focusRequester(initialFocus)) { onLaunch(hero.intent) } }
            }
            if (state.watchNext.isNotEmpty()) {
                item {
                    Section("Continue watching") {
                        items(state.watchNext, key = { it.id }) { item ->
                            WatchNextCard(item) { onLaunch(item.intent) }
                        }
                    }
                }
            }
            if (state.favorites.isNotEmpty()) {
                item {
                    Section("Favorites") {
                        items(state.favorites, key = { it.packageName }) { app ->
                            val first = hero == null && app == state.favorites.first()
                            AppCard(
                                app = app,
                                modifier = if (first) Modifier.focusRequester(initialFocus) else Modifier,
                                onClick = { onLaunch(app.intent) },
                                onLongClick = { menuApp = app },
                            )
                        }
                    }
                }
            }
            item {
                Section("Apps") {
                    items(state.apps, key = { it.packageName }) { app ->
                        val first = hero == null && state.favorites.isEmpty() && app == state.apps.first()
                        AppCard(
                            app = app,
                            modifier = if (first) Modifier.focusRequester(initialFocus) else Modifier,
                            onClick = { onLaunch(app.intent) },
                            onLongClick = { menuApp = app },
                        )
                    }
                }
            }
            if (state.shortcuts.isNotEmpty()) {
                item {
                    Section("Inputs & settings") {
                        items(state.shortcuts, key = { it.label }) { shortcut ->
                            ShortcutCard(shortcut) { onLaunch(shortcut.intent) }
                        }
                    }
                }
            }
        }
    }

    menuApp?.let { app ->
        val isFavorite = state.favorites.any { it.packageName == app.packageName }
        AppMenu(
            app = app,
            isFavorite = isFavorite,
            onDismiss = { menuApp = null },
            onToggleFavorite = { onToggleFavorite(app.packageName); menuApp = null },
            onMove = { delta -> onMoveFavorite(app.packageName, delta); menuApp = null },
            onAppInfo = { onAppInfo(app.packageName); menuApp = null },
        )
    }
}

@Composable
private fun Section(title: String, content: LazyListScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Gutter, bottom = 14.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

@Composable
private fun TopBar(weather: Weather?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clock()
        Spacer(Modifier.weight(1f))
        if (weather != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${weather.temperature}${weather.unit}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listOfNotNull(weather.description.ifBlank { null }, weather.place).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            Text("Continue watching", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                Text("Resume")
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
    onMove: (Int) -> Unit,
    onAppInfo: () -> Unit,
) {
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstButton.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF2A2B30)),
        ) {
            Column(
                modifier = Modifier.padding(28.dp).width(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                Spacer(Modifier.height(6.dp))
                MenuButton(
                    text = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    modifier = Modifier.focusRequester(firstButton),
                    onClick = onToggleFavorite,
                )
                if (isFavorite) {
                    MenuButton("Move left", onClick = { onMove(-1) })
                    MenuButton("Move right", onClick = { onMove(1) })
                }
                MenuButton("App info", onClick = onAppInfo)
            }
        }
    }
}

@Composable
private fun MenuButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) { Text(text) }
}
