package io.github.viewcapture.demo;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import io.github.viewcapture.demo.sample.VideoTextureSampleView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exports unmodified capture outputs from the live-video showcase. */
@RunWith(AndroidJUnit4.class)
public final class ReadmeCaptureTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Activity activity;
    private LinearLayout target;
    private VideoTextureSampleView video;

    @Test
    public void exportLiveVideoShowcase() throws Exception {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 31);
        String packageName = instrumentation.getTargetContext().getPackageName();
        try (ParcelFileDescriptor descriptor =
                        instrumentation
                                .getUiAutomation()
                                .executeShellCommand(
                                        "am start -W -n "
                                                + packageName
                                                + "/io.github.viewcapture.demo.MainActivity --activity-clear-top --ez showcase true");
                FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            byte[] bytes = new byte[1024];
            while (input.read(bytes) != -1) {}
        }
        long deadline = SystemClock.uptimeMillis() + 10000;
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(
                    () -> {
                        for (Activity candidate :
                                ActivityLifecycleMonitorRegistry.getInstance()
                                        .getActivitiesInStage(Stage.RESUMED)) {
                            if (candidate instanceof MainActivity) {
                                activity = candidate;
                            }
                        }
                        if (activity != null) {
                            target =
                                    activity.getWindow()
                                            .getDecorView()
                                            .findViewWithTag("capture-target");
                            video = target.findViewById(R.id.video_sample);
                            ready.set(
                                    target.isLaidOut()
                                            && !target.isLayoutRequested()
                                            && video.isFrameReady());
                        }
                    });
            if (ready.get()) {
                break;
            }
            SystemClock.sleep(32);
        }
        assertTrue("Live video showcase must provide a frame", ready.get());
        SystemClock.sleep(250);
        File directory = new File(instrumentation.getTargetContext().getFilesDir(), "readme");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap first = videoFrame();
        JSONObject report = new JSONObject();
        report.put("device", Build.MODEL);
        report.put("api", Build.VERSION.SDK_INT);
        report.put("buildType", BuildConfig.BUILD_TYPE);
        report.put("videoSource", "res/raw/flower.mp4");
        report.put("width", target.getWidth());
        report.put("height", target.getHeight());
        report.put("videoPlayingDuringCapture", true);
        report.put("renderEffect", "blur(7px)");
        JSONArray captures = new JSONArray();
        String[] files = {"software-canvas.png", "viewcapture.png", "pixelcopy.png"};
        for (int backend = 0; backend < files.length; backend++) {
            AtomicReference<CaptureSample> result = new AtomicReference<>();
            CountDownLatch completed = new CountDownLatch(1);
            int method = backend;
            instrumentation.runOnMainSync(
                    () ->
                            CaptureBackends.capture(
                                    method,
                                    activity,
                                    target,
                                    sample -> {
                                        result.set(sample);
                                        completed.countDown();
                                    }));
            assertTrue("Capture must complete", completed.await(20, TimeUnit.SECONDS));
            try (CaptureSample sample = result.get()) {
                assertEquals(sample.message, "OK", sample.status);
                Bitmap pixels = sample.readableBitmap();
                try {
                    assertEquals(target.getWidth(), pixels.getWidth());
                    assertEquals(target.getHeight(), pixels.getHeight());
                    int paddingAlpha = Color.alpha(pixels.getPixel(5, 5));
                    assertEquals(backend == 2 ? 255 : 0, paddingAlpha);
                    try (FileOutputStream output =
                            new FileOutputStream(new File(directory, files[backend]))) {
                        assertTrue(pixels.compress(Bitmap.CompressFormat.PNG, 100, output));
                    }
                    captures.put(
                            new JSONObject()
                                    .put("method", sample.backend)
                                    .put("file", files[backend])
                                    .put("paddingAlpha", paddingAlpha));
                } finally {
                    if (pixels != sample.bitmap) {
                        pixels.recycle();
                    }
                }
            }
            SystemClock.sleep(150);
        }
        SystemClock.sleep(350);
        Bitmap later = videoFrame();
        try {
            assertFalse("The video must keep playing throughout capture", first.sameAs(later));
            report.put("videoFramesChanged", true);
        } finally {
            first.recycle();
            later.recycle();
        }
        JSONArray regions = new JSONArray();
        instrumentation.runOnMainSync(
                () -> {
                    for (int i = 0; i < target.getChildCount(); i++) {
                        View child = target.getChildAt(i);
                        Rect bounds = new Rect(0, 0, child.getWidth(), child.getHeight());
                        target.offsetDescendantRectToMyCoords(child, bounds);
                        regions.put(
                                new JSONArray()
                                        .put(bounds.left)
                                        .put(bounds.top)
                                        .put(bounds.right)
                                        .put(bounds.bottom));
                    }
                    assertEquals(0, ((ScrollView) target.getParent()).getScrollY());
                });
        report.put("regions", regions);
        report.put("captures", captures);
        try (FileOutputStream output =
                new FileOutputStream(new File(directory, "capture-info.json"))) {
            output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private Bitmap videoFrame() {
        AtomicReference<Bitmap> frame = new AtomicReference<>();
        instrumentation.runOnMainSync(
                () -> {
                    android.view.TextureView texture = video.findViewById(R.id.video_producer);
                    frame.set(texture.getBitmap(160, 90));
                });
        assertNotNull(frame.get());
        return frame.get();
    }

    @After
    public void finish() {
        if (activity != null) {
            instrumentation.runOnMainSync(() -> activity.finish());
        }
        instrumentation.waitForIdleSync();
    }
}
