package io.github.eggplants.godlo.core

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.eggplants.godlo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeMode(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark)
}

/** Which way pages turn. */
enum class ReadingDirection(@StringRes val label: Int, @StringRes val shortLabel: Int = label) {
    RTL(R.string.direction_rtl, R.string.direction_rtl_short),
    LTR(R.string.direction_ltr),
    VERTICAL(R.string.direction_vertical)
}

/** How many pages the reader shows at once. */
enum class SpreadMode(@StringRes val label: Int, @StringRes val shortLabel: Int = label) {
    AUTO(R.string.spread_auto, R.string.spread_auto_short),
    SINGLE(R.string.spread_single),
    SPREAD(R.string.spread_spread)
}

/** Which episodes a getjmanga download takes besides the one linked. */
enum class EpisodeRange(@StringRes val label: Int) {
    ONE(R.string.episodes_one),

    /** `--bulk`. */
    FOLLOWING(R.string.episodes_following),

    /** `--both`: the previous episodes as well as the following ones. */
    ALL(R.string.episodes_all)
}

/** How a library screen lays out what it lists. */
enum class LibraryLayout(@StringRes val label: Int) {
    LIST(R.string.layout_list),
    LARGE_GRID(R.string.layout_large_grid),
    SMALL_GRID(R.string.layout_small_grid)
}

data class AppSettings(
    /** Each tool's save directory; `<site>/` directories go inside. */
    val roots: Map<Engine, String> = Engine.entries.associateWith(Storage::defaultRoot),
    val videoQuality: String = "1080",
    val audioFormat: String = "mp3",
    val imageFormat: String = "jpg",
    val cbz: Boolean = false,
    val episodes: EpisodeRange = EpisodeRange.ONE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val readingDirection: ReadingDirection = ReadingDirection.RTL,
    val spreadMode: SpreadMode = SpreadMode.AUTO,
    /** In a spread, show the first page alone, the way a printed book opens on its cover. */
    val coverAlone: Boolean = true,
    val imageLayout: LibraryLayout = LibraryLayout.LARGE_GRID,
    val audioLayout: LibraryLayout = LibraryLayout.LIST,
    val videoLayout: LibraryLayout = LibraryLayout.LARGE_GRID
) {
    fun root(engine: Engine): String = roots[engine] ?: Storage.defaultRoot(engine)
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        fun root(engine: Engine) = stringPreferencesKey("root_${engine.id}")
        val videoQuality = stringPreferencesKey("video_quality")
        val audioFormat = stringPreferencesKey("audio_format")
        val imageFormat = stringPreferencesKey("image_format")
        val cbz = booleanPreferencesKey("cbz")
        val episodes = stringPreferencesKey("getjmanga_episodes")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val readingDirection = stringPreferencesKey("reading_direction")
        val spreadMode = stringPreferencesKey("spread_mode")
        val coverAlone = booleanPreferencesKey("cover_alone")
        val imageLayout = stringPreferencesKey("image_layout")
        val audioLayout = stringPreferencesKey("audio_layout")
        val videoLayout = stringPreferencesKey("video_layout")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    /**
     * The latest settings, read from disk once as the app starts. Screens show what is saved
     * from their first frame on, instead of the defaults until their own read finishes.
     */
    val state: StateFlow<AppSettings> = settings.stateIn(
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        SharingStarted.Eagerly,
        AppSettings()
    )

    private fun Preferences.toSettings(): AppSettings {
        val default = AppSettings()
        return AppSettings(
            roots = Engine.entries.associateWith { this[Keys.root(it)] ?: default.root(it) },
            videoQuality = this[Keys.videoQuality] ?: default.videoQuality,
            audioFormat = this[Keys.audioFormat] ?: default.audioFormat,
            imageFormat = this[Keys.imageFormat] ?: default.imageFormat,
            cbz = this[Keys.cbz] ?: default.cbz,
            episodes = enumOr(this[Keys.episodes], default.episodes),
            themeMode = enumOr(this[Keys.themeMode], default.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: default.dynamicColor,
            readingDirection = enumOr(this[Keys.readingDirection], default.readingDirection),
            spreadMode = enumOr(this[Keys.spreadMode], default.spreadMode),
            coverAlone = this[Keys.coverAlone] ?: default.coverAlone,
            imageLayout = enumOr(this[Keys.imageLayout], default.imageLayout),
            audioLayout = enumOr(this[Keys.audioLayout], default.audioLayout),
            videoLayout = enumOr(this[Keys.videoLayout], default.videoLayout)
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            for (engine in Engine.entries) prefs[Keys.root(engine)] = next.root(engine)
            prefs[Keys.videoQuality] = next.videoQuality
            prefs[Keys.audioFormat] = next.audioFormat
            prefs[Keys.imageFormat] = next.imageFormat
            prefs[Keys.cbz] = next.cbz
            prefs[Keys.episodes] = next.episodes.name
            prefs[Keys.themeMode] = next.themeMode.name
            prefs[Keys.dynamicColor] = next.dynamicColor
            prefs[Keys.readingDirection] = next.readingDirection.name
            prefs[Keys.spreadMode] = next.spreadMode.name
            prefs[Keys.coverAlone] = next.coverAlone
            prefs[Keys.imageLayout] = next.imageLayout.name
            prefs[Keys.audioLayout] = next.audioLayout.name
            prefs[Keys.videoLayout] = next.videoLayout.name
        }
    }
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
