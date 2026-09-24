package io.github.eggplants.godlo.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.core.AppSettings
import io.github.eggplants.godlo.core.ReadingDirection
import io.github.eggplants.godlo.core.SpreadMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

private val ReaderBackground = Color(0xFF0B0B0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    container: AppContainer,
    dir: String,
    onBack: () -> Unit,
    onOpenAlbum: (File) -> Unit
) {
    val context = LocalContext.current
    val album = remember(dir) { File(dir) }
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)
    val chapter by produceState<Chapter?>(null, album) {
        value = withContext(Dispatchers.IO) { ReaderModel.load(album) }
    }
    var showUi by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    // The page (not the spread) being read: spreads regroup when the screen rotates.
    var page by rememberSaveable(dir) { mutableIntStateOf(ReaderModel.lastPage(context, album)) }
    val scope = rememberCoroutineScope()

    ImmersiveMode(visible = showUi)
    BackHandler(showOptions) { showOptions = false }

    Box(Modifier.fillMaxSize().background(ReaderBackground)) {
        val loaded = chapter
        val prefs = settings
        if (loaded == null || prefs == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else if (loaded.pages.isEmpty()) {
            Text("画像がありません", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val landscape = maxWidth > maxHeight
                val spread = when (prefs.spreadMode) {
                    SpreadMode.SINGLE -> false
                    SpreadMode.SPREAD -> true
                    SpreadMode.AUTO -> landscape
                }
                if (prefs.readingDirection == ReadingDirection.VERTICAL) {
                    VerticalReader(loaded, page, onPage = { page = it }, onToggleUi = {
                        showUi =
                            !showUi
                    })
                } else {
                    PagedReader(
                        chapter = loaded,
                        spreads = remember(loaded, spread, prefs.coverAlone) {
                            ReaderModel.spreads(loaded.pages, spread, prefs.coverAlone)
                        },
                        rtl = prefs.readingDirection == ReadingDirection.RTL,
                        page = page,
                        onPage = { page = it },
                        onToggleUi = { showUi = !showUi },
                        onOpenAlbum = onOpenAlbum
                    )
                }
            }
            LaunchedEffect(page) { ReaderModel.saveLastPage(context, album, page) }
        }

        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る") }
                    Column(Modifier.weight(1f)) {
                        Text(
                            album.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            album.parentFile?.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        showOptions = true
                    }) { Icon(Icons.Outlined.AutoStories, "表示設定") }
                }
            }
        }

        val current = chapter
        val currentSettings = settings
        AnimatedVisibility(
            visible = showUi && current != null && current.pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (current != null && currentSettings != null) {
                ReaderBottomBar(
                    chapter = current,
                    page = page,
                    rtl = currentSettings.readingDirection == ReadingDirection.RTL,
                    onSeek = { page = it },
                    onOpenAlbum = onOpenAlbum
                )
            }
        }
    }

    val prefs = settings
    if (showOptions && prefs != null) {
        ModalBottomSheet(onDismissRequest = { showOptions = false }) {
            ReaderOptions(prefs, onChange = { transform ->
                scope.launch { container.settings.update(transform) }
            })
        }
    }
}

@Composable
private fun PagedReader(
    chapter: Chapter,
    spreads: List<List<Int>>,
    rtl: Boolean,
    page: Int,
    onPage: (Int) -> Unit,
    onToggleUi: () -> Unit,
    onOpenAlbum: (File) -> Unit
) {
    val scope = rememberCoroutineScope()
    fun spreadOf(page: Int) = spreads.indexOfFirst { page in it }.coerceAtLeast(0)

    // One extra page after the last spread, offering the next chapter.
    val pager = rememberPagerState(initialPage = spreadOf(page)) { spreads.size + 1 }
    LaunchedEffect(spreads) { pager.scrollToPage(spreadOf(page)) }
    LaunchedEffect(page) {
        val target = spreadOf(page)
        if (pager.currentPage < spreads.size &&
            target != pager.currentPage
        ) {
            pager.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pager, spreads) {
        snapshotFlow { pager.currentPage }.collect { current ->
            spreads.getOrNull(current)?.let { onPage(it.first()) }
        }
    }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val tap: (Offset) -> Unit = { offset ->
            val step = when {
                offset.x < widthPx * 0.3f -> if (rtl) 1 else -1
                offset.x > widthPx * 0.7f -> if (rtl) -1 else 1
                else -> 0
            }
            if (step == 0) {
                onToggleUi()
            } else {
                scope.launch {
                    pager.animateScrollToPage(
                        (pager.currentPage + step).coerceIn(
                            0,
                            pager.pageCount - 1
                        )
                    )
                }
            }
        }
        HorizontalPager(
            state = pager,
            reverseLayout = rtl,
            beyondViewportPageCount = 1,
            key = { index -> spreads.getOrNull(index)?.first() ?: -1 },
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val indices = spreads.getOrNull(index)
            if (indices == null) {
                ChapterEnd(
                    chapter,
                    onOpenAlbum,
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = tap)
                    }
                )
            } else if (indices.size == 1) {
                val zoom =
                    rememberZoomableImageState(
                        rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 5f))
                    )
                ZoomableAsyncImage(
                    model = chapter.pages[indices[0]].file,
                    contentDescription = null,
                    state = zoom,
                    modifier = Modifier.fillMaxSize(),
                    onClick = tap
                )
            } else {
                SpreadPage(chapter.pages[indices[0]], chapter.pages[indices[1]], rtl, tap)
            }
        }
    }
}

/** Two pages side by side, meeting at the gutter, zoomed together. */
@Composable
private fun SpreadPage(first: Page, second: Page, rtl: Boolean, onClick: (Offset) -> Unit) {
    val (left, right) = if (rtl) second to first else first to second
    val zoom = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f))
    Box(
        Modifier.fillMaxSize().zoomable(zoom, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Lay out left-to-right whatever the reading direction: the pages are already swapped.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = left.file,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                AsyncImage(
                    model = right.file,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun ChapterEnd(
    chapter: Chapter,
    onOpenAlbum: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("おわり", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        chapter.next?.let { next ->
            FilledTonalButton(onClick = { onOpenAlbum(next) }) {
                Icon(Icons.Filled.SkipNext, null)
                Spacer(Modifier.width(8.dp))
                Text("次: ${next.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        chapter.previous?.let { previous ->
            OutlinedButton(onClick = { onOpenAlbum(previous) }) {
                Icon(Icons.Filled.SkipPrevious, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    "前: ${previous.name}",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VerticalReader(
    chapter: Chapter,
    page: Int,
    onPage: (Int) -> Unit,
    onToggleUi: () -> Unit
) {
    val list =
        rememberLazyListState(
            initialFirstVisibleItemIndex = page.coerceIn(0, chapter.pages.lastIndex)
        )
    LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex }.collect(onPage) }
    LaunchedEffect(page) {
        if (page != list.firstVisibleItemIndex &&
            page in chapter.pages.indices
        ) {
            list.scrollToItem(page)
        }
    }
    LazyColumn(
        state = list,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = onToggleUi
            )
    ) {
        itemsIndexed(chapter.pages, key = { _, it -> it.file.absolutePath }) { _, item ->
            AsyncImage(
                model = item.file,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().aspectRatio(item.aspect)
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    chapter: Chapter,
    page: Int,
    rtl: Boolean,
    onSeek: (Int) -> Unit,
    onOpenAlbum: (File) -> Unit
) {
    val last = chapter.pages.lastIndex
    var dragging by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(page.toFloat()) }
    if (!dragging) position = page.toFloat()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(
                horizontal = 12.dp,
                vertical = 8.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val previous = if (rtl) chapter.next else chapter.previous
                val next = if (rtl) chapter.previous else chapter.next
                IconButton(onClick = { previous?.let(onOpenAlbum) }, enabled = previous != null) {
                    Icon(Icons.Filled.SkipPrevious, if (rtl) "次の話" else "前の話")
                }
                // The slider runs the way the pages turn.
                CompositionLocalProvider(
                    LocalLayoutDirection provides
                        if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Slider(
                        value = position,
                        onValueChange = {
                            dragging = true
                            position = it
                        },
                        onValueChangeFinished = {
                            dragging = false
                            onSeek(position.toInt())
                        },
                        valueRange = 0f..last.coerceAtLeast(1).toFloat(),
                        steps = (last - 1).coerceAtLeast(0),
                        enabled = last > 0,
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(onClick = { next?.let(onOpenAlbum) }, enabled = next != null) {
                    Icon(Icons.Filled.SkipNext, if (rtl) "前の話" else "次の話")
                }
            }
            Text(
                "${position.toInt() + 1} / ${last + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ReaderOptions(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("読み方", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = ReadingDirection.entries
            options.forEachIndexed { index, direction ->
                SegmentedButton(
                    selected = settings.readingDirection == direction,
                    onClick = { onChange { it.copy(readingDirection = direction) } },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) { Text(direction.label, maxLines = 1) }
            }
        }
        Text("ページ", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = SpreadMode.entries
            options.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.spreadMode == mode,
                    onClick = { onChange { it.copy(spreadMode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) { Text(mode.label.substringBefore(" "), maxLines = 1) }
            }
        }
        ListItem(
            headlineContent = { Text("表紙を単独で表示") },
            supportingContent = { Text("見開きで 1 ページ目だけを単独にして、左右の組み合わせをずらします") },
            trailingContent = {
                Switch(checked = settings.coverAlone, onCheckedChange = { value ->
                    onChange { it.copy(coverAlone = value) }
                })
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

/** Hides the system bars while reading; they come back with the reader's own bars. */
@Composable
private fun ImmersiveMode(visible: Boolean) {
    val view = LocalView.current
    val controller = remember(view) {
        (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
    }
    LaunchedEffect(visible) {
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller?.show(
                WindowInsetsCompat.Type.systemBars()
            )
        } else {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}
