package io.github.eggplants.godlo.core

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("システム"), LIGHT("ライト"), DARK("ダーク") }

/** Which way pages turn. */
enum class ReadingDirection(val label: String) {
    RTL("右→左 (漫画)"),
    LTR("左→右"),
    VERTICAL("縦スクロール")
}

/** How many pages the reader shows at once. */
enum class SpreadMode(val label: String) {
    AUTO("自動 (横向きで見開き)"),
    SINGLE("単ページ"),
    SPREAD("見開き")
}

data class AppSettings(
    val root: String = Storage.defaultRoot,
    val videoQuality: String = "1080",
    val audioFormat: String = "mp3",
    val imageFormat: String = "jpg",
    val cbz: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val readingDirection: ReadingDirection = ReadingDirection.RTL,
    val spreadMode: SpreadMode = SpreadMode.AUTO,
    /** In a spread, show the first page alone, the way a printed book opens on its cover. */
    val coverAlone: Boolean = true
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val root = stringPreferencesKey("root")
        val videoQuality = stringPreferencesKey("video_quality")
        val audioFormat = stringPreferencesKey("audio_format")
        val imageFormat = stringPreferencesKey("image_format")
        val cbz = booleanPreferencesKey("cbz")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val readingDirection = stringPreferencesKey("reading_direction")
        val spreadMode = stringPreferencesKey("spread_mode")
        val coverAlone = booleanPreferencesKey("cover_alone")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val default = AppSettings()
        return AppSettings(
            root = this[Keys.root] ?: default.root,
            videoQuality = this[Keys.videoQuality] ?: default.videoQuality,
            audioFormat = this[Keys.audioFormat] ?: default.audioFormat,
            imageFormat = this[Keys.imageFormat] ?: default.imageFormat,
            cbz = this[Keys.cbz] ?: default.cbz,
            themeMode = enumOr(this[Keys.themeMode], default.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: default.dynamicColor,
            readingDirection = enumOr(this[Keys.readingDirection], default.readingDirection),
            spreadMode = enumOr(this[Keys.spreadMode], default.spreadMode),
            coverAlone = this[Keys.coverAlone] ?: default.coverAlone
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.root] = next.root
            prefs[Keys.videoQuality] = next.videoQuality
            prefs[Keys.audioFormat] = next.audioFormat
            prefs[Keys.imageFormat] = next.imageFormat
            prefs[Keys.cbz] = next.cbz
            prefs[Keys.themeMode] = next.themeMode.name
            prefs[Keys.dynamicColor] = next.dynamicColor
            prefs[Keys.readingDirection] = next.readingDirection.name
            prefs[Keys.spreadMode] = next.spreadMode.name
            prefs[Keys.coverAlone] = next.coverAlone
        }
    }
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
