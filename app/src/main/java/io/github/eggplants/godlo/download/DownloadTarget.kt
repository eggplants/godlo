package io.github.eggplants.godlo.download

import io.github.eggplants.godlo.core.MediaKind
import io.github.eggplants.godlo.library.Library
import java.io.File

/**
 * Where a finished download can be opened: a viewer, over the folder in its library tab that
 * backing out of the viewer returns to.
 */
sealed interface DownloadTarget {
    /** The folder in the library tab, `site/title/...`. */
    val folder: List<String>

    /** One episode or gallery: the reader. */
    data class Album(val dir: File, override val folder: List<String>) : DownloadTarget

    /** Several albums, e.g. a run of episodes: just their folder in the image library. */
    data class ImageFolder(override val folder: List<String>) : DownloadTarget

    data class Video(val file: File, override val folder: List<String>) : DownloadTarget

    data class Audio(val files: List<File>, override val folder: List<String>) :
        DownloadTarget

    companion object {
        /**
         * Where [task] can be opened, going by what the library found on disk, so a download
         * whose files were deleted since has nowhere to go.
         */
        fun of(task: DownloadTask, library: Library): DownloadTarget? {
            if (task.state != TaskState.DONE || task.files.isEmpty()) return null
            val files = task.files.map(::File)
            val paths = files.map { it.absolutePath }.toSet()
            val video = library.video.firstOrNull { it.file.absolutePath in paths }
                ?.let { Video(it.file, it.path.dropLast(1)) }
            return when (task.kind) {
                MediaKind.AUDIO ->
                    library.audio.filter { it.file.absolutePath in paths }
                        .takeIf { it.isNotEmpty() }
                        ?.let { found ->
                            val byPath = found.associateBy { it.file.absolutePath }
                            Audio(
                                // In the order they were downloaded, as a playlist runs.
                                files.filter { it.absolutePath in byPath },
                                commonPrefix(found.map { it.path.dropLast(1) })
                            )
                        }

                MediaKind.VIDEO -> video

                MediaKind.IMAGE -> {
                    // getjmanga reports episode directories, gallery-dl the pictures in them.
                    val byDir = library.albums.associateBy { it.dir }
                    val albums = files.mapNotNull { byDir[it] ?: byDir[it.parentFile] }.distinct()
                    when {
                        albums.size == 1 -> albums.single().let {
                            Album(it.dir, it.path.dropLast(1))
                        }

                        albums.isNotEmpty() -> ImageFolder(
                            commonPrefix(
                                albums.map {
                                    it.path.dropLast(1)
                                }
                            )
                        )

                        // gallery-dl also saves the videos of image sites.
                        else -> video
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
