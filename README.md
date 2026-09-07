# ScreenVideo FREE

Nostalgic Android framebuffer recorder prototype for Android VMs and old Android devices.

## Target
- minSdkVersion 10 (Android 2.3 / Gingerbread)
- Java
- RGB565 framebuffer input
- Default framebuffer: `/dev/graphics/fb0`
- Default capture size: 480x800
- Default FPS: 4

## Important
The project deliberately does NOT bundle an FFmpeg binary. FFmpeg binaries are platform/build dependent and have separate licensing considerations.

The app can:
1. detect the framebuffer;
2. test/read one RGB565 frame;
3. capture a sequence of frames to a raw `.rgb565` file;
4. optionally invoke an FFmpeg executable if one is available and accessible.

For the VM used during development, the framebuffer was observed as RGB565 little-endian, 480x800, 768000 bytes/frame.

### Permissions
A normal Android application cannot automatically access another process' `/dev/graphics/fb0`.
The VM must expose the framebuffer with permissions that the app UID can read, or a privileged/helper setup must provide access.

### Building
Open the project folder in Android Studio and let Android Studio use/download the Android Gradle Plugin dependencies and an Android SDK.

Build:
`./gradlew assembleDebug`

The resulting APK is under:
`app/build/outputs/apk/debug/`

## UI
The UI intentionally uses a dark, glossy, early-2010s Android/HTC-inspired style without copying HTC assets.

## Current 0.1 limitations
- MP4 encoding depends on an FFmpeg executable being supplied separately.
- Audio capture is not implemented yet.
- Root/privileged framebuffer access is not implemented.
