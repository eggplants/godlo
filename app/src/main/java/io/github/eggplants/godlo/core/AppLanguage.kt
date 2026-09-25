package io.github.eggplants.godlo.core

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The UI language, set per app through AppCompat: kept by the system on Android 13+ (and shown
 * in its per-app language settings), by AppCompat before that.
 */
object AppLanguage {
    /** Language tags offered in settings, each named in itself; "" follows the device. */
    val options = listOf("" to null, "ja" to "日本語", "en" to "English")

    /** The chosen language tag, or "" when the app follows the device. */
    fun current(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    /** Switches the UI language; activities are recreated in it. */
    fun set(tag: String) =
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

    /**
     * [context] in the chosen language. Activities get it from AppCompat, and everything does on
     * Android 13+; this is for the rest before that, e.g. the download notifications.
     */
    fun localize(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return context.createConfigurationContext(config)
    }

    /**
     * Puts the chosen language back into an activity's [resources] before Android 13. A
     * configuration change the activity handles itself, such as rotation, brings back the
     * device's language there, and AppCompat does not apply the chosen one again.
     */
    @Suppress("DEPRECATION") // Resources.updateConfiguration, which AppCompat itself uses here.
    fun reapply(resources: Resources) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return
        val chosen = LocaleList.forLanguageTags(locales.toLanguageTags())
        if (resources.configuration.locales == chosen) return
        val config = Configuration(resources.configuration)
        config.setLocales(chosen)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    /**
     * The language the UI is shown in, "ja" or "en": English stands in for any language the app
     * has no resources for. For text made outside Android resources, i.e. by the Python tools.
     */
    fun shown(): String {
        val locale =
            AppCompatDelegate.getApplicationLocales()[0] ?: LocaleListCompat.getDefault()[0]
        return if (locale?.language == "ja") "ja" else "en"
    }
}
