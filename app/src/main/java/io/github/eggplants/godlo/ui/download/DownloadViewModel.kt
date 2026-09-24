package io.github.eggplants.godlo.ui.download

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.core.Engine
import io.github.eggplants.godlo.core.MediaKind
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadForm(
    val url: String = "",
    /** Null while nothing is detected yet. */
    val engine: Engine? = null,
    val kind: MediaKind = MediaKind.VIDEO,
    val site: String = "",
    /** The user picked the engine or kind by hand, so detection leaves them alone. */
    val manual: Boolean = false,
    val detecting: Boolean = false,
    val playlist: Boolean = false,
    val videoQuality: String = "1080",
    val audioFormat: String = "mp3"
) {
    /**
     * The URL as the tools get it: trimmed, and with the full-width letters a Japanese keyboard
     * may slip in ("ｗ" for "w") made ASCII.
     */
    val cleanUrl: String get() = cleanUrl(url)

    val valid: Boolean get() = cleanUrl.startsWith("http") && engine != null
}

@OptIn(FlowPreview::class)
class DownloadViewModel(private val container: AppContainer) : ViewModel() {
    private val _form = MutableStateFlow(DownloadForm())
    val form: StateFlow<DownloadForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            _form.update {
                it.copy(videoQuality = settings.videoQuality, audioFormat = settings.audioFormat)
            }
        }
        viewModelScope.launch {
            _form.map { it.cleanUrl }.distinctUntilChanged().debounce(400).collect { url ->
                if (url.startsWith("http")) detect(url)
            }
        }
        viewModelScope.launch {
            container.sharedUrl.collect { url ->
                if (url != null) {
                    setUrl(url)
                    container.sharedUrl.value = null
                }
            }
        }
    }

    fun setUrl(url: String) = _form.update {
        if (it.cleanUrl == cleanUrl(url)) {
            it.copy(url = url)
        } else {
            it.copy(url = url, manual = false, engine = null, site = "")
        }
    }

    private suspend fun detect(url: String) {
        _form.update { it.copy(detecting = true) }
        val found =
            withContext(Dispatchers.IO) {
                runCatching { container.python.detect(url) }
                    .onFailure { Log.w("Godlo", "detect failed: $url", it) }
                    .getOrNull()
            }
        _form.update { form ->
            if (form.cleanUrl != url) return@update form
            if (found ==
                null
            ) {
                return@update form.copy(
                    detecting = false,
                    engine =
                        form.engine ?: Engine.YTDLP
                )
            }
            val site = found.site
            if (form.manual) {
                form.copy(detecting = false, site = site)
            } else {
                form.copy(
                    detecting = false,
                    engine = Engine.fromId(found.engine),
                    kind = MediaKind.fromId(found.kind),
                    site = site
                )
            }
        }
    }

    fun setEngine(engine: Engine) = _form.update {
        it.copy(
            engine = engine,
            kind = if (it.kind in
                engine.kinds
            ) {
                it.kind
            } else {
                engine.kinds.first()
            },
            manual = true
        )
    }

    fun setKind(kind: MediaKind) = _form.update {
        val engine =
            it.engine?.takeIf { e -> kind in e.kinds }
                ?: if (kind == MediaKind.IMAGE) Engine.GALLERY_DL else Engine.YTDLP
        it.copy(kind = kind, engine = engine, manual = true)
    }

    fun setPlaylist(value: Boolean) = _form.update { it.copy(playlist = value) }

    fun setVideoQuality(value: String) = _form.update { it.copy(videoQuality = value) }

    fun setAudioFormat(value: String) = _form.update { it.copy(audioFormat = value) }

    fun submit() {
        val form = _form.value
        val engine = form.engine ?: return
        container.downloads.enqueue(
            url = form.cleanUrl,
            engine = engine,
            kind = form.kind,
            site = form.site,
            playlist = form.playlist,
            videoQuality = form.videoQuality,
            audioFormat = form.audioFormat
        )
        _form.update {
            it.copy(url = "", engine = null, site = "", manual = false, playlist = false)
        }
    }

    fun cancel(id: Long) = container.downloads.cancel(id)

    fun retry(id: Long) = container.downloads.retry(id)

    fun remove(id: Long) = container.downloads.remove(id)

    fun clearFinished() = container.downloads.clearFinished()
}

private fun cleanUrl(url: String): String = Normalizer.normalize(url, Normalizer.Form.NFKC).trim()
