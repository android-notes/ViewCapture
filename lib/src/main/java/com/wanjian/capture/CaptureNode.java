package com.wanjian.capture;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;

abstract class CaptureNode implements AutoCloseable {
    static CaptureNode create(int width, int height) {
        if (Build.VERSION.SDK_INT >= 29) {
            return new Api29CaptureNode(width, height);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            return new Api26CaptureNode(width, height);
        }
        return new SoftwareCaptureNode(width, height);
    }

    static CaptureNode createTiles(int width, int height) {
        return Build.VERSION.SDK_INT >= 29
                ? new ImageReaderCaptureNode(width, height)
                : create(width, height);
    }

    abstract Canvas beginRecording(int width, int height);

    abstract void endRecording(Canvas canvas);

    abstract Bitmap createBitmap(int width, int height);

    @Override
    public abstract void close();
}
