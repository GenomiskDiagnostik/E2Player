# E2Player

E2Player is an Even Hub plugin for Even G2 and the R1 ring. Its default **Android** view connects to the caption companion on the same phone. It displays player-rendered captions recognized by the companion. **Demo** runs the bundled test video and audio independently.

The project goals also include Wear-style control of the phone's active media session, G2 voice search for YouTube, and synced captions on the glasses for eligible videos. [PLANS.md](PLANS.md) records the platform constraints and the acceptance checks.

## Connect on Android

Install the [captions APK](android/README.md#build-locally) and the E2Player `.ehpk` (0.2.0 or later). Start single-app sharing in the companion, select the player and turn on captions in that player. Return to the companion, tap **Copy glasses code**, then paste it into the **Android** view in E2Player in the Even app and tap **Connect**. Return to the player. Use split screen with the Even app if Android suspends the plugin while the player is in front.

Swipe on G2 or R1 to select a bottom control, then tap it. The Android view shows captions by default. System playback controls are available with the separate media-controls APK and its Android notification-access grant. Individual controls enable when the active session reports a matching action. Stopping Android screen sharing ends the connection and invalidates its code.

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

The pack command type-checks, builds, and writes `e2player.ehpk` at the repository root. GitHub Actions verifies the test and pack commands. The only allowed network destination is the Android companion at `http://127.0.0.1:47841`.

For a physical-device preview, use [Even's QR testing guide](https://hub.evenrealities.com/docs/test/local-testing) with the phone and development machine on the same network. Use `npm exec -- vite --host 0.0.0.0 --strictPort` for this LAN test. For installation of the packaged build, follow the [Even Hub packaging and beta guide](https://hub.evenrealities.com/docs/ship/packaging).

## Code

- `src/playback.ts` owns the playback state, transport commands and timed captions.
- `src/glasses.ts` adapts SDK gesture events and sends text to the G2 display.
- `src/caption-link.ts` handles pairing, local caption polling and media commands.
- `src/main.ts` binds the mobile display and controls to the same player.
