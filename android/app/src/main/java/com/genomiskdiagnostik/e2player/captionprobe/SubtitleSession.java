package com.genomiskdiagnostik.e2player.captionprobe;

import java.util.Locale;

/** Groups repeated observations on the monotonic screen-sharing timeline. */
final class SubtitleSession {
    record Cue(long startMs, long endMs, String text) {}

    enum Format {
        TXT("txt", "text/plain"),
        SRT("srt", "application/x-subrip");

        final String extension;
        final String mimeType;

        Format(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        String render(int index, Cue cue) {
            return this == TXT ? cue.text() + "\n" : index + "\n" + timestamp(cue.startMs())
                    + " --> " + timestamp(cue.endMs()) + "\n" + cue.text() + "\n\n";
        }

        private static String timestamp(long ms) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d",
                    ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000);
        }
    }

    private final long startedAtMs;
    private String text = "";
    private long startMs;
    private long lastSeenMs;

    SubtitleSession(long startedAtMs) {
        this.startedAtMs = startedAtMs;
        lastSeenMs = startedAtMs;
    }

    Cue observe(String next, long nowMs) {
        if (nowMs < lastSeenMs) return null;
        long expiryMs = lastSeenMs + CaptionSample.MAX_AGE_MS;
        if (next.equals(text) && nowMs < expiryMs) {
            lastSeenMs = nowMs;
            return null;
        }
        long endMs = Math.min(nowMs, expiryMs);
        Cue cue = text.isEmpty() || endMs <= startMs ? null
                : new Cue(startMs - startedAtMs, endMs - startedAtMs, text);
        text = next;
        startMs = nowMs;
        lastSeenMs = nowMs;
        return cue;
    }
}
