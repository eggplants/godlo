package io.github.eggplants.godlo.library

import java.io.File

/** One entry at a level of a library: a folder to go into, or an item (album, video) to open. */
sealed interface TreeNode<out T> {
    val name: String
    val modified: Long

    /** Unique among the entries of a level, even when a folder and an item share a name. */
    val key: String

    /** What long-pressing and deleting the entry removes. */
    val dirs: List<File>

    /** A directory holding items or further folders: a site, a manga title, a playlist, ... */
    data class Folder<T>(
        val path: List<String>,
        /** The most recently updated item inside, which stands for the folder. */
        val latest: T,
        override val modified: Long,
        /** How many entries going into the folder shows. */
        val entries: Int,
        override val dirs: List<File>
    ) : TreeNode<T> {
        override val name: String get() = path.last()
        override val key: String get() = "folder:$name"
    }

    data class Leaf<T>(
        val item: T,
        override val name: String,
        override val modified: Long,
        val file: File
    ) : TreeNode<T> {
        override val key: String get() = "item:${file.absolutePath}"
        override val dirs: List<File> get() = listOf(file)
    }
}

/**
 * Items grouped by the directories they sit in: sites at the top, then manga titles, users or
 * playlists, then the items themselves. The same site saved by two tools is one folder.
 */
class LibraryTree<T>(
    /** Directory names from the tool's directory down to the item, the item's own name last. */
    private val pathOf: (T) -> List<String>,
    /** The item on disk: an album's directory, a video's file. */
    private val fileOf: (T) -> File,
    private val modifiedOf: (T) -> Long,
    /** How the items of one level are ordered; folders always come first, newest first. */
    private val itemOrder: (List<T>) -> List<T>
) {
    /** What is directly under [path] (empty for the top level). */
    fun children(items: List<T>, path: List<String>): List<TreeNode<T>> {
        val below = items.filter {
            pathOf(it).size > path.size &&
                pathOf(it).subList(0, path.size) == path
        }
        val (leaves, deeper) = below.partition { pathOf(it).size == path.size + 1 }
        val folders = deeper.groupBy { pathOf(it)[path.size] }.map { (name, inside) ->
            val folderPath = path + name
            val latest = inside.maxBy(modifiedOf)
            TreeNode.Folder(
                path = folderPath,
                latest = latest,
                modified = modifiedOf(latest),
                entries = inside.map { pathOf(it)[folderPath.size] }.distinct().size,
                dirs = inside.map {
                    fileOf(it).ancestor(pathOf(it).size - folderPath.size)
                }.distinct()
            )
        }
        return folders.sortedByDescending { it.modified } +
            itemOrder(leaves).map {
                TreeNode.Leaf(it, pathOf(it).last(), modifiedOf(it), fileOf(it))
            }
    }

    private fun File.ancestor(levels: Int): File = (1..levels).fold(this) { dir, _ ->
        dir.parentFile!!
    }

    companion object {
        /** Albums: episodes in reading order, so 2話 comes before 10話. */
        val albums = LibraryTree<Album>(
            pathOf = { it.path },
            fileOf = { it.dir },
            modifiedOf = { it.modified },
            itemOrder = { level -> level.sortedWith(compareBy(NaturalOrder) { it.title }) }
        )

        /**
         * Videos and audio: newest first, except where every name starts with a number, as
         * yt-dlp's playlist entries do; those keep the playlist's order.
         */
        val media = LibraryTree<MediaFile>(
            pathOf = { it.path },
            fileOf = { it.file },
            modifiedOf = { it.modified },
            itemOrder = { level ->
                if (level.isNotEmpty() && level.all { it.file.name.first().isDigit() }) {
                    level.sortedWith(compareBy(NaturalOrder) { it.file.name })
                } else {
                    level.sortedByDescending { it.modified }
                }
            }
        )
    }
}

/** An album's first page, or for a folder, that of the album inside it most recently updated. */
val TreeNode<Album>.cover: File
    get() = when (this) {
        is TreeNode.Folder -> latest.cover
        is TreeNode.Leaf -> item.cover
    }
