package io.github.eggplants.godlo.core

import androidx.annotation.StringRes
import io.github.eggplants.godlo.R
import java.io.File

/** How a config file is written, which decides the editors it gets. */
enum class ConfigFormat {
    /** yt-dlp's command line options, one or more per line. */
    ARGS,
    JSON,
    TOML,

    /** Netscape cookies.txt. */
    COOKIES
}

/** The files in [Storage.configDir] the tools read, by the name `godlo_bridge.py` knows them by. */
enum class ConfigFile(
    val fileName: String,
    val format: ConfigFormat,
    @StringRes val summary: Int,
    @StringRes val hint: Int,
    val docs: String
) {
    YTDLP(
        "yt-dlp.conf",
        ConfigFormat.ARGS,
        R.string.config_ytdlp_summary,
        R.string.config_ytdlp_hint,
        "https://github.com/yt-dlp/yt-dlp#configuration"
    ),
    GALLERY_DL(
        "gallery-dl.conf",
        ConfigFormat.JSON,
        R.string.config_gallery_dl_summary,
        R.string.config_gallery_dl_hint,
        "https://gdl-org.github.io/docs/configuration.html"
    ),
    GETJMANGA(
        "getjmanga.toml",
        ConfigFormat.TOML,
        R.string.config_getjmanga_summary,
        R.string.config_getjmanga_hint,
        "https://github.com/eggplants/getjmanga#configuration"
    ),
    COOKIES(
        "cookies.txt",
        ConfigFormat.COOKIES,
        R.string.config_cookies_summary,
        R.string.config_cookies_hint,
        "https://github.com/yt-dlp/yt-dlp/wiki/FAQ#how-do-i-pass-cookies-to-yt-dlp"
    )
    ;

    val file: File get() = File(Storage.configDir, fileName)

    /** Whether the file is structured data the visual editor can show as a tree. */
    val visual: Boolean get() = format == ConfigFormat.JSON || format == ConfigFormat.TOML
}
