package io.github.eggplants.godlo.ui.settings

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.core.AppSettings
import io.github.eggplants.godlo.core.ReadingDirection
import io.github.eggplants.godlo.core.SpreadMode
import io.github.eggplants.godlo.core.Storage
import io.github.eggplants.godlo.core.ThemeMode
import io.github.eggplants.godlo.ui.download.AUDIO_FORMATS
import io.github.eggplants.godlo.ui.download.VIDEO_QUALITIES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { container.settings.update(transform) }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var editingRoot by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text("設定") }, scrollBehavior = scrollBehavior) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Section("保存先", Icons.Outlined.Folder) {
                SettingItem(
                    title = "保存フォルダ",
                    summary = settings.root + "/{image,audio,video}/{サイト}/",
                    onClick = { editingRoot = true }
                )
                SettingItem(
                    title = "ツールの設定ファイル",
                    summary = "${settings.root}/.config/ に gallery-dl.conf・getjmanga.toml・" +
                        "cookies.txt (Netscape 形式) を置くと読み込みます"
                )
            }

            Section("ダウンロード", Icons.Outlined.HighQuality) {
                ChoiceItem("動画の画質", VIDEO_QUALITIES, settings.videoQuality) { v ->
                    update { it.copy(videoQuality = v) }
                }
                ChoiceItem("音楽の形式", AUDIO_FORMATS, settings.audioFormat) { v ->
                    update { it.copy(audioFormat = v) }
                }
                ChoiceItem(
                    "漫画ページの形式 (getjmanga)",
                    listOf("jpg" to "JPEG", "png" to "PNG", "webp" to "WebP"),
                    settings.imageFormat
                ) { v -> update { it.copy(imageFormat = v) } }
                SwitchItem("CBZ も作成 (getjmanga)", "各話を _cbz/ に .cbz でもまとめます", settings.cbz) { v ->
                    update { it.copy(cbz = v) }
                }
            }

            Section("ビューア", Icons.Outlined.AutoStories) {
                ChoiceItem(
                    "読む方向",
                    ReadingDirection.entries.map {
                        it.name to it.label
                    },
                    settings.readingDirection.name
                ) { v ->
                    update { it.copy(readingDirection = ReadingDirection.valueOf(v)) }
                }
                ChoiceItem(
                    "見開き",
                    SpreadMode.entries.map {
                        it.name to it.label
                    },
                    settings.spreadMode.name
                ) { v ->
                    update { it.copy(spreadMode = SpreadMode.valueOf(v)) }
                }
                SwitchItem("表紙を単独で表示", "見開きで 1 ページ目だけを単独にします", settings.coverAlone) { v ->
                    update { it.copy(coverAlone = v) }
                }
            }

            Section("外観", Icons.Outlined.Palette) {
                ChoiceItem(
                    "テーマ",
                    ThemeMode.entries.map {
                        it.name to it.label
                    },
                    settings.themeMode.name
                ) { v ->
                    update { it.copy(themeMode = ThemeMode.valueOf(v)) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchItem(
                        "ダイナミックカラー",
                        "壁紙の色を使います (オフで #F5F6F6 のテーマ)",
                        settings.dynamicColor
                    ) { v ->
                        update { it.copy(dynamicColor = v) }
                    }
                }
            }

            ToolsSection(container)
        }
    }

    if (editingRoot) {
        RootDialog(
            current = settings.root,
            onDismiss = { editingRoot = false },
            onSave = { root -> update { it.copy(root = root.trimEnd('/')) } }
        )
    }
}

@Composable
private fun ToolsSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var updating by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val versions by produceState<Map<String, String>?>(null, refresh) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    container.python.versions()
                }.getOrElse { mapOf("error" to (it.message ?: "")) }
            }
    }
    Section("ツール", Icons.Outlined.Build) {
        val current = versions
        if (current == null) {
            ListItem(
                headlineContent = { Text("Python を起動中…") },
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
                    result = "同梱版に戻しました。アプリを再起動すると反映されます。"
                }
            ) { Text("同梱版に戻す") }
            FilledTonalButton(
                enabled = !updating,
                onClick = {
                    updating = true
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) { container.python.updateTools() }
                        updating = false
                        result = if (outcome.status == "ok") {
                            "更新しました。アプリを再起動すると反映されます。\n\n" + outcome.message.takeLast(600)
                        } else {
                            "更新に失敗しました。\n\n" + outcome.message.takeLast(1200)
                        }
                        refresh++
                    }
                }
            ) { Text("最新版に更新") }
        }
    }
    result?.let { text ->
        AlertDialog(
            onDismissRequest = { result = null },
            confirmButton = { TextButton(onClick = { result = null }) { Text("OK") } },
            title = { Text("ツールの更新") },
            text = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        )
    }
}

@Composable
private fun RootDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存フォルダ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = value, onValueChange = {
                    value = it
                }, singleLine = false, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { value = Storage.defaultRoot }) { Text("既定に戻す") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.startsWith("/"),
                onClick = {
                    onSave(value)
                    onDismiss()
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
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
            confirmButton = { TextButton(onClick = { open = false }) { Text("閉じる") } }
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
