package com.genomiskdiagnostik.e2player.captionprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SubtitleHistoryActivity extends Activity {
    private static final int EXPORT_REQUEST = 1;
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SubtitleHistory history;
    private List<SubtitleHistory.Session> sessions = List.of();
    private LinearLayout details;
    private Spinner sessionPicker;
    private Spinner formatPicker;
    private Button copy;
    private Button save;
    private Button delete;
    private TextView status;
    private TextView preview;
    private String selectedId;
    private String exportId;
    private SubtitleSession.Format exportFormat;
    private int previewRequest;
    private boolean updatingList;
    private boolean exporting;
    private final Runnable refreshAfterCapture = new Runnable() {
        @Override
        public void run() {
            if (CaptureService.activeHistoryId().isEmpty()) loadSessions();
            else ui.postDelayed(this, 500);
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        history = new SubtitleHistory(this);
        if (state != null) {
            selectedId = state.getString("session");
            exportId = state.getString("exportId");
            exportFormat = SubtitleSession.Format.values()[state.getInt("exportFormat")];
        }

        var scroll = new ScrollView(this);
        var content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars());
            int margin = Math.round(16 * getResources().getDisplayMetrics().density);
            content.setPadding(bars.left + margin, bars.top + margin, bars.right + margin, bars.bottom + margin);
            return insets;
        });
        scroll.addView(content);
        setContentView(scroll);

        addText(content, R.string.subtitle_history).setTextSize(24);
        addText(content, R.string.history_description);
        status = addText(content, R.string.history_loading);
        details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        content.addView(details);

        sessionPicker = addPicker(R.string.history_session);
        formatPicker = addPicker(R.string.history_format);
        setChoices(formatPicker, List.of("TXT", "SRT"));
        if (state != null) formatPicker.setSelection(state.getInt("format"));
        copy = addButton(R.string.history_copy, () -> copyText(preview.getText().toString()));
        save = addButton(R.string.history_save, this::chooseExportFile);
        delete = addButton(R.string.history_delete, this::confirmDelete);
        preview = addText(details, 0);
        preview.setTextIsSelectable(true);

        var selectionListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!updatingList) loadPreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        sessionPicker.setOnItemSelectedListener(selectionListener);
        formatPicker.setOnItemSelectedListener(selectionListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSessions();
        if (!CaptureService.activeHistoryId().isEmpty()) ui.postDelayed(refreshAfterCapture, 500);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(refreshAfterCapture);
        super.onPause();
    }

    private void loadSessions() {
        disk.execute(() -> {
            try {
                var found = history.sessions(CaptureService.activeHistoryId());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    sessions = found;
                    details.setVisibility(found.isEmpty() ? View.GONE : View.VISIBLE);
                    if (found.isEmpty()) {
                        ++previewRequest;
                        status.setText(R.string.history_empty);
                        selectedId = null;
                        return;
                    }
                    var locale = getResources().getConfiguration().getLocales().get(0);
                    var dateFormat = new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "yMMMdjms"), locale);
                    var labels = new ArrayList<String>(found.size());
                    int selected = 0;
                    for (int index = 0; index < found.size(); index++) {
                        var item = found.get(index);
                        labels.add(dateFormat.format(new Date(item.startedAt())));
                        if (item.id().equals(selectedId)) selected = index;
                    }
                    updatingList = true;
                    setChoices(sessionPicker, labels);
                    sessionPicker.setSelection(selected);
                    updatingList = false;
                    loadPreview();
                });
            } catch (SQLiteException ignored) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    ++previewRequest;
                    details.setVisibility(View.GONE);
                    status.setText(R.string.history_error);
                });
            }
        });
    }

    private void loadPreview() {
        var session = selectedSession();
        if (session == null) return;
        selectedId = session.id();
        var format = selectedFormat();
        int request = ++previewRequest;
        preview.setText("");
        copy.setEnabled(false);
        save.setEnabled(exportId == null && !exporting);
        delete.setEnabled(exportId == null && !exporting);
        status.setText(R.string.history_loading);
        disk.execute(() -> {
            try {
                var result = history.preview(session.id(), format);
                runOnUiThread(() -> {
                    if (isDestroyed() || previewRequest != request) return;
                    preview.setText(result.text());
                    copy.setEnabled(!result.truncated() && !result.text().isEmpty());
                    status.setText(result.truncated() ? getText(R.string.history_truncated) : "");
                });
            } catch (SQLiteException ignored) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && previewRequest == request) status.setText(R.string.history_error);
                });
            }
        });
    }

    private void copyText(String text) {
        try {
            var clip = ClipData.newPlainText(getText(R.string.subtitle_history), text);
            var extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
            getSystemService(ClipboardManager.class).setPrimaryClip(clip);
            toast(R.string.history_copied);
        } catch (RuntimeException ignored) {
            toast(R.string.history_copy_failed);
        }
    }

    private void chooseExportFile() {
        var session = selectedSession();
        if (session == null || exportId != null || exporting) return;
        exportId = session.id();
        exportFormat = selectedFormat();
        save.setEnabled(false);
        delete.setEnabled(false);
        var date = new SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.ROOT);
        var request = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(exportFormat.mimeType)
                .putExtra(Intent.EXTRA_TITLE, "E2Player_" + date.format(new Date(session.startedAt())) + "." + exportFormat.extension);
        try {
            startActivityForResult(request, EXPORT_REQUEST);
        } catch (ActivityNotFoundException ignored) {
            finishExport(R.string.history_save_failed);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REQUEST || exportId == null) return;
        var uri = data == null ? null : data.getData();
        if (resultCode != RESULT_OK || uri == null) {
            finishExport(0);
            return;
        }
        String id = exportId;
        var format = exportFormat;
        exportId = null;
        exporting = true;
        disk.execute(() -> {
            try {
                var stream = getContentResolver().openOutputStream(uri, "wt");
                if (stream == null) throw new IOException("Document unavailable");
                try (var output = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                    history.export(id, format, output);
                }
                runOnUiThread(() -> finishExport(R.string.history_saved));
            } catch (IOException | SecurityException | SQLiteException ignored) {
                runOnUiThread(() -> finishExport(R.string.history_save_failed));
            }
        });
    }

    private void finishExport(int message) {
        if (isDestroyed()) return;
        exportId = null;
        exporting = false;
        save.setEnabled(selectedSession() != null);
        delete.setEnabled(selectedSession() != null);
        if (message != 0) toast(message);
    }

    private void confirmDelete() {
        var session = selectedSession();
        if (session == null) return;
        new AlertDialog.Builder(this).setMessage(R.string.history_delete_prompt)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.history_delete, (dialog, which) -> disk.execute(() -> {
                    try {
                        history.delete(session.id());
                        runOnUiThread(() -> { if (!isDestroyed()) loadSessions(); });
                    } catch (SQLiteException ignored) {
                        runOnUiThread(() -> toast(R.string.history_error));
                    }
                })).show();
    }

    private SubtitleHistory.Session selectedSession() {
        int index = sessionPicker.getSelectedItemPosition();
        return index >= 0 && index < sessions.size() ? sessions.get(index) : null;
    }

    private SubtitleSession.Format selectedFormat() {
        return formatPicker.getSelectedItemPosition() == 1 ? SubtitleSession.Format.SRT : SubtitleSession.Format.TXT;
    }

    private void setChoices(Spinner picker, List<String> values) {
        picker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
    }

    private Spinner addPicker(int label) {
        var picker = new Spinner(this);
        picker.setId(View.generateViewId());
        addText(details, label).setLabelFor(picker.getId());
        details.addView(picker);
        return picker;
    }

    private Button addButton(int label, Runnable action) {
        var button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        details.addView(button);
        return button;
    }

    private TextView addText(LinearLayout parent, int label) {
        var text = new TextView(this);
        if (label != 0) text.setText(label);
        parent.addView(text);
        return text;
    }

    private void toast(int message) {
        if (!isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("session", selectedId);
        state.putInt("format", selectedFormat().ordinal());
        state.putString("exportId", exportId);
        if (exportFormat != null) state.putInt("exportFormat", exportFormat.ordinal());
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        disk.execute(history::close);
        disk.shutdown();
        super.onDestroy();
    }
}
