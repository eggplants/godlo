package io.github.eggplants.godlo.core

/**
 * Between the folders the system's folder picker hands back, as a document ID of a provider,
 * and the plain paths the tools write to. Only folders on the device's own storage have one.
 */
object FolderPath {
    /** The provider for internal storage and SD cards, with IDs such as `primary:Download/x`. */
    const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    /** The Downloads provider, whose IDs are a path only when they start with `raw:`. */
    const val DOWNLOADS = "com.android.providers.downloads.documents"

    /**
     * The path of the folder [documentId] of [authority], or null when it is not on storage the
     * tools can write to. [primaryRoot] is internal storage, e.g. `/storage/emulated/0`.
     */
    fun toPath(authority: String, documentId: String, primaryRoot: String): String? =
        when (authority) {
            EXTERNAL_STORAGE -> {
                val volume = documentId.substringBefore(':')
                val relative = documentId.substringAfter(':', "").trim('/')
                val root = when (volume) {
                    "" -> null
                    "primary" -> primaryRoot
                    "home" -> "$primaryRoot/Documents"
                    else -> "/storage/$volume"
                }
                root?.let { if (relative.isEmpty()) it else "$it/$relative" }
            }

            DOWNLOADS ->
                documentId.removePrefix("raw:").takeIf {
                    documentId.startsWith("raw:") && it.startsWith("/")
                }?.trimEnd('/')

            else -> null
        }

    /**
     * The [EXTERNAL_STORAGE] document ID of [path], for opening the picker there, or null when
     * [path] is on no storage volume.
     */
    fun toDocumentId(path: String, primaryRoot: String): String? {
        val clean = path.trimEnd('/')
        if (clean == primaryRoot || clean.startsWith("$primaryRoot/")) {
            return "primary:" + clean.removePrefix(primaryRoot).trimStart('/')
        }
        val volume = clean.removePrefix("/storage/").substringBefore('/')
        if (!clean.startsWith("/storage/") || volume.isEmpty() || volume == "emulated") return null
        return "$volume:" + clean.removePrefix("/storage/$volume").trimStart('/')
    }
}
