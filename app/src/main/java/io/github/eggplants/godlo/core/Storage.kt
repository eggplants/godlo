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
import androidx.core.content.ContextCompat
import java.io.File

/** What a download is saved as, and the directory under the save root it goes in. */
enum class MediaKind(val dir: String, val label: String) {
    IMAGE("image", "画像"),
    AUDIO("audio", "音楽"),
    VIDEO("video", "動画")
    ;

    companion object {
        fun fromDir(dir: String): MediaKind = entries.firstOrNull { it.dir == dir } ?: VIDEO
    }
}

object Storage {
    /** `/storage/emulated/0/Download/Godlo`: `<root>/<kind>/<site>/` holds everything saved. */
    val defaultRoot: String
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Godlo"
        ).absolutePath

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
