package com.wanjian.capture;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;

@TargetApi(29)
class Api29CaptureNode extends CaptureNode {
    final android.graphics.RenderNode node = new android.graphics.RenderNode("ViewCapture");

    Api29CaptureNode(int width, int height) {
        node.setPosition(0, 0, width, height);
    }

    @Override
    Canvas beginRecording(int width, int height) {
        return node.beginRecording(width, height);
    }

    @Override
    void endRecording(Canvas canvas) {
        node.endRecording();
    }

    @Override
    Bitmap createBitmap(int width, int height) {
        return android.graphics.HardwareRenderer.createHardwareBitmap(node, width, height);
    }

    @Override
    public void close() {
        node.discardDisplayList();
    }
}
