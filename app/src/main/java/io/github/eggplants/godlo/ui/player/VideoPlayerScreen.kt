package io.github.eggplants.godlo.ui.player

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.RepeatModeUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.MainActivity
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.library.LibraryTree
import io.github.eggplants.godlo.library.TreeNode
import io.github.eggplants.godlo.player.toMediaItem
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(container: AppContainer, path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = view.context as? MainActivity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Follows the player's controls, which start hidden and tell only when they change.
    var controlsVisible by remember { mutableStateOf(false) }
    var playing by remember(path) { mutableStateOf(File(path)) }
    var isPlaying by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(Rational(16, 9)) }
    // Where the picture is on screen, for the window to shrink from.
    var videoBounds by remember { mutableStateOf<Rect?>(null) }
    var inPictureInPicture by remember {
        mutableStateOf(activity?.isInPictureInPictureMode == true)
    }

    // The other videos of the same folder play next, in the order the video tab lists them.
    val player = remember(path) {
        val videos = container.library.library.value.video
        val folder = videos.firstOrNull { it.file.absolutePath == path }?.path?.dropLast(1)
        val siblings = folder?.let { dir ->
            LibraryTree.media.children(videos, dir).mapNotNull {
                (it as? TreeNode.Leaf)?.item?.file
            }
        }
        val playlist = siblings.orEmpty().ifEmpty { listOf(File(path)) }
        val start = playlist.indexOfFirst { it.absolutePath == path }.coerceAtLeast(0)
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(
                    C.USAGE_MEDIA
                ).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setMediaItems(playlist.map { it.toMediaItem() }, start, 0)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        // Stopped, not just paused: in picture-in-picture the activity is paused but still seen.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(observer)
        val listener = object : Player.Listener {
            // The header names the video playing, which changes as the folder plays on.
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                item?.mediaId?.let { playing = File(it) }
            }

            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                pictureInPictureAspect(size)?.let { aspect = it }
            }
        }
        player.addListener(listener)
        // Lets the picture-in-picture window, headphones and the like play and pause it.
        val session = MediaSession.Builder(context, player).setId("video").build()
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            session.release()
            player.release()
        }
    }

    // Leaving the app while a video plays shrinks it into picture-in-picture.
    LaunchedEffect(activity, isPlaying, aspect, videoBounds) {
        activity?.pictureInPicture =
            if (isPlaying) pictureInPictureParams(aspect, videoBounds) else null
    }
    DisposableEffect(activity) {
        val onChange = Consumer<PictureInPictureModeChangedInfo> {
            inPictureInPicture = it.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(onChange)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(onChange)
            activity?.pictureInPicture = null
        }
    }

    // The status bar comes and goes with the controls, as in the reader; the navigation bar
    // stays away, where it would cover the player's own bottom bar.
    val bars = remember(view) {
        activity?.window?.let { WindowCompat.getInsetsController(it, view) }
    }
    LaunchedEffect(controlsVisible, inPictureInPicture) {
        bars?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.navigationBars())
        if (controlsVisible && !inPictureInPicture) {
            bars?.show(WindowInsetsCompat.Type.statusBars())
        } else {
            bars?.hide(WindowInsetsCompat.Type.statusBars())
        }
    }
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            bars?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    // Hide the controls in one go: animated, they leave the progress bar up a
                    // while and say they are hidden only after, keeping the header up alone.
                    setControllerAnimationEnabled(false)
                    videoSurfaceView?.addOnLayoutChangeListener { surface, _, _, _, _, _, _, _, _ ->
                        videoBounds = Rect().takeIf { surface.getGlobalVisibleRect(it) }
                    }
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    // Off, this video over and over, or the whole folder over and over.
                    setRepeatToggleModes(
                        RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE or
                            RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL
                    )
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                        }
                    )
                }
            },
            // The small window has room for the picture only.
            update = { it.useController = !inPictureInPicture },
            // Keeps the controls clear of a camera hole in the screen. Only that: padding for the
            // status bar too would move the video each time the controls come and go.
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
        )
        // Comes and goes with the player's controls, as the reader's header does with a tap.
        AnimatedVisibility(
            visible = controlsVisible && !inPictureInPicture,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Column(Modifier.weight(1f).padding(end = 4.dp)) {
                        Text(
                            playing.nameWithoutExtension,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            playing.parentFile?.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (activity?.supportsPictureInPicture == true) {
                        IconButton(onClick = {
                            activity.pictureInPicture = pictureInPictureParams(aspect, videoBounds)
                            activity.enterPictureInPicture()
                        }) {
                            Icon(
                                Icons.Filled.PictureInPictureAlt,
                                stringResource(R.string.picture_in_picture)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun pictureInPictureParams(aspect: Rational, bounds: Rect?): PictureInPictureParams =
    PictureInPictureParams.Builder().setAspectRatio(aspect).setSourceRectHint(bounds).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setAutoEnterEnabled(true)
            setSeamlessResizeEnabled(true)
        }
    }.build()

/** The video's shape, within what a picture-in-picture window can take (2.39:1 to 1:2.39). */
private fun pictureInPictureAspect(size: VideoSize): Rational? {
    if (size.width <= 0 || size.height <= 0) return null
    val width = (size.width * size.pixelWidthHeightRatio).toInt()
    val ratio = width.toFloat() / size.height
    return when {
        ratio > 2.39f -> Rational(239, 100)
        ratio < 1 / 2.39f -> Rational(100, 239)
        else -> Rational(width, size.height)
    }
}
