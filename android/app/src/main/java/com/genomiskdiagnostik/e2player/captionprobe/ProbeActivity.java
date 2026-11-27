package com.genomiskdiagnostik.e2player.captionprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LocaleManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.LocaleList;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

public final class ProbeActivity extends Activity {
    private static final int CAPTURE_REQUEST = 1;
    private static final String[] LOCALE_TAGS = {"", "en", "ja", "da", "de", "fr", "es", "zh-Hans"};
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Button start;
    private Button stop;
    private Button copy;
    private TextView status;
    private TextView linkStatus;
    private TextView historyStatus;
    private TextView caption;
    private boolean choosingSource;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            var state = CaptureService.state();
            start.setEnabled(!state.running() && !choosingSource);
            stop.setEnabled(state.running());
            copy.setEnabled(CaptureService.pairingCode() != null);
            linkStatus.setVisibility(CaptureService.linkFailed() ? View.VISIBLE : View.GONE);
            historyStatus.setVisibility(CaptureService.historyFailed() ? View.VISIBLE : View.GONE);
            status.setText(state.status());
            String text = state.sample() == null ? "" : state.sample().visibleText(SystemClock.elapsedRealtime());
            caption.setText(text.isEmpty() ? getText(R.string.no_current_text) : text);
            ui.postDelayed(this, 250);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Avoid feeding the preview text back into a capture.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        var viewport = new ScrollView(this);
        var content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        viewport.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            int margin = Math.round(16 * getResources().getDisplayMetrics().density);
            content.setPadding(bars.left + margin, bars.top + margin,
                    bars.right + margin, bars.bottom + margin);
            return insets;
        });
        viewport.addView(content);
        setContentView(viewport);

        var title = new TextView(this);
        title.setText(R.string.title);
        title.setTextSize(24);
        content.addView(title);
        addLanguagePicker(content);

        var instructions = new TextView(this);
        instructions.setText(R.string.instructions);
        content.addView(instructions);

        var ocrArea = new Button(this);
        updateOcrCropLabel(ocrArea);
        ocrArea.setOnClickListener(view -> chooseOcrCrop(ocrArea));
        content.addView(ocrArea);

        start = new Button(this);
        start.setText(R.string.choose_source);
        start.setOnClickListener(view -> {
            var manager = getSystemService(MediaProjectionManager.class);
            choosingSource = true;
            start.setEnabled(false);
            var config = MediaProjectionConfig.createConfigForUserChoice();
            startActivityForResult(manager.createScreenCaptureIntent(config), CAPTURE_REQUEST);
        });
        content.addView(start);

        stop = new Button(this);
        stop.setText(R.string.stop_sharing);
        stop.setOnClickListener(view -> stopService(new Intent(this, CaptureService.class)));
        content.addView(stop);

        copy = new Button(this);
        copy.setText(R.string.copy_glasses_code);
        copy.setOnClickListener(view -> copyPairingCode());
        content.addView(copy);

        linkStatus = new TextView(this);
        linkStatus.setText(R.string.link_unavailable);
        content.addView(linkStatus);

        status = new TextView(this);
        content.addView(status);
        var history = new Button(this);
        history.setText(R.string.subtitle_history);
        history.setOnClickListener(view -> startActivity(new Intent(this, SubtitleHistoryActivity.class)));
        content.addView(history);
        historyStatus = new TextView(this);
        historyStatus.setText(R.string.history_write_failed);
        content.addView(historyStatus);
        caption = new TextView(this);
        caption.setTextSize(20);
        content.addView(caption);

        if (BuildConfig.MEDIA_CONTROLS) {
            var mediaHelp = new TextView(this);
            mediaHelp.setText(R.string.media_access_help);
            content.addView(mediaHelp);
            var mediaSettings = new Button(this);
            mediaSettings.setText(R.string.media_access_settings);
            mediaSettings.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            content.addView(mediaSettings);
        }
    }

    private void addLanguagePicker(LinearLayout content) {
        var label = new TextView(this);
        label.setText(R.string.language);
        var picker = new Spinner(this);
        picker.setId(View.generateViewId());
        label.setLabelFor(picker.getId());
        var choices = new String[]{getString(R.string.system_language), "English", "日本語", "Dansk",
                "Deutsch", "Français", "Español", "简体中文"};
        var adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices);
        picker.setAdapter(adapter);
        var manager = getSystemService(LocaleManager.class);
        int selected = Arrays.asList(LOCALE_TAGS).indexOf(manager.getApplicationLocales().toLanguageTags());
        picker.setSelection(Math.max(0, selected));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
                String tag = LOCALE_TAGS[index];
                if (!tag.equals(manager.getApplicationLocales().toLanguageTags())) {
                    manager.setApplicationLocales(LocaleList.forLanguageTags(tag));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        content.addView(label);
        content.addView(picker);
    }

    private void chooseOcrCrop(Button button) {
        int savedPercent = CaptureService.ignoredTopPercent(this);
        int initialPercent = CaptionRaster.isCustomCrop(savedPercent) ? savedPercent : CaptionRaster.STANDARD_TOP_PERCENT;
        var options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        int margin = Math.round(24 * getResources().getDisplayMetrics().density);
        options.setPadding(margin, margin / 2, margin, 0);

        var summary = new TextView(this);
        summary.setText(getString(R.string.ocr_crop_selection, initialPercent, 100 - initialPercent));
        options.addView(summary);
        var slider = new SeekBar(this);
        slider.setContentDescription(getText(R.string.ocr_crop_title));
        slider.setMax(CaptionRaster.MAX_IGNORED_PERCENT);
        slider.setProgress(initialPercent);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int percent, boolean fromUser) {
                summary.setText(getString(R.string.ocr_crop_selection, percent, 100 - percent));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        options.addView(slider);

        new AlertDialog.Builder(this).setTitle(R.string.ocr_crop_title).setView(options)
                .setNeutralButton(R.string.ocr_crop_standard, (dialog, which) -> {
                    CaptureService.setIgnoredTopPercent(this, CaptionRaster.STANDARD_CROP);
                    updateOcrCropLabel(button);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    CaptureService.setIgnoredTopPercent(this, slider.getProgress());
                    updateOcrCropLabel(button);
                }).show();
    }

    private void updateOcrCropLabel(Button button) {
        int percent = CaptureService.ignoredTopPercent(this);
        String value = CaptionRaster.isCustomCrop(percent)
                ? getString(R.string.ocr_crop_summary, percent) : getString(R.string.ocr_crop_standard);
        button.setText(getString(R.string.ocr_crop_button, value));
    }

    private void copyPairingCode() {
        String code = CaptureService.pairingCode();
        if (code == null) return;
        var clipboard = getSystemService(ClipboardManager.class);
        var clip = ClipData.newPlainText(getText(R.string.copy_glasses_code), code);
        var extras = new PersistableBundle();
        extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
        clip.getDescription().setExtras(extras);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.code_copied, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CAPTURE_REQUEST) return;
        choosingSource = false;
        if (resultCode != RESULT_OK || data == null) return;
        var service = new Intent(this, CaptureService.class);
        service.putExtra(CaptureService.CONSENT, data);
        startForegroundService(service);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh.run();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(refresh);
        caption.setText("");
        super.onPause();
    }
}
