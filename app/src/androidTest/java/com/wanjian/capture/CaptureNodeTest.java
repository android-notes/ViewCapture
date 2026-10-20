package com.wanjian.capture;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CaptureNodeTest {
    public static Bitmap renderSolidHardwareBitmap(int width, int height, int color) {
        try (CaptureNode node = CaptureNode.create(width, height)) {
            Canvas canvas = node.beginRecording(width, height);
            canvas.drawColor(color);
            node.endRecording(canvas);
            return node.createBitmap(width, height);
        }
    }

    @Test
    public void softwareNodeDrawsPixelsAndTransfersBitmapOwnership() {
        Bitmap result;
        try (CaptureNode node = new SoftwareCaptureNode(20, 30)) {
            Canvas canvas = node.beginRecording(20, 30);
            assertFalse(canvas.isHardwareAccelerated());
            canvas.drawColor(Color.BLUE);
            node.endRecording(canvas);
            result = node.createBitmap(20, 30);
        }
        try {
            assertFalse(result.isRecycled());
            assertEquals(Bitmap.Config.ARGB_8888, result.getConfig());
            assertEquals(20, result.getWidth());
            assertEquals(30, result.getHeight());
            assertEquals(Color.BLUE, result.getPixel(0, 0));
            assertEquals(Color.BLUE, result.getPixel(19, 29));
        } finally {
            result.recycle();
        }
    }
}
