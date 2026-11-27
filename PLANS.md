# E2Player execution plan

## 0. Even Hub baseline

- [x] Initialize Git and a private GitHub repository.
- [x] Build an `.ehpk` with a synthetic video and audio queue, shared controls, and timed captions.
- [ ] Verify on paired G2 and R1. Check both input sources, the exit dialog, WebView media playback, and caption display while playing, seeking, pausing, and stopping.

## 1. Phone media sessions

- [x] Discover Android's active media session via optional notification access and send only advertised transport actions via the paired loopback bridge.
- [ ] Verify the new packaged bridge on Android with YouTube and Spotify, including missing Stop support, session changes, WebView suspension, and invocation from the glasses.
- [ ] Check whether a supported iOS system-media integration exists. Apple's documented remote-command interface handles commands for the app playing the media. The Even Hub SDK has no documented system-media interface in the version used here.

Sources: [Android MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager), [Apple remote playback events](https://developer.apple.com/documentation/mediaplayer/handling-external-player-events-notifications), [Even Hub Device APIs](https://hub.evenrealities.com/docs/build/device-apis), [Even Hub Networking](https://hub.evenrealities.com/docs/build/networking).

## 2. YouTube playback and voice search

- [ ] First validate an official, visible YouTube IFrame Player inside the Even Realities App on Android and iOS. Use a known embeddable video and verify client identification, display size, host network restrictions, and R1/G2 commands. Cue the selected video and handle player errors and autoplay denial. Check that playback respects YouTube foreground visibility requirements.
- [ ] Use YouTube Data API `search.list` with `type=video` and `videoEmbeddable=true`. Verify end-to-end selection from the glasses with an initial text query. Keep restricted API credentials outside the packaged client and enforce a request budget. Search filters do not guarantee playability for every account or region.
- [ ] Add explicit press-to-talk from G2 via `audioControl` and `audioEvent`. Validate Danish speech recognition from G2's mono 16 kHz PCM on actual hardware. Prefer on-device recognition if viable. Evaluate latency and device support before choosing a recognizer. Show when the mic is active and clear the capture on stop, cancel, timeout and interruption. Send only the confirmed search text to the search service.

Sources: [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference), [Search API](https://developers.google.com/youtube/v3/docs/search/list), [YouTube embedded player requirements](https://developers.google.com/youtube/terms/required-minimum-functionality), [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies/), [G2 audio and input events](https://hub.evenrealities.com/docs/build/device-apis).

## 3. YouTube captions on G2

- [x] Build an offline Android probe for visible captions via single-app capture and OCR. See [decision record](docs/adr/0001-read-rendered-captions.md) and [probe setup](android/README.md).
- [ ] Validate the local probe on an Android phone with synthetic captions. Check capture consent, crop, timing, pause, seek, rotation, track changes, protected frames and clearing when the source is hidden or capture stops. Measure recognition accuracy and latency before adding scripts or an adjustable crop.
- [ ] Confirm permitted YouTube use and test the selected player captions on a representative set of videos. Check subtitles off, absent tracks, auto captions and selected languages. Use the visible cue for synchronization and validate the playback-position source separately.
- [x] Link the companion to Even Hub with a session-scoped code and an IPv4 loopback endpoint. Show paired captions by default, with a separate demo source.
- [x] Add an authenticated loopback WebSocket for packaged WebViews that block HTTP fetches. Verify with a synthetic caption and transport command in the official simulator.
- [ ] Confirm packaged loopback access, G2 display, R1 toggle, and text expiry on paired hardware while the player is visible. Check Japanese and Chinese HUD glyph coverage on the firmware.

Sources: [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference), [YouTube Captions download](https://developers.google.com/youtube/v3/docs/captions/download), [Android MediaProjection](https://developer.android.com/media/grow/media-projection).
