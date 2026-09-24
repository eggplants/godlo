package io.github.eggplants.godlo.core

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The three tools, by the name `godlo_bridge.py` knows them by. */
enum class Engine(val id: String, val kinds: List<MediaKind>) {
    YTDLP("yt-dlp", listOf(MediaKind.VIDEO, MediaKind.AUDIO)),
    GALLERY_DL("gallery-dl", listOf(MediaKind.IMAGE)),
    GETJMANGA("getjmanga", listOf(MediaKind.IMAGE))
    ;

    companion object {
        fun fromId(id: String): Engine = entries.firstOrNull { it.id == id } ?: YTDLP
    }
}

@Serializable
data class Detection(val engine: String, val kind: String, val site: String)

@Serializable
data class DownloadRequest(
    val url: String,
    val engine: String,
    val kind: String,
    val root: String,
    /** yt-dlp: the whole playlist. getjmanga: every next episode too. */
    val playlist: Boolean = false,
    @SerialName("video_quality") val videoQuality: String = "1080",
    @SerialName("audio_format") val audioFormat: String = "mp3",
    @SerialName("image_format") val imageFormat: String = "jpg",
    val cbz: Boolean = false,
    val cookies: String? = null,
    /** Holds `gallery-dl.conf` and `getjmanga.toml`, when the user put them there. */
    @SerialName("config_dir") val configDir: String? = null
)

@Serializable
data class Outcome(val status: String, val message: String = "")

/** What Python reports a running download through. Called from the download thread. */
interface DownloadCallback {
    /** [fraction] in 0..1, or negative when the total is unknown. */
    fun progress(fraction: Double, detail: String)

    fun title(text: String)

    /** A file (or, for getjmanga, an episode directory) that was written. */
    fun file(path: String)

    fun log(text: String)

    fun cancelled(): Boolean
}

/** Starts Chaquopy once and calls into `godlo_bridge.py`. */
class PythonBridge(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val module: PyObject by lazy {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        val module = Python.getInstance().getModule("godlo_bridge")
        val binaries = Binaries.prepare(context)
        val env = mapOf(
            "ffmpeg" to binaries.ffmpeg?.absolutePath,
            "ffmpeg_lib_dir" to binaries.ffmpegLibDir?.absolutePath,
            "qjs" to binaries.qjs?.absolutePath,
            "cache_dir" to context.cacheDir.absolutePath,
            "config_dir" to File(context.filesDir, "config").absolutePath,
            "packages_dir" to packagesDir.absolutePath
        ).filterValues { it != null }
        module.callAttr("setup", json.encodeToString(env))
        module
    }

    /** Where runtime updates of the tools go; see [updateTools]. */
    val packagesDir: File get() = File(context.noBackupFilesDir, "python-packages")

    fun versions(): Map<String, String> =
        json.decodeFromString(module.callAttr("versions").toString())

    fun detect(url: String): Detection =
        json.decodeFromString(module.callAttr("detect", url).toString())

    fun download(request: DownloadRequest, callback: DownloadCallback): Outcome = try {
        json.decodeFromString(
            module.callAttr("download", json.encodeToString(request), callback).toString()
        )
    } catch (e: Exception) {
        Outcome("error", e.message ?: e.javaClass.simpleName)
    }

    /** pip-installs the newest tools into [packagesDir]; they are used from the next launch on. */
    fun updateTools(): Outcome = try {
        packagesDir.mkdirs()
        json.decodeFromString(module.callAttr("update_tools").toString())
    } catch (e: Exception) {
        Outcome("error", e.message ?: e.javaClass.simpleName)
    }

    /** Drops the runtime updates, falling back to the tools in the APK from the next launch on. */
    fun resetTools() {
        packagesDir.deleteRecursively()
    }
}
