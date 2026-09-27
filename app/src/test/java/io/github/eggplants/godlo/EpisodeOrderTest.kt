package io.github.eggplants.godlo

import io.github.eggplants.godlo.library.Album
import io.github.eggplants.godlo.library.LibraryTree
import io.github.eggplants.godlo.library.episodeNumber
import io.github.eggplants.godlo.library.inReadingOrder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeOrderTest {
    @Test
    fun numberIsTheTopLevelOne() {
        // As getjmanga writes it: the site's own metadata, with numbers of its own, comes first.
        val metadata =
            """
            {
                "url": "https://example.com/episode/2",
                "series_title": "作品",
                "episode_title": "第1話",
                "metadata": {"episode": {"number": 99}},
                "number": 2
            }
            """
        assertEquals(2, episodeNumber(metadata))
    }

    @Test
    fun missingOrUnusableNumbersAreNull() {
        assertNull(episodeNumber("""{"number": null}"""))
        assertNull(episodeNumber("""{"episode_title": "第1話"}"""))
        assertNull(episodeNumber("""{"number": 0}"""))
        assertNull(episodeNumber("""{"number": "2"x}"""))
        assertNull(episodeNumber("not json"))
    }

    private fun order(vararg episodes: Pair<String, Int?>) =
        inReadingOrder(episodes.toList(), { it.first }, { it.second }).map { it.first }

    @Test
    fun numbersWinOverNames() {
        // Titles that do not sort by name: a prologue, then numbered ones.
        assertEquals(
            listOf("プロローグ", "第1話", "第2話", "最終話"),
            order("第2話" to 3, "最終話" to 4, "プロローグ" to 1, "第1話" to 2),
        )
    }

    @Test
    fun anEpisodeWithoutANumberOrdersEveryOneByName() {
        assertEquals(
            listOf("1話", "2話", "10話"),
            order("10話" to 1, "2話" to null, "1話" to 3),
        )
    }

    @Test
    fun imageTabUsesTheNumbers() {
        val albums =
            listOf("第2話" to 3, "プロローグ" to 1, "第1話" to 2).map { (name, number) ->
                val dir = File("/d/getjmanga/example.com/作品", name)
                Album(
                    dir,
                    listOf("example.com", "作品", name),
                    File(dir, "0.jpg"),
                    1,
                    0,
                    number = number,
                )
            }
        assertEquals(
            listOf("プロローグ", "第1話", "第2話"),
            LibraryTree.albums.children(albums, listOf("example.com", "作品")).map { it.name },
        )
    }
}
