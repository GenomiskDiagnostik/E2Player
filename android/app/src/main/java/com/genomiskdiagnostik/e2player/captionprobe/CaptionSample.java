package com.genomiskdiagnostik.e2player.captionprobe;

record CaptionSample(String text, long capturedAtMs) {
    private static final long MAX_AGE_MS = 2000;

    CaptionSample {
        text = text.replaceAll("[\\s\\p{Z}]+", " ").strip();
    }

    String visibleText(long nowMs) {
        long ageMs = nowMs - capturedAtMs;
        return ageMs >= 0 && ageMs < MAX_AGE_MS ? text : "";
    }
}
