package com.wanjian.capture;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.HardwareRenderer;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.SystemClock;

@TargetApi(29)
final class ImageReaderCaptureNode extends Api29CaptureNode {
    private final HardwareRenderer renderer = new HardwareRenderer();
    private final ImageReader reader;

    ImageReaderCaptureNode(int width, int height) {
        super(width, height);
        ImageReader allocated = null;
        try {
            allocated =
                    ImageReader.newInstance(
                            width,
                            height,
                            PixelFormat.RGBA_8888,
                            2,
                            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
                                    | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
            renderer.setOpaque(false);
            renderer.setSurface(allocated.getSurface());
            renderer.setContentRoot(node);
            reader = allocated;
        } catch (RuntimeException | Error error) {
            renderer.destroy();
            if (allocated != null) {
                allocated.close();
            }
            super.close();
            throw error;
        }
    }

    @Override
    Canvas beginRecording(int width, int height) {
        return node.beginRecording(reader.getWidth(), reader.getHeight());
    }

    @Override
    Bitmap createBitmap(int width, int height) {
        int status = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
        if ((status
                        & (HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND
                                | HardwareRenderer.SYNC_CONTEXT_IS_STOPPED))
                != 0) {
            throw new IllegalStateException("HardwareRenderer sync failed: " + status);
        }
        try (Image image = awaitImage()) {
            try (HardwareBuffer buffer = image.getHardwareBuffer()) {
                if (buffer == null) {
                    throw new IllegalStateException("Image has no HardwareBuffer");
                }
                return Bitmap.wrapHardwareBuffer(buffer, null);
            }
        }
    }

    private Image awaitImage() {
        long deadline = SystemClock.uptimeMillis() + 3000;
        Image image;
        while ((image = reader.acquireNextImage()) == null) {
            if (SystemClock.uptimeMillis() >= deadline) {
                throw new IllegalStateException("Timed out waiting for rendered Image");
            }
            SystemClock.sleep(1);
        }
        return image;
    }

    @Override
    public void close() {
        renderer.destroy();
        reader.close();
        super.close();
    }
}
