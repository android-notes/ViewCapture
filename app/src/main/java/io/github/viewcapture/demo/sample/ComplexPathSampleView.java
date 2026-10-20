package io.github.viewcapture.demo.sample;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

public final class ComplexPathSampleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path text = new Path();
    private final Path[] waves = new Path[40];
    private final DashPathEffect dash = new DashPathEffect(new float[] {5, 2, 1, 2}, 0);
    private final Shader gradient =
            new SweepGradient(
                    100,
                    180,
                    new int[] {0xFF1565C0, 0xFF8E24AA, 0xFFEF6C00, 0xFF00897B, 0xFF1565C0},
                    null);

    public ComplexPathSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        clip.setFillType(Path.FillType.EVEN_ODD);
        clip.addRoundRect(10, 54, 190, 306, 20, 20, Path.Direction.CW);
        clip.addCircle(100, 180, 30, Path.Direction.CW);
        text.addOval(28, 90, 172, 270, Path.Direction.CW);
        for (int row = 0; row < waves.length; row++) {
            Path wave = waves[row] = new Path();
            float y = 44 + row * 7;
            wave.moveTo(-20, y);
            for (int x = -20; x < 220; x += 15) {
                wave.cubicTo(x + 5, y - 26, x + 10, y + 26, x + 15, y);
            }
        }
        setContentDescription(
                "Complex paths: 640 Bezier segments, cutout clipping, dashes, and path text");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int root = canvas.save();
        canvas.scale(getWidth() / 200f, getHeight() / 320f);
        paint.setColor(0xFF333333);
        paint.setTextSize(12);
        canvas.drawText("Complex Path", 10, 20, paint);
        paint.setTextSize(9);
        canvas.drawText("Bezier / clip / dash / textOnPath", 10, 38, paint);
        int clipped = canvas.save();
        canvas.clipPath(clip);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setPathEffect(dash);
        paint.setShader(gradient);
        for (Path wave : waves) canvas.drawPath(wave, paint);
        paint.setShader(null);
        paint.setPathEffect(null);
        canvas.restoreToCount(clipped);
        paint.setColor(0xFF455A64);
        paint.setStrokeWidth(1);
        canvas.drawPath(clip, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF212121);
        canvas.drawTextOnPath("Canvas · Bezier · EVEN_ODD · dashed strokes", text, 0, 0, paint);
        canvas.restoreToCount(root);
    }
}
