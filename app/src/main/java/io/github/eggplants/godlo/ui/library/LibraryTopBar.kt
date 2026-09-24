package io.github.eggplants.godlo.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.eggplants.godlo.R

/**
 * A library tab's top bar: the tab's name at the top level; inside a folder, the folder's name
 * over the way back to it ("Images › takecomic.jp"), with an arrow up one level.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTopBar(
    root: String,
    path: List<String>,
    onUp: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable RowScope.() -> Unit
) {
    TopAppBar(
        navigationIcon = {
            if (path.isNotEmpty()) {
                IconButton(onClick = onUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.up_one_level))
                }
            }
        },
        title = {
            if (path.isEmpty()) {
                Text(root)
            } else {
                Column {
                    Text(path.last(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (listOf(root) + path.dropLast(1)).joinToString(" › "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior
    )
}
