package io.github.eggplants.godlo.download

import android.content.Context
import android.media.MediaScannerConnection
import androidx.core.content.ContextCompat
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.AppLanguage
import io.github.eggplants.godlo.core.AppSettings
import io.github.eggplants.godlo.core.DownloadCallback
import io.github.eggplants.godlo.core.DownloadRequest
import io.github.eggplants.godlo.core.Engine
import io.github.eggplants.godlo.core.MediaKind
import io.github.eggplants.godlo.core.PythonBridge
import io.github.eggplants.godlo.core.SettingsRepository
import io.github.eggplants.godlo.core.Storage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class TaskState { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

@Serializable
data class DownloadTask(
    val id: Long,
    val url: String,
    val engine: Engine,
    val kind: MediaKind,
    val site: String,
    val playlist: Boolean,
    val videoQuality: String,
    val audioFormat: String,
    /** getjmanga: the previous episodes too, besides the next ones. */
    val previous: Boolean = false,
    /** getjmanga: remember the work, for [Patrol] to come back to. */
    val store: Boolean = false,
    /** getjmanga: new episodes of every stored work, rather than [url]. */
    val patrol: Boolean = false,
    val state: TaskState = TaskState.QUEUED,
    val title: String = "",
    /** 0..1, or negative while the total is unknown. */
    val progress: Float = -1f,
    val detail: String = "",
    val files: List<String> = emptyList(),
    val message: String = "",
    val log: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val finished: Boolean get() = state == TaskState.DONE || state == TaskState.FAILED ||
        state == TaskState.CANCELLED
}

/**
 * The download queue. Tasks run one at a time: the tools share global state inside the one
 * Python interpreter, and the site would rather not see parallel scrapes anyway.
 */
class DownloadManager(
    private val context: Context,
    private val python: PythonBridge,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val historyFile = File(context.filesDir, "downloads.json")
    private val saveLock = Mutex()
    private val runLock = Mutex()
    private val cancelled = ConcurrentHashMap.newKeySet<Long>()

    private val _tasks = MutableStateFlow(loadHistory())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _completed = MutableSharedFlow<DownloadTask>(extraBufferCapacity = 16)

    /** Each task that finished with files, for the library to rescan. */
    val completed: SharedFlow<DownloadTask> = _completed

    fun enqueue(
        url: String,
        engine: Engine,
        kind: MediaKind,
        site: String,
        playlist: Boolean,
        videoQuality: String,
        audioFormat: String,
        previous: Boolean = false,
        store: Boolean = false
    ) {
        val task = DownloadTask(
            id = System.nanoTime(),
            url = url.trim(),
            engine = engine,
            kind = kind,
            site = site,
            playlist = playlist,
            videoQuality = videoQuality,
            audioFormat = audioFormat,
            previous = previous,
            store = store
        )
        add(task)
    }

    /** Queues a look for new episodes of every work stored for patrol. */
    fun enqueuePatrol() {
        add(
            DownloadTask(
                id = System.nanoTime(),
                url = "",
                engine = Engine.GETJMANGA,
                kind = MediaKind.IMAGE,
                site = "",
                playlist = false,
                videoQuality = "",
                audioFormat = "",
                title = AppLanguage.localize(context).getString(R.string.patrol_title),
                patrol = true
            )
        )
    }

    private fun add(task: DownloadTask) {
        _tasks.update { listOf(task) + it }
        persist()
        ContextCompat.startForegroundService(context, DownloadService.intent(context))
    }

    fun cancel(id: Long) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        when (task.state) {
            TaskState.QUEUED -> edit(id) { it.copy(state = TaskState.CANCELLED) }
            TaskState.RUNNING -> cancelled += id
            else -> Unit
        }
    }

    fun retry(id: Long) {
        edit(id) {
            it.copy(
                state = TaskState.QUEUED,
                progress = -1f,
                detail = "",
                message = "",
                log = emptyList()
            )
        }
        ContextCompat.startForegroundService(context, DownloadService.intent(context))
    }

    fun remove(id: Long) {
        cancel(id)
        _tasks.update { list -> list.filterNot { it.id == id && it.state != TaskState.RUNNING } }
        persist()
    }

    fun clearFinished() {
        _tasks.update { list -> list.filterNot { it.finished } }
        persist()
    }

    val hasPending: Boolean
        get() = _tasks.value.any { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING }

    /** Runs queued tasks until none are left. Called by [DownloadService]. */
    suspend fun drain(onUpdate: (DownloadTask) -> Unit) = withContext(Dispatchers.IO) {
        runLock.withLock {
            while (true) {
                // Oldest first: new tasks go to the front of the list.
                val next = _tasks.value.lastOrNull { it.state == TaskState.QUEUED } ?: break
                run(next, settings.settings.first(), onUpdate)
            }
        }
    }

    private fun run(task: DownloadTask, settings: AppSettings, onUpdate: (DownloadTask) -> Unit) {
        val id = task.id
        cancelled -= id
        edit(id) { it.copy(state = TaskState.RUNNING) }
        onUpdate(current(id))
        val root = settings.root(task.engine)
        val configDir = Storage.configDir
        val cookies = File(configDir, "cookies.txt").takeIf { it.isFile }?.absolutePath
        val request = DownloadRequest(
            url = task.url,
            engine = task.engine.id,
            kind = task.kind.id,
            root = root,
            playlist = task.playlist,
            previous = task.previous,
            store = task.store,
            patrol = task.patrol,
            videoQuality = task.videoQuality,
            audioFormat = task.audioFormat,
            imageFormat = settings.imageFormat,
            cbz = settings.cbz,
            cookies = cookies,
            configDir = configDir.absolutePath,
            lang = AppLanguage.shown()
        )
        var lastEmit = 0L
        val callback = object : DownloadCallback {
            override fun progress(fraction: Double, detail: String) {
                edit(id, save = false) { it.copy(progress = fraction.toFloat(), detail = detail) }
                val now = System.currentTimeMillis()
                if (now - lastEmit > 500) {
                    lastEmit = now
                    onUpdate(current(id))
                }
            }

            override fun title(text: String) {
                if (current(id).title != text) edit(id, save = false) { it.copy(title = text) }
            }

            override fun file(path: String) {
                edit(id, save = false) { it.copy(files = it.files + path) }
            }

            override fun log(text: String) {
                edit(id, save = false) { it.copy(log = (it.log + text).takeLast(MAX_LOG)) }
            }

            override fun cancelled(): Boolean = id in cancelled
        }

        File(root).mkdirs()
        val outcome = python.download(request, callback)
        val state = when (outcome.status) {
            "ok" -> TaskState.DONE
            "cancelled" -> TaskState.CANCELLED
            else -> TaskState.FAILED
        }
        edit(id) {
            it.copy(
                state = state,
                progress = if (state == TaskState.DONE) 1f else it.progress,
                detail = "",
                message = outcome.message,
                title = it.title.ifBlank { it.url }
            )
        }
        cancelled -= id
        val done = current(id)
        onUpdate(done)
        if (done.files.isNotEmpty()) {
            scan(done.files)
            _completed.tryEmit(done)
        }
    }

    /** Tell MediaStore about new files, so other apps (and the system gallery) see them. */
    private fun scan(paths: List<String>) {
        val files = paths.flatMap { path ->
            val file = File(path)
            if (file.isDirectory) {
                file.walkTopDown().filter {
                    it.isFile
                }.map { it.absolutePath }.toList()
            } else {
                listOf(path)
            }
        }
        if (files.isNotEmpty()) {
            MediaScannerConnection.scanFile(
                context,
                files.toTypedArray(),
                null,
                null
            )
        }
    }

    private fun current(id: Long): DownloadTask = _tasks.value.first { it.id == id }

    private fun edit(id: Long, save: Boolean = true, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
        if (save) persist()
    }

    private fun persist() {
        val snapshot = _tasks.value
        scope.launch {
            saveLock.withLock {
                runCatching {
                    historyFile.writeText(json.encodeToString(snapshot.take(MAX_HISTORY)))
                }
            }
        }
    }

    private fun loadHistory(): List<DownloadTask> = runCatching {
        json.decodeFromString<List<DownloadTask>>(historyFile.readText()).map {
            // Whatever was running when the process died starts over.
            if (it.state ==
                TaskState.RUNNING
            ) {
                it.copy(state = TaskState.QUEUED, progress = -1f, detail = "")
            } else {
                it
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_LOG = 200
        const val MAX_HISTORY = 300
    }
}
