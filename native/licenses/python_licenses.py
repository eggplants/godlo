"""List the Python packages Chaquopy installed, with their licenses, for the app to show.

The OSS Licenses Gradle Plugin covers the Maven dependencies whose POM names a license. This
covers what pip put in the APK, and what neither list names: Python and Chaquopy themselves,
FFmpeg and QuickJS, and the Maven dependencies whose license only their parent POM gives.

Usage: python_licenses.py <pip dir with *.dist-info> <output json>
"""

from __future__ import annotations

import email
import json
import sys
from pathlib import Path

#: Chaquopy's builds of native libraries carry no license metadata.
OVERRIDES = {
    "chaquopy-libffi": ("MIT", "https://sourceware.org/libffi/"),
    "chaquopy-libjpeg": ("IJG AND BSD-3-Clause AND Zlib", "https://libjpeg-turbo.org/"),
    "chaquopy-freetype": ("FTL OR GPL-2.0-or-later", "https://freetype.org/"),
}

APACHE_2 = "Apache-2.0"

#: Bundled, but not as a Python package or a Maven dependency the plugin reads.
EXTRAS = [
    {
        "name": "Chaquopy",
        "version": "17.0.0",
        "license": "MIT",
        "url": "https://chaquo.com/chaquopy/",
        "text": "",
    },
    {
        "name": "youtubedl-android",
        "version": "0.18.1",
        "license": "GPL-3.0",
        "url": "https://github.com/JunkFood02/youtubedl-android",
        "text": "",
    },
    *(
        {"name": name, "version": version, "license": APACHE_2, "url": url, "text": ""}
        for name, version, url in (
            ("Apache Commons Compress", "1.28.0", "https://commons.apache.org/proper/commons-compress/"),
            ("Apache Commons IO", "2.20.0", "https://commons.apache.org/proper/commons-io/"),
            ("Apache Commons Codec", "1.19.0", "https://commons.apache.org/proper/commons-codec/"),
            ("Apache Commons Lang", "3.18.0", "https://commons.apache.org/proper/commons-lang/"),
        )
    ),
    {
        "name": "Python",
        "version": "3.13",
        "license": "PSF-2.0",
        "url": "https://www.python.org/",
        "text": "",
    },
    {
        "name": "FFmpeg (youtubedl-android)",
        "version": "",
        "license": "GPL-2.0-or-later (built with GPL components such as x264 and x265)",
        "url": "https://github.com/JunkFood02/youtubedl-android",
        "text": "",
    },
    {
        "name": "QuickJS (youtubedl-android)",
        "version": "",
        "license": "MIT",
        "url": "https://bellard.org/quickjs/",
        "text": "",
    },
]

LICENSE_FILES = ("LICENSE", "LICENCE", "COPYING", "NOTICE", "FTL", "AUTHORS")


def license_of(meta: email.message.Message) -> str:
    """The SPDX expression when there is one, else the License field, else the classifiers."""
    if meta.get("License-Expression"):
        return meta["License-Expression"]
    field = (meta.get("License") or "").strip()
    # Some packages paste the whole license text into the field.
    if field and "\n" not in field and len(field) < 80:
        return field
    classifiers = [c.split("::")[-1].strip() for c in meta.get_all("Classifier") or [] if c.startswith("License ::")]
    return " / ".join(classifiers) or field.splitlines()[0] if field else " / ".join(classifiers)


def url_of(meta: email.message.Message) -> str:
    if meta.get("Home-page"):
        return meta["Home-page"]
    urls = [u.split(",", 1)[-1].strip() for u in meta.get_all("Project-URL") or []]
    preferred = [
        u.split(",", 1)[-1].strip()
        for u in meta.get_all("Project-URL") or []
        if u.split(",", 1)[0].strip().lower() in ("homepage", "home", "source", "repository", "source code", "code")
    ]
    return (preferred or urls or [""])[0]


def text_of(dist_info: Path) -> str:
    files = sorted(
        f
        for f in dist_info.rglob("*")
        if f.is_file() and f.name.upper().startswith(LICENSE_FILES)
    )
    return "\n\n".join(f.read_text(encoding="utf-8", errors="replace").strip() for f in files)


def main(pip_dir: str, output: str) -> None:
    entries = []
    for dist_info in sorted(Path(pip_dir).glob("*.dist-info")):
        meta = email.message_from_string((dist_info / "METADATA").read_text(encoding="utf-8"))
        name = meta["Name"]
        license_, url = OVERRIDES.get(name.lower(), (license_of(meta), url_of(meta)))
        entries.append(
            {"name": name, "version": meta["Version"], "license": license_, "url": url, "text": text_of(dist_info)}
        )
    entries += EXTRAS
    entries.sort(key=lambda e: e["name"].lower())
    out = Path(output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(entries, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main(*sys.argv[1:])
