package io.github.eggplants.godlo.ui.settings

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** One step down a config tree: a key of an object, or an index into an array. */
sealed interface PathStep {
    data class Key(val name: String) : PathStep

    data class Index(val index: Int) : PathStep
}

/** What a value in a config tree is, as the visual editor offers them. */
enum class ValueType {
    STRING,
    NUMBER,
    BOOLEAN,
    OBJECT,
    ARRAY,
    NULL;

    val container: Boolean get() = this == OBJECT || this == ARRAY

    /** What a new value of this type starts as. */
    fun empty(): JsonElement = when (this) {
        STRING -> JsonPrimitive("")
        NUMBER -> JsonPrimitive(0)
        BOOLEAN -> JsonPrimitive(false)
        OBJECT -> JsonObject(emptyMap())
        ARRAY -> JsonArray(emptyList())
        NULL -> JsonNull
    }
}

val JsonElement.valueType: ValueType
    get() = when (this) {
        is JsonObject -> ValueType.OBJECT

        is JsonArray -> ValueType.ARRAY

        JsonNull -> ValueType.NULL

        is JsonPrimitive -> when {
            isString -> ValueType.STRING
            booleanOrNull != null -> ValueType.BOOLEAN
            else -> ValueType.NUMBER
        }
    }

/**
 * The value [type] holds when written as [text]: null for a number that is not one.
 *
 * Containers start empty; [text] matters only for strings, numbers and booleans.
 */
fun scalarOf(type: ValueType, text: String): JsonElement? = when (type) {
    ValueType.STRING -> JsonPrimitive(text)

    ValueType.NUMBER -> text.trim().let {
        it.toLongOrNull()
            ?: it.toDoubleOrNull()?.takeIf { d -> d.isFinite() }
    }
        ?.let(::JsonPrimitive)

    ValueType.BOOLEAN -> JsonPrimitive(text == "true")

    else -> type.empty()
}

/** What an edit field starts with for a string, number or boolean. */
val JsonElement.editText: String
    get() = (this as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content ?: ""

/** The value at [path], or null when there is none. */
fun JsonElement.at(path: List<PathStep>): JsonElement? =
    path.fold(this as JsonElement?) { node, step ->
        when (step) {
            is PathStep.Key -> (node as? JsonObject)?.get(step.name)
            is PathStep.Index -> (node as? JsonArray)?.getOrNull(step.index)
        }
    }

/** This tree with the value at [path] made [transform] of it. */
fun JsonElement.update(path: List<PathStep>, transform: (JsonElement) -> JsonElement): JsonElement {
    if (path.isEmpty()) return transform(this)
    val rest = path.drop(1)
    return when (val step = path.first()) {
        is PathStep.Key -> {
            val obj = this as? JsonObject ?: throw IllegalArgumentException("not an object")
            val child = obj[step.name] ?: throw IllegalArgumentException("no ${step.name}")
            JsonObject(
                obj.mapValues { (key, value) ->
                    if (key ==
                        step.name
                    ) {
                        child.update(rest, transform)
                    } else {
                        value
                    }
                }
            )
        }

        is PathStep.Index -> {
            val array = this as? JsonArray ?: throw IllegalArgumentException("not an array")
            require(step.index in array.indices) { "no [${step.index}]" }
            JsonArray(
                array.mapIndexed { i, value ->
                    if (i ==
                        step.index
                    ) {
                        value.update(rest, transform)
                    } else {
                        value
                    }
                }
            )
        }
    }
}

/** This tree without the value at [path]. */
fun JsonElement.remove(path: List<PathStep>): JsonElement {
    require(path.isNotEmpty()) { "cannot remove the root" }
    return update(path.dropLast(1)) { parent ->
        when (val step = path.last()) {
            is PathStep.Key -> JsonObject((parent as JsonObject) - step.name)

            is PathStep.Index -> JsonArray(
                (parent as JsonArray).filterIndexed { i, _ ->
                    i !=
                        step.index
                }
            )
        }
    }
}

/**
 * This tree with [value] added to the container at [path]: under [key] in an object, keeping
 * the order of the others, and at the end of an array, where [key] is not used.
 *
 * @throws IllegalArgumentException when the object has [key] already.
 */
fun JsonElement.add(path: List<PathStep>, key: String, value: JsonElement): JsonElement =
    update(path) { parent ->
        when (parent) {
            is JsonObject -> {
                require(key !in parent) { key }
                JsonObject(parent + (key to value))
            }

            is JsonArray -> JsonArray(parent + value)

            else -> throw IllegalArgumentException("not a container")
        }
    }

/**
 * This tree with the key at [path] renamed to [name], in the same place among its siblings.
 *
 * @throws IllegalArgumentException when a sibling is called [name] already.
 */
fun JsonElement.rename(path: List<PathStep>, name: String): JsonElement {
    val old = (path.last() as PathStep.Key).name
    if (old == name) return this
    return update(path.dropLast(1)) { parent ->
        val obj = parent as JsonObject
        require(name !in obj) { name }
        JsonObject(
            obj.entries.associate { (key, value) ->
                (
                    if (key ==
                        old
                    ) {
                        name
                    } else {
                        key
                    }
                    ) to value
            }
        )
    }
}

/** This tree with the array item at [path] moved [by] places, staying inside the array. */
fun JsonElement.move(path: List<PathStep>, by: Int): JsonElement {
    val from = (path.last() as PathStep.Index).index
    return update(path.dropLast(1)) { parent ->
        val items = (parent as JsonArray).toMutableList()
        val to = (from + by).coerceIn(items.indices)
        items.add(to, items.removeAt(from))
        JsonArray(items)
    }
}

/** The longest start of [path] that still leads to an object or array. */
fun JsonElement.validPrefix(path: List<PathStep>): List<PathStep> {
    var node: JsonElement = this
    path.forEachIndexed { i, step ->
        val next = node.at(listOf(step))
        if (next == null || !next.valueType.container) return path.take(i)
        node = next
    }
    return path
}

@OptIn(ExperimentalSerializationApi::class)
private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "    "
}

/** [text] as a JSON config: an empty file is an empty object. */
fun parseConfigJson(text: String): JsonElement =
    if (text.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(text)

/** [tree] as gallery-dl.conf is usually written: four spaces, and a newline at the end. */
fun formatConfigJson(tree: JsonElement): String =
    prettyJson.encodeToString(JsonElement.serializer(), tree) + "\n"
