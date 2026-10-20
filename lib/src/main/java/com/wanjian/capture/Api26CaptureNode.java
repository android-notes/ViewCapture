package com.wanjian.capture;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;

@TargetApi(26)
final class Api26CaptureNode extends CaptureNode {
    private final android.view.RenderNode node =
            android.view.RenderNode.create("ViewCapture", null);

    Api26CaptureNode(int width, int height) {
        node.setLeftTopRightBottom(0, 0, width, height);
    }

    @Override
    Canvas beginRecording(int width, int height) {
        return node.start(width, height);
    }

    @Override
    void endRecording(Canvas canvas) {
        node.end((android.view.DisplayListCanvas) canvas);
    }

    @Override
    Bitmap createBitmap(int width, int height) {
        return android.view.ThreadedRenderer.createHardwareBitmap(node, width, height);
    }

    @Override
    public void close() {
        node.discardDisplayList();
    }
}
