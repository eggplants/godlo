package io.github.eggplants.godlo.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Badge
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.pluralStringResource
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
import io.github.eggplants.godlo.library.AudioArt
import io.github.eggplants.godlo.library.LibraryTree
import io.github.eggplants.godlo.library.MediaFile
import io.github.eggplants.godlo.library.TreeNode
import io.github.eggplants.godlo.ui.components.ConfirmDeleteDialog
import io.github.eggplants.godlo.ui.components.EmptyState
import io.github.eggplants.godlo.ui.components.LayoutMenuButton
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
    // The folder being looked at, e.g. "youtube.com/Some album"; empty for the sites.
    var pathKey by rememberSaveable { mutableStateOf("") }
    val path = pathKey.split("/").filter { it.isNotEmpty() }
    var deleting by remember { mutableStateOf<TreeNode<MediaFile>?>(null) }
    val nodes = LibraryTree.media.children(library.audio, path)
    val tracks = nodes.mapNotNull { (it as? TreeNode.Leaf)?.item?.file }
    // Everything inside the folder, for shuffling it.
    val below = library.audio.filter { it.path.size > path.size && it.path.take(path.size) == path }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    fun goUp() {
        pathKey = path.dropLast(1).joinToString("/")
    }
    BackHandler(enabled = path.isNotEmpty(), onBack = ::goUp)
    // A folder whose last track was deleted, here or elsewhere, is gone: step out of it.
    LaunchedEffect(nodes.isEmpty(), library.loading) {
        if (nodes.isEmpty() && !library.loading && path.isNotEmpty()) goUp()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopBar(
                root = stringResource(R.string.nav_audio),
                path = path,
                onUp = ::goUp,
                scrollBehavior = scrollBehavior
            ) {
                if (below.isNotEmpty()) {
                    IconButton(onClick = {
                        container.audio.play(below.map { it.file }.shuffled(), 0)
                    }) { Icon(Icons.Filled.Shuffle, stringResource(R.string.shuffle_play)) }
                }
                LayoutMenuButton(layout) { next ->
                    scope.launch { container.settings.update { it.copy(audioLayout = next) } }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = library.loading,
            onRefresh = container.library::refresh,
            modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()
        ) {
            LibraryGrid(layout, largeMinSize = 150.dp, smallMinSize = 96.dp) {
                if (nodes.isEmpty() && !library.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            Icons.Outlined.Headphones,
                            stringResource(R.string.audio_empty_title),
                            stringResource(R.string.audio_empty_body)
                        )
                    }
                }
                items(nodes, key = { it.key }) { node ->
                    val modifier = Modifier
                        .animateItem()
                        .combinedClickable(
                            onClick = {
                                when (node) {
                                    is TreeNode.Folder -> pathKey = node.path.joinToString("/")

                                    is TreeNode.Leaf ->
                                        container.audio.play(tracks, tracks.indexOf(node.item.file))
                                }
                            },
                            onLongClick = { deleting = node }
                        )
                    val (title, details, file) = when (node) {
                        is TreeNode.Folder -> Triple(
                            node.name,
                            pluralStringResource(R.plurals.entries, node.entries, node.entries),
                            node.latest.file
                        )

                        is TreeNode.Leaf -> Triple(
                            node.item.title,
                            formatSize(node.item.size),
                            node.item.file
                        )
                    }
                    val folderEntries = (node as? TreeNode.Folder)?.entries
                    val playing = node is TreeNode.Leaf && nowPlaying.path == file.absolutePath
                    if (layout == LibraryLayout.LIST) {
                        MediaRow(
                            title = title,
                            details = details,
                            highlight = playing,
                            modifier = modifier,
                            trailing = if (folderEntries != null) {
                                {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }
                            } else {
                                null
                            }
                        ) { AudioArtwork(file, Modifier.size(52.dp), playing) }
                    } else {
                        MediaTile(
                            title = title,
                            details = details,
                            compact = layout == LibraryLayout.SMALL_GRID,
                            highlight = playing,
                            modifier = modifier
                        ) {
                            AudioArtwork(
                                file,
                                Modifier.fillMaxWidth().aspectRatio(1f),
                                playing,
                                folderEntries
                            )
                        }
                    }
                }
            }
        }
    }
    deleting?.let { node ->
        ConfirmDeleteDialog(node.name, { container.library.delete(*node.dirs.toTypedArray()) }, {
            deleting = null
        })
    }
}

@Composable
fun AudioArtwork(
    file: File,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    folderEntries: Int? = null
) {
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
        if (folderEntries != null) {
            FolderBadge(folderEntries, Modifier.align(Alignment.BottomEnd).padding(6.dp))
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
    // The folder being looked at, e.g. "youtube.com/Some playlist"; empty for the sites.
    var pathKey by rememberSaveable { mutableStateOf("") }
    val path = pathKey.split("/").filter { it.isNotEmpty() }
    var deleting by remember { mutableStateOf<TreeNode<MediaFile>?>(null) }
    val nodes = LibraryTree.media.children(library.video, path)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    fun goUp() {
        pathKey = path.dropLast(1).joinToString("/")
    }
    BackHandler(enabled = path.isNotEmpty(), onBack = ::goUp)
    // A folder whose last video was deleted, here or elsewhere, is gone: step out of it.
    LaunchedEffect(nodes.isEmpty(), library.loading) {
        if (nodes.isEmpty() && !library.loading && path.isNotEmpty()) goUp()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopBar(
                root = stringResource(R.string.nav_videos),
                path = path,
                onUp = ::goUp,
                scrollBehavior = scrollBehavior
            ) {
                LayoutMenuButton(layout) { next ->
                    scope.launch { container.settings.update { it.copy(videoLayout = next) } }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = library.loading,
            onRefresh = container.library::refresh,
            modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()
        ) {
            LibraryGrid(layout, largeMinSize = 240.dp, smallMinSize = 150.dp) {
                if (nodes.isEmpty() && !library.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            Icons.Outlined.Movie,
                            stringResource(R.string.videos_empty_title),
                            stringResource(R.string.videos_empty_body)
                        )
                    }
                }
                items(nodes, key = { it.key }) { node ->
                    val modifier = Modifier
                        .animateItem()
                        .combinedClickable(
                            onClick = {
                                when (node) {
                                    is TreeNode.Folder -> pathKey = node.path.joinToString("/")
                                    is TreeNode.Leaf -> onOpen(node.item.file)
                                }
                            },
                            onLongClick = { deleting = node }
                        )
                    val (title, details, file) = when (node) {
                        is TreeNode.Folder -> Triple(
                            node.name,
                            pluralStringResource(R.plurals.entries, node.entries, node.entries),
                            node.latest.file
                        )

                        is TreeNode.Leaf -> Triple(
                            node.item.title,
                            formatSize(node.item.size),
                            node.item.file
                        )
                    }
                    val folderEntries = (node as? TreeNode.Folder)?.entries
                    if (layout == LibraryLayout.LIST) {
                        MediaRow(
                            title = title,
                            details = details,
                            modifier = modifier,
                            trailing = if (folderEntries != null) {
                                {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }
                            } else {
                                null
                            }
                        ) {
                            VideoThumbnail(
                                file,
                                playIconSize = 24.dp,
                                Modifier.width(128.dp),
                                folderEntries
                            )
                        }
                    } else {
                        val compact = layout == LibraryLayout.SMALL_GRID
                        MediaTile(
                            title = title,
                            details = details,
                            compact = compact,
                            modifier = modifier
                        ) {
                            VideoThumbnail(
                                file,
                                playIconSize = if (compact) 32.dp else 44.dp,
                                Modifier.fillMaxWidth(),
                                folderEntries
                            )
                        }
                    }
                }
            }
        }
    }
    deleting?.let { node ->
        ConfirmDeleteDialog(node.name, { container.library.delete(*node.dirs.toTypedArray()) }, {
            deleting =
                null
        })
    }
}

/** A video's first frame; for a folder ([folderEntries] set), a folder badge instead of a play button. */
@Composable
private fun VideoThumbnail(
    file: File,
    playIconSize: Dp,
    modifier: Modifier = Modifier,
    folderEntries: Int? = null
) {
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
            if (folderEntries == null) {
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
            } else {
                FolderBadge(folderEntries, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
    }
}

/** Marks a folder's picture with how many entries it holds. */
@Composable
private fun FolderBadge(entries: Int, modifier: Modifier = Modifier) {
    Badge(
        containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Icon(Icons.Filled.Folder, null, Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
            Text("$entries")
        }
    }
}

@Composable
private fun MediaRow(
    title: String,
    details: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
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
        trailingContent = trailing,
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
