package io.github.viewcapture.demo;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.viewcapture.demo.sample.HardwareBitmapSampleView;
import io.github.viewcapture.demo.sample.SurfaceSampleView;
import io.github.viewcapture.demo.sample.TextureSampleView;
import io.github.viewcapture.demo.sample.VideoTextureSampleView;
import io.github.viewcapture.demo.sample.WebSampleView;

final class SceneContent implements AutoCloseable {
    static final int SHOWCASE = 8;
    private static final int[] LAYOUTS = {
        R.layout.scene_ordinary,
        R.layout.scene_texture,
        R.layout.scene_surface,
        R.layout.scene_bitmap,
        R.layout.scene_outline,
        R.layout.scene_effect,
        R.layout.scene_mixed,
        R.layout.scene_video,
        R.layout.scene_showcase
    };
    static final int TOP_COLOR = Color.rgb(33, 150, 243);
    static final int BOTTOM_COLOR = Color.rgb(156, 39, 176);
    static final int TEXTURE_COLOR = TextureSampleView.FRAME_COLOR;
    static final int SURFACE_COLOR = SurfaceSampleView.FRAME_COLOR;
    static final int BITMAP_COLOR = HardwareBitmapSampleView.BITMAP_COLOR;
    final LinearLayout target;
    final View topMarker;
    final View bottomMarker;
    final TextureSampleView texture;
    final VideoTextureSampleView video;
    final SurfaceSampleView surface;
    final HardwareBitmapSampleView bitmapView;
    final boolean effects;

    SceneContent(Context context, int scene, boolean longContent) {
        this(context, scene, longContent, 1);
    }

    SceneContent(Context context, int scene, boolean longContent, int groups) {
        effects = scene == 4 || scene == 5 || scene == 6 || scene == SHOWCASE;
        LayoutInflater inflater = LayoutInflater.from(context);
        target = (LinearLayout) inflater.inflate(LAYOUTS[scene], null);
        for (int i = 1; i < groups; i++) {
            LinearLayout copy = (LinearLayout) inflater.inflate(LAYOUTS[scene], target, false);
            while (copy.getChildCount() > 0) {
                View child = copy.getChildAt(0);
                copy.removeViewAt(0);
                target.addView(child);
            }
        }
        topMarker = target.findViewById(R.id.start_marker);
        bottomMarker = target.getChildAt(target.getChildCount() - 1);
        texture = target.findViewById(R.id.texture_sample);
        video = target.findViewById(R.id.video_sample);
        surface = target.findViewById(R.id.surface_sample);
        bitmapView = target.findViewById(R.id.bitmap_sample);
        if (scene == 6 && Build.VERSION.SDK_INT < 31) {
            for (int i = 0; i < target.getChildCount(); i++) {
                View child = target.getChildAt(i);
                if (child.getId() == R.id.effect_sample) {
                    child.setVisibility(View.GONE);
                }
            }
        }
        if (longContent) {
            fillToTwoScreens(context, inflater);
        }
    }

    private void fillToTwoScreens(Context context, LayoutInflater inflater) {
        int minimumHeight = 2 * screenHeight(context);
        target.setMinimumHeight(minimumHeight);
        int contentHeight = target.getPaddingTop() + target.getPaddingBottom();
        for (int i = 0; i < target.getChildCount(); i++) {
            View child = target.getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                contentHeight += child.getLayoutParams().height;
            }
        }
        int remaining = Math.max(0, minimumHeight - contentHeight);
        int minimumRowHeight = dp(context, 128);
        int rows = remaining == 0 ? 0 : Math.max(1, remaining / minimumRowHeight);
        for (int i = 0; i < rows; i++) {
            TextView row = (TextView) inflater.inflate(R.layout.sample_content_row, target, false);
            row.setText(
                    String.format(
                            java.util.Locale.ROOT,
                            "Content beyond the viewport · ROW %02d",
                            i + 1));
            row.setBackgroundColor(i % 2 == 0 ? Color.rgb(235, 243, 251) : Color.WHITE);
            row.getLayoutParams().height =
                    Math.max(minimumRowHeight, remaining / rows + (i < remaining % rows ? 1 : 0));
            target.addView(row, target.indexOfChild(bottomMarker));
        }
    }

    boolean ready() {
        return target.isAttachedToWindow()
                && target.isLaidOut()
                && !target.isLayoutRequested()
                && (texture == null || texture.isFrameReady())
                && (surface == null || surface.isFrameReady())
                && (video == null || video.isFrameReady());
    }

    private static int screenHeight(Context context) {
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 30) {
            return manager.getMaximumWindowMetrics().getBounds().height();
        }
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        manager.getDefaultDisplay().getRealMetrics(metrics);
        return metrics.heightPixels;
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public void close() {
        for (int i = 0; i < target.getChildCount(); i++) {
            View child = target.getChildAt(i);
            if (child instanceof WebSampleView) {
                ((WebSampleView) child).close();
            }
            if (child instanceof HardwareBitmapSampleView) {
                ((HardwareBitmapSampleView) child).close();
            }
            if (child instanceof VideoTextureSampleView) {
                ((VideoTextureSampleView) child).close();
            }
        }
    }
}
