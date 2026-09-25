package io.github.eggplants.godlo.download

import android.util.Log
import io.github.eggplants.godlo.core.Engine
import io.github.eggplants.godlo.core.PatrolWork
import io.github.eggplants.godlo.core.PythonBridge
import io.github.eggplants.godlo.core.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The getjmanga works to go back to for new episodes: the `[[patrol]]` entries of
 * `getjmanga.toml` in [Storage.configDir], which getjmanga on a computer reads too.
 */
class Patrol(private val python: PythonBridge, private val downloads: DownloadManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val _works = MutableStateFlow<List<PatrolWork>>(emptyList())
    val works: StateFlow<List<PatrolWork>> = _works.asStateFlow()

    init {
        // A getjmanga download may store its work, and a patrol moves each work on to where
        // the next one picks up: read the file again whenever one of them ends.
        scope.launch {
            downloads.tasks
                .map { tasks ->
                    tasks.filter { it.engine == Engine.GETJMANGA && it.finished }.map { it.id }
                }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    fun refresh() {
        scope.launch { lock.withLock { _works.value = read() } }
    }

    fun forget(url: String) {
        scope.launch {
            lock.withLock {
                runCatching { python.forgetWork(Storage.configDir, url) }
                    .onFailure { Log.w("Godlo", "could not forget $url", it) }
                _works.value = read()
            }
        }
    }

    fun start() = downloads.enqueuePatrol()

    private fun read(): List<PatrolWork> = runCatching { python.patrolWorks(Storage.configDir) }
        .onFailure { Log.w("Godlo", "could not read the patrol", it) }
        .getOrDefault(emptyList())
}
