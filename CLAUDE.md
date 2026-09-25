# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Godlo is an Android app (Kotlin, Jetpack Compose) that runs yt-dlp, gallery-dl and getjmanga
on the device and then shows what they saved: a download queue, per-media-type library tabs, a
manga/picture reader, and audio and video players.

The download tools are the real Python packages, embedded with [Chaquopy](https://chaquo.com/chaquopy/)
(Python 3.13), not reimplementations. ffmpeg and QuickJS come prebuilt from youtubedl-android,
which is used only for its binaries (`isTransitive = false`), never for its Java API. The tools
write with plain file paths under `Download/Godlo/<tool>/<site>/`, so the app needs "all files
access" (`MANAGE_EXTERNAL_STORAGE`) rather than scoped storage. Only `arm64-v8a` and `x86_64`
are built, because Chaquopy ships Python for these two ABIs only.

## Commands

Tools (JDK 21, Python 3.13, ktlint, ...) are pinned in `mise.toml`, which is also the canonical
list of tasks.

```bash
mise run build                         # ./gradlew assembleDebug
mise run build:release                 # signed release APK from .env and release.jks (.env.example)
mise run test                          # ./gradlew test (JVM unit tests)
./gradlew testDebugUnitTest --tests 'io.github.eggplants.godlo.SharedTextTest'  # a single test class
mise run format                        # ktlint --format
mise run lint                          # ktlint + Android Lint
mise run ci                            # ktlint, then ./gradlew lint test assembleDebug -- what CI runs
```

The build needs a `python3.13` on the host, because Chaquopy pip-installs the packages at build
time with the same minor version. `app/build.gradle.kts` falls back to mise's install directory
when it is not on `PATH` (Android Studio started from a desktop entry does not see the shell's
`PATH`); `-Pgodlo.buildPython=/path/to/python3.13` overrides it. The configuration cache is off
on purpose: the Chaquopy plugin runs Python at configuration time.

Kotlin style comes from `.editorconfig`: `ktlint_code_style = android_studio` with
`max_line_length = 100`, so ktlint and Android Lint agree.

## Architecture

- **`GodloApp.kt`** -- builds the `AppContainer` (manual DI: settings, Python bridge, download
  manager, library, audio player, patrol) and starts Python in the background, since that takes
  seconds. Reach it with `context.container`.
- **`MainActivity`** / **`ShareActivity`** -- `ShareActivity` takes `ACTION_SEND` text, finds
  the URL with `SharedText` and hands it over through `container.sharedUrl`. Shares must not go
  to `MainActivity` directly: Android would then deliver only the first share's intent.
- **`core/`**
  - `PythonBridge.kt` -- starts Chaquopy once and calls `godlo_bridge.py`. Everything crosses
    the boundary as JSON strings (`DownloadRequest`, `Detection`, `Outcome`, ...) plus a
    `DownloadCallback` object that Python calls for progress, files, log lines and cancellation.
    `@SerialName` fields must match the keys Python reads.
  - `Binaries.kt` -- unpacks ffmpeg's shared libraries from youtubedl-android's
    `lib*.zip.so` into `noBackupFilesDir` once per install/update. Binaries run from
    `nativeLibraryDir` only, which is why `useLegacyPackaging = true` is set.
  - `SettingsRepository.kt` (DataStore), `Storage.kt` (paths, `MediaKind`), `AppLanguage.kt`
    (per-app language via AppCompat).
  - `ConfigFile.kt` -- the tools' own config files in `Download/Godlo/.config/`
    (`yt-dlp.conf`, `gallery-dl.conf`, `getjmanga.toml`, `cookies.txt`), which
    `ui/settings/ConfigEditorScreen.kt` edits as a tree (`ConfigTree.kt`, JSON; TOML goes through
    Python's `toml_to_json` / `toml_from_json`, which keep the comments) or as text
    (`CodeEditor.kt`: line numbers, and colours from the lexers in `SyntaxHighlight.kt`), checking
    them with `check_config` before it saves. Settings that would move files out of where the
    library looks, or that Godlo overrides on every run, are refused: `ConfigRules.kt` for
    gallery-dl.conf and getjmanga.toml, `strip_ytdlp_config` for yt-dlp.conf. Import drops them,
    the editor will not add them, and the download drops them again for files written elsewhere.
- **`app/src/main/python/godlo_bridge.py`** -- the one Python module Kotlin calls: `setup`,
  `detect` (which tools can take a URL), `download`, the patrol functions and `update_tools`.
  Cancellation is the `Cancelled` exception, a `BaseException` so the tools' `except Exception`
  blocks do not swallow it. User-facing text Python writes is localized in `_MESSAGES` (en/ja).
  `update_tools` pip-installs newer tools into `packages_dir`, which `setup` puts first on
  `sys.path`; dependencies always stay the ones in the APK.
- **`download/`** -- `DownloadManager` is the queue, persisted to `downloads.json`. Tasks run
  **one at a time**: the tools share global state in the single interpreter. `DownloadService`
  is the foreground service that drains the queue. `Patrol` wraps getjmanga's `[[patrol]]`
  entries in `Download/Godlo/.config/getjmanga.toml`, which getjmanga on a PC reads too.
  `DownloadTarget` maps a finished task to where the library can open it.
  `FolderCopy` copies a finished task into a folder picked with the system's folder picker that
  has no file path (e.g. SMB through a documents provider): the tools still save on the device.
- **`library/`** -- `LibraryRepository` walks the tools' directories; what a file is comes from
  its extension, and a directory with pictures is an album. `LibraryTree` groups items by
  `<site>/<title>/...` for every tab, with `NaturalOrder` for page and episode numbers.
- **`player/`** -- Media3 `PlaybackService` and `AudioPlayer`.
- **`ui/`** -- Compose screens by feature (`download`, `library`, `reader`, `player`,
  `settings`); `GodloRoot.kt` holds navigation and the top-level tabs.

### Native workarounds (16 KB pages)

Android 15+ devices with 16 KB pages refuse libraries aligned to 4 KB. Two prebuilt pieces are
replaced for that, and the replacements are checked in:

- `native/webp-stub/` -- stub `libwebp.so` / `libwebpmux.so` in `app/src/main/jniLibs/` for
  youtubedl-android's ffmpeg (built with zig; see `build.sh`). Only the libwebp encoder is lost.
- `native/pillow-libs/build.sh` -- rebuilds Chaquopy's libjpeg and freetype into
  `native/wheels/*+16k*.whl`, which the Chaquopy `pip` block installs by exact version.

### Python dependencies

The Chaquopy `pip` block installs with `--no-deps`, so every transitive dependency is listed by
hand. The reason is that getjmanga asks for `cryptography>=43`, but the newest Chaquopy build is
42.0.8; `_shim_cryptography()` in `godlo_bridge.py` fills the gap. When you add or bump a
package, add its dependencies to the list as well. `native/licenses/python_licenses.py` then
generates the license list the app shows for them.

## Versioning and releases

`versionName` / `versionCode` come from the latest `v<versionName>-<versionCode>` git tag
(`git describe`), computed at the top of `app/build.gradle.kts`: `v1.2.3-4` is `1.2.3` / `4`.
Bump the code by hand on every tag; Android refuses to update to a lower one. Nothing in the
repo hard-codes a version, and a checkout without tags builds as `0.0.0` / `1`. CI
(`.github/workflows/ci.yml`) runs `mise run ci` on pushes to `master`, tags and PRs; it does not
publish anything. Pushing a `v<versionName>-<versionCode>` tag also runs
`.github/workflows/release.yml`, which builds a signed release APK and attaches it to a GitHub
Release. The key comes from the secrets `GODLO_KEYSTORE_BASE64`, `GODLO_KEYSTORE_PASSWORD`,
`GODLO_KEY_ALIAS` and `GODLO_KEY_PASSWORD`; locally, a release build is signed only when the
`GODLO_KEYSTORE_FILE` (and the other `GODLO_*`) environment variables are set. Actions are
pinned to full commit SHAs (`mise run pin`).

### F-Droid

The store listing is in `fastlane/metadata/android/<locale>/` (en-US and ja-JP), which F-Droid
reads from the tagged commit; add `changelogs/<versionCode>.txt` (500 characters at most) for
every tag. `fdroid/metadata/io.github.eggplants.godlo.yml` is the recipe for fdroiddata: it
deletes the checked-in `.so` and `.whl` files and rebuilds them with the NDK (both `build.sh`
scripts use the NDK's clang when `ANDROID_NDK_HOME` is set), and passes `-Pgodlo.noDirty`,
because F-Droid edits the checkout and `-dirty` would change the versionName. Dependencies must
stay free software: no Google Play services or Firebase (the license list is AboutLibraries).

## Testing conventions

JVM unit tests live in `app/src/test/java/io/github/eggplants/godlo/` and cover the pure logic
(URL extraction, the download form, `DownloadTarget`, `LibraryTree`, the reader model). Keep
new logic out of Android APIs and Compose where you can, so it is testable here. There are no
instrumented tests, and nothing tests `godlo_bridge.py` directly.

## Commits

Follow `.claude/skills/commit/SKILL.md`: `type: subject` in English without a scope, and no
`Co-Authored-By` trailer.
