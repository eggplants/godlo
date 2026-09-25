package io.github.eggplants.godlo.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.MainActivity
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.AppLanguage
import io.github.eggplants.godlo.core.AppSettings
import io.github.eggplants.godlo.core.Engine
import io.github.eggplants.godlo.core.ReadingDirection
import io.github.eggplants.godlo.core.SpreadMode
import io.github.eggplants.godlo.core.Storage
import io.github.eggplants.godlo.core.ThemeMode
import io.github.eggplants.godlo.ui.download.AUDIO_FORMATS
import io.github.eggplants.godlo.ui.download.videoQualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenAndroidLicenses: () -> Unit,
    onOpenPythonLicenses: () -> Unit
) {
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { container.settings.update(transform) }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var editingRoot by remember { mutableStateOf<Engine?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(title = {
                Text(stringResource(R.string.nav_settings))
            }, scrollBehavior = scrollBehavior)
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Section(stringResource(R.string.settings_save_location), Icons.Outlined.Folder) {
                for (engine in Engine.entries) {
                    SettingItem(
                        title = engine.id,
                        summary = stringResource(
                            R.string.settings_root_summary,
                            settings.root(engine)
                        ),
                        onClick = { editingRoot = engine }
                    )
                }
                SettingItem(
                    title = stringResource(R.string.settings_config_title),
                    summary = stringResource(R.string.settings_config_body, Storage.configDir.path)
                )
            }

            Section(stringResource(R.string.settings_downloads), Icons.Outlined.HighQuality) {
                ChoiceItem(
                    stringResource(R.string.settings_video_quality),
                    videoQualities(),
                    settings.videoQuality
                ) { v ->
                    update { it.copy(videoQuality = v) }
                }
                ChoiceItem(
                    stringResource(R.string.settings_audio_format),
                    AUDIO_FORMATS,
                    settings.audioFormat
                ) { v ->
                    update { it.copy(audioFormat = v) }
                }
                ChoiceItem(
                    stringResource(R.string.settings_manga_format),
                    listOf("jpg" to "JPEG", "png" to "PNG", "webp" to "WebP"),
                    settings.imageFormat
                ) { v -> update { it.copy(imageFormat = v) } }
                SwitchItem(
                    stringResource(R.string.settings_cbz),
                    stringResource(R.string.settings_cbz_desc),
                    settings.cbz
                ) { v ->
                    update { it.copy(cbz = v) }
                }
            }

            Section(stringResource(R.string.settings_viewer), Icons.Outlined.AutoStories) {
                ChoiceItem(
                    stringResource(R.string.reading_direction),
                    ReadingDirection.entries.map { it.name to stringResource(it.label) },
                    settings.readingDirection.name
                ) { v ->
                    update { it.copy(readingDirection = ReadingDirection.valueOf(v)) }
                }
                ChoiceItem(
                    stringResource(R.string.settings_spread),
                    SpreadMode.entries.map { it.name to stringResource(it.label) },
                    settings.spreadMode.name
                ) { v ->
                    update { it.copy(spreadMode = SpreadMode.valueOf(v)) }
                }
                SwitchItem(
                    stringResource(R.string.cover_alone),
                    stringResource(R.string.cover_alone_desc),
                    settings.coverAlone
                ) { v ->
                    update { it.copy(coverAlone = v) }
                }
            }

            Section(stringResource(R.string.settings_appearance), Icons.Outlined.Palette) {
                ChoiceItem(
                    stringResource(R.string.settings_language),
                    AppLanguage.options.map { (tag, name) ->
                        tag to (name ?: stringResource(R.string.settings_language_system))
                    },
                    AppLanguage.current()
                ) { tag -> AppLanguage.set(tag) }
                ChoiceItem(
                    stringResource(R.string.settings_theme),
                    ThemeMode.entries.map { it.name to stringResource(it.label) },
                    settings.themeMode.name
                ) { v ->
                    update { it.copy(themeMode = ThemeMode.valueOf(v)) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchItem(
                        stringResource(R.string.settings_dynamic_color),
                        stringResource(R.string.settings_dynamic_color_desc),
                        settings.dynamicColor
                    ) { v ->
                        update { it.copy(dynamicColor = v) }
                    }
                }
            }

            ToolsSection(container)

            AboutSection(onOpenAndroidLicenses, onOpenPythonLicenses)
        }
    }

    editingRoot?.let { engine ->
        RootDialog(
            engine = engine,
            current = settings.root(engine),
            onDismiss = { editingRoot = null },
            onSave = { root ->
                update { it.copy(roots = it.roots + (engine to root.trimEnd('/'))) }
            }
        )
    }
}

@Composable
private fun ToolsSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var updating by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ToolsResult?>(null) }
    val versions by produceState<Map<String, String>?>(null, refresh) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    container.python.versions()
                }.getOrElse { mapOf("error" to (it.message ?: "")) }
            }
    }
    // Read here: the buttons below set them from outside composition.
    val resetDone = stringResource(R.string.tools_reset_done)
    val updated = stringResource(R.string.tools_updated)
    val updateFailed = stringResource(R.string.tools_update_failed)
    val restartLater = stringResource(R.string.tools_restart_after_downloads)

    // Restarting would cut a running download short, and nothing resumes the queue on launch.
    fun applied(text: String) = if (container.downloads.hasPending) {
        ToolsResult(text + "\n\n" + restartLater, restart = false)
    } else {
        ToolsResult(text, restart = true)
    }
    Section(stringResource(R.string.settings_tools), Icons.Outlined.Build) {
        val current = versions
        if (current == null) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.tools_starting_python)) },
                leadingContent = { CircularProgressIndicator(Modifier.size(24.dp)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        } else {
            current.forEach { (name, version) ->
                SettingItem(title = name, summary = version)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (updating) CircularProgressIndicator(Modifier.size(24.dp))
            OutlinedButton(
                enabled = !updating,
                onClick = {
                    container.python.resetTools()
                    result = applied(resetDone)
                }
            ) { Text(stringResource(R.string.tools_reset)) }
            FilledTonalButton(
                enabled = !updating,
                onClick = {
                    updating = true
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) { container.python.updateTools() }
                        updating = false
                        result = if (outcome.status == "ok") {
                            applied(updated + "\n\n" + outcome.message.takeLast(600))
                        } else {
                            ToolsResult(
                                updateFailed + "\n\n" + outcome.message.takeLast(1200),
                                restart = false
                            )
                        }
                        refresh++
                    }
                }
            ) { Text(stringResource(R.string.tools_update)) }
        }
    }
    result?.let { shown ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { result = null },
            confirmButton = {
                if (shown.restart) {
                    TextButton(onClick = { restartApp(context) }) {
                        Text(stringResource(R.string.tools_restart))
                    }
                } else {
                    TextButton(onClick = { result = null }) { Text(stringResource(R.string.ok)) }
                }
            },
            dismissButton = if (shown.restart) {
                {
                    TextButton(onClick = { result = null }) {
                        Text(stringResource(R.string.tools_later))
                    }
                }
            } else {
                null
            },
            title = { Text(stringResource(R.string.tools_update_title)) },
            text = {
                Text(
                    shown.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        )
    }
}

/** What the tools dialog says, and whether it offers to restart for the change to apply. */
private data class ToolsResult(val text: String, val restart: Boolean)

/** Starts the app over in a new process, which is the only way Python picks up other tools. */
private fun restartApp(context: Context) {
    val intent = Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java))
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

@Composable
private fun RootDialog(
    engine: Engine,
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.root_dialog_title, engine.id)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = value, onValueChange = {
                    value = it
                }, singleLine = false, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.root_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    value = Storage.defaultRoot(engine)
                }) { Text(stringResource(R.string.reset_default)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.startsWith("/"),
                onClick = {
                    onSave(value)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            )
        ) {
            Column { content() }
        }
    }
}

private const val REPOSITORY = "https://github.com/eggplants/godlo"
private const val SPONSORS = "https://github.com/sponsors/eggplants"

@Composable
private fun AboutSection(onOpenAndroidLicenses: () -> Unit, onOpenPythonLicenses: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }
    Section(stringResource(R.string.settings_about), Icons.Outlined.Info) {
        SettingItem(
            title = stringResource(R.string.about_repository),
            summary = REPOSITORY.removePrefix("https://"),
            onClick = { uriHandler.openUri(REPOSITORY) }
        )
        SettingItem(title = stringResource(R.string.about_version), summary = version)
        SettingItem(
            title = stringResource(R.string.about_donate),
            summary = stringResource(R.string.about_donate_summary),
            onClick = { uriHandler.openUri(SPONSORS) }
        )
        SettingItem(
            title = stringResource(R.string.about_licenses),
            summary = stringResource(R.string.about_licenses_android),
            onClick = onOpenAndroidLicenses
        )
        SettingItem(
            title = stringResource(R.string.about_licenses),
            summary = stringResource(R.string.about_licenses_python),
            onClick = onOpenPythonLicenses
        )
    }
}

@Composable
private fun SettingItem(title: String, summary: String, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickableRow(onClick) else Modifier
    )
}

@Composable
private fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow { onChange(!checked) }
    )
}

@Composable
private fun ChoiceItem(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    SettingItem(
        title = title,
        summary =
            options.firstOrNull { it.first == selected }?.second ?: selected,
        onClick = { open = true }
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEachIndexed { index, (value, label) ->
                        if (index >
                            0
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(
                                    alpha = 0.4f
                                )
                            )
                        }
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                RadioButton(selected = value == selected, onClick = null)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickableRow {
                                onSelect(value)
                                open = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
