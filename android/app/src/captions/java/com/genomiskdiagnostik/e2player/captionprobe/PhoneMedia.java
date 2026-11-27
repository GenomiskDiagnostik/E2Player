package com.genomiskdiagnostik.e2player.captionprobe;

import android.content.Context;

import org.json.JSONObject;

/** The caption-only build does not address Android media sessions. */
final class PhoneMedia {
    PhoneMedia(Context context) {}

    boolean hasAccess() {
        return false;
    }

    JSONObject snapshot() {
        return null;
    }

    boolean send(String id, String action) {
        return false;
    }
}
