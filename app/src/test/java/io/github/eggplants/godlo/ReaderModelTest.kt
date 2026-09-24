package io.github.eggplants.godlo

import io.github.eggplants.godlo.library.NaturalOrder
import io.github.eggplants.godlo.ui.reader.Page
import io.github.eggplants.godlo.ui.reader.ReaderModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderModelTest {
    private fun pages(vararg wide: Boolean) = wide.mapIndexed { i, w ->
        Page(File("$i.jpg"), if (w) 2000 else 1000, 1400)
    }

    @Test
    fun singlePagesWhenSpreadIsOff() {
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2)),
            ReaderModel.spreads(pages(false, false, false), false, true)
        )
    }

    @Test
    fun coverStandsAloneThenPagesPair() {
        val spreads = ReaderModel.spreads(pages(false, false, false, false, false), true, true)
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), spreads)
    }

    @Test
    fun withoutCoverAlonePagesPairFromTheFirst() {
        val spreads = ReaderModel.spreads(pages(false, false, false, false, false), true, false)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), spreads)
    }

    @Test
    fun widePagesStandAlone() {
        val spreads = ReaderModel.spreads(pages(false, false, true, false, false), true, false)
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3, 4)), spreads)
        // A page whose partner is wide is not paired with it.
        val shifted = ReaderModel.spreads(pages(false, true, false), true, false)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), shifted)
    }

    @Test
    fun naturalOrderSortsNumbersByValue() {
        val names = listOf("10.jpg", "2.jpg", "1.jpg", "010.jpg", "a2.png", "a10.png")
        assertEquals(
            listOf("1.jpg", "2.jpg", "10.jpg", "010.jpg", "a2.png", "a10.png"),
            names.sortedWith(NaturalOrder)
        )
    }
}
