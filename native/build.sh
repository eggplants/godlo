#!/usr/bin/env bash
# Build the native pieces the APK needs but git does not keep: the libwebp stubs into
# app/src/main/jniLibs and the Pillow libraries into native/wheels (F-Droid's scanner refuses
# prebuilt binaries in the source). Run by `mise run build:native`.
#
# Uses NDK r28c, the one F-Droid builds with, so the output is the same everywhere; install it
# with `sdkmanager "ndk;28.2.13676358"`. ANDROID_NDK_HOME is used instead when r28c is missing.
set -euo pipefail
cd "$(dirname "$0")"

ndk_version=28.2.13676358
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
if [[ -d $sdk/ndk/$ndk_version ]]; then
    export ANDROID_NDK_HOME=$sdk/ndk/$ndk_version
elif [[ -z ${ANDROID_NDK_HOME:-} ]]; then
    echo "NDK $ndk_version is not in $sdk/ndk: sdkmanager \"ndk;$ndk_version\"" >&2
    exit 1
fi

./webp-stub/build.sh
./pillow-libs/build.sh
