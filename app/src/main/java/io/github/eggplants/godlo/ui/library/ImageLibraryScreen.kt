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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.AppSettings
import io.github.eggplants.godlo.core.LibraryLayout
import io.github.eggplants.godlo.library.Album
import io.github.eggplants.godlo.library.LibraryTree
import io.github.eggplants.godlo.library.TreeNode
import io.github.eggplants.godlo.library.cover
import io.github.eggplants.godlo.ui.components.ConfirmDeleteDialog
import io.github.eggplants.godlo.ui.components.EmptyState
import io.github.eggplants.godlo.ui.components.LayoutMenuButton
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImageLibraryScreen(container: AppContainer, initialPath: String = "", onOpen: (File) -> Unit) {
    val library by container.library.library.collectAsStateWithLifecycle()
    // The folder being looked at, e.g. "takecomic.jp/メイドインアビス"; empty for the sites.
    var pathKey by rememberSaveable { mutableStateOf(initialPath) }
    val path = pathKey.split("/").filter { it.isNotEmpty() }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TreeNode<Album>?>(null) }
    val searchResults = query.isNotBlank()
    val nodes = if (searchResults) {
        library.albums
            .filter { it.path.any { name -> name.contains(query, ignoreCase = true) } }
            .map { TreeNode.Leaf(it, it.title, it.modified, it.dir) }
    } else {
        LibraryTree.albums.children(library.albums, path)
    }
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val layout = settings.imageLayout
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    fun goUp() {
        pathKey = path.dropLast(1).joinToString("/")
    }
    BackHandler(enabled = path.isNotEmpty() && !searchResults, onBack = ::goUp)
    // A folder whose last album was deleted, here or elsewhere, is gone: step out of it.
    LaunchedEffect(nodes.isEmpty(), library.loading) {
        if (nodes.isEmpty() && !library.loading && path.isNotEmpty() && !searchResults) goUp()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopBar(
                root = stringResource(R.string.nav_images),
                // Search results come from every folder, so they are not shown as inside one.
                path = if (searchResults) emptyList() else path,
                onUp = ::goUp,
                scrollBehavior = scrollBehavior
            ) {
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) query = ""
                }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                LayoutMenuButton(layout) { next ->
                    scope.launch { container.settings.update { it.copy(imageLayout = next) } }
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
                if (searching) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.image_search_hint)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp - layout.gutter, vertical = 4.dp)
                        )
                    }
                }
                if (nodes.isEmpty() && !library.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (searchResults) {
                            EmptyState(
                                Icons.Outlined.SearchOff,
                                stringResource(R.string.not_found_title),
                                stringResource(R.string.not_found_body, query)
                            )
                        } else {
                            EmptyState(
                                Icons.Outlined.Image,
                                stringResource(R.string.images_empty_title),
                                stringResource(R.string.images_empty_body)
                            )
                        }
                    }
                }
                items(nodes, key = { it.key }) { node ->
                    val modifier = Modifier
                        .animateItem()
                        .combinedClickable(
                            onClick = {
                                when (node) {
                                    is TreeNode.Folder -> pathKey = node.path.joinToString("/")
                                    is TreeNode.Leaf -> onOpen(node.item.dir)
                                }
                            },
                            onLongClick = { deleting = node }
                        )
                    // Search results come from anywhere, so they say where they are from.
                    val detail = when (node) {
                        is TreeNode.Folder -> pluralStringResource(
                            R.plurals.entries,
                            node.entries,
                            node.entries
                        )

                        is TreeNode.Leaf -> if (searchResults) {
                            node.item.path.dropLast(1).joinToString(" › ")
                        } else {
                            pluralStringResource(
                                R.plurals.pages,
                                node.item.count,
                                node.item.count
                            )
                        }
                    }
                    if (layout == LibraryLayout.LIST) {
                        NodeRow(node, detail, modifier)
                    } else {
                        NodeCard(
                            node,
                            detail,
                            compact = layout == LibraryLayout.SMALL_GRID,
                            modifier = modifier
                        )
                    }
                }
            }
        }
    }
    deleting?.let { node ->
        ConfirmDeleteDialog(
            name = node.name,
            onConfirm = { container.library.delete(*node.dirs.toTypedArray()) },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun NodeRow(node: TreeNode<Album>, detail: String, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(node.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            AsyncImage(
                model = node.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 56.dp, height = 78.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        },
        trailingContent = {
            if (node is TreeNode.Folder) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
    )
}

/**
 * A cover with a count in its corner: pages for an album, entries (with a folder icon) for a
 * folder. [compact] keeps only a one-line name under it.
 */
@Composable
private fun NodeCard(
    node: TreeNode<Album>,
    detail: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = node.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CardDefaults.shape)
                )
                Badge(
                    containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        when (node) {
                            is TreeNode.Folder -> {
                                Icon(Icons.Filled.Folder, null, Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("${node.entries}")
                            }

                            is TreeNode.Leaf -> Text("${node.item.count}")
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(
                node.name,
                style = with(MaterialTheme.typography) { if (compact) labelMedium else titleSmall },
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
