# Read player-rendered captions

Status: Experimental

## Context

E2Player should show the same caption track the viewer selected in YouTube, including its selected language. The documented IFrame API provides playback time and caption-display settings. It does not provide the timed cue text. Data API caption download requires video edit access, which is unsuitable for general viewing.

## Decision

Probe local OCR of captions rendered by a player that the viewer has explicitly selected in Android's MediaProjection picker. Use the stock player for playback and track selection. Observe the currently rendered cue so following a seek or track change does not require reconstructing a caption timeline. A monotonic observation time bounds how long the cue may be displayed. Actual video position, when needed for playback controls, comes independently from IFrame `getCurrentTime()` or Android `PlaybackState` when the session publishes it.

The probe uses bundled Tesseract models and has no internet permission. It uses frames transiently and emits only the latest recognized text in its local UI. Each new capture session needs Android consent. The first hardware tests use owned or synthetic media. The probe does not call YouTube endpoints or modify the player.

## Consequences

Visible captions in a capturable area are a prerequisite. Videos with no visible track and protected or unavailable frames have no reliable text source. The initial crop and Danish and English models are for feasibility testing. Layout, OCR accuracy, latency, script coverage, permission behavior and playback constraints set the support limits.

Before production use with YouTube, confirm the permitted use of player-rendered text and validate the actual phone-to-glasses path with a packaged Even Hub app and paired hardware. Treat transient mirroring and transcript export as distinct cases. The native Even Enterprise SDK requires licensed hardware and firmware, so it is not a drop-in bridge for ordinary Even Hub installs.

Sources: [YouTube IFrame API](https://developers.google.com/youtube/iframe_api_reference), [YouTube caption download](https://developers.google.com/youtube/v3/docs/captions/download), [YouTube terms](https://www.youtube.com/t/terms), [Android MediaProjection](https://developer.android.com/media/grow/media-projection), [Android PlaybackState](https://developer.android.com/reference/android/media/session/PlaybackState), [Even Enterprise SDK](https://hub.evenrealities.com/docs/enterprise/).
