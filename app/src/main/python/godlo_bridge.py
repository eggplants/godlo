"""The one module Kotlin calls: pick a tool for a URL, run it, report back.

Every tool saves under `<root>/<site>/`, where root is the tool's directory
(`Download/Godlo/<tool>` unless the user moved it) and site is the URL's host
without `www.`. The Kotlin side hands over a
`callback` object (see `PythonBridge.kt`) that takes progress, the files
written and log lines, and says whether the user cancelled.
"""

from __future__ import annotations

import json
import logging
import os
import sys
import traceback
from pathlib import Path
from urllib.parse import urlparse

ENGINES = ("yt-dlp", "gallery-dl", "getjmanga")
KINDS = ("image", "audio", "video")

_env: dict[str, str] = {}

#: What this module writes for the app to show, in the app's UI languages; English is the fallback.
_MESSAGES = {
    "en": {
        "fetching": "Fetching info",
        "eta": "{eta} left",
        "converting": "Converting: {step}",
        "unsupported": "gallery-dl does not support this URL",
        "picture": "Picture {n}: {name}",
        "existing": "Already saved: {name}",
        "saved_pictures": "{n} pictures saved",
        "gallery_failed": "gallery-dl failed (status {status})",
        "episodes": "{n} episodes",
        "pages": "{done}/{total} pages",
        "locked": "Locked: {title}",
        "already_saved": "Already saved: {title}",
        "nothing_readable": "No readable episode (all locked)",
    },
    "ja": {
        "fetching": "情報を取得中",
        "eta": "残り {eta}",
        "converting": "変換中: {step}",
        "unsupported": "gallery-dl はこの URL に対応していません",
        "picture": "{n} 枚目: {name}",
        "existing": "既存: {name}",
        "saved_pictures": "{n} 枚保存",
        "gallery_failed": "gallery-dl が失敗しました (status {status})",
        "episodes": "{n} 話",
        "pages": "{done}/{total} ページ",
        "locked": "ロック中: {title}",
        "already_saved": "保存済み: {title}",
        "nothing_readable": "読めるエピソードがありません (ロック中)",
    },
}
_lang = "en"


def _t(key: str, **values: object) -> str:
    """The message `key` in the language of the download being run."""
    return _MESSAGES.get(_lang, _MESSAGES["en"])[key].format(**values)


class Cancelled(BaseException):
    """Raised from a progress hook when the user cancels.

    A BaseException, so the tools' broad `except Exception` blocks let it through.
    """


def setup(env_json: str) -> str:
    """Remember the paths Kotlin prepared, and point TLS at certifi's CA bundle.

    Args:
        env_json: `{"ffmpeg", "ffmpeg_lib_dir", "qjs", "cache_dir", "config_dir", "packages_dir"}`.

    Returns:
        The tools' versions, as JSON.
    """
    _env.update(json.loads(env_json))
    packages = _env.get("packages_dir")
    # Tools updated at runtime (see `update_tools`) shadow the ones built into the APK.
    if packages and packages not in sys.path:
        sys.path.insert(0, packages)

    _shim_cryptography()

    import certifi

    os.environ.setdefault("SSL_CERT_FILE", certifi.where())
    lib_dir = _env.get("ffmpeg_lib_dir")
    if lib_dir:
        os.environ["LD_LIBRARY_PATH"] = lib_dir
    return versions()


def _shim_cryptography() -> None:
    """Serve `cryptography.hazmat.decrepit` on cryptography 42, the newest Chaquopy builds.

    cryptography 43 moved the legacy ciphers (ARC4, TripleDES, ...) there, and
    getjmanga imports ARC4 from the new place. 42 still has them where they were.
    """
    try:
        import cryptography.hazmat.decrepit.ciphers.algorithms  # noqa: F401
    except ImportError:
        import types
        import warnings

        from cryptography.hazmat.primitives.ciphers import algorithms

        for name in ("cryptography.hazmat.decrepit", "cryptography.hazmat.decrepit.ciphers"):
            module = types.ModuleType(name)
            module.__path__ = []
            sys.modules.setdefault(name, module)
        legacy = types.ModuleType("cryptography.hazmat.decrepit.ciphers.algorithms")
        with warnings.catch_warnings():
            # Reaching them at the old place is deprecated, which is the point here.
            warnings.simplefilter("ignore")
            for name in ("ARC4", "TripleDES", "Blowfish", "CAST5", "IDEA", "SEED", "RC2"):
                if hasattr(algorithms, name):
                    setattr(legacy, name, getattr(algorithms, name))
        sys.modules[legacy.__name__] = legacy


def versions() -> str:
    """The versions of the three tools, as JSON."""
    out = {}
    for name, module in (("yt-dlp", "yt_dlp.version"), ("gallery-dl", "gallery_dl.version"), ("getjmanga", "getjmanga")):
        try:
            mod = __import__(module, fromlist=["__version__"])
            out[name] = mod.__version__
        except Exception as exc:  # noqa: BLE001
            out[name] = f"error: {exc}"
    return json.dumps(out)


def site_of(url: str) -> str:
    """The directory name for the site `url` is on: its host, without `www.` or `m.`."""
    host = (urlparse(url.strip()).hostname or "unknown").lower()
    for prefix in ("www.", "m.", "mobile."):
        if host.startswith(prefix) and host.count(".") > 1:
            host = host[len(prefix) :]
    return host


def detect(url: str) -> str:
    """Pick the tool and the kind of media for `url`, and list every tool that can take it.

    getjmanga first, since its sites are manga; then gallery-dl for image
    sites; yt-dlp for everything else.

    Returns:
        `{"engine", "kind", "site", "engines"}` as JSON; `engines` is best first.
    """
    url = url.strip()
    engines = supported_engines(url)
    engine = engines[0]
    kind = "video" if engine == "yt-dlp" else "image"
    return json.dumps({"engine": engine, "kind": kind, "site": site_of(url), "engines": engines})


def supported_engines(url: str) -> list[str]:
    """The tools with an extractor for `url`, best first; yt-dlp when none has.

    yt-dlp's generic extractor takes any page, and gallery-dl's directlink any
    file link, so neither counts as knowing a site: yt-dlp is left out only when
    another tool knows the site and yt-dlp does not.
    """
    engines = []
    knows_site = False
    try:
        from getjmanga import find_extractor

        find_extractor(url)
        engines.append("getjmanga")
        knows_site = True
    except Exception:  # noqa: BLE001
        pass
    try:
        from gallery_dl import extractor

        found = extractor.find(url)
        if found is not None:
            engines.append("gallery-dl")
            knows_site = knows_site or found.category != "directlink"
    except Exception:  # noqa: BLE001
        pass
    if not knows_site or _ytdlp_knows(url):
        engines.append("yt-dlp")
    return engines


def _ytdlp_knows(url: str) -> bool:
    """Whether one of yt-dlp's site extractors, not the generic one, takes `url`."""
    try:
        from yt_dlp.extractor import gen_extractor_classes

        return any(ie.ie_key() != "Generic" and ie.suitable(url) for ie in gen_extractor_classes())
    except Exception:  # noqa: BLE001
        return True


def download(request_json: str, callback: object) -> str:
    """Run one download.

    Args:
        request_json: See `DownloadRequest` in Kotlin.
        callback: `progress(fraction, detail)`, `title(text)`, `file(path)`,
            `log(text)` and `cancelled()`; fraction is negative when unknown.

    Returns:
        `{"status": "ok" | "cancelled" | "error", "message"}` as JSON.
    """
    request = json.loads(request_json)
    global _lang  # noqa: PLW0603 -- one download runs at a time
    _lang = request.get("lang") or "en"
    handler = _CallbackLogHandler(callback)
    root_logger = logging.getLogger()
    root_logger.addHandler(handler)
    root_logger.setLevel(logging.INFO)
    try:
        engine = request["engine"]
        if engine == "yt-dlp":
            _run_ytdlp(request, callback)
        elif engine == "gallery-dl":
            _run_gallery_dl(request, callback)
        elif engine == "getjmanga":
            _run_getjmanga(request, callback)
        else:
            msg = f"unknown engine: {engine}"
            raise ValueError(msg)
    except Cancelled:
        return json.dumps({"status": "cancelled", "message": ""})
    except BaseException as exc:  # noqa: BLE001
        if _is_cancel(exc):
            return json.dumps({"status": "cancelled", "message": ""})
        callback.log(traceback.format_exc())
        return json.dumps({"status": "error", "message": _clean(str(exc)) or exc.__class__.__name__})
    finally:
        root_logger.removeHandler(handler)
    return json.dumps({"status": "ok", "message": ""})


def _is_cancel(exc: BaseException) -> bool:
    """Whether `exc` is, or wraps, a `Cancelled`: yt-dlp re-raises hook errors as its own."""
    seen = set()
    while exc is not None and id(exc) not in seen:
        if isinstance(exc, Cancelled):
            return True
        seen.add(id(exc))
        exc = getattr(exc, "exc_info", (None, None))[1] or exc.__cause__ or exc.__context__
    return False


def _clean(message: str) -> str:
    """Drop the ANSI colours yt-dlp puts in its error messages."""
    import re

    return re.sub(r"\x1b\[[0-9;]*m", "", message).strip()


class _CallbackLogHandler(logging.Handler):
    """Send the tools' warnings and errors to the Kotlin log."""

    def __init__(self, callback: object) -> None:
        super().__init__(logging.WARNING)
        self.callback = callback

    def emit(self, record: logging.LogRecord) -> None:
        try:
            self.callback.log(f"{record.levelname.lower()}: {record.getMessage()}")
        except Exception:  # noqa: BLE001
            pass


def _check(callback: object) -> None:
    if callback.cancelled():
        raise Cancelled


def _target_dir(request: dict, site: str) -> Path:
    path = Path(request["root"]) / site
    path.mkdir(parents=True, exist_ok=True)
    return path


# yt-dlp ---------------------------------------------------------------------


class _YtdlpLogger:
    def __init__(self, callback: object) -> None:
        self.callback = callback

    def debug(self, msg: str) -> None:
        if not msg.startswith("[debug] "):
            self.info(msg)

    def info(self, msg: str) -> None:
        pass

    def warning(self, msg: str) -> None:
        self.callback.log(_clean(msg))

    def error(self, msg: str) -> None:
        self.callback.log(_clean(msg))


def _run_ytdlp(request: dict, callback: object) -> None:
    import yt_dlp

    audio = request["kind"] == "audio"
    url = request["url"].strip()
    site = site_of(url)
    out_dir = _target_dir(request, site)
    playlist = bool(request.get("playlist"))
    template = "%(title).150B [%(id)s].%(ext)s"
    if playlist:
        template = "%(playlist_title,playlist_id|playlist).100B/%(playlist_index|0)03d %(title).120B [%(id)s].%(ext)s"

    def progress_hook(d: dict) -> None:
        _check(callback)
        if d.get("status") == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            done = d.get("downloaded_bytes") or 0
            fraction = done / total if total else -1.0
            parts = []
            info = d.get("info_dict") or {}
            if playlist and info.get("playlist_index") and info.get("n_entries"):
                parts.append(f"{info['playlist_index']}/{info['n_entries']}")
            speed, eta = _clean(d.get("_speed_str") or ""), _clean(d.get("_eta_str") or "")
            if speed and "Unknown" not in speed:
                parts.append(speed)
            if eta and "Unknown" not in eta:
                parts.append(_t("eta", eta=eta))
            callback.progress(fraction, "  ".join(parts))
            title = info.get("title")
            if title:
                callback.title(title)

    def postprocessor_hook(d: dict) -> None:
        _check(callback)
        if d.get("status") == "started" and d.get("postprocessor") != "MoveFiles":
            callback.progress(-1.0, _t("converting", step=d.get("postprocessor", "")))

    def post_hook(path: str) -> None:
        # Called once per video with the final path, after every postprocessor.
        callback.file(os.path.abspath(path))

    opts: dict = {
        "paths": {"home": str(out_dir), "temp": str(Path(_env["cache_dir"]) / "yt-dlp")},
        "outtmpl": {"default": template, "thumbnail": template, "pl_thumbnail": ""},
        "noplaylist": not playlist,
        "ignoreerrors": "only_download" if playlist else False,
        "logger": _YtdlpLogger(callback),
        "progress_hooks": [progress_hook],
        "postprocessor_hooks": [postprocessor_hook],
        "post_hooks": [post_hook],
        "noprogress": True,
        "windowsfilenames": False,
        "trim_file_name": 200,
        "overwrites": False,
        "continuedl": True,
        "retries": 5,
        "fragment_retries": 10,
        "concurrent_fragment_downloads": 4,
        "writethumbnail": True,
        "cachedir": str(Path(_env["cache_dir"]) / "yt-dlp-cache"),
    }
    if _env.get("ffmpeg"):
        opts["ffmpeg_location"] = _env["ffmpeg"]
    if _env.get("qjs"):
        opts["js_runtimes"] = {"quickjs": {"path": _env["qjs"]}}
    cookies = request.get("cookies")
    if cookies and os.path.exists(cookies):
        opts["cookiefile"] = cookies

    postprocessors: list[dict] = []
    if audio:
        codec = request.get("audio_format") or "mp3"
        opts["format"] = "ba[ext=m4a]/ba/b" if codec == "m4a" else "ba/b"
        pp = {"key": "FFmpegExtractAudio", "preferredcodec": codec}
        if codec == "mp3":
            pp["preferredquality"] = "0"
        postprocessors.append(pp)
    else:
        height = str(request.get("video_quality") or "best").rstrip("p")
        # Prefer H.264 + AAC so every Android device can play the result without a transcode.
        sort = ["vcodec:h264", "acodec:aac", "ext:mp4:m4a"]
        if height.isdigit():
            sort.insert(0, f"res:{height}")
        opts["format"] = "bv*+ba/b"
        opts["format_sort"] = sort
        opts["merge_output_format"] = "mp4/mkv"
    postprocessors += [
        {"key": "FFmpegMetadata", "add_metadata": True, "add_chapters": True},
        {"key": "EmbedThumbnail", "already_have_thumbnail": False},
    ]
    opts["postprocessors"] = postprocessors

    callback.progress(-1.0, _t("fetching"))
    with yt_dlp.YoutubeDL(opts) as ydl:
        ydl.extract_info(url, download=True)


# gallery-dl -----------------------------------------------------------------


def _run_gallery_dl(request: dict, callback: object) -> None:
    from gallery_dl import config, extractor, job, output

    url = request["url"].strip()
    site = site_of(url)
    base = _target_dir(request, site)

    config.clear()
    config_file = Path(request.get("config_dir") or _env["config_dir"]) / "gallery-dl.conf"
    if config_file.exists():
        config.load([str(config_file)])
    config.set(("extractor",), "base-directory", str(base) + os.sep)
    config.set(("output",), "mode", "null")
    config.set(("output",), "progress", False)
    cookies = request.get("cookies")
    if cookies and os.path.exists(cookies):
        config.set(("extractor",), "cookies", cookies)

    # The site directory stands in for gallery-dl's leading "{category}".
    first = extractor.find(url)
    if first is None:
        msg = _t("unsupported")
        raise ValueError(msg)
    fmt = first.directory_fmt
    if isinstance(fmt, (list, tuple)) and fmt and fmt[0] == "{category}":
        config.set(("extractor", first.category), "directory", list(fmt[1:]) or [""])

    class Out(output.NullOutput):
        count = 0

        def start(self, path: str) -> None:
            _check(callback)
            callback.progress(-1.0, _t("picture", n=Out.count + 1, name=os.path.basename(path)))

        def skip(self, path: str) -> None:
            _check(callback)
            # Already on disk, but still what this download is: the app opens it from here.
            callback.file(path)
            callback.progress(-1.0, _t("existing", name=os.path.basename(path)))

        def success(self, path: str) -> None:
            Out.count += 1
            callback.file(path)
            callback.progress(-1.0, _t("saved_pictures", n=Out.count))

        def progress(self, bytes_total, bytes_downloaded, bytes_per_second) -> None:  # noqa: ANN001
            _check(callback)

    class Job(job.DownloadJob):
        def __init__(self, url, parent=None):  # noqa: ANN001
            super().__init__(url, parent)
            self.out = Out()

        def handle_directory(self, kwdict):  # noqa: ANN001
            _check(callback)
            title = kwdict.get("title") or kwdict.get("gallery") or kwdict.get("user", {})
            if isinstance(title, dict):
                title = title.get("name") or title.get("nick")
            if title:
                callback.title(str(title))
            super().handle_directory(kwdict)

    callback.progress(-1.0, _t("fetching"))
    status = Job(url).run()
    if status and not Out.count:
        msg = _t("gallery_failed", status=status)
        raise RuntimeError(msg)


# getjmanga ------------------------------------------------------------------


def _run_getjmanga(request: dict, callback: object) -> None:
    from getjmanga import cli, downloader
    from getjmanga.config import load_config
    from getjmanga.console import Display

    url = request["url"].strip()
    # getjmanga makes the <site>/ directory itself.
    root = Path(request["root"])
    root.mkdir(parents=True, exist_ok=True)

    # Same site naming as everything else: the host without www.
    downloader.Downloader._site = lambda self, episode: site_of(episode.url)  # noqa: SLF001

    class Report(Display):
        #: The episode whose pages are being written, until it is finished.
        current = None

        def work(self, url: str) -> None:
            callback.progress(-1.0, _t("fetching"))

        def series(self, total: int) -> None:
            callback.log(_t("episodes", n=total))

        def fetching(self, url: str) -> None:
            _check(callback)

        def pages(self, episode, done: int, total: int) -> None:  # noqa: ANN001
            Report.current = episode
            _check(callback)
            callback.title(f"{episode.series_title} {episode.episode_title}")
            callback.progress(done / total if total else -1.0, _t("pages", done=done, total=total))

        def finished(self, result) -> None:  # noqa: ANN001
            Report.current = None
            # Set here too: an episode that was already saved never reaches pages().
            callback.title(f"{result.episode.series_title} {result.episode.episode_title}")
            if result.status == "locked":
                callback.log(_t("locked", title=result.episode.episode_title))
                return
            if result.status == "exists":
                callback.log(_t("already_saved", title=result.episode.episode_title))
            callback.file(str(result.save_dir))

        def done(self) -> None:
            pass

    args = [url, "-d", str(root), "-F", request.get("image_format") or "jpg"]
    if request.get("previous"):
        args.append("--both")  # the previous episodes as well as the next ones
    else:
        args.append("--bulk" if request.get("playlist") else "--no-bulk")
    if request.get("cbz"):
        args.append("--cbz")
    parsed = cli.parse_args(args)
    config_file = Path(request.get("config_dir") or _env["config_dir"]) / "getjmanga.toml"
    config = load_config(config_file if config_file.exists() else Path(os.devnull))
    cli.apply_config(parsed, config)
    parsed.savedir = str(root)
    runner = cli.Runner(parsed, config, None, Report())
    try:
        results = runner.run(url)
    except BaseException:
        # getjmanga counts an episode directory that exists as saved, so a
        # half-written one would never be completed by a later run.
        episode = Report.current
        if episode is not None:
            import shutil

            partial = root / site_of(episode.url) / downloader._dirname(episode.series_title) / downloader._dirname(episode.episode_title)  # noqa: SLF001
            shutil.rmtree(partial, ignore_errors=True)
        raise
    if results and all(r.status == "locked" for r in results):
        msg = _t("nothing_readable")
        raise RuntimeError(msg)


# updates --------------------------------------------------------------------


def update_tools() -> str:
    """Install the newest yt-dlp, gallery-dl and getjmanga into `packages_dir`.

    Only the tools themselves: their dependencies stay the ones in the APK,
    which keeps native packages at the versions Chaquopy builds.

    Returns:
        `{"status", "message"}` as JSON.
    """
    import io

    from pip._internal.cli.main import main as pip_main

    target = _env["packages_dir"]
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout = sys.stderr = buf = io.StringIO()
    try:
        code = pip_main(
            ["install", "--upgrade", "--no-deps", "--no-cache-dir", "--target", target, "yt-dlp", "yt-dlp-ejs", "gallery-dl", "getjmanga"]
        )
    except SystemExit as exc:
        code = exc.code
    finally:
        sys.stdout, sys.stderr = old_out, old_err
    status = "ok" if not code else "error"
    return json.dumps({"status": status, "message": buf.getvalue()[-4000:]})
