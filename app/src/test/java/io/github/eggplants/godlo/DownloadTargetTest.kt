package io.github.eggplants.godlo

import io.github.eggplants.godlo.core.Engine
import io.github.eggplants.godlo.core.MediaKind
import io.github.eggplants.godlo.download.DownloadTarget
import io.github.eggplants.godlo.download.DownloadTask
import io.github.eggplants.godlo.download.TaskState
import io.github.eggplants.godlo.library.Album
import io.github.eggplants.godlo.library.Library
import io.github.eggplants.godlo.library.MediaFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadTargetTest {
    private val manga = "/d/getjmanga"

    private fun album(path: String) = File(manga, path).let {
        Album(it, path.split("/"), File(it, "0.jpg"), count = 31, modified = 0)
    }

    private fun media(path: String) =
        MediaFile(File(path), listOf("youtube.com", File(path).name), 0, 0)

    private val library = Library(
        albums = listOf(
            album("takecomic.jp/メイドインアビス/1話"),
            album("takecomic.jp/メイドインアビス/2話"),
            album("takecomic.jp/メイドインアビス/Vol.3"),
            album("x.com/someone")
        ),
        audio = listOf(media("/d/yt-dlp/youtube.com/a.mp3")),
        video = listOf(media("/d/yt-dlp/youtube.com/v.mp4")),
        loading = false
    )

    private fun task(kind: MediaKind, vararg files: String, state: TaskState = TaskState.DONE) =
        DownloadTask(
            id = 1,
            url = "https://example.com",
            engine = Engine.YTDLP,
            kind = kind,
            site = "",
            playlist = false,
            videoQuality = "1080",
            audioFormat = "mp3",
            state = state,
            files = files.toList()
        )

    @Test
    fun oneEpisodeOpensTheReader() {
        assertEquals(
            DownloadTarget.Album(
                File(manga, "takecomic.jp/メイドインアビス/Vol.3"),
                listOf("takecomic.jp", "メイドインアビス")
            ),
            DownloadTarget.of(task(MediaKind.IMAGE, "$manga/takecomic.jp/メイドインアビス/Vol.3"), library)
        )
    }

    @Test
    fun galleryPicturesOpenTheirAlbum() {
        assertEquals(
            DownloadTarget.Album(File(manga, "x.com/someone"), listOf("x.com")),
            DownloadTarget.of(
                task(MediaKind.IMAGE, "$manga/x.com/someone/1.jpg", "$manga/x.com/someone/2.jpg"),
                library
            )
        )
    }

    @Test
    fun severalEpisodesOpenTheirTitle() {
        val target = DownloadTarget.of(
            task(
                MediaKind.IMAGE,
                "$manga/takecomic.jp/メイドインアビス/1話",
                "$manga/takecomic.jp/メイドインアビス/2話"
            ),
            library
        )
        assertEquals(DownloadTarget.ImageFolder(listOf("takecomic.jp", "メイドインアビス")), target)
    }

    @Test
    fun audioAndVideoPlay() {
        assertEquals(
            DownloadTarget.Audio(
                listOf(File("/d/yt-dlp/youtube.com/a.mp3")),
                listOf("youtube.com")
            ),
            DownloadTarget.of(task(MediaKind.AUDIO, "/d/yt-dlp/youtube.com/a.mp3"), library)
        )
        assertEquals(
            DownloadTarget.Video(File("/d/yt-dlp/youtube.com/v.mp4"), listOf("youtube.com")),
            DownloadTarget.of(task(MediaKind.VIDEO, "/d/yt-dlp/youtube.com/v.mp4"), library)
        )
    }

    @Test
    fun nothingToOpenWhenDeletedOrUnfinished() {
        assertNull(
            DownloadTarget.of(task(MediaKind.VIDEO, "/d/yt-dlp/youtube.com/gone.mp4"), library)
        )
        assertNull(
            DownloadTarget.of(
                task(MediaKind.VIDEO, "/d/yt-dlp/youtube.com/v.mp4", state = TaskState.FAILED),
                library
            )
        )
    }

    @Test
    fun aPlaylistPlaysOverItsFolder() {
        fun track(name: String) = MediaFile(
            File("/d/yt-dlp/youtube.com/Mix/$name"),
            listOf("youtube.com", "Mix", name),
            0,
            0
        )
        val tracks = listOf(track("001 a.mp3"), track("002 b.mp3"))
        val target = DownloadTarget.of(
            task(MediaKind.AUDIO, *tracks.map { it.file.path }.toTypedArray()),
            library.copy(audio = tracks)
        )
        assertEquals(
            DownloadTarget.Audio(tracks.map { it.file }, listOf("youtube.com", "Mix")),
            target
        )
    }
}
