package io.github.eggplants.godlo

import io.github.eggplants.godlo.ui.settings.PathStep
import io.github.eggplants.godlo.ui.settings.ValueType
import io.github.eggplants.godlo.ui.settings.add
import io.github.eggplants.godlo.ui.settings.at
import io.github.eggplants.godlo.ui.settings.formatConfigJson
import io.github.eggplants.godlo.ui.settings.move
import io.github.eggplants.godlo.ui.settings.parseConfigJson
import io.github.eggplants.godlo.ui.settings.remove
import io.github.eggplants.godlo.ui.settings.rename
import io.github.eggplants.godlo.ui.settings.scalarOf
import io.github.eggplants.godlo.ui.settings.update
import io.github.eggplants.godlo.ui.settings.validPrefix
import io.github.eggplants.godlo.ui.settings.valueType
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigTreeTest {
    private val tree = parseConfigJson(
        """{"extractor": {"pixiv": {"ugoira": true}, "skip": "abort:3"}, "list": [1, 2.5, null]}"""
    )
    private val extractor = PathStep.Key("extractor")
    private val list = PathStep.Key("list")

    @Test
    fun typesOfValues() {
        assertEquals(ValueType.OBJECT, tree.valueType)
        assertEquals(ValueType.ARRAY, tree.at(listOf(list))!!.valueType)
        assertEquals(ValueType.NUMBER, tree.at(listOf(list, PathStep.Index(1)))!!.valueType)
        assertEquals(ValueType.NULL, tree.at(listOf(list, PathStep.Index(2)))!!.valueType)
        assertEquals(
            ValueType.BOOLEAN,
            tree.at(listOf(extractor, PathStep.Key("pixiv"), PathStep.Key("ugoira")))!!.valueType
        )
        assertEquals(
            ValueType.STRING,
            tree.at(listOf(extractor, PathStep.Key("skip")))!!.valueType
        )
        assertNull(tree.at(listOf(PathStep.Key("missing"))))
    }

    @Test
    fun emptyFileIsAnEmptyObject() {
        assertEquals(ValueType.OBJECT, parseConfigJson("  \n").valueType)
    }

    @Test
    fun numbersAreCheckedWhenTyped() {
        assertEquals(JsonPrimitive(3), scalarOf(ValueType.NUMBER, " 3 "))
        assertEquals(JsonPrimitive(0.5), scalarOf(ValueType.NUMBER, "0.5"))
        assertNull(scalarOf(ValueType.NUMBER, "three"))
        assertNull(scalarOf(ValueType.NUMBER, "NaN"))
        assertEquals(JsonPrimitive("3"), scalarOf(ValueType.STRING, "3"))
    }

    @Test
    fun updateReplacesOnlyThePathGiven() {
        val path = listOf(extractor, PathStep.Key("skip"))
        val changed = tree.update(path) { JsonPrimitive(false) }
        assertEquals(JsonPrimitive(false), changed.at(path))
        assertEquals(tree.at(listOf(list)), changed.at(listOf(list)))
    }

    @Test
    fun addKeepsTheOrderAndAppends() {
        val added = tree.add(listOf(extractor), "base-directory", JsonPrimitive("/x"))
        val keys = (added.at(listOf(extractor)) as Map<*, *>).keys.toList()
        assertEquals(listOf("pixiv", "skip", "base-directory"), keys)
        val appended = tree.add(listOf(list), "ignored", JsonPrimitive(4))
        assertEquals(JsonPrimitive(4), appended.at(listOf(list, PathStep.Index(3))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun addRefusesAKeyThatIsThere() {
        tree.add(listOf(extractor), "skip", JsonPrimitive(1))
    }

    @Test
    fun renameKeepsThePlace() {
        val renamed = tree.rename(listOf(extractor, PathStep.Key("pixiv")), "twitter")
        val keys = (renamed.at(listOf(extractor)) as Map<*, *>).keys.toList()
        assertEquals(listOf("twitter", "skip"), keys)
    }

    @Test
    fun removeAndMoveInArrays() {
        val removed = tree.remove(listOf(list, PathStep.Index(0)))
        assertEquals(JsonPrimitive(2.5), removed.at(listOf(list, PathStep.Index(0))))
        val moved = tree.move(listOf(list, PathStep.Index(0)), 1)
        assertEquals(JsonPrimitive(2.5), moved.at(listOf(list, PathStep.Index(0))))
        assertEquals(JsonPrimitive(1), moved.at(listOf(list, PathStep.Index(1))))
        // Past the end stays at the end.
        val last = tree.move(listOf(list, PathStep.Index(2)), 1)
        assertEquals(tree, last)
    }

    @Test
    fun validPrefixDropsWhatIsGone() {
        val path = listOf(extractor, PathStep.Key("pixiv"))
        assertEquals(path, tree.validPrefix(path))
        val removed = tree.remove(path)
        assertEquals(listOf(extractor), removed.validPrefix(path))
        // A value that is not a container is not somewhere to be.
        assertEquals(listOf(extractor), tree.validPrefix(listOf(extractor, PathStep.Key("skip"))))
    }

    @Test
    fun formatsWithFourSpacesAndKeepsBigNumbers() {
        val text = formatConfigJson(parseConfigJson("""{"a": [1, 1.5, 12345678901234567890]}"""))
        assertEquals(
            "{\n    \"a\": [\n        1,\n        1.5,\n        12345678901234567890\n    ]\n}\n",
            text
        )
    }
}
