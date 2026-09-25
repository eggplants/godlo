package io.github.eggplants.godlo

import io.github.eggplants.godlo.core.SharedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextTest {
    @Test
    fun aBareLink() {
        assertEquals("https://x.com/a/status/1", SharedText.url("https://x.com/a/status/1"))
    }

    @Test
    fun aLinkAfterATitle() {
        assertEquals(
            "https://www.youtube.com/watch?v=jNQXAC9IVRw",
            SharedText.url("Me at the zoo https://www.youtube.com/watch?v=jNQXAC9IVRw")
        )
    }

    @Test
    fun chromesQuoteOfAPassage() {
        assertEquals(
            "https://example.com/#:~:text=This%20domain",
            SharedText.url("\"This domain is for use\"\nhttps://example.com/#:~:text=This%20domain")
        )
    }

    @Test
    fun japaneseTextRightAfterTheLink() {
        assertEquals(
            "https://shonenjumpplus.com/episode/1",
            SharedText.url("最新話（https://shonenjumpplus.com/episode/1）を読んだ")
        )
        assertEquals("https://example.com/a", SharedText.url("ここ https://example.com/a、です。"))
        assertEquals("https://example.com/a", SharedText.url("See https://example.com/a."))
    }

    @Test
    fun noLink() {
        assertNull(SharedText.url("just words"))
        assertNull(SharedText.url(null))
    }
}
