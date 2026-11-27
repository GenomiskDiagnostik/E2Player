# Android caption probe

The companion reads the caption area of a user-shared Android app window using Tesseract with bundled Danish and English models. Frames stay in memory. The app serves the latest caption on IPv4 loopback over WebSocket on port `47842` and HTTP on port `47841`. It opens no listener on Wi-Fi or mobile interfaces and sends no data to remote services. [Even Hub pairing instructions](../README.md#connect-on-android).

## Build locally

Use JDK 17 and Android SDK Platform 36. Set `ANDROID_HOME` to the Android SDK location. The first build downloads the pinned dependencies and two checksum-verified Tesseract models.

```powershell
.\android\gradlew.bat -p android testCaptionsDebugUnitTest lintCaptionsDebug assembleCaptionsDebug
```

On macOS and Linux, use `./android/gradlew` with the same arguments. The captions APK is `android/app/build/outputs/apk/captions/debug/app-captions-debug.apk`. It retains OCR, G2 pairing and the language picker. It omits Android notification access and the system playback commands. It uses the same application ID as version 0.2.0.

The `mediaControls` variant retains Android media-session discovery and transport. Use `testMediaControlsDebugUnitTest lintMediaControlsDebug assembleMediaControlsDebug` to verify it. The notification-listener service can trigger Google Play Protect blocks when the APK is installed from a browser, messaging app or file manager. Keep Play Protect enabled and use the captions APK for direct installation. For a suspected false positive, use Google's developer appeal process. [Google Play Protect warnings](https://support.google.com/work/android/answer/15162069?hl=en-GB).

## Test on an Android phone

Use Android 14 QPR2 or newer with single-app sharing. Start with owned or synthetic captions. For a basic check, use a test video with distinct cues and a pause between them.

1. Install the debug APK and open **E2Player Caption Probe** beside the player in split screen.
2. Select **Choose app to share** and select the player under single-app sharing. Turn captions on in the player and dismiss its controls.
3. Watch the detected text during playback, pause, seeking and cue changes. The probe looks for white text in the central 80% of the window width and 58–96% of its height. Use the default white caption color and a dark caption background where available. Empty reads and observations older than two seconds clear the text.
4. Hide the player, rotate the phone and stop sharing. Confirm that the old caption clears and that starting again requires the system's consent.

To test the Even Hub link, pair once using the companion's code on the same phone. Stop and restart sharing, and reopen the plugin to confirm it reconnects with the saved code. Exercise **Subtitles only** and verify that stopping sharing clears the text in either layout. With the media-controls variant, enable notification access from **Media control access** before testing commands against the active player. Some players offer only a subset of the transport actions.

The app picker can also offer full-screen sharing. Select a single app to keep other windows outside the capture. Android can withhold protected frames. Visible text inside the crop can be mistaken for a caption, and OCR can miss words and punctuation.

## Subtitle history and export

Open **Subtitle history** after stopping screen sharing. Select a session and choose **SRT** or **TXT**. The preview is selectable. **Copy text** copies the selected format, and **Save file…** opens Android's file picker. For very long sessions, save the complete file and use the preview to copy excerpts. **Delete session** removes the copy inside E2Player.

Sessions with recognized text are saved as cues in the app's private storage, excluded from backup and device transfer. The history contains recognized text and timestamps. TXT exports one line per cue. SRT uses the elapsed time since Android screen sharing started, including gaps, so a session with pauses or seeks follows the capture timeline. A force stop can omit the last unfinished cue.

On Android 15 QPR1 and later, locking the phone ends screen projection. E2Player closes and saves that session. Keep the shared player visible to continue reading captions. [Android MediaProjection](https://developer.android.com/media/grow/media-projection#status-bar-chip-and-auto-stop).

## Dependencies

The APK uses [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0) and [Tesseract fast models](https://github.com/tesseract-ocr/tessdata_fast/tree/65727574dfcd264acbb0c3e07860e4e9e9b22185). See their licenses and Tesseract4Android's native dependency notices before distributing the probe.
