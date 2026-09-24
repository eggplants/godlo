package io.github.eggplants.godlo.core

import android.content.Context
import android.system.Os
import java.io.File
import org.apache.commons.compress.archivers.zip.ZipFile

/**
 * The native programs yt-dlp runs: ffmpeg and QuickJS, which solves YouTube's JavaScript
 * challenges. Both come from youtubedl-android.
 *
 * Both ship as `lib*.so` so the installer puts them in `nativeLibraryDir`, the one place an app
 * may execute files from. ffmpeg's shared libraries come zipped, with symlinks, and are unpacked
 * once per APK: most from `libffmpeg.zip.so`, the rest from youtubedl-android's Python bundle,
 * which ffmpeg shares them with there.
 */
class Binaries(val ffmpeg: File?, val ffmpegLibDir: File?, val qjs: File?) {
    companion object {
        fun prepare(context: Context): Binaries {
            val nativeDir = File(context.applicationInfo.nativeLibraryDir)
            val ffmpeg = File(nativeDir, "libffmpeg.so").takeIf { it.exists() }
            val qjs = File(nativeDir, "libqjs.so").takeIf { it.exists() }
            val zip = File(nativeDir, "libffmpeg.zip.so")
            val pythonZip = File(nativeDir, "libpython.zip.so")
            val libRoot = File(context.noBackupFilesDir, "ffmpeg")
            val libDir = File(libRoot, "usr/lib")
            if (zip.exists() && pythonZip.exists()) {
                val stamp = File(libRoot, ".stamp")
                // Unpacked again whenever the app is installed or updated.
                val want = context.packageManager
                    .getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
                if (!stamp.exists() || stamp.readText() != want) {
                    libRoot.deleteRecursively()
                    unzip(zip, libRoot) { it !in WEBP }
                    unzip(pythonZip, libRoot) { it in FROM_PYTHON }
                    for (stub in listOf("libwebp.so", "libwebpmux.so")) {
                        Os.symlink(
                            File(nativeDir, stub).absolutePath,
                            File(libDir, stub).absolutePath
                        )
                    }
                    stamp.writeText(want)
                }
            }
            return Binaries(ffmpeg, libDir.takeIf { it.isDirectory }, qjs)
        }

        /**
         * libwebp as `libffmpeg.zip.so` ships it: aligned to 4 KB pages, which 16 KB page
         * devices refuse to load. Stubs in jniLibs (see native/webp-stub) stand in for it.
         */
        private val WEBP = setOf(
            "usr/lib/libsharpyuv.so",
            "usr/lib/libwebp.so",
            "usr/lib/libwebpdecoder.so",
            "usr/lib/libwebpdemux.so",
            "usr/lib/libwebpmux.so"
        )

        /** What ffmpeg links against that `libffmpeg.zip.so` leaves out. */
        private val FROM_PYTHON = setOf(
            "usr/lib/libandroid-posix-semaphore.so",
            "usr/lib/libandroid-support.so",
            "usr/lib/libc++_shared.so",
            "usr/lib/libcrypto.so.3",
            "usr/lib/libexpat.so.1",
            "usr/lib/libexpat.so.1.11.1"
        )

        private fun unzip(zip: File, target: File, wanted: (String) -> Boolean) {
            val root = target.canonicalPath + File.separator
            ZipFile.builder().setFile(zip).get().use { archive ->
                for (entry in archive.entries) {
                    if (!wanted(entry.name)) continue
                    val out = File(target, entry.name)
                    require(out.canonicalPath.startsWith(root)) {
                        "zip entry outside target: ${entry.name}"
                    }
                    when {
                        entry.isDirectory -> out.mkdirs()

                        entry.isUnixSymlink -> {
                            out.parentFile?.mkdirs()
                            val link = archive.getInputStream(entry).use {
                                it.readBytes().decodeToString()
                            }
                            Os.symlink(link, out.absolutePath)
                        }

                        else -> {
                            out.parentFile?.mkdirs()
                            archive.getInputStream(entry).use { input ->
                                out.outputStream().use { input.copyTo(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}
