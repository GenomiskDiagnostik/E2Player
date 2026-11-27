# E2Player

E2Player displays captions from an Android video player on Even G2 glasses. It uses an Even Hub plugin and a companion app on the same Android phone. You can use the glasses or an Even R1 ring to navigate the controls.

The project goals also include Wear-style control of the phone's active media session, G2 voice search for YouTube, and synced captions on the glasses for eligible videos. [PLANS.md](PLANS.md) records the platform constraints and the acceptance checks.

## Install E2Player

1. **Even G2 plugin.** In the Even Realities app, open the **Even Hub** tab, search for **E2Player**, and tap **Install** when the public listing is available. If you have a beta invitation, follow the invitation and install the app from the beta section. [Even Hub installation guide](https://support.evenrealities.com/hc/en-us/articles/15688149217167-Even-Hub)
2. **Android companion.** Use an Android phone with Android 14 QPR2 or later and build the APK from this repository. Both APK flavors show captions and export subtitle history. The media-controls build also supports playback commands after you grant it **Media control access** on the phone. [Android build and installation guide](android/README.md#build-and-install-the-android-app)
3. **Versions.** Use the Even Hub plugin 0.2.3 or later for persistent pairing and **Subtitles only**. Android companion 0.2.4 adds local subtitle history and export.

## Pair and watch

1. Open the Android companion and tap **Choose app to share**. Select the player in Android's single-app sharing picker, then turn on captions in that player.
2. For the first pairing, return to the companion and tap **Copy glasses code**. Open E2Player in the Even Realities app, select **Android**, paste the code, and tap **Connect**.
3. Return to the player and keep its captions visible. Use split screen with the Even Realities app if the phone suspends the plugin in the background. Locking the phone can end screen sharing, and Android asks for consent again when you start a new session. [Android screen-sharing behavior](https://developer.android.com/media/grow/media-projection#status-bar-chip-and-auto-stop)

Swipe on G2 or R1 to select a control and tap to activate it. **Subtitles only** moves captions to the bottom and hides the rest of the display. Tap once to show the controls again. With the media-controls build, playback buttons appear when the active player advertises the matching actions. Select **Demo** in the Even app to try the controls with bundled test media.

The Even Hub plugin remembers a verified code. Select **Forget pairing** to remove it. Reinstalling the Android companion or clearing its data generates a new code.

## Subtitle history

Open **Subtitle history** in the Android companion, select a finished session and choose **SRT** or **TXT**. Use **Copy text** or **Save file…**. SRT times start at the beginning of screen sharing. Text recognition and session storage run locally on the phone. [History and export guide](android/README.md#subtitle-history-and-export)

Both apps follow the system language by default and provide a **Language** selector for English, Japanese, Danish, German, French, Spanish and Simplified Chinese. This changes the interface language. OCR still uses Danish and English models and shows the words recognized from the selected player. The Even Hub CLI does not accept Danish in the app listing metadata, so Danish is selectable inside E2Player.

## Run locally

Requires Node.js 24 and npm. The first package targets Even Realities App 2.2.10 or later.

```sh
npm ci
npm run dev
```

Open http://127.0.0.1:5173 for the phone preview. With the dev server still running, start the official simulator from a second terminal.

```sh
npm run simulator
```

Select **Demo** on the phone to exercise transport without the Android companion. Swipe up or down to move through the bottom controls and tap to select. A double tap opens the Even system exit confirmation. Demo Stop rewinds to the start, and the queue wraps in both directions. If the WebView blocks the first Play gesture from the glasses, tap **Play** on the phone.

The fixtures are synthetic and bundled with the app. The video shows a moving test pattern and three ascending tones. The audio track contains three descending tones. Both have three four-second caption cues.

The device adapter handles taps delivered as either container events or system events with a touch source. In development, it rebuilds a retained glasses page if the simulator rejects startup during a reload. See Even Hub reports [#30](https://github.com/even-realities/everything-evenhub/issues/30) and [#32](https://github.com/even-realities/everything-evenhub/issues/32).

## Build and package

```sh
npm test
npm run pack
```

The pack command type-checks, builds, and writes `e2player.ehpk` at the repository root. GitHub Actions verifies the test and pack commands. The network allowlist contains the Android companion's loopback WebSocket on port `47842` and its HTTP fallback on port `47841`.

For a physical-device preview, use [Even's QR testing guide](https://hub.evenrealities.com/docs/test/local-testing) with the phone and development machine on the same network. Use `npm exec -- vite --host 0.0.0.0 --strictPort` for this LAN test. For installation of the packaged build, follow the [Even Hub packaging and beta guide](https://hub.evenrealities.com/docs/ship/packaging).

## Code

- `src/playback.ts` owns the playback state, transport commands and timed captions.
- `src/glasses.ts` adapts SDK gesture events and sends text to the G2 display.
- `src/caption-link.ts` handles pairing, local caption polling and media commands.
- `src/main.ts` binds the mobile display and controls to the same player.
