package io.github.eggplants.godlo.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DataArray
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoNotDisturb
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.ConfigFile
import io.github.eggplants.godlo.core.ConfigFormat
import io.github.eggplants.godlo.ui.components.ConfirmDeleteDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean

/** Edits [config] as a tree of values or as plain text, and imports or exports it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(container: AppContainer, config: ConfigFile, onBack: () -> Unit) {
    val vm: ConfigEditorViewModel = viewModel(key = config.name) {
        ConfigEditorViewModel(container, config)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val message = state.message
    val messageText = message?.let { stringResource(it.text, it.arg) }
    LaunchedEffect(message) {
        if (messageText != null) {
            vm.messageShown()
            snackbar.showSnackbar(messageText)
        }
    }

    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use {
                        it.readBytes().decodeToString()
                    }
                }
            }.onSuccess(vm::import).onFailure {
                vm.showMessage(EditorMessage(R.string.config_error, it.message ?: ""))
            }
        }
    }
    // Any other type makes the file picker append its extension to the name.
    val mime = if (config.format ==
        ConfigFormat.COOKIES
    ) {
        "text/plain"
    } else {
        "application/octet-stream"
    }
    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mime)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.withContent { text ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(text.toByteArray())
                }
            }
            vm.showMessage(EditorMessage(R.string.config_exported))
        }
    }

    fun leave() {
        if (state.dirty) confirmDiscard = true else onBack()
    }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = { Text(config.fileName) },
                actions = {
                    if (state.busy) CircularProgressIndicator(Modifier.size(24.dp))
                    IconButton(onClick = { vm.save() }, enabled = state.dirty && !state.busy) {
                        Icon(Icons.Filled.Save, stringResource(R.string.save))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.config_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MenuItem(R.string.config_import_file, Icons.Outlined.FileOpen) {
                                menu = false
                                importFile.launch(arrayOf("*/*"))
                            }
                            MenuItem(
                                R.string.config_import_clipboard,
                                Icons.Outlined.ContentPaste
                            ) {
                                menu = false
                                val text = readClipboard(context)
                                if (text == null) {
                                    vm.showMessage(EditorMessage(R.string.config_clipboard_empty))
                                } else {
                                    vm.import(text)
                                }
                            }
                            HorizontalDivider()
                            MenuItem(R.string.config_export_file, Icons.Outlined.SaveAs) {
                                menu = false
                                exportFile.launch(config.fileName)
                            }
                            MenuItem(R.string.config_export_clipboard, Icons.Outlined.ContentCopy) {
                                menu = false
                                vm.withContent { text ->
                                    copyToClipboard(context, config.fileName, text)
                                    // Android 13+ shows its own confirmation.
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        vm.showMessage(EditorMessage(R.string.config_copied))
                                    }
                                }
                            }
                            HorizontalDivider()
                            MenuItem(R.string.config_docs, Icons.AutoMirrored.Outlined.MenuBook) {
                                menu = false
                                uriHandler.openUri(config.docs)
                            }
                            MenuItem(R.string.config_delete, Icons.Outlined.Delete) {
                                menu = false
                                confirmDelete = true
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            val node = state.tree?.at(state.path)
            if (state.mode == EditorMode.VISUAL && node != null && node.valueType.container) {
                var adding by rememberSaveable { mutableStateOf(false) }
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.config_add))
                }
                if (adding) {
                    ValueDialog(
                        title = stringResource(R.string.config_add_title),
                        key = if (node is JsonObject) "" else null,
                        value = JsonPrimitive(""),
                        siblings = (node as? JsonObject)?.keys.orEmpty(),
                        toml = config.format == ConfigFormat.TOML,
                        refusalOf = { name -> config.refusal(state.path + PathStep.Key(name)) },
                        onDismiss = { adding = false },
                        onConfirm = { key, value ->
                            vm.editTree { it.add(state.path, key.orEmpty(), value) }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (config.visual) {
                PrimaryTabRow(selectedTabIndex = state.mode.ordinal) {
                    EditorMode.entries.forEach { mode ->
                        Tab(
                            selected = state.mode == mode,
                            onClick = { vm.setMode(mode) },
                            enabled = !state.busy && !state.loading,
                            text = {
                                Text(
                                    stringResource(
                                        if (mode == EditorMode.VISUAL) {
                                            R.string.config_visual
                                        } else {
                                            R.string.config_text
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
            }
            Text(
                stringResource(config.hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            val tree = state.tree
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.mode == EditorMode.VISUAL && tree != null -> VisualEditor(
                    tree = tree,
                    path = state.path,
                    config = config,
                    onNavigate = vm::navigate,
                    onEdit = vm::editTree
                )

                else -> CodeEditor(
                    text = state.text,
                    onChange = vm::editText,
                    format = config.format,
                    placeholder = stringResource(R.string.config_text_empty)
                )
            }
        }
    }

    state.problem?.let { problem ->
        AlertDialog(
            onDismissRequest = vm::dismissProblem,
            title = { Text(stringResource(R.string.config_invalid_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        problem,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Text(stringResource(R.string.config_invalid_body))
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.save(check = false) }) {
                    Text(stringResource(R.string.config_save_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissProblem) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    state.refused?.let { refused ->
        AlertDialog(
            onDismissRequest = vm::dismissProblem,
            title = { Text(stringResource(R.string.config_refused_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.config_refused_body))
                    refused.forEach { (setting, refusal) ->
                        Column {
                            Text(
                                setting,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                stringResource(refusal.reason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::removeRefusedAndSave) {
                    Text(stringResource(R.string.config_remove_and_save))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissProblem) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (state.conflict) {
        AlertDialog(
            onDismissRequest = vm::dismissProblem,
            title = { Text(stringResource(R.string.config_conflict_title)) },
            text = { Text(stringResource(R.string.config_conflict_body, config.fileName)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        vm.dismissProblem()
                        vm.load()
                    }) { Text(stringResource(R.string.config_reload)) }
                    TextButton(onClick = { vm.save(check = false, overwrite = true) }) {
                        Text(stringResource(R.string.config_overwrite))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissProblem) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.config_discard_title)) },
            text = { Text(stringResource(R.string.config_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.config_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            name = config.fileName,
            onConfirm = vm::delete,
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun MenuItem(text: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(text)) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick
    )
}

/** The objects and arrays in [tree], one level at a time from [path]. */
@Composable
private fun VisualEditor(
    tree: JsonElement,
    path: List<PathStep>,
    config: ConfigFile,
    onNavigate: (List<PathStep>) -> Unit,
    onEdit: ((JsonElement) -> JsonElement) -> Unit
) {
    // Back goes up a level before it leaves the editor.
    BackHandler(enabled = path.isNotEmpty()) { onNavigate(path.dropLast(1)) }
    var editing by remember { mutableStateOf<List<PathStep>?>(null) }
    val node = tree.at(path)
    val entries: List<Pair<PathStep, JsonElement>> = when (node) {
        is JsonObject -> node.map { (key, value) -> PathStep.Key(key) to value }
        is JsonArray -> node.mapIndexed { i, value -> PathStep.Index(i) to value }
        else -> emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onNavigate(emptyList()) }, enabled = path.isNotEmpty()) {
                Text(stringResource(R.string.config_root))
            }
            path.forEachIndexed { i, step ->
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { onNavigate(path.take(i + 1)) },
                    enabled = i < path.lastIndex
                ) { Text(step.label) }
            }
        }
        HorizontalDivider()
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.config_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            // Clear of the add button.
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            items(entries, key = { (step, _) -> step.toString() }) { (step, value) ->
                val here = path + step
                EntryRow(
                    step = step,
                    value = value,
                    last = step is PathStep.Index && step.index == entries.lastIndex,
                    refusal = config.refusal(here),
                    onOpen = {
                        if (value.valueType.container) onNavigate(here) else editing = here
                    },
                    onToggle = { on -> onEdit { it.update(here) { JsonPrimitive(on) } } },
                    onEdit = { editing = here },
                    onMove = { by -> onEdit { it.move(here, by) } },
                    onDelete = { onEdit { it.remove(here) } }
                )
            }
        }
    }

    editing?.let { target ->
        val value = tree.at(target) ?: return@let
        val step = target.last()
        val parent = tree.at(target.dropLast(1)) as? JsonObject
        ValueDialog(
            title = stringResource(R.string.config_edit_title, step.label),
            key = (step as? PathStep.Key)?.name,
            value = value,
            siblings = parent?.keys.orEmpty() - (step as? PathStep.Key)?.name.orEmpty(),
            toml = config.format == ConfigFormat.TOML,
            refusalOf = { name -> config.refusal(target.dropLast(1) + PathStep.Key(name)) },
            onDismiss = { editing = null },
            onConfirm = { key, changed ->
                onEdit { current ->
                    val renamed = if (key != null) current.rename(target, key) else current
                    val at = if (key != null) target.dropLast(1) + PathStep.Key(key) else target
                    renamed.update(at) { changed }
                }
            }
        )
    }
}

private val PathStep.label: String
    get() = when (this) {
        is PathStep.Key -> name
        is PathStep.Index -> "[$index]"
    }

@Composable
private fun EntryRow(
    step: PathStep,
    value: JsonElement,
    last: Boolean,
    refusal: Refusal?,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val type = value.valueType
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(step.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val summary = when (type) {
                ValueType.OBJECT -> pluralStringResource(
                    R.plurals.entries,
                    (value as JsonObject).size,
                    value.size
                )

                ValueType.ARRAY -> pluralStringResource(
                    R.plurals.entries,
                    (value as JsonArray).size,
                    value.size
                )

                ValueType.BOOLEAN -> null

                ValueType.NULL -> "null"

                else -> value.editText.ifEmpty { "\"\"" }
            }
            Column {
                if (summary != null) {
                    Text(
                        summary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = if (type.container) null else FontFamily.Monospace
                    )
                }
                // Only from a file written elsewhere: import and the editor keep these out.
                if (refusal != null) {
                    Text(
                        stringResource(
                            R.string.config_entry_refused,
                            stringResource(refusal.reason)
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        leadingContent = { Icon(type.icon, stringResource(type.label)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (type == ValueType.BOOLEAN) {
                    Switch(checked = (value as JsonPrimitive).boolean, onCheckedChange = onToggle)
                }
                if (type.container) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.config_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.config_edit)) },
                            onClick = {
                                menu = false
                                onEdit()
                            }
                        )
                        if (step is PathStep.Index) {
                            if (step.index > 0) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.config_move_up)) },
                                    onClick = {
                                        menu = false
                                        onMove(-1)
                                    }
                                )
                            }
                            if (!last) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.config_move_down)) },
                                    onClick = {
                                        menu = false
                                        onMove(1)
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onOpen)
    )
}

private val ValueType.icon: ImageVector
    get() = when (this) {
        ValueType.STRING -> Icons.Outlined.Abc
        ValueType.NUMBER -> Icons.Outlined.Numbers
        ValueType.BOOLEAN -> Icons.Outlined.ToggleOn
        ValueType.OBJECT -> Icons.Outlined.DataObject
        ValueType.ARRAY -> Icons.Outlined.DataArray
        ValueType.NULL -> Icons.Outlined.DoNotDisturb
    }

private val ValueType.label: Int
    get() = when (this) {
        ValueType.STRING -> R.string.config_type_string
        ValueType.NUMBER -> R.string.config_type_number
        ValueType.BOOLEAN -> R.string.config_type_boolean
        ValueType.OBJECT -> R.string.config_type_object
        ValueType.ARRAY -> R.string.config_type_array
        ValueType.NULL -> R.string.config_type_null
    }

/**
 * Asks for a value, and for its key when [key] is not null (an object's entry).
 *
 * A container keeps what it holds unless its type is changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValueDialog(
    title: String,
    key: String?,
    value: JsonElement,
    siblings: Set<String>,
    toml: Boolean,
    refusalOf: (String) -> Refusal?,
    onDismiss: () -> Unit,
    onConfirm: (String?, JsonElement) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(key.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(value.valueType) }
    var text by rememberSaveable { mutableStateOf(value.editText) }
    val nameError = when {
        key == null -> null

        name.isEmpty() -> stringResource(R.string.config_key_empty)

        name in siblings -> stringResource(R.string.config_key_exists, name)

        else -> refusalOf(name)?.let {
            stringResource(R.string.config_key_refused, name, stringResource(it.reason))
        }
    }
    val result = if (type == value.valueType && type.container) {
        value
    } else {
        scalarOf(type, text)
    }
    // TOML has no null.
    val types = ValueType.entries.filter { !toml || it != ValueType.NULL }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (key != null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.config_key)) },
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(stringResource(option.label)) },
                            leadingIcon = { Icon(option.icon, null, Modifier.size(18.dp)) }
                        )
                    }
                }
                when (type) {
                    ValueType.STRING, ValueType.NUMBER -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.config_value)) },
                        isError = result == null,
                        singleLine = type == ValueType.NUMBER,
                        maxLines = 8,
                        keyboardOptions = if (type == ValueType.NUMBER) {
                            KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        } else {
                            KeyboardOptions(autoCorrectEnabled = false)
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    ValueType.BOOLEAN -> Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.config_value), Modifier.weight(1f))
                        Switch(
                            checked = text == "true",
                            onCheckedChange = { text = it.toString() }
                        )
                    }

                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameError == null && result != null,
                onClick = {
                    onConfirm(if (key != null) name else null, result!!)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    // Accounts and cookies: kept out of the clipboard preview and keyboard suggestions.
    clip.description.extras = PersistableBundle().apply {
        // ClipDescription.EXTRA_IS_SENSITIVE, which older versions simply ignore.
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
