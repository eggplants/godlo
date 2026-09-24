package io.github.eggplants.godlo.library

import java.io.File

/** One entry at a level of the image library: a folder to go into, or an album to read. */
sealed interface AlbumNode {
    val name: String
    val cover: File
    val modified: Long

    /** Unique among the entries of a level, even when a folder and an album share a name. */
    val key: String

    /** What long-pressing and deleting the entry removes. */
    val dirs: List<File>

    /** A directory holding albums or further folders: a site, a manga title, a user, ... */
    data class Folder(
        val path: List<String>,
        override val cover: File,
        override val modified: Long,
        /** How many entries going into the folder shows. */
        val entries: Int,
        override val dirs: List<File>
    ) : AlbumNode {
        override val name: String get() = path.last()
        override val key: String get() = "folder:$name"
    }

    data class Leaf(val album: Album) : AlbumNode {
        override val name: String get() = album.title
        override val cover: File get() = album.cover
        override val modified: Long get() = album.modified
        override val key: String get() = "album:${album.dir.absolutePath}"
        override val dirs: List<File> get() = listOf(album.dir)
    }
}

/**
 * The albums, grouped by the directories they sit in: sites at the top, then manga titles (or
 * users, ...), then the episodes themselves. The same site saved by two tools is one folder.
 */
object AlbumTree {
    /**
     * What is directly under [path] (empty for the top level).
     *
     * Folders come first, most recently updated first; albums follow in reading order, so
     * episode 2 comes after episode 1.
     */
    fun children(albums: List<Album>, path: List<String>): List<AlbumNode> {
        val below = albums.filter {
            it.path.size > path.size &&
                it.path.subList(0, path.size) == path
        }
        val (leaves, deeper) = below.partition { it.path.size == path.size + 1 }
        val folders = deeper.groupBy { it.path[path.size] }.map { (name, inside) ->
            val folderPath = path + name
            val latest = inside.maxBy { it.modified }
            AlbumNode.Folder(
                path = folderPath,
                cover = latest.cover,
                modified = latest.modified,
                entries = inside.map { it.path[folderPath.size] }.distinct().size,
                dirs = inside.map { it.dir.ancestor(it.path.size - folderPath.size) }.distinct()
            )
        }
        return folders.sortedByDescending { it.modified } +
            leaves.sortedWith(compareBy(NaturalOrder) { it.title }).map { AlbumNode.Leaf(it) }
    }

    private fun File.ancestor(levels: Int): File = (1..levels).fold(this) { dir, _ ->
        dir.parentFile!!
    }
}
