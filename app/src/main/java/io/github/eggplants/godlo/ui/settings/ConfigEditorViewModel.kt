package io.github.eggplants.godlo.ui.settings

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.ConfigFile
import io.github.eggplants.godlo.core.ConfigFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

enum class EditorMode { VISUAL, TEXT }

/** A line for the snackbar: [text] with [arg] in it. */
data class EditorMessage(@StringRes val text: Int, val arg: String = "")

data class ConfigEditorState(
    val loading: Boolean = true,
    /** Waiting for Python: checking, converting or writing. */
    val busy: Boolean = false,
    val mode: EditorMode = EditorMode.TEXT,
    /** What the file holds, as last read or written. */
    val saved: String = "",
    /** When the file was last read or written, to tell whether something else wrote it since. */
    val stamp: Long = 0L,
    /** The text editor's content; the source of truth in [EditorMode.TEXT]. */
    val text: String = "",
    /** The visual editor's tree; the source of truth in [EditorMode.VISUAL]. */
    val tree: JsonElement? = null,
    /** The text [tree] was read from: a TOML file keeps its comments by being edited from it. */
    val base: String = "",
    /** [tree] as read from [base], before any edit. */
    val baseTree: JsonElement? = null,
    /** Where the visual editor is in [tree]. */
    val path: List<PathStep> = emptyList(),
    val message: EditorMessage? = null,
    /** What the tool would stumble on in what is being saved, for the user to confirm. */
    val problem: String? = null,
    /** The file was written by something else since it was read here. */
    val conflict: Boolean = false,
    /** Settings Godlo refuses in what is being saved, for the user to have removed. */
    val refused: List<RefusedSetting>? = null
) {
    val dirty: Boolean
        get() = when (mode) {
            EditorMode.TEXT -> text != saved
            EditorMode.VISUAL -> base != saved || tree != baseTree
        }
}

/** Edits one of the tools' config files, as a tree or as text. */
class ConfigEditorViewModel(private val container: AppContainer, val config: ConfigFile) :
    ViewModel() {
    private val _state = MutableStateFlow(ConfigEditorState())
    val state: StateFlow<ConfigEditorState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val file = config.file
        val read = runCatching {
            withContext(Dispatchers.IO) {
                if (file.isFile) file.readText() to file.lastModified() else "" to 0L
            }
        }
        val (text, stamp) = read.getOrElse {
            Log.w("Godlo", "could not read $file", it)
            _state.value = ConfigEditorState(
                loading = false,
                message = EditorMessage(R.string.config_error, it.message ?: "")
            )
            return
        }
        // Reading it again keeps the editor the user picked; the first read opens the tree.
        val keepText = _state.value.run { !loading && mode == EditorMode.TEXT }
        val tree = if (config.visual && !keepText) parse(text) else null
        _state.value = ConfigEditorState(
            loading = false,
            mode = if (tree?.isSuccess == true) EditorMode.VISUAL else EditorMode.TEXT,
            saved = text,
            stamp = stamp,
            text = text,
            tree = tree?.getOrNull(),
            base = text,
            baseTree = tree?.getOrNull(),
            message = tree?.exceptionOrNull()?.let(::cannotShow)
        )
    }

    fun setMode(mode: EditorMode) {
        val current = _state.value
        if (mode == current.mode || current.busy || !config.visual) return
        busy {
            when (mode) {
                EditorMode.TEXT -> {
                    val text = content(current)
                    _state.update {
                        it.copy(mode = mode, text = text, base = text, baseTree = it.tree)
                    }
                }

                EditorMode.VISUAL -> {
                    parse(current.text)
                        .onSuccess { tree ->
                            _state.update {
                                it.copy(
                                    mode = mode,
                                    tree = tree,
                                    base = it.text,
                                    baseTree = tree,
                                    path = tree.validPrefix(it.path)
                                )
                            }
                        }
                        .onFailure { e -> _state.update { it.copy(message = cannotShow(e)) } }
                }
            }
        }
    }

    fun editText(text: String) = _state.update { it.copy(text = text) }

    fun editTree(transform: (JsonElement) -> JsonElement) = _state.update { state ->
        val tree = state.tree ?: return@update state
        runCatching { transform(tree) }
            .map { changed -> state.copy(tree = changed, path = changed.validPrefix(state.path)) }
            .getOrElse {
                state.copy(message = EditorMessage(R.string.config_error, it.message ?: ""))
            }
    }

    fun navigate(path: List<PathStep>) = _state.update { it.copy(path = path) }

    /**
     * Puts [text], from a file or the clipboard, in the editor, leaving out the settings Godlo
     * refuses (see [refusal]); saving is still up to the user.
     */
    fun import(text: String) {
        busy {
            val (stripped, removed) = strip(text)
            val tree = if (_state.value.mode == EditorMode.VISUAL) parse(stripped) else null
            _state.update { state ->
                when {
                    tree == null -> state.copy(text = stripped)

                    tree.isSuccess -> state.copy(
                        tree = tree.getOrNull(),
                        base = stripped,
                        baseTree = tree.getOrNull(),
                        path = emptyList()
                    )

                    else -> state.copy(mode = EditorMode.TEXT, text = stripped)
                }.copy(
                    message = tree?.exceptionOrNull()?.let(::cannotShow)
                        ?: if (removed.isEmpty()) {
                            EditorMessage(R.string.config_imported)
                        } else {
                            EditorMessage(
                                R.string.config_imported_removed,
                                removed.joinToString(", ") { it.setting }
                            )
                        }
                )
            }
        }
    }

    /** Hands what the editor holds, as the file would be written, to [block]. */
    fun withContent(block: suspend (String) -> Unit) = busy { block(content(_state.value)) }

    /**
     * Writes the file.
     *
     * @param check Have the tool read it first, and ask the user when it would stumble.
     * @param overwrite Write it even if something else wrote the file since it was read here.
     */
    fun save(check: Boolean = true, overwrite: Boolean = false) {
        _state.update { it.copy(problem = null, conflict = false, refused = null) }
        busy { saveNow(check, overwrite) }
    }

    /** Takes the settings Godlo refuses out of the editor, then saves. */
    fun removeRefusedAndSave() {
        _state.update { it.copy(refused = null) }
        busy {
            val current = _state.value
            val tree = current.tree
            if (current.mode == EditorMode.VISUAL && tree != null) {
                val cleaned = config.refusedIn(tree).fold(tree) { t, (path, _) -> t.remove(path) }
                _state.update { it.copy(tree = cleaned, path = cleaned.validPrefix(it.path)) }
            } else {
                val (text, _) = strip(current.text)
                _state.update { it.copy(text = text) }
            }
            saveNow(check = true, overwrite = false)
        }
    }

    private suspend fun saveNow(check: Boolean, overwrite: Boolean) {
        val current = _state.value
        val refused = refused(current)
        if (refused.isNotEmpty()) {
            _state.update { it.copy(refused = refused) }
            return
        }
        val text = content(current)
        if (check) {
            val problem = withContext(Dispatchers.IO) {
                runCatching { container.python.checkConfig(config, text) }
                    .getOrElse { it.message ?: it.javaClass.simpleName }
            }
            if (problem.isNotEmpty()) {
                _state.update { it.copy(problem = problem) }
                return
            }
        }
        val file = config.file
        val stamp = withContext(Dispatchers.IO) { if (file.isFile) file.lastModified() else 0L }
        if (!overwrite && stamp != current.stamp) {
            _state.update { it.copy(conflict = true) }
            return
        }
        runCatching {
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                file.writeText(text)
                file.lastModified()
            }
        }.onSuccess { written ->
            _state.update {
                it.copy(
                    saved = text,
                    stamp = written,
                    text = if (it.mode == EditorMode.TEXT) text else it.text,
                    base = if (it.mode == EditorMode.VISUAL) text else it.base,
                    baseTree = if (it.mode == EditorMode.VISUAL) it.tree else it.baseTree,
                    message = EditorMessage(R.string.config_saved)
                )
            }
            changed()
        }.onFailure { e ->
            _state.update {
                it.copy(
                    message = EditorMessage(
                        R.string.config_error,
                        e.message ?: ""
                    )
                )
            }
        }
    }

    fun delete() {
        busy {
            runCatching { withContext(Dispatchers.IO) { config.file.delete() } }
            changed()
            reload()
            _state.update { it.copy(message = EditorMessage(R.string.config_deleted)) }
        }
    }

    fun dismissProblem() = _state.update {
        it.copy(problem = null, conflict = false, refused = null)
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun showMessage(message: EditorMessage) = _state.update { it.copy(message = message) }

    /** The patrol list is read from getjmanga.toml. */
    private fun changed() {
        if (config == ConfigFile.GETJMANGA) container.patrol.refresh()
    }

    /** The settings in what [state] holds that Godlo refuses. */
    private suspend fun refused(state: ConfigEditorState): List<RefusedSetting> {
        val tree = state.tree
        if (state.mode == EditorMode.VISUAL && tree != null) {
            return config.refusedIn(tree).map { (path, refusal) ->
                RefusedSetting(settingName(path), refusal)
            }
        }
        return strip(state.text).second
    }

    /**
     * [text] without the settings Godlo refuses, and those settings. Text that cannot be read
     * comes back as it is: the syntax check before saving points that out.
     */
    private suspend fun strip(text: String): Pair<String, List<RefusedSetting>> =
        withContext(Dispatchers.IO) {
            when (config.format) {
                ConfigFormat.ARGS -> runCatching { container.python.stripYtdlpConfig(text) }
                    .map { stripped ->
                        stripped.text to stripped.removed.map {
                            RefusedSetting(it.setting, Refusal.fromId(it.reason))
                        }
                    }
                    .getOrDefault(text to emptyList())

                ConfigFormat.COOKIES -> text to emptyList()

                ConfigFormat.JSON, ConfigFormat.TOML -> {
                    val tree = parse(text).getOrNull() ?: return@withContext text to emptyList()
                    val found = config.refusedIn(tree)
                    if (found.isEmpty()) return@withContext text to emptyList()
                    val cleaned = found.fold(tree) { t, (path, _) -> t.remove(path) }
                    val written = if (config.format == ConfigFormat.TOML) {
                        container.python.tomlFromJson(text, cleaned.toString())
                    } else {
                        formatConfigJson(cleaned)
                    }
                    written to
                        found.map { (path, refusal) -> RefusedSetting(settingName(path), refusal) }
                }
            }
        }

    /** What [state] would be written as. */
    private suspend fun content(state: ConfigEditorState): String {
        val tree = state.tree
        if (state.mode == EditorMode.TEXT || tree == null) return state.text
        // Unchanged: exactly as read, not as the tree would be written out.
        if (tree == state.baseTree) return state.base
        return when (config.format) {
            ConfigFormat.TOML -> withContext(Dispatchers.IO) {
                container.python.tomlFromJson(state.base, tree.toString())
            }

            else -> formatConfigJson(tree)
        }
    }

    private suspend fun parse(text: String): Result<JsonElement> = withContext(Dispatchers.IO) {
        runCatching {
            when (config.format) {
                ConfigFormat.TOML -> Json.parseToJsonElement(container.python.tomlToJson(text))
                else -> parseConfigJson(text)
            }
        }
    }

    private fun cannotShow(e: Throwable) = EditorMessage(
        R.string.config_parse_error,
        e.message ?: ""
    )

    private fun busy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("Godlo", "config editor", e)
                _state.update {
                    it.copy(
                        message = EditorMessage(
                            R.string.config_error,
                            e.message ?: ""
                        )
                    )
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
