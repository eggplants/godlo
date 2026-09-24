package io.github.eggplants.godlo

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import io.github.eggplants.godlo.core.PythonBridge
import io.github.eggplants.godlo.core.SettingsRepository
import io.github.eggplants.godlo.download.DownloadManager
import io.github.eggplants.godlo.library.AudioArtFetcher
import io.github.eggplants.godlo.library.LibraryRepository
import io.github.eggplants.godlo.player.MusicPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val python = PythonBridge(context)
    val downloads = DownloadManager(context, python, settings)
    val library = LibraryRepository(settings)
    val music = MusicPlayer(context)

    /** A URL shared into the app, waiting for the download screen to pick it up. */
    val sharedUrl = MutableStateFlow<String?>(null)
}

class GodloApp :
    Application(),
    SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scope.launch { container.downloads.completed.collect { container.library.refresh() } }
        // Starting Python takes a few seconds; do it before the first download asks for it.
        scope.launch(Dispatchers.IO) { runCatching { container.python.versions() } }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
                add(AudioArtFetcher.Factory())
            }
            .crossfade(true)
            .build()
}

val Context.container: AppContainer get() = (applicationContext as GodloApp).container
