# Android caption probe

This experimental app reads the caption area of a user-shared Android app window using Tesseract with bundled Danish and English models. It displays the latest text in the probe while the source is visible. Processing uses pixels only and requests no microphone, notification-listener, or internet permission. Frames and recognized text are kept in memory. The probe is independent of the Even Hub package and does not send text to the glasses yet.

## Build locally

Use JDK 17 and Android SDK Platform 36. Set `ANDROID_HOME` to the Android SDK location. The first build downloads the pinned dependencies and two checksum-verified Tesseract models.

```powershell
.\android\gradlew.bat -p android testDebugUnitTest lintDebug assembleDebug
```

On macOS and Linux, use `./android/gradlew` with the same arguments. The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

## Test on an Android phone

Use Android 14 QPR2 or newer with single-app sharing. Start with owned or synthetic captions. For a basic check, use a test video with distinct cues and a pause between them.

1. Install the debug APK and open **E2Player Caption Probe** beside the player in split screen.
2. Select **Choose app to share** and select the player under single-app sharing. Turn captions on in the player and dismiss its controls.
3. Watch the detected text during playback, pause, seeking and cue changes. The probe looks for white text in the central 80% of the window width and 58–96% of its height. Use the default white caption color and a dark caption background where available. Empty reads and observations older than two seconds clear the text.
4. Hide the player, rotate the phone and stop sharing. Confirm that the old caption clears and that starting again requires the system's consent.

The app picker can also offer full-screen sharing. Select a single app to keep other windows outside the capture. Android can withhold protected frames. Visible text inside the crop can be mistaken for a caption, and OCR can miss words and punctuation.

## Dependencies

The APK uses [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0) and [Tesseract fast models](https://github.com/tesseract-ocr/tessdata_fast/tree/65727574dfcd264acbb0c3e07860e4e9e9b22185). See their licenses and Tesseract4Android's native dependency notices before distributing the probe.
