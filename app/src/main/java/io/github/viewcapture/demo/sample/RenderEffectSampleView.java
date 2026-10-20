package io.github.viewcapture.demo.sample;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;
import io.github.viewcapture.demo.R;

public final class RenderEffectSampleView extends FrameLayout {
    public RenderEffectSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.view_render_effect_sample, this);
        TextView text = findViewById(R.id.effect_content);
        if (Build.VERSION.SDK_INT >= 31) {
            text.setRenderEffect(RenderEffect.createBlurEffect(7, 7, Shader.TileMode.CLAMP));
        } else {
            text.setText("RenderEffect requires API 31+");
        }
    }
}
