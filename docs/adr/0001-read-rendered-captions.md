# Read player-rendered captions

Status: Experimental

## Context

E2Player should show the same caption track the viewer selected in YouTube, including its selected language. The documented IFrame API provides playback time and caption-display settings. It does not provide the timed cue text. Data API caption download requires video edit access, which is unsuitable for general viewing.

## Decision

Probe local OCR of captions rendered by a player that the viewer has explicitly selected in Android's MediaProjection picker. Use the stock player for playback and track selection. Observe the currently rendered cue so following a seek or track change does not require reconstructing a caption timeline. A monotonic observation time bounds how long the cue may be displayed. Actual video position, when needed for playback controls, comes independently from IFrame `getCurrentTime()` or Android `PlaybackState` when the session publishes it.

The companion uses bundled Tesseract models and processes frames transiently. Each new capture session needs Android consent and starts a listener bound to `127.0.0.1` with a new random pairing code. Only paired clients receive the latest recognized text and optional media-session metadata. Frames stay in the companion. The first hardware tests use owned or synthetic media.

## Consequences

Visible captions in a capturable area are a prerequisite. Videos with no visible track and protected or unavailable frames have no reliable text source. The initial crop and Danish and English models are for feasibility testing. Layout, OCR accuracy, latency, script coverage, permission behavior and playback constraints set the support limits.

Verify loopback and foreground behavior in a packaged Even Hub app on paired hardware. The host WebView and its Android permissions can affect access and background polling. Before production use with YouTube, confirm the permitted use of player-rendered text. The native Even Enterprise SDK requires licensed hardware and firmware.

The packaged Even Hub WebView can block local HTTP fetches before the Android bridge receives a request. Version 0.2.2 tries the loopback WebSocket first, with HTTP as a fallback. The WebSocket validates the origin and authenticates each request with the current sharing code in the message body so it can accept new WebView origins on restart. Both transports use the same capture and command handlers. The HTTP client rejects redirects and caching, and the HTTP server pins the first authenticated origin for the current share. [Packaged HTTP tests in Send to G2](https://github.com/GenomiskDiagnostik/share-with-g2/blob/main/docs/decisions/ADR-014-loopback-websocket-probe.md).

The direct-install captions variant exposes the caption endpoint only. The media-controls variant uses Android notification access for media discovery and reads no notification contents. Its authenticated commands include the last reported session identifier and are checked against the active session and its advertised actions.

Sources: [YouTube IFrame API](https://developers.google.com/youtube/iframe_api_reference), [YouTube caption download](https://developers.google.com/youtube/v3/docs/captions/download), [YouTube terms](https://www.youtube.com/t/terms), [Android MediaProjection](https://developer.android.com/media/grow/media-projection), [Android PlaybackState](https://developer.android.com/reference/android/media/session/PlaybackState), [Even Enterprise SDK](https://hub.evenrealities.com/docs/enterprise/).
