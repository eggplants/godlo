package io.github.eggplants.godlo.ui.player

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.player.MusicPlayer
import io.github.eggplants.godlo.ui.components.formatDuration
import kotlinx.coroutines.delay

/** Where playback is, polled while it is on screen: the controller has no position callback. */
@Composable
private fun rememberPosition(player: MusicPlayer, playing: Boolean): Long {
    var position by remember { mutableLongStateOf(player.positionMs) }
    LaunchedEffect(playing) {
        while (true) {
            position = player.positionMs
            if (!playing) break
            delay(500)
        }
    }
    return position
}

@Composable
private fun Artwork(bytes: ByteArray?, modifier: Modifier = Modifier) {
    val bitmap =
        remember(bytes) {
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    Box(
        modifier.background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Filled.MusicNote,
                null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxSize(0.4f)
            )
        }
    }
}

@Composable
fun MiniPlayer(container: AppContainer, onExpand: () -> Unit) {
    val state by container.music.state.collectAsStateWithLifecycle()
    val position = rememberPosition(container.music, state.isPlaying)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand)
    ) {
        Column {
            LinearProgressIndicator(
                progress = {
                    if (state.durationMs >
                        0
                    ) {
                        position.toFloat() / state.durationMs
                    } else {
                        0f
                    }
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                drawStopIndicator = {},
                gapSize = 0.dp
            )
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(state.artwork, Modifier.size(44.dp).clip(MaterialTheme.shapes.small))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.artist.isNotBlank()) {
                        Text(
                            state.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = container.music::toggle) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) "一時停止" else "再生"
                    )
                }
                IconButton(onClick = container.music::next, enabled = state.hasNext) {
                    Icon(Icons.Filled.SkipNext, "次へ")
                }
                IconButton(onClick = container.music::stop) { Icon(Icons.Filled.Close, "停止") }
            }
        }
    }
}

@Composable
fun NowPlayingScreen(container: AppContainer, onBack: () -> Unit) {
    val music = container.music
    val state by music.state.collectAsStateWithLifecycle()
    val position = rememberPosition(music, state.isPlaying)
    var seeking by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(state.active) { if (!state.active) onBack() }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            Column(
                Modifier.fillMaxSize().padding(
                    horizontal = 24.dp
                ).padding(top = 32.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, "閉じる") }
                    Text(
                        "再生中",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.size(48.dp))
                }
                if (!wide) {
                    Artwork(
                        state.artwork,
                        Modifier.widthIn(
                            max = 420.dp
                        ).fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge)
                    )
                }
                Column(
                    Modifier.widthIn(max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.artist.isNotBlank()) {
                        Text(
                            state.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = seeking ?: position.toFloat(),
                        onValueChange = { seeking = it },
                        onValueChangeFinished = {
                            seeking?.let { music.seekTo(it.toLong()) }
                            seeking = null
                        },
                        valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat()
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            formatDuration(seeking?.toLong() ?: position),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatDuration(state.durationMs),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconToggleButton(checked = state.shuffle, onCheckedChange = {
                            music.toggleShuffle()
                        }) {
                            Icon(Icons.Filled.Shuffle, "シャッフル")
                        }
                        IconButton(onClick = music::previous, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipPrevious, "前へ", Modifier.size(32.dp))
                        }
                        FilledIconButton(
                            onClick = music::toggle,
                            modifier = Modifier.size(80.dp),
                            // Morphs from a circle to a rounded square while playing.
                            shape = if (state.isPlaying) {
                                MaterialTheme.shapes.extraLarge
                            } else {
                                CircleShape
                            },
                            colors = IconButtonDefaults.filledIconButtonColors()
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) "一時停止" else "再生",
                                Modifier.size(40.dp)
                            )
                        }
                        IconButton(
                            onClick = music::next,
                            enabled = state.hasNext,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Filled.SkipNext, "次へ", Modifier.size(32.dp))
                        }
                        IconToggleButton(
                            checked = state.repeatMode != Player.REPEAT_MODE_OFF,
                            onCheckedChange = { music.cycleRepeat() }
                        ) {
                            Icon(
                                if (state.repeatMode ==
                                    Player.REPEAT_MODE_ONE
                                ) {
                                    Icons.Filled.RepeatOne
                                } else {
                                    Icons.Filled.Repeat
                                },
                                "リピート"
                            )
                        }
                    }
                }
            }
        }
    }
}
