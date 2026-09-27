package io.github.eggplants.godlo.library

import io.github.eggplants.godlo.core.LibrarySort
import io.github.eggplants.godlo.core.SortKey
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
        /** The items inside together. */
        val size: Long,
        /** The oldest item inside. */
        val created: Long,
        override val dirs: List<File>,
    ) : TreeNode<T> {
        override val name: String
            get() = path.last()

        override val key: String
            get() = "folder:$name"
    }

    data class Leaf<T>(
        val item: T,
        override val name: String,
        override val modified: Long,
        val file: File,
    ) : TreeNode<T> {
        override val key: String
            get() = "item:${file.absolutePath}"

        override val dirs: List<File>
            get() = listOf(file)
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
    private val createdOf: (T) -> Long,
    private val sizeOf: (T) -> Long,
    /** The episode number [SortKey.EPISODE] goes by; null for items that have none. */
    private val numberOf: (T) -> Int? = { null },
) {
    /**
     * What is directly under [path] (empty for the top level): folders first, then items, each in
     * [sort]'s order.
     */
    fun children(items: List<T>, path: List<String>, sort: LibrarySort): List<TreeNode<T>> {
        val below = items.filter {
            pathOf(it).size > path.size && pathOf(it).subList(0, path.size) == path
        }
        val (leaves, deeper) = below.partition { pathOf(it).size == path.size + 1 }
        val folders =
            deeper
                .groupBy { pathOf(it)[path.size] }
                .map { (name, inside) ->
                    val folderPath = path + name
                    val latest = inside.maxBy(modifiedOf)
                    TreeNode.Folder(
                        path = folderPath,
                        latest = latest,
                        modified = modifiedOf(latest),
                        entries = inside.map { pathOf(it)[folderPath.size] }.distinct().size,
                        size = inside.sumOf(sizeOf),
                        created = inside.minOf(createdOf),
                        dirs =
                            inside
                                .map {
                                    fileOf(it).ancestor(pathOf(it).size - folderPath.size)
                                }
                                .distinct(),
                    )
                }
        val folderOrder =
            when (sort.key) {
                // Folders have no numbers: the most recently updated first, as before.
                SortKey.EPISODE -> compareByDescending<TreeNode.Folder<T>> { it.modified }
                else -> ordered(sort) { it.facts() }
            }
        return folders.sortedWith(folderOrder) +
            sorted(leaves, sort).map {
                TreeNode.Leaf(it, pathOf(it).last(), modifiedOf(it), fileOf(it))
            }
    }

    /** [items] in [sort]'s order, wherever they are: search results, say. */
    fun sorted(items: List<T>, sort: LibrarySort): List<T> =
        if (sort.key == SortKey.EPISODE) {
            inReadingOrder(items, { pathOf(it).last() }, numberOf).let {
                if (sort.descending) it.reversed() else it
            }
        } else {
            items.sortedWith(ordered(sort) { it.facts() })
        }

    private fun T.facts() =
        Facts(
            pathOf(this).last(),
            sizeOf(this),
            createdOf(this),
            modifiedOf(this),
        )

    private fun TreeNode.Folder<T>.facts() = Facts(name, size, created, modified)

    private fun File.ancestor(levels: Int): File =
        (1..levels).fold(this) { dir, _ ->
            dir.parentFile!!
        }

    /** What [SortKey]s other than [SortKey.EPISODE] compare. */
    private data class Facts(
        val name: String,
        val size: Long,
        val created: Long,
        val modified: Long,
    )

    private fun <N> ordered(sort: LibrarySort, facts: (N) -> Facts): Comparator<N> {
        val byName = compareBy(NaturalOrder) { n: N -> facts(n).name }
        val byKey =
            when (sort.key) {
                SortKey.SIZE -> compareBy { n: N -> facts(n).size }.then(byName)
                SortKey.CREATED -> compareBy { n: N -> facts(n).created }.then(byName)
                SortKey.MODIFIED -> compareBy { n: N -> facts(n).modified }.then(byName)
                SortKey.NAME,
                SortKey.EPISODE -> byName
            }
        return if (sort.descending) byKey.reversed() else byKey
    }

    companion object {
        val albums =
            LibraryTree<Album>(
                pathOf = { it.path },
                fileOf = { it.dir },
                modifiedOf = { it.modified },
                createdOf = { it.created },
                sizeOf = { it.size },
                numberOf = { it.number },
            )

        /** Videos and audio. */
        val media =
            LibraryTree<MediaFile>(
                pathOf = { it.path },
                fileOf = { it.file },
                modifiedOf = { it.modified },
                createdOf = { it.created },
                sizeOf = { it.size },
            )
    }
}

/** An album's first page, or for a folder, that of the album inside it most recently updated. */
val TreeNode<Album>.cover: File
    get() =
        when (this) {
            is TreeNode.Folder -> latest.cover
            is TreeNode.Leaf -> item.cover
        }
