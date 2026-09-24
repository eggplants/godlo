#!/usr/bin/env bash
# Rebuild the two native libraries Chaquopy's Pillow links against, aligned to 16 KB pages.
#
# Chaquopy's chaquopy-libjpeg 1.5.3 and chaquopy-freetype 2.9.1 wheels are aligned to 4 KB pages,
# which Android refuses to load on 16 KB page devices, so Pillow, and getjmanga with it, fails to
# import there. This builds the same versions, with the same configuration and SONAMEs, into
# wheels under native/wheels/ that app/build.gradle.kts installs in their place.
#
# Neither make nor cmake is needed: the sources are compiled directly with the NDK's clang.
#
# Usage: ANDROID_NDK_HOME=/path/to/android-ndk-r28c native/pillow-libs/build.sh
set -euo pipefail

: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to an NDK r28 or newer}"
here=$(cd "$(dirname "$0")" && pwd)
out=$here/../wheels
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

api=24
toolchain=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin
abis=(arm64-v8a:aarch64-linux-android x86_64:x86_64-linux-android)
cflags=(-O2 -fPIC -fvisibility=default -w)
ldflags=(-shared -Wl,-z,max-page-size=16384 -Wl,--build-id=none -lm -ldl)

fetch() { # url sha256 dest
    curl -fsSL -o "$3" "$1"
    echo "$2  $3" | sha256sum -c --quiet
}

cd "$work"
fetch https://github.com/libjpeg-turbo/libjpeg-turbo/archive/refs/tags/1.5.3.tar.gz \
    1a17020f859cb12711175a67eab5c71fc1904e04b587046218e36106e07eabde libjpeg.tar.gz
fetch https://download.savannah.gnu.org/releases/freetype/freetype-2.9.1.tar.gz \
    ec391504e55498adceb30baceebd147a6e963f636eb617424bcfc47a169898ce freetype.tar.gz
tar xf libjpeg.tar.gz
tar xf freetype.tar.gz

# libjpeg-turbo 1.5.3 as Chaquopy configures it: --without-turbojpeg --without-simd, jpeg6b API.
jpeg=$work/libjpeg-turbo-1.5.3
cat > "$jpeg/jconfig.h" <<'EOF'
#define JPEG_LIB_VERSION 62
#define LIBJPEG_TURBO_VERSION 1.5.3
#define LIBJPEG_TURBO_VERSION_NUMBER 1005003
#define C_ARITH_CODING_SUPPORTED 1
#define D_ARITH_CODING_SUPPORTED 1
#define BITS_IN_JSAMPLE 8
#define HAVE_LOCALE_H 1
#define HAVE_STDDEF_H 1
#define HAVE_STDLIB_H 1
#define HAVE_UNSIGNED_CHAR 1
#define HAVE_UNSIGNED_SHORT 1
#define MEM_SRCDST_SUPPORTED 1
#define NEED_SYS_TYPES_H 1
EOF
cat > "$jpeg/jconfigint.h" <<'EOF'
#define BUILD "godlo"
#define INLINE inline __attribute__((always_inline))
#define PACKAGE_NAME "libjpeg-turbo"
#define VERSION "1.5.3"
#define SIZEOF_SIZE_T 8
EOF
jpeg_sources=(jcapimin jcapistd jccoefct jccolor jcdctmgr jchuff jcinit jcmainct jcmarker
    jcmaster jcomapi jcparam jcphuff jcprepct jcsample jctrans jdapimin jdapistd jdatadst
    jdatasrc jdcoefct jdcolor jddctmgr jdhuff jdinput jdmainct jdmarker jdmaster jdmerge
    jdphuff jdpostct jdsample jdtrans jerror jfdctflt jfdctfst jfdctint jidctflt jidctfst
    jidctint jidctred jquant1 jquant2 jutils jmemmgr jmemnobs jaricom jcarith jdarith
    jsimd_none)

# FreeType 2.9.1 as Chaquopy configures it: system zlib, no bzip2, png or harfbuzz.
ft=$work/freetype-2.9.1
ft_sources=(base/ftsystem base/ftinit base/ftdebug base/ftbase base/ftbbox base/ftglyph
    base/ftbdf base/ftbitmap base/ftcid base/ftfstype base/ftgasp base/ftgxval base/ftmm
    base/ftotval base/ftpatent base/ftpfr base/ftstroke base/ftsynth base/fttype1
    base/ftwinfnt bdf/bdf cff/cff cid/type1cid pcf/pcf pfr/pfr sfnt/sfnt truetype/truetype
    type1/type1 type42/type42 winfonts/winfnt raster/raster smooth/smooth autofit/autofit
    cache/ftcache gzip/ftgzip lzw/ftlzw gxvalid/gxvalid otvalid/otvalid psaux/psaux
    pshinter/pshinter psnames/psnames)

wheel() { # name version abi lib license
    python3 - "$@" "$out" <<'EOF'
import base64, hashlib, sys, zipfile
name, version, abi, lib, license, out = sys.argv[1:]
dist = f"{name}-{version}"
tag = f"py3-none-android_24_{abi.replace('-', '_')}"
files = {
    f"chaquopy/lib/{lib.rsplit('/', 1)[1]}": open(lib, "rb").read(),
    f"{dist}.dist-info/{license.rsplit('/', 1)[1]}": open(license, "rb").read(),
    f"{dist}.dist-info/METADATA": (
        f"Metadata-Version: 2.1\nName: {name.replace('_', '-')}\nVersion: {version}\n"
        "Summary: Rebuilt for 16 KB page sizes by Godlo (native/pillow-libs/build.sh)\n"
    ).encode(),
    f"{dist}.dist-info/WHEEL": (
        f"Wheel-Version: 1.0\nGenerator: godlo\nRoot-Is-Purelib: false\nTag: {tag}\n"
    ).encode(),
}
record = "".join(
    f"{path},sha256={base64.urlsafe_b64encode(hashlib.sha256(data).digest()).rstrip(b'=').decode()},{len(data)}\n"
    for path, data in files.items()
) + f"{dist}.dist-info/RECORD,,\n"
files[f"{dist}.dist-info/RECORD"] = record.encode()
with zipfile.ZipFile(f"{out}/{dist}-{tag}.whl", "w", zipfile.ZIP_DEFLATED) as whl:
    for path, data in files.items():
        info = zipfile.ZipInfo(path, (2020, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        whl.writestr(info, data)
EOF
}

mkdir -p "$out"
for pair in "${abis[@]}"; do
    abi=${pair%%:*}
    cc="$toolchain/${pair##*:}$api-clang"
    mkdir -p "$work/$abi"

    for src in "${jpeg_sources[@]}"; do
        "$cc" "${cflags[@]}" -I"$jpeg" -c "$jpeg/$src.c" -o "$work/$abi/jpeg-$src.o"
    done
    "$cc" "${ldflags[@]}" -Wl,-soname,libjpeg_chaquopy.so -o "$work/$abi/libjpeg_chaquopy.so" "$work/$abi"/jpeg-*.o
    "$toolchain/llvm-strip" --strip-unneeded "$work/$abi/libjpeg_chaquopy.so"
    wheel chaquopy_libjpeg 1.5.3+16k "$abi" "$work/$abi/libjpeg_chaquopy.so" "$jpeg/LICENSE.md"

    for src in "${ft_sources[@]}"; do
        "$cc" "${cflags[@]}" -I"$ft/include" -DFT2_BUILD_LIBRARY -DFT_CONFIG_OPTION_SYSTEM_ZLIB \
            -c "$ft/src/$src.c" -o "$work/$abi/ft-${src//\//-}.o"
    done
    "$cc" "${ldflags[@]}" -Wl,-soname,libfreetype.so -o "$work/$abi/libfreetype.so" "$work/$abi"/ft-*.o -lz
    "$toolchain/llvm-strip" --strip-unneeded "$work/$abi/libfreetype.so"
    wheel chaquopy_freetype 2.9.1+16k "$abi" "$work/$abi/libfreetype.so" "$ft/docs/FTL.TXT"
done
ls -l "$out"
