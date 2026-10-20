package io.github.viewcapture.demo.sample;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.TextureView;
import android.widget.FrameLayout;
import io.github.viewcapture.demo.R;

public final class TextureSampleView extends FrameLayout
        implements TextureView.SurfaceTextureListener {
    public static final int FRAME_COLOR = Color.rgb(0, 188, 160);
    private final TextureView texture;
    private boolean frameReady;

    public TextureSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.view_texture_sample, this);
        texture = findViewById(R.id.texture_producer);
        texture.setSurfaceTextureListener(this);
    }

    public boolean isFrameReady() {
        return frameReady;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        drawFrame();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        frameReady = false;
        drawFrame();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        frameReady = false;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        frameReady = true;
    }

    private void drawFrame() {
        Canvas canvas = texture.lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            canvas.drawColor(FRAME_COLOR);
        } finally {
            texture.unlockCanvasAndPost(canvas);
        }
    }
}
