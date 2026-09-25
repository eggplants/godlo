package io.github.eggplants.godlo

import io.github.eggplants.godlo.core.ConfigFile
import io.github.eggplants.godlo.ui.settings.PathStep
import io.github.eggplants.godlo.ui.settings.Refusal
import io.github.eggplants.godlo.ui.settings.parseConfigJson
import io.github.eggplants.godlo.ui.settings.refusal
import io.github.eggplants.godlo.ui.settings.refusedIn
import io.github.eggplants.godlo.ui.settings.settingName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigRulesTest {
    private fun keys(vararg names: String) = names.map { PathStep.Key(it) }

    @Test
    fun galleryDlLocations() {
        val file = ConfigFile.GALLERY_DL
        assertEquals(Refusal.LOCATION, file.refusal(keys("base-directory")))
        assertEquals(Refusal.LOCATION, file.refusal(keys("extractor", "base-directory")))
        assertEquals(Refusal.LOCATION, file.refusal(keys("extractor", "pixiv", "directory")))
        assertEquals(
            Refusal.LOCATION,
            file.refusal(keys("extractor", "pixiv", "user", "base-directory"))
        )
        // A postprocessor's directory is inside the download's own.
        assertNull(file.refusal(keys("postprocessor", "zip", "directory")))
        assertNull(file.refusal(keys("extractor", "pixiv", "filename")))
        assertNull(
            file.refusal(
                listOf(PathStep.Key("postprocessors"), PathStep.Index(0), PathStep.Key("directory"))
            )
        )
    }

    @Test
    fun galleryDlOthers() {
        val file = ConfigFile.GALLERY_DL
        assertEquals(Refusal.OVERRIDDEN, file.refusal(keys("output", "mode")))
        assertEquals(Refusal.OVERRIDDEN, file.refusal(keys("output", "progress")))
        assertNull(file.refusal(keys("output", "log")))
        assertEquals(Refusal.UNCHECKED, file.refusal(keys("subconfigs")))
    }

    @Test
    fun getjmanga() {
        val file = ConfigFile.GETJMANGA
        assertEquals(Refusal.LOCATION, file.refusal(keys("savedir")))
        for (key in listOf("format", "bulk", "both", "cbz")) {
            assertEquals(key, Refusal.OVERRIDDEN, file.refusal(keys(key)))
        }
        assertNull(file.refusal(keys("overwrite")))
        assertNull(file.refusal(keys("metadata")))
        // Only at the top: a site may well have a key called format.
        assertNull(file.refusal(keys("site", "x.com", "format")))
    }

    @Test
    fun otherFilesHaveNoTreeRules() {
        assertNull(ConfigFile.YTDLP.refusal(keys("savedir")))
        assertNull(ConfigFile.COOKIES.refusal(keys("directory")))
    }

    @Test
    fun refusedInListsEachOnceAndNotWhatIsInside() {
        val tree = parseConfigJson(
            """
            {"subconfigs": [{"directory": 1}],
             "extractor": {"base-directory": "/x", "pixiv": {"directory": ["a"], "ugoira": true}},
             "output": {"mode": "terminal", "log": "x"}}
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "subconfigs" to Refusal.UNCHECKED,
                "extractor.base-directory" to Refusal.LOCATION,
                "extractor.pixiv.directory" to Refusal.LOCATION,
                "output.mode" to Refusal.OVERRIDDEN
            ),
            ConfigFile.GALLERY_DL.refusedIn(tree).map { (path, refusal) ->
                settingName(path) to
                    refusal
            }
        )
    }

    @Test
    fun settingNamesPutIndexesInBrackets() {
        assertEquals(
            "patrol[1].url",
            settingName(listOf(PathStep.Key("patrol"), PathStep.Index(1), PathStep.Key("url")))
        )
    }
}
