package com.genomiskdiagnostik.e2player.captionprobe;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

final class SubtitleHistory extends SQLiteOpenHelper {
    record Session(String id, long startedAt) {}
    record Preview(String text, boolean truncated) {}

    // Keep the selectable preview and full-copy operation within Android's clipboard transaction limit.
    private static final int PREVIEW_CHARS = 100_000;

    SubtitleHistory(Context context) {
        super(context.getApplicationContext(), "subtitle-history.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("""
                CREATE TABLE cue (
                    session_id TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    start_ms INTEGER NOT NULL,
                    end_ms INTEGER NOT NULL,
                    caption TEXT NOT NULL,
                    PRIMARY KEY (session_id, start_ms)
                )
                """);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported subtitle history upgrade");
    }

    void append(Session session, SubtitleSession.Cue cue) {
        var values = new ContentValues();
        values.put("session_id", session.id());
        values.put("started_at", session.startedAt());
        values.put("start_ms", cue.startMs());
        values.put("end_ms", cue.endMs());
        values.put("caption", cue.text());
        getWritableDatabase().insertOrThrow("cue", null, values);
    }

    List<Session> sessions(String activeId) {
        var sessions = new ArrayList<Session>();
        try (var rows = getReadableDatabase().rawQuery("""
                SELECT DISTINCT session_id, started_at FROM cue
                WHERE session_id <> ? ORDER BY started_at DESC, session_id DESC
                """, new String[]{activeId})) {
            while (rows.moveToNext()) sessions.add(new Session(rows.getString(0), rows.getLong(1)));
        }
        return sessions;
    }

    Preview preview(String id, SubtitleSession.Format format) {
        var text = new StringBuilder();
        try (var rows = cues(id)) {
            int index = 1;
            while (rows.moveToNext()) {
                String block = format.render(index++, cue(rows));
                if (text.length() + block.length() > PREVIEW_CHARS) return new Preview(text.toString(), true);
                text.append(block);
            }
        }
        return new Preview(text.toString(), false);
    }

    void export(String id, SubtitleSession.Format format, Writer output) throws IOException {
        try (var rows = cues(id)) {
            int index = 1;
            while (rows.moveToNext()) output.write(format.render(index++, cue(rows)));
            if (index == 1) throw new IOException("Subtitle session no longer exists");
        }
    }

    void delete(String id) {
        getWritableDatabase().delete("cue", "session_id = ?", new String[]{id});
    }

    private Cursor cues(String id) {
        return getReadableDatabase().rawQuery("""
                SELECT start_ms, end_ms, caption FROM cue WHERE session_id = ? ORDER BY start_ms
                """, new String[]{id});
    }

    private static SubtitleSession.Cue cue(Cursor row) {
        return new SubtitleSession.Cue(row.getLong(0), row.getLong(1), row.getString(2));
    }
}
