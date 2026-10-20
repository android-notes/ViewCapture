package io.github.viewcapture.demo.sample;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import io.github.viewcapture.demo.R;

public final class OutlineSampleView extends FrameLayout {
    public OutlineSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.view_outline_sample, this);
        View child = findViewById(R.id.outline_content);
        child.setOutlineProvider(
                new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(
                                0,
                                0,
                                view.getWidth(),
                                view.getHeight(),
                                18 * getResources().getDisplayMetrics().density);
                    }
                });
        child.setClipToOutline(true);
    }
}
