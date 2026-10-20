package io.github.viewcapture.demo;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.wanjian.capture.ViewCapture;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class CaptureDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private CaptureTestActivity activity;

    private void launch(int scene, boolean longContent) {
        launch(scene, longContent, 1);
    }

    private void launch(int scene, boolean longContent, int groups) {
        // Shell launch avoids OEM background-activity restrictions on instrumentation UIDs.
        String command =
                "am start -W -n io.github.viewcapture.demo/.CaptureTestActivity --ei scene "
                        + scene
                        + " --ez long "
                        + longContent
                        + " --ei groups "
                        + groups;
        try (android.os.ParcelFileDescriptor descriptor =
                        instrumentation.getUiAutomation().executeShellCommand(command);
                java.io.FileInputStream stream =
                        new java.io.FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) {
                /* Drain am start completion output. */
            }
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not launch fixture", e);
        }
        long deadline = SystemClock.uptimeMillis() + 8000;
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(
                    () -> {
                        for (android.app.Activity candidate :
                                ActivityLifecycleMonitorRegistry.getInstance()
                                        .getActivitiesInStage(Stage.RESUMED)) {
                            if (candidate instanceof CaptureTestActivity) {
                                activity = (CaptureTestActivity) candidate;
                            }
                        }
                        ready.set(activity != null && activity.scene.ready());
                    });
            if (ready.get()) {
                break;
            }
            SystemClock.sleep(32);
        }
        assertTrue("Host and producer frame must be ready", ready.get());
        instrumentation.waitForIdleSync();
        // Producer buffers are frozen; wait two display frames for Window PixelCopy tests.
        SystemClock.sleep(100);
    }

    private CaptureSample capture(int backend) throws Exception {
        return capture(backend, activity.scene.target);
    }

    private CaptureSample capture(int backend, View target) throws Exception {
        AtomicReference<CaptureSample> result = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        instrumentation.runOnMainSync(
                () ->
                        CaptureBackends.capture(
                                backend,
                                activity,
                                target,
                                sample -> {
                                    result.set(sample);
                                    completed.countDown();
                                }));
        assertTrue("Screenshot completion timed out", completed.await(60, TimeUnit.SECONDS));
        return result.get();
    }

    private String validation(CaptureSample sample) {
        AtomicReference<List<ContentValidation.Probe>> probes = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> probes.set(ContentValidation.probes(activity.scene)));
        return ContentValidation.validate(sample, probes.get(), activity.scene.effects);
    }

    @After
    public void finish() {
        if (activity != null) {
            instrumentation.runOnMainSync(() -> activity.finish());
        }
        instrumentation.waitForIdleSync();
    }

    @Test
    public void libraryCapturesBottomOutsideViewportEvenAfterScrolling() {
        launch(0, true);
        AtomicReference<Bitmap> output = new AtomicReference<>();
        instrumentation.runOnMainSync(
                () -> {
                    android.util.DisplayMetrics display = new android.util.DisplayMetrics();
                    activity.getWindowManager().getDefaultDisplay().getRealMetrics(display);
                    assertTrue(
                            "Full content must be at least two screen heights",
                            activity.scene.target.getHeight() >= display.heightPixels * 2);
                    assertTrue(activity.scene.target.getHeight() > activity.scroll.getHeight());
                    activity.scroll.scrollTo(0, activity.scene.target.getHeight() / 2);
                    output.set(ViewCapture.capture(activity.scene.target));
                });
        Bitmap bitmap = output.get();
        try {
            assertEquals(
                    Build.VERSION.SDK_INT >= 26 ? "HARDWARE" : "ARGB_8888",
                    bitmap.getConfig().name());
            assertEquals(activity.scene.target.getWidth(), bitmap.getWidth());
            assertEquals(activity.scene.target.getHeight(), bitmap.getHeight());
            Bitmap readable = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            assertNotNull(readable);
            try {
                assertEquals(
                        SceneContent.TOP_COLOR,
                        readable.getPixel(
                                activity.scene.topMarker.getRight() - 10,
                                activity.scene.topMarker.getTop() + 10));
                assertEquals(
                        SceneContent.BOTTOM_COLOR,
                        readable.getPixel(
                                activity.scene.bottomMarker.getRight() - 10,
                                activity.scene.bottomMarker.getBottom() - 10));
            } finally {
                readable.recycle();
            }
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void libraryScalesDuringDrawingAndRejectsInvalidRatios() throws Exception {
        for (float scale :
                new float[] {
                    0f, -0.5f, 1.01f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY
                }) {
            try {
                ViewCapture.capture(new View(instrumentation.getTargetContext()), scale);
                fail("Invalid scale must be rejected: " + scale);
            } catch (IllegalArgumentException expected) {
                assertEquals("Scale must be in (0, 1]", expected.getMessage());
            }
        }
        launch(0, false);
        ScaleProbeView probe = layoutScaleProbe(101);
        for (float scale : new float[] {1f, 0.5f, 0.25f, 0.3f, Float.MIN_VALUE}) {
            Bitmap bitmap = captureScaled(probe, scale);
            try {
                assertEquals(Math.max(1, Math.round(64 * scale)), bitmap.getWidth());
                assertEquals(Math.max(1, Math.round(101 * scale)), bitmap.getHeight());
                assertEquals(1, probe.draws);
                assertEquals(Build.VERSION.SDK_INT >= 26, probe.hardware);
            } finally {
                bitmap.recycle();
            }
        }
    }

    @Test
    public void libraryScalesNestedHardwareContent() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 26);
        launch(3, true);
        AtomicReference<Bitmap> result = new AtomicReference<>();
        instrumentation.runOnMainSync(
                () -> result.set(ViewCapture.capture(activity.scene.target, 0.5f)));
        Bitmap bitmap = result.get();
        try {
            assertEquals(Math.round(activity.scene.target.getWidth() * 0.5f), bitmap.getWidth());
            assertEquals(Math.round(activity.scene.target.getHeight() * 0.5f), bitmap.getHeight());
            Bitmap pixels = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            assertNotNull(pixels);
            try {
                View[] samples = {
                    activity.scene.topMarker, activity.scene.bitmapView, activity.scene.bottomMarker
                };
                int[] colors = {
                    SceneContent.TOP_COLOR, SceneContent.BITMAP_COLOR, SceneContent.BOTTOM_COLOR
                };
                for (int i = 0; i < samples.length; i++) {
                    assertEquals(
                            "Scaled nested content " + i,
                            colors[i],
                            pixels.getPixel(
                                    (samples[i].getRight() - 10) / 2,
                                    (samples[i].getBottom() - 10) / 2));
                }
                assertEquals(0, pixels.getPixel(0, 0));
            } finally {
                pixels.recycle();
            }
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void libraryUsesScaledHeightForTilingAndKeepsEveryOutputRow() throws Exception {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 26);
        launch(0, false);
        ScaleProbeView initial = layoutScaleProbe(64);
        Bitmap first = captureScaled(initial, 1f);
        first.recycle();
        int maximum = initial.maximumHeight;
        for (int height : new int[] {maximum * 2, maximum * 2 + 2, maximum * 4 + 74}) {
            ScaleProbeView probe = layoutScaleProbe(height);
            Bitmap bitmap = captureScaled(probe, 0.5f);
            try {
                int outputHeight = height / 2;
                assertEquals(32, bitmap.getWidth());
                assertEquals(outputHeight, bitmap.getHeight());
                assertEquals(1 + (outputHeight - 1) / maximum, probe.draws);
                assertEquals(
                        outputHeight <= maximum ? Bitmap.Config.HARDWARE : Bitmap.Config.ARGB_8888,
                        bitmap.getConfig());
                Bitmap pixels = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                assertNotNull(pixels);
                try {
                    for (int y = 0; y < outputHeight; y++) {
                        assertEquals("Scaled row " + y, stripeColor(y / 4), pixels.getPixel(16, y));
                        assertEquals("Left alpha at scaled row " + y, 0, pixels.getPixel(0, y));
                        assertEquals("Right alpha at scaled row " + y, 0, pixels.getPixel(31, y));
                    }
                } finally {
                    pixels.recycle();
                }
            } finally {
                bitmap.recycle();
            }
        }
    }

    private ScaleProbeView layoutScaleProbe(int height) throws InterruptedException {
        AtomicReference<ScaleProbeView> view = new AtomicReference<>();
        CountDownLatch laidOut = new CountDownLatch(1);
        instrumentation.runOnMainSync(
                () -> {
                    ScaleProbeView probe = new ScaleProbeView(activity);
                    probe.setMinimumHeight(height);
                    view.set(probe);
                    activity.scroll.removeAllViews();
                    activity.scroll.addView(
                            probe, new android.widget.ScrollView.LayoutParams(64, height));
                    probe.postOnAnimation(() -> probe.postOnAnimation(laidOut::countDown));
                });
        assertTrue("Scale probe layout timed out", laidOut.await(3, TimeUnit.SECONDS));
        assertEquals(64, view.get().getWidth());
        assertEquals(height, view.get().getHeight());
        return view.get();
    }

    private Bitmap captureScaled(ScaleProbeView probe, float scale) {
        AtomicReference<Bitmap> output = new AtomicReference<>();
        instrumentation.runOnMainSync(
                () -> {
                    activity.scroll.scrollTo(0, probe.getHeight() / 2);
                    int scrollY = activity.scroll.getScrollY();
                    probe.scale = scale;
                    probe.draws = 0;
                    try {
                        output.set(ViewCapture.capture(probe, scale));
                    } finally {
                        probe.scale = 0f;
                    }
                    assertEquals(scrollY, activity.scroll.getScrollY());
                    assertEquals(64, probe.getWidth());
                });
        return output.get();
    }

    private static final class ScaleProbeView extends View {
        final Paint paint = new Paint();
        float scale;
        int draws, maximumHeight;
        boolean hardware;

        ScaleProbeView(android.content.Context context) {
            super(context);
        }

        @Override
        @SuppressWarnings("deprecation")
        protected void onDraw(Canvas canvas) {
            if (scale > 0f) {
                draws++;
                hardware = canvas.isHardwareAccelerated();
                maximumHeight = canvas.getMaximumBitmapHeight();
                float[] matrix = new float[9];
                canvas.getMatrix().getValues(matrix);
                assertEquals("Scale must be applied before View.draw", scale, matrix[0], 0f);
                assertEquals("Height must use the same scale", scale, matrix[4], 0f);
                assertEquals(Math.max(1, Math.round(getWidth() * scale)), canvas.getWidth());
            }
            Rect clip = canvas.getClipBounds();
            for (int y = Math.max(0, clip.top / 8 * 8);
                    y < Math.min(getHeight(), clip.bottom);
                    y += 8) {
                paint.setColor(stripeColor(y / 8));
                canvas.drawRect(8, y, getWidth() - 8, Math.min(y + 8, getHeight()), paint);
            }
        }
    }

    @Test
    public void hardwareTilesKeepEveryRowAtBoundariesAndRecoverAfterDrawFailure() throws Exception {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 26);
        launch(0, false);
        StripedView stripes = new StripedView(activity);
        instrumentation.runOnMainSync(
                () -> {
                    activity.scroll.removeAllViews();
                    activity.scroll.addView(
                            stripes, new android.widget.ScrollView.LayoutParams(64, 1));
                });
        Bitmap probe = captureStripes(stripes, 1);
        probe.recycle();
        int maximum = stripes.maximumHeight;
        System.out.println("RecordingCanvas maximum bitmap height: " + maximum);
        for (int height : new int[] {maximum, maximum + 1}) {
            AtomicReference<Bitmap> direct = new AtomicReference<>();
            instrumentation.runOnMainSync(
                    () ->
                            direct.set(
                                    com.wanjian.capture.CaptureNodeTest.renderSolidHardwareBitmap(
                                            64, height, android.graphics.Color.RED)));
            assertNotNull(direct.get());
            try {
                Bitmap pixels = direct.get().copy(Bitmap.Config.ARGB_8888, false);
                assertNotNull(pixels);
                try {
                    System.out.println(
                            "Direct hardware render "
                                    + height
                                    + ": first="
                                    + Integer.toHexString(pixels.getPixel(32, 0))
                                    + ", last="
                                    + Integer.toHexString(pixels.getPixel(32, height - 1)));
                } finally {
                    pixels.recycle();
                }
            } finally {
                direct.get().recycle();
            }
        }
        for (int height : new int[] {maximum - 1, maximum, maximum + 1, maximum * 2 + 37}) {
            Bitmap bitmap = captureStripes(stripes, height);
            try {
                assertEquals(
                        height <= maximum ? Bitmap.Config.HARDWARE : Bitmap.Config.ARGB_8888,
                        bitmap.getConfig());
                assertEquals(64, bitmap.getWidth());
                assertEquals(height, bitmap.getHeight());
                assertEquals(1 + (height - 1) / maximum, stripes.draws);
                Bitmap pixels =
                        bitmap.getConfig() == Bitmap.Config.ARGB_8888
                                ? bitmap
                                : bitmap.copy(Bitmap.Config.ARGB_8888, false);
                assertNotNull(pixels);
                try {
                    for (int y = 0; y < height; y++) {
                        assertEquals(
                                "Row " + y + " of " + height,
                                stripeColor(y),
                                pixels.getPixel(32, y));
                        assertEquals("Left alpha at row " + y, 0, pixels.getPixel(0, y));
                        assertEquals("Right alpha at row " + y, 0, pixels.getPixel(63, y));
                    }
                } finally {
                    if (pixels != bitmap) {
                        pixels.recycle();
                    }
                }
            } finally {
                bitmap.recycle();
            }
        }
        instrumentation.runOnMainSync(
                () -> {
                    stripes.draws = 0;
                    stripes.failOnDraw = 2;
                    try {
                        ViewCapture.capture(stripes);
                        fail("The second tile's drawing error must propagate");
                    } catch (IllegalStateException expected) {
                        assertEquals("Tile draw failure", expected.getMessage());
                    } finally {
                        stripes.failOnDraw = 0;
                    }
                });
        Bitmap recovered = captureStripes(stripes, maximum + 1);
        try {
            Bitmap pixels = recovered.copy(Bitmap.Config.ARGB_8888, false);
            assertNotNull(pixels);
            try {
                assertEquals(stripeColor(maximum), pixels.getPixel(32, maximum));
            } finally {
                pixels.recycle();
            }
        } finally {
            recovered.recycle();
        }

        int groups = maximum / SceneContent.dp(activity, 384) + 1;
        CountDownLatch nestedLayout = new CountDownLatch(1);
        instrumentation.runOnMainSync(
                () -> {
                    activity.scroll.removeAllViews();
                    activity.scene.close();
                    activity.scene = new SceneContent(activity, 3, true, groups);
                    activity.scroll.addView(
                            activity.scene.target,
                            new android.widget.ScrollView.LayoutParams(-1, -2));
                    activity.scene.target.postOnAnimation(
                            () -> activity.scene.target.postOnAnimation(nestedLayout::countDown));
                });
        assertTrue(nestedLayout.await(3, TimeUnit.SECONDS));
        try (CaptureSample nested = capture(1)) {
            assertEquals(nested.message, "OK", nested.status);
            assertTrue(nested.height > maximum);
            assertEquals(Bitmap.Config.ARGB_8888, nested.bitmap.getConfig());
            int markers = 0;
            for (int i = 0; i < activity.scene.target.getChildCount(); i++) {
                View child = activity.scene.target.getChildAt(i);
                if (child.getId() == R.id.start_marker || child.getId() == R.id.end_marker) {
                    int color =
                            child.getId() == R.id.start_marker
                                    ? SceneContent.TOP_COLOR
                                    : SceneContent.BOTTOM_COLOR;
                    assertEquals(
                            "Nested marker " + markers,
                            color,
                            nested.bitmap.getPixel(child.getRight() - 10, child.getTop() + 10));
                    markers++;
                } else if (child.getId() == R.id.bitmap_sample) {
                    assertEquals(
                            "Hardware-only pixels must survive ARGB output",
                            SceneContent.BITMAP_COLOR,
                            nested.bitmap.getPixel(child.getRight() - 10, child.getBottom() - 10));
                }
            }
            assertEquals(groups * 2, markers);
        }
    }

    private Bitmap captureStripes(StripedView stripes, int height) throws InterruptedException {
        layoutStripes(stripes, height);
        AtomicReference<Bitmap> result = new AtomicReference<>();
        instrumentation.runOnMainSync(
                () -> {
                    int scrollY = activity.scroll.getScrollY();
                    stripes.draws = 0;
                    result.set(ViewCapture.capture(stripes));
                    assertEquals(scrollY, activity.scroll.getScrollY());
                });
        return result.get();
    }

    private void layoutStripes(StripedView stripes, int height) throws InterruptedException {
        CountDownLatch laidOut = new CountDownLatch(1);
        instrumentation.runOnMainSync(
                () -> {
                    android.view.ViewGroup.LayoutParams params = stripes.getLayoutParams();
                    params.height = height;
                    stripes.setMinimumHeight(height);
                    stripes.setLayoutParams(params);
                    stripes.postOnAnimation(() -> stripes.postOnAnimation(laidOut::countDown));
                });
        assertTrue("Striped view layout timed out", laidOut.await(3, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(
                () -> {
                    assertEquals(64, stripes.getWidth());
                    assertEquals(height, stripes.getHeight());
                    activity.scroll.scrollTo(0, height / 2);
                });
    }

    @Test
    public void otherBackendsKeepEveryRowAcrossTallCaptureBoundaries() throws Exception {
        launch(0, false);
        StripedView stripes = new StripedView(activity);
        instrumentation.runOnMainSync(
                () -> {
                    activity.scroll.removeAllViews();
                    activity.scroll.addView(
                            stripes, new android.widget.ScrollView.LayoutParams(64, 1));
                });
        Bitmap probe = captureStripes(stripes, 1);
        probe.recycle();
        int maximum = stripes.maximumHeight;
        // Exercises a one-row tail and multiple full tiles followed by a partial tile.
        for (int height : new int[] {maximum, maximum + 1, maximum * 2 + 37}) {
            layoutStripes(stripes, height);
            int originalY = activity.scroll.getScrollY();
            for (int backend : new int[] {0, 2}) {
                if (!CaptureBackends.isSupported(backend)) {
                    continue;
                }
                try (CaptureSample sample = capture(backend, stripes)) {
                    assertEquals(sample.message, "OK", sample.status);
                    assertEquals(height, sample.bitmap.getHeight());
                    assertEquals(64, sample.bitmap.getWidth());
                    Bitmap pixels = sample.readableBitmap();
                    try {
                        for (int y = 0; y < height; y++) {
                            assertEquals(
                                    sample.backend + " row " + y,
                                    stripeColor(y),
                                    pixels.getPixel(32, y));
                            if (backend != 2) {
                                assertEquals(0, pixels.getPixel(0, y));
                                assertEquals(0, pixels.getPixel(63, y));
                            }
                        }
                    } finally {
                        if (pixels != sample.bitmap) {
                            pixels.recycle();
                        }
                    }
                    assertEquals(originalY, activity.scroll.getScrollY());
                    assertTrue(sample.segmentCount > 0);
                }
            }
        }
    }

    @Test
    public void allBackendsCaptureElevenGroupsAtFullHeight() throws Exception {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 26);
        launch(6, true, 11);
        CountDownLatch layout = new CountDownLatch(1);
        instrumentation.runOnMainSync(
                () -> {
                    android.view.ViewGroup.LayoutParams params = activity.scroll.getLayoutParams();
                    params.width =
                            (activity.getResources().getDisplayMetrics().widthPixels
                                            - SceneContent.dp(activity, 25))
                                    / 2;
                    activity.scroll.setLayoutParams(params);
                    activity.scene.target.postOnAnimation(
                            () -> activity.scene.target.postOnAnimation(layout::countDown));
                });
        assertTrue(layout.await(3, TimeUnit.SECONDS));
        for (int backend = 0; backend < CaptureBackends.NAMES.length; backend++) {
            if (!CaptureBackends.isSupported(backend)) {
                continue;
            }
            try (CaptureSample sample = capture(backend)) {
                assertEquals(sample.backend + ": " + sample.message, "OK", sample.status);
                assertEquals(activity.scene.target.getHeight(), sample.bitmap.getHeight());
                assertEquals(activity.scene.target.getWidth(), sample.bitmap.getWidth());
                assertEquals(Bitmap.Config.ARGB_8888, sample.bitmap.getConfig());
                int markers = 0, bitmaps = 0;
                for (int i = 0; i < activity.scene.target.getChildCount(); i++) {
                    View child = activity.scene.target.getChildAt(i);
                    if (child.getId() == R.id.start_marker || child.getId() == R.id.end_marker) {
                        int color =
                                child.getId() == R.id.start_marker
                                        ? SceneContent.TOP_COLOR
                                        : SceneContent.BOTTOM_COLOR;
                        assertEquals(
                                sample.backend + " marker " + markers,
                                color,
                                sample.bitmap.getPixel(child.getRight() - 10, child.getTop() + 10));
                        markers++;
                    } else if (child.getId() == R.id.bitmap_sample && backend != 0) {
                        assertEquals(
                                sample.backend + " hardware bitmap " + bitmaps,
                                SceneContent.BITMAP_COLOR,
                                sample.bitmap.getPixel(
                                        child.getRight() - 10, child.getBottom() - 10));
                        bitmaps++;
                    }
                }
                assertEquals(22, markers);
                if (backend != 0) {
                    assertEquals(11, bitmaps);
                }
                if (backend != 2) {
                    assertEquals(0, sample.bitmap.getPixel(5, 5));
                    assertEquals(0, sample.bitmap.getPixel(5, sample.height - 5));
                }
                System.out.println(
                        "Tall "
                                + sample.backend
                                + ": "
                                + sample.width
                                + "x"
                                + sample.height
                                + ", segments="
                                + sample.segmentCount
                                + ", ms="
                                + sample.totalNs / 1_000_000.0);
            }
        }
    }

    private static int stripeColor(int y) {
        return 0xFF000000 | ((y & 255) << 16) | (((y >>> 8) & 255) << 8) | ((y * 37) & 255);
    }

    private static final class StripedView extends View {
        final Paint paint = new Paint();
        final Rect clip = new Rect();
        int maximumHeight, draws, failOnDraw;

        StripedView(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            maximumHeight = canvas.getMaximumBitmapHeight();
            if (++draws == failOnDraw) {
                throw new IllegalStateException("Tile draw failure");
            }
            canvas.getClipBounds(clip);
            for (int y = Math.max(0, clip.top); y < Math.min(getHeight(), clip.bottom); y++) {
                paint.setColor(stripeColor(y));
                canvas.drawRect(4, y, getWidth() - 4, y + 1, paint);
            }
        }
    }

    @Test
    public void softwareAndViewCapturePreserveFullOrdinaryContent() throws Exception {
        launch(0, true);
        for (int backend : new int[] {0, 1}) {
            try (CaptureSample sample = capture(backend)) {
                assertEquals(sample.message, "OK", sample.status);
                assertEquals(sample.backend, "PROBES_PASS", validation(sample));
                assertEquals(activity.scene.target.getHeight(), sample.bitmap.getHeight());
            }
        }
        if (Build.VERSION.SDK_INT < 26) {
            try (CaptureSample sample = capture(2)) {
                assertEquals("UNSUPPORTED", sample.status);
                assertNull(sample.bitmap);
            }
        }
    }

    @Test
    public void pixelCopyStitchesEveryRowAndRestoresOriginalScroll() throws Exception {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 26);
        launch(0, true);
        instrumentation.runOnMainSync(
                () -> {
                    android.view.ViewGroup.LayoutParams params = activity.scroll.getLayoutParams();
                    params.height = SceneContent.dp(activity, 220);
                    activity.scroll.setLayoutParams(params);
                    activity.scroll.setPadding(
                            0, SceneContent.dp(activity, 5), 0, SceneContent.dp(activity, 7));
                });
        instrumentation.waitForIdleSync();
        int originalY = activity.scene.target.getHeight() / 3;
        instrumentation.runOnMainSync(() -> activity.scroll.scrollTo(0, originalY));
        try (CaptureSample sample = capture(2)) {
            assertEquals(sample.message, "OK", sample.status);
            assertEquals("PROBES_PASS", validation(sample));
            assertEquals(activity.scene.target.getHeight(), sample.bitmap.getHeight());
            assertTrue("Must capture multiple viewport strips", sample.segmentCount > 2);
            assertTrue(sample.scrollingNs > 0 && sample.stitchingNs > 0);
            assertEquals(originalY, activity.scroll.getScrollY());
            // Check every row to detect repeated or skipped strips.
            instrumentation.runOnMainSync(
                    () -> {
                        for (int i = 3; i < activity.scene.target.getChildCount() - 1; i++) {
                            View row = activity.scene.target.getChildAt(i);
                            int expected =
                                    ((android.graphics.drawable.ColorDrawable) row.getBackground())
                                            .getColor();
                            assertEquals(
                                    "Row " + i,
                                    expected,
                                    sample.bitmap.getPixel(
                                            row.getRight() - 8,
                                            row.getTop() + row.getHeight() / 2));
                        }
                    });
        }
    }

    @Test
    public void cancellingPixelCopyRestoresStateAndCompletesExactlyOnce() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 26);
        launch(0, true);
        java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();
        instrumentation.runOnMainSync(
                () -> {
                    activity.scroll.scrollTo(0, 100);
                    boolean bar = activity.scroll.isVerticalScrollBarEnabled();
                    CaptureBackends.Task task =
                            CaptureBackends.capture(
                                    3,
                                    activity,
                                    activity.scene.target,
                                    sample -> {
                                        calls.incrementAndGet();
                                        assertEquals("ERROR", sample.status);
                                        assertNull(sample.bitmap);
                                    });
                    task.cancel();
                    task.cancel();
                    assertEquals(100, activity.scroll.getScrollY());
                    assertEquals(bar, activity.scroll.isVerticalScrollBarEnabled());
                });
        instrumentation.waitForIdleSync();
        assertEquals(1, calls.get());
    }

    @Test
    public void comparisonUsesOriginalPixelsAndSynchronizesBothDirections() throws Exception {
        launch(0, true);
        AtomicReference<BitmapCompareView> right = new AtomicReference<>();
        AtomicReference<ScrollSynchronizer> sync = new AtomicReference<>();
        instrumentation.runOnMainSync(
                () -> {
                    activity.scroll.removeAllViews();
                    activity.setContentView(R.layout.activity_main);
                    CaptureScrollView left = activity.findViewById(R.id.scene_scroll);
                    left.addView(
                            activity.scene.target,
                            new android.widget.ScrollView.LayoutParams(-1, -2));
                    activity.scroll = left;
                    right.set(activity.findViewById(R.id.bitmap_compare));
                    activity.findViewById(R.id.preview_empty).setVisibility(View.GONE);
                    sync.set(new ScrollSynchronizer(left, right.get()));
                });
        instrumentation.waitForIdleSync();
        try (CaptureSample software = capture(0)) {
            instrumentation.runOnMainSync(() -> right.get().setBitmap(software.bitmap, false));
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(activity.scroll.getHeight(), right.get().getHeight());
                        assertTrue(
                                Math.abs(activity.scroll.getWidth() - right.get().getWidth()) <= 1);
                        assertEquals(software.height, right.get().getChildAt(0).getHeight());
                        activity.scroll.scrollTo(0, 180);
                        assertEquals(180, right.get().getScrollY());
                        right.get().scrollTo(0, 420);
                        assertEquals(420, activity.scroll.getScrollY());
                        sync.get().setEnabled(false);
                        activity.scroll.scrollTo(0, 820);
                        assertEquals(
                                "Automatic capture scrolling must not move the old preview",
                                420,
                                right.get().getScrollY());
                    });
            try (CaptureSample stitched = capture(CaptureBackends.isSupported(2) ? 2 : 1)) {
                assertEquals(stitched.message, "OK", stitched.status);
                instrumentation.runOnMainSync(
                        () -> {
                            assertEquals(820, activity.scroll.getScrollY());
                            assertEquals(420, right.get().getScrollY());
                            right.get().setBitmap(stitched.bitmap, false);
                            sync.get().setEnabled(true);
                            assertEquals(820, right.get().getScrollY());
                            float x = right.get().getWidth() / 2f, y = right.get().getHeight() / 2f;
                            float half = right.get().getWidth() / 8f;
                            long down = SystemClock.uptimeMillis();
                            touch(
                                    right.get(),
                                    down,
                                    down,
                                    android.view.MotionEvent.ACTION_DOWN,
                                    1,
                                    x,
                                    y,
                                    half);
                            touch(
                                    right.get(),
                                    down,
                                    down + 16,
                                    android.view.MotionEvent.ACTION_POINTER_DOWN | (1 << 8),
                                    2,
                                    x,
                                    y,
                                    half);
                            touch(
                                    right.get(),
                                    down,
                                    down + 32,
                                    android.view.MotionEvent.ACTION_MOVE,
                                    2,
                                    x,
                                    y,
                                    half * 2);
                            touch(
                                    right.get(),
                                    down,
                                    down + 48,
                                    android.view.MotionEvent.ACTION_POINTER_UP | (1 << 8),
                                    2,
                                    x,
                                    y,
                                    half * 2);
                            touch(
                                    right.get(),
                                    down,
                                    down + 64,
                                    android.view.MotionEvent.ACTION_UP,
                                    1,
                                    x,
                                    y,
                                    half * 2);
                            assertEquals(stitched.height, right.get().getChildAt(0).getHeight());
                            assertEquals(activity.scroll.getScrollY(), right.get().getScrollY());
                            Bitmap viewport =
                                    Bitmap.createBitmap(
                                            right.get().getWidth(),
                                            right.get().getHeight(),
                                            Bitmap.Config.ARGB_8888);
                            try {
                                android.graphics.Canvas canvas =
                                        new android.graphics.Canvas(viewport);
                                canvas.translate(0, -right.get().getScrollY());
                                right.get().draw(canvas);
                                for (int row :
                                        new int[] {
                                            20, viewport.getHeight() / 2, viewport.getHeight() - 20
                                        }) {
                                    int original =
                                            stitched.bitmap.getPixel(
                                                    20, row + right.get().getScrollY());
                                    int rendered = viewport.getPixel(20, row);
                                    if (android.graphics.Color.alpha(original) == 0) {
                                        assertEquals(0, android.graphics.Color.alpha(rendered));
                                    } else {
                                        assertEquals(
                                                "Preview must keep original pixel coordinates after a pinch",
                                                original,
                                                rendered);
                                    }
                                }
                            } finally {
                                viewport.recycle();
                            }
                        });
            } finally {
                instrumentation.runOnMainSync(() -> right.get().setBitmap(null, false));
            }
        }
    }

    private static void touch(
            CaptureScrollView view,
            long down,
            long time,
            int action,
            int pointers,
            float centerX,
            float centerY,
            float halfSpan) {
        android.view.MotionEvent.PointerProperties[] properties =
                new android.view.MotionEvent.PointerProperties[pointers];
        android.view.MotionEvent.PointerCoords[] coordinates =
                new android.view.MotionEvent.PointerCoords[pointers];
        for (int i = 0; i < pointers; i++) {
            properties[i] = new android.view.MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = android.view.MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new android.view.MotionEvent.PointerCoords();
            coordinates[i].x = centerX + (i == 0 ? -halfSpan : halfSpan);
            coordinates[i].y = centerY;
            coordinates[i].pressure = 1;
            coordinates[i].size = 1;
        }
        android.view.MotionEvent event =
                android.view.MotionEvent.obtain(
                        down,
                        time,
                        action,
                        pointers,
                        properties,
                        coordinates,
                        0,
                        0,
                        1,
                        1,
                        0,
                        0,
                        android.view.InputDevice.SOURCE_TOUCHSCREEN,
                        0);
        try {
            view.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    @Test
    public void pixelCopyCapturesCompactFullyVisibleContent() throws Exception {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 26);
        launch(0, false);
        try (CaptureSample sample = capture(2)) {
            assertEquals(sample.message, "OK", sample.status);
            assertEquals("PROBES_PASS", validation(sample));
        }
    }

    @Test
    public void softwareSkipsHardwareOnlyFixtureAndStillCapturesTheContentEnd() throws Exception {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 26);
        launch(3, true);
        try (CaptureSample hardware = capture(1);
                CaptureSample software = capture(0)) {
            assertEquals(hardware.message, "OK", hardware.status);
            assertEquals("PROBES_PASS", validation(hardware));
            assertEquals(software.message, "OK", software.status);
            assertNotNull(software.bitmap);
            assertEquals(Bitmap.Config.ARGB_8888, software.bitmap.getConfig());
            assertEquals(activity.scene.target.getHeight(), software.bitmap.getHeight());
            assertTrue(validation(software), validation(software).contains("HARDWARE Bitmap"));
            assertEquals(
                    SceneContent.BOTTOM_COLOR,
                    software.bitmap.getPixel(
                            activity.scene.bottomMarker.getRight() - 10,
                            activity.scene.bottomMarker.getBottom() - 10));
        }
    }

    @Test
    public void textureViewHasARealFrameInHardwareSnapshot() throws Exception {
        launch(1, true);
        try (CaptureSample sample = capture(1)) {
            assertEquals(sample.message, "OK", sample.status);
            assertEquals(
                    Build.VERSION.SDK_INT >= 26 ? "PROBES_PASS" : "CONTENT_MISSING: TextureView",
                    validation(sample));
        }
    }

    @Test
    public void videoLoopsAndCapturedBitmapStaysStill() throws Exception {
        launch(7, true);
        long duration;
        android.media.MediaMetadataRetriever metadata = new android.media.MediaMetadataRetriever();
        try (android.content.res.AssetFileDescriptor file =
                activity.getResources().openRawResourceFd(R.raw.flower)) {
            metadata.setDataSource(
                    file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            duration =
                    Long.parseLong(
                            metadata.extractMetadata(
                                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));
        } finally {
            metadata.release();
        }
        SystemClock.sleep(duration + 300);
        Bitmap firstFrame = videoFrame();
        try (CaptureSample snapshot = capture(1)) {
            assertEquals(snapshot.message, "OK", snapshot.status);
            assertEquals("PROBES_PASS", validation(snapshot));
            Bitmap pixels = snapshot.readableBitmap();
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    int colored = 0;
                    int centerY =
                            activity.scene.video.getTop() + activity.scene.video.getHeight() / 2;
                    for (int y = centerY - 30; y <= centerY + 30; y += 3) {
                        for (int x = snapshot.width / 4; x < snapshot.width * 3 / 4; x += 6) {
                            int color = pixels.getPixel(x, y);
                            if (android.graphics.Color.green(color) > 35) {
                                colored++;
                            }
                        }
                    }
                    assertTrue("The snapshot must contain decoded video pixels", colored > 100);
                }
                SystemClock.sleep(500);
                Bitmap secondFrame = videoFrame();
                try {
                    assertFalse(
                            "Video must keep moving after the first loop",
                            firstFrame.sameAs(secondFrame));
                } finally {
                    secondFrame.recycle();
                }
                Bitmap later = snapshot.readableBitmap();
                try {
                    assertTrue(
                            "The captured Bitmap must stay still while video plays",
                            pixels.sameAs(later));
                } finally {
                    if (later != snapshot.bitmap) {
                        later.recycle();
                    }
                }
            } finally {
                if (pixels != snapshot.bitmap) {
                    pixels.recycle();
                }
            }
        } finally {
            firstFrame.recycle();
        }
    }

    private Bitmap videoFrame() {
        AtomicReference<Bitmap> frame = new AtomicReference<>();
        instrumentation.runOnMainSync(
                () -> {
                    android.view.TextureView texture =
                            activity.scene.video.findViewById(R.id.video_producer);
                    assertNull(
                            activity.scene
                                    .video
                                    .findViewWithTag("video-view-label")
                                    .getBackground());
                    frame.set(texture.getBitmap(96, 54));
                });
        assertNotNull(frame.get());
        return frame.get();
    }

    @Test
    public void softwareSnapshotKeepsTransparentNamesInsideSampleBounds() throws Exception {
        launch(6, true);
        java.util.ArrayList<android.graphics.Rect> labels = new java.util.ArrayList<>();
        instrumentation.runOnMainSync(
                () -> {
                    for (String tag : new String[] {"texture-view-label", "surface-view-label"}) {
                        View label = activity.scene.target.findViewWithTag(tag);
                        assertTrue(
                                "Label must be an ordinary TextView",
                                label instanceof android.widget.TextView);
                        View sampleView =
                                tag.startsWith("texture")
                                        ? activity.scene.texture
                                        : activity.scene.surface;
                        assertSame(
                                "Label overlays its sample instead of adding a separate row",
                                sampleView,
                                label.getParent());
                        assertSame(activity.scene.target, sampleView.getParent());
                        assertNull("Overlay label must have no background", label.getBackground());
                        assertEquals(0, label.getTop());
                        assertTrue(label.getHeight() < sampleView.getHeight());
                        android.graphics.Rect bounds =
                                new android.graphics.Rect(
                                        0, 0, label.getWidth(), label.getHeight());
                        activity.scene.target.offsetDescendantRectToMyCoords(label, bounds);
                        labels.add(bounds);
                    }
                });
        try (CaptureSample sample = capture(0)) {
            assertEquals(sample.message, "OK", sample.status);
            for (android.graphics.Rect label : labels) {
                int darkPixels = 0;
                for (int y = label.top; y < label.bottom; y += 2) {
                    for (int x = label.left; x < label.right; x += 2) {
                        int pixel = sample.bitmap.getPixel(x, y);
                        if (android.graphics.Color.alpha(pixel) >= 240
                                && android.graphics.Color.red(pixel) < 120
                                && android.graphics.Color.green(pixel) < 120
                                && android.graphics.Color.blue(pixel) < 120) {
                            darkPixels++;
                        }
                    }
                }
                assertTrue(
                        "Name text must actually be visible in software bitmap: " + label,
                        darkPixels > 50);
            }
            assertEquals(
                    SceneContent.BOTTOM_COLOR,
                    sample.bitmap.getPixel(
                            activity.scene.bottomMarker.getRight() - 10,
                            activity.scene.bottomMarker.getBottom() - 10));
        }
    }

    @Test
    public void surfaceViewIsReportedAsMissingRatherThanEquivalent() throws Exception {
        launch(2, true);
        try (CaptureSample sample = capture(1)) {
            assertEquals(sample.message, "OK", sample.status);
            assertTrue(
                    validation(sample),
                    validation(sample).contains("CONTENT_MISSING: SurfaceView"));
        }
    }

    @Test
    public void demoRejectsUnreadyTargets() {
        launch(0, true);
        instrumentation.runOnMainSync(
                () -> {
                    CaptureBackends.capture(
                            1,
                            activity,
                            new View(activity),
                            sample -> {
                                assertEquals("ERROR", sample.status);
                                assertNull(sample.bitmap);
                            });
                });
    }

    @Test
    public void transparentPaddingIsPreservedExceptByPixelCopy() throws Exception {
        launch(0, true);
        instrumentation.runOnMainSync(
                () -> {
                    activity.scroll.removeAllViews();
                    activity.setContentView(R.layout.activity_main);
                    activity.scroll = activity.findViewById(R.id.scene_scroll);
                    activity.scroll.addView(
                            activity.scene.target,
                            new android.widget.ScrollView.LayoutParams(-1, -2));
                    View background = (View) activity.scroll.getParent().getParent();
                    assertNotNull(background.getBackground());
                    assertNull(activity.scroll.getBackground());
                    assertNull(activity.findViewById(R.id.bitmap_compare).getBackground());
                    assertNull(activity.scene.target.getBackground());
                    assertTrue(activity.scene.target.getPaddingLeft() > 0);
                });
        instrumentation.waitForIdleSync();
        for (int backend = 0; backend < CaptureBackends.NAMES.length; backend++) {
            if (!CaptureBackends.isSupported(backend)) {
                continue;
            }
            try (CaptureSample sample = capture(backend)) {
                assertEquals(sample.message, "OK", sample.status);
                Bitmap pixels = sample.readableBitmap();
                try {
                    for (int[] point :
                            new int[][] {
                                {5, 5},
                                {5, sample.height / 2},
                                {sample.width - 5, sample.height - 5}
                            }) {
                        assertEquals(
                                sample.backend + " padding alpha",
                                backend == 2 ? 255 : 0,
                                android.graphics.Color.alpha(pixels.getPixel(point[0], point[1])));
                    }
                    if (backend == 2) {
                        assertNotEquals(android.graphics.Color.WHITE, pixels.getPixel(5, 5));
                        assertTrue(sample.segmentCount > 1);
                    }
                } finally {
                    if (pixels != sample.bitmap) {
                        pixels.recycle();
                    }
                }
            }
        }
    }
}
