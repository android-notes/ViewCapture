package io.github.viewcapture.demo.sample;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public final class TransformSampleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tile = new Path();
    private final Matrix[] transforms = new Matrix[48];
    private final Shader gradient =
            new LinearGradient(-16, -16, 16, 16, 0xFF00897B, 0xFF3949AB, Shader.TileMode.MIRROR);

    public TransformSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            float radius = i % 2 == 0 ? 19 : 11;
            float x = (float) Math.cos(angle) * radius;
            float y = (float) Math.sin(angle) * radius;
            if (i == 0) {
                tile.moveTo(x, y);
            } else {
                tile.lineTo(x, y);
            }
        }
        tile.close();
        for (int i = 0; i < transforms.length; i++) {
            Matrix matrix = transforms[i] = new Matrix();
            matrix.setRotate(i * 17);
            float scale = .65f + (i % 6) * .045f;
            matrix.postScale(scale, scale);
            matrix.postTranslate(25 + i % 6 * 30, 73 + i / 6 * 30);
        }
        setContentDescription(
                "Complex transforms: 48 rotated and scaled groups, nested clips, gradients, and repeated shapes");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int root = canvas.save();
        canvas.scale(getWidth() / 200f, getHeight() / 320f);
        paint.setColor(0xFF333333);
        paint.setTextSize(12);
        canvas.drawText("Matrix & Nested Clip", 10, 20, paint);
        paint.setTextSize(9);
        canvas.drawText("48 tiles / rotate / scale / concat", 10, 38, paint);
        canvas.clipRect(10, 54, 190, 306);
        for (Matrix transform : transforms) {
            int cell = canvas.save();
            canvas.concat(transform);
            canvas.clipPath(tile);
            paint.setShader(gradient);
            canvas.drawRect(-20, -20, 20, 20, paint);
            paint.setShader(null);
            paint.setColor(0xAAFFFFFF);
            paint.setStrokeWidth(1);
            for (int x = -18; x <= 18; x += 6) canvas.drawLine(x, -20, x + 12, 20, paint);
            paint.setColor(0xFFFFCC80);
            canvas.drawCircle(0, 0, 5, paint);
            canvas.restoreToCount(cell);
        }
        canvas.restoreToCount(root);
    }
}
