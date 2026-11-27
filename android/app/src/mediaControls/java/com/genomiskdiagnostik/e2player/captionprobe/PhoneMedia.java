package com.genomiskdiagnostik.e2player.captionprobe;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

final class PhoneMedia {
    private final MediaSessionManager sessions;
    private final NotificationManager notifications;
    private final ComponentName listener;
    private MediaSession.Token lastToken;
    private long generation;

    PhoneMedia(Context context) {
        sessions = context.getSystemService(MediaSessionManager.class);
        notifications = context.getSystemService(NotificationManager.class);
        listener = new ComponentName(context, MediaAccessService.class);
    }

    boolean hasAccess() {
        return notifications.isNotificationListenerAccessGranted(listener);
    }

    private MediaController active() {
        if (!hasAccess()) return null;
        try {
            var activeSessions = sessions.getActiveSessions(listener);
            // Android orders the list by control priority.
            return activeSessions.isEmpty() ? null : activeSessions.get(0);
        } catch (SecurityException ignored) {
            return null;
        }
    }

    JSONObject snapshot() throws JSONException {
        var controller = active();
        var token = controller == null ? null : controller.getSessionToken();
        if (!Objects.equals(token, lastToken)) {
            lastToken = token;
            generation++;
        }
        if (controller == null) return null;
        var state = controller.getPlaybackState();
        if (state == null) return null;
        var metadata = controller.getMetadata();
        CharSequence title = metadata == null ? null : metadata.getDescription().getTitle();
        long duration = metadata == null ? 0 : Math.max(0, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING
                || state.getState() == PlaybackState.STATE_BUFFERING;
        var actions = new JSONArray();
        for (String action : new String[]{"previous", playing ? "pause" : "play", "stop", "next"}) {
            if (supports(state, action)) actions.put(action);
        }
        return new JSONObject().put("id", String.valueOf(generation))
                .put("title", title == null ? "" : CaptionSample.limit(title.toString(), 100))
                .put("playing", playing)
                .put("positionMs", position(state, duration))
                .put("durationMs", duration)
                .put("actions", actions);
    }

    boolean send(String id, String action) {
        if (!String.valueOf(generation).equals(id) || lastToken == null) return false;
        var controller = active();
        if (controller == null || !lastToken.equals(controller.getSessionToken())) return false;
        var state = controller.getPlaybackState();
        if (state == null || !supports(state, action)) return false;
        var controls = controller.getTransportControls();
        switch (action) {
            case "play", "pause" -> {
                long direct = action.equals("play") ? PlaybackState.ACTION_PLAY : PlaybackState.ACTION_PAUSE;
                if ((state.getActions() & direct) == 0) {
                    int key = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                    boolean handled = controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
                    controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
                    return handled;
                }
                if (action.equals("play")) controls.play();
                else controls.pause();
            }
            case "stop" -> controls.stop();
            case "previous" -> controls.skipToPrevious();
            case "next" -> controls.skipToNext();
            default -> { return false; }
        }
        return true;
    }

    private static boolean supports(PlaybackState state, String action) {
        long direct = switch (action) {
            case "play" -> PlaybackState.ACTION_PLAY;
            case "pause" -> PlaybackState.ACTION_PAUSE;
            case "stop" -> PlaybackState.ACTION_STOP;
            case "previous" -> PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            case "next" -> PlaybackState.ACTION_SKIP_TO_NEXT;
            default -> 0;
        };
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING
                || state.getState() == PlaybackState.STATE_BUFFERING;
        if (action.equals("play") && playing || action.equals("pause") && !playing) return false;
        boolean toggle = (action.equals("play") || action.equals("pause"))
                && (state.getActions() & PlaybackState.ACTION_PLAY_PAUSE) != 0;
        return (state.getActions() & direct) != 0 || toggle;
    }

    private static long position(PlaybackState state, long duration) {
        long position = state.getPosition();
        if (position < 0) return -1;
        if (state.getState() == PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime() > 0) {
            long elapsed = Math.max(0, SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime());
            position += Math.round(elapsed * (double) state.getPlaybackSpeed());
        }
        return Math.max(0, duration > 0 ? Math.min(position, duration) : position);
    }

}
