package io.github.eggplants.godlo.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.core.MediaKind
import io.github.eggplants.godlo.library.Album
import io.github.eggplants.godlo.ui.components.ConfirmDeleteDialog
import io.github.eggplants.godlo.ui.components.EmptyState
import io.github.eggplants.godlo.ui.components.SiteFilterRow
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImageLibraryScreen(container: AppContainer, onOpen: (File) -> Unit) {
    val library by container.library.library.collectAsStateWithLifecycle()
    var site by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Album?>(null) }
    val albums = library.albums.filter { album ->
        (site == null || album.site == site) &&
            (
                query.isBlank() || album.title.contains(query, true) ||
                    album.group.contains(query, true)
                )
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("画像") },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                    }) { Icon(Icons.Outlined.Search, "検索") }
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (searching) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text("タイトルで絞り込み") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SiteFilterRow(
                        sites = library.sites(MediaKind.IMAGE),
                        selected = site,
                        onSelect = { site = it },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                if (albums.isEmpty() && !library.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            Icons.Outlined.Image,
                            "画像はまだありません",
                            "gallery-dl や getjmanga で保存した画像・漫画がここに並びます。"
                        )
                    }
                }
                items(albums, key = { it.dir.absolutePath }) { album ->
                    AlbumCard(
                        album = album,
                        modifier = Modifier
                            .animateItem()
                            .combinedClickable(onClick = { onOpen(album.dir) }, onLongClick = {
                                deleting =
                                    album
                            })
                    )
                }
            }
        }
    }
    deleting?.let { album ->
        ConfirmDeleteDialog(
            name = album.title,
            onConfirm = { container.library.delete(album.dir) },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun AlbumCard(album: Album, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = album.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CardDefaults.shape)
                )
                Badge(
                    containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                ) { Text("${album.count}", modifier = Modifier.padding(horizontal = 2.dp)) }
            }
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(
                album.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                album.group.ifBlank { album.site },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
