#!/usr/bin/env bash
# Build the 16 KB aligned libwebp stubs into app/src/main/jniLibs.
# Needs zig (`mise x zig@latest -- ./native/webp-stub/build.sh`).
set -euo pipefail
cd "$(dirname "$0")"
out=../../app/src/main/jniLibs
for abi in arm64-v8a:aarch64 x86_64:x86_64; do
    dir=$out/${abi%%:*}
    mkdir -p "$dir"
    for lib in libwebp libwebpmux; do
        zig cc -target "${abi##*:}-linux-none" -O2 -fPIC -shared -nostdlib \
            -Wl,-soname,$lib.so -Wl,-z,max-page-size=16384 -Wl,--hash-style=both \
            -o "$dir/$lib.so" webp_stub.c
    done
done
