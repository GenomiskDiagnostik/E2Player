# E2Player

- Read [README.md](README.md) and [PLANS.md](PLANS.md) before changing a playback or device integration.
- Keep phone playback in the player and Even Hub rendering and input in the SDK adapter. Use documented platform interfaces.
- Keep test media synthetic. Keep speech audio, secrets, and personal media out of Git. Request microphone and external network permissions only for implemented workflows.
- Run `npm test` and `npm run pack` for app changes. Verify G2 display and R1 gestures in the official simulator and record hardware results separately.
- Validate permissions, player visibility, and playback behavior on physical devices before publishing builds through Even Hub.
