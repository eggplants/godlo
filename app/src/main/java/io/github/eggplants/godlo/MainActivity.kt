package io.github.eggplants.godlo

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eggplants.godlo.core.AppSettings
import io.github.eggplants.godlo.ui.GodloRoot
import io.github.eggplants.godlo.ui.theme.GodloTheme

/** An AppCompatActivity so that AppCompat can apply the per-app language on Android 12 and older. */
class MainActivity : AppCompatActivity() {
    /**
     * Set by the video player while a video plays: leaving the app then shrinks the video into
     * picture-in-picture instead of stopping it. Null otherwise.
     */
    var pictureInPicture: PictureInPictureParams? = null
        set(value) {
            field = value
            if (!supportsPictureInPicture) return
            // Android 12 and later enter on their own, when the params say so.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setPictureInPictureParams(
                    value ?: PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
                )
            }
        }

    val supportsPictureInPicture: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /** Shrinks the playing video into a window; false if the user turned that off for Godlo. */
    fun enterPictureInPicture(): Boolean {
        val params = pictureInPicture ?: return false
        return runCatching { enterPictureInPictureMode(params) }.getOrDefault(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container.audio.connect()
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            GodloTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                GodloRoot(container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Files may have been added or removed by another app meanwhile.
        container.library.refresh()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Before Android 12, picture-in-picture has to be entered by hand on the way out.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enterPictureInPicture()
    }
}
