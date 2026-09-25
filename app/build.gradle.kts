import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.chaquopy)
    alias(libs.plugins.aboutlibraries)
}

// The version comes from the latest `v<versionName>-<versionCode>` tag: v1.2.3-1 builds as
// versionName 1.2.3 and versionCode 1. Commits past the tag keep its versionCode and get git
// describe's suffix in the name (1.2.3-4-gabcdef0, -dirty with uncommitted changes); no tag at
// all is 0.0.0 and 1.
val gitDescribe: String = runCatching {
    providers.exec {
        commandLine("git", "describe", "--tags", "--match", "v[0-9]*-[0-9]*", "--dirty")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

val gitVersion = Regex("""^v(\d+\.\d+\.\d+)-(\d+)(.*)$""").find(gitDescribe)?.destructured

val gitVersionName: String = gitVersion?.let { (name, _, rest) -> name + rest } ?: "0.0.0"

val gitVersionCode: Int = gitVersion?.let { (_, code, _) ->
    // Android requires a versionCode of at least 1.
    code.toInt().also { require(it >= 1) { "versionCode must be 1 or more: $gitDescribe" } }
} ?: 1

android {
    namespace = "io.github.eggplants.godlo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.eggplants.godlo"
        minSdk = 26
        targetSdk = 37
        versionCode = gitVersionCode
        versionName = gitVersionName

        ndk {
            // Chaquopy ships Python for these ABIs only.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // The release key comes from the environment (the release workflow decodes it from secrets).
    // Without it the release APK is left unsigned.
    val releaseKeystore = providers.environmentVariable("GODLO_KEYSTORE_FILE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("GODLO_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("GODLO_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("GODLO_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    bundle {
        language {
            // The UI language can be switched in the app, so every language has to be installed.
            enableSplit = false
        }
    }
    androidResources {
        // Lists the languages with values-xx/ for the system's per-app language settings
        // (Android 13+); res/resources.properties names what plain values/ is in.
        generateLocaleConfig = true
    }
    packaging {
        jniLibs {
            // ffmpeg is run as an executable out of nativeLibraryDir, so it must be extracted.
            useLegacyPackaging = true
            // youtubedl-android's Python launcher; Chaquopy runs Python here.
            excludes += "**/libpython.so"
        }
    }
}

// Chaquopy needs a Python of the same minor version at build time. It looks for `python3.13`
// on PATH, which Android Studio launched from a desktop entry does not share with the shell,
// so fall back to where mise (see mise.toml) installs it. `-Pgodlo.buildPython=...` overrides.
val hostPython: String? = providers.gradleProperty("godlo.buildPython").orNull
    ?: listOfNotNull(
        System.getenv("MISE_DATA_DIR"),
        System.getenv("XDG_DATA_HOME")?.let { "$it/mise" },
        "${System.getProperty("user.home")}/.local/share/mise"
    ).map { file("$it/installs/python/3.13/bin/python3.13") }
        .firstOrNull { it.canExecute() }
        ?.absolutePath

chaquopy {
    defaultConfig {
        version = "3.13"
        hostPython?.let { buildPython(it) }
        pip {
            // getjmanga asks for cryptography>=43, but Chaquopy's newest Android build is 42.0.8,
            // which has the AES-CBC API getjmanga uses. Resolve the tree by hand instead of pip.
            options("--no-deps", "--find-links", rootProject.file("native/wheels").absolutePath)
            install("pip")
            install("yt-dlp")
            install("yt-dlp-ejs")
            install("gallery-dl")
            install("getjmanga")
            // yt-dlp / gallery-dl
            install("requests")
            install("urllib3")
            install("certifi")
            install("charset-normalizer")
            install("idna")
            install("brotli")
            install("mutagen")
            install("pycryptodomex")
            // getjmanga
            install("beautifulsoup4")
            install("soupsieve")
            install("typing-extensions")
            install("cbz")
            install("langcodes")
            install("pypdf")
            install("rarfile")
            install("xmltodict")
            install("cryptography==42.0.8")
            install("cffi==1.17.1")
            // Chaquopy's builds of cffi and pillow link against these; --no-deps skips them.
            install("chaquopy-libffi")
            // Chaquopy's own builds of these two are aligned to 4 KB pages, which 16 KB page
            // devices refuse to load; native/pillow-libs/build.sh rebuilds them.
            install("chaquopy-libjpeg==1.5.3+16k")
            install("chaquopy-freetype==2.9.1+16k")
            install("pycparser==2.22")
            install("httpx2")
            install("httpcore2")
            install("anyio")
            install("h11")
            install("truststore")
            install("pathvalidate")
            install("pillow==11.0.0")
            install("rich")
            install("pygments")
            install("markdown-it-py")
            install("mdurl")
            install("tomlkit")
            install("ua-generator")
        }
    }
}

/**
 * Writes python_licenses.json into the APK's assets: what pip installed, with its licenses,
 * which AboutLibraries does not see. See native/licenses/python_licenses.py.
 */
abstract class PythonLicenses : DefaultTask() {
    @get:InputDirectory
    abstract val pipDir: DirectoryProperty

    @get:InputFile
    abstract val script: RegularFileProperty

    @get:Input
    abstract val python: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun generate() {
        exec.exec {
            commandLine(
                python.get(),
                script.get().asFile.absolutePath,
                pipDir.get().asFile.absolutePath,
                outputDir.file("python_licenses.json").get().asFile.absolutePath
            )
        }
    }
}

// The license list of the Maven dependencies, for the about screen. Offline, so that the build
// reads nothing but the POMs and gives the same list every time.
aboutLibraries {
    offlineMode = true
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<PythonLicenses>("generate${name}PythonLicenses") {
            dependsOn("install${name}PythonRequirements")
            pipDir.set(layout.buildDirectory.dir("python/pip/${variant.name}/common"))
            script.set(rootProject.file("native/licenses/python_licenses.py"))
            python.set(hostPython ?: "python3.13")
            outputDir.set(layout.buildDirectory.dir("generated/pythonLicenses/${variant.name}"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, PythonLicenses::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Per-app UI language (AppCompatDelegate.setApplicationLocales) on every Android version.
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.commons.compress)
    // The license screen for the Maven dependencies; see pythonLicenses below for the rest.
    implementation(libs.aboutlibraries.compose.m3)
    // Only the prebuilt binaries: ffmpeg, QuickJS (libqjs.so) and the shared libraries ffmpeg
    // links against (inside libpython.zip.so). Godlo unpacks and runs them itself.
    implementation(libs.youtubedl.android.ffmpeg) { isTransitive = false }
    implementation(libs.youtubedl.android.library) { isTransitive = false }
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
