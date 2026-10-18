# ScreenVideo FREE

A lightweight Android screen recorder designed to capture virtual machines (VMs) while they are visible on the device display.

## What changed in 0.2

- Replaced the fragile `/dev/graphics/fb0` + shell + FFmpeg pipeline with Android's MediaProjection screen-capture API and MediaRecorder.
- Records the visible device screen, so an on-screen VM (Virtual Master, Limbo, or another emulator) is captured like a normal screen recorder.
- Uses H.264 video in an MP4 container at 30 FPS.
- Validates the finished recording and deletes invalid/empty output files instead of reporting success for a zero-byte recording.
- Improved the interface with a cleaner dark layout and direct view IDs (no fragile view-order detection).
- Saves recordings in the app-specific Movies directory, which avoids legacy shared-storage permission problems.

## Requirements and limitations

- Android 5.0 (API 21) or newer. MediaProjection is required for reliable screen capture; Android 2.3/4.x are no longer supported by this recording implementation.
- Android displays a system permission prompt before each capture session.
- Keep the VM visible on the phone screen while recording. This records what is displayed, not a VM's internal framebuffer directly.
- This version records video only; system audio and microphone audio are not implemented.
- A recording must run long enough for the encoder to produce valid frames. If finalization fails or the output is too small, the invalid file is deleted and the UI reports the failure.
- Some protected apps may show a black screen because Android prevents screen capture of protected content.

## Build

Open the project in Android Studio and build with the Android SDK:

```sh
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

No FFmpeg binary is bundled or required.