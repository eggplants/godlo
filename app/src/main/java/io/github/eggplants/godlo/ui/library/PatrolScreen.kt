package io.github.eggplants.godlo.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.ui.components.EmptyState

/** The getjmanga works the patrol goes back to for new episodes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolScreen(container: AppContainer, onBack: () -> Unit, onStarted: () -> Unit) {
    val works by container.patrol.works.collectAsStateWithLifecycle()
    val tasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // The file may have been edited by hand, or on a computer, since it was last read.
    LaunchedEffect(Unit) { container.patrol.refresh() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.patrol_works)) },
                actions = {
                    IconButton(
                        onClick = {
                            container.patrol.start()
                            onStarted()
                        },
                        enabled = works.isNotEmpty() && tasks.none { it.patrol && !it.finished }
                    ) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.patrol_run)) }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        if (works.isEmpty()) {
            EmptyState(
                ImageVector.vectorResource(R.drawable.ic_patrol),
                stringResource(R.string.patrol_empty_title),
                stringResource(R.string.patrol_empty_body),
                Modifier.padding(padding)
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 16.dp)
        ) {
            items(works, key = { it.url }) { work ->
                ListItem(
                    headlineContent = {
                        Text(
                            work.title.ifBlank { work.url },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    // Where the next patrol picks up: the first episode still locked, or the last one read.
                    supportingContent = {
                        Text(work.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        IconButton(onClick = { container.patrol.forget(work.url) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.patrol_forget))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
