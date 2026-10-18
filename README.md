# ScreenVideo FREE — Legacy VM Recorder

Video-only screen recorder aimed at Android 1.5 through Android 4.4, for recording the visible screen while a virtual machine/emulator is open.

## APK variants

- `cupcakeDebug`: Android 1.5 / 1.6 era (min SDK 3; target SDK 4), with a glossy, beveled 3D-style camera icon.
- `legacyDebug`: Android 2.0 through 4.4 (min SDK 5; target SDK 19), with a separate glossy blue-green 3D-style camcorder icon.

## Capture methods

- Android 4.4 (API 19): root launches Android's `screenrecord` command to record MP4/H.264.
- Android 1.5–4.3: root framebuffer snapshots from `/dev/graphics/fb0`, then bundled FFmpeg encodes a silent MP4.

**Compatibility caveat:** Android 1.5–4.3 framebuffer devices vary in pixel format, stride, permissions, and node path. This fallback is best-effort and must be tested on each device. It may capture a visible game because it reads the screen framebuffer, but some GPU overlays, protected surfaces, unusual framebuffer formats, or games that pause when backgrounded can prevent reliable capture. Keep the game visible while recording. Root access is required, and a generic APK cannot guarantee capture on every old device.

## Empty-file protection

The app checks FFmpeg's exit status and verifies the MP4 exists and exceeds 1 KiB. Invalid output is deleted and reported as a failure.

## Video only

No microphone or system audio is recorded. On supported devices, capture targets the whole visible display rather than only the recorder app, so a game can be recorded while it is on screen. This is not a guarantee for every device/game; background capture and stable frame rates are especially limited on Android 1.5–4.3.

## Build

```sh
./gradlew assembleCupcakeDebug assembleLegacyDebug
```

Artifacts are generated under `app/build/outputs/apk/`.

## Sharing website

The `docs/` directory is intended for GitHub Pages. A static page can provide previews and share links after a video is uploaded to storage. A real public upload feature needs a configured storage/backend provider; the site must not claim a local preview is already public.