package io.github.viewcapture.demo.sample;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import io.github.viewcapture.demo.R;

public final class SurfaceSampleView extends FrameLayout implements SurfaceHolder.Callback {
    public static final int FRAME_COLOR = Color.rgb(255, 152, 0);
    private boolean frameReady;

    public SurfaceSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.view_surface_sample, this);
        SurfaceView surface = findViewById(R.id.surface_producer);
        surface.getHolder().addCallback(this);
    }

    public boolean isFrameReady() {
        return frameReady;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        frameReady = false;
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            canvas.drawColor(FRAME_COLOR);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
        frameReady = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        frameReady = false;
    }
}
