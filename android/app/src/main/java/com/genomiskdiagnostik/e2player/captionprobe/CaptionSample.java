package com.genomiskdiagnostik.e2player.captionprobe;

record CaptionSample(String text, long capturedAtMs) {
    static final long MAX_AGE_MS = 2000;

    CaptionSample {
        text = text.replaceAll("[\\s\\p{Z}]+", " ").strip();
    }

    String visibleText(long nowMs) {
        long ageMs = nowMs - capturedAtMs;
        return ageMs >= 0 && ageMs < MAX_AGE_MS ? text : "";
    }

    static String limit(String text, int count) {
        int length = Math.min(text.codePointCount(0, text.length()), count);
        return text.substring(0, text.offsetByCodePoints(0, length));
    }
}
