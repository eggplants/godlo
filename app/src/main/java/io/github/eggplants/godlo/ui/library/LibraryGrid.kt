package io.github.eggplants.godlo.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eggplants.godlo.core.LibraryLayout

/** Space on either side of the grid; list rows bring their own 16dp, lining them up with the title. */
val LibraryLayout.gutter: Dp get() = if (this == LibraryLayout.LIST) 0.dp else 12.dp

/**
 * The grid every library layout is drawn in: one wide column of rows for [LibraryLayout.LIST]
 * (more on a tablet), or cells at least [largeMinSize] / [smallMinSize] wide.
 */
@Composable
fun LibraryGrid(
    layout: LibraryLayout,
    largeMinSize: Dp,
    smallMinSize: Dp,
    content: LazyGridScope.() -> Unit
) {
    val gutter = layout.gutter
    LazyVerticalGrid(
        columns = GridCells.Adaptive(
            when (layout) {
                LibraryLayout.LIST -> 360.dp
                LibraryLayout.LARGE_GRID -> largeMinSize
                LibraryLayout.SMALL_GRID -> smallMinSize
            }
        ),
        contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(
            if (layout ==
                LibraryLayout.SMALL_GRID
            ) {
                6.dp
            } else {
                12.dp
            }
        ),
        verticalArrangement = Arrangement.spacedBy(
            when (layout) {
                LibraryLayout.LIST -> 0.dp
                LibraryLayout.LARGE_GRID -> 16.dp
                LibraryLayout.SMALL_GRID -> 8.dp
            }
        ),
        modifier = Modifier.fillMaxSize(),
        content = content
    )
}
