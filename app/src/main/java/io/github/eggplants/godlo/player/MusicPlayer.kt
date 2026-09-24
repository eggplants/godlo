package io.github.eggplants.godlo.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val path: String? = null,
    val title: String = "",
    val artist: String = "",
    val artwork: ByteArray? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
) {
    val active: Boolean get() = path != null
}

/** The app's handle on [PlaybackService]: a [MediaController], and what it is playing. */
class MusicPlayer(private val context: Context) {
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    val positionMs: Long get() = controller?.currentPosition ?: 0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let { return action(it) }
        pending += action
        if (pending.size > 1) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val built =
                runCatching { future.get() }.getOrNull() ?: return@addListener pending.clear()
            controller = built
            built.addListener(listener)
            publish(built)
            pending.toList().forEach { it(built) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    /** Connects if the service is already playing, so the mini player shows up on launch. */
    fun connect() = withController { }

    fun play(files: List<File>, index: Int) = withController { c ->
        c.setMediaItems(files.map { it.toMediaItem() }, index, 0)
        c.prepare()
        c.play()
    }

    fun toggle() = withController { if (it.isPlaying) it.pause() else it.play() }

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(ms: Long) = withController { it.seekTo(ms) }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        val meta = player.mediaMetadata
        _state.value = NowPlaying(
            path = item?.mediaId,
            title = meta.title?.toString() ?: item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = meta.artist?.toString() ?: meta.albumArtist?.toString().orEmpty(),
            artwork = meta.artworkData,
            isPlaying = player.isPlaying,
            durationMs = player.duration.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem()
        )
    }
}

fun File.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(absolutePath)
    .setUri(Uri.fromFile(this))
    .setMediaMetadata(MediaMetadata.Builder().setTitle(nameWithoutExtension).build())
    .build()
