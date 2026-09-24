package io.github.eggplants.godlo.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import io.github.eggplants.godlo.R
import java.io.File

/** What a download is saved as, by the name `godlo_bridge.py` knows it by. */
enum class MediaKind(val id: String, @StringRes val label: Int) {
    IMAGE("image", R.string.kind_image),
    AUDIO("audio", R.string.kind_audio),
    VIDEO("video", R.string.kind_video)
    ;

    companion object {
        fun fromId(id: String): MediaKind = entries.firstOrNull { it.id == id } ?: VIDEO
    }
}

object Storage {
    /** `/storage/emulated/0/Download/Godlo`, under which each tool gets its own directory. */
    val base: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Godlo"
        )

    /** Where [engine] saves by default: `Download/Godlo/<tool>`, holding `<site>/` directories. */
    fun defaultRoot(engine: Engine): String = File(base, engine.id).absolutePath

    /** Holds the tools' own config files and cookies.txt, whatever the save directories are. */
    val configDir: File get() = File(base, ".config")

    /**
     * Whether the app may write anywhere on shared storage.
     *
     * yt-dlp, gallery-dl and getjmanga write with plain file paths, so scoped storage alone is
     * not enough: Android 11+ needs "all files access".
     */
    fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** The settings screen that grants [hasAccess] on Android 11+. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun accessSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.fromParts("package", context.packageName, null)
    )
}
