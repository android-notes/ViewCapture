package io.github.viewcapture.demo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String RELEASE_TIMING_HINT =
            "Install the Release build to compare capture times";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Button[] captureButtons = new Button[CaptureBackends.NAMES.length];
    private CaptureScrollView sceneScroll;
    private BitmapCompareView bitmapCompare;
    private ScrollSynchronizer comparison;
    private View previewEmpty;
    private TextView state;
    private SceneContent content;
    private CaptureSample displayed;
    private boolean busy, destroying;
    private CaptureBackends.Task currentTask = CaptureBackends.COMPLETED;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView title = findViewById(R.id.app_title);
        title.setText("View Capture Lab · API " + Build.VERSION.SDK_INT);
        state = findViewById(R.id.state);
        sceneScroll = findViewById(R.id.scene_scroll);
        bitmapCompare = findViewById(R.id.bitmap_compare);
        previewEmpty = findViewById(R.id.preview_empty);
        comparison = new ScrollSynchronizer(sceneScroll, bitmapCompare);
        int[] ids = {R.id.capture_software, R.id.capture_hardware, R.id.capture_pixel_copy};
        for (int i = 0; i < ids.length; i++) {
            int backend = i;
            captureButtons[i] = findViewById(ids[i]);
            captureButtons[i].setOnClickListener(view -> capture(backend));
            captureButtons[i].setEnabled(CaptureBackends.isSupported(i));
            if (!CaptureBackends.isSupported(i)) {
                captureButtons[i].setText(
                        CaptureBackends.NAMES[i] + " (API " + CaptureBackends.minApi(i) + "+)");
            }
        }
        boolean showcase = getIntent().getBooleanExtra("showcase", false);
        content =
                new SceneContent(
                        this, showcase ? SceneContent.SHOWCASE : 6, !showcase, showcase ? 1 : 11);
        sceneScroll.addView(content.target, new ScrollView.LayoutParams(-1, -2));
        awaitReady(
                System.nanoTime() + 5_000_000_000L,
                () -> {
                    if (!busy && displayed == null) {
                        state.setText(
                                (BuildConfig.DEBUG ? RELEASE_TIMING_HINT + " · " : "")
                                        + content.target.getWidth()
                                        + " × "
                                        + content.target.getHeight()
                                        + " px");
                    }
                });
    }

    private void awaitReady(long deadline, Runnable ready) {
        if (destroying) {
            return;
        }
        if (content.ready()) {
            content.target.postOnAnimation(
                    () ->
                            content.target.postOnAnimation(
                                    () -> {
                                        if (!destroying) {
                                            ready.run();
                                        }
                                    }));
        } else if (System.nanoTime() < deadline) {
            main.postDelayed(() -> awaitReady(deadline, ready), 32);
        } else {
            state.setText("First frame is not ready. Try again shortly.");
            setBusy(false);
        }
    }

    private void capture(int backend) {
        if (busy || destroying) {
            return;
        }
        setBusy(true);
        state.setText("Capturing · " + CaptureBackends.NAMES[backend]);
        awaitReady(
                System.nanoTime() + 5_000_000_000L,
                () -> {
                    currentTask =
                            CaptureBackends.capture(
                                    backend,
                                    this,
                                    content.target,
                                    sample -> {
                                        currentTask = CaptureBackends.COMPLETED;
                                        if (destroying) {
                                            sample.close();
                                            return;
                                        }
                                        worker.execute(() -> preparePreview(sample));
                                    });
                });
    }

    private void preparePreview(CaptureSample sample) {
        try {
            Bitmap preview = sample.bitmap == null ? null : sample.readableBitmap();
            main.post(() -> showCapture(sample, preview, null));
        } catch (RuntimeException | OutOfMemoryError error) {
            main.post(() -> showCapture(sample, null, error.toString()));
        }
    }

    private void showCapture(CaptureSample sample, Bitmap preview, String error) {
        if (destroying) {
            if (preview != null && preview != sample.bitmap) {
                preview.recycle();
            }
            sample.close();
            return;
        }
        String details =
                sample.bitmap == null
                        ? "Capture failed: " + sample.message
                        : error != null
                                ? "Preview failed: " + error
                                : (BuildConfig.DEBUG
                                                ? RELEASE_TIMING_HINT
                                                : String.format(
                                                        Locale.ROOT,
                                                        "%.2f ms",
                                                        sample.totalNs / 1_000_000.0))
                                        + " · "
                                        + sample.width
                                        + " × "
                                        + sample.height
                                        + " px";
        if (preview != null) {
            bitmapCompare.setBitmap(preview, preview != sample.bitmap);
            previewEmpty.setVisibility(View.GONE);
            if (displayed != null) {
                displayed.close();
            }
            displayed = sample;
        } else {
            sample.close();
        }
        state.setText(sample.backend + " · " + details);
        setBusy(false);
    }

    private void setBusy(boolean value) {
        busy = value;
        for (int i = 0; i < captureButtons.length; i++) {
            captureButtons[i].setEnabled(!value && CaptureBackends.isSupported(i));
        }
        sceneScroll.setCaptureLocked(value);
        bitmapCompare.setCaptureLocked(value);
        comparison.setEnabled(!value);
    }

    @Override
    protected void onDestroy() {
        destroying = true;
        currentTask.cancel();
        bitmapCompare.setBitmap(null, false);
        if (displayed != null) {
            displayed.close();
        }
        sceneScroll.removeAllViews();
        content.close();
        worker.shutdown();
        super.onDestroy();
    }
}
