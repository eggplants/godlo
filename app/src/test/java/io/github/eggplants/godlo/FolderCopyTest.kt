package io.github.eggplants.godlo

import io.github.eggplants.godlo.download.FolderCopy
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderCopyTest {
    @Test
    fun plan() {
        val root = Files.createTempDirectory("godlo").toFile()
        val episode = File(root, "site/Work/01").apply { mkdirs() }
        File(episode, "001.jpg").writeText("a")
        File(episode, "002.jpg").writeText("b")
        val video = File(root, "youtube.com/v.mp4").apply {
            parentFile!!.mkdirs()
            writeText("c")
        }
        val outside = Files.createTempFile("godlo", ".mp4").toFile()

        val plan = FolderCopy.plan(
            root,
            listOf(episode.path, video.path, video.path, outside.path, "/missing")
        )

        assertEquals(
            listOf("site/Work/01/001.jpg", "site/Work/01/002.jpg", "youtube.com/v.mp4"),
            plan.map { it.second }.sorted()
        )
        root.deleteRecursively()
        outside.delete()
    }
}
