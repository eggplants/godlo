plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "io.github.eggplants.godlo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.eggplants.godlo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // Chaquopy ships Python for these ABIs only.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
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
    packaging {
        jniLibs {
            // ffmpeg is run as an executable out of nativeLibraryDir, so it must be extracted.
            useLegacyPackaging = true
            // youtubedl-android's Python launcher; Chaquopy runs Python here.
            excludes += "**/libpython.so"
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            // getjmanga asks for cryptography>=43, but Chaquopy's newest Android build is 42.0.8,
            // which has the AES-CBC API getjmanga uses. Resolve the tree by hand instead of pip.
            options("--no-deps")
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
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
    // Only the prebuilt binaries: ffmpeg, QuickJS (libqjs.so) and the shared libraries ffmpeg
    // links against (inside libpython.zip.so). Godlo unpacks and runs them itself.
    implementation(libs.youtubedl.android.ffmpeg) { isTransitive = false }
    implementation(libs.youtubedl.android.library) { isTransitive = false }
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
