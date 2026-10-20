package com.wanjian.capture;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

public final class ViewCapture {
    private ViewCapture() {}

    /**
     * Captures the View's full bounds. Call on the main thread after layout and display.
     *
     * @param view an attached, displayed View that has completed layout
     * @return a caller-owned bitmap: HARDWARE for a single hardware capture, or ARGB_8888 for
     *         tiled captures and software rendering
     */
    public static Bitmap capture(View view) {
        return capture(view, 1f);
    }

    /**
     * Captures the View's full bounds, scaling directly while drawing.
     *
     * @param view an attached, displayed View that has completed layout; call on the main thread
     * @param scale uniform width and height scale in (0, 1]; dimensions are rounded to at least one
     *         pixel
     * @return a caller-owned bitmap: HARDWARE for a single hardware capture, or ARGB_8888 for
     *         tiled captures and software rendering
     * @throws IllegalArgumentException if scale is outside (0, 1] or NaN
     */
    public static Bitmap capture(View view, float scale) {
        if (!(scale > 0f && scale <= 1f)) {
            throw new IllegalArgumentException("Scale must be in (0, 1]");
        }
        if (Build.VERSION.SDK_INT >= 28) {
            HiddenApiAccess.ensure();
        }
        int width = Math.max(1, Math.round(view.getWidth() * scale));
        int height = Math.max(1, Math.round(view.getHeight() * scale));
        int maximumHeight;
        boolean tiled;
        try (CaptureNode node = CaptureNode.create(width, height)) {
            Canvas canvas = node.beginRecording(width, height);
            try {
                maximumHeight = canvas.getMaximumBitmapHeight();
                tiled = canvas.isHardwareAccelerated() && height > maximumHeight;
                if (!tiled) {
                    canvas.scale(scale, scale);
                    view.draw(canvas);
                }
            } finally {
                node.endRecording(canvas);
            }
            if (!tiled) {
                return createBitmap(node, width, height);
            }
        }
        return captureTiles(view, width, height, maximumHeight, scale);
    }

    private static Bitmap captureTiles(
            View view, int width, int height, int maximumHeight, float scale) {
        try (CaptureNode node = new SoftwareCaptureNode(width, height);
                CaptureNode tile = CaptureNode.createTiles(width, maximumHeight)) {
            Canvas canvas = node.beginRecording(width, height);
            Rect source = new Rect(), destination = new Rect();
            try {
                for (int top = 0; top < height; ) {
                    int tileHeight = Math.min(maximumHeight, height - top);
                    Canvas recording = tile.beginRecording(width, tileHeight);
                    try {
                        recording.clipRect(0, 0, width, tileHeight);
                        recording.translate(0, -top);
                        recording.scale(scale, scale);
                        view.draw(recording);
                    } finally {
                        tile.endRecording(recording);
                    }
                    Bitmap hardware = createBitmap(tile, width, tileHeight);
                    Bitmap pixels = null;
                    try {
                        pixels = hardware.copy(Bitmap.Config.ARGB_8888, false);
                        if (pixels == null) {
                            throw new IllegalStateException("Could not read capture tile");
                        }
                        source.set(0, 0, width, tileHeight);
                        destination.set(0, top, width, top + tileHeight);
                        canvas.drawBitmap(pixels, source, destination, null);
                    } finally {
                        if (pixels != null) {
                            pixels.recycle();
                        }
                        hardware.recycle();
                    }
                    top += tileHeight;
                }
            } finally {
                node.endRecording(canvas);
            }
            return createBitmap(node, width, height);
        }
    }

    private static Bitmap createBitmap(CaptureNode node, int width, int height) {
        Bitmap bitmap = node.createBitmap(width, height);
        if (bitmap == null) {
            throw new IllegalStateException("Could not create capture bitmap");
        }
        return bitmap;
    }

    @android.annotation.TargetApi(28)
    private static final class HiddenApiAccess {
        static {
            HiddenApiBypass.addHiddenApiExemptions(
                    Build.VERSION.SDK_INT >= 29
                            ? new String[] {"Landroid/graphics/HardwareRenderer;"}
                            : new String[] {
                                "Landroid/view/RenderNode;", "Landroid/view/ThreadedRenderer;"
                            });
        }

        static void ensure() {}
    }
}
