package com.genomiskdiagnostik.e2player.captionprobe;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.WindowManager;

import com.googlecode.tesseract.android.TessBaseAPI;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class CaptureService extends Service {
    static final String CONSENT = "screenConsent";
    private static final String STOP = "stopCapture";
    private static final String CHANNEL = "caption_capture";
    private static final int NOTIFICATION_ID = 1;
    private static final long SAMPLE_INTERVAL_MS = 250;

    record State(boolean running, int status, CaptionSample sample) {}
    private static final State INACTIVE = new State(false, R.string.sharing_off, null);
    private static volatile State currentState = INACTIVE;
    private static volatile String pairingCode;
    private static volatile boolean linkFailed;

    static State state() {
        return currentState;
    }

    static String pairingCode() {
        return pairingCode;
    }

    static boolean linkFailed() {
        return linkFailed;
    }

    private HandlerThread captureThread;
    private Handler capture;
    private volatile boolean closing;
    private boolean started;
    private CaptionBridge bridge;

    // These resources are used only on the capture thread.
    private ImageReader reader;
    private VirtualDisplay display;
    private MediaProjection projection;
    private TessBaseAPI recognizer;
    private boolean visible;
    private long lastSampleMs = -SAMPLE_INTERVAL_MS;
    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            stopSelf();
        }

        @Override
        public void onCapturedContentResize(int width, int height) {
            if (closing || display == null || width <= 0 || height <= 0) return;
            if (width == reader.getWidth() && height == reader.getHeight()) return;
            update(true, R.string.resizing, null);
            var previous = reader;
            try {
                reader = newReader(width, height);
                display.resize(width, height, density());
                display.setSurface(reader.getSurface());
            } catch (RuntimeException ignored) {
                update(false, R.string.resize_failed, null);
                stopSelf();
            } finally {
                if (reader != previous) previous.close();
            }
        }

        @Override
        public void onCapturedContentVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            update(true, isVisible ? R.string.reading : R.string.hidden, null);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (started) return START_NOT_STICKY;
        var consent = intent.getParcelableExtra(CONSENT, Intent.class);
        if (consent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            started = true;
            linkFailed = false;
            update(true, R.string.starting, null);
            captureThread = new HandlerThread("caption-capture");
            captureThread.start();
            capture = new Handler(captureThread.getLooper());
            capture.post(() -> openProjection(consent));
        } catch (SecurityException ignored) {
            update(false, R.string.declined, null);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void openProjection(Intent consent) {
        if (closing) return;
        try {
            var manager = getSystemService(MediaProjectionManager.class);
            projection = manager.getMediaProjection(Activity.RESULT_OK, consent);
            if (projection == null) throw new IOException("Screen sharing consent is unavailable");
            projection.registerCallback(projectionCallback, capture);
            var bounds = getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            reader = newReader(bounds.width(), bounds.height());
            display = projection.createVirtualDisplay("Caption Probe", bounds.width(), bounds.height(),
                    density(), DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, capture);
            if (display == null) throw new IOException("Could not create the virtual display");
            recognizer = openRecognizer();
            openBridge();
        } catch (RuntimeException | IOException | UnsatisfiedLinkError ignored) {
            update(false, R.string.start_failed, null);
            stopSelf();
        }
    }

    private synchronized void openBridge() {
        if (closing) return;
        var media = new PhoneMedia(this);
        try {
            bridge = new CaptionBridge(CaptionBridge.PORT, () -> bridgeSnapshot(media),
                    BuildConfig.MEDIA_CONTROLS ? (id, action) -> !closing && media.send(id, action) : null);
            pairingCode = bridge.code();
        } catch (IOException ignored) {
            linkFailed = true;
        }
    }

    private String bridgeSnapshot(PhoneMedia media) {
        var state = currentState;
        var sample = closing ? null : state.sample();
        String text = sample == null ? "" : sample.visibleText(SystemClock.elapsedRealtime());
        try {
            var playing = media.snapshot();
            return new JSONObject().put("caption", CaptionSample.limit(text, 160))
                    .put("mediaAccess", media.hasAccess())
                    .put("media", playing == null ? JSONObject.NULL : playing)
                    .toString();
        } catch (JSONException ignored) {
            return "{\"caption\":\"\",\"mediaAccess\":false,\"media\":null}";
        }
    }

    private TessBaseAPI openRecognizer() throws IOException {
        var modelDirectory = new File(getCacheDir(), "tessdata");
        Files.createDirectories(modelDirectory.toPath());
        for (String language : new String[]{"dan", "eng"}) {
            var name = language + ".traineddata";
            try (var asset = getAssets().open("tessdata/" + name)) {
                Files.copy(asset, new File(modelDirectory, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        var api = new TessBaseAPI();
        if (!api.init(getCacheDir().getAbsolutePath(), "dan+eng")) {
            api.recycle();
            throw new IOException("Tesseract initialization failed");
        }
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
        return api;
    }

    private ImageReader newReader(int width, int height) {
        var source = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        source.setOnImageAvailableListener(this::observe, capture);
        return source;
    }

    private void observe(ImageReader source) {
        if (closing || source != reader) return;
        Bitmap crop = null;
        try {
            long now = SystemClock.elapsedRealtime();
            try (var frame = source.acquireLatestImage()) {
                if (frame == null || !visible || recognizer == null || now - lastSampleMs < SAMPLE_INTERVAL_MS) return;
                lastSampleMs = now;
                crop = captionArea(frame);
            }
            recognizer.setImage(crop);
            update(true, R.string.reading, new CaptionSample(recognizer.getUTF8Text(), now));
        } catch (RuntimeException ignored) {
            update(true, R.string.unreadable, null);
        } finally {
            if (crop != null) crop.recycle();
        }
    }

    private static Bitmap captionArea(Image image) {
        var plane = image.getPlanes()[0];
        if (plane.getPixelStride() != 4) throw new IllegalArgumentException("Expected RGBA pixels");
        var raster = CaptionRaster.fromRgba(plane.getBuffer(), plane.getRowStride(), image.getWidth(), image.getHeight());
        return Bitmap.createBitmap(raster.pixels(), raster.width(), raster.height(), Bitmap.Config.ARGB_8888);
    }

    private int density() {
        return getResources().getDisplayMetrics().densityDpi;
    }

    private Notification notification() {
        var manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, getText(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW));
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        var open = PendingIntent.getActivity(this, 0, new Intent(this, ProbeActivity.class), flags);
        var stop = new Intent(this, CaptureService.class).setAction(STOP);
        var stopAction = PendingIntent.getService(this, 0, stop, flags);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.notification_description))
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, getText(R.string.stop_sharing), stopAction).build())
                .build();
    }

    private synchronized void update(boolean running, int status, CaptionSample sample) {
        if (!closing) currentState = new State(running, status, sample);
    }

    @Override
    public void onDestroy() {
        synchronized (this) {
            closing = true;
            pairingCode = null;
            linkFailed = false;
            if (bridge != null) {
                try {
                    bridge.close();
                } catch (IOException ignored) {
                    // The session is invalidated even if the socket was already closed.
                }
            }
            if (currentState.running()) currentState = INACTIVE;
        }
        if (capture != null) {
            capture.post(() -> {
                if (projection != null) {
                    projection.unregisterCallback(projectionCallback);
                    projection.stop();
                }
                if (display != null) display.release();
                if (reader != null) reader.close();
                if (recognizer != null) recognizer.recycle();
                captureThread.quitSafely();
            });
        }
        super.onDestroy();
    }
}
