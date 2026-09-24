package io.github.eggplants.godlo.library

import android.media.MediaMetadataRetriever
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import java.io.File
import okio.Buffer

/** Coil model for the cover art embedded in an audio file. */
data class AudioArt(val file: File)

class AudioArtFetcher(private val art: AudioArt, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bytes = MediaMetadataRetriever().run {
            try {
                setDataSource(art.file.absolutePath)
                embeddedPicture
            } catch (_: RuntimeException) {
                null
            } finally {
                release()
            }
        } ?: return null
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<AudioArt> {
        override fun create(data: AudioArt, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioArtFetcher(data, options)
    }
}
