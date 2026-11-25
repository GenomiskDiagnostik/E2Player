# E2Player execution plan

## 0. Even Hub baseline

- [x] Initialize Git and a private GitHub repository.
- [x] Build an `.ehpk` with a synthetic video and audio queue, shared controls, and timed captions.
- [ ] Verify on paired G2 and R1. Check both input sources, the exit dialog, WebView media playback, and caption display while playing, seeking, pausing, and stopping.

## 1. Phone media sessions

- [ ] Build an Android native proof of concept that discovers an active session via an explicitly enabled notification listener and invokes only transport actions advertised by that session. Check Spotify and YouTube independently, including missing Stop support and changes to the active session.
- [ ] Test how the native component can communicate with the Even Hub WebView using supported APIs. If considering a loopback bridge, verify access in a packaged build and design per-install authentication and narrow origins before implementation. Keep metadata on-device. Test whether the plugin can be invoked from the glasses while another app plays and whether Even supports showing controls automatically.
- [ ] Check whether a supported iOS system-media integration exists. Apple's documented remote-command interface handles commands for the app playing the media. The Even Hub SDK has no documented system-media interface in the version used here.

Sources: [Android MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager), [Apple remote playback events](https://developer.apple.com/documentation/mediaplayer/handling-external-player-events-notifications), [Even Hub Device APIs](https://hub.evenrealities.com/docs/build/device-apis), [Even Hub Networking](https://hub.evenrealities.com/docs/build/networking).

## 2. YouTube playback and voice search

- [ ] First validate an official, visible YouTube IFrame Player inside the Even Realities App on Android and iOS. Use a known embeddable video and verify client identification, display size, host network restrictions, and R1/G2 commands. Cue the selected video and handle player errors and autoplay denial. Check that playback respects YouTube foreground visibility requirements.
- [ ] Use YouTube Data API `search.list` with `type=video` and `videoEmbeddable=true`. Verify end-to-end selection from the glasses with an initial text query. Keep restricted API credentials outside the packaged client and enforce a request budget. Search filters do not guarantee playability for every account or region.
- [ ] Add explicit press-to-talk from G2 via `audioControl` and `audioEvent`. Validate Danish speech recognition from G2's mono 16 kHz PCM on actual hardware. Prefer on-device recognition if viable. Evaluate latency and device support before choosing a recognizer. Show when the mic is active and clear the capture on stop, cancel, timeout and interruption. Send only the confirmed search text to the search service.

Sources: [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference), [Search API](https://developers.google.com/youtube/v3/docs/search/list), [YouTube embedded player requirements](https://developers.google.com/youtube/terms/required-minimum-functionality), [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies/), [G2 audio and input events](https://hub.evenrealities.com/docs/build/device-apis).

## 3. YouTube captions on G2

- [ ] Pilot an editable test video with an authorized, timed caption track. Pair the track with its video ID and synchronize cues to the player's clock. Verify the on/off action, seeking, pauses, track switches and unavailable subtitles.
- [ ] Confirm permitted caption sources before expanding beyond the pilot. The documented IFrame API does not expose timed caption text. YouTube Data API caption download requires permission to edit the video. Evaluate separately supplied tracks with appropriate rights and display a clear unavailable state for unsupported videos.

Sources: [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference), [YouTube Captions download](https://developers.google.com/youtube/v3/docs/captions/download).
