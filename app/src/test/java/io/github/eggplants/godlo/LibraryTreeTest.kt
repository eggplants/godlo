package io.github.eggplants.godlo

import io.github.eggplants.godlo.core.LibrarySort
import io.github.eggplants.godlo.core.SortKey
import io.github.eggplants.godlo.library.Album
import io.github.eggplants.godlo.library.LibraryTree
import io.github.eggplants.godlo.library.MediaFile
import io.github.eggplants.godlo.library.TreeNode
import io.github.eggplants.godlo.library.cover
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTreeTest {
    private fun album(root: String, path: String, modified: Long, size: Long = 0) =
        File(root, path).let { dir ->
            Album(dir, path.split("/"), File(dir, "0.jpg"), 10, modified, size)
        }

    private val albums =
        listOf(
            // getjmanga: <site>/<title>/<episode>
            album("/d/getjmanga", "takecomic.jp/メイドインアビス/2話", 20, size = 300),
            album("/d/getjmanga", "takecomic.jp/メイドインアビス/10話", 30, size = 100),
            album("/d/getjmanga", "takecomic.jp/メイドインアビス/1話", 10, size = 200),
            album("/d/getjmanga", "takecomic.jp/別の漫画/1話", 5),
            album("/d/getjmanga", "shonenjumpplus.com/阿波連さん/第1話", 40),
            // gallery-dl: <site>/<user> holds the pictures itself
            album("/d/gallery-dl", "x.com/someone", 50),
            // the same site from a second tool is the same folder
            album("/d/gallery-dl", "takecomic.jp/イラスト", 1),
        )

    private fun names(path: List<String>, sort: LibrarySort = LibrarySort.IMAGES) =
        LibraryTree.albums.children(albums, path, sort).map {
            (if (it is TreeNode.Folder) "folder " else "album ") + it.name
        }

    @Test
    fun topLevelListsSitesMostRecentFirst() {
        assertEquals(
            listOf("folder x.com", "folder shonenjumpplus.com", "folder takecomic.jp"),
            names(emptyList()),
        )
    }

    @Test
    fun siteListsTitlesAndAlbumsStraightInIt() {
        assertEquals(
            listOf("folder メイドインアビス", "folder 別の漫画", "album イラスト"),
            names(listOf("takecomic.jp")),
        )
        assertEquals(listOf("album someone"), names(listOf("x.com")))
    }

    @Test
    fun titleListsEpisodesInReadingOrder() {
        assertEquals(
            listOf("album 1話", "album 2話", "album 10話"),
            names(listOf("takecomic.jp", "メイドインアビス")),
        )
    }

    @Test
    fun folderSummarisesWhatIsInside() {
        val site =
            LibraryTree.albums
                .children(albums, emptyList(), LibrarySort.IMAGES)
                .filterIsInstance<TreeNode.Folder<Album>>()
                .first { it.name == "takecomic.jp" }
        // メイドインアビス, 別の漫画 and イラスト
        assertEquals(3, site.entries)
        assertEquals(30L, site.modified)
        assertEquals(File("/d/getjmanga/takecomic.jp/メイドインアビス/10話/0.jpg"), site.cover)
        assertEquals(
            setOf(File("/d/getjmanga/takecomic.jp"), File("/d/gallery-dl/takecomic.jp")),
            site.dirs.toSet(),
        )
    }

    private fun video(path: String, modified: Long) =
        MediaFile(File("/d/yt-dlp", path), path.split("/"), modified, size = 0)

    private val videos =
        listOf(
            video("youtube.com/old [a].mp4", 1),
            video("youtube.com/new [b].mp4", 3),
            video("youtube.com/Some playlist/002 second [d].mp4", 5),
            video("youtube.com/Some playlist/001 first [c].mp4", 6),
            video("nicovideo.jp/clip [e].mp4", 2),
        )

    private fun videoNames(path: List<String>, sort: LibrarySort = LibrarySort.MEDIA) =
        LibraryTree.media.children(videos, path, sort).map {
            (if (it is TreeNode.Folder) "folder " else "video ") + it.name
        }

    @Test
    fun videosGroupBySiteNewestFirst() {
        assertEquals(listOf("folder youtube.com", "folder nicovideo.jp"), videoNames(emptyList()))
        assertEquals(
            listOf("folder Some playlist", "video new [b].mp4", "video old [a].mp4"),
            videoNames(listOf("youtube.com")),
        )
    }

    @Test
    fun playlistGoesInOrderByName() {
        assertEquals(
            listOf("video 001 first [c].mp4", "video 002 second [d].mp4"),
            videoNames(listOf("youtube.com", "Some playlist"), LibrarySort(SortKey.NAME, false)),
        )
    }

    @Test
    fun episodesSortBySizeEitherWay() {
        val title = listOf("takecomic.jp", "メイドインアビス")
        assertEquals(
            listOf("album 10話", "album 1話", "album 2話"),
            names(title, LibrarySort(SortKey.SIZE, descending = false)),
        )
        assertEquals(
            listOf("album 2話", "album 1話", "album 10話"),
            names(title, LibrarySort(SortKey.SIZE, descending = true)),
        )
        assertEquals(
            listOf("album 10話", "album 2話", "album 1話"),
            names(title, LibrarySort(SortKey.EPISODE, descending = true)),
        )
    }

    @Test
    fun foldersSortByTheSameKeyButStayFirst() {
        assertEquals(
            listOf("folder 別の漫画", "folder メイドインアビス", "album イラスト"),
            names(listOf("takecomic.jp"), LibrarySort(SortKey.MODIFIED, descending = false)),
        )
        assertEquals(
            listOf("folder x.com", "folder takecomic.jp", "folder shonenjumpplus.com"),
            names(emptyList(), LibrarySort(SortKey.NAME, descending = true)),
        )
    }
}
