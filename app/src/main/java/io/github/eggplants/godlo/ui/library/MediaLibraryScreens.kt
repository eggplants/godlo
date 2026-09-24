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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.LibraryMusic
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.core.MediaKind
import io.github.eggplants.godlo.library.AudioArt
import io.github.eggplants.godlo.library.MediaFile
import io.github.eggplants.godlo.ui.components.ConfirmDeleteDialog
import io.github.eggplants.godlo.ui.components.EmptyState
import io.github.eggplants.godlo.ui.components.SiteFilterRow
import io.github.eggplants.godlo.ui.components.formatSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudioLibraryScreen(container: AppContainer) {
    val library by container.library.library.collectAsStateWithLifecycle()
    val nowPlaying by container.music.state.collectAsStateWithLifecycle()
    var site by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<MediaFile?>(null) }
    val tracks = library.audio.filter { site == null || it.site == site }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("音楽") },
                actions = {
                    if (tracks.isNotEmpty()) {
                        IconButton(onClick = {
                            val shuffled = tracks.map { it.file }.shuffled()
                            container.music.play(shuffled, 0)
                        }) { Icon(Icons.Filled.Shuffle, "シャッフル再生") }
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
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    SiteFilterRow(library.sites(MediaKind.AUDIO), site, {
                        site = it
                    }, Modifier.padding(vertical = 4.dp))
                }
                if (tracks.isEmpty() && !library.loading) {
                    item {
                        EmptyState(
                            Icons.Outlined.LibraryMusic,
                            "音楽はまだありません",
                            "ダウンロード画面で「音楽」を選ぶと、音声だけを保存できます。"
                        )
                    }
                }
                items(tracks, key = { it.file.absolutePath }) { track ->
                    val playing = nowPlaying.path == track.file.absolutePath
                    ListItem(
                        headlineContent = {
                            Text(
                                track.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (playing) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Unspecified
                                }
                            )
                        },
                        supportingContent = {
                            Text(
                                listOf(track.site, track.folder, formatSize(track.size)).filter {
                                    it.isNotBlank()
                                }.joinToString(" · ")
                            )
                        },
                        leadingContent = {
                            AudioArtwork(track.file, Modifier.size(52.dp), playing)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .animateItem()
                            .combinedClickable(
                                onClick = {
                                    container.music.play(
                                        tracks.map {
                                            it.file
                                        },
                                        tracks.indexOf(track)
                                    )
                                },
                                onLongClick = { deleting = track }
                            )
                    )
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
                        Icons.Filled.MusicNote,
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
    var site by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<MediaFile?>(null) }
    val videos = library.video.filter { site == null || it.site == site }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text("動画") }, scrollBehavior = scrollBehavior) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = library.loading,
            onRefresh = container.library::refresh,
            modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SiteFilterRow(library.sites(MediaKind.VIDEO), site, {
                        site = it
                    }, Modifier.padding(vertical = 4.dp))
                }
                if (videos.isEmpty() && !library.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(Icons.Outlined.Movie, "動画はまだありません", "yt-dlp で保存した動画がここに並びます。")
                    }
                }
                items(videos, key = { it.file.absolutePath }) { video ->
                    Column(
                        Modifier.animateItem().combinedClickable(onClick = {
                            onOpen(video.file)
                        }, onLongClick = {
                            deleting =
                                video
                        }),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                SubcomposeAsyncImage(
                                    model = video.file,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    error = {
                                        Box(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Outlined.Movie,
                                                null,
                                                tint = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                )
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(44.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.4f),
                                            MaterialTheme.shapes.extraLarge
                                        )
                                        .padding(8.dp)
                                )
                            }
                        }
                        Text(
                            video.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOf(video.site, video.folder, formatSize(video.size)).filter {
                                it.isNotBlank()
                            }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
