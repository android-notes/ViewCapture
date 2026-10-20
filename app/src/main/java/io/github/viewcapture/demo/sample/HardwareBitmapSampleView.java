package io.github.viewcapture.demo.sample;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

public final class HardwareBitmapSampleView extends View implements AutoCloseable {
    public static final int BITMAP_COLOR = Color.rgb(233, 30, 99);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();
    private final Bitmap bitmap;

    public HardwareBitmapSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (Build.VERSION.SDK_INT < 26) {
            bitmap = null;
            return;
        }
        Bitmap source = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        source.eraseColor(BITMAP_COLOR);
        bitmap = source.copy(Bitmap.Config.HARDWARE, false);
        source.recycle();
        if (bitmap == null) {
            throw new IllegalStateException("Could not create hardware bitmap fixture");
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        bounds.set(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap != null && canvas.isHardwareAccelerated() && !bitmap.isRecycled()) {
            canvas.drawBitmap(bitmap, null, bounds, paint);
        }
        paint.setColor(Color.BLACK);
        float density = getResources().getDisplayMetrics().density;
        paint.setTextSize(14 * density);
        canvas.drawText("HARDWARE Bitmap", 10 * density, 26 * density, paint);
        canvas.drawText("Config.HARDWARE", 10 * density, 46 * density, paint);
    }

    @Override
    public void close() {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
