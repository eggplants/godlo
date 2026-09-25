# Godlo

[![ci](https://github.com/eggplants/godlo/actions/workflows/ci.yml/badge.svg)](https://github.com/eggplants/godlo/actions/workflows/ci.yml)

An Android app that runs [yt-dlp](https://github.com/yt-dlp/yt-dlp),
[gallery-dl](https://github.com/mikf/gallery-dl) and
[getjmanga](https://pypi.org/project/getjmanga/) on the device, and lets you browse, read,
watch and listen to what they saved.

## Features

- **Downloads**: paste a URL or share it from a browser or any other app. Godlo detects which
  tools can take the URL and whether it is video, audio or images. It then queues the download
  and runs it in the background with a progress notification.
  - yt-dlp: video (quality selectable) or audio (format selectable), and whole playlists.
  - gallery-dl: images from the sites it supports.
  - getjmanga: manga episodes, the next and previous ones too, with optional CBZ output.
    Works can be stored for **patrol**, which fetches their new episodes later.
- **Library**: the image, audio and video tabs list everything in the save directories,
  grouped by site and then by title, user or playlist. Files that other apps add or delete show
  up too.
- **Reader**: right-to-left, left-to-right or vertical, with single pages or two-page spreads.
- **Players**: an audio player with background playback, and a video player with
  picture-in-picture.
- **Tool updates**: update yt-dlp, gallery-dl and getjmanga in the app without a new APK.
- English and Japanese UI, light and dark themes, and dynamic color.

## Development

[mise](https://mise.jdx.dev/) required.

```bash
# first
mise install
mise generate git-pre-commit -w

mise run build
mise run test
mise run lint
mise run format
```

## License

[MIT](LICENSE)
