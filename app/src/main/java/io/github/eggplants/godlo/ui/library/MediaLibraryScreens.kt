package io.github.eggplants.godlo.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.AppSettings
import io.github.eggplants.godlo.core.LibraryLayout
import io.github.eggplants.godlo.core.MediaKind
import io.github.eggplants.godlo.library.AudioArt
import io.github.eggplants.godlo.library.MediaFile
import io.github.eggplants.godlo.ui.components.ConfirmDeleteDialog
import io.github.eggplants.godlo.ui.components.EmptyState
import io.github.eggplants.godlo.ui.components.LayoutMenuButton
import io.github.eggplants.godlo.ui.components.SiteFilterRow
import io.github.eggplants.godlo.ui.components.formatSize
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudioLibraryScreen(container: AppContainer) {
    val library by container.library.library.collectAsStateWithLifecycle()
    val nowPlaying by container.audio.state.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val layout = settings.audioLayout
    val scope = rememberCoroutineScope()
    var site by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<MediaFile?>(null) }
    val tracks = library.audio.filter { site == null || it.site == site }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_audio)) },
                actions = {
                    if (tracks.isNotEmpty()) {
                        IconButton(onClick = {
                            container.audio.play(tracks.map { it.file }.shuffled(), 0)
                        }) { Icon(Icons.Filled.Shuffle, stringResource(R.string.shuffle_play)) }
                    }
                    LayoutMenuButton(layout) { next ->
                        scope.launch { container.settings.update { it.copy(audioLayout = next) } }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = library.loading,
            onRefresh = container.library::refresh,
            modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()
        ) {
            LibraryGrid(layout, largeMinSize = 150.dp, smallMinSize = 96.dp) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SiteFilterRow(
                        library.sites(MediaKind.AUDIO),
                        site,
                        { site = it },
                        Modifier.padding(vertical = 4.dp)
                    )
                }
                if (tracks.isEmpty() && !library.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            Icons.Outlined.Headphones,
                            stringResource(R.string.audio_empty_title),
                            stringResource(R.string.audio_empty_body)
                        )
                    }
                }
                items(tracks, key = { it.file.absolutePath }) { track ->
                    val playing = nowPlaying.path == track.file.absolutePath
                    val modifier = Modifier
                        .animateItem()
                        .combinedClickable(
                            onClick = {
                                container.audio.play(tracks.map { it.file }, tracks.indexOf(track))
                            },
                            onLongClick = { deleting = track }
                        )
                    if (layout == LibraryLayout.LIST) {
                        MediaRow(
                            title = track.title,
                            details = track.details,
                            highlight = playing,
                            modifier = modifier
                        ) { AudioArtwork(track.file, Modifier.size(52.dp), playing) }
                    } else {
                        MediaTile(
                            title = track.title,
                            details = track.details,
                            compact = layout == LibraryLayout.SMALL_GRID,
                            highlight = playing,
                            modifier = modifier
                        ) {
                            AudioArtwork(
                                track.file,
                                Modifier.fillMaxWidth().aspectRatio(1f),
                                playing
                            )
                        }
                    }
                }
            }
        }
    }
    deleting?.let { track ->
        ConfirmDeleteDialog(track.title, {
            container.library.delete(track.file)
        }, { deleting = null })
    }
}

@Composable
fun AudioArtwork(file: File, modifier: Modifier = Modifier, playing: Boolean = false) {
    Box(modifier.clip(MaterialTheme.shapes.medium)) {
        SubcomposeAsyncImage(
            model = AudioArt(file),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AudioFile,
                        null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        )
        if (playing) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.GraphicEq, null, tint = Color.White) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoLibraryScreen(container: AppContainer, onOpen: (File) -> Unit) {
    val library by container.library.library.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val layout = settings.videoLayout
    val scope = rememberCoroutineScope()
    var site by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<MediaFile?>(null) }
    val videos = library.video.filter { site == null || it.site == site }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_videos)) },
                actions = {
                    LayoutMenuButton(layout) { next ->
                        scope.launch { container.settings.update { it.copy(videoLayout = next) } }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = library.loading,
            onRefresh = container.library::refresh,
            modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()
        ) {
            LibraryGrid(layout, largeMinSize = 240.dp, smallMinSize = 150.dp) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SiteFilterRow(
                        library.sites(MediaKind.VIDEO),
                        site,
                        { site = it },
                        Modifier.padding(vertical = 4.dp)
                    )
                }
                if (videos.isEmpty() && !library.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            Icons.Outlined.Movie,
                            stringResource(R.string.videos_empty_title),
                            stringResource(R.string.videos_empty_body)
                        )
                    }
                }
                items(videos, key = { it.file.absolutePath }) { video ->
                    val modifier = Modifier
                        .animateItem()
                        .combinedClickable(onClick = { onOpen(video.file) }, onLongClick = {
                            deleting =
                                video
                        })
                    if (layout == LibraryLayout.LIST) {
                        MediaRow(
                            title = video.title,
                            details = video.details,
                            modifier = modifier
                        ) {
                            VideoThumbnail(video.file, playIconSize = 24.dp, Modifier.width(128.dp))
                        }
                    } else {
                        val compact = layout == LibraryLayout.SMALL_GRID
                        MediaTile(
                            title = video.title,
                            details = video.details,
                            compact = compact,
                            modifier = modifier
                        ) {
                            VideoThumbnail(
                                video.file,
                                playIconSize = if (compact) 32.dp else 44.dp,
                                Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
    deleting?.let { video ->
        ConfirmDeleteDialog(video.title, {
            container.library.delete(video.file)
        }, { deleting = null })
    }
}

@Composable
private fun VideoThumbnail(file: File, playIconSize: Dp, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier.aspectRatio(16f / 9f)
    ) {
        Box(Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Movie, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            )
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(playIconSize)
                    .background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.extraLarge)
                    .padding(playIconSize / 6)
            )
        }
    }
}

private val MediaFile.details: String
    get() = listOf(site, folder, formatSize(size)).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
private fun MediaRow(
    title: String,
    details: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    leading: @Composable () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (highlight) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
        },
        supportingContent = { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = leading,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
    )
}

/** A picture over its title; [compact] keeps only a one-line title under it. */
@Composable
private fun MediaTile(
    title: String,
    details: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    picture: @Composable () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        picture()
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(
                title,
                style = with(MaterialTheme.typography) { if (compact) labelMedium else titleSmall },
                color = if (highlight) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
