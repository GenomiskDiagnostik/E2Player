package com.genomiskdiagnostik.e2player.captionprobe;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class ProbeActivity extends Activity {
    private static final int CAPTURE_REQUEST = 1;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Button start;
    private Button stop;
    private TextView status;
    private TextView caption;
    private boolean choosingSource;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            var state = CaptureService.state();
            start.setEnabled(!state.running() && !choosingSource);
            stop.setEnabled(state.running());
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

        var instructions = new TextView(this);
        instructions.setText(R.string.instructions);
        content.addView(instructions);

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

        status = new TextView(this);
        content.addView(status);
        caption = new TextView(this);
        caption.setTextSize(20);
        content.addView(caption);
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
