package io.github.viewcapture.demo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.FrameMetrics;
import android.view.Window;
import android.view.View;
import android.widget.ScrollView;

/** Sequential Window PixelCopies, after rendered-frame metrics, stitched in target-local coordinates. */
@android.annotation.TargetApi(26)
final class ScrollPixelCopyCapture
        implements CaptureBackends.Task, View.OnAttachStateChangeListener {
    private final Activity activity;
    private final ScrollView scroll;
    private final View target;
    private final CaptureSample sample;
    private final long started;
    private final CaptureBackends.Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final int originalX, originalY, originalOverScroll;
    private final boolean originalVerticalBar, originalHorizontalBar, originalSmooth;
    private final Runnable timeout =
            () -> fail("Timed out waiting for a rendered frame or PixelCopy");
    private Window.OnFrameMetricsAvailableListener metricsListener;
    private Canvas output;
    private int nextY;
    private int frameGeneration;
    private long waitStarted;
    private boolean finished;

    ScrollPixelCopyCapture(
            Activity activity,
            ScrollView scroll,
            View target,
            CaptureSample sample,
            long started,
            CaptureBackends.Callback callback) {
        this.activity = activity;
        this.scroll = scroll;
        this.target = target;
        this.sample = sample;
        this.started = started;
        this.callback = callback;
        originalX = scroll.getScrollX();
        originalY = scroll.getScrollY();
        originalOverScroll = scroll.getOverScrollMode();
        originalVerticalBar = scroll.isVerticalScrollBarEnabled();
        originalHorizontalBar = scroll.isHorizontalScrollBarEnabled();
        originalSmooth = scroll.isSmoothScrollingEnabled();
    }

    void start() {
        try {
            if (!target.isHardwareAccelerated()) {
                throw new IllegalStateException("Window must use hardware rendering");
            }
            long phase = System.nanoTime();
            sample.bitmap =
                    Bitmap.createBitmap(sample.width, sample.height, Bitmap.Config.ARGB_8888);
            output = new Canvas(sample.bitmap);
            scroll.fling(0);
            scroll.computeScroll();
            scroll.setSmoothScrollingEnabled(false);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            target.addOnAttachStateChangeListener(this);
            sample.setupNs += System.nanoTime() - phase;
            nextStrip();
        } catch (RuntimeException | OutOfMemoryError e) {
            fail(e.toString());
        }
    }

    private boolean valid() {
        return !activity.isDestroyed()
                && target.isAttachedToWindow()
                && target.isShown()
                && target.getWidth() == sample.width
                && target.getHeight() == sample.height;
    }

    private void nextStrip() {
        if (finished) {
            return;
        }
        if (!valid()) {
            fail("Target detached or its layout changed during stitching");
            return;
        }
        try {
            waitStarted = System.nanoTime();
            int generation = ++frameGeneration;
            metricsListener =
                    (window, metrics, dropped) -> {
                        if (finished || generation != frameGeneration || metricsListener == null) {
                            return;
                        }
                        // Ignore completed frames that began before this strip's scroll request.
                        if (metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
                                < waitStarted) {
                            target.postInvalidateOnAnimation();
                            return;
                        }
                        window.removeOnFrameMetricsAvailableListener(metricsListener);
                        metricsListener = null;
                        main.post(
                                () -> {
                                    if (finished || generation != frameGeneration) {
                                        return;
                                    }
                                    sample.scrollingNs += System.nanoTime() - waitStarted;
                                    copyStrip();
                                });
                    };
            activity.getWindow().addOnFrameMetricsAvailableListener(metricsListener, main);
            scroll.scrollTo(0, target.getTop() + nextY - scroll.getPaddingTop());
            scroll.invalidate();
            target.invalidate();
            main.removeCallbacks(timeout);
            main.postDelayed(timeout, 3000);
        } catch (RuntimeException e) {
            fail(e.toString());
        }
    }

    private void copyStrip() {
        if (finished) {
            return;
        }
        if (!valid() || target.isLayoutRequested()) {
            fail("Target detached or its layout changed during stitching");
            return;
        }
        Rect visible = new Rect();
        if (!target.getLocalVisibleRect(visible)
                || visible.left != 0
                || visible.right != sample.width
                || visible.top > nextY
                || visible.bottom <= nextY) {
            fail("The full target width or next strip is not visible in the ScrollView");
            return;
        }
        int top = nextY;
        int bottom = Math.min(visible.bottom, sample.height);
        int[] location = new int[2];
        target.getLocationInWindow(location);
        Rect source =
                new Rect(
                        location[0],
                        location[1] + top,
                        location[0] + sample.width,
                        location[1] + bottom);
        Bitmap strip = null;
        try {
            long phase = System.nanoTime();
            strip = Bitmap.createBitmap(sample.width, bottom - top, Bitmap.Config.ARGB_8888);
            sample.setupNs += System.nanoTime() - phase;
            Bitmap destination = strip;
            long requested = System.nanoTime();
            PixelCopy.request(
                    activity.getWindow(),
                    source,
                    destination,
                    result -> {
                        main.removeCallbacks(timeout);
                        if (finished) {
                            destination.recycle();
                            return;
                        }
                        sample.renderingNs += System.nanoTime() - requested;
                        try {
                            if (result != PixelCopy.SUCCESS) {
                                fail("PixelCopy result=" + result);
                                return;
                            }
                            long stitching = System.nanoTime();
                            output.drawBitmap(
                                    destination,
                                    null,
                                    new Rect(0, top, sample.width, bottom),
                                    null);
                            sample.stitchingNs += System.nanoTime() - stitching;
                            sample.segmentCount++;
                            nextY = bottom;
                        } catch (RuntimeException | OutOfMemoryError e) {
                            fail(e.toString());
                            return;
                        } finally {
                            // A cancelled in-flight copy owns its destination until this callback.
                            destination.recycle();
                        }
                        if (nextY == sample.height) {
                            finish();
                        } else {
                            nextStrip();
                        }
                    },
                    main);
        } catch (RuntimeException | OutOfMemoryError e) {
            if (strip != null) {
                strip.recycle();
            }
            fail(e.toString());
        }
    }

    private void fail(String message) {
        if (finished) {
            return;
        }
        sample.status = "ERROR";
        sample.message = message;
        finish();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        ++frameGeneration;
        long phase = System.nanoTime();
        main.removeCallbacks(timeout);
        if (metricsListener != null) {
            activity.getWindow().removeOnFrameMetricsAvailableListener(metricsListener);
        }
        target.removeOnAttachStateChangeListener(this);
        scroll.scrollTo(originalX, originalY);
        scroll.setVerticalScrollBarEnabled(originalVerticalBar);
        scroll.setHorizontalScrollBarEnabled(originalHorizontalBar);
        scroll.setSmoothScrollingEnabled(originalSmooth);
        scroll.setOverScrollMode(originalOverScroll);
        output = null;
        if (!sample.status.equals("OK") && sample.bitmap != null) {
            sample.bitmap.recycle();
            sample.bitmap = null;
        }
        sample.cleanupNs += System.nanoTime() - phase;
        sample.totalNs = System.nanoTime() - started;
        callback.accept(sample);
    }

    @Override
    public void cancel() {
        fail("Capture cancelled");
    }

    @Override
    public void onViewAttachedToWindow(View view) {}

    @Override
    public void onViewDetachedFromWindow(View view) {
        cancel();
    }
}
