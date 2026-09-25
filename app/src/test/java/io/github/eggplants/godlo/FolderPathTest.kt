package io.github.eggplants.godlo

import io.github.eggplants.godlo.core.FolderPath
import io.github.eggplants.godlo.core.FolderPath.DOWNLOADS
import io.github.eggplants.godlo.core.FolderPath.EXTERNAL_STORAGE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderPathTest {
    private val primary = "/storage/emulated/0"

    @Test
    fun internalStorage() {
        assertEquals(
            "/storage/emulated/0/Download/Godlo/yt-dlp",
            FolderPath.toPath(EXTERNAL_STORAGE, "primary:Download/Godlo/yt-dlp", primary)
        )
        assertEquals(
            "/storage/emulated/0",
            FolderPath.toPath(EXTERNAL_STORAGE, "primary:", primary)
        )
    }

    @Test
    fun sdCard() {
        assertEquals(
            "/storage/1A2B-3C4D/Movies",
            FolderPath.toPath(EXTERNAL_STORAGE, "1A2B-3C4D:Movies", primary)
        )
    }

    @Test
    fun documentsRoot() {
        assertEquals(
            "/storage/emulated/0/Documents/a",
            FolderPath.toPath(EXTERNAL_STORAGE, "home:a", primary)
        )
    }

    @Test
    fun downloadsProvider() {
        assertEquals(
            "/storage/emulated/0/Download/x",
            FolderPath.toPath(DOWNLOADS, "raw:/storage/emulated/0/Download/x/", primary)
        )
        assertNull(FolderPath.toPath(DOWNLOADS, "msf:123", primary))
        assertNull(FolderPath.toPath(DOWNLOADS, "downloads", primary))
    }

    @Test
    fun otherProviders() {
        assertNull(FolderPath.toPath("com.google.android.apps.docs.storage", "abc", primary))
    }

    @Test
    fun documentIds() {
        assertEquals(
            "primary:Download/Godlo/yt-dlp",
            FolderPath.toDocumentId("/storage/emulated/0/Download/Godlo/yt-dlp/", primary)
        )
        assertEquals("primary:", FolderPath.toDocumentId(primary, primary))
        assertEquals(
            "1A2B-3C4D:Movies",
            FolderPath.toDocumentId("/storage/1A2B-3C4D/Movies", primary)
        )
        assertNull(FolderPath.toDocumentId("/storage/emulated/10/x", primary))
        assertNull(FolderPath.toDocumentId("/data/x", primary))
    }
}
