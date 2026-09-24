package io.github.eggplants.godlo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eggplants.godlo.core.AppSettings
import io.github.eggplants.godlo.ui.GodloRoot
import io.github.eggplants.godlo.ui.theme.GodloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        container.music.connect()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            GodloTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                GodloRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // Files may have been added or removed by another app meanwhile.
        container.library.refresh()
    }

    /** A URL shared from a browser or an app, or opened with Godlo, goes to the download screen. */
    private fun handle(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val url = URL_PATTERN.find(text)?.value ?: return
        container.sharedUrl.value = url
    }

    private companion object {
        val URL_PATTERN = Regex("https?://\\S+")
    }
}
