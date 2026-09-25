package io.github.eggplants.godlo.ui.download

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.AppSettings
import io.github.eggplants.godlo.core.Engine
import io.github.eggplants.godlo.core.MediaKind
import io.github.eggplants.godlo.core.Storage
import io.github.eggplants.godlo.download.DownloadTarget
import io.github.eggplants.godlo.download.DownloadTask
import io.github.eggplants.godlo.download.TaskState
import java.io.File
import kotlinx.coroutines.launch

/** yt-dlp's height limits, with their labels. */
@Composable
fun videoQualities(): List<Pair<String, String>> = listOf(
    "best" to stringResource(R.string.quality_best),
    "2160" to "4K",
    "1440" to "1440p",
    "1080" to "1080p",
    "720" to "720p",
    "480" to "480p",
    "360" to "360p"
)
val AUDIO_FORMATS = listOf("mp3" to "MP3", "m4a" to "M4A", "opus" to "Opus", "flac" to "FLAC")

fun MediaKind.icon(): ImageVector = when (this) {
    MediaKind.IMAGE -> Icons.Filled.Image
    MediaKind.AUDIO -> Icons.Filled.Audiotrack
    MediaKind.VIDEO -> Icons.Filled.Movie
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(container: AppContainer, onOpen: (DownloadTarget) -> Unit) {
    val vm: DownloadViewModel = viewModel { DownloadViewModel(container) }
    val form by vm.form.collectAsStateWithLifecycle()
    val tasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val library by container.library.library.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(Storage.hasAccess(context)) }
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        hasAccess = Storage.hasAccess(context)
        onPauseOrDispose { }
    }
    val storagePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasAccess = Storage.hasAccess(context)
        }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Godlo") },
                actions = {
                    if (tasks.any { it.finished }) {
                        IconButton(onClick = { confirmingClear = true }) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.clear_history)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasAccess) {
                item {
                    StorageAccessCard(onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.startActivity(Storage.accessSettingsIntent(context))
                        } else {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    })
                }
            }
            item {
                DownloadFormCard(
                    form = form,
                    roots = settings.roots,
                    vm = vm,
                    onSubmit = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        vm.submit()
                    }
                )
            }
            if (tasks.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.queue_and_history),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }
            }
            items(tasks, key = { it.id }) { task ->
                val target =
                    remember(task.state, task.files, library) { DownloadTarget.of(task, library) }
                TaskCard(
                    task = task,
                    target = target,
                    onOpen = { target?.let(onOpen) },
                    onCancel = { vm.cancel(task.id) },
                    onRetry = { vm.retry(task.id) },
                    onRemove = { vm.remove(task.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    if (confirmingClear) {
        val finished = tasks.count { it.finished }
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = {
                Text(pluralStringResource(R.plurals.clear_history_body, finished, finished))
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearFinished()
                    confirmingClear = false
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmingClear = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun StorageAccessCard(onGrant: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.storage_access_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                stringResource(R.string.storage_access_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            FilledTonalButton(onClick = onGrant, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.grant))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadFormCard(
    form: DownloadForm,
    roots: Map<Engine, String>,
    vm: DownloadViewModel,
    onSubmit: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = form.url,
                onValueChange = vm::setUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL") },
                placeholder = { Text("https://…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                trailingIcon = {
                    if (form.url.isEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val text = clipboard.getClipEntry()?.clipData?.takeIf {
                                    it.itemCount >
                                        0
                                }
                                    ?.getItemAt(0)?.text?.toString()
                                if (text != null) vm.setUrl(text.trim())
                            }
                        }) {
                            Icon(
                                Icons.Filled.ContentPaste,
                                contentDescription = stringResource(R.string.paste)
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            vm.setUrl("")
                        }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { if (form.valid) onSubmit() })
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.type),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(56.dp)
                )
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    val kinds = listOf(MediaKind.VIDEO, MediaKind.AUDIO, MediaKind.IMAGE)
                    kinds.forEachIndexed { index, kind ->
                        SegmentedButton(
                            selected = form.kind == kind,
                            onClick = { vm.setKind(kind) },
                            enabled = form.allows(kind),
                            shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                            // As tall as the tool chips beside it.
                            modifier = Modifier.height(FilterChipDefaults.Height),
                            icon = {
                                SegmentedButtonDefaults.Icon(form.kind == kind) {
                                    Icon(kind.icon(), null, Modifier.size(18.dp))
                                }
                            }
                        ) { Text(stringResource(kind.label), maxLines = 1) }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.tool),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(56.dp)
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Engine.entries.forEach { engine ->
                        FilterChip(
                            selected = form.engine == engine,
                            onClick = { vm.setEngine(engine) },
                            enabled = form.allows(engine),
                            label = { Text(engine.id) }
                        )
                    }
                    if (form.detecting) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            if (form.engine == Engine.YTDLP) {
                val options = if (form.kind == MediaKind.AUDIO) AUDIO_FORMATS else videoQualities()
                val selected = if (form.kind ==
                    MediaKind.AUDIO
                ) {
                    form.audioFormat
                } else {
                    form.videoQuality
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (form.kind ==
                            MediaKind.AUDIO
                        ) {
                            stringResource(R.string.format)
                        } else {
                            stringResource(R.string.quality)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(56.dp)
                    )
                    options.forEach { (value, label) ->
                        FilterChip(
                            selected = selected == value,
                            onClick = {
                                if (form.kind ==
                                    MediaKind.AUDIO
                                ) {
                                    vm.setAudioFormat(value)
                                } else {
                                    vm.setVideoQuality(value)
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (form.engine == Engine.YTDLP || form.engine == Engine.GETJMANGA) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                if (form.engine ==
                                    Engine.YTDLP
                                ) {
                                    R.string.whole_playlist
                                } else {
                                    R.string.following_episodes
                                }
                            )
                        )
                    },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(checked = form.playlist, onCheckedChange = vm::setPlaylist)
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    modifier = Modifier.padding(horizontal = 0.dp)
                )
            }
            if (form.engine == Engine.GETJMANGA) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.previous_episodes)) },
                    leadingContent = {
                        Icon(Icons.Filled.SwapVert, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(checked = form.previous, onCheckedChange = vm::setPrevious)
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
            }

            val engine = form.engine
            if (form.site.isNotEmpty() && engine != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Folder,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        File(roots[engine] ?: Storage.defaultRoot(engine), form.site)
                            .absolutePath.removePrefix("/storage/emulated/0/") + "/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis
                    )
                }
            }

            Button(
                onClick = onSubmit,
                enabled = form.valid,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.download))
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: DownloadTask,
    /** Where the finished download opens; null when there is nothing (left) to open. */
    target: DownloadTarget?,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            Modifier.padding(
                start = 16.dp,
                top = 12.dp,
                end = 8.dp,
                bottom = 12.dp
            ).animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(
                        40.dp
                    ).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        task.kind.icon(),
                        null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title.ifBlank { task.url },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(task.site, task.engine.id, stateLabel(task)).filter {
                            it.isNotBlank()
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (task.state ==
                            TaskState.FAILED
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                when (task.state) {
                    TaskState.QUEUED, TaskState.RUNNING -> IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Stop, stringResource(R.string.cancel))
                    }

                    TaskState.FAILED, TaskState.CANCELLED -> IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.retry))
                    }

                    TaskState.DONE -> if (target != null) {
                        FilledTonalIconButton(onClick = onOpen) {
                            when (target) {
                                is DownloadTarget.Album, is DownloadTarget.ImageFolder ->
                                    Icon(Icons.Outlined.AutoStories, stringResource(R.string.open))

                                is DownloadTarget.Video, is DownloadTarget.Audio ->
                                    Icon(Icons.Filled.PlayArrow, stringResource(R.string.play))
                            }
                        }
                    }
                }
                if (task.finished) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.remove_from_history))
                    }
                }
            }
            if (task.state == TaskState.RUNNING) {
                Spacer(Modifier.height(10.dp))
                if (task.progress >= 0f) {
                    LinearProgressIndicator(progress = {
                        task.progress
                    }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 8.dp))
                }
                if (task.detail.isNotBlank()) {
                    Text(
                        task.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (task.state == TaskState.FAILED && task.message.isNotBlank()) {
                Text(
                    task.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp)
                )
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.padding(top = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        task.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    task.files.takeLast(5).forEach {
                        Text(
                            "✓ " + File(it).name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (task.log.isNotEmpty()) {
                        Text(
                            task.log.takeLast(30).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.shapes.small
                                )
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun stateLabel(task: DownloadTask): String = when (task.state) {
    TaskState.QUEUED -> stringResource(R.string.state_queued)

    TaskState.RUNNING -> stringResource(R.string.state_running)

    TaskState.DONE -> if (task.files.size > 1) {
        stringResource(R.string.state_done_count, task.files.size)
    } else {
        stringResource(R.string.state_done)
    }

    TaskState.FAILED -> stringResource(R.string.state_failed)

    TaskState.CANCELLED -> stringResource(R.string.state_cancelled)
}
