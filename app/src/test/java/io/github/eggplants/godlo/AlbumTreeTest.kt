package io.github.eggplants.godlo

import io.github.eggplants.godlo.library.Album
import io.github.eggplants.godlo.library.AlbumNode
import io.github.eggplants.godlo.library.AlbumTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumTreeTest {
    private fun album(root: String, path: String, modified: Long) = File(root, path).let { dir ->
        Album(dir, path.split("/"), File(dir, "0.jpg"), count = 10, modified = modified)
    }

    private val albums = listOf(
        // getjmanga: <site>/<title>/<episode>
        album("/d/getjmanga", "takecomic.jp/メイドインアビス/2話", 20),
        album("/d/getjmanga", "takecomic.jp/メイドインアビス/10話", 30),
        album("/d/getjmanga", "takecomic.jp/メイドインアビス/1話", 10),
        album("/d/getjmanga", "takecomic.jp/別の漫画/1話", 5),
        album("/d/getjmanga", "shonenjumpplus.com/阿波連さん/第1話", 40),
        // gallery-dl: <site>/<user> holds the pictures itself
        album("/d/gallery-dl", "x.com/someone", 50),
        // the same site from a second tool is the same folder
        album("/d/gallery-dl", "takecomic.jp/イラスト", 1)
    )

    private fun names(path: List<String>) = AlbumTree.children(albums, path).map {
        (if (it is AlbumNode.Folder) "folder " else "album ") + it.name
    }

    @Test
    fun topLevelListsSitesMostRecentFirst() {
        assertEquals(
            listOf("folder x.com", "folder shonenjumpplus.com", "folder takecomic.jp"),
            names(emptyList())
        )
    }

    @Test
    fun siteListsTitlesAndAlbumsStraightInIt() {
        assertEquals(
            listOf("folder メイドインアビス", "folder 別の漫画", "album イラスト"),
            names(listOf("takecomic.jp"))
        )
        assertEquals(listOf("album someone"), names(listOf("x.com")))
    }

    @Test
    fun titleListsEpisodesInReadingOrder() {
        assertEquals(
            listOf("album 1話", "album 2話", "album 10話"),
            names(listOf("takecomic.jp", "メイドインアビス"))
        )
    }

    @Test
    fun folderSummarisesWhatIsInside() {
        val site = AlbumTree.children(albums, emptyList())
            .filterIsInstance<AlbumNode.Folder>()
            .first { it.name == "takecomic.jp" }
        // メイドインアビス, 別の漫画 and イラスト
        assertEquals(3, site.entries)
        assertEquals(30L, site.modified)
        assertEquals(File("/d/getjmanga/takecomic.jp/メイドインアビス/10話/0.jpg"), site.cover)
        assertEquals(
            setOf(File("/d/getjmanga/takecomic.jp"), File("/d/gallery-dl/takecomic.jp")),
            site.dirs.toSet()
        )
    }
}
