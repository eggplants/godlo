#!/usr/bin/env bash
# Build the 16 KB aligned libwebp stubs into app/src/main/jniLibs.
# Needs zig (`mise x zig@latest -- ./native/webp-stub/build.sh`), or the NDK's clang when
# ANDROID_NDK_HOME is set (as on F-Droid's build server).
set -euo pipefail
cd "$(dirname "$0")"
out=../../app/src/main/jniLibs

cc() { # arch, then the compiler's arguments
    local arch=$1
    shift
    if [[ -n ${ANDROID_NDK_HOME:-} ]]; then
        "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" \
            --target="$arch-linux-android24" "$@"
    else
        zig cc -target "$arch-linux-none" "$@"
    fi
}

for abi in arm64-v8a:aarch64 x86_64:x86_64; do
    dir=$out/${abi%%:*}
    mkdir -p "$dir"
    for lib in libwebp libwebpmux; do
        cc "${abi##*:}" -O2 -fPIC -shared -nostdlib \
            -Wl,-soname,$lib.so -Wl,-z,max-page-size=16384 -Wl,--hash-style=both \
            -o "$dir/$lib.so" webp_stub.c
    done
done
