package io.github.viewcapture.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

/** Uses a viewport-sized software layer to display bitmaps taller than the GPU limit. */
public final class BitmapCompareView extends CaptureScrollView {
    private final Paint paint = new Paint();
    private final Rect visible = new Rect();
    private final View extent;
    private Bitmap bitmap;
    private boolean ownsBitmap;

    public BitmapCompareView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        extent = new View(context);
        addView(extent, new ScrollView.LayoutParams(-1, -2));
    }

    boolean hasBitmap() {
        return bitmap != null;
    }

    /** Takes ownership when ownsBitmap is true. */
    void setBitmap(Bitmap bitmap, boolean ownsBitmap) {
        if (bitmap != null
                && Build.VERSION.SDK_INT >= 26
                && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
            throw new IllegalArgumentException("Comparison requires a software-readable bitmap");
        }
        if (this.ownsBitmap && this.bitmap != null && this.bitmap != bitmap) {
            this.bitmap.recycle();
        }
        this.bitmap = bitmap;
        this.ownsBitmap = ownsBitmap;
        extent.setMinimumHeight(bitmap == null ? 0 : bitmap.getHeight());
        setContentDescription(
                bitmap == null
                        ? "Captured bitmap, awaiting capture"
                        : "Captured bitmap, original pixels, synchronized scrolling");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        getDrawingRect(visible);
        if (visible.intersect(0, 0, bitmap.getWidth(), bitmap.getHeight())) {
            // Matching rectangles prevent implicit bitmap density scaling.
            canvas.drawBitmap(bitmap, visible, visible, paint);
        }
    }
}
