# E2Player

E2Player is an Even Hub plugin project for Even G2 and the R1 ring. The first build plays a bundled test video and audio track on the phone. G2 and R1 can select previous, play/pause, stop, next, and captions. Captions use the current playback time and are off by default.

The project goals also include Wear-style control of the phone's active media session, G2 voice search for YouTube, and synced captions on the glasses for eligible videos. [PLANS.md](PLANS.md) records the platform constraints and the acceptance checks.

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

Swipe up or down to move through the commands and tap to select. A double tap opens the Even system exit confirmation. Stop rewinds to the start. The queue wraps in both directions, and switching from an item that is playing continues playback. If the WebView blocks the first Play gesture from the glasses, tap **Play** on the phone.

The fixtures are synthetic and bundled with the app. The video shows a moving test pattern and three ascending tones. The audio track contains three descending tones. Both have three four-second caption cues.

The device adapter handles taps delivered as either container events or system events with a touch source. In development, it rebuilds a retained glasses page if the simulator rejects startup during a reload. See Even Hub reports [#30](https://github.com/even-realities/everything-evenhub/issues/30) and [#32](https://github.com/even-realities/everything-evenhub/issues/32).

## Build and package

```sh
npm test
npm run pack
```

The pack command type-checks, builds, and writes `e2player.ehpk` at the repository root. GitHub Actions also uploads the package as a build artifact. The package requests no microphone or external network permissions.

For a physical-device preview, use [Even's QR testing guide](https://hub.evenrealities.com/docs/test/local-testing) with the phone and development machine on the same network. Use `npm exec -- vite --host 0.0.0.0 --strictPort` for this LAN test. For installation of the packaged build, follow the [Even Hub packaging and beta guide](https://hub.evenrealities.com/docs/ship/packaging).

## Code

- `src/playback.ts` owns the playback state, transport commands and timed captions.
- `src/glasses.ts` adapts SDK gesture events and sends text to the G2 display.
- `src/main.ts` binds the mobile display and controls to the same player.
