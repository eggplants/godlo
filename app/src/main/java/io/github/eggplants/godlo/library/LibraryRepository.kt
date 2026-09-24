package io.github.eggplants.godlo.library

import io.github.eggplants.godlo.core.Engine
import io.github.eggplants.godlo.core.MediaKind
import io.github.eggplants.godlo.core.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "heic", "heif")
val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp", "ts", "flv", "avi")
val AUDIO_EXTENSIONS =
    setOf("mp3", "m4a", "aac", "opus", "ogg", "oga", "flac", "wav", "wma", "alac")

/** A directory of pages or pictures: a getjmanga episode, a gallery-dl user, ... */
data class Album(
    val dir: File,
    /** Directory names from the tool's directory down: `<site>/[<title>/...]<album>`. */
    val path: List<String>,
    val cover: File,
    val count: Int,
    val modified: Long
) {
    val title: String get() = dir.name
    val site: String get() = path.first()

    /** The directories between the site and the album, e.g. the manga series; empty when none. */
    val group: String get() = path.drop(1).dropLast(1).joinToString(" / ")
}

data class MediaFile(
    val file: File,
    val site: String,
    val folder: String,
    val modified: Long,
    val size: Long
) {
    val title: String get() = file.nameWithoutExtension
}

data class Library(
    val albums: List<Album> = emptyList(),
    val audio: List<MediaFile> = emptyList(),
    val video: List<MediaFile> = emptyList(),
    val loading: Boolean = true
) {
    fun sites(kind: MediaKind): List<String> = when (kind) {
        MediaKind.IMAGE -> albums.map { it.site }
        MediaKind.AUDIO -> audio.map { it.site }
        MediaKind.VIDEO -> video.map { it.site }
    }.distinct().sorted()
}

/**
 * Everything saved in the tools' directories, found by walking the file system.
 *
 * Each tool keeps `<site>/` directories in its own directory, whatever it saved, so what a
 * file is comes from its extension: a directory of pictures makes an album.
 */
class LibraryRepository(settings: SettingsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()
    private val roots = settings.settings
        .map { s -> Engine.entries.map { File(s.root(it)) }.distinct() }
        .distinctUntilChanged()

    init {
        scope.launch { roots.collect { refreshNow(it) } }
    }

    fun refresh() {
        scope.launch { refreshNow(roots.first()) }
    }

    private suspend fun refreshNow(roots: List<File>) = lock.withLock {
        _library.value = Library(
            albums = roots.flatMap(::scanAlbums).sortedByDescending { it.modified },
            audio = roots.flatMap {
                scanFiles(it, AUDIO_EXTENSIONS)
            }.sortedByDescending { it.modified },
            video = roots.flatMap {
                scanFiles(it, VIDEO_EXTENSIONS)
            }.sortedByDescending { it.modified },
            loading = false
        )
    }

    /** Deletes albums, folders of them or files, and forgets them. */
    fun delete(vararg targets: File) {
        scope.launch {
            targets.forEach { it.deleteRecursively() }
            refreshNow(roots.first())
        }
    }

    private fun scanAlbums(base: File): List<Album> {
        if (!base.isDirectory) return emptyList()
        val albums = mutableListOf<Album>()
        base.walkTopDown()
            .onEnter { !it.name.startsWith(".") && it.name != "_cbz" }
            .filter { it.isDirectory && it != base }
            .forEach { dir ->
                val images = dir.listFiles { f ->
                    f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS
                }
                if (images.isNullOrEmpty()) return@forEach
                albums += Album(
                    dir = dir,
                    // Pictures straight in <site>/ make an album named after the site.
                    path = dir.relativeTo(base).invariantSeparatorsPath.split("/"),
                    cover = images.minWith(NaturalOrder.files),
                    count = images.size,
                    modified = images.maxOf { it.lastModified() }
                )
            }
        return albums
    }

    private fun scanFiles(base: File, extensions: Set<String>): List<MediaFile> {
        if (!base.isDirectory) return emptyList()
        return base.walkTopDown()
            .onEnter { !it.name.startsWith(".") }
            .filter { it.isFile && it.extension.lowercase() in extensions && it.parentFile != base }
            .map { file ->
                val parts = file.parentFile!!.relativeTo(base).invariantSeparatorsPath.split("/")
                MediaFile(
                    file = file,
                    site = parts.first(),
                    folder = parts.drop(1).joinToString(" / "),
                    modified = file.lastModified(),
                    size = file.length()
                )
            }
            .toList()
    }
}

/** Orders "2.jpg" before "10.jpg", the way a person numbers pages. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val xs = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val ys = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(xs.size, ys.size)) {
            val x = xs[i]
            val y = ys[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 }
                    ?: x.trimStart('0').compareTo(y.trimStart('0'))
            } else {
                x.compareTo(y)
            }
            if (c != 0) return c
        }
        return xs.size.compareTo(ys.size)
    }

    val files: Comparator<File> = Comparator { a, b -> compare(a.name, b.name) }
}
