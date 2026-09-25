package io.github.eggplants.godlo.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.eggplants.godlo.AppContainer
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.download.DownloadTarget
import io.github.eggplants.godlo.download.TaskState
import io.github.eggplants.godlo.ui.download.DownloadScreen
import io.github.eggplants.godlo.ui.library.AudioLibraryScreen
import io.github.eggplants.godlo.ui.library.ImageLibraryScreen
import io.github.eggplants.godlo.ui.library.VideoLibraryScreen
import io.github.eggplants.godlo.ui.player.MiniPlayer
import io.github.eggplants.godlo.ui.player.NowPlayingScreen
import io.github.eggplants.godlo.ui.player.VideoPlayerScreen
import io.github.eggplants.godlo.ui.reader.ReaderScreen
import io.github.eggplants.godlo.ui.settings.AndroidLicensesScreen
import io.github.eggplants.godlo.ui.settings.PatrolScreen
import io.github.eggplants.godlo.ui.settings.PythonLicensesScreen
import io.github.eggplants.godlo.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object DownloadsRoute

/** The image library, opened at [path] ("site/title/..."; empty for the sites). */
@Serializable data class ImagesRoute(val path: String = "")

/** The audio library, opened at [path], as [ImagesRoute]. */
@Serializable data class AudioRoute(val path: String = "")

/** The video library, opened at [path], as [ImagesRoute]. */
@Serializable data class VideosRoute(val path: String = "")

@Serializable object SettingsRoute

@Serializable data class ReaderRoute(val dir: String)

@Serializable data class VideoRoute(val path: String)

@Serializable object NowPlayingRoute

@Serializable object AndroidLicensesRoute

@Serializable object PythonLicensesRoute

@Serializable object PatrolRoute

/** The tabs, the libraries in the order the download screen offers the kinds of media. */
private enum class TopLevel(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    DOWNLOADS(
        DownloadsRoute,
        R.string.nav_downloads,
        Icons.Outlined.Download,
        Icons.Filled.Download
    ),
    VIDEOS(VideosRoute(), R.string.nav_videos, Icons.Outlined.Movie, Icons.Filled.Movie),
    AUDIO(AudioRoute(), R.string.nav_audio, Icons.Outlined.Headphones, Icons.Filled.Headphones),
    IMAGES(ImagesRoute(), R.string.nav_images, Icons.Outlined.Image, Icons.Filled.Image),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

@Composable
fun GodloRoot(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    // Before the first destination is set, the start destination is what is about to show:
    // without this the navigation bar is missing, and taps on it lost, for the first frames.
    val current = if (destination == null) {
        TopLevel.DOWNLOADS
    } else {
        TopLevel.entries.firstOrNull { top ->
            destination.hierarchy.any { it.hasRoute(top.route::class) }
        }
    }
    val tasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val active = tasks.count { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING }
    val nowPlaying by container.audio.state.collectAsStateWithLifecycle()

    // To the download screen for each link shared in. Not keyed on the link itself: the
    // download screen's view model, alive on another tab too, takes it straight away.
    LaunchedEffect(nav) {
        container.shares.collect {
            nav.navigate(DownloadsRoute) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    val layoutType = if (current == null) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    }
    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevel.entries.forEach { top ->
                val selected = top == current
                item(
                    selected = selected,
                    onClick = {
                        nav.navigate(top.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        BadgedBox(badge = {
                            if (top == TopLevel.DOWNLOADS &&
                                active > 0
                            ) {
                                Badge { Text("$active") }
                            }
                        }) {
                            Icon(
                                if (selected) top.selectedIcon else top.icon,
                                contentDescription = null
                            )
                        }
                    },
                    label = { Text(stringResource(top.label)) }
                )
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                NavHost(nav, startDestination = DownloadsRoute) {
                    composable<DownloadsRoute> {
                        DownloadScreen(container, onOpen = { target ->
                            // The folder in the library tab first, so that backing out of the
                            // viewer lands among the download's neighbours.
                            val folder = target.folder.joinToString("/")
                            val tab = when (target) {
                                is DownloadTarget.Album, is DownloadTarget.ImageFolder ->
                                    ImagesRoute(folder)

                                is DownloadTarget.Video -> VideosRoute(folder)

                                is DownloadTarget.Audio -> AudioRoute(folder)
                            }
                            nav.navigate(tab) {
                                // Like picking the tab, but at the folder instead of where it was left.
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                            when (target) {
                                is DownloadTarget.Album ->
                                    nav.navigate(ReaderRoute(target.dir.absolutePath))

                                is DownloadTarget.ImageFolder -> Unit

                                is DownloadTarget.Video ->
                                    nav.navigate(VideoRoute(target.file.absolutePath))

                                is DownloadTarget.Audio -> {
                                    container.audio.play(target.files, 0)
                                    nav.navigate(NowPlayingRoute)
                                }
                            }
                        })
                    }
                    composable<ImagesRoute> {
                        ImageLibraryScreen(
                            container,
                            initialPath = it.toRoute<ImagesRoute>().path,
                            onOpen = { dir -> nav.navigate(ReaderRoute(dir.absolutePath)) }
                        )
                    }
                    composable<AudioRoute> {
                        AudioLibraryScreen(container, initialPath = it.toRoute<AudioRoute>().path)
                    }
                    composable<VideosRoute> {
                        VideoLibraryScreen(
                            container,
                            initialPath = it.toRoute<VideosRoute>().path,
                            onOpen = { file -> nav.navigate(VideoRoute(file.absolutePath)) }
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            container,
                            onOpenPatrol = { nav.navigate(PatrolRoute) },
                            onOpenAndroidLicenses = { nav.navigate(AndroidLicensesRoute) },
                            onOpenPythonLicenses = { nav.navigate(PythonLicensesRoute) }
                        )
                    }
                    composable<PatrolRoute> {
                        PatrolScreen(container, onBack = { nav.popBackStack() }, onStarted = {
                            // To the queue, where the patrol shows how it goes.
                            nav.navigate(DownloadsRoute) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        })
                    }
                    composable<AndroidLicensesRoute> {
                        AndroidLicensesScreen(onBack = { nav.popBackStack() })
                    }
                    composable<PythonLicensesRoute> {
                        PythonLicensesScreen(onBack = { nav.popBackStack() })
                    }
                    composable<ReaderRoute> {
                        ReaderScreen(
                            container = container,
                            dir = it.toRoute<ReaderRoute>().dir,
                            onBack = { nav.popBackStack() },
                            onOpenAlbum = { dir ->
                                nav.navigate(ReaderRoute(dir.absolutePath)) {
                                    popUpTo<ReaderRoute> { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<VideoRoute> {
                        VideoPlayerScreen(container, it.toRoute<VideoRoute>().path, onBack = {
                            nav.popBackStack()
                        })
                    }
                    composable<NowPlayingRoute> {
                        NowPlayingScreen(container, onBack = { nav.popBackStack() })
                    }
                }
            }
            if (current != null && nowPlaying.active) {
                MiniPlayer(container, onExpand = { nav.navigate(NowPlayingRoute) })
            }
        }
    }
}
