package io.github.eggplants.godlo.download

import io.github.eggplants.godlo.core.MediaKind
import io.github.eggplants.godlo.library.Library
import java.io.File

/** Where a finished download can be opened. */
sealed interface DownloadTarget {
    /** One episode or gallery: straight to the reader. */
    data class Album(val dir: File) : DownloadTarget

    /** Several albums, e.g. a run of episodes: their folder in the image library. */
    data class ImageFolder(val path: List<String>) : DownloadTarget

    data class Video(val file: File) : DownloadTarget

    data class Audio(val files: List<File>) : DownloadTarget

    companion object {
        /**
         * Where [task] can be opened, going by what the library found on disk, so a download
         * whose files were deleted since has nowhere to go.
         */
        fun of(task: DownloadTask, library: Library): DownloadTarget? {
            if (task.state != TaskState.DONE || task.files.isEmpty()) return null
            val files = task.files.map(::File)
            val paths = files.map { it.absolutePath }.toSet()
            val videos = library.video.map { it.file }.filter { it.absolutePath in paths }
            return when (task.kind) {
                MediaKind.AUDIO ->
                    library.audio.map { it.file }.filter { it.absolutePath in paths }
                        .takeIf { it.isNotEmpty() }
                        ?.let { found -> Audio(files.filter { it in found }) }

                MediaKind.VIDEO -> videos.firstOrNull()?.let(::Video)

                MediaKind.IMAGE -> {
                    // getjmanga reports episode directories, gallery-dl the pictures in them.
                    val byDir = library.albums.associateBy { it.dir }
                    val albums = files.mapNotNull { byDir[it] ?: byDir[it.parentFile] }.distinct()
                    when {
                        albums.size == 1 -> Album(albums.single().dir)

                        albums.isNotEmpty() -> ImageFolder(
                            commonPrefix(
                                albums.map {
                                    it.path.dropLast(1)
                                }
                            )
                        )

                        // gallery-dl also saves the videos of image sites.
                        else -> videos.firstOrNull()?.let(::Video)
                    }
                }
            }
        }

        private fun commonPrefix(paths: List<List<String>>): List<String> =
            paths.reduce { prefix, path ->
                prefix.zip(path).takeWhile { (a, b) -> a == b }.map { it.first }
            }
    }
}
