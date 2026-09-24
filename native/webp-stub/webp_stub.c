/*
 * Stand-ins for libwebp.so and libwebpmux.so, which youtubedl-android's ffmpeg
 * links against but ships aligned to 4 KB pages only, so Android refuses to load
 * them on 16 KB page devices and ffmpeg does not start at all.
 *
 * ffmpeg uses libwebp only for its "libwebp" encoder; reading WebP (what yt-dlp
 * does to thumbnails) goes through ffmpeg's own decoder. So every function here
 * fails: 0 for an int, NULL for a pointer, nothing for a void. The encoder then
 * refuses to open, and nothing else notices.
 *
 * Rebuild with ./build.sh.
 */

#define FAIL(name) \
    long name(void) { return 0; }

/* libwebp */
FAIL(WebPCleanupTransparentArea)
FAIL(WebPConfigInitInternal)
FAIL(WebPEncode)
FAIL(WebPFree)
FAIL(WebPMemoryWrite)
FAIL(WebPMemoryWriterClear)
FAIL(WebPMemoryWriterInit)
FAIL(WebPPictureFree)
FAIL(WebPPictureInitInternal)
FAIL(WebPValidateConfig)

/* libwebpmux */
FAIL(WebPAnimEncoderAdd)
FAIL(WebPAnimEncoderAssemble)
FAIL(WebPAnimEncoderDelete)
FAIL(WebPAnimEncoderNewInternal)
FAIL(WebPAnimEncoderOptionsInitInternal)
