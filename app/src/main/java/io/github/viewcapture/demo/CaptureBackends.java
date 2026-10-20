package io.github.viewcapture.demo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.widget.ScrollView;
import com.wanjian.capture.ViewCapture;

final class CaptureBackends {
    static final String[] NAMES = {"Software Canvas", "ViewCapture", "PixelCopy stitching"};

    interface Task {
        void cancel();
    }

    interface Callback {
        void accept(CaptureSample sample);
    }

    static final Task COMPLETED = () -> {};

    private CaptureBackends() {}

    static int minApi(int backend) {
        return backend == 2 ? 26 : 21;
    }

    static boolean isSupported(int backend) {
        return Build.VERSION.SDK_INT >= minApi(backend);
    }

    static Task capture(int backend, Activity activity, View target, Callback callback) {
        CaptureSample sample =
                new CaptureSample(NAMES[backend], target.getWidth(), target.getHeight());
        long started = System.nanoTime();
        if (!target.isAttachedToWindow()
                || target.isLayoutRequested()
                || target.getWidth() <= 0
                || target.getHeight() <= 0) {
            sample.status = "ERROR";
            sample.message = "Target has not completed layout";
            callback.accept(sample);
            return COMPLETED;
        }
        try {
            switch (backend) {
                case 0:
                    software(target, sample);
                    break;
                case 1:
                    sample.bitmap = ViewCapture.capture(target);
                    break;
                case 2:
                    {
                        if (Build.VERSION.SDK_INT < 26) {
                            sample.status = "UNSUPPORTED";
                            sample.message = "PixelCopy stitching requires API 26+";
                            break;
                        }
                        if (!(target.getParent() instanceof ScrollView)) {
                            throw new IllegalArgumentException(
                                    "PixelCopy stitching requires a direct ScrollView child");
                        }
                        ScrollPixelCopyCapture task =
                                new ScrollPixelCopyCapture(
                                        activity,
                                        (ScrollView) target.getParent(),
                                        target,
                                        sample,
                                        started,
                                        callback);
                        task.start();
                        return task;
                    }
                default:
                    throw new IllegalArgumentException("Unknown backend");
            }
            sample.totalNs = System.nanoTime() - started;
        } catch (RuntimeException | LinkageError | OutOfMemoryError e) {
            if (sample.bitmap != null) {
                sample.bitmap.recycle();
                sample.bitmap = null;
            }
            sample.status = "ERROR";
            sample.message = e.getClass().getSimpleName() + ": " + e.getMessage();
            sample.totalNs = System.nanoTime() - started;
        }
        callback.accept(sample);
        return COMPLETED;
    }

    private static void software(View target, CaptureSample sample) {
        long phase = System.nanoTime();
        sample.bitmap =
                Bitmap.createBitmap(target.getWidth(), target.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sample.bitmap);
        sample.setupNs = System.nanoTime() - phase;
        phase = System.nanoTime();
        int maximumHeight = canvas.getMaximumBitmapHeight();
        try {
            for (int top = 0; top < sample.height; ) {
                int height = Math.min(maximumHeight, sample.height - top);
                int saved = canvas.save();
                try {
                    canvas.clipRect(0, top, sample.width, top + height);
                    target.draw(canvas);
                } finally {
                    canvas.restoreToCount(saved);
                }
                sample.segmentCount++;
                top += height;
            }
        } finally {
            canvas.setBitmap(null);
        }
        sample.renderingNs = System.nanoTime() - phase;
    }
}
