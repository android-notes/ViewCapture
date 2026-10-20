package io.github.viewcapture.demo;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;
import com.wanjian.capture.ViewCapture;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Release-only scale timing and isolated memory measurements on API 29+. */
@android.annotation.TargetApi(29)
public final class ScaleBenchmarkActivity extends Activity {
    private static final float[] SCALES = {1f, 0.98f, 0.75f, 0.5f, 0.25f, 0.15f, 0.14f, 0.125f};
    private final Handler main = new Handler(Looper.getMainLooper());
    private final JSONObject report = new JSONObject();
    private final JSONArray samples = new JSONArray();
    private SceneContent scene;
    private TextView state;
    private boolean memoryOnly, destroying;
    private float memoryScale;
    private int round, shot, maximumHeight;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        state = findViewById(R.id.state);
        for (int id :
                new int[] {R.id.capture_software, R.id.capture_hardware, R.id.capture_pixel_copy}) {
            findViewById(id).setEnabled(false);
        }
        memoryOnly = getIntent().getBooleanExtra("memoryOnly", false);
        memoryScale = getIntent().getFloatExtra("scale", 1f);
        scene = new SceneContent(this, 6, true, 11);
        CaptureScrollView scroll = findViewById(R.id.scene_scroll);
        scroll.setCaptureLocked(true);
        scroll.addView(scene.target, new ScrollView.LayoutParams(-1, -2));
        RenderNode probe = new RenderNode("ScaleBenchmarkLimit");
        maximumHeight = probe.beginRecording(1, 1).getMaximumBitmapHeight();
        probe.endRecording();
        probe.discardDisplayList();
        try {
            report.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            report.put("fingerprint", Build.FINGERPRINT);
            report.put("api", Build.VERSION.SDK_INT);
            report.put("pid", android.os.Process.myPid());
            report.put(
                    "debuggable",
                    (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
            report.put("mode", memoryOnly ? "cold-process memory" : "warm timing");
            report.put("maximumBitmapHeight", maximumHeight);
            report.put("warmupPerScale", memoryOnly ? 0 : 1);
            report.put("measuredCapturesPerScale", memoryOnly ? 1 : 5);
            report.put("startedAtEpochMs", System.currentTimeMillis());
            report.put("samples", samples);
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
        awaitScene(System.nanoTime() + 15_000_000_000L);
    }

    private void awaitScene(long deadline) {
        if (scene.ready()) {
            main.postDelayed(this::measure, 5000);
        } else if (System.nanoTime() < deadline) {
            main.postDelayed(() -> awaitScene(deadline), 100);
        } else {
            save("scene not ready");
        }
    }

    private void measure() {
        if (destroying) {
            return;
        }
        float scale = memoryOnly ? memoryScale : SCALES[(round + shot) % SCALES.length];
        state.setText("Scale " + scale + " · " + (memoryOnly ? "memory" : "round " + round));
        Bitmap bitmap = null, pixels = null;
        CaptureMemorySampler sampler = memoryOnly ? new CaptureMemorySampler() : null;
        try {
            JSONObject result = new JSONObject();
            result.put("scale", scale);
            result.put("trial", round);
            result.put("warmup", !memoryOnly && round == 0);
            if (memoryOnly) {
                result.put("before", CaptureMemorySampler.snapshot());
                sampler.start();
            }
            long started = System.nanoTime();
            bitmap = ViewCapture.capture(scene.target, scale);
            double captureMs = (System.nanoTime() - started) / 1_000_000.0;
            if (memoryOnly) {
                sampler.close();
                result.put("sampledPeakRssKb", sampler.peakKb());
                sampler = null;
                result.put("retained", CaptureMemorySampler.snapshot());
            }
            long readbackStarted = System.nanoTime();
            pixels =
                    bitmap.getConfig() == Bitmap.Config.HARDWARE
                            ? bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            : bitmap;
            double readbackMs = (System.nanoTime() - readbackStarted) / 1_000_000.0;
            if (pixels == null) {
                throw new IllegalStateException("Could not read capture");
            }
            result.put("captureMs", captureMs);
            result.put("cpuReadyMs", captureMs + readbackMs);
            result.put("width", bitmap.getWidth());
            result.put("height", bitmap.getHeight());
            result.put("config", bitmap.getConfig().name());
            result.put("allocationByteCount", bitmap.getAllocationByteCount());
            result.put("logicalPixelBytes", (long) bitmap.getWidth() * bitmap.getHeight() * 4);
            result.put("tiles", 1 + (bitmap.getHeight() - 1) / maximumHeight);
            int markers = 0, hardwarePatches = 0;
            for (int i = 0; i < scene.target.getChildCount(); i++) {
                View child = scene.target.getChildAt(i);
                int id = child.getId();
                if (id == R.id.start_marker || id == R.id.end_marker || id == R.id.bitmap_sample) {
                    int expected =
                            id == R.id.start_marker
                                    ? SceneContent.TOP_COLOR
                                    : id == R.id.end_marker
                                            ? SceneContent.BOTTOM_COLOR
                                            : SceneContent.BITMAP_COLOR;
                    if (pixels.getPixel(
                                    (int) ((child.getRight() - 10) * scale),
                                    (int) ((child.getBottom() - 10) * scale))
                            == expected) {
                        if (id == R.id.bitmap_sample) {
                            hardwarePatches++;
                        } else {
                            markers++;
                        }
                    }
                }
            }
            boolean valid =
                    bitmap.getWidth() == Math.max(1, Math.round(scene.target.getWidth() * scale))
                            && bitmap.getHeight()
                                    == Math.max(1, Math.round(scene.target.getHeight() * scale))
                            && markers == 22
                            && hardwarePatches == 11
                            && Color.alpha(pixels.getPixel(0, 0)) == 0
                            && Color.alpha(pixels.getPixel(0, pixels.getHeight() - 1)) == 0;
            result.put("matchingMarkers", markers);
            result.put("matchingHardwarePatches", hardwarePatches);
            result.put("valid", valid);
            samples.put(result);
            if (!valid) {
                save("failed validation");
                return;
            }
            if (memoryOnly || (++shot == SCALES.length && ++round > 5)) {
                report.put("sourceWidth", scene.target.getWidth());
                report.put("sourceHeight", scene.target.getHeight());
                report.put("finishedAtEpochMs", System.currentTimeMillis());
                save("complete");
                return;
            }
            if (shot == SCALES.length) {
                shot = 0;
            }
            save("running");
            main.postDelayed(this::measure, 250);
        } catch (JSONException | RuntimeException error) {
            Log.e("ScaleBenchmark", "Capture failed", error);
            save("failed: " + error);
        } finally {
            if (sampler != null) {
                sampler.close();
            }
            if (pixels != null && pixels != bitmap) {
                pixels.recycle();
            }
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private void save(String status) {
        try {
            report.put("status", status);
            String filename =
                    memoryOnly ? "scale-memory-" + memoryScale + ".json" : "scale-timing.json";
            try (FileOutputStream stream =
                    new FileOutputStream(new File(getExternalFilesDir(null), filename))) {
                stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            if (!status.equals("running")) {
                state.setText("Scale benchmark " + status);
            }
        } catch (java.io.IOException | JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    @Override
    protected void onDestroy() {
        destroying = true;
        main.removeCallbacksAndMessages(null);
        ((ScrollView) scene.target.getParent()).removeAllViews();
        scene.close();
        super.onDestroy();
    }
}
