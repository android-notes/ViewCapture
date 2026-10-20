package io.github.viewcapture.demo.sample;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public final class GradientSampleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader gradient;

    public GradientSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        gradient =
                new LinearGradient(
                        0,
                        0,
                        w,
                        h,
                        Color.rgb(33, 150, 243),
                        Color.rgb(76, 175, 80),
                        Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setShader(gradient);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(getWidth() * .75f, getHeight() * .5f, getHeight() * .3f, paint);
        paint.setColor(Color.BLACK);
        paint.setTextSize(12 * getResources().getDisplayMetrics().density);
        float density = getResources().getDisplayMetrics().density;
        canvas.drawText("Canvas · gradient", 8 * density, getHeight() * .5f, paint);
        canvas.drawText("text / shape", 8 * density, getHeight() * .5f + 20 * density, paint);
    }
}
