package io.github.viewcapture.demo;

import android.graphics.Bitmap;
import android.os.Build;

public final class CaptureSample implements AutoCloseable {
    public final String backend;
    public final int width;
    public final int height;
    public String status = "OK";
    public String message = "";
    public Bitmap bitmap;
    public long totalNs;
    public long setupNs;
    public long renderingNs;
    public long cleanupNs;
    public long scrollingNs;
    public long stitchingNs;
    public int segmentCount;

    CaptureSample(String backend, int width, int height) {
        this.backend = backend;
        this.width = width;
        this.height = height;
    }

    /** Recycle the result after use if it differs from the captured bitmap. */
    Bitmap readableBitmap() {
        Bitmap readable =
                Build.VERSION.SDK_INT >= 26 && bitmap.getConfig() == Bitmap.Config.HARDWARE
                        ? bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        : bitmap;
        if (readable == null) {
            throw new IllegalStateException("Could not read hardware bitmap");
        }
        return readable;
    }

    @Override
    public void close() {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        bitmap = null;
    }
}
