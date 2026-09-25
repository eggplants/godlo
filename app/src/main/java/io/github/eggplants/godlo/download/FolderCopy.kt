package io.github.eggplants.godlo.download

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

/**
 * Copies what a download saved under its tool's directory into a folder picked with the system's
 * folder picker, keeping the same `<site>/...` layout there. For folders with no file path the
 * tools could write to, such as a NAS through an SMB documents provider.
 */
class FolderCopy(private val resolver: ContentResolver, private val tree: Uri) {
    /** Document URIs of the folders made or found so far, by their path under the tree. */
    private val folders = mutableMapOf<String, Uri>()

    /**
     * Copies [files] and calls [progress] with the count done so far and the total. Throws
     * [IOException] when the provider refuses something, e.g. the NAS is out of reach.
     */
    fun copy(files: List<Pair<File, String>>, progress: (Int, Int) -> Unit) {
        files.forEachIndexed { i, (file, relative) ->
            progress(i, files.size)
            val parent = folder(relative.substringBeforeLast('/', ""))
            val name = relative.substringAfterLast('/')
            val target = child(parent, name) ?: DocumentsContract.createDocument(
                resolver,
                parent,
                mimeType(name),
                name
            ) ?: throw IOException("cannot create $relative")
            val output = resolver.openOutputStream(target, "wt")
                ?: throw IOException("cannot write $relative")
            output.use { out -> file.inputStream().use { it.copyTo(out) } }
        }
        progress(files.size, files.size)
    }

    /** The folder at [path] under the tree ("" for the tree itself), made if missing. */
    private fun folder(path: String): Uri {
        folders[path]?.let { return it }
        val uri = if (path.isEmpty()) {
            DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree)
            )
        } else {
            val parent = folder(path.substringBeforeLast('/', ""))
            val name = path.substringAfterLast('/')
            child(parent, name) ?: DocumentsContract.createDocument(
                resolver,
                parent,
                Document.MIME_TYPE_DIR,
                name
            ) ?: throw IOException("cannot create $path")
        }
        folders[path] = uri
        return uri
    }

    /** The document named [name] in the folder [parent], if there is one. */
    private fun child(parent: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getDocumentId(parent)
        )
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        }
        return null
    }

    private fun mimeType(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            name.substringAfterLast('.', "").lowercase()
        ) ?: "application/octet-stream"

    companion object {
        /**
         * The files under [paths], which may be directories, each with its path relative to
         * [root]. Paths outside [root] are left out: they have no place in the copy.
         */
        fun plan(root: File, paths: List<String>): List<Pair<File, String>> = paths
            .map(::File)
            .flatMap {
                if (it.isDirectory) it.walkTopDown().filter(File::isFile).toList() else listOf(it)
            }
            .filter { it.isFile }
            .distinct()
            .mapNotNull { file ->
                val relative = file.relativeToOrNull(root)?.invariantSeparatorsPath
                relative?.takeIf { it.isNotEmpty() && !it.startsWith("..") }?.let { file to it }
            }
    }
}
