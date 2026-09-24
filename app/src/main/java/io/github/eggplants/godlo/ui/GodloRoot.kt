package io.github.eggplants.godlo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryMusic
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
import io.github.eggplants.godlo.download.TaskState
import io.github.eggplants.godlo.ui.download.DownloadScreen
import io.github.eggplants.godlo.ui.library.AudioLibraryScreen
import io.github.eggplants.godlo.ui.library.ImageLibraryScreen
import io.github.eggplants.godlo.ui.library.VideoLibraryScreen
import io.github.eggplants.godlo.ui.player.MiniPlayer
import io.github.eggplants.godlo.ui.player.NowPlayingScreen
import io.github.eggplants.godlo.ui.player.VideoPlayerScreen
import io.github.eggplants.godlo.ui.reader.ReaderScreen
import io.github.eggplants.godlo.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object DownloadsRoute

@Serializable object ImagesRoute

@Serializable object MusicRoute

@Serializable object VideosRoute

@Serializable object SettingsRoute

@Serializable data class ReaderRoute(val dir: String)

@Serializable data class VideoRoute(val path: String)

@Serializable object NowPlayingRoute

private enum class TopLevel(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    DOWNLOADS(DownloadsRoute, "ダウンロード", Icons.Outlined.Download, Icons.Filled.Download),
    IMAGES(ImagesRoute, "画像", Icons.Outlined.Image, Icons.Filled.Image),
    MUSIC(MusicRoute, "音楽", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
    VIDEOS(VideosRoute, "動画", Icons.Outlined.Movie, Icons.Filled.Movie),
    SETTINGS(SettingsRoute, "設定", Icons.Outlined.Settings, Icons.Filled.Settings)
}

@Composable
fun GodloRoot(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val current = TopLevel.entries.firstOrNull { top ->
        destination?.hierarchy?.any { it.hasRoute(top.route::class) } == true
    }
    val tasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val active = tasks.count { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING }
    val nowPlaying by container.music.state.collectAsStateWithLifecycle()
    val sharedUrl by container.sharedUrl.collectAsStateWithLifecycle()

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null && current != TopLevel.DOWNLOADS) {
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
                    label = { Text(top.label) }
                )
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                NavHost(nav, startDestination = DownloadsRoute) {
                    composable<DownloadsRoute> { DownloadScreen(container) }
                    composable<ImagesRoute> {
                        ImageLibraryScreen(container, onOpen = {
                            nav.navigate(ReaderRoute(it.absolutePath))
                        })
                    }
                    composable<MusicRoute> { AudioLibraryScreen(container) }
                    composable<VideosRoute> {
                        VideoLibraryScreen(container, onOpen = {
                            nav.navigate(VideoRoute(it.absolutePath))
                        })
                    }
                    composable<SettingsRoute> { SettingsScreen(container) }
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
