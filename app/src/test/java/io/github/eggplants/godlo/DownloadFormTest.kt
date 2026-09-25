package io.github.eggplants.godlo

import io.github.eggplants.godlo.core.Engine
import io.github.eggplants.godlo.core.MediaKind
import io.github.eggplants.godlo.ui.download.DownloadForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFormTest {
    @Test
    fun anythingGoesBeforeDetection() {
        val form = DownloadForm()
        Engine.entries.forEach { assertTrue(form.allows(it)) }
        MediaKind.entries.forEach { assertTrue(form.allows(it)) }
    }

    @Test
    fun onlyTheToolsThatTakeTheUrl() {
        // A pixiv page: gallery-dl only, so pictures only.
        val form = DownloadForm(engine = Engine.GALLERY_DL, supported = listOf(Engine.GALLERY_DL))
        assertTrue(form.allows(Engine.GALLERY_DL))
        assertFalse(form.allows(Engine.YTDLP))
        assertFalse(form.allows(Engine.GETJMANGA))
        assertTrue(form.allows(MediaKind.IMAGE))
        assertFalse(form.allows(MediaKind.VIDEO))
        assertFalse(form.allows(MediaKind.AUDIO))
    }

    @Test
    fun switchingKindPicksATool() {
        // An x.com post: pictures with gallery-dl, or the video with yt-dlp.
        val form = DownloadForm(
            engine = Engine.GALLERY_DL,
            kind = MediaKind.IMAGE,
            supported = listOf(Engine.GALLERY_DL, Engine.YTDLP)
        )
        assertEquals(Engine.YTDLP, form.engineFor(MediaKind.VIDEO))
        assertEquals(Engine.GALLERY_DL, form.engineFor(MediaKind.IMAGE))
        // A manga site: getjmanga, not the gallery-dl a kind switch would otherwise pick.
        val manga = DownloadForm(
            engine = Engine.YTDLP,
            supported = listOf(Engine.GETJMANGA, Engine.YTDLP)
        )
        assertEquals(Engine.GETJMANGA, manga.engineFor(MediaKind.IMAGE))
    }
}
