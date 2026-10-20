package io.github.viewcapture.demo.sample;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import io.github.viewcapture.demo.R;
import java.io.IOException;

public final class VideoTextureSampleView extends FrameLayout
        implements TextureView.SurfaceTextureListener, AutoCloseable {
    private final TextureView texture;
    private MediaPlayer player;
    private Surface surface;
    private boolean prepared, frameReady;

    public VideoTextureSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.BLACK);
        inflate(context, R.layout.view_video_texture_sample, this);
        texture = findViewById(R.id.video_producer);
        texture.setSurfaceTextureListener(this);
    }

    public boolean isFrameReady() {
        return frameReady;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        player = new MediaPlayer();
        player.setSurface(surface);
        player.setLooping(true);
        player.setVolume(0, 0);
        player.setOnPreparedListener(
                media -> {
                    prepared = true;
                    fitVideo();
                    if (getWindowVisibility() == View.VISIBLE) {
                        media.start();
                    }
                });
        player.setOnVideoSizeChangedListener((media, w, h) -> fitVideo());
        player.setOnErrorListener(
                (media, what, extra) -> {
                    Log.e("VideoTexture", "Playback error " + what + "/" + extra);
                    close();
                    return true;
                });
        try (AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.flower)) {
            player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            player.prepareAsync();
        } catch (IOException e) {
            close();
            Log.e("VideoTexture", "Cannot open bundled video", e);
        }
    }

    private void fitVideo() {
        if (!prepared) {
            return;
        }
        int width = player.getVideoWidth(), height = player.getVideoHeight();
        int viewWidth = texture.getWidth(), viewHeight = texture.getHeight();
        if (width == 0 || height == 0 || viewWidth == 0 || viewHeight == 0) {
            return;
        }
        float scale = Math.min((float) viewWidth / width, (float) viewHeight / height);
        Matrix matrix = new Matrix();
        matrix.setScale(
                width * scale / viewWidth,
                height * scale / viewHeight,
                viewWidth / 2f,
                viewHeight / 2f);
        texture.setTransform(matrix);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (!prepared) {
            return;
        }
        if (visibility == View.VISIBLE) {
            player.start();
        } else if (player.isPlaying()) {
            player.pause();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        fitVideo();
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        frameReady = true;
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        close();
        return true;
    }

    @Override
    public void close() {
        prepared = frameReady = false;
        if (player != null) {
            player.release();
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }
}
