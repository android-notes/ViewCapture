package io.github.viewcapture.demo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Enable with -PcaptureBenchmark=true. */
@TargetApi(26)
public final class CaptureBenchmarkActivity extends Activity {
    private static final String TAG = "CaptureBenchmark";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final JSONObject report = new JSONObject();
    private final JSONArray samples = new JSONArray();
    private CaptureBackends.Task task = CaptureBackends.COMPLETED;
    private SceneContent scene;
    private TextView state;
    private int trials, round, shot;
    private boolean destroying;

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
        trials = getIntent().getIntExtra("trials", 5);
        if (trials < 1 || Build.VERSION.SDK_INT < 26) {
            throw new IllegalArgumentException("Requires API 26+ and at least one trial");
        }
        scene = new SceneContent(this, 6, true, 11);
        ScrollView scroll = findViewById(R.id.scene_scroll);
        scroll.addView(scene.target, new ScrollView.LayoutParams(-1, -2));
        try {
            report.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            report.put("fingerprint", Build.FINGERPRINT);
            report.put("api", Build.VERSION.SDK_INT);
            report.put(
                    "debuggable",
                    (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
            report.put("measuredCapturesPerMethod", trials);
            report.put("warmupCapturesPerMethod", 1);
            report.put("interCaptureDelayMs", 250);
            report.put("scale", 1);
            report.put("samples", samples);
            report.put("startedAtEpochMs", System.currentTimeMillis());
            report.put(
                    "timing",
                    "CPU-readable complete bitmap; recording, allocation, rendering, "
                            + "readback, stitching and capture cleanup included; validation, final bitmap "
                            + "recycling, logging, file writes and delays excluded");
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
            save("Scene not ready");
        }
    }

    private void measure() {
        if (destroying) {
            return;
        }
        if (round > trials) {
            complete();
            return;
        }
        int method = (round + shot) % CaptureBackends.NAMES.length;
        state.setText(
                (round == 0 ? "Warmup" : "Trial " + round + "/" + trials)
                        + " · "
                        + CaptureBackends.NAMES[method]);
        long started = System.nanoTime();
        task =
                CaptureBackends.capture(
                        method,
                        this,
                        scene.target,
                        sample -> {
                            task = CaptureBackends.COMPLETED;
                            if (destroying) {
                                sample.close();
                                return;
                            }
                            Bitmap pixels = null;
                            boolean valid = false;
                            try {
                                if (sample.bitmap == null) {
                                    throw new IllegalStateException(sample.message);
                                }
                                pixels = sample.readableBitmap();
                                double cpuReadyMs = (System.nanoTime() - started) / 1_000_000.0;
                                JSONObject result = new JSONObject();
                                result.put("method", CaptureBackends.NAMES[method]);
                                result.put("trial", round);
                                result.put("warmup", round == 0);
                                result.put("captureMs", sample.totalNs / 1_000_000.0);
                                result.put("cpuReadyMs", cpuReadyMs);
                                result.put("width", pixels.getWidth());
                                result.put("height", pixels.getHeight());
                                result.put("config", sample.bitmap.getConfig().name());
                                int markers = 0;
                                int hardwarePatches = 0;
                                for (int i = 0; i < scene.target.getChildCount(); i++) {
                                    View child = scene.target.getChildAt(i);
                                    int id = child.getId();
                                    if (id == R.id.start_marker || id == R.id.end_marker) {
                                        int expected =
                                                id == R.id.start_marker
                                                        ? SceneContent.TOP_COLOR
                                                        : SceneContent.BOTTOM_COLOR;
                                        if (pixels.getPixel(
                                                        child.getRight() - 10, child.getTop() + 10)
                                                == expected) {
                                            markers++;
                                        }
                                    } else if (id == R.id.bitmap_sample && method != 0) {
                                        if (pixels.getPixel(
                                                        child.getRight() - 10,
                                                        child.getBottom() - 10)
                                                == SceneContent.BITMAP_COLOR) {
                                            hardwarePatches++;
                                        }
                                    }
                                }
                                int paddingAlpha = Color.alpha(pixels.getPixel(0, 0));
                                valid =
                                        pixels.getWidth() == scene.target.getWidth()
                                                && pixels.getHeight() == scene.target.getHeight()
                                                && markers == 22
                                                && (method == 0 || hardwarePatches == 11)
                                                && paddingAlpha == (method == 2 ? 255 : 0)
                                                && Color.alpha(
                                                                pixels.getPixel(
                                                                        0, pixels.getHeight() - 1))
                                                        == paddingAlpha;
                                result.put("matchingMarkers", markers);
                                result.put("matchingHardwarePatches", hardwarePatches);
                                result.put("paddingAlpha", paddingAlpha);
                                result.put("valid", valid);
                                samples.put(result);
                            } catch (JSONException | RuntimeException error) {
                                Log.e(TAG, "Capture failed", error);
                            } finally {
                                if (pixels != null && pixels != sample.bitmap) {
                                    pixels.recycle();
                                }
                                sample.close();
                            }
                            save(valid ? "running" : "failed");
                            if (valid) {
                                if (++shot == CaptureBackends.NAMES.length) {
                                    shot = 0;
                                    round++;
                                }
                                main.postDelayed(this::measure, 250);
                            }
                        });
    }

    private void complete() {
        try {
            JSONArray summary = new JSONArray();
            for (String method : CaptureBackends.NAMES) {
                ArrayList<Double> values = new ArrayList<>();
                for (int i = 0; i < samples.length(); i++) {
                    JSONObject sample = samples.getJSONObject(i);
                    if (!sample.getBoolean("warmup") && method.equals(sample.getString("method"))) {
                        values.add(sample.getDouble("cpuReadyMs"));
                    }
                }
                Collections.sort(values);
                double sum = 0;
                for (double value : values) {
                    sum += value;
                }
                JSONObject row = new JSONObject();
                row.put("method", method);
                row.put("count", values.size());
                row.put("meanMs", sum / values.size());
                row.put("minMs", values.get(0));
                row.put("maxMs", values.get(values.size() - 1));
                summary.put(row);
            }
            report.put("width", scene.target.getWidth());
            report.put("height", scene.target.getHeight());
            report.put("directChildViews", scene.target.getChildCount());
            report.put("summary", summary);
            report.put("finishedAtEpochMs", System.currentTimeMillis());
            save("complete");
            Log.i(TAG, "COMPLETE capture-benchmark.json");
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    private void save(String status) {
        try {
            report.put("status", status);
            File file = new File(getExternalFilesDir(null), "capture-benchmark.json");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            if (!status.equals("running")) {
                state.setText("Benchmark " + status + " · capture-benchmark.json");
            }
        } catch (java.io.IOException | JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    @Override
    protected void onDestroy() {
        destroying = true;
        main.removeCallbacksAndMessages(null);
        task.cancel();
        ((ScrollView) scene.target.getParent()).removeAllViews();
        scene.close();
        super.onDestroy();
    }
}
