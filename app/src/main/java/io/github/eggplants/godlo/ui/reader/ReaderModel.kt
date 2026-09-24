package io.github.eggplants.godlo.ui.reader

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.content.edit
import io.github.eggplants.godlo.library.IMAGE_EXTENSIONS
import io.github.eggplants.godlo.library.NaturalOrder
import java.io.File

data class Page(val file: File, val width: Int, val height: Int) {
    /** A page drawn across two pages, which a spread shows alone. */
    val wide: Boolean get() = width > height * 1.1f
    val aspect: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 0.7f
}

data class Chapter(val pages: List<Page>, val previous: File?, val next: File?)

object ReaderModel {
    fun load(dir: File): Chapter {
        val pages = dir.listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS }
            .orEmpty()
            .sortedWith(NaturalOrder.files)
            .map { file ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                Page(file, options.outWidth, options.outHeight)
            }
        val siblings = dir.parentFile?.listFiles { f ->
            f.isDirectory && !f.name.startsWith(".") &&
                f.listFiles()?.any { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS } ==
                true
        }.orEmpty().sortedWith(NaturalOrder.files)
        val index = siblings.indexOfFirst { it.absolutePath == dir.absolutePath }
        return Chapter(
            pages = pages,
            previous = siblings.getOrNull(index - 1).takeIf { index > 0 },
            next = siblings.getOrNull(index + 1).takeIf { index >= 0 }
        )
    }

    /**
     * Group pages into what one screen shows: one page each, or two side by side.
     *
     * With [coverAlone], page 0 stands alone the way a book opens on its cover, so the
     * following pages pair up as printed. A wide page, and a page whose partner would be
     * wide, stands alone too.
     */
    fun spreads(pages: List<Page>, spread: Boolean, coverAlone: Boolean): List<List<Int>> {
        if (!spread) return pages.indices.map { listOf(it) }
        val out = mutableListOf<List<Int>>()
        var i = 0
        if (coverAlone && pages.isNotEmpty()) {
            out += listOf(0)
            i = 1
        }
        while (i < pages.size) {
            if (pages[i].wide || i == pages.lastIndex || pages[i + 1].wide) {
                out += listOf(i)
                i += 1
            } else {
                out += listOf(i, i + 1)
                i += 2
            }
        }
        return out
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("reader", Context.MODE_PRIVATE)

    fun lastPage(context: Context, dir: File): Int = prefs(context).getInt(dir.absolutePath, 0)

    fun saveLastPage(context: Context, dir: File, page: Int) = prefs(context).edit {
        putInt(dir.absolutePath, page)
    }
}
