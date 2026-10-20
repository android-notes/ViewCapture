package io.github.viewcapture.demo.sample;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public final class LayerBlendSampleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffXfermode[] modes = {
        new PorterDuffXfermode(PorterDuff.Mode.DST_IN),
        new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT),
        new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    };
    private final String[] names = {
        "DST_IN · alpha mask", "SRC_OUT · cutout", "MULTIPLY · overlap"
    };
    private final Shader colors =
            new RadialGradient(
                    86,
                    36,
                    80,
                    new int[] {0xFF42A5F5, 0xCC7E57C2, 0x003949AB},
                    null,
                    Shader.TileMode.CLAMP);
    private final Shader mask =
            new LinearGradient(
                    12,
                    0,
                    188,
                    0,
                    new int[] {0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                    new float[] {0, .3f, .7f, 1},
                    Shader.TileMode.CLAMP);

    public LayerBlendSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setContentDescription(
                "Complex layers: saveLayer, alpha masks, cutouts, and color blending");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int root = canvas.save();
        canvas.scale(getWidth() / 200f, getHeight() / 320f);
        paint.setColor(0xFF333333);
        paint.setTextSize(12);
        canvas.drawText("Layers & PorterDuff", 10, 20, paint);
        paint.setTextSize(9);
        canvas.drawText("saveLayer / mask / blend", 10, 38, paint);
        int group = canvas.saveLayerAlpha(10, 50, 190, 310, 230);
        for (int row = 0; row < modes.length; row++) {
            int panel = canvas.save();
            canvas.translate(0, 52 + row * 86);
            int layer = canvas.saveLayer(12, 0, 188, 74, null);
            paint.setShader(colors);
            canvas.drawRect(12, 0, 188, 74, paint);
            paint.setShader(null);
            for (int i = 0; i < 12; i++) {
                paint.setColor(0x705C6BC0 + i * 0x00060000);
                canvas.drawCircle(30 + i * 12, 38, 10 + i % 4 * 5, paint);
            }
            paint.setXfermode(modes[row]);
            if (row == 0) {
                paint.setShader(mask);
                canvas.drawRect(12, 0, 188, 74, paint);
                paint.setShader(null);
            } else {
                paint.setColor(0xDDFFB74D);
                canvas.drawRoundRect(60, 18, 176, 66, 18, 18, paint);
            }
            paint.setXfermode(null);
            canvas.restoreToCount(layer);
            paint.setColor(0xFF333333);
            canvas.drawText(names[row], 14, 12, paint);
            canvas.restoreToCount(panel);
        }
        canvas.restoreToCount(group);
        canvas.restoreToCount(root);
    }
}
