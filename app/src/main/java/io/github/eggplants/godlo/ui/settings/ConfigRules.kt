package io.github.eggplants.godlo.ui.settings

import androidx.annotation.StringRes
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.ConfigFile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Why a setting is kept out of a config file. */
enum class Refusal(val id: String, @StringRes val reason: Int) {
    /** The app decides where files go; the library looks for them there. */
    LOCATION("location", R.string.config_refused_location),

    /** The app passes its own value on every run, from its settings or the download form. */
    OVERRIDDEN("overridden", R.string.config_refused_overridden),

    /** It would read other files, which nothing checks. */
    UNCHECKED("unchecked", R.string.config_refused_unchecked),

    /** It does nothing when the app runs the tool. */
    IGNORED("ignored", R.string.config_refused_ignored);

    companion object {
        fun fromId(id: String): Refusal = entries.firstOrNull { it.id == id } ?: IGNORED
    }
}

/** A setting refused in a config file: as written ("-o …", "extractor.pixiv.directory"). */
data class RefusedSetting(val setting: String, val refusal: Refusal)

/**
 * Why the setting at [path] cannot be in this file, or null when it can.
 *
 * Only for the files shown as trees; yt-dlp.conf is checked in `godlo_bridge.py`
 * (`strip_ytdlp_config`), which also drops these settings at run time, as it does for
 * gallery-dl.conf (`_strip_gallery_dl_config`) whatever wrote the file.
 */
fun ConfigFile.refusal(path: List<PathStep>): Refusal? {
    val names = path.map { (it as? PathStep.Key)?.name ?: return null }
    return when (this) {
        ConfigFile.GALLERY_DL -> when {
            names == listOf("subconfigs") -> Refusal.UNCHECKED

            names == listOf("output", "mode") || names == listOf("output", "progress") ->
                Refusal.OVERRIDDEN

            // At the top, and in extractor down to a category's subcategory: gallery-dl reads
            // these there, and one set for a category would win over the app's.
            names.last() in GALLERY_DL_LOCATION &&
                (names.size == 1 || (names.first() == "extractor" && names.size in 2..4)) ->
                Refusal.LOCATION

            else -> null
        }

        // getjmanga is run with -d, -F, --cbz / --no-cbz and one of --bulk, --no-bulk, --both.
        ConfigFile.GETJMANGA -> when {
            names.size != 1 -> null
            names[0] == "savedir" -> Refusal.LOCATION
            names[0] in GETJMANGA_OVERRIDDEN -> Refusal.OVERRIDDEN
            else -> null
        }

        else -> null
    }
}

/** Every setting in [tree] that [refusal] refuses, outermost first, with where it is. */
fun ConfigFile.refusedIn(tree: JsonElement): List<Pair<List<PathStep>, Refusal>> {
    val found = mutableListOf<Pair<List<PathStep>, Refusal>>()
    fun walk(node: JsonElement, path: List<PathStep>) {
        val children = when (node) {
            is JsonObject -> node.map { (key, value) -> PathStep.Key(key) to value }
            is JsonArray -> node.mapIndexed { i, value -> PathStep.Index(i) to value }
            else -> return
        }
        for ((step, child) in children) {
            val here = path + step
            val refused = refusal(here)
            // A refused table goes whole; nothing inside it needs listing.
            if (refused != null) found += here to refused else walk(child, here)
        }
    }
    walk(tree, emptyList())
    return found
}

/** [path] as a dotted name, the way the files' documentation writes settings. */
fun settingName(path: List<PathStep>): String = path.joinToString(".") {
    when (it) {
        is PathStep.Key -> it.name
        is PathStep.Index -> "[${it.index}]"
    }
}.replace(".[", "[")

private val GALLERY_DL_LOCATION = setOf("base-directory", "directory")
private val GETJMANGA_OVERRIDDEN = setOf("format", "bulk", "both", "cbz")
