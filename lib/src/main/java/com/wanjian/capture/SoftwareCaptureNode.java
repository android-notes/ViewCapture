package com.wanjian.capture;

import android.graphics.Bitmap;
import android.graphics.Canvas;

final class SoftwareCaptureNode extends CaptureNode {
    private Bitmap bitmap;

    SoftwareCaptureNode(int width, int height) {
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    @Override
    Canvas beginRecording(int width, int height) {
        return new Canvas(bitmap);
    }

    @Override
    void endRecording(Canvas canvas) {
        canvas.setBitmap(null);
    }

    @Override
    Bitmap createBitmap(int width, int height) {
        Bitmap result = bitmap;
        bitmap = null;
        return result;
    }

    @Override
    public void close() {
        if (bitmap != null) {
            bitmap.recycle();
        }
    }
}
