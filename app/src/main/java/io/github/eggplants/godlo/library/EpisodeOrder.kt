package io.github.eggplants.godlo.library

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What getjmanga's `--metadata` writes next to an episode's pages. */
const val METADATA_FILE = "metadata.json"

/**
 * The episode's place in its series, from getjmanga's `metadata.json`: the site's own count,
 * from 1. Null when there is no such file, or the site does not number its episodes.
 */
fun episodeNumber(dir: File): Int? =
    File(dir, METADATA_FILE)
        .takeIf { it.isFile }
        ?.let {
            runCatching { episodeNumber(it.readText()) }.getOrNull()
        }

/** The top-level `number` of a `metadata.json`, not those in the site's own metadata inside it. */
fun episodeNumber(metadata: String): Int? {
    // Not JSON, not an object, or a `number` that is not a plain value.
    val number =
        try {
            Json.parseToJsonElement(metadata).jsonObject["number"]?.jsonPrimitive?.intOrNull
        } catch (_: IllegalArgumentException) {
            null
        }
    return number?.takeIf { it > 0 }
}

/**
 * Episodes in reading order: by their numbers when every one has one, else by name, so 2話 comes
 * before 10話. A number and a name cannot be compared, so a level with an episode saved without
 * metadata goes by name alone.
 */
fun <T> inReadingOrder(items: List<T>, name: (T) -> String, number: (T) -> Int?): List<T> =
    if (items.isNotEmpty() && items.all { number(it) != null }) {
        items.sortedWith(compareBy<T> { number(it) }.thenBy(NaturalOrder, name))
    } else {
        items.sortedWith(compareBy(NaturalOrder, name))
    }
