package com.jayked.tvhome.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jayked.tvhome.R
import com.jayked.tvhome.data.AppEntry
import com.jayked.tvhome.data.WatchNextItem

private val focusBorder = Border(BorderStroke(3.dp, Color.White))
private val cardShape = CardDefaults.shape(RoundedCornerShape(12.dp))

@Composable
fun AppCard(
    app: AppEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    moving: Boolean = false,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.aspectRatio(16f / 9f),
        shape = cardShape,
        border = CardDefaults.border(
            focusedBorder = if (moving) Border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary)) else focusBorder,
        ),
        scale = CardDefaults.scale(focusedScale = if (moving) 1.12f else 1.08f),
    ) {
        if (app.banner != null) {
            Image(app.banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun WatchNextCard(item: WatchNextItem, app: AppEntry?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Without artwork (or when it fails to load) show the app's banner instead.
    val fallback = (app?.banner ?: app?.icon)?.let { BitmapPainter(it) }
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
        shape = cardShape,
        border = CardDefaults.border(focusedBorder = focusBorder),
        scale = CardDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                error = fallback,
                fallback = fallback,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 14.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item.progress?.let { progress ->
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(MaterialTheme.colorScheme.primary))
                }
            }
            if (item.playing) {
                NowPlayingBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
            }
        }
    }
}

/** Bouncing equalizer bars, marking what is playing right now. */
@Composable
private fun NowPlayingBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "now playing")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            // Each bar on its own period, so they never move in step.
            listOf(520, 680, 440).forEach { period ->
                val height by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(period, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "bar",
                )
                Box(Modifier.width(3.dp).fillMaxHeight(height).background(MaterialTheme.colorScheme.primary))
            }
        }
        Text(
            stringResource(R.string.now_playing),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
