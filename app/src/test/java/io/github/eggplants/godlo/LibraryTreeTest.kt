package io.github.eggplants.godlo

import io.github.eggplants.godlo.library.Album
import io.github.eggplants.godlo.library.LibraryTree
import io.github.eggplants.godlo.library.MediaFile
import io.github.eggplants.godlo.library.TreeNode
import io.github.eggplants.godlo.library.cover
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTreeTest {
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

    private fun names(path: List<String>) = LibraryTree.albums.children(albums, path).map {
        (if (it is TreeNode.Folder) "folder " else "album ") + it.name
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
        val site = LibraryTree.albums.children(albums, emptyList())
            .filterIsInstance<TreeNode.Folder<Album>>()
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

    private fun video(path: String, modified: Long) =
        MediaFile(File("/d/yt-dlp", path), path.split("/"), modified, size = 0)

    private val videos = listOf(
        video("youtube.com/old [a].mp4", 1),
        video("youtube.com/new [b].mp4", 3),
        video("youtube.com/Some playlist/002 second [d].mp4", 5),
        video("youtube.com/Some playlist/001 first [c].mp4", 6),
        video("nicovideo.jp/clip [e].mp4", 2)
    )

    private fun videoNames(path: List<String>) = LibraryTree.media.children(videos, path).map {
        (if (it is TreeNode.Folder) "folder " else "video ") + it.name
    }

    @Test
    fun videosGroupBySiteNewestFirst() {
        assertEquals(listOf("folder youtube.com", "folder nicovideo.jp"), videoNames(emptyList()))
        assertEquals(
            listOf("folder Some playlist", "video new [b].mp4", "video old [a].mp4"),
            videoNames(listOf("youtube.com"))
        )
    }

    @Test
    fun playlistKeepsItsOrder() {
        assertEquals(
            listOf("video 001 first [c].mp4", "video 002 second [d].mp4"),
            videoNames(listOf("youtube.com", "Some playlist"))
        )
    }
}
